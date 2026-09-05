package risc

import spinal.core._
import spinal.core.formal._
import spinal.lib._
// import spinal.lib.fsm._

import scala.language.postfixOps

object InstrType extends SpinalEnum {
  val EMPTY, ALU, LD, ST, JMP, BR, LDI = newElement()
}

object LdPhase extends SpinalEnum {
  val IDLE, WAIT_BUS, DATA_READY = newElement()
}

case class PipCore() extends Component with CoreBusIoComponent {
  val io: CoreIo = CoreIo()
  io.bus.cmd.asSlave()
  io.bus.rsp.asMaster()
  io.bus.ack.asOutput()
  io.instrRsp.asSlave()
  io.dataBus.asMaster()

  // === Sub-components ===
  private val decoder = Decoder()
  private val regFile = RegFile()
  private val alu = ALU()
  private val busIf = BusInterface()

  io.bus.cmd >> busIf.io.cmd
  busIf.io.rsp >> io.bus.rsp
  io.bus.ack := busIf.io.ack

  // =========================================================================
  // PC
  // =========================================================================
  private val pc = Reg(UInt(16 bits)) init 0
  io.instrAddr := pc

  // =========================================================================
  // Pipeline Registers — IF/ID
  // =========================================================================
  private val rID_instr = Reg(Bits(16 bits))
  private val rID_pc = Reg(UInt(16 bits))
  private val rID_ldiData = Reg(Bits(16 bits))

  // =========================================================================
  // Pipeline Registers — ID/EX
  // =========================================================================
  private val rEX_instr = Reg(Bits(16 bits))
  private val rEX_pc = Reg(UInt(16 bits))
  private val rEX_rd = Reg(UInt(3 bits))
  private val rEX_hasRd = Reg(Bool()) init False
  private val rEX_rsVal = Reg(Bits(16 bits))
  private val rEX_rtVal = Reg(Bits(16 bits))
  private val rEX_sext = Reg(Bits(16 bits))
  private val rEX_effAddr = Reg(Bits(16 bits))
  private val rEX_brTarget = Reg(Bits(16 bits))
  private val rEX_ldiData = Reg(Bits(16 bits))

  private val rEX_type = Reg(InstrType()) init InstrType.EMPTY
  private val rEX_aluFunc = Reg(Bits(3 bits))
  private val rEX_brTaken = Reg(Bool()) init False
  private val rEX_jmpTarget = Reg(Bits(16 bits))

  private val rEX_isBEQ = rEX_instr(15 downto 12) === B"1100"
  private val rEX_isBNE = rEX_instr(15 downto 12) === B"1101"
  private val rEX_isBLT = rEX_instr(15 downto 12) === B"1110"

  // =========================================================================
  // Pipeline Registers — EX/WB
  // =========================================================================
  private val rWB_rd = Reg(UInt(3 bits))
  private val rWB_hasRd = Reg(Bool()) init False
  private val rWB_result = Reg(Bits(16 bits))

  // =========================================================================
  // Pipeline Valid Bits
  // =========================================================================
  private val vID = Reg(Bool()) init False
  private val vWB = Reg(Bool()) init False

  // =========================================================================
  // Decoder (combinational from rID_instr)
  // =========================================================================
  decoder.io.instr := rID_instr

  // =========================================================================
  // Register File Read Ports
  // =========================================================================
  private val idRsAddr = ((decoder.io.isBranch || decoder.io.isJMP) ?
    decoder.io.instr(11 downto 9).asBits | decoder.io.rsReg.asBits).asUInt
  private val idRtAddr = (decoder.io.isST ?
    decoder.io.rdField.asBits |
    (decoder.io.isBranch ? decoder.io.instr(8 downto 6).asBits | decoder.io.rtReg.asBits)).asUInt

  regFile.io.rsAddr := idRsAddr
  regFile.io.rtAddr := idRtAddr

  // =========================================================================
  // LD State Machine
  // =========================================================================
  private val ldRd = Reg(UInt(3 bits))
  private val ldData = Reg(Bits(16 bits))
  private val ldState = Reg(LdPhase()) init LdPhase.IDLE
  private val ldWbVld = Reg(Bool()) init False // valid flag for LD writeback, decoupled from rEX_type
  private val ldPending = ldState === LdPhase.WAIT_BUS
  private val ldRspPending = ldState === LdPhase.DATA_READY
  // Accept response when waiting (WAIT_BUS) or when req fires and response
  // arrives in the same cycle (async RAM read).  Without the second
  // condition, the combinational response from async RAM would be lost
  // because ldPending is only set one cycle after req fires.
  io.dataBus.rsp.ready := ldPending || (rEX_type === InstrType.LD && ldState === LdPhase.IDLE && io.dataBus.req.fire)

  // Forwarding from WB stage (used by both ID address and EX ALU forwarding)
  private val fwdFromWb = vWB && rWB_hasRd && rWB_rd =/= 0

  // =========================================================================
  // ID-stage Address Computations
  // =========================================================================
  private val idSext = decoder.io.imm6.asSInt.resize(16).asBits
  // Forwarding for address computation: LD/ST use rs+imm6, but rs may be
  // updated by the instruction currently in EX (ALU/LDI) or WB/LD.
  private val idFwdRsVal = Mux(rEX_type === InstrType.ALU && rEX_hasRd && rEX_rd === idRsAddr && rEX_rd =/= 0,
    alu.io.result,
    Mux(rEX_type === InstrType.LDI && rEX_hasRd && rEX_rd === idRsAddr && rEX_rd =/= 0,
      rEX_ldiData,
      Mux(ldRspPending && ldRd === idRsAddr && ldRd =/= 0,
        ldData,
        Mux(fwdFromWb && rWB_rd === idRsAddr,
          rWB_result,
          regFile.io.rsVal))))
  private val idFwdRtVal = Mux(rEX_type === InstrType.ALU && rEX_hasRd && rEX_rd === idRtAddr && rEX_rd =/= 0,
    alu.io.result,
    Mux(rEX_type === InstrType.LDI && rEX_hasRd && rEX_rd === idRtAddr && rEX_rd =/= 0,
      rEX_ldiData,
      Mux(ldRspPending && ldRd === idRtAddr && ldRd =/= 0,
        ldData,
        Mux(fwdFromWb && rWB_rd === idRtAddr,
          rWB_result,
          regFile.io.rtVal))))
  private val idEffAddr = (idFwdRsVal.asSInt + idSext.asSInt).asBits.resized
  private val idBrShifted = idSext(14 downto 0) ## B"0"
  private val idBrTarget = (rID_pc.asSInt + 2 + idBrShifted.asSInt).asBits.resized

  // Pre-compute branch condition in ID to shorten EX critical path.
  // Uses idFwdRsVal/idFwdRtVal which include all forwarding (ALU, LDI,
  // LD data in DATA_READY, WB).  loadUseHazard stalls the branch in ID
  // if its operands depend on a pending LD, guaranteeing data settles
  // before EX.  Stored as rEX_brTaken in the ID→EX transfer.
  private val idBrTaken =
    decoder.io.isJMP ||
    (decoder.io.isBEQ && (idFwdRsVal === idFwdRtVal)) ||
    (decoder.io.isBNE && (idFwdRsVal =/= idFwdRtVal)) ||
    (decoder.io.isBLT && (idFwdRsVal.asSInt < idFwdRtVal.asSInt))

  // Pre-compute JMP target in ID to remove forwarding mux from EX
  // critical path.  Stored as rEX_jmpTarget in the ID→EX transfer.
  private val idJmpTarget = idFwdRsVal

  // =========================================================================
  // EX-stage register addresses (for forwarding)
  // =========================================================================
  private val exRsAddr = ((rEX_type === InstrType.BR || rEX_type === InstrType.JMP) ?
    rEX_instr(11 downto 9).asBits | rEX_instr(8 downto 6).asBits).asUInt
  private val exRtAddr = Mux(rEX_type === InstrType.ST, rEX_instr(11 downto 9).asUInt,
    Mux(rEX_type === InstrType.BR, rEX_instr(8 downto 6).asUInt, rEX_instr(5 downto 3).asUInt))

  // =========================================================================
  // LD State Machine — Transitions
  // =========================================================================
  switch(ldState) {
    is(LdPhase.IDLE) {
      when(rEX_type === InstrType.LD && io.dataBus.req.fire) {
        ldRd := rEX_rd
        ldWbVld := True
        when(io.dataBus.rsp.fire) {
          // Async RAM: response available same cycle as request — skip WAIT_BUS
          ldData := io.dataBus.rsp.payload
          ldState := LdPhase.DATA_READY
        } otherwise {
          // Sync RAM or UART: wait for response in WAIT_BUS
          ldState := LdPhase.WAIT_BUS
        }
      }
    }
    is(LdPhase.WAIT_BUS) {
      when(io.dataBus.rsp.fire) {
        ldData := io.dataBus.rsp.payload
        ldState := LdPhase.DATA_READY
      }
    }
    is(LdPhase.DATA_READY) {
      ldState := LdPhase.IDLE
      rEX_type := InstrType.EMPTY
    }
  }

  // =========================================================================
  // Stall and Flush Logic
  // =========================================================================

  // Load-use hazard: EX has LD that writes a register needed by ID
  private val idNeedsRs = vID && !decoder.io.isLDI
  private val idNeedsRt = vID && (decoder.io.isALU || decoder.io.isST || decoder.io.isBranch) && !decoder.io.isImmEn
  private val loadUseHazard = rEX_type === InstrType.LD && !ldRspPending && rEX_hasRd && rEX_rd =/= 0 &&
    ((idNeedsRs && idRsAddr === rEX_rd) || (idNeedsRt && idRtAddr === rEX_rd))
  private val ldWaitStall = rEX_type === InstrType.LD && ldPending && !ldRspPending
  // Stall a new LD from entering EX while a previous LD's data is in
  // DATA_READY.  Without this, ID→EX fires in the same cycle as the
  // DATA_READY→IDLE transition, and the new LD's req.fire hits after
  // the writeback but before the regfile update — safe for forwarding,
  // but the next instruction (if not LD) could see the old LD lingering.
  private val ldRespNow = ldState === LdPhase.WAIT_BUS && io.dataBus.rsp.fire
  private val ldActiveStall = (ldRspPending || ldRespNow) && vID && decoder.io.isLD
  // Stall IF when a branch/JMP is in ID — prevents speculative fetch of
  // sequential instructions that would become stale if the branch is taken.
  // NOTE: Removed for predict-not-taken optimization.  IF now speculatively
  // fetches the sequential instruction while branch is in ID.  If the branch
  // is taken, exBrTaken flushes the speculative instruction and redirects PC.
  // This reduces branch penalty from 2 cycles to 1 for taken branches,
  // and eliminates the penalty entirely for not-taken branches.
  //private val stallBrId = vID && (decoder.io.isBranch || decoder.io.isJMP)
  private val stallLdiId = vID && decoder.io.isLDI
  private val stallID = loadUseHazard || ldWaitStall || ldActiveStall
  private val stallIF = stallID || stallLdiId

  // =========================================================================
  // Forwarding and Branch Detection
  // =========================================================================

  // Self-forwarding guard: when the same instruction is stalled in EX,
  // EX→WB keeps writing its result to rWB each cycle.  Without this guard,
  // the WB→EX forwarding feeds the ALU input and creates an arithmetic
  // loop (e.g. ADDI R7,R7,#2 would add 2 every stall cycle).
  private val exFwdRsVal = Mux(fwdFromWb && rWB_rd === exRsAddr && !(rEX_hasRd && rWB_rd === rEX_rd),
    rWB_result,
    Mux(ldRspPending && ldRd === exRsAddr && ldRd =/= 0,
      ldData,
      rEX_rsVal))
  private val exFwdRtVal = Mux(fwdFromWb && rWB_rd === exRtAddr && !(rEX_hasRd && rWB_rd === rEX_rd),
    rWB_result,
    Mux(ldRspPending && ldRd === exRtAddr && ldRd =/= 0,
      ldData,
      rEX_rtVal))

  // Branch taken — pre-computed in ID stage, stored as rEX_brTaken.
  // This removes the 16-bit comparator and forwarding mux from the EX
  // critical path.  The result is identical to re-computing from
  // exFwdRsVal/exFwdRtVal because loadUseHazard stalls the branch in ID
  // if its operands depend on a pending LD, and all other forwarding
  // sources (ALU, LDI, WB) are settled before ID→EX fires.
  private val exBrTaken = rEX_brTaken

  // =========================================================================
  // IF Stage — Instruction Fetch
  // =========================================================================
  private val ldiPending = Reg(Bool()) init False
  private val ldiHeader = Reg(Bits(16 bits))
  private val ldiHeaderPc = Reg(UInt(16 bits))

  io.instrRsp.ready := !stallIF

  private val instrIsLDI = io.instrRsp.payload(15 downto 12) === B"1111"

  when(io.instrRsp.fire) {
    // Guard against stale bus responses arriving after a branch redirects
    // PC.  When exBrTaken is True the response is for the sequential address
    // (already in flight before the branch) and must be discarded.
    when(!exBrTaken) {
      when(instrIsLDI && !ldiPending) {
        ldiPending := True
        ldiHeader := io.instrRsp.payload
        ldiHeaderPc := pc
        pc := pc + 2
        vID := False
      } otherwise {
        when(ldiPending) {
          rID_instr := ldiHeader
          rID_pc := ldiHeaderPc
          rID_ldiData := io.instrRsp.payload
          vID := True
          ldiPending := False
          pc := pc + 2
        } otherwise {
          rID_instr := io.instrRsp.payload
          rID_pc := pc
          rID_ldiData := 0
          vID := True
          pc := pc + 2
        }
      }
    } otherwise {
      // Stale response after branch: discard, clear stale LDI state.
      ldiPending := False
    }
  } otherwise {
    when(!stallIF && !ldiPending) {
      vID := False
    }
  }

  when(exBrTaken) {
    ldiPending := False
  }

  // =========================================================================
  // EX Stage — ALU, Branch, LD/ST
  // =========================================================================
  // NOTE: All EX stage logic is combinational (no := assignments), so it
  // always uses the CURRENT rEX_* values regardless of ordering.

  // Immediate enable for ALU operands (computed from opcode, see ISA.md §3.2)
  private val exIsImmEn = rEX_instr(15 downto 12) === 0x01 || rEX_instr(15 downto 12) === 0x03

  alu.io.rsVal := exFwdRsVal
  alu.io.opB := Mux(exIsImmEn, rEX_sext, exFwdRtVal)
  alu.io.aluFunc := rEX_aluFunc

  // EX result (for non-LD/ST)
  private val exResult = Mux(rEX_type === InstrType.LDI, rEX_ldiData,
    Mux(rEX_type === InstrType.ALU || exIsImmEn, alu.io.result, B(0, 16 bits)))

  // Data bus request (combinational).
  io.dataBus.req.payload.addr := rEX_effAddr.asUInt
  io.dataBus.req.payload.wrData := exFwdRtVal
  io.dataBus.req.payload.wr := rEX_type === InstrType.ST
  io.dataBus.req.valid :=
    (rEX_type === InstrType.LD && !ldRspPending) || rEX_type === InstrType.ST

  // =========================================================================
  // EX → WB Transfer — MUST come BEFORE ID→EX so it sees the OLD rEX_* values
  // =========================================================================

  // Default: no new WB data
  vWB := False

  // Non-LD/ST: transfer every cycle (no stall guard — ID→EX stalls handle
  // load-use hazards; general RAW hazards are resolved via forwarding).
  when(rEX_type =/= InstrType.EMPTY && rEX_type =/= InstrType.LD && rEX_type =/= InstrType.ST && !exBrTaken) {
    rWB_rd := rEX_rd
    rWB_hasRd := rEX_hasRd
    rWB_result := exResult
    vWB := True
  }

  // LD: direct write to regfile when FSM is in DATA_READY state.
  // Uses a dedicated path (bypasses rWB) to avoid writeback conflicts
  // with non-LD instructions that may be in EX concurrently.
  // Guard with ldWbVld (not rEX_type === LD) because ID→EX may have
  // overwritten rEX_type before the bus response arrives (no-data-hazard
  // case: the next instruction doesn't read the loaded register).
  // ldWbVld is NOT cleared on exBrTaken — a LD can only reach EX after the
  // branch resolves (ID→EX gated by !exBrTaken), so no speculative LD
  // writeback needs suppression.  Clearing ldWbVld on exBrTaken kills
  // legitimate LD+JMP sequences (e.g. __mul16 epilogue).
  private val ldWbFiring = ldRspPending && ldWbVld
  when(ldWbFiring) {
    ldWbVld := False
  }

  // =========================================================================
  // Branch target update (uses pre-clear rEX_type for JMP vs BR)
  // =========================================================================
  when(exBrTaken) {
    pc := Mux(rEX_type === InstrType.JMP, rEX_jmpTarget.asUInt, rEX_brTarget.asUInt)
    vID := False
    ldiPending := False
  }

  // =========================================================================
  // ID → EX Transfer — AFTER EX→WB so it doesn't corrupt writeback values
  // =========================================================================
  private val idType = InstrType()
  idType := InstrType.ALU
  when(decoder.io.isLD) { idType := InstrType.LD }
  when(decoder.io.isST) { idType := InstrType.ST }
  when(decoder.io.isJMP) { idType := InstrType.JMP }
  when(decoder.io.isBranch) { idType := InstrType.BR }
  when(decoder.io.isLDI) { idType := InstrType.LDI }

  // Clear rEX_type on branch (prevents stale EX→WB after flush)
  // Also clear rEX_brTaken — with the pre-computed branch condition now
  // stored in a register, we must reset it to avoid re-triggering the
  // branch PC update every cycle (the old combinational formula would
  // have automatically resolved to False once rEX_type was EMPTY).
  when(exBrTaken) {
    rEX_type := InstrType.EMPTY
    ldiPending := False
    rEX_brTaken := False
  }

  // Normal ID→EX transfer (when no stall/flush).  vID is used in an inner
  // when (not a Mux) to avoid the stallBrId→vClr race: if vClr clears vID
  // before rEX_type is assigned in the same cycle, a Mux would see vID=0
  // and kill the branch/JMP transfer.  By nesting, we enter the outer when
  // unconditionally (for the no-stall/no-flush case) and only gate the
  // actual transfer on vID.
  when(!stallID && !exBrTaken) {
    when(vID) {
      // Clear vID when IF→ID is not also firing (otherwise the new
      // instruction from IF would be lost).
      when(!io.instrRsp.fire) { vID := False }
      rEX_instr := rID_instr
      rEX_pc := rID_pc
      rEX_rd := (decoder.io.hasRd ? decoder.io.rdField | B"000").asUInt
      rEX_hasRd := decoder.io.hasRd
      rEX_rsVal := idFwdRsVal
      rEX_rtVal := idFwdRtVal
      rEX_sext := idSext
      rEX_effAddr := idEffAddr
      rEX_brTarget := idBrTarget
      rEX_ldiData := rID_ldiData

      rEX_aluFunc := decoder.io.aluFunc
      rEX_type := idType
      rEX_brTaken := idBrTaken
      rEX_jmpTarget := idJmpTarget
    } otherwise {
      // Clear rEX_type when ID is empty (vID=0).  Without this, a stale
      // LD/ST lingering in EX would re-trigger its bus request every cycle
      // (io.dataBus.req.valid is combinational from rEX_type), causing
      // duplicate bus transactions.
      rEX_type := InstrType.EMPTY
    }
  }

  // =========================================================================
  // WB Stage — Register File Write (with LD direct-write bypass)
  // =========================================================================
  regFile.io.wrAddr := Mux(ldWbFiring && ldRd =/= 0, ldRd, rWB_rd)
  regFile.io.wrData := Mux(ldWbFiring && ldRd =/= 0, ldData, rWB_result)
  regFile.io.wrEn := (ldWbFiring && ldRd =/= 0) || (vWB && rWB_hasRd && rWB_rd =/= 0)

  // =========================================================================
  // Debug Bus
  // =========================================================================

  private val resetn = ClockDomain.current.readResetWire

  regFile.io.auxAddr := Mux(
    busIf.io.cmdStrb && busIf.io.cmdWord(31 downto 24) === 0x06,
    busIf.io.cmdWord(18 downto 16).asUInt,
    U"001"
  )
  io.dbgRegFile1 := regFile.io.auxVal

  busIf.io.cmdDone := False
  busIf.io.rspStrb := False
  busIf.io.rspWord := 0

  private def flsPipeline(): Unit = {
    ldiPending := False; vID := False
    rEX_type := InstrType.EMPTY; vWB := False
    busIf.io.cmdDone := True
  }

  when(busIf.io.cmdStrb) {
    switch(busIf.io.cmdWord(31 downto 24)) {
      is(0x03) { flsPipeline() }
      is(0x04) { flsPipeline() }
      is(0x05) { flsPipeline() }
      is(0x06) {
        busIf.io.rspWord := B(0, 16 bits) ## regFile.io.auxVal
        busIf.io.rspStrb := True
      }
      is(0x08) {
        busIf.io.rspWord := B(0, 16 bits) ## pc.asBits
        busIf.io.rspStrb := True
      }
    }
  }

  // =========================================================================
  // Debug Outputs
  // =========================================================================
  io.dbgRunning := True
  io.dbgState := Mux(resetn, B"001", B"000")
  io.dbgPC := pc
  io.dbgRd := rEX_rd
  io.dbgAluRes := alu.io.result
  io.dbgInstr := rEX_instr
  io.dbgCmdPhrase := busIf.io.cmdStrb
  io.dbgRspPhrase := busIf.io.rsp.fire
  io.dbgBusWordFire := busIf.io.cmdStrb
  io.dbgCmdBuf := busIf.io.cmdWord
  io.dbgRspBuf := busIf.io.rspWord

  override def bus(): CoreBusIo = io.bus

  // =========================================================================
  // Formal Verification
  // =========================================================================
  GenerationFlags.formal {
    val resetn = ClockDomain.current.readResetWire
    assumeInitial(!resetn)
    assumeInitial(!pastValid())
    assumeInitial(pc === 0)
    assumeInitial(!vID)
    assumeInitial(rEX_type === InstrType.EMPTY)
    assumeInitial(!vWB)
    assumeInitial(!ldiPending)

    // ======================================================================
    // Reset invariants
    // ======================================================================
    when(!pastValid()) {
      when(!resetn) { assert(pc === 0) }
    }

    // ======================================================================
    // PC progression — ISA.md §4.3
    // ======================================================================
    when(pastValid() && resetn) {
      when(past(vID) && !past(exBrTaken) && !past(stallIF)) {
        assert(pc === past(pc) + 2 || pc === past(pc))
      }
    }

    // ======================================================================
    // R0 reads always return 0
    // ======================================================================
    when(regFile.io.rsAddr === 0) { assert(regFile.io.rsVal === 0) }
    when(regFile.io.rtAddr === 0) { assert(regFile.io.rtVal === 0) }

    // ======================================================================
    // Register file never writes to R0
    // ======================================================================
    when(regFile.io.wrEn) { assert(regFile.io.wrAddr =/= 0) }

    // ======================================================================
    // Data bus request properties
    // ======================================================================
    when(rEX_type === InstrType.ST) {
      assert(io.dataBus.req.valid)
      assert(io.dataBus.req.payload.wr)
    }
    // LD req.valid is gated by !ldRspPending (fires only when FSM is not DATA_READY)
    when(rEX_type === InstrType.LD && !ldRspPending) {
      assert(io.dataBus.req.valid)
      assert(!io.dataBus.req.payload.wr)
    }

    // ======================================================================
    // EX result — LDI uses ldiData, not ALU
    // ======================================================================
    when(rEX_type === InstrType.LDI) {
      assert(exResult === rEX_ldiData)
    }

    // ======================================================================
    // Pipeline progression: ID → EX
    // ======================================================================
    when(pastValid() && resetn) {
      when(past(vID) && !past(stallID) && !past(exBrTaken)) {
        assert(rEX_type =/= InstrType.EMPTY)
      }
    }

    // ======================================================================
    // Pipeline progression: EX → WB (non-LD/ST, EX unchanged by stall)
    // ======================================================================
    when(pastValid() && resetn) {
      when(past(rEX_type) =/= InstrType.EMPTY &&
           past(rEX_type) =/= InstrType.LD &&
           past(rEX_type) =/= InstrType.ST &&
           !past(exBrTaken) && stallID) {
        assert(vWB)
      }
    }

    // ======================================================================
    // LD state machine properties
    // ======================================================================
    when(rEX_type === InstrType.LD && !ldRspPending) { assert(io.dataBus.req.valid) }

    // ldData captures the payload when bus response fires
    when(pastValid() && resetn) {
      when(past(io.dataBus.rsp.fire)) {
        assert(ldData === past(io.dataBus.rsp.payload))
      }
    }

    // ldRspPending && ldWbVld (LD data ready and valid) triggers regfile write
    // in the same cycle (direct write path bypassing rWB), unless ldRd is 0
    // (R0 writes are silently ignored per ISA §1).
    when(ldRspPending && ldWbVld && ldRd =/= 0) {
      assert(regFile.io.wrEn)
      assert(regFile.io.wrAddr === ldRd)
      assert(regFile.io.wrData === ldData)
    }

    // ======================================================================
    // Instruction-Specific Correctness — ISA §3 / §4
    // ======================================================================

    // == ALU R-type (ADD, XOR, SUB, AND, OR, SLL, SRL): correct operands  ==
    when(rEX_type === InstrType.ALU) {
      assert(alu.io.rsVal === exFwdRsVal)
      assert(alu.io.aluFunc === rEX_aluFunc)
      assert(exResult === alu.io.result)
      assert(rEX_hasRd)
      when(!exIsImmEn) { assert(alu.io.opB === exFwdRtVal) }
    }

    // == Immediate ALU (ADDI / XORI, ISA §3.2): opB uses sign-extended imm ==
    when(exIsImmEn && rEX_type === InstrType.ALU) {
      assert(alu.io.opB === rEX_sext)
      assert(exResult === alu.io.result)
      assert(rEX_hasRd)
    }

    // == LDI (ISA §3.6 / §4.8): result is the second word from instruction ==
    when(rEX_type === InstrType.LDI) {
      assert(exResult === rEX_ldiData)
      assert(rEX_hasRd)
    }

    // == LD (ISA §3.3 / §4.9): data bus read request with correct address  ==
    // req.valid is gated by !ldRspPending (fires only when FSM is not DATA_READY).
    when(rEX_type === InstrType.LD && !ldRspPending) {
      assert(io.dataBus.req.valid)
      assert(!io.dataBus.req.payload.wr)
      assert(io.dataBus.req.payload.addr === rEX_effAddr.asUInt)
      assert(rEX_hasRd)
    }

    // == ST (ISA §3.3 / §4.10): data bus write with correct addr + data   ==
    when(rEX_type === InstrType.ST) {
      assert(io.dataBus.req.valid)
      assert(io.dataBus.req.payload.wr)
      assert(io.dataBus.req.payload.addr === rEX_effAddr.asUInt)
      assert(io.dataBus.req.payload.wrData === exFwdRtVal)
      assert(!rEX_hasRd)
    }

    // == JMP (ISA §3.4 / §4.11): unconditional, no rd                     ==
    when(pastValid() && resetn) {
      when(rEX_type === InstrType.JMP) {
        assert(exBrTaken)
        assert(!rEX_hasRd)
      }
    }

    // == Branch Condition Correctness (ISA §3.5 / §4.12–4.14)             ==
    when(pastValid() && resetn) {
      when(rEX_type === InstrType.BR) {
        when(rEX_isBEQ) { assert(exBrTaken === (exFwdRsVal === exFwdRtVal)) }
        when(rEX_isBNE) { assert(exBrTaken === (exFwdRsVal =/= exFwdRtVal)) }
        when(rEX_isBLT) { assert(exBrTaken === (exFwdRsVal.asSInt < exFwdRtVal.asSInt)) }
      }
    }

    // ======================================================================
    // Temporal: EX → WB Transfer (non-LD/ST)
    // ======================================================================
    when(pastValid() && resetn) {
      when(past(rEX_type) =/= InstrType.EMPTY &&
           past(rEX_type) =/= InstrType.LD &&
           past(rEX_type) =/= InstrType.ST &&
           !past(exBrTaken)) {
        assert(vWB)
        assert(rWB_rd === past(rEX_rd))
        assert(rWB_hasRd === past(rEX_hasRd))
        assert(rWB_result === past(exResult))
      }
    }

    // ======================================================================
    // Temporal: WB → Register File Write
    // ======================================================================
    when(vWB && rWB_hasRd) {
      assert(regFile.io.wrEn === ((rWB_rd =/= 0) || (ldWbFiring && ldRd =/= 0)))
      assert(regFile.io.wrAddr === Mux(ldWbFiring && ldRd =/= 0, ldRd, rWB_rd))
      assert(regFile.io.wrData === Mux(ldWbFiring && ldRd =/= 0, ldData, rWB_result))
    }

    // ======================================================================
    // Temporal: PC Update After Branch / Jump
    // ======================================================================
    when(pastValid() && resetn) {
      when(past(exBrTaken)) {
        when(past(rEX_type) === InstrType.JMP) {
          assert(pc === past(exFwdRsVal).asUInt)
        }
        when(past(rEX_type) === InstrType.BR) {
          assert(pc === past(rEX_brTarget).asUInt)
        }
      }
    }

    // ======================================================================
    // Temporal: Branch Taken Clears vID and Flushes Pipeline
    // ======================================================================
    when(pastValid() && resetn) {
      when(past(exBrTaken)) {
        assert(!vID)
      }
    }

    // ======================================================================
    // Temporal: LD State Machine — ldRd Captures rEX_rd on Request Fire
    // ======================================================================
    when(pastValid() && resetn) {
      when(past(rEX_type) === InstrType.LD && past(io.dataBus.req.fire)) {
        assert(ldRd === past(rEX_rd))
      }
    }

    // ======================================================================
    // Coverage
    // ======================================================================
    cover(rEX_type === InstrType.ALU)
    cover(rEX_type === InstrType.LD)
    cover(rEX_type === InstrType.LDI)
    cover(rEX_type === InstrType.ST)
    cover(rEX_type === InstrType.JMP)
    cover(rEX_type === InstrType.BR && exBrTaken)
    cover(rEX_type === InstrType.BR && !exBrTaken)
    cover(loadUseHazard)
    cover(ldWaitStall)
    cover(rEX_aluFunc === B"000")
    cover(rEX_aluFunc === B"001")
    cover(rEX_aluFunc === B"010")
    cover(rEX_aluFunc === B"011")
    cover(rEX_aluFunc === B"100")
    cover(rEX_aluFunc === B"101")
    cover(rEX_aluFunc === B"110")
    cover(rEX_isBEQ && exBrTaken)
    cover(rEX_isBNE && exBrTaken)
    cover(rEX_isBLT && exBrTaken)
    cover(vWB && rWB_hasRd && rWB_rd =/= 0)
    cover(io.bus.ack)
    cover(io.bus.rsp.valid)
    cover(io.dataBus.req.fire)
    cover(io.dataBus.rsp.fire)
    cover(rEX_type === InstrType.ST && io.dataBus.req.fire)
  }
}

object PipCore extends App {
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
  ).generateSystemVerilog(PipCore())
}
