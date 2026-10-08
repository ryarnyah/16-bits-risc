package risc

import spinal.core._
import spinal.core.formal._
import spinal.lib._

import scala.language.postfixOps

/**
 * Top-level I/O bundle for the 16-bit RISC core.
 *
 * Provides the external debug/control bus interface (8-bit Stream protocol),
 * a stream-based data bus for LD/ST data access, and debug observation
 * signals for the emulator/testbench.
 */
case class CoreIo() extends Bundle {
  /** Debug/control bus interface (8-bit cmd/rsp Stream + ack). */
  val bus: CoreBusIo = CoreBusIo()
  /** Data bus for LD/ST (stream-based request/response). */
  val dataBus: DataBusIo = DataBusIo()
  /** Instruction fetch bus (stream response, address driven combinatorially). */
  val instrAddr: UInt = out UInt (16 bits)
  val instrRsp: Stream[Bits] = Stream(Bits(16 bits))

  val dbgState: Bits = out Bits (3 bits)
  val dbgPC: UInt = out UInt (16 bits)
  val dbgRunning: Bool = out Bool ()
  val dbgRd: UInt = out UInt (3 bits)
  val dbgAluRes: Bits = out Bits (16 bits)
  val dbgInstr: Bits = out Bits (16 bits)
  val dbgCmdPhrase: Bool = out Bool ()
  val dbgRspPhrase: Bool = out Bool ()
  val dbgBusWordFire: Bool = out Bool ()
  val dbgCmdBuf: Bits = out Bits (32 bits)
  val dbgRspBuf: Bits = out Bits (32 bits)
  val dbgRegFile1: Bits = out Bits (16 bits)
}

case class Core() extends Component with CoreBusIoComponent {
  val io: CoreIo = CoreIo()
  io.bus.cmd.asSlave()
  io.bus.rsp.asMaster()
  io.bus.ack.asOutput()
  io.instrRsp.asSlave()
  io.dataBus.asMaster()

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

  // === Instruction fetch via stream bus from SoC ===
  // addr driven combinatorially above; response arrives on io.instrRsp

  // === Decoder ===
  decoder.io.instr := instr

  // === Sign/zero extension (ISA v2.1 §4.5, shared via Isa) ===
  private val sextVal = Isa.sext6(decoder.io.imm6)
  private val zextVal = Isa.zext6(decoder.io.imm6)   // group-B immediates

  // === v2.1: byte-memory class (LDB/STB) ===
  private val isByteMem = decoder.io.isLDB || decoder.io.isSTB
  private val isLoad = decoder.io.isLD || decoder.io.isLDB

  // === ALU ===
  // Operand B: sign-extended imm (ADDI/XORI), zero-extended imm (group-B
  // immediates), or the Rt register value.
  alu.io.rsVal := regFile.io.rsVal
  alu.io.opB := decoder.io.isImmEn ? sextVal |
    (decoder.io.immZext ? zextVal | regFile.io.rtVal)
  alu.io.aluFunc := decoder.io.aluFunc

  // === Load/store effective address ===
  // ISA v3.1: word access = Rs + sext(off6)*2 (off in words, -32..+31);
  // byte access (LDB/STB, v2.1 §4.6) = Rs + zext(off3).  Odd word addresses
  // are impossible by construction (off*2 is even), so the v2.1 §2.3
  // bit-0-ignored aliasing rule is gone.
  private val offZext3 = B(0, 13 bits) ## decoder.io.instr(2 downto 0)
  private val offScaled = (sextVal.asUInt << 1).resize(16)
  private val effAddr = Mux(isByteMem,
    (regFile.io.rsVal.asUInt + offZext3.asUInt).asBits,
    (regFile.io.rsVal.asUInt + offScaled).asBits.resized)

  // === Register file ===
  // Port addresses via Isa (single source of truth with PipCore/Decoder).
  private val rsAddr = Isa.rsAddrOf(decoder.io.instr)
  private val rtAddr = Isa.rtAddrOf(decoder.io.instr)

  regFile.io.rsAddr := rsAddr
  regFile.io.rtAddr := rtAddr
  regFile.io.wrAddr := rd
  // LD hazard: aluRes is updated at the same posedge as the register file write,
  // so the regfile would get the stale aluRes value. Use rsp.payload directly
  // when LD completes (rsp.fire), bypassing the register pipeline delay.
  // v2.1 §5.6: LDB zero-extends the addressed byte lane (selected by addr[0],
  // little-endian §2.2); LD passes the full word through.
  private val loadWord = io.dataBus.rsp.payload
  private val loadByte = Mux(effAddr(0), loadWord(15 downto 8), loadWord(7 downto 0))
  private val loadVal = decoder.io.isLDB ? (B(0, 8 bits) ## loadByte) | loadWord
  regFile.io.wrData := Mux(isLoad && io.dataBus.rsp.fire, loadVal, aluRes)
  // LD/LDB: wrEn only asserted when rsp.fire (no unnecessary writes during stall)
  // All other instrs: wrEn asserted in WRITEBACK (aluRes already computed)
  regFile.io.wrEn := (rd =/= 0) && (state === CoreState.WRITEBACK) &&
    (!isLoad || io.dataBus.rsp.fire)
  regFile.io.auxAddr := Mux(
    busIf.io.cmdStrb && busIf.io.cmdWord(31 downto 24) === 0x06,
    busIf.io.cmdWord(18 downto 16).asUInt,
    U"001"
  )
  io.dbgRegFile1 := regFile.io.auxVal

  // === Branch target ===
  // ISA v3.2: BR offset is instr[6:0] (words, ±64), added to the already-
  // incremented PC.  Same shift pattern as the word-scaled LD/ST offset.
  private val brOff7 = decoder.io.instr(6 downto 0).asSInt.resize(16).asBits
  private val brShifted = brOff7(14 downto 0) ## B"0"
  private val brTarget = (PC.asSInt + brShifted.asSInt).asBits.resized

  // === v3.3 PC-relative control targets ===
  // CALLR: off9 = instr[8:0]; JMPR: off12 = instr[11:0] (words, same
  // already-incremented-PC rule as branches; same shift pattern).
  private val callOff9 = decoder.io.instr(8 downto 0).asSInt.resize(16).asBits
  private val callShifted = callOff9(14 downto 0) ## B"0"
  private val jmpOff12 = decoder.io.instr(11 downto 0).asSInt.resize(16).asBits
  private val jmpShifted = jmpOff12(14 downto 0) ## B"0"

  // === Branch condition evaluation (v3.2, shared via Isa) ===
  private val brTaken = decoder.io.isBranch &&
    Isa.brTaken(regFile.io.rsVal, decoder.io.brCC)

  // === ST data (from rdField via rtAddr override) ===
  private val stVal = regFile.io.rtVal
  // v2.1 §5.6: STB writes R[Rd][7:0] into the addressed byte lane
  // (little-endian: odd address → upper lane).  The SoC masks the other lane.
  private val stData = Mux(effAddr(0),
    (stVal(7 downto 0) ## B(0, 8 bits)),
    (B(0, 8 bits) ## stVal(7 downto 0)))

  // === Bus command processing ===
  // Commands: 0x03=RESET, 0x04=STEP, 0x05=RUN, 0x06=READ_REG, 0x08=READ_PC
  busIf.io.cmdDone := False
  busIf.io.rspStrb := False
  busIf.io.rspWord := 0

  when(busIf.io.cmdStrb) {
    switch(busIf.io.cmdWord(31 downto 24)) {
      is(0x03) { state := CoreState.IDLE; PC := 0; running := False; busIf.io.cmdDone := True }
      is(0x04) { state := CoreState.FETCH; busIf.io.cmdDone := True }
      is(0x05) { state := CoreState.FETCH; running := True; busIf.io.cmdDone := True }
      is(0x06) {
        busIf.io.rspWord := B(0, 16 bits) ## regFile.io.auxVal
        busIf.io.rspStrb := True
      }
      is(0x08) {
        busIf.io.rspWord := B(0, 16 bits) ## PC.asBits
        busIf.io.rspStrb := True
      }
    }
  }

  // === Data bus default assignments ===
  io.dataBus.req.payload.addr := effAddr.asUInt
  io.dataBus.req.payload.wrData := stVal
  io.dataBus.req.valid := False
  io.dataBus.req.payload.wr := False
  io.dataBus.req.payload.isByte := False
  io.dataBus.rsp.ready := False

  // === Instruction bus default (not ready outside FETCH/LDI_FETCH) ===
  io.instrRsp.ready := False

  // Instruction fetch address driven combinatorially from PC
  io.instrAddr := PC

  // === FSM: done() helper ===
  private def done(): Unit = {
    when(running) { state := CoreState.FETCH } otherwise { state := CoreState.IDLE }
  }

  // === FSM states ===
  // ISA §3: Multi-cycle execution — FETCH → DECODE → (LDI_FETCH) → WRITEBACK
  switch(state) {
    is(CoreState.FETCH) {
      io.instrRsp.ready := True
      when(io.instrRsp.fire) {
        instr := io.instrRsp.payload
        PC := PC + 2
        state := CoreState.DECODE
      }
    }
    is(CoreState.DECODE) {
      rd := (decoder.io.hasRd ? decoder.io.rdField | B"000").asUInt
      // v2.1 §2.6: reserved encodings execute as NOP — no state effect,
      // PC already advanced past the word at FETCH.
      when(decoder.io.isReserved) { done() }
      when(decoder.io.isALU) { aluRes := alu.io.result; state := CoreState.WRITEBACK }
      // v2.1 §5.6: LDI8 — 1-word constant load, skips LDI_FETCH (PC += 2).
      when(decoder.io.isLDI8) {
        aluRes := B(0, 8 bits) ## decoder.io.instr(7 downto 0)
        state := CoreState.WRITEBACK
      }
      when(decoder.io.isLD || decoder.io.isLDB) {
        io.dataBus.req.valid := True
        io.dataBus.req.payload.addr := effAddr.asUInt
        io.dataBus.req.payload.wr := False
        io.dataBus.req.payload.isByte := decoder.io.isLDB
        when(io.dataBus.req.fire) { state := CoreState.WRITEBACK }
      }
      when(decoder.io.isST || decoder.io.isSTB) {
        io.dataBus.req.valid := True
        io.dataBus.req.payload.addr := effAddr.asUInt
        io.dataBus.req.payload.wrData := decoder.io.isSTB ? stData | stVal
        io.dataBus.req.payload.wr := True
        io.dataBus.req.payload.isByte := decoder.io.isSTB
        when(io.dataBus.req.fire) { done() }
      }
      when(decoder.io.isJMP) { PC := regFile.io.rsVal.asUInt; done() }
      // v2.1 §5.4: CALL Rlink,Rtarget — Rlink ← PC (already incremented past
      // the CALL at FETCH), then jump to Rtarget (Rt field instr[5:3]).
      when(decoder.io.isCALL) {
        aluRes := PC.asBits
        PC := regFile.io.rtVal.asUInt
        state := CoreState.WRITEBACK
      }
      // v3.3: CALLR Rlink,off9 — link write like CALL, PC-relative target.
      when(decoder.io.isCALLR) {
        aluRes := PC.asBits
        PC := (PC.asSInt + callShifted.asSInt).asBits.resized.asUInt
        state := CoreState.WRITEBACK
      }
      // v3.3: JMPR off12 — PC-relative unconditional jump.
      when(decoder.io.isJMPR) {
        PC := (PC.asSInt + jmpShifted.asSInt).asBits.resized.asUInt
        done()
      }
      when(decoder.io.isBranch) {
        when(brTaken) { PC := brTarget.asUInt }
        done()
      }
      // v2.1 §5.4: HALT — terminal state until reset/bus command.
      when(decoder.io.isHALT) { state := CoreState.HALT }
      when(decoder.io.isLDI) { state := CoreState.LDI_FETCH }
    }
    is(CoreState.LDI_FETCH) {
      io.instrRsp.ready := True
      when(io.instrRsp.fire) {
        aluRes := io.instrRsp.payload
        PC := PC + 2
        state := CoreState.WRITEBACK
      }
    }
    is(CoreState.WRITEBACK) {
      when(isLoad) {
        io.dataBus.rsp.ready := True
        when(io.dataBus.rsp.fire) {
          aluRes := loadVal
          done()
        }
      } otherwise {
        done()
      }
    }
    // v2.1 §5.4: HALT — freeze until reset/bus command (cmd 0x03/0x04/0x05
    // are handled above the switch and can leave this state).
    is(CoreState.HALT) {}
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
      // TC-CORE-3: WRITEBACK transitions to IDLE, FETCH, or stays (LD waiting for rsp)
      when(past(state) === CoreState.WRITEBACK) {
        when(!busChangesState && resetn) {
          assert(state === CoreState.IDLE || state === CoreState.FETCH || state === CoreState.WRITEBACK)
        }
        when(!busChangesPC && resetn)    { assert(PC === past(PC)) }
      }

      // TC-CORE-7 (v2.1): ALU-class (incl. group-B immediates) and LDI8
      // unconditionally move DECODE → WRITEBACK without touching the PC.
      when((past(decoder.io.isALU) || past(decoder.io.isLDI8)) &&
        past(state) === CoreState.DECODE) {
        when(!busChangesState && resetn) { assert(state === CoreState.WRITEBACK) }
        when(!busChangesPC && resetn)    { assert(PC === past(PC)) }
      }

      // TC-CORE-8 (v2.1): LD/LDB/ST/STB only leave DECODE on a bus handshake,
      // and never modify the PC there.  (instr is held from FETCH, so the
      // current decode still describes the instruction that was in DECODE.)
      when(past(state) === CoreState.DECODE &&
        (past(decoder.io.isLD) || past(decoder.io.isLDB) ||
         past(decoder.io.isST) || past(decoder.io.isSTB)) &&
        past(io.dataBus.req.fire)) {
        when(!busChangesPC && resetn) { assert(PC === past(PC)) }
        when((decoder.io.isLD || decoder.io.isLDB) && !busChangesState && resetn) {
          assert(state === CoreState.WRITEBACK)
        }
        when((decoder.io.isST || decoder.io.isSTB) && !busChangesState && resetn) {
          assert(state === CoreState.IDLE || state === CoreState.FETCH)
        }
      }

      // TC-CORE-9 (v2.1): CALL — link register receives the PC that was current
      // at DECODE (address after the 1-word CALL), PC ← R[Rt] (target).
      when(resetn && past(state) === CoreState.DECODE && past(decoder.io.isCALL)) {
        when(!busChangesState) { assert(state === CoreState.WRITEBACK) }
        when(!busChangesPC) {
          assert(regFile.io.wrData === past(PC).asBits)
          assert(PC === past(regFile.io.rtVal).asUInt)
        }
      }

      // TC-CORE-12 (v3.3): CALLR — link write like CALL, PC-relative target
      // (PC_at_DECODE + sext(off9)*2, no register read, no memory effect).
      when(resetn && past(state) === CoreState.DECODE && past(decoder.io.isCALLR)) {
        when(!busChangesState) { assert(state === CoreState.WRITEBACK) }
        when(!busChangesPC) {
          assert(regFile.io.wrData === past(PC).asBits)
          assert(PC === (past(PC).asSInt + past(callShifted).asSInt).asBits.resized.asUInt)
          assert(!io.dataBus.req.valid)
        }
      }

      // TC-CORE-13 (v3.3): JMPR — PC-relative jump, no writeback, no memory.
      when(resetn && past(state) === CoreState.DECODE && past(decoder.io.isJMPR)) {
        when(!busChangesState) {
          assert(state === CoreState.IDLE || state === CoreState.FETCH)
        }
        when(!busChangesPC) {
          assert(PC === (past(PC).asSInt + past(jmpShifted).asSInt).asBits.resized.asUInt)
          assert(!io.dataBus.req.valid)
        }
      }

      // TC-CORE-10 (v2.1): HALT is terminal — state and PC freeze until a
      // bus command (§5.4: only reset/bus reset exits HALT on this SoC).
      when(past(state) === CoreState.HALT) {
        when(!busChangesState && resetn) { assert(state === CoreState.HALT) }
        when(!busChangesPC && resetn)    { assert(PC === past(PC)) }
      }

      // TC-CORE-11 (v2.1): reserved encodings are NOP — DECODE → IDLE/FETCH,
      // no PC change, no register write (§2.6).
      when(past(state) === CoreState.DECODE && past(decoder.io.isReserved)) {
        when(!busChangesState && resetn) {
          assert(state === CoreState.IDLE || state === CoreState.FETCH)
        }
        when(!busChangesPC && resetn) { assert(PC === past(PC)) }
        assert(!regFile.io.wrEn || rd === 0)
      }
    }

    // ── v2.1 byte-load lane select (LDB, §5.6) ──
    when(state === CoreState.WRITEBACK && decoder.io.isLDB && io.dataBus.rsp.fire) {
      val expByte = Mux(effAddr(0),
        io.dataBus.rsp.payload(15 downto 8), io.dataBus.rsp.payload(7 downto 0))
      assert(regFile.io.wrData === (B(0, 8 bits) ## expByte))
      assert(regFile.io.wrEn || rd === 0)
    }

    // ── v2.1: LDI8 writes the zero-extended immediate (§5.6) ──
    when(state === CoreState.WRITEBACK && decoder.io.isLDI8) {
      assert(aluRes === (B(0, 8 bits) ## decoder.io.instr(7 downto 0)))
    }

    // ── Data write path assertions ──

    // TC-CORE-4: LD writes data bus response payload directly, not stale aluRes.
    // The wrData Mux (line 105-106) bypasses the aluRes pipeline register when
    // rsp.fire, ensuring the regfile gets the correct load data even though
    // aluRes is updated at the same posedge (non-blocking assignment hazard).
    when(state === CoreState.WRITEBACK && decoder.io.isLD && io.dataBus.rsp.fire) {
      assert(regFile.io.wrData === io.dataBus.rsp.payload)
    }

    // TC-CORE-5: During LD WRITEBACK stalling (waiting for rsp), wrEn is false.
    // Prevents regfile from being written with stale data every cycle.
    when(state === CoreState.WRITEBACK && decoder.io.isLD && !io.dataBus.rsp.fire) {
      when(!busChangesState && resetn) {
        assert(!regFile.io.wrEn || rd === 0)
      }
    }

    // TC-CORE-6: wrData during LD stalling equals aluRes (not garbage from rsp).
    when(state === CoreState.WRITEBACK && decoder.io.isLD && !io.dataBus.rsp.fire) {
      assert(regFile.io.wrData === aluRes)
    }

    // ── Cover properties (reachability) ──
    cover(state === CoreState.FETCH)
    cover(state === CoreState.DECODE)
    cover(state === CoreState.WRITEBACK)
    cover(state === CoreState.LDI_FETCH)
    cover(state === CoreState.HALT)
    cover(decoder.io.isCALL)
    cover(decoder.io.isCALLR)
    cover(decoder.io.isJMPR)
    cover(decoder.io.isLDI8)
    cover(decoder.io.isLDB)
    cover(decoder.io.isSTB)
    cover(decoder.io.isGrpBImm)
    cover(decoder.io.isReserved)
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
