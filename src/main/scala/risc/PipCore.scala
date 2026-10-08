package risc

import spinal.core._
import spinal.core.formal._
import spinal.lib._
// import spinal.lib.fsm._

import scala.language.postfixOps

object InstrType extends SpinalEnum {
  val EMPTY, ALU, LD, ST, JMP, BR, LDI, CALL = newElement()
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
  // Register-file port addresses latched TOGETHER WITH rID_instr (FPGA
  // timing): deriving them from rID_instr in ID put 2-3 decode/mux levels
  // in front of the regfile read and every forwarding/hazard compare —
  // the decode sat on the critical path into rEX_effAddr.  rsAddrOf/
  // rtAddrOf mirror Decoder's idRsAddr/idRtAddr equations exactly on the
  // incoming word (formally asserted below); for instructions that read
  // no register the value is unused (all consumers are class-gated).
  private val rID_rsAddr = Reg(UInt(3 bits)) init 0
  private val rID_rtAddr = Reg(UInt(3 bits)) init 0

  private def rsAddrOf(i: Bits): UInt = {
    val opc = i(15 downto 12)
    val cf = i(8 downto 6)
    val isBr = (opc === B"1100") || (opc === B"1101") || (opc === B"1110")
    val isJmp = (opc === B"1011") && (cf === B"000") && (i(5 downto 0) === 0)
    val isGb = (opc === B"1011") && (cf.asUInt >= 3)
    val isByte = (opc === B"1111") && !i(8) &&
      ((i(7 downto 6) === B"01") || (i(7 downto 6) === B"10"))
    Mux(isBr || isJmp || isGb, i(11 downto 9).asUInt,
      Mux(isByte, i(5 downto 3).asUInt, i(8 downto 6).asUInt))
  }
  private def rtAddrOf(i: Bits): UInt = {
    val opc = i(15 downto 12)
    val isSt = (opc === B"1010") ||
      ((opc === B"1111") && !i(8) && (i(7 downto 6) === B"10"))
    val isBr = (opc === B"1100") || (opc === B"1101") || (opc === B"1110")
    Mux(isSt, i(11 downto 9).asUInt,
      Mux(isBr, i(8 downto 6).asUInt, i(5 downto 3).asUInt))
  }

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
  private val rEX_aluFunc = Reg(Bits(4 bits))
  private val rEX_brTaken = Reg(Bool()) init False
  private val rEX_jmpTarget = Reg(Bits(16 bits))
  // v2.1 §4.6: byte-memory access (LDB/STB) — selects the byte lane on
  // writeback and sets DataBusReq.isByte (SoC masks the other lane).
  private val rEX_byte = Reg(Bool()) init False
  // Round-2 FPGA timing: register-file addresses and immediate-enable for
  // the EX instruction, latched together with rEX_instr in the ID→EX
  // transfer.  Re-deriving them from rEX_instr in EX (opcode / group-B /
  // byte decode, 2-3 levels) put that decode in front of the forwarding
  // compares feeding the ALU operand mux — the head of the
  // rEX_instr → ALU → rWB_result critical path.  The old equations are
  // asserted equivalent in the formal section (guarded by rEX_type =/=
  // EMPTY, the only state where the class flags are meaningful).
  private val rEX_rsAddr = Reg(UInt(3 bits)) init 0
  private val rEX_rtAddr = Reg(UInt(3 bits)) init 0
  private val rEX_immEn = Reg(Bool()) init False

  // v2.1 §5.4: HALT freezes fetch/decode; set when HALT leaves ID, cleared by
  // the debug/bus commands 0x03/0x04/0x05 (flsPipeline).
  private val halted = Reg(Bool()) init False

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
  // Branch: Rs in [11:9], Rt in [8:6]; JMP: Rs in [11:9];
  // ST/STB: store source in Rd field [11:9];
  // group-B immediates: Rd read in place from [11:9] (v2.1 §4.5);
  // LDB/STB: base register in [5:3] (v2.1 §4.6); others: [8:6]/[5:3].
  private val idIsByteMem = decoder.io.isLDB || decoder.io.isSTB
  private val idRsAddr = ((decoder.io.isBranch || decoder.io.isJMP ||
    decoder.io.isGrpBImm) ? decoder.io.instr(11 downto 9).asBits |
    (idIsByteMem ? decoder.io.instr(5 downto 3).asBits |
      decoder.io.rsReg.asBits)).asUInt
  private val idRtAddr = ((decoder.io.isST || decoder.io.isSTB) ?
    decoder.io.rdField.asBits |
    (decoder.io.isBranch ? decoder.io.instr(8 downto 6).asBits |
      decoder.io.rtReg.asBits)).asUInt

  regFile.io.rsAddr := rID_rsAddr
  regFile.io.rtAddr := rID_rtAddr

  // =========================================================================
  // LD State Machine
  // =========================================================================
  private val ldRd = Reg(UInt(3 bits))
  private val ldData = Reg(Bits(16 bits))
  private val ldState = Reg(LdPhase()) init LdPhase.IDLE
  private val ldWbVld = Reg(Bool()) init False // valid flag for LD writeback, decoupled from rEX_type
  private val ldPending = ldState === LdPhase.WAIT_BUS
  private val ldRspPending = ldState === LdPhase.DATA_READY
  // v2.1 §5.6: byte-load lane info — captured with ldRd so the WAIT_BUS
  // path can select the lane after rEX has moved on to another instruction.
  private val ldIsByte = Reg(Bool()) init False
  private val ldAddr0 = Reg(Bool()) init False
  // Store-issue handshake (FPGA timing): set when the ST request fires,
  // cleared when EX receives its next content.  The store therefore leaves
  // EX one cycle after fire, decided from this REGISTERED flag instead of
  // the combinational req.ready — that removes the
  //   rEX_effAddr → isIoAddr → ready → stStall → stallID → CE
  // cone that was the nextpnr critical path (addr decode + backpressure
  // no longer feed any clock-enable).
  private val stFired = Reg(Bool()) init False
  // Little-endian byte-lane select (v2.1 §2.2/§5.6): zero-extend the addressed
  // byte (addr[0] picks the upper lane), or pass the full word through.
  private def laneSel(word: Bits, isByte: Bool, a0: Bool): Bits =
    isByte ? (B(0, 8 bits) ## Mux(a0, word(15 downto 8), word(7 downto 0))) | word
  // Accept response when waiting (WAIT_BUS) or when req fires and response
  // arrives in the same cycle (async RAM read).  Without the second
  // condition, the combinational response from async RAM would be lost
  // because ldPending is only set one cycle after req fires.
  io.dataBus.rsp.ready := ldPending || (rEX_type === InstrType.LD && ldState === LdPhase.IDLE && io.dataBus.req.fire)

  // Forwarding from WB stage (used by both ID address and EX ALU forwarding).
  //
  // vWB/rWB_hasRd/rWB_rd are written ONLY by the EX→WB block below (default
  // vWB := False, set True inside the exWbFired condition) plus the debug
  // flush (flsPipeline forces vWB := False).  That combinational term
  //   vWB && rWB_hasRd && rWB_rd =/= 0
  // sits on every forwarding path and cost 2 LUT levels on the FPGA
  // critical path, so it is mirrored into a register here — evaluated from
  // the same source values, one cycle earlier, which is exactly what the
  // register update will hold.
  private val exWbFired = (rEX_type =/= InstrType.EMPTY && rEX_type =/= InstrType.LD &&
    rEX_type =/= InstrType.ST &&
    (!rEX_brTaken || rEX_type === InstrType.CALL))
  private val dbgFlush = busIf.io.cmdStrb &&
    ((busIf.io.cmdWord(31 downto 24) === 0x03) ||
      (busIf.io.cmdWord(31 downto 24) === 0x04) ||
      (busIf.io.cmdWord(31 downto 24) === 0x05))
  private val fwdFromWb = RegNext(
    exWbFired && rEX_hasRd && rEX_rd =/= 0 && !dbgFlush) init False

  // =========================================================================
  // ID-stage Address Computations
  // =========================================================================
  private val idSext = decoder.io.imm6.asSInt.resize(16).asBits
  // v2.1 §4.5: group-B immediates are ZERO-extended (masks/shift amounts are
  // unsigned); ADDI/XORI keep the v2.0 sign extension.  Stored as rEX_sext.
  private val idImm = decoder.io.immZext ?
    (B(0, 10 bits) ## decoder.io.imm6) | idSext
  // v2.1 §4.6: byte memory effective address = Rs + zext(off3) (0..7).
  private val idOffZext3 = B(0, 13 bits) ## decoder.io.instr(2 downto 0)

  // Forwarding priority for register reads in ID: EX (LDI only), LD data,
  // WB, regfile — pre-computed selects feeding one nested Mux per port.
  //
  // Timing (FPGA, 100 MHz target): the ALU result is deliberately NOT
  // forwarded into ID anymore.  The old path
  //   rWB_rd → exFwd compare → alu.io.result → idFwdExData → idFwdRsVal
  //     → idEffAddr adder → rEX_effAddr
  // was ~15 logic levels and dominated the nextpnr critical path (69.96 MHz
  // at 14.3 ns).  Only the LDI value remains, and it is the rEX_ldiData
  // REGISTER — zero logic depth.
  //
  // Consumers that need an ALU/CALL result *in this cycle* in ID — the
  // effective address (rs), the branch condition (rs, rt) and the JMP/CALL
  // target — are stalled one cycle by exIdFwdHazard until EX→WB has copied
  // the result into rWB, which idFwd*WbSel then forwards from a register.
  // All other consumers just latch the operand here; EX→WB always fires for
  // the predecessor, so exFwdRsVal/exFwdRtVal re-forward it from rWB_result
  // while they are themselves in EX.
  private val exLdiWritesRd = (rEX_type === InstrType.LDI) &&
    rEX_hasRd && rEX_rd =/= 0
  private val idFwdRsExSel = exLdiWritesRd && rEX_rd === rID_rsAddr
  private val idFwdRtExSel = exLdiWritesRd && rEX_rd === rID_rtAddr
  private val idFwdExData = rEX_ldiData
  private val idFwdRsLdSel = ldRspPending && ldRd === rID_rsAddr && ldRd =/= 0
  private val idFwdRtLdSel = ldRspPending && ldRd === rID_rtAddr && ldRd =/= 0
  private val idFwdRsWbSel = fwdFromWb && rWB_rd === rID_rsAddr
  private val idFwdRtWbSel = fwdFromWb && rWB_rd === rID_rtAddr

  private val idFwdRsVal = Mux(idFwdRsExSel, idFwdExData,
    Mux(idFwdRsLdSel, ldData,
      Mux(idFwdRsWbSel, rWB_result,
        regFile.io.rsVal)))
  private val idFwdRtVal = Mux(idFwdRtExSel, idFwdExData,
    Mux(idFwdRtLdSel, ldData,
      Mux(idFwdRtWbSel, rWB_result,
        regFile.io.rtVal)))
  // Single adder for both word and byte offsets: the immediate operand is
  // selected BEFORE the add (the old form built two parallel adders and
  // muxed their outputs, adding a level after the carry chain).  Both forms
  // are 16-bit modular addition, so the merged expression is bit-identical.
  private val idAddrImm = idIsByteMem ? idOffZext3 | idSext
  private val idEffAddr = (idFwdRsVal.asUInt + idAddrImm.asUInt).asBits
  private val idBrShifted = idSext(14 downto 0) ## B"0"
  private val idBrTarget = (rID_pc.asSInt + 2 + idBrShifted.asSInt).asBits.resized

  // Pre-compute branch condition in ID to shorten EX critical path.
  // Uses idFwdRsVal/idFwdRtVal, whose sources are all REGISTERS by now
  // (LDI result, LD data in DATA_READY, WB result, regfile).  Two stalls
  // guarantee the operands are settled before ID→EX fires:
  // loadUseHazard for a pending LD and exIdFwdHazard for an ALU/CALL
  // result still sitting in EX.  Stored as rEX_brTaken in the ID→EX
  // transfer.
  // v2.1 §5.4: CALL is taken unconditionally (flush + redirect like JMP).
  private val idBrTaken =
    decoder.io.isJMP ||
    decoder.io.isCALL ||
    (decoder.io.isBEQ && (idFwdRsVal === idFwdRtVal)) ||
    (decoder.io.isBNE && (idFwdRsVal =/= idFwdRtVal)) ||
    (decoder.io.isBLT && (idFwdRsVal.asSInt < idFwdRtVal.asSInt))

  // Pre-compute JMP/CALL target in ID to remove forwarding mux from EX
  // critical path.  Stored as rEX_jmpTarget in the ID→EX transfer.
  // JMP: target = R[Rs] (instr[11:9]); CALL: target = R[Rtarget] (instr[5:3],
  // read through the rt port — idRtAddr falls through to rtReg for CALL).
  private val idJmpTarget = Mux(decoder.io.isCALL, idFwdRtVal, idFwdRsVal)

  // =========================================================================
  // EX-stage register addresses (for forwarding)
  // =========================================================================
  // Delivered by registers (rEX_rsAddr/rEX_rtAddr) instead of being
  // re-derived from rEX_instr here — the re-derivation (opcode/group-B
  // compare + byte mux) was the head of the forwarding→ALU critical cone
  // and is now only elaborated for formal (exRsAddrRef/exRtAddrRef below,
  // inside GenerationFlags.formal).
  private val exIsGrpBImm = (rEX_instr(15 downto 12) === B"1011") &&
    (rEX_instr(8 downto 6).asUInt >= 3)
  private val exRsAddr = rEX_rsAddr
  private val exRtAddr = rEX_rtAddr

  // =========================================================================
  // LD State Machine — Transitions
  // =========================================================================
  switch(ldState) {
    is(LdPhase.IDLE) {
      when(rEX_type === InstrType.LD && io.dataBus.req.fire) {
        ldRd := rEX_rd
        ldWbVld := True
        // v2.1: remember byte-lane info for the WAIT_BUS capture path
        ldIsByte := rEX_byte
        ldAddr0 := rEX_effAddr(0)
        when(io.dataBus.rsp.fire) {
          // Async RAM: response available same cycle as request — skip WAIT_BUS
          ldData := laneSel(io.dataBus.rsp.payload, rEX_byte, rEX_effAddr(0))
          ldState := LdPhase.DATA_READY
        } otherwise {
          // Sync RAM or UART: wait for response in WAIT_BUS
          ldState := LdPhase.WAIT_BUS
        }
      }
    }
    is(LdPhase.WAIT_BUS) {
      when(io.dataBus.rsp.fire) {
        ldData := laneSel(io.dataBus.rsp.payload, ldIsByte, ldAddr0)
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
  // (LDB is an LD-type access and participates; LDI8 reads no registers.)
  private val idNeedsRs = vID && !decoder.io.isLDI && !decoder.io.isLDI8
  // rt port readers: register ALU ops (not the in-place group-B immediates,
  // whose [5:3] field is the immediate), stores (incl. STB), branches and
  // CALL (Rtarget in instr[5:3]).
  private val idNeedsRt = vID && (decoder.io.isALU || decoder.io.isST ||
    decoder.io.isSTB || decoder.io.isBranch || decoder.io.isCALL) &&
    !decoder.io.isImmEn && !decoder.io.isGrpBImm
  private val loadUseHazard = rEX_type === InstrType.LD && !ldRspPending && rEX_hasRd && rEX_rd =/= 0 &&
    ((idNeedsRs && rID_rsAddr === rEX_rd) || (idNeedsRt && rID_rtAddr === rEX_rd))
  private val ldWaitStall = rEX_type === InstrType.LD && ldPending && !ldRspPending
  // Stall a new LD from entering EX while a previous LD's data is in
  // DATA_READY.  Without this, ID→EX fires in the same cycle as the
  // DATA_READY→IDLE transition, and the new LD's req.fire hits after
  // the writeback but before the regfile update — safe for forwarding,
  // but the next instruction (if not LD) could see the old LD lingering.
  private val ldRespNow = ldState === LdPhase.WAIT_BUS && io.dataBus.rsp.fire
  private val ldActiveStall = (ldRspPending || ldRespNow) && vID &&
    (decoder.io.isLD || decoder.io.isLDB)
  // v2.1: hold the instruction in EX until its store request is accepted
  // (UART TX backpressure) and the acceptance is REGISTERED (stFired).
  // Without the hold, a ST/STB whose req.ready is low would be overwritten
  // by the next ID→EX transfer and its write lost; without stFired, the
  // combinational ready→stall path was the FPGA critical path.
  private val stStall = rEX_type === InstrType.ST && !stFired
  // Hold ID while an EX load has NOT yet captured its request into the LD
  // FSM (ldState still IDLE).  Without this, an adjacent instruction can
  // replace the EX load before req.fire: with a same-cycle response,
  // ldRspPending is still 0 during the issue cycle, so neither ldActiveStall
  // nor loadUseHazard stalls ID — the load's request is then never issued
  // and its destination register is never written (e.g. LDB;LDB back-to-
  // back).  Once ldState leaves IDLE the FSM owns the access (ldRd/ldWbVld/
  // ldIsByte captured) and EX may be overwritten safely.
  private val ldIssueStall = rEX_type === InstrType.LD &&
    ldState === LdPhase.IDLE
  // Stall IF when a branch/JMP is in ID — prevents speculative fetch of
  // sequential instructions that would become stale if the branch is taken.
  // NOTE: Removed for predict-not-taken optimization.  IF now speculatively
  // fetches the sequential instruction while branch is in ID.  If the branch
  // is taken, exBrTaken flushes the speculative instruction and redirects PC.
  // This reduces branch penalty from 2 cycles to 1 for taken branches,
  // and eliminates the penalty entirely for not-taken branches.
  //private val stallBrId = vID && (decoder.io.isBranch || decoder.io.isJMP)
  private val stallLdiId = vID && decoder.io.isLDI

  // =========================================================================
  // EX→ID result hazard (timing-critical reads only)
  // =========================================================================
  // ID uses these register reads COMBINATIONALLY in the same cycle and then
  // latches the result into EX, where it can no longer be re-forwarded:
  //   - effective address of LD/ST/LDB/STB   (rs)
  //   - branch condition BEQ/BNE/BLT         (rs and rt)
  //   - JMP / CALL target                    (rs / rt)
  // An ALU/CALL result sitting in EX is not forwarded into ID (see
  // idFwdExData — that path was the FPGA critical path), so stall ID for one
  // cycle until EX→WB has copied the result into rWB, where idFwd*WbSel
  // forwards it from a register.  LDI needs no stall: its value is already
  // a register (rEX_ldiData) and is forwarded above; LD producers are
  // covered by loadUseHazard.
  //
  // The stall is self-clearing and cannot deadlock: holding ID prevents
  // ID→EX, which retains the EX instruction, whose EX→WB transfer re-fires
  // every cycle (non-LD/ST, no flush) — so rWbHasExRes goes high exactly
  // one cycle later and clears the hazard.
  private val idIsMemOp = decoder.io.isLD || decoder.io.isLDB ||
    decoder.io.isST || decoder.io.isSTB
  private val idUsesRsInId = vID && (idIsMemOp || decoder.io.isBranch ||
    decoder.io.isJMP)
  private val idUsesRtInId = vID && (decoder.io.isBranch || decoder.io.isCALL)
  private val exIdFwdHazard = ((rEX_type === InstrType.ALU ||
    rEX_type === InstrType.CALL) && rEX_hasRd && rEX_rd =/= 0) &&
    ((idUsesRsInId && rEX_rd === rID_rsAddr) ||
      (idUsesRtInId && rEX_rd === rID_rtAddr))
  // "rWB currently holds the result of the instruction that is still sitting
  // in EX" (EX retained across the last edge while EX→WB re-wrote its own
  // result).  Declared here, assigned right after stallID: its D input
  // depends on stallID (the retention condition), which in turn needs this
  // register's Q — a register feedback loop, no combinational loop.
  private val rWbHasExRes = Reg(Bool()) init False

  private val stallID = loadUseHazard || ldWaitStall || ldActiveStall ||
    ldIssueStall || stStall || (exIdFwdHazard && !rWbHasExRes)
  private val stallIF = stallID || stallLdiId

  // EX retained its instruction iff the ID→EX block did not run (stallID or
  // halted) and nothing cleared it (exBrTaken, debug flush).  hasRd/rd/=0
  // are included so that rWbHasExRes ⇒ fwdFromWb: whenever the flag is set,
  // idFwd*WbSel is guaranteed to select rWB_result — which is what lets the
  // hazard above clear.
  rWbHasExRes := exWbFired && rEX_hasRd && rEX_rd =/= 0 &&
    !rEX_brTaken && (stallID || halted) && !dbgFlush

  // Store handshake register: clear on any ID→EX transfer (EX receives new
  // content or empties), set when the ST request fires.  Both can never
  // coincide: !stallID with ST in EX implies stFired=1, which forces
  // valid=0, so no fire can happen in a transfer cycle.
  // (Uses rEX_brTaken directly — its alias exBrTaken is declared below.)
  when(!stallID && !rEX_brTaken && !halted) {
    stFired := False
  }
  when(io.dataBus.req.fire && (rEX_type === InstrType.ST)) {
    stFired := True
  }

  // =========================================================================
  // Forwarding and Branch Detection
  // =========================================================================

  // Self-forwarding guard: when the same instruction is stalled in EX,
  // EX→WB keeps writing its result to rWB each cycle.  Forwarding that back
  // into the ALU input creates an arithmetic loop (e.g. ADDI R7,R7,#2 would
  // add 2 every stall cycle).  rWbHasExRes is the precise form of the old
  //  !(rEX_hasRd && rWB_rd === rEX_rd)  check: rWB can only ever hold the
  // current EX instruction's own result (EX retained while EX→WB re-fired)
  // or its direct predecessor.  Blocking only the first case is required —
  // since ID no longer forwards the EX ALU result, a *different* instruction
  // with the same rd in WB must be forwarded (the ID-latched operand may be
  // stale in exactly that case).
  private val exFwdRsVal = Mux(fwdFromWb && rWB_rd === exRsAddr && !rWbHasExRes,
    rWB_result,
    Mux(ldRspPending && ldRd === exRsAddr && ldRd =/= 0,
      ldData,
      rEX_rsVal))
  private val exFwdRtVal = Mux(fwdFromWb && rWB_rd === exRtAddr && !rWbHasExRes,
    rWB_result,
    Mux(ldRspPending && ldRd === exRtAddr && ldRd =/= 0,
      ldData,
      rEX_rtVal))

  // Branch taken — pre-computed in ID stage, stored as rEX_brTaken.
  // This removes the 16-bit comparator and forwarding mux from the EX
  // critical path.  The result is identical to re-computing from
  // exFwdRsVal/exFwdRtVal because loadUseHazard stalls the branch in ID
  // if its operands depend on a pending LD, exIdFwdHazard stalls it for an
  // ALU/CALL result still in EX, and the remaining sources (LDI register,
  // WB result, regfile) are settled before ID→EX fires.
  private val exBrTaken = rEX_brTaken

  // =========================================================================
  // IF Stage — Instruction Fetch
  // =========================================================================
  private val ldiPending = Reg(Bool()) init False
  private val ldiHeader = Reg(Bits(16 bits))
  private val ldiHeaderPc = Reg(UInt(16 bits))

  io.instrRsp.ready := !stallIF && !halted

  // v2.1 §9.4: only the exact v2.0 word-1 (b8=0, mf=00, payload=0) starts a
  // 2-word fetch.  LDI8/LDB/STB (and reserved group-F words) are 1-word.
  private val instrIsLDI = (io.instrRsp.payload(15 downto 12) === B"1111") &&
    !io.instrRsp.payload(8) &&
    (io.instrRsp.payload(7 downto 6) === B"00") &&
    (io.instrRsp.payload(5 downto 0) === B"000000")

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
          rID_rsAddr := rsAddrOf(ldiHeader)
          rID_rtAddr := rtAddrOf(ldiHeader)
          vID := True
          ldiPending := False
          pc := pc + 2
        } otherwise {
          rID_instr := io.instrRsp.payload
          rID_pc := pc
          rID_ldiData := 0
          rID_rsAddr := rsAddrOf(io.instrRsp.payload)
          rID_rtAddr := rtAddrOf(io.instrRsp.payload)
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

  // v2.1 §5.4: once HALT has left ID, drop any instruction IF delivered in
  // the same cycle (it is younger than HALT and must never execute).
  // Placed after the IF block so it overrides vID := True.
  when(halted) {
    vID := False
  }

  // =========================================================================
  // EX Stage — ALU, Branch, LD/ST
  // =========================================================================
  // NOTE: All EX stage logic is combinational (no := assignments), so it
  // always uses the CURRENT rEX_* values regardless of ordering.

  // Immediate enable for ALU operands (v2.1 §4.5: group-B immediates
  // ANDI/ORI/SLLI/SRLI/SRAI ride the same path with rEX_sext zero-extended).
  // Delivered by rEX_immEn (latched in ID→EX from decoder.io.isImmEn ||
  // decoder.io.isGrpBImm): the EX re-derivation (two opcode compares +
  // group-B compare) was the select path of the ALU opB mux on the same
  // critical cone as the forwarding compares.
  private val exIsImmEn = rEX_immEn

  alu.io.rsVal := exFwdRsVal
  alu.io.opB := Mux(exIsImmEn, rEX_sext, exFwdRtVal)
  alu.io.aluFunc := rEX_aluFunc

  // EX result (for non-LD/ST).  LDI (incl. 1-word LDI8) returns the piped
  // immediate; CALL returns PC_next (rEX_pc + 2, the link address).
  private val exResult = Mux(rEX_type === InstrType.LDI, rEX_ldiData,
    Mux(rEX_type === InstrType.CALL, (rEX_pc + 2).asBits,
      Mux(rEX_type === InstrType.ALU || exIsImmEn, alu.io.result, B(0, 16 bits))))

  // v2.1 §5.6: STB writes R[Rd][7:0] into the addressed byte lane
  // (little-endian: odd address → upper lane); the SoC masks the other lane.
  private val stByteData = Mux(rEX_effAddr(0),
    (exFwdRtVal(7 downto 0) ## B(0, 8 bits)),
    (B(0, 8 bits) ## exFwdRtVal(7 downto 0)))

  // Data bus request (combinational).
  io.dataBus.req.payload.addr := rEX_effAddr.asUInt
  io.dataBus.req.payload.wrData := (rEX_byte && (rEX_type === InstrType.ST)) ?
    stByteData | exFwdRtVal
  io.dataBus.req.payload.wr := rEX_type === InstrType.ST
  io.dataBus.req.payload.isByte := rEX_byte
  io.dataBus.req.valid :=
    (rEX_type === InstrType.LD && !ldRspPending) ||
      (rEX_type === InstrType.ST && !stFired)

  // =========================================================================
  // EX → WB Transfer — MUST come BEFORE ID→EX so it sees the OLD rEX_* values
  // =========================================================================

  // Default: no new WB data
  vWB := False

  // Non-LD/ST: transfer every cycle (no stall guard — ID→EX stalls handle
  // load-use hazards; general RAW hazards are resolved via forwarding).
  // v2.1: CALL resolves like a taken branch (flush + redirect) but must
  // still write its link value — allow its EX→WB transfer despite exBrTaken.
  when(rEX_type =/= InstrType.EMPTY && rEX_type =/= InstrType.LD &&
    rEX_type =/= InstrType.ST &&
    (!exBrTaken || rEX_type === InstrType.CALL)) {
    // Commit this instruction's result — UNLESS rWB already holds it, i.e.
    // EX is on a retained (stalled) cycle and this is a re-computation:
    //   rWbHasExRes ⇒ rWB_result = result of the FIRST cycle in EX.
    // Retention must not overwrite it.  The ID operand latch can be stale
    // for an ALU instruction whose predecessor writes its source (the EX
    // ALU result is no longer forwarded into ID — timing), so only the
    // first cycle in EX computes a correct exResult: EX forwarding feeds // the correct operand there and is blocked afterwards by the
    // rWbHasExRes self-forward guard.  Without this hold, the recomputed
    // garbage from the stale latch wins (ADDI R7,R7,#2 committed 1ff4
    // instead of 1ff8 while held by ldActiveStall, corrupting R7).
    // rWB_rd/rWB_hasRd are gated too — they already hold these exact
    // values during retention (same instruction), so the gate is a no-op
    // for them and keeps the whole commit atomic.
    when(!rWbHasExRes) {
      rWB_rd := rEX_rd
      rWB_hasRd := rEX_hasRd
      rWB_result := exResult
    }
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
  // Branch target update (uses pre-clear rEX_type for JMP/CALL vs BR)
  // =========================================================================
  when(exBrTaken) {
    pc := Mux(rEX_type === InstrType.JMP || rEX_type === InstrType.CALL,
      rEX_jmpTarget.asUInt, rEX_brTarget.asUInt)
    vID := False
    ldiPending := False
  }

  // =========================================================================
  // ID → EX Transfer — AFTER EX→WB so it doesn't corrupt writeback values
  // =========================================================================
  private val idType = InstrType()
  idType := InstrType.ALU
  when(decoder.io.isLD) { idType := InstrType.LD }
  when(decoder.io.isLDB) { idType := InstrType.LD }
  when(decoder.io.isST) { idType := InstrType.ST }
  when(decoder.io.isSTB) { idType := InstrType.ST }
  when(decoder.io.isJMP) { idType := InstrType.JMP }
  when(decoder.io.isBranch) { idType := InstrType.BR }
  when(decoder.io.isLDI) { idType := InstrType.LDI }
  // v2.1: LDI8 rides the LDI path (rEX_ldiData gets the zext8 immediate);
  // CALL is its own type (link write + taken-branch flush); HALT and
  // reserved encodings transfer as EMPTY (no architectural effect, §2.6).
  when(decoder.io.isLDI8) { idType := InstrType.LDI }
  when(decoder.io.isCALL) { idType := InstrType.CALL }
  when(decoder.io.isHALT) { idType := InstrType.EMPTY }
  when(decoder.io.isReserved) { idType := InstrType.EMPTY }

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
  when(!stallID && !exBrTaken && !halted) {
    when(vID) {
      // Clear vID when IF→ID is not also firing (otherwise the new
      // instruction from IF would be lost).
      when(!io.instrRsp.fire) { vID := False }
      // v2.1 §5.4: HALT freezes fetch/decode — set here (with an older
      // instruction still draining through EX/WB), gated above by
      // !stallID/!exBrTaken so a flushed speculative HALT never halts.
      when(decoder.io.isHALT) { halted := True }
      rEX_instr := rID_instr
      rEX_pc := rID_pc
      rEX_rd := (decoder.io.hasRd ? decoder.io.rdField | B"000").asUInt
      rEX_hasRd := decoder.io.hasRd
      rEX_rsVal := idFwdRsVal
      rEX_rtVal := idFwdRtVal
      rEX_sext := idImm
      rEX_effAddr := idEffAddr
      rEX_brTarget := idBrTarget
      // LDI8 (1-word): materialise zext8(imm8) as the "second word"
      rEX_ldiData := decoder.io.isLDI8 ?
        (B(0, 8 bits) ## rID_instr(7 downto 0)) | rID_ldiData
      rEX_byte := idIsByteMem
      // Round-2 timing: EX-stage addresses / immediate-enable ride along
      // with rEX_instr so EX never re-decodes them (see declarations).
      rEX_rsAddr := rID_rsAddr
      rEX_rtAddr := rID_rtAddr
      rEX_immEn := decoder.io.isImmEn || decoder.io.isGrpBImm

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
    // v2.1 §5.4: debug/bus commands exit HALT (reset 0x03, step 0x04, run 0x05)
    halted := False
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
  io.dbgRunning := !halted
  io.dbgState := Mux(!resetn, B"000", Mux(halted, B"101", B"001"))
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
    assumeInitial(!halted)
    assumeInitial(!rEX_byte)
    // LD FSM regs at hardware reset (needed for state-consistency asserts:
    // clk2fflogic does not apply init attrs to the frame-0 state)
    assumeInitial(ldState === LdPhase.IDLE)
    assumeInitial(!ldIsByte)
    assumeInitial(!ldAddr0)
    assumeInitial(!ldWbVld)
    // Store-handshake flag at reset (registered; clk2fflogic leaves frame-0 free)
    assumeInitial(!stFired)
    // IF/ID register-file addresses at reset (init 0 in hardware)
    assumeInitial(rID_rsAddr === 0)
    assumeInitial(rID_rtAddr === 0)

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
      // Store handshake: valid stays high until the request fires; after
      // fire (stFired) it must drop so the held cycle cannot re-issue.
      assert(io.dataBus.req.valid === !stFired)
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
      when(past(vID) && !past(stallID) && !past(exBrTaken) && !past(halted)) {
        // v2.1 §2.6/§5.4: reserved encodings and HALT transfer as EMPTY
        // (no architectural effect); everything else must occupy EX.
        assert(rEX_type =/= InstrType.EMPTY ||
          past(decoder.io.isReserved) || past(decoder.io.isHALT))
      }
    }

    // ======================================================================
    // IF/ID register-file address latch: must mirror the decoder-derived
    // mux of the SAME instruction word (latched together with rID_instr).
    // ======================================================================
    when(vID) {
      assert(rID_rsAddr === idRsAddr)
      assert(rID_rtAddr === idRtAddr)
    }

    // ======================================================================
    // Round-2: EX addresses / immediate-enable are the values latched in
    // ID→EX and must equal the OLD combinational decode of the same
    // instruction (exRsAddrRef/exRtAddrRef) — this proves the swap to
    // registered delivery is behaviour-preserving for forwarding, the ALU
    // opB mux and exResult.  Guard: frame 0 assumes rEX_type === EMPTY, and
    // rEX_type only becomes non-EMPTY via an ID→EX transfer, which writes
    // rEX_rsAddr/rEX_rtAddr/rEX_immEn in the same block as rEX_instr.
    // ======================================================================
    val exRsAddrRef = ((rEX_type === InstrType.BR || rEX_type === InstrType.JMP ||
      exIsGrpBImm) ? rEX_instr(11 downto 9).asBits |
      (rEX_byte ? rEX_instr(5 downto 3).asBits |
        rEX_instr(8 downto 6).asBits)).asUInt
    val exRtAddrRef = Mux(rEX_type === InstrType.ST, rEX_instr(11 downto 9).asUInt,
      Mux(rEX_type === InstrType.BR, rEX_instr(8 downto 6).asUInt, rEX_instr(5 downto 3).asUInt))
    when(rEX_type =/= InstrType.EMPTY) {
      assert(rEX_rsAddr === exRsAddrRef)
      assert(rEX_rtAddr === exRtAddrRef)
      assert(rEX_immEn === (rEX_instr(15 downto 12) === 0x01 ||
        rEX_instr(15 downto 12) === 0x03 || exIsGrpBImm))
    }

    // ======================================================================
    // Store handshake (stFired): set when the ST request fires, cleared by
    // any ID→EX transfer (EX receives new content); never both at once.
    // ======================================================================
    when(pastValid() && resetn) {
      when(past(rEX_type) === InstrType.ST && past(io.dataBus.req.fire)) {
        assert(stFired)
      }
      when(past(stFired) && !past(stallID) && !past(exBrTaken) && !past(halted)) {
        assert(!stFired)
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

    // ldData captures the payload when bus response fires — with the v2.1
    // byte-lane select applied for LDB (§5.6): zero-extended addressed lane.
    when(pastValid() && resetn) {
      when(past(io.dataBus.rsp.fire) && past(ldState) === LdPhase.IDLE) {
        assert(ldData === laneSel(past(io.dataBus.rsp.payload),
          past(rEX_byte), past(rEX_effAddr(0))))
      }
      when(past(io.dataBus.rsp.fire) && past(ldState) === LdPhase.WAIT_BUS) {
        assert(ldData === laneSel(past(io.dataBus.rsp.payload), ldIsByte, ldAddr0))
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
    // v2.1 §5.6: STB (rEX_byte) writes R[Rd][7:0] in the addressed lane.
    when(rEX_type === InstrType.ST) {
      assert(io.dataBus.req.valid === !stFired)
      assert(io.dataBus.req.payload.wr)
      assert(io.dataBus.req.payload.addr === rEX_effAddr.asUInt)
      assert(io.dataBus.req.payload.isByte === rEX_byte)
      when(rEX_byte) {
        assert(io.dataBus.req.payload.wrData === stByteData)
      } otherwise {
        assert(io.dataBus.req.payload.wrData === exFwdRtVal)
      }
      assert(!rEX_hasRd)
    }

    // == v2.1: byte flag rides along every memory request (§4.6) ==
    when(rEX_type === InstrType.LD && !ldRspPending) {
      assert(io.dataBus.req.payload.isByte === rEX_byte)
    }

    // == JMP (ISA §3.4 / §4.11): unconditional, no rd                     ==
    when(pastValid() && resetn) {
      when(rEX_type === InstrType.JMP) {
        assert(exBrTaken)
        assert(!rEX_hasRd)
      }
    }

    // == v2.1 §5.4: CALL — taken like JMP, writes the link address       ==
    when(pastValid() && resetn) {
      when(rEX_type === InstrType.CALL) {
        assert(exBrTaken)
        assert(rEX_hasRd)
        // link = address of the next instruction (PC_next, §5.4)
        assert(exResult === (rEX_pc + 2).asBits)
        assert(!io.dataBus.req.valid)
        // target rides the jmp mux (rt port, instr[5:3])
        assert(rEX_jmpTarget === exFwdRtVal)
      }
    }

    // == v2.1 §5.4: HALT never executes — HALT words transfer as EMPTY   ==
    when(pastValid() && resetn) {
      when(past(vID) && past(decoder.io.isHALT) && !past(stallID) &&
        !past(exBrTaken)) {
        assert(halted)
        assert(rEX_type === InstrType.EMPTY)
      }
    }

    // == v2.1 §5.6: LDI8 produces the zero-extended 8-bit immediate      ==
    when(rEX_type === InstrType.LDI &&
      (rEX_instr(15 downto 12) === B"1111") && rEX_instr(8)) {
      assert(rEX_ldiData(15 downto 8) === B"00000000")
      assert(rEX_ldiData(7 downto 0) === rEX_instr(7 downto 0))
    }

    // == v2.1 §5.5: group-B immediates — in-place, zero-extended         ==
    when(rEX_type === InstrType.ALU && exIsGrpBImm) {
      assert(exIsImmEn)
      assert(alu.io.opB === rEX_sext)
      assert(rEX_sext(15 downto 6) === B"0000000000")
      // operand A is R[Rd] read in place (exRsAddr selects instr[11:9])
      assert(exRsAddr === rEX_instr(11 downto 9).asUInt)
      assert(rEX_hasRd)
    }

    // == v2.1 §5.6: LDB — byte loads capture a zero-extended lane        ==
    when(ldRspPending && ldIsByte) {
      assert(ldData(15 downto 8) === B"00000000")
    }
    when(ldRspPending && ldWbVld && ldIsByte && ldRd =/= 0) {
      assert(regFile.io.wrData === ldData)
    }

    // == v2.1 §2.6: reserved encodings transfer as EMPTY (no writeback)  ==
    when(pastValid() && resetn) {
      when(past(vID) && past(decoder.io.isReserved) && !past(stallID) &&
        !past(exBrTaken) && !past(halted)) {
        assert(rEX_type === InstrType.EMPTY)
        assert(!rEX_hasRd)
      }
    }

    // == v2.1 §5.4: HALT freezes fetch and PC                            ==
    when(halted) {
      assert(!io.instrRsp.fire)
    }
    when(pastValid() && resetn && !past(resetn)) {
      when(past(halted) && !past(busIf.io.cmdStrb)) {
        assert(halted)
        assert(pc === past(pc))
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
        when(past(rEX_type) === InstrType.CALL) {
          // v2.1 §5.4: PC ← R[Rtarget] (target forwarded in ID, re-checked
          // against the EX rt-forward like JMP does for its rs-forward)
          assert(pc === past(exFwdRtVal).asUInt)
        }
        when(past(rEX_type) === InstrType.BR) {
          assert(pc === past(rEX_brTarget).asUInt)
        }
      }
    }

    // ======================================================================
    // Temporal: CALL EX → WB (link write) despite taken-branch flush
    // ======================================================================
    when(pastValid() && resetn) {
      when(past(rEX_type) === InstrType.CALL) {
        assert(vWB)
        assert(rWB_rd === past(rEX_rd))
        assert(rWB_hasRd)
        assert(rWB_result === past(exResult))
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
    cover(rEX_aluFunc === B"0000")
    cover(rEX_aluFunc === B"0001")
    cover(rEX_aluFunc === B"0010")
    cover(rEX_aluFunc === B"0011")
    cover(rEX_aluFunc === B"0100")
    cover(rEX_aluFunc === B"0101")
    cover(rEX_aluFunc === B"0110")
    cover(rEX_aluFunc === B"1000")
    cover(rEX_aluFunc === B"1001")
    cover(rEX_aluFunc === B"1010")
    cover(rEX_type === InstrType.CALL)
    cover(halted)
    cover(rEX_byte && rEX_type === InstrType.LD)
    cover(rEX_byte && rEX_type === InstrType.ST)
    cover(rEX_type === InstrType.ALU && exIsGrpBImm)
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
