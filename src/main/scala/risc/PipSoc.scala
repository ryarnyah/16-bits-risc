package risc

import spinal.core._
import spinal.core.formal._

import scala.language.postfixOps

case class PipSocIo() extends Bundle {
  val uartTx: Bool = out Bool ()
  val uartRx: Bool = in Bool ()
  val dbgState: Bits = out Bits (3 bits)
  val dbgRunning: Bool = out Bool ()
  val dbgPc: UInt = out UInt (16 bits)
}

class PipSoc(hexPath: String = "") extends Component {
  val io: PipSocIo = PipSocIo()

  private val hexData = if (hexPath.nonEmpty) {
    val source = scala.io.Source.fromFile(hexPath)
    try {
      source.getLines()
        .filter(l => l.nonEmpty && !l.startsWith("#") && !l.startsWith(";"))
        .map(l => BigInt(l.trim, 16))
        .toSeq
    } finally { source.close() }
  } else Seq.empty

private val core = PipCore()
  private val uart = new Uart()
  private val dataRam = Mem(Bits(16 bits), wordCount = 4096)
  private val instrRom = Mem(Bits(16 bits), wordCount = 4096)
  // Only initialize at compile time if hexPath is provided
  if (hexPath.nonEmpty) instrRom.initBigInt(hexData.padTo(4096, BigInt(0)))

  core.io.bus.cmd.valid := False
  core.io.bus.cmd.payload := 0
  core.io.bus.rsp.ready := False

  io.dbgState <> core.io.dbgState
  io.dbgRunning <> core.io.dbgRunning
  io.dbgPc <> core.io.dbgPC

  io.uartTx <> uart.io.tx
  uart.io.rx <> io.uartRx

  // I/O region = [0x1FFC, 0xFFFF].  Written as pure boolean logic —
  // addr ≥ 0x1FFC ⟺ addr[15:13] ≠ 0 (⇒ addr ≥ 0x2000) ∨ ∧addr[12:2]
  // (⇒ addr ∈ [0x1FFC, 0x1FFF], bits 1:0 free).  Exhaustively equivalent
  // to `addr >= 0x1FFC` over all 65,536 addresses (and asserted in the
  // SoC formal below), but maps to 2 LUT levels instead of the
  // LUT+CARRY4 chain the `>=` constant compare inferred — the compare
  // sat on the rBusAddr → ramRspVld → ldData critical path (90.13 MHz).
  private val isIoAddr = (core.io.dataBus.req.addr(15 downto 13) =/= 0) ||
    core.io.dataBus.req.addr(12 downto 2).andR
  private val dataWordAddr = core.io.dataBus.req.addr(15 downto 1).resize(log2Up(4096))

  // ── Interrupt controller (Phase 4, zero-ISA — ISA-3 §10.4) ──
  // I/O registers carved out of the I/O region: 0x2000 CTRL (bit0 IMASK,
  // bit1 SWIRQ) and 0x2002 EPC (read-only window onto the core's link
  // value).  Sources: UART RX non-empty (level) and the software bit.
  // Byte-identical to Soc — both SoCs share the memory map and protocol.
  private val isIntCtrl = isIoAddr && (core.io.dataBus.req.addr(15 downto 1) === 0x1000)
  private val isEpc     = isIoAddr && (core.io.dataBus.req.addr(15 downto 1) === 0x1001)
  private val isIntReg  = isIntCtrl || isEpc
  private val imask = Reg(Bool()) init False
  private val swIrq = Reg(Bool()) init False
  private val epcReg = Reg(Bits(16 bits)) init 0
  private val intPending = uart.io.rxVld || swIrq

  core.io.irq := intPending && imask
  core.io.irqVector := B(0x0008, 16 bits)

  // Registered CTRL/EPC read response — a fixed 1-cycle latency the LD unit
  // consumes via its WAIT_BUS phase (same as the UART read path).
  private val intRspVld = Reg(Bool()) init False
  private val intRdData = Reg(Bits(16 bits))
  when(core.io.dataBus.rsp.fire) {
    intRspVld := False
  }
  when(core.io.dataBus.req.fire && isIntReg && !core.io.dataBus.req.wr) {
    intRspVld := True
    intRdData := Mux(isEpc, epcReg, B(0, 13 bits) ## intPending ## swIrq ## imask)
  }

  // CTRL write (word ST, or STB to the low byte).
  when(core.io.dataBus.req.fire && isIntCtrl && core.io.dataBus.req.wr) {
    imask := core.io.dataBus.req.wrData(0)
    swIrq := core.io.dataBus.req.wrData(1)
  }
  // Interrupt entry (LAST: auto-DI wins over a same-cycle software write,
  // so a handler can never be entered with the mask left enabled).
  when(core.io.irqAccepted) {
    epcReg := core.io.irqLink
    imask := False
  }

  // RAM / int-registers: always ready; UART write: ready when TX FIFO not
  // full (int-register writes are register writes, no UART involvement).
  core.io.dataBus.req.ready := Mux(isIoAddr && core.io.dataBus.req.wr && !isIntReg,
    uart.io.txReady,
    True)

  // === Instruction ROM (async read, compile-time init only when hexPath provided) ===
  private val instrWordAddr = core.io.instrAddr(15 downto 1).resize(log2Up(4096))
  core.io.instrRsp.valid := True
  core.io.instrRsp.payload := instrRom.readAsync(instrWordAddr)

  uart.io.txVld := core.io.dataBus.req.fire && isIoAddr && core.io.dataBus.req.wr &&
    !isIntReg
  uart.io.txData := core.io.dataBus.req.wrData(7 downto 0)

  private val uartReadPending = RegInit(False)
  private val uartRspVld = RegInit(False)
  private val uartRdData = Reg(Bits(8 bits))

  // Response consumption BEFORE data capture — so when both happen in the
  // same cycle (old response consumed + new UART data already available),
  // the capture block's uartRspVld:=True overwrites the consume block's
  // False, keeping the response valid for the new data.
  when(core.io.dataBus.rsp.fire) {
    uartRspVld := False
  }
  when(uartReadPending && uart.io.rxVld) {
    uartRdData := uart.io.rxData
    uartRspVld := True
    uartReadPending := False
  }
  when(core.io.dataBus.req.fire && isIoAddr && !core.io.dataBus.req.wr && !isIntReg) {
    uartReadPending := True
  }

  uart.io.rxPop := uartReadPending && uart.io.rxVld

  // Async RAM: the LD unit consumes the response in the fire cycle
  // (SNAPSHOT state), so the WB-stage writeback stays in program order —
  // a sync (1-cycle-late) response would let later instructions retire
  // before the load (WAW inversion) and collide with the regfile write
  // port (dropped ADDI R7 frame updates; see PLAN.md known limitation).
  private val ramRdData = dataRam.readAsync(dataWordAddr)
  private val ramRspVld = core.io.dataBus.req.fire && !isIoAddr && !core.io.dataBus.req.wr

  // v2.1 §4.6: byte writes (STB) mask exactly one 8-bit lane — little-endian,
  // lane selected by addr[0].  Word writes (ST) keep the full 2-bit mask.
  private val ramWrMask = core.io.dataBus.req.payload.isByte ?
    Mux(core.io.dataBus.req.addr(0), B"10", B"01") | B"11"
  dataRam.write(dataWordAddr, core.io.dataBus.req.wrData,
    core.io.dataBus.req.fire && !isIoAddr && core.io.dataBus.req.wr,
    mask = ramWrMask)

  core.io.dataBus.rsp.valid := ramRspVld || uartRspVld || intRspVld
  core.io.dataBus.rsp.payload := Mux(ramRspVld, ramRdData,
    Mux(uartRspVld, B(0, 8 bits) ## uartRdData, intRdData))

  GenerationFlags.formal {
    val resetn = ClockDomain.current.readResetWire
    assumeInitial(!resetn)

    assumeInitial(!pastValid())
    assumeInitial(!uartReadPending)
    assumeInitial(!uartRspVld)
    assumeInitial(uartRdData === 0)

    val pReqFire   = past(core.io.dataBus.req.fire)
    val pIsIo      = past(isIoAddr)
    val pReqWr     = past(core.io.dataBus.req.wr)
    val pRamRdReq  = past(core.io.dataBus.req.fire && !isIoAddr && !core.io.dataBus.req.wr)
    val pUartCond  = past(uartReadPending && uart.io.rxVld)
    val pIsIntReg  = past(isIntReg)
    assumeInitial(!pReqFire)
    assumeInitial(!pIsIo)
    assumeInitial(!pReqWr)
    assumeInitial(!pRamRdReq)
    assumeInitial(!pUartCond)
    assumeInitial(!pIsIntReg)
    assumeInitial(!ramRspVld)
    assumeInitial(!uartReadPending)
    assumeInitial(!uartRspVld)
    assumeInitial(uartRdData === 0)
    assumeInitial(!intRspVld)
    assumeInitial(intRdData === 0)
    assumeInitial(epcReg === 0)

    assert(isIoAddr === (core.io.dataBus.req.addr >= 0x1FFC))

    // Async RAM: ramRspVld is combinational (same cycle as req.fire)
    when(core.io.dataBus.req.fire && !isIoAddr && !core.io.dataBus.req.wr) {
      assert(ramRspVld)
    }

    /* Int-register writes — 0x2000/0x2002 — are register writes and never
     * emit a UART byte (see TC-IRQ-S1 below). */
    when(core.io.dataBus.req.fire && isIoAddr && core.io.dataBus.req.wr && !isIntReg) {
      assert(uart.io.txVld)
      assert(uart.io.txData === core.io.dataBus.req.wrData(7 downto 0))
    }
    when(!(core.io.dataBus.req.fire && isIoAddr && core.io.dataBus.req.wr && !isIntReg)) {
      assert(!uart.io.txVld)
    }

    when(!isIoAddr) {
      assert(core.io.dataBus.req.ready)
    }
    when(isIoAddr && core.io.dataBus.req.wr && !isIntReg) {
      assert(core.io.dataBus.req.ready === uart.io.txReady)
    }
    when(isIoAddr && core.io.dataBus.req.wr && isIntReg) {
      assert(core.io.dataBus.req.ready)
    }
    when(isIoAddr && !core.io.dataBus.req.wr) {
      assert(core.io.dataBus.req.ready)
    }

    /* v2.1 §4.6: byte writes mask exactly one 8-bit lane (little-endian,
     * addr[0] selects); word writes mask both lanes. */
    when(core.io.dataBus.req.fire && !isIoAddr && core.io.dataBus.req.wr) {
      when(core.io.dataBus.req.payload.isByte) {
        assert(ramWrMask === Mux(core.io.dataBus.req.addr(0), B"10", B"01"))
        assert(ramWrMask =/= B"11")
      } otherwise {
        assert(ramWrMask === B"11")
      }
    }

    when(pastValid()) {
      when(pReqFire && pIsIo && !pReqWr && !pIsIntReg) {
        assert(uartReadPending)
      }
    }

    when(pastValid()) {
      when(past(uartReadPending && uart.io.rxVld)) {
        assert(uartRspVld)
        assert(uartRdData === past(uart.io.rxData))
      }
    }

    when(pastValid()) {
      when(past(core.io.dataBus.rsp.fire) && !past(uartReadPending)) {
        assert(!uartRspVld)
      }
    }

    when(pastValid()) {
      when(past(uartRspVld) && past(core.io.dataBus.rsp.fire)) {
        // After consuming response: uartRspVld is False unless new data arrived simultaneously
        assert(uartRspVld === (past(uartReadPending) && past(uart.io.rxVld)))
      }
    }

    assert(core.io.dataBus.rsp.valid === (ramRspVld || uartRspVld || intRspVld))

    when(ramRspVld) {
      assert(core.io.dataBus.rsp.payload === ramRdData)
    }
    when(!ramRspVld && uartRspVld) {
      assert(core.io.dataBus.rsp.payload === B(0, 8 bits) ## uartRdData)
    }
    when(!ramRspVld && !uartRspVld) {
      assert(core.io.dataBus.rsp.payload === intRdData)
    }
    when(!ramRspVld && !uartRspVld && !intRspVld) {
      assert(!core.io.dataBus.rsp.valid)
    }

    // ── Interrupt controller (Phase 4, ISA-3 §10.4) — same cases as Soc ──

    /* TC-IRQ-S1: int-register accesses never touch the UART. */
    when(core.io.dataBus.req.fire && isIoAddr && core.io.dataBus.req.wr && isIntReg) {
      assert(!uart.io.txVld)
    }
    when(pastValid()) {
      when(pReqFire && pIsIo && !pReqWr && pIsIntReg) {
        assert(!uartReadPending)
      }
    }

    /* TC-IRQ-S2: EPC latches the core's link value on entry. */
    when(pastValid()) {
      when(past(core.io.irqAccepted)) {
        assert(epcReg === past(core.io.irqLink))
      }
    }

    /* TC-IRQ-S3: auto-DI on entry. */
    when(pastValid()) {
      when(past(core.io.irqAccepted)) {
        assert(!imask)
      }
    }

    /* TC-IRQ-S4: the core request is exactly pending && mask. */
    when(!imask) { assert(!core.io.irq) }
    when(core.io.irqAccepted) { assert(core.io.irq) }

    /* TC-IRQ-S5: CTRL write semantics (auto-DI overrides a same-cycle
     * write). */
    when(pastValid()) {
      when(past(core.io.dataBus.req.fire && isIntCtrl && core.io.dataBus.req.wr) &&
        !past(core.io.irqAccepted)) {
        assert(imask === past(core.io.dataBus.req.wrData(0)))
        assert(swIrq === past(core.io.dataBus.req.wrData(1)))
      }
    }

    /* TC-IRQ-S6: int-register read response — valid exactly one cycle
     * after the request (the LD unit consumes it in WAIT_BUS), cleared on
     * consumption unless a new int read replaces it. */
    when(pastValid()) {
      when(past(core.io.dataBus.req.fire && isIntReg && !core.io.dataBus.req.wr)) {
        assert(intRspVld)
      }
      when(past(intRspVld) && past(core.io.dataBus.rsp.fire)) {
        assert(intRspVld === past(core.io.dataBus.req.fire && isIntReg &&
          !core.io.dataBus.req.wr))
      }
    }

    /* TC-IRQ-S7: interrupt decode — int registers live in the I/O region
     * at 0x2000/0x2002. */
    when(isIntReg) {
      assert(isIoAddr)
      assert(core.io.dataBus.req.addr(15 downto 1) === 0x1000 ||
        core.io.dataBus.req.addr(15 downto 1) === 0x1001)
    }

    cover(!resetn && pastValid())
    cover(core.io.dataBus.req.fire)
    cover(uartReadPending)
    cover(uartRspVld)
    cover(intRspVld)
    cover(core.io.irqAccepted)
  }
}

object PipSoc extends App {
  val hexPath = if (args.length > 0) args(0) else ""
  // Optional 2nd arg: output directory.  The FPGA flow generates a separate
  // copy with a program baked into instrRom (target/gen/PipSoc.sv must stay
  // uninitialized — the emulator pokes instrRom directly after elaboration).
  val outDir = if (args.length > 1) args(1) else "target/gen"
  SpinalConfig(
    targetDirectory = outDir,
    mergeAsyncProcess = true,
    mergeSyncProcess = true,
    genLineComments = true,
    removePruned = true,
    defaultConfigForClockDomains = ClockDomainConfig(
      resetKind = SYNC,
      resetActiveLevel = LOW
    ),
    defaultClockDomainFrequency = FixedFrequency(100 MHz)
  ).generateSystemVerilog(new PipSoc(hexPath))
}
