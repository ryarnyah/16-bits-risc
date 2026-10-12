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

  // Phase 4b memory map: RAM [0x0000, 0x1FFF] (all 8 KB usable), I/O at
  // the top of the address space (UART alias [0xFF00, 0xFFF7] and
  // [0xFFFC, 0xFFFF]; interrupt registers [0xFFF8, 0xFFFB]), unmapped
  // hole [0x2000, 0xFEFF]: reads return 0 via the registered responder,
  // writes are dropped.  Pure boolean one-level compares (the Phase-3
  // lesson — the old `>= 0x1FFC` CARRY4 compare sat on the rBusAddr →
  // ramRspVld → ldData critical path at 90.13 MHz); equivalent forms
  // machine-asserted in the formal below.  Same decode as Soc.
  private val isRam    = core.io.dataBus.req.addr(15 downto 13) === 0
  private val isIoAddr = core.io.dataBus.req.addr(15 downto 8) === 0xFF
  private val isDead   = !isRam && !isIoAddr
  private val dataWordAddr = core.io.dataBus.req.addr(15 downto 1).resize(log2Up(4096))

  // ── Interrupt controller (Phase 4, zero-ISA — ISA-3 §10.4) ──
  // I/O registers at the top of memory: 0xFFF8 CTRL (bit0 IMASK, bit1
  // SWIRQ) and 0xFFFA EPC (read-only window onto the core's link
  // value).  Sources: UART RX non-empty (level) and the software bit.
  // Byte-identical to Soc — both SoCs share the memory map and protocol.
  private val isIntTop  = isIoAddr && (core.io.dataBus.req.addr(7 downto 2) === 0x3E)
  private val isIntCtrl = isIntTop && !core.io.dataBus.req.addr(1)
  private val isEpc     = isIntTop && core.io.dataBus.req.addr(1)
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
  // Unmapped-hole reads share the responder and return 0 (reads are
  // deterministic for every address pattern — design goal 2).
  when(core.io.dataBus.req.fire && (isIntReg || isDead) &&
    !core.io.dataBus.req.wr) {
    intRspVld := True
    intRdData := Mux(isEpc, epcReg,
      Mux(isDead, B(0, 16 bits), B(0, 13 bits) ## intPending ## swIrq ## imask))
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
  private val ramRspVld = core.io.dataBus.req.fire && isRam && !core.io.dataBus.req.wr

  // v2.1 §4.6: byte writes (STB) mask exactly one 8-bit lane — little-endian,
  // lane selected by addr[0].  Word writes (ST) keep the full 2-bit mask.
  private val ramWrMask = core.io.dataBus.req.payload.isByte ?
    Mux(core.io.dataBus.req.addr(0), B"10", B"01") | B"11"
  private val ramWrEn = core.io.dataBus.req.fire && isRam && core.io.dataBus.req.wr
  dataRam.write(dataWordAddr, core.io.dataBus.req.wrData, ramWrEn,
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
    val pRamRdReq  = past(core.io.dataBus.req.fire && isRam && !core.io.dataBus.req.wr)
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

    /* TC-SOC-2: memory-map decode equivalence — RAM is exactly
     * addr[15:13]=0, the I/O page is exactly addr[15:8]=0xFF, and the
     * two regions are disjoint. */
    assert(isIoAddr === (core.io.dataBus.req.addr(15 downto 8) === 0xFF))
    assert(isRam === (core.io.dataBus.req.addr(15 downto 13) === 0))
    assert(!(isIoAddr && isRam))

    // Async RAM: ramRspVld is combinational (same cycle as req.fire)
    when(core.io.dataBus.req.fire && isRam && !core.io.dataBus.req.wr) {
      assert(ramRspVld)
    }

    /* Int-register writes — 0xFFF8/0xFFFA — are register writes and never
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
    when(core.io.dataBus.req.fire && isRam && core.io.dataBus.req.wr) {
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
        assert(intRspVld === past(core.io.dataBus.req.fire &&
          (isIntReg || isDead) && !core.io.dataBus.req.wr))
      }
    }

    /* TC-IRQ-S7: interrupt decode — int registers live at the top of
     * the I/O page, 0xFFF8 CTRL / 0xFFFA EPC (byte accesses map to the
     * same registers through the word decode). */
    when(isIntReg) {
      assert(isIoAddr)
      assert(core.io.dataBus.req.addr(7 downto 2) === 0x3E)
    }

    /* TC-IRQ-S8: the unmapped hole 0x2000..0xFEFF is deterministic —
     * reads are answered by the registered responder with 0, writes
     * have no effect anywhere (no RAM write, no UART byte, interrupt
     * state unchanged). */
    when(pastValid()) {
      when(past(core.io.dataBus.req.fire && isDead && !core.io.dataBus.req.wr)) {
        assert(intRspVld)
        assert(intRdData === 0)
      }
    }
    when(core.io.dataBus.req.fire && isDead && core.io.dataBus.req.wr) {
      assert(!uart.io.txVld)
      assert(!ramWrEn)
    }
    when(pastValid()) {
      when(past(core.io.dataBus.req.fire && isDead && core.io.dataBus.req.wr)) {
        assert(imask === past(imask))
        assert(swIrq === past(swIrq))
        assert(epcReg === past(epcReg))
      }
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
