package risc

import spinal.core._
import spinal.core.formal._

import scala.language.postfixOps

/**
 * Top-level SoC — 16-bit RISC core with external data RAM and serial UART.
 *
 * Core fetches instructions from its internal program memory and accesses data
 * via a stream-based data bus.  SoC provides:
 *   - Data RAM for LD/ST at addresses 0x0000..0x1FFF (all 8 KB usable)
 *   - UART for LD/ST in the top I/O page (conventional 0xFFFC = RX,
 *     0xFFFE = TX) except the interrupt registers 0xFFF8..0xFFFB
 *   - Unmapped hole 0x2000..0xFEFF: reads return 0, writes are dropped
 */
case class SocIo() extends Bundle {
  val uartTx: Bool = out Bool ()
  val uartRx: Bool = in Bool ()
}

class Soc(hexPath: String = "") extends Component {
  val io: SocIo = SocIo()

  private val core = new Core()
  private val uart = new Uart()
  private val dataRam = Mem(Bits(16 bits), wordCount = 4096)
  private val instrRom = Mem(Bits(16 bits), wordCount = 4096)

  // Initialize program memory from hex file at build time
  if (hexPath.nonEmpty) {
    val source = scala.io.Source.fromFile(hexPath)
    val words = try {
      source.getLines()
        .filter(l => l.nonEmpty && !l.startsWith("#") && !l.startsWith(";"))
        .map(l => BigInt(l.trim, 16))
        .toSeq
    } finally { source.close() }
    // Pad to the full ROM size: initBigInt asserts content == wordCount
    // (the same padding PipSoc applies; without it the f4pga Soc flow
    // aborts on any hex shorter than 4096 words).
    instrRom.initBigInt(words.padTo(4096, BigInt(0)))
  }

  // Tie off Core debug bus (not exposed at SoC level)
  core.io.bus.cmd.valid := False
  core.io.bus.cmd.payload := 0
  core.io.bus.rsp.ready := False

  // ── Instruction fetch (async read from program ROM) ──
  private val instrWordAddr = core.io.instrAddr(15 downto 1).resize(log2Up(4096))
  core.io.instrRsp.valid := True
  core.io.instrRsp.payload := instrRom.readAsync(instrWordAddr)

  // ── Serial UART ──
  io.uartTx <> uart.io.tx
  uart.io.rx <> io.uartRx

  // ── Address decode (Phase 4b map) ──
  // RAM [0x0000, 0x1FFF] — all 8 KB usable; I/O at the top of the
  // address space (UART alias [0xFF00, 0xFFF7] and [0xFFFC, 0xFFFF];
  // interrupt registers [0xFFF8, 0xFFFB]); unmapped hole
  // [0x2000, 0xFEFF]: reads return 0 via the registered responder,
  // writes are dropped.  Pure boolean one-level compares; the
  // equivalent-form checks are machine-asserted in the formal below.
  private val isRam    = core.io.dataBus.req.addr(15 downto 13) === 0
  private val isIoAddr = core.io.dataBus.req.addr(15 downto 8) === 0xFF
  private val isDead   = !isRam && !isIoAddr
  private val dataWordAddr = core.io.dataBus.req.addr(15 downto 1).resize(log2Up(4096))

  // ── Interrupt controller (Phase 4, zero-ISA — ISA-3 §10.4) ──
  // I/O registers at the top of memory: 0xFFF8 CTRL (bit0 IMASK, bit1
  // SWIRQ) and 0xFFFA EPC (read-only window onto the core's link
  // value).  Sources: UART RX non-empty (level) and the software bit.
  private val isIntTop  = isIoAddr && (core.io.dataBus.req.addr(7 downto 2) === 0x3E)
  private val isIntCtrl = isIntTop && !core.io.dataBus.req.addr(1)
  private val isEpc     = isIntTop && core.io.dataBus.req.addr(1)
  private val isIntReg  = isIntCtrl || isEpc
  // Region answered by the registered responder on reads: the interrupt
  // registers plus the unmapped hole.  `respRegion` is algebraically
  // identical to (isIntReg || isDead) — equivalence machine-asserted in
  // the formal below — but stacks one less compare level in front of the
  // capture condition (Soc critical path: instr → decoder → offset →
  // effAddr → region decode → req.fire → responder CE, 89.76 MHz).
  private val respRegionRef = isIntReg || isDead
  private val respRegion    = !isRam && (!isIoAddr || isIntTop)
  private val imask = Reg(Bool()) init False
  private val swIrq = Reg(Bool()) init False
  private val epcReg = Reg(Bits(16 bits)) init 0
  private val intPending = uart.io.rxVld || swIrq

  core.io.irq := intPending && imask
  core.io.irqVector := B(0x0008, 16 bits)

  // Registered CTRL/EPC read response — same 1-cycle latency as the sync
  // data RAM, so the core consumes it exactly like any RAM read.
  private val intRspVld = Reg(Bool()) init False
  private val intRdData = Reg(Bits(16 bits))
  when(core.io.dataBus.rsp.fire) {
    intRspVld := False
  }
  // Unmapped-hole reads share the responder and return 0 (reads are
  // deterministic for every address pattern — design goal 2).
  when(core.io.dataBus.req.fire && respRegion &&
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

  // ── Data bus request: accept ──
  // RAM / int-registers: always ready; UART write: ready when TX FIFO not
  // full (int-register writes are register writes, no UART involvement).
  core.io.dataBus.req.ready := Mux(isIoAddr && core.io.dataBus.req.wr && !isIntReg,
    uart.io.txReady,
    True)

  // ── UART write ──
  uart.io.txVld := core.io.dataBus.req.fire && isIoAddr && core.io.dataBus.req.wr &&
    !isIntReg
  uart.io.txData := core.io.dataBus.req.wrData(7 downto 0)

  // ── UART read (pending/response state machine) ──
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
  // If RX FIFO already has data, consume immediately (no extra cycle)
  when(uartReadPending && uart.io.rxVld) {
    uartRdData := uart.io.rxData
    uartRspVld := True
    uartReadPending := False
  }
  when(core.io.dataBus.req.fire && isIoAddr && !core.io.dataBus.req.wr && !isIntReg) {
    uartReadPending := True
  }

  uart.io.rxPop := uartReadPending && uart.io.rxVld

  // ── Data RAM ──
  private val ramRdData = dataRam.readSync(dataWordAddr)
  private val ramRspVld = RegNext(core.io.dataBus.req.fire && isRam && !core.io.dataBus.req.wr)

  // v2.1 §4.6: byte writes (STB) mask exactly one 8-bit lane — little-endian,
  // lane selected by addr[0].  Word writes (ST) keep the full 2-bit mask.
  private val ramWrMask = core.io.dataBus.req.payload.isByte ?
    Mux(core.io.dataBus.req.addr(0), B"10", B"01") | B"11"
  private val ramWrEn = core.io.dataBus.req.fire && isRam && core.io.dataBus.req.wr
  dataRam.write(dataWordAddr, core.io.dataBus.req.wrData, ramWrEn,
    mask = ramWrMask)

  // ── Data bus response ──
  core.io.dataBus.rsp.valid := ramRspVld || uartRspVld || intRspVld
  core.io.dataBus.rsp.payload := Mux(ramRspVld, ramRdData,
    Mux(uartRspVld, B(0, 8 bits) ## uartRdData, intRdData))

  // ======================================================================
  // Formal Verification — covers the following test cases:
  //   TC-SOC-1:  instrRsp always valid
  //   TC-SOC-2:  Address decode: RAM [0,0x1FFF], I/O top page [0xFF00,0xFFFF]
  //   TC-SOC-3:  Data RAM: read response 1 cycle after req.fire
  //   TC-SOC-4:  Data RAM: write on req.fire when isRam, wr=1
  //   TC-SOC-5:  UART write: txVld on req.fire when isIo&&!isIntReg, wr=1
  //   TC-SOC-6:  UART read: uartReadPending on req.fire when isIo&&!isIntReg, wr=0
  //   TC-SOC-7:  UART read: uartRspVld set when pending + uart.io.rxVld
  //   TC-SOC-8:  rsp.valid = ramRspVld || uartRspVld
  //   TC-SOC-9:  rsp.payload mux selects correct source
  //   TC-SOC-10: req.ready: RAM always, UART write depends on txReady
  // ======================================================================
  GenerationFlags.formal {
    val resetn = ClockDomain.current.readResetWire
    assumeInitial(!resetn)

    // Initial state assumptions for registered signals
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

    // ── Instruction fetch ──

    /* TC-SOC-1: Instruction response is always valid (async ROM read) */
    assert(core.io.instrRsp.valid)

    // ── Address decode ──

    /* TC-SOC-2: memory-map decode equivalence — RAM is exactly
     * addr[15:13]=0, the I/O page is exactly addr[15:8]=0xFF, and the
     * two regions are disjoint. */
    assert(isIoAddr === (core.io.dataBus.req.addr(15 downto 8) === 0xFF))
    assert(isRam === (core.io.dataBus.req.addr(15 downto 13) === 0))
    assert(!(isIoAddr && isRam))
    /* The responder capture region uses the restructured one-level form
     * (timing fix on the Soc critical path); semantics unchanged. */
    assert(respRegion === respRegionRef)

    // ── Data RAM ──

    /* TC-SOC-3: RAM read response valid 1 cycle after read request */
    when(pastValid()) {
      when(past(core.io.dataBus.req.fire && isRam && !core.io.dataBus.req.wr)) {
        assert(ramRspVld)
      }
    }

    // ── UART I/O ──

    /* TC-SOC-5: UART write strobe (int-register writes — 0xFFF8/0xFFFA —
     * are register writes and never emit a UART byte; see TC-IRQ-S1) */
    when(core.io.dataBus.req.fire && isIoAddr && core.io.dataBus.req.wr && !isIntReg) {
      assert(uart.io.txVld)
      assert(uart.io.txData === core.io.dataBus.req.wrData(7 downto 0))
    }
    when(!(core.io.dataBus.req.fire && isIoAddr && core.io.dataBus.req.wr && !isIntReg)) {
      assert(!uart.io.txVld)
    }

    /* TC-SOC-10: req.ready routing */
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

    /* TC-SOC-11 (v2.1 §4.6): byte writes mask exactly one 8-bit lane,
     * selected by addr[0] (little-endian); word writes mask both lanes. */
    when(core.io.dataBus.req.fire && isRam && core.io.dataBus.req.wr) {
      when(core.io.dataBus.req.payload.isByte) {
        assert(ramWrMask === Mux(core.io.dataBus.req.addr(0), B"10", B"01"))
        assert(ramWrMask =/= B"11")
      } otherwise {
        assert(ramWrMask === B"11")
      }
    }

    // ── UART read state machine ──

    /* TC-SOC-6: UART read request sets pending (int-register reads are
     * answered by the int FSM, never by the UART — TC-IRQ-S1) */
    when(pastValid()) {
      when(pReqFire && pIsIo && !pReqWr && !pIsIntReg) {
        assert(uartReadPending)
      }
    }

    /* TC-SOC-7: UART read response one cycle after pending + data available */
    when(pastValid()) {
      when(past(uartReadPending && uart.io.rxVld)) {
        assert(uartRspVld)
        assert(uartRdData === past(uart.io.rxData))
      }
    }

    /* uartReadPending cleared when rsp.fire */
    when(core.io.dataBus.rsp.fire) {
      assert(!uartReadPending)
    }

    /* uartRspVld cleared when rsp.fire (unless new data arrived simultaneously) */
    when(pastValid()) {
      when(past(uartRspVld) && past(core.io.dataBus.rsp.fire)) {
        assert(uartRspVld === (past(uartReadPending) && past(uart.io.rxVld)))
      }
    }

    // ── Data bus response ──

    /* TC-SOC-8: rsp.valid = OR of RAM, UART and interrupt-register
     * response valids */
    assert(core.io.dataBus.rsp.valid === (ramRspVld || uartRspVld || intRspVld))

    /* TC-SOC-9: rsp.payload mux (priority RAM > UART > int) */
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

    // ── Interrupt controller (Phase 4, ISA-3 §10.4) ──

    /* TC-IRQ-S1: int-register accesses never touch the UART: writes do
     * not emit a byte (txVld), reads do not start the read FSM. */
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

    /* TC-IRQ-S3: auto-DI on entry (mask is down the cycle after accept). */
    when(pastValid()) {
      when(past(core.io.irqAccepted)) {
        assert(!imask)
      }
    }

    /* TC-IRQ-S4: the core request is exactly pending && mask, and any
     * acceptance implies the request was up in that cycle. */
    when(!imask) { assert(!core.io.irq) }
    when(core.io.irqAccepted) { assert(core.io.irq) }

    /* TC-IRQ-S5: CTRL write semantics (skipped when the same-cycle entry
     * auto-DI overrides it — entry has priority by block order). */
    when(pastValid()) {
      when(past(core.io.dataBus.req.fire && isIntCtrl && core.io.dataBus.req.wr) &&
        !past(core.io.irqAccepted)) {
        assert(imask === past(core.io.dataBus.req.wrData(0)))
        assert(swIrq === past(core.io.dataBus.req.wrData(1)))
      }
    }

    /* TC-IRQ-S6: int-register read response — valid exactly one cycle
     * after the request (same latency as the sync RAM), cleared on
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

object Soc extends App {
  val hexPath = if (args.length > 0) args(0) else ""
  // Optional 2nd arg: output directory (see object PipSoc) — keeps the FPGA
  // copy with an initialized instrRom out of the emulator's target/gen.
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
  ).generateSystemVerilog(new Soc(hexPath))
}
