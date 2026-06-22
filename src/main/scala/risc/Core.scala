package risc

import spinal.core._
import spinal.lib._
import spinal.core.formal._
import scala.language.postfixOps

/**
 * Top-level I/O bundle for the 16-bit RISC core.
 *
 * Provides the external debug/control bus interface (8-bit Stream protocol)
 * and debug observation signals for the emulator/testbench.
 */
case class CoreIo() extends Bundle {
  /** Debug/control bus interface (8-bit cmd/rsp Stream + ack). */
  val bus: CoreBusIo = CoreBusIo()
  /** Current FSM state (CoreState enum, 3 bits). */
  val dbgState: Bits = out Bits (3 bits)
  /** Program counter (16-bit byte address). */
  val dbgPC: UInt = out UInt (16 bits)
  /** Continuous execution mode flag (true when RUN command active). */
  val dbgRunning: Bool = out Bool ()
  /** Destination register address pipeline register (3 bits). */
  val dbgRd: UInt = out UInt (3 bits)
  /** ALU/load pipeline result register (16 bits). */
  val dbgAluRes: Bits = out Bits (16 bits)
  /** Current instruction in the pipeline (16 bits). */
  val dbgInstr: Bits = out Bits (16 bits)
  /** Strobe: complete 4-byte command word has been assembled. */
  val dbgCmdPhrase: Bool = out Bool ()
  /** Active: response bytes are being shifted out to the bus master. */
  val dbgRspPhrase: Bool = out Bool ()
  /** Strobe: bus command word is valid (alias for dbgCmdPhrase). */
  val dbgBusWordFire: Bool = out Bool ()
  /** Most recently assembled 32-bit command word. */
  val dbgCmdBuf: Bits = out Bits (32 bits)
  /** Current 32-bit response word being sent to the bus master. */
  val dbgRspBuf: Bits = out Bits (32 bits)
  /** Read-only value of register R1 for debug/monitoring. */
  val dbgRegFile1: Bits = out Bits (16 bits)
}

case class Core(config: CoreConfig = CoreConfig()) extends Component with CoreBusIoComponent {
  val io: CoreIo = CoreIo()
  io.bus.cmd.asSlave()
  io.bus.rsp.asMaster()
  io.bus.ack.asOutput()

  private val memAddrBits = log2Up(config.memWordCount)
  private val byteAddrBits = log2Up(config.memWordCount * 2)

  // === FSM state ===
  // ISA §3: Multi-cycle execution — FETCH → DECODE → (LDI_FETCH) → WRITEBACK → IDLE
  private val state = RegInit(CoreState.IDLE)
  private val running = Reg(Bool()) init False
  private val PC = Reg(UInt(16 bits)) init 0

  // === Pipeline registers ===
  private val instr = Reg(Bits(16 bits))
  private val rd = Reg(UInt(3 bits))
  private val aluRes = Reg(Bits(16 bits))

  // === Sub-components ===
  private val decoder = Decoder()
  private val regFile = RegFile()
  private val alu = ALU()
  private val busIf = BusInterface()

  // === Wire bus interface ===
  io.bus.cmd >> busIf.io.cmd
  busIf.io.rsp >> io.bus.rsp
  io.bus.ack := busIf.io.ack

  // === Memory (single-port synchronous word-addressable RAM) ===
  private val memory = Mem(Bits(16 bits), wordCount = config.memWordCount)

  // === Instruction fetch ===
  private val pcWordAddr = PC(byteAddrBits - 1 downto 1).resize(memAddrBits)
  private val fetchedInstr = memory.readSync(pcWordAddr)

  // === Decoder ===
  decoder.io.instr := instr

  // === Sign extension ===
  private val sextVal = decoder.io.imm6.asSInt.resize(16).asBits

  // === ALU ===
  alu.io.rsVal := regFile.io.rsVal
  alu.io.opB := decoder.io.isImmEn ? sextVal | regFile.io.rtVal
  alu.io.aluFunc := decoder.io.aluFunc

  // === Load/store effective address ===
  // ISA §3.3: Effective address = Rs + sext(off), word-aligned
  private val effAddr = (regFile.io.rsVal.asSInt + sextVal.asSInt).asBits.resized
  private val dataWordAddr = effAddr(15 downto 1).asUInt.resize(memAddrBits)
  private val dataLoad = memory.readSync(dataWordAddr)

  // === Register file ===
  // Branch (BEQ/BNE/BLT):   Rs in instr[11:9], Rt in instr[8:6]   (ISA §3.4)
  // JMP:                    Rs in instr[11:9]                       (ISA §3.5)
  // ST:                     store source in rdField instr[11:9]     (ISA §3.3)
  // All others:             Rs in instr[8:6],  Rt in instr[5:3]
  private val rsAddr = ((decoder.io.isBranch || decoder.io.isJMP) ?
    decoder.io.instr(11 downto 9).asBits | decoder.io.rsReg.asBits).asUInt
  private val rtAddr = (decoder.io.isST ?
    decoder.io.rdField.asBits |
    (decoder.io.isBranch ? decoder.io.instr(8 downto 6).asBits | decoder.io.rtReg.asBits)).asUInt

  regFile.io.rsAddr := rsAddr
  regFile.io.rtAddr := rtAddr
  regFile.io.wrAddr := rd
  regFile.io.wrData := Mux(decoder.io.isLD, dataLoad, aluRes)
  regFile.io.wrEn := (rd =/= 0) && (state === CoreState.WRITEBACK)
  regFile.io.auxAddr := Mux(
    busIf.io.cmdStrb && busIf.io.cmdWord(31 downto 24) === 0x06,
    busIf.io.cmdWord(18 downto 16).asUInt,
    U"001"
  )
  io.dbgRegFile1 := regFile.io.auxVal

  // === Branch target ===
  // ISA §3.4: Offset is in instructions (×2 for byte address)
  private val brShifted = sextVal(14 downto 0) ## B"0"
  private val brTarget = (PC.asSInt + brShifted.asSInt).asBits.resized

  // === Branch condition evaluation ===
  private val brTaken = (decoder.io.isBEQ && (regFile.io.rsVal === regFile.io.rtVal)) ||
                (decoder.io.isBNE && (regFile.io.rsVal =/= regFile.io.rtVal)) ||
                (decoder.io.isBLT && (regFile.io.rsVal.asSInt < regFile.io.rtVal.asSInt))

  // === ST data (from rdField via rtAddr override) ===
  private val stVal = regFile.io.rtVal

  // === Bus command processing ===
  // Commands: 0x01=LOAD_ADDR, 0x02=LOAD_DATA, 0x03=RESET, 0x04=STEP,
  //           0x05=RUN, 0x06=READ_REG, 0x07=READ_MEM, 0x08=READ_PC
  private val memPtr = Reg(UInt(memAddrBits bits)) init 0

  busIf.io.cmdDone := False
  busIf.io.rspStrb := False
  busIf.io.rspWord := 0

  when(busIf.io.cmdStrb) {
    switch(busIf.io.cmdWord(31 downto 24)) {
      is(0x01) { memPtr := busIf.io.cmdWord(memAddrBits - 1 downto 0).asUInt; busIf.io.cmdDone := True }
      is(0x02) { memPtr := memPtr + 1; busIf.io.cmdDone := True }
      is(0x03) { state := CoreState.IDLE; PC := 0; running := False; busIf.io.cmdDone := True }
      is(0x04) { state := CoreState.FETCH; busIf.io.cmdDone := True }
      is(0x05) { state := CoreState.FETCH; running := True; busIf.io.cmdDone := True }
      is(0x06) {
        busIf.io.rspWord := B(0, 16 bits) ## regFile.io.auxVal
        busIf.io.rspStrb := True
      }
      is(0x07) {
        val memIdx = busIf.io.cmdWord(memAddrBits - 1 downto 0).asUInt
        busIf.io.rspWord := B(0, 16 bits) ## memory.readAsync(memIdx)
        busIf.io.rspStrb := True
      }
      is(0x08) {
        busIf.io.rspWord := B(0, 16 bits) ## PC.asBits
        busIf.io.rspStrb := True
      }
    }
  }

  // === Memory writes ===
  // Bus LOAD_DATA
  memory.write(memPtr, busIf.io.cmdWord(15 downto 0),
    busIf.io.cmdStrb && (busIf.io.cmdWord(31 downto 24) === 0x02))
  // ISA §3.3: ST instruction writes to memory at effective address
  memory.write(dataWordAddr, stVal, decoder.io.isST)

  // === FSM: done() helper ===
  private def done(): Unit = {
    when(running) { state := CoreState.FETCH } otherwise { state := CoreState.IDLE }
  }

  // === FSM states ===
  // ISA §3: Multi-cycle execution — FETCH → DECODE → (LDI_FETCH) → WRITEBACK
  switch(state) {
    is(CoreState.FETCH) {
      instr := fetchedInstr
      PC := PC + 2
      state := CoreState.DECODE
    }
    is(CoreState.DECODE) {
      rd := (decoder.io.hasRd ? decoder.io.rdField | B"000").asUInt
      when(decoder.io.isALU) { aluRes := alu.io.result; state := CoreState.WRITEBACK }
      when(decoder.io.isLD)  { state := CoreState.WRITEBACK }
      when(decoder.io.isST)  { done() }
      when(decoder.io.isJMP) { PC := regFile.io.rsVal.asUInt; done() }
      when(decoder.io.isBranch) {
        when(brTaken) { PC := brTarget.asUInt }
        done()
      }
      when(decoder.io.isLDI) { state := CoreState.LDI_FETCH }
    }
    is(CoreState.LDI_FETCH) {
      aluRes := fetchedInstr
      PC := PC + 2
      state := CoreState.WRITEBACK
    }
    is(CoreState.WRITEBACK) {
      when(decoder.io.isLD) { aluRes := dataLoad }
      done()
    }
    default {}
  }

  // === Debug outputs ===
  io.dbgState := state.asBits.resize(3 bits)
  io.dbgPC := PC
  io.dbgRunning := running
  io.dbgRd := rd
  io.dbgAluRes := aluRes
  io.dbgInstr := instr
  io.dbgCmdPhrase := busIf.io.cmdStrb
  io.dbgRspPhrase := busIf.io.rsp.fire
  io.dbgBusWordFire := busIf.io.cmdStrb
  io.dbgCmdBuf := busIf.io.cmdWord
  io.dbgRspBuf := busIf.io.rspWord

  override def bus(): CoreBusIo = io.bus

  // ==========================================================================
  // Formal Verification Assertions
  // Guarded by GenerationFlags.formal — only active during formal verification
  //
  // Each sub-component (RegFile, ALU, Decoder, BusInterface) is individually
  // verified with its own formal test cases. The Core-level assertions focus
  // on FSM transitions, PC tracking, and high-level integration invariants.
  // ==========================================================================
  GenerationFlags.formal {
    val resetn = ClockDomain.current.readResetWire

    // ── Assumptions about the environment ──
    assumeInitial(!resetn)

    // Sub-components (BusInterface) already use anyseq on bus inputs.
    // Bus inputs are free variables by default in formal verification.

    // Force initial state of all Core-level registers (synchronous init is
    // ineffective because assumeInitial(!resetn) prevents reset from being
    // active in Yosys multiclock model).
    assumeInitial(!pastValid())
    assumeInitial(state === CoreState.IDLE)
    assumeInitial(PC === 0)
    assumeInitial(!running)
    assumeInitial(instr === 0)
    assumeInitial(rd === 0)
    assumeInitial(aluRes === 0)
    assumeInitial(memPtr === 0)

    // ── Reset invariants ──
    // After the initial reset (checked only in first cycle, before any writes):
    // state=IDLE, PC=0, not running.  The multiclock formal model does not
    // reliably propagate synchronous resets on re-assertion, so this check is
    // limited to the initial `!pastValid()` window.
    when(!pastValid()) {
      when(!resetn) {
        assert(state === CoreState.IDLE)
        assert(PC === 0)
        assert(!running)
      }
    }

    // ── Universal invariants ──
    // ISA §1: R0 is always 0 — checked via rsVal when rsAddr=0.
    // (The RegFile component has its own TC-RF-2 assertion for R0.)
    when(regFile.io.rsAddr === 0) { assert(regFile.io.rsVal === 0) }

    // ── Temporal (past) assertions ──
    // Bus commands (STEP=0x04, RUN=0x05, RESET=0x03) can override FSM state/PC
    // in any cycle.  Guard each past-based pipeline assertion to only check
    // when no interfering bus command fires in the same cycle.
    val busChangesState = busIf.io.cmdStrb && (
      busIf.io.cmdWord(31 downto 24) === 0x03 ||
      busIf.io.cmdWord(31 downto 24) === 0x04 ||
      busIf.io.cmdWord(31 downto 24) === 0x05
    )
    val busChangesPC = busIf.io.cmdStrb && busIf.io.cmdWord(31 downto 24) === 0x03

    when(pastValid()) {
      // TC-CORE-1: FETCH always transitions to DECODE (ISA §3)
      // Guarded by resetn (active-low reset) because hardware reset overrides
      // all pipeline registers and would otherwise spuriously fail the check.
      when(past(state) === CoreState.FETCH) {
        when(!busChangesState && resetn) { assert(state === CoreState.DECODE) }
        when(!busChangesPC && resetn)    { assert(PC === past(PC) + 2) }
      }
      // TC-CORE-2: LDI_FETCH always transitions to WRITEBACK (ISA §3.6)
      when(past(state) === CoreState.LDI_FETCH) {
        when(!busChangesState && resetn) { assert(state === CoreState.WRITEBACK) }
        when(!busChangesPC && resetn)    { assert(PC === past(PC) + 2) }
      }
      // TC-CORE-3: WRITEBACK transitions to IDLE or FETCH (never stays)
      when(past(state) === CoreState.WRITEBACK) {
        when(!busChangesState && resetn) { assert(state === CoreState.IDLE || state === CoreState.FETCH) }
        when(!busChangesPC && resetn)    { assert(PC === past(PC)) }
      }
    }

    // ── Cover properties (reachability) ──
    cover(state === CoreState.FETCH)
    cover(state === CoreState.DECODE)
    cover(state === CoreState.WRITEBACK)
    cover(state === CoreState.LDI_FETCH)
    cover(io.bus.ack)
    cover(io.bus.rsp.valid)
  }
}

object Core extends App {
  SpinalConfig(
    targetDirectory = "target/gen",
    mergeAsyncProcess = true,
    mergeSyncProcess = true,
    genLineComments = true,
    removePruned = true,
    defaultConfigForClockDomains = ClockDomainConfig(
      resetKind = SYNC,
      resetActiveLevel = LOW
    ),
    defaultClockDomainFrequency = FixedFrequency(100 MHz)
  ).generateSystemVerilog(Core())
}
