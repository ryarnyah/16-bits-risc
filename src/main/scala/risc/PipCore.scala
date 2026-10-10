package risc

import spinal.core._
import spinal.core.formal._
import spinal.lib._
// import spinal.lib.fsm._

import scala.language.postfixOps

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
  // PC (+ next-state wire — task 3: hold-default here, overrides in file
  // order below, committed once in "State update"; reads see pre-edge)
  // =========================================================================
  private val pc = Reg(UInt(16 bits)) init 0
  private val nextPC = UInt(16 bits)
  nextPC := pc
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
  // the decode sat on the critical path into rEX_effAddr.  Both sides call
  // Isa.rsAddrOf/rtAddrOf on the same word (formally asserted below);
  // for instructions that read no register the value is unused (all
  // consumers are class-gated).
  private val rID_rsAddr = Reg(UInt(3 bits)) init 0
  private val rID_rtAddr = Reg(UInt(3 bits)) init 0

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
  private val nextREXtype = InstrType()
  nextREXtype := rEX_type
  private val rEX_aluFunc = Reg(Bits(4 bits))
  private val rEX_brTaken = Reg(Bool()) init False
  private val nextREXbrTaken = Bool()
  nextREXbrTaken := rEX_brTaken
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
  private val nextHalted = Bool()
  nextHalted := halted

  // v3.2: BR condition code rides in rEX_instr[8:7] (single-register
  // branch; no Rt).  Formal-only reference like exRsAddrRef below.
  private val rEX_brCC = rEX_instr(8 downto 7)

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
  private val nextVID = Bool()
  private val nextVWB = Bool()
  nextVID := vID
  // NOTE: no hold-default for nextVWB — vWB is a per-cycle pulse whose
  // unconditional default (False, below at the old site) is the default.

  // =========================================================================
  // Decoder (combinational from rID_instr)
  // =========================================================================
  decoder.io.instr := rID_instr

  // =========================================================================
  // Register File Read Ports
  // =========================================================================
  // v3.2 BR: single Rs in [11:9] (cond in [8:7], no Rt); JMP: Rs in [11:9];
  // ST/STB: store source in Rd field [11:9];
  // group-B immediates: Rd read in place from [11:9] (v2.1 §4.5);
  // LDB/STB: base register in [5:3] (v2.1 §4.6); others: [8:6]/[5:3].
  private val idIsByteMem = decoder.io.isLDB || decoder.io.isSTB
  // Class→field equations live in Isa (single source of truth with the
  // IF-stage latch); the vID-gated latch assert below guards the capture.
  private val idRsAddr = Isa.rsAddrOf(decoder.io.instr)
  private val idRtAddr = Isa.rtAddrOf(decoder.io.instr)

  regFile.io.rsAddr := rID_rsAddr
  regFile.io.rtAddr := rID_rtAddr

  // =========================================================================
  // LD unit — snapshot ownership (Phase 3 IPC #1)
  // =========================================================================
  // An LD's request is snapshotted at ID→EX entry ({rd, byte, addr}) and
  // issued from the snapshot — EX may advance immediately; no pipeline
  // register is read after the transfer edge. Single-outstanding invariant
  // (the bus has no transaction IDs): a second LD may snapshot only after
  // the first LD's response arrives (ldBusyStall), and may overwrite the
  // snapshot only after the first LD's request fired (ldSnapStall).
  private val ldRd = Reg(UInt(3 bits))
  private val ldData = Reg(Bits(16 bits))
  private val nextLdRd = UInt(3 bits)
  private val nextLdData = Bits(16 bits)
  nextLdRd := ldRd
  nextLdData := ldData
  private val ldState = Reg(LdPhase()) init LdPhase.IDLE
  private val ldWbVld = Reg(Bool()) init False // valid flag for LD writeback, decoupled from rEX_type
  private val nextLdWbVld = Bool()
  nextLdWbVld := ldWbVld
  private val ldPending = ldState === LdPhase.WAIT_BUS
  private val ldRspPending = ldState === LdPhase.DATA_READY
  // Snapshot: dest/LSB captured at ID→EX entry of an LD, consumed when its
  // request fires (byte/addr moved to rBusByte/rBusAddr, which serve all
  // mem ops).  Single-driver regs (transfer writes, FSM reads).
  private val snapRd = Reg(UInt(3 bits))
  private val snapAddr0 = Reg(Bool()) init False
  // Bus-issue address/lane — written by EVERY mem-op ID→EX transfer
  // (LD/LDB and ST/STB) and read by the bus request directly.  Existing
  // stalls keep it stable until the request fires: snapLdStall/snapStStall
  // for a held snapshot, stStall for an unfired store.  It subsumes the
  // old snapAddr and the snapPending/EX mux on req.addr: a direct FF
  // output (not a mux) keeps the RAM address cone trivial — the F4PGA
  // Yosys maps a muxed RAM address to RAM256X1S LUTRAM cells that
  // nextpnr-xilinx cannot place ("no BELs remaining"), while a plain
  // register maps to placeable RAM64M (Phase-1 Fmax netlists).
  private val rBusAddr = Reg(Bits(16 bits))
  private val rBusByte = Reg(Bool()) init False
  private val snapPending = ldState === LdPhase.SNAPSHOT
  private val nextLdState = LdPhase()
  nextLdState := ldState
  // Store-to-load bypass tracker (Phase 3 IPC): last completed RAM word
  // store {addr, data}. A word LD to the same word index completes from
  // this register without the bus round trip (see transfer). Updated only
  // at ST fire (the values actually written); STB invalidates (partial
  // lane — the other lane's tracked value would be stale); I/O stores
  // never track (side effects + TX state).
  private val stTrackVld = Reg(Bool()) init False
  private val stTrackAddr = Reg(Bits(16 bits))
  private val stTrackData = Reg(Bits(16 bits))
  private def isIoAddr(a: UInt): Bool = a >= 0x1FFC
  // v2.1 §5.6: byte-load lane info — captured with ldRd so the WAIT_BUS
  // path can select the lane after rEX has moved on to another instruction.
  // Next-state (bypass transfer also writes: word completions record False).
  private val ldIsByte = Reg(Bool()) init False
  private val ldAddr0 = Reg(Bool()) init False
  private val nextLdIsByte = Bool()
  private val nextLdAddr0 = Bool()
  nextLdIsByte := ldIsByte
  nextLdAddr0 := ldAddr0
  // Store-issue handshake (FPGA timing): set when the ST request fires,
  // cleared when EX receives its next content.  The store therefore leaves
  // EX one cycle after fire, decided from this REGISTERED flag instead of
  // the combinational req.ready — that removes the
  //   rEX_effAddr → isIoAddr → ready → stStall → stallID → CE
  // cone that was the nextpnr critical path (addr decode + backpressure
  // no longer feed any clock-enable).
  private val stFired = Reg(Bool()) init False
  private val nextStFired = Bool()
  nextStFired := stFired
  // Byte-lane select shared via Isa (single source of truth).
  // Accept the response whenever the LD unit owns an access: WAIT_BUS (a
  // request already issued) or SNAPSHOT (async RAM responds in the fire
  // cycle).  Expressed WITHOUT the req.fire term (was
  //   ldPending || (snapPending && io.dataBus.req.fire))
  // — functionally identical (rsp.valid can only assert in those states),
  // but the fire term fed a combinational ready/valid cross-dependency
  // that the F4PGA Yosys build collapsed into a hollow netlist cell
  // (no-BEL $buf with z inputs; see PLAN.md).
  io.dataBus.rsp.ready := ldPending || snapPending

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
  // ISA v3.1: word offsets are scaled by 2 (off in words, -32..+31);
  // the byte path (LDB/STB) is unchanged.
  private val idWordScaled = (idSext(14 downto 0) ## B"0")
  private val idAddrImm = idIsByteMem ? idOffZext3 | idWordScaled
  private val idEffAddr = (idFwdRsVal.asUInt + idAddrImm.asUInt).asBits
  private val idBrOff7 = decoder.io.instr(6 downto 0).asSInt.resize(16).asBits
  private val idBrShifted = idBrOff7(14 downto 0) ## B"0"
  private val idBrTarget = (rID_pc.asSInt + 2 + idBrShifted.asSInt).asBits.resized

  // Pre-compute branch condition in ID to shorten EX critical path.
  // v3.2: single Rs compared against 0 (Z/NZ) or its sign bit (MI/PL) —
  // no Rt read, so only idFwdRsVal matters.  Uses sources that are all
  // REGISTERS by now (LDI result, LD data in DATA_READY, WB result,
  // regfile).  Two stalls guarantee the operand is settled before ID→EX
  // fires: loadUseHazard for a pending LD and exIdFwdHazard for an
  // ALU/CALL result still sitting in EX.  Stored as rEX_brTaken in the
  // ID→EX transfer.
  // v2.1 §5.4: CALL is taken unconditionally (flush + redirect like JMP).
  private val idBrTaken =
    decoder.io.isJMP ||
    decoder.io.isCALL ||
    decoder.io.isCALLR ||
    decoder.io.isJMPR ||
    (decoder.io.isBranch && Isa.brTaken(idFwdRsVal, decoder.io.brCC))

  // Pre-compute JMP/CALL target in ID to remove forwarding mux from EX
  // critical path.  Stored as rEX_jmpTarget in the ID→EX transfer.
  // JMP: target = R[Rs] (instr[11:9]); CALL: target = R[Rtarget] (instr[5:3],
  // read through the rt port — idRtAddr falls through to rtReg for CALL).
  // v3.3: CALLR/JMPR targets resolve here (PC_next + scaled offset), so EX
  // needs no new path — rEX_jmpTarget carries the final address for all
  // four control transfers.
  private val idCallOff9 = decoder.io.instr(8 downto 0).asSInt.resize(16).asBits
  private val idCallTarget =
    (rID_pc.asSInt + 2 + (idCallOff9(14 downto 0) ## B"0").asSInt).asBits.resized
  private val idJmpOff12 = decoder.io.instr(11 downto 0).asSInt.resize(16).asBits
  private val idJmpRelTarget =
    (rID_pc.asSInt + 2 + (idJmpOff12(14 downto 0) ## B"0").asSInt).asBits.resized
  private val idJmpTarget = Mux(decoder.io.isCALLR, idCallTarget,
    Mux(decoder.io.isCALL, idFwdRtVal,
      Mux(decoder.io.isJMPR, idJmpRelTarget, idFwdRsVal)))

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
      // Empty: transfers capture snapshots directly into SNAPSHOT.
    }
    is(LdPhase.SNAPSHOT) {
      // Issue from the snapshot (taken at ID→EX entry); the LD itself may
      // long have left EX. Firing consumes the snapshot the same edge a
      // newer LD's transfer may overwrite it — safe: fire uses pre-edge.
      when(io.dataBus.req.fire) {
        nextLdRd := snapRd
        nextLdWbVld := True
        nextLdIsByte := rBusByte
        nextLdAddr0 := snapAddr0
        when(io.dataBus.rsp.fire) {
          // Async RAM: response available same cycle as request — skip WAIT_BUS
          nextLdData := Isa.laneSel(io.dataBus.rsp.payload, rBusByte, snapAddr0)
          nextLdState := LdPhase.DATA_READY
        } otherwise {
          // Sync RAM or UART: wait for response in WAIT_BUS
          nextLdState := LdPhase.WAIT_BUS
        }
      }
    }
    is(LdPhase.WAIT_BUS) {
      when(io.dataBus.rsp.fire) {
        nextLdData := Isa.laneSel(io.dataBus.rsp.payload, ldIsByte, ldAddr0)
        nextLdState := LdPhase.DATA_READY
      }
    }
    is(LdPhase.DATA_READY) {
      // Completion touches nothing in EX: a stale LD occupant is inert
      // (req.valid is snapshot-driven, spent at fire), so it is simply
      // overwritten by the next transfer. The old rEX_type clear is gone.
      // (A same-cycle LD transfer overrides to SNAPSHOT — writeback for
      // the completing LD already fired combinationally; see ldWbFiring.)
      nextLdState := LdPhase.IDLE
    }
  }

  // =========================================================================
  // Stall and Flush Logic
  // =========================================================================

  // Load-use hazard: EX has LD that writes a register needed by ID
  // (LDB is an LD-type access and participates; LDI8 reads no registers;
  // v3.3 CALLR/JMPR read no registers — their Rs/Rt fields are immediates.)
  private val idNeedsRs = vID && !decoder.io.isLDI && !decoder.io.isLDI8 &&
    !decoder.io.isCALLR && !decoder.io.isJMPR
  // rt port readers: register ALU ops (not the in-place group-B immediates,
  // whose [5:3] field is the immediate), stores (incl. STB) and CALL
  // (Rtarget in instr[5:3]).  v3.2: branches read no Rt.
  private val idNeedsRt = vID && (decoder.io.isALU || decoder.io.isST ||
    decoder.io.isSTB || decoder.io.isCALL) &&
    !decoder.io.isImmEn && !decoder.io.isGrpBImm
  // Load-use hazard (Phase 3: snapshot-aware, EX-independent). A pending
  // snapshot's data is never available yet; a WAIT_BUS load's data is
  // available at DATA_READY via ldData forwarding (hence !ldRspPending
  // only on the WAIT_BUS term — during SNAPSHOT ldRspPending is False by
  // construction, and during DATA_READY neither term fires).
  private val loadUseHazard =
    (snapPending && snapRd =/= 0 &&
      ((idNeedsRs && rID_rsAddr === snapRd) ||
        (idNeedsRt && rID_rtAddr === snapRd))) ||
    (ldPending && !ldRspPending && ldRd =/= 0 &&
      ((idNeedsRs && rID_rsAddr === ldRd) ||
        (idNeedsRt && rID_rtAddr === ldRd)))
  // Hold a second LD in ID while the first LD's request is still
  // outstanding on the bus (single-outstanding invariant — the bus has no
  // transaction IDs, so two in-flight reads would be indistinguishable).
  // Non-LD instructions flow freely; true dependents use loadUseHazard.
  private val ldBusyStall = ldPending && !ldRspPending && vID &&
    (decoder.io.isLD || decoder.io.isLDB)
  // Serialize LD/ST transfers against a held snapshot. A same-edge LD
  // transfer would hijack nextLdState (transfer is later in file = higher
  // priority), skipping the firing load's DATA_READY/WAIT_BUS state — its
  // writeback enable and response would be lost (byte_test: second LDB
  // kills the first). Hence LD entry is blocked for the whole held
  // window (firing or not); the ST half needs only the unfired case (an
  // ST transfer never touches snapshot/state, but would mix its strobe
  // with the snapshot's address on the bus).
  private val snapLdStall = snapPending && vID &&
    (decoder.io.isLD || decoder.io.isLDB)
  private val snapStStall = snapPending && !io.dataBus.req.fire && vID &&
    (decoder.io.isST || decoder.io.isSTB)
  // v2.1: hold the instruction in EX until its store request is accepted
  // (UART TX backpressure) and the acceptance is REGISTERED (stFired).
  // Without the hold, a ST/STB whose req.ready is low would be overwritten
  // by the next ID→EX transfer and its write lost; without stFired, the
  // combinational ready→stall path was the FPGA critical path.
  private val stStall = rEX_type === InstrType.ST && !stFired
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
  //   - v3.2 branch condition BR cc, Rs      (rs only)
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
  private val idUsesRtInId = vID && decoder.io.isCALL
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

  private val stallID = loadUseHazard || ldBusyStall || snapLdStall || snapStStall ||
    stStall || (exIdFwdHazard && !rWbHasExRes)
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
    nextStFired := False
  }
  when(io.dataBus.req.fire && (rEX_type === InstrType.ST)) {
    nextStFired := True
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

  // Bypass tracker update: only at ST fire (the values actually written —
  // pre-edge EX state, so retention/re-fire subtleties cannot pollute it;
  // ST fires at most once per EX residency via stFired). Word stores to
  // RAM track; byte stores invalidate (untracked lane would go stale);
  // I/O stores never track.
  when(io.dataBus.req.fire && (rEX_type === InstrType.ST)) {
    when(rEX_byte) {
      stTrackVld := False
    } otherwise {
      when(!isIoAddr(rEX_effAddr.asUInt)) {
        stTrackVld := True
        stTrackAddr := rEX_effAddr
        stTrackData := exFwdRtVal
      }
    }
  }

  // =========================================================================
  // IF Stage — Instruction Fetch
  // =========================================================================
  private val ldiPending = Reg(Bool()) init False
  private val nextLdiPending = Bool()
  nextLdiPending := ldiPending
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
        nextLdiPending := True
        ldiHeader := io.instrRsp.payload
        ldiHeaderPc := pc
        nextPC := pc + 2
        nextVID := False
      } otherwise {
        when(ldiPending) {
          rID_instr := ldiHeader
          rID_pc := ldiHeaderPc
          rID_ldiData := io.instrRsp.payload
          rID_rsAddr := Isa.rsAddrOf(ldiHeader)
          rID_rtAddr := Isa.rtAddrOf(ldiHeader)
          nextVID := True
          nextLdiPending := False
          nextPC := pc + 2
        } otherwise {
          rID_instr := io.instrRsp.payload
          rID_pc := pc
          rID_ldiData := 0
          rID_rsAddr := Isa.rsAddrOf(io.instrRsp.payload)
          rID_rtAddr := Isa.rtAddrOf(io.instrRsp.payload)
          nextVID := True
          nextPC := pc + 2
        }
      }
    } otherwise {
      // Stale response after branch: discard, clear stale LDI state.
      nextLdiPending := False
    }
  } otherwise {
    when(!stallIF && !ldiPending) {
      nextVID := False
    }
  }

  when(exBrTaken) {
    nextLdiPending := False
  }

  // v2.1 §5.4: once HALT has left ID, drop any instruction IF delivered in
  // the same cycle (it is younger than HALT and must never execute).
  // Placed after the IF block so it overrides nextVID := True.
  when(halted) {
    nextVID := False
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

  // Data bus request: LD issues from the snapshot state (valid until it
  // fires, even after the LD leaves EX); ST issues from EX with the
  // registered handshake as before.  Address/lane come from the mem-op
  // transfer registers (rBusAddr/rBusByte — registered, see declarations).
  io.dataBus.req.payload.addr := rBusAddr.asUInt
  io.dataBus.req.payload.wrData := (rEX_byte && (rEX_type === InstrType.ST)) ?
    stByteData | exFwdRtVal
  io.dataBus.req.payload.wr := rEX_type === InstrType.ST
  io.dataBus.req.payload.isByte := rBusByte
  io.dataBus.req.valid :=
    (snapPending && !ldRspPending) ||
      (rEX_type === InstrType.ST && !stFired)

  // =========================================================================
  // EX → WB Transfer — reads pre-edge rEX_* (SpinalHDL reads are always
  // pre-edge, so no source ordering is required); priority vs the ID→EX
  // transfer below is explicit: ID→EX assigns nextREXtype LATER, so a
  // simultaneous transfer wins over retention (intended — the LD FSM owns
  // completed accesses via ldRd/ldWbVld, see ldWbFiring).
  // =========================================================================

  // Default: no new WB data
  nextVWB := False

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
    // first cycle in EX computes a correct exResult: EX forwarding feeds
    // the correct operand there and is blocked afterwards by the
    // rWbHasExRes self-forward guard.  Without this hold, the recomputed
    // garbage from the stale latch wins (ADDI R7,R7,#2 committed 1ff4
    // instead of 1ff8 while stalled, corrupting R7).
    // rWB_rd/rWB_hasRd are gated too — they already hold these exact
    // values during retention (same instruction), so the gate is a no-op
    // for them and keeps the whole commit atomic.
    when(!rWbHasExRes) {
      rWB_rd := rEX_rd
      rWB_hasRd := rEX_hasRd
      rWB_result := exResult
    }
    nextVWB := True
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
    nextLdWbVld := False
  }

  // =========================================================================
  // Branch target update (uses pre-clear rEX_type for JMP/CALL vs BR)
  // =========================================================================
  when(exBrTaken) {
    nextPC := Mux(rEX_type === InstrType.JMP || rEX_type === InstrType.CALL,
      rEX_jmpTarget.asUInt, rEX_brTarget.asUInt)
    nextVID := False
    nextLdiPending := False
  }

  // =========================================================================
  // ID → EX Transfer — assigns nextREXtype after EX→WB's readers, so both
  // orders elaborate identically; the vID-gated nesting below (not a Mux)
  // avoids the vClr race documented there.
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
  // v3.3: CALLR rides the CALL path (link write + taken flush; its target
  // is PC-relative and pre-resolved into rEX_jmpTarget below); JMPR rides
  // the JMP path (same, no link write).
  when(decoder.io.isCALLR) { idType := InstrType.CALL }
  when(decoder.io.isJMPR) { idType := InstrType.JMP }
  when(decoder.io.isHALT) { idType := InstrType.EMPTY }
  when(decoder.io.isReserved) { idType := InstrType.EMPTY }

  // Clear rEX_type on branch (prevents stale EX→WB after flush)
  // Also clear rEX_brTaken — with the pre-computed branch condition now
  // stored in a register, we must reset it to avoid re-triggering the
  // branch PC update every cycle (the old combinational formula would
  // have automatically resolved to False once rEX_type was EMPTY).
  when(exBrTaken) {
    nextREXtype := InstrType.EMPTY
    nextLdiPending := False
    nextREXbrTaken := False
  }

  // Normal ID→EX transfer (when no stall/flush).  vID is used in an inner
  // when (not a Mux) to avoid the stallBrId→vClr race: if vClr clears vID
  // before rEX_type is assigned in the same cycle, a Mux would see vID=0
  // and kill the branch/JMP transfer.  By nesting, we enter the outer when
  // unconditionally (for the no-stall/no-flush case) and only gate the
  // actual transfer on vID.
  // Store-to-load bypass decision (Phase 3 IPC): word LD to the tracked
  // word index. Decided atomically at transfer; completion data is latched
  // below so later stores cannot disturb it. An unfired ST in EX excludes
  // bypass via stStall (no transfer while ST unfired).
  // Both isIoAddr terms are dropped (F4PGA timing): the tracker invariant
  // `stTrackVld ⇒ !isIoAddr(stTrackAddr)` is formally asserted below, and
  // [15:1] equality pins idEffAddr within ±1 of that address — so
  // idEffAddr ≥ 0x1FFC would require the tracker to hold 0x1FFC/0x1FFD,
  // which the invariant forbids (0x1FFC has word index 0xFFE ≠ 0xFFD).
  // The removed `16'h1ffc <= idEffAddr` carry-chain compare was on the
  // rID_rsAddr → idEffAddr → CE critical path (86.63 MHz).
  private val bypassTake = decoder.io.isLD && stTrackVld &&
    (idEffAddr(15 downto 1) === stTrackAddr(15 downto 1))
  when(!stallID && !exBrTaken && !halted) {
    when(vID) {
      // Clear vID when IF→ID is not also firing (otherwise the new
      // instruction from IF would be lost).
      when(!io.instrRsp.fire) { nextVID := False }
      // v2.1 §5.4: HALT freezes fetch/decode — set here (with an older
      // instruction still draining through EX/WB), gated above by
      // !stallID/!exBrTaken so a flushed speculative HALT never halts.
      when(decoder.io.isHALT) { nextHalted := True }
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
      // Snapshot the LD request at entry (Phase 3): address, dest, lane.
      // EX may advance immediately; issue/forwarding use only this.
      // Safe when it fires: ldBusyStall blocks entry during WAIT_BUS
      // (outstanding request), snapLdStall/snapStStall during a held
      // snapshot; DATA_READY entry is safe (writeback already fired —
      // see ldWbFiring). Skipped when the bypass below takes the LD
      // (bus path unused then).
      // Bus-issue address/lane for any mem op (LD/LDB and ST/STB).
      when(decoder.io.isLD || decoder.io.isLDB || decoder.io.isST ||
        decoder.io.isSTB) {
        rBusAddr := idEffAddr
        rBusByte := idIsByteMem
      }
      when((decoder.io.isLD || decoder.io.isLDB) && !bypassTake) {
        snapRd := (decoder.io.hasRd ? decoder.io.rdField | B"000").asUInt
        snapAddr0 := idEffAddr(0)
        nextLdState := LdPhase.SNAPSHOT
      }
      // Bypass completion (no bus): latch dest/valid/data now, DATA_READY
      // next; the normal writeback path fires from ldWbFiring.
      when(bypassTake) {
        nextLdRd := (decoder.io.hasRd ? decoder.io.rdField | B"000").asUInt
        nextLdWbVld := True
        nextLdData := stTrackData
        // Word completion: record non-byte lane info (else a stale LDB
        // record would fail the zero-extension assert below).
        nextLdIsByte := False
        nextLdAddr0 := False
        nextLdState := LdPhase.DATA_READY
      }

      rEX_aluFunc := decoder.io.aluFunc
      nextREXtype := idType
      nextREXbrTaken := idBrTaken
      rEX_jmpTarget := idJmpTarget
    } otherwise {
      // Clear nextREXtype when ID is empty (vID=0).  Without this, a stale
      // ST lingering in EX would re-trigger its bus request every cycle
      // (ST req.valid is combinational from rEX_type), causing duplicate
      // bus transactions. (LD req.valid is snapshot-driven and needs no
      // such guard — a stale LD occupant is inert.)
      nextREXtype := InstrType.EMPTY
    }
  }

  // =========================================================================
  // WB Stage — Register File Write (with LD direct-write bypass)
  // =========================================================================
  // Port 1 keeps the original muxed LD/WB equation (byte-identical
  // semantics for the existing formal properties).  Port 2 carries the
  // WB write only in the cycle where port 1 is simultaneously occupied
  // by an LD writeback (ldWbTerm && wbWrTerm) — the single-port mux used
  // to silently DROP that WB write there.  Reachable e.g. with a
  // store-to-load bypass LD: its DATA_READY/writeback lands at transfer+1
  // while its predecessor (an ALU/CALL) sits in WB, or with a delayed
  // bus/UART response completing next to an unrelated ALU writeback.
  // Exposed by cc.py's MOV operand handoff (`LD; MOV; LD(bypass)` — the
  // old stack round-trip always put an ADDI R7 or ST before the LD).
  private val ldWbTerm = ldWbFiring && ldRd =/= 0
  private val wbWrTerm = vWB && rWB_hasRd && rWB_rd =/= 0
  regFile.io.wrAddr := Mux(ldWbTerm, ldRd, rWB_rd)
  regFile.io.wrData := Mux(ldWbTerm, ldData, rWB_result)
  regFile.io.wrEn := ldWbTerm || wbWrTerm
  regFile.io.wr2En := ldWbTerm && wbWrTerm
  regFile.io.wr2Addr := rWB_rd
  regFile.io.wr2Data := rWB_result

  // =========================================================================
  // State update — the ONLY drivers of these registers. Priority between
  // overlapping overrides is file order above (later source wins).
  // =========================================================================
  vID := nextVID
  rEX_type := nextREXtype
  rEX_brTaken := nextREXbrTaken
  vWB := nextVWB
  halted := nextHalted
  stFired := nextStFired
  ldWbVld := nextLdWbVld
  ldRd := nextLdRd
  ldData := nextLdData
  ldIsByte := nextLdIsByte
  ldAddr0 := nextLdAddr0
  ldState := nextLdState
  ldiPending := nextLdiPending
  pc := nextPC

  // =========================================================================
  // Debug Bus
  // =========================================================================

  private val resetn = ClockDomain.current.readResetWire

  // Shared debug read path owns auxAddr + rspWord/rspStrb (0x06/0x08).
  DebugReads.attach(busIf, regFile, pc)
  io.dbgRegFile1 := regFile.io.auxVal

  busIf.io.cmdDone := False

  private def flsPipeline(): Unit = {
    nextLdiPending := False; nextVID := False
    nextREXtype := InstrType.EMPTY; nextVWB := False
    // v2.1 §5.4: debug/bus commands exit HALT (reset 0x03, step 0x04, run 0x05)
    nextHalted := False
    busIf.io.cmdDone := True
  }

  when(busIf.io.cmdStrb) {
    switch(busIf.io.cmdWord(31 downto 24)) {
      is(0x03) { flsPipeline() }
      is(0x04) { flsPipeline() }
      is(0x05) { flsPipeline() }
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
  // Stall-cause vector: {ldiStall, exIdFwd, stStall, snapSt, snapLd,
  // ldBusy, loadUse}.
  io.dbgStall := stallLdiId.asBits ## exIdFwdHazard.asBits ## stStall.asBits ##
    snapStStall.asBits ## snapLdStall.asBits ## ldBusyStall.asBits ##
    loadUseHazard.asBits

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
    // Bypass tracker at reset (valid False; addr/data unconstrained but
    // unreadable until set — all readers gate on stTrackVld).
    assumeInitial(!stTrackVld)
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
    when(regFile.io.wr2En) { assert(regFile.io.wr2Addr =/= 0) }

    // ======================================================================
    // Concurrent LD + WB writeback: BOTH writes must land (dual-port fix)
    // ======================================================================
    // Before the dual-port RegFile the single-port priority mux silently
    // dropped the WB write whenever ldWbFiring coincided with vWB for a
    // different rd (e.g. a store-to-load bypass LD completing while its
    // ALU predecessor is in WB).  RegFile TC-RF-3b/TC-RF-7 machine-check
    // that both ports' writes are visible; this pins the wiring.
    when(ldWbFiring && ldRd =/= 0 && vWB && rWB_hasRd && rWB_rd =/= 0) {
      assert(regFile.io.wrEn)
      assert(regFile.io.wrAddr === ldRd)
      assert(regFile.io.wrData === ldData)
      assert(regFile.io.wr2En)
      assert(regFile.io.wr2Addr === rWB_rd)
      assert(regFile.io.wr2Data === rWB_result)
    }

    // ======================================================================
    // Data bus request properties
    // ======================================================================
    when(rEX_type === InstrType.ST) {
      // Store handshake: valid stays high until the request fires; after
      // fire (stFired) it must drop so the held cycle cannot re-issue.
      assert(io.dataBus.req.valid === !stFired)
      assert(io.dataBus.req.payload.wr)
    }
    // LD req.valid is snapshot-driven (held snapshot outside DATA_READY).
    when(snapPending && !ldRspPending) {
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
    // v3.3: JMPR words (opcode 0xE) read no register — the latched address
    // falls through to instr[8:6], so the [11:9] select applies only to
    // real register jumps (0xB words with cf=000/payload=0).
    // v3.3: PC-relative targets recomputed from EX state (formal mirrors
    // of idCallTarget/idJmpRelTarget).
    val exIsJmpRegRef = (rEX_instr(15 downto 12) === B"1011") &&
      (rEX_instr(8 downto 6) === B"000") && (rEX_instr(5 downto 0) === B"000000")
    val exCallOff9 = rEX_instr(8 downto 0).asSInt.resize(16).asBits
    val exCallTargetRef =
      (rEX_pc.asSInt + 2 + (exCallOff9(14 downto 0) ## B"0").asSInt).asBits.resized
    val exJmpOff12 = rEX_instr(11 downto 0).asSInt.resize(16).asBits
    val exJmpRelRef =
      (rEX_pc.asSInt + 2 + (exJmpOff12(14 downto 0) ## B"0").asSInt).asBits.resized
    val exRsAddrRef = ((rEX_type === InstrType.BR || exIsJmpRegRef ||
      exIsGrpBImm) ? rEX_instr(11 downto 9).asBits |
      (rEX_byte ? rEX_instr(5 downto 3).asBits |
        rEX_instr(8 downto 6).asBits)).asUInt
    // v3.2: BR reads a single Rs ([11:9]); Rt is never read (the latched
    // rEX_rtAddr falls through to instr[5:3], unused by all consumers).
    val exRtAddrRef = Mux(rEX_type === InstrType.ST, rEX_instr(11 downto 9).asUInt,
      rEX_instr(5 downto 3).asUInt)
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
    when(snapPending && !ldRspPending) { assert(io.dataBus.req.valid) }

    // ldData captures the payload when bus response fires — with the v2.1
    // byte-lane select applied for LDB (§5.6): zero-extended addressed lane.
    when(pastValid() && resetn) {
      when(past(io.dataBus.rsp.fire) && past(ldState) === LdPhase.SNAPSHOT) {
        assert(ldData === Isa.laneSel(past(io.dataBus.rsp.payload),
          past(rBusByte), past(snapAddr0)))
      }
      when(past(io.dataBus.rsp.fire) && past(ldState) === LdPhase.WAIT_BUS) {
        assert(ldData === Isa.laneSel(past(io.dataBus.rsp.payload), ldIsByte, ldAddr0))
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

    // == LD (ISA §3.3 / §4.9): request issues from the snapshot ==
    // (dest/lane/address captured at ID→EX entry — the LD may have left EX).
    when(snapPending && !ldRspPending) {
      assert(io.dataBus.req.valid)
      assert(!io.dataBus.req.payload.wr)
      // Lane info recorded at transfer matches the issued address LSB
      // (addr/isByte now come from the rBus* registers directly).
      assert(rBusAddr(0) === snapAddr0)
    }

    // == ST (ISA §3.3 / §4.10): data bus write with correct addr + data   ==
    // v2.1 §5.6: STB (rEX_byte) writes R[Rd][7:0] in the addressed lane.
    when(rEX_type === InstrType.ST) {
      assert(io.dataBus.req.valid === !stFired)
      assert(io.dataBus.req.payload.wr)
      // Bus address/lane are the ST's own (rBus* latched at its transfer;
      // stStall blocks later mem-op transfers until the request fires).
      assert(rBusAddr === rEX_effAddr.asBits)
      assert(rBusByte === rEX_byte)
      when(rEX_byte) {
        assert(io.dataBus.req.payload.wrData === stByteData)
      } otherwise {
        assert(io.dataBus.req.payload.wrData === exFwdRtVal)
      }
      assert(!rEX_hasRd)
    }

    // == JMP (ISA §3.4 / §4.11): unconditional, no rd                     ==
    // v3.3: JMPR (opcode 0xE) rides the same type with a pre-resolved target.
    when(pastValid() && resetn) {
      when(rEX_type === InstrType.JMP) {
        assert(exBrTaken)
        assert(!rEX_hasRd)
        when(rEX_instr(15 downto 12) === B"1110") {
          assert(rEX_jmpTarget === exJmpRelRef)
        } otherwise {
          assert(rEX_jmpTarget === exFwdRsVal)
        }
      }
    }

    // == v2.1 §5.4: CALL — taken like JMP, writes the link address       ==
    // v3.3: CALLR (opcode 0xD) rides the same type; its target is
    // PC-relative and pre-resolved (no register read).
    when(pastValid() && resetn) {
      when(rEX_type === InstrType.CALL) {
        assert(exBrTaken)
        assert(rEX_hasRd)
        // link = address of the next instruction (PC_next, §5.4)
        assert(exResult === (rEX_pc + 2).asBits)
        // CALL itself issues nothing: any live request belongs to a pending
        // LD snapshot (Phase 3: the bus is unit-owned, not EX-owned).
        assert(io.dataBus.req.valid === (snapPending && !ldRspPending))
        assert(!io.dataBus.req.payload.wr)
        when(rEX_instr(15 downto 12) === B"1101") {
          assert(rEX_jmpTarget === exCallTargetRef)
        } otherwise {
          // target rides the jmp mux (rt port, instr[5:3])
          assert(rEX_jmpTarget === exFwdRtVal)
        }
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

    // == Branch Condition Correctness (v3.2: BR cc, Rs vs 0 / sign)      ==
    when(pastValid() && resetn) {
      when(rEX_type === InstrType.BR) {
        when(rEX_brCC === B"00") { assert(exBrTaken === (exFwdRsVal === 0)) }
        when(rEX_brCC === B"01") { assert(exBrTaken === (exFwdRsVal =/= 0)) }
        when(rEX_brCC === B"10") { assert(exBrTaken === exFwdRsVal(15)) }
        when(rEX_brCC === B"11") { assert(exBrTaken === !exFwdRsVal(15)) }
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
          // v3.3: register-JMP re-checks the rs forward; JMPR re-checks
          // the pre-resolved target.
          when(past(rEX_instr)(15 downto 12) === B"1110") {
            assert(pc === past(rEX_jmpTarget).asUInt)
          } otherwise {
            assert(pc === past(exFwdRsVal).asUInt)
          }
        }
        when(past(rEX_type) === InstrType.CALL) {
          // v2.1 §5.4: PC ← R[Rtarget] (target forwarded in ID, re-checked
          // against the EX rt-forward like JMP does for its rs-forward).
          // v3.3: CALLR re-checks the pre-resolved target instead.
          when(past(rEX_instr)(15 downto 12) === B"1101") {
            assert(pc === past(rEX_jmpTarget).asUInt)
          } otherwise {
            assert(pc === past(exFwdRtVal).asUInt)
          }
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
    // Temporal: LD snapshot capture + fire + single-outstanding (Phase 3)
    // ======================================================================
    when(pastValid() && resetn) {
      // ID→EX of an LD either snapshots (bus path) or bypass-completes.
      when(past(vID) && (past(decoder.io.isLD) || past(decoder.io.isLDB)) &&
        !past(stallID) && !past(exBrTaken) && !past(halted)) {
        when(past(bypassTake)) {
          // Bypass: DATA_READY + valid writeback + latched data/rd now.
          assert(ldState === LdPhase.DATA_READY)
          assert(ldWbVld)
          assert(ldRd === past(decoder.io.rdField).asUInt)
          assert(ldData === past(stTrackData))
          // No bus request fires with a bypassing transfer (either valid
          // term would imply a stall: LD-term via snapLdStall, ST-term via
          // stStall) — so the tracker read is committed, not mid-update.
          assert(!io.dataBus.req.fire)
        } otherwise {
          assert(snapPending)
          assert(snapRd === past(decoder.io.rdField).asUInt)
          // rBusAddr/rBusByte latched the low 16 bits (modular address,
          // same truncation as the rEX_effAddr := idEffAddr assignment).
          assert(rBusAddr === past(idEffAddr)(15 downto 0))
          assert(rBusByte === past(idIsByteMem))
          assert(snapAddr0 === past(idEffAddr(0)))
        }
      }
      // rBusAddr/rBusByte capture EVERY mem-op ID→EX transfer (LD/LDB and
      // ST/STB) — the bus issues from these registers.
      when(pastValid() && resetn && past(vID) && !past(stallID) &&
        !past(exBrTaken) && !past(halted) &&
        (past(decoder.io.isLD) || past(decoder.io.isLDB) ||
          past(decoder.io.isST) || past(decoder.io.isSTB))) {
        assert(rBusAddr === past(idEffAddr)(15 downto 0))
        assert(rBusByte === past(idIsByteMem))
      }
      // Firing consumes the snapshot (dest/lane move to the completion regs).
      when(past(snapPending) && past(io.dataBus.req.fire)) {
        assert(ldRd === past(snapRd))
        assert(ldIsByte === past(rBusByte))
        assert(ldAddr0 === past(snapAddr0))
        assert(ldWbVld)
      }
      // DATA_READY always carries a valid writeback (set at fire, cleared
      // at firing) — the invariant the DATA_READY-overwrite relies on.
      // Combinational form: all three DATA_READY entries set ldWbVld on
      // the same edge (bus fire ×2, bypass transfer ×1).
      when(ldRspPending) {
        assert(ldWbVld)
      }
      // No LD transfer while a request is outstanding (single-outstanding).
      when(past(ldPending) && !past(ldRspPending) && past(vID) &&
        (past(decoder.io.isLD) || past(decoder.io.isLDB))) {
        assert(stallID)
      }
      // No LD entry while a snapshot is held (firing or not) — a
      // same-edge transfer would hijack nextLdState and lose the
      // completing load. No ST entry over an unfired snapshot without
      // same-cycle fire (mixed-request serialization).
      when(past(snapPending) && past(vID) &&
        (past(decoder.io.isLD) || past(decoder.io.isLDB))) {
        assert(stallID)
      }
      when(past(snapPending) && !past(io.dataBus.req.fire) && past(vID) &&
        (past(decoder.io.isST) || past(decoder.io.isSTB))) {
        assert(stallID)
      }
      // LD transfer during DATA_READY is safe only because the completing
      // writeback fired this cycle (direct path, before the overwrite).
      when(past(ldRspPending) && past(ldWbVld) && past(ldRd) =/= 0 &&
        past(vID) && (past(decoder.io.isLD) || past(decoder.io.isLDB)) &&
        !past(stallID) && !past(exBrTaken) && !past(halted)) {
        assert(regFile.io.wrEn)
        assert(regFile.io.wrAddr === past(ldRd))
      }
    }

    // ======================================================================
    // Bypass tracker + bypass path (Phase 3 IPC)
    // ======================================================================
    // Tracker mirrors the last completed RAM word store: updated only at
    // ST fire with the written values, invalidated by any byte store,
    // never set for I/O.
    when(pastValid() && resetn) {
      when(past(io.dataBus.req.fire) && past(rEX_type) === InstrType.ST) {
        when(past(rEX_byte)) {
          assert(!stTrackVld)
        } otherwise {
          when(!isIoAddr(past(rEX_effAddr).asUInt)) {
            assert(stTrackVld)
            assert(stTrackAddr === past(rEX_effAddr))
            assert(stTrackData === past(exFwdRtVal))
          }
        }
      }
    }
    when(stTrackVld) {
      assert(!isIoAddr(stTrackAddr.asUInt))
    }
    // Redundancy of the dropped isIoAddr terms in bypassTake (timing
    // optimisation): a bypassing LD can never target I/O — follows from
    // the tracker invariant above + [15:1] address equality.  Asserted so
    // the simplification is machine-checked, not just argued.
    when(bypassTake) {
      assert(!isIoAddr(idEffAddr(15 downto 0).asUInt))
    }

    // ======================================================================
    // Coverage
    // ======================================================================
    cover(rEX_type === InstrType.ALU)
    cover(rEX_type === InstrType.LD)
    cover(rEX_type === InstrType.LDI)
    cover(rEX_type === InstrType.ST)
    cover(rEX_type === InstrType.JMP)
    cover(rEX_type === InstrType.JMP && rEX_instr(15 downto 12) === B"1110")
    cover(rEX_type === InstrType.BR && exBrTaken)
    cover(rEX_type === InstrType.BR && !exBrTaken)
    cover(loadUseHazard)
    cover(ldBusyStall)
    cover(snapLdStall)
    cover(snapStStall)
    cover(snapPending)
    cover(snapPending && io.dataBus.req.fire)
    cover(bypassTake)
    cover(stTrackVld)
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
    cover(rEX_type === InstrType.CALL && rEX_instr(15 downto 12) === B"1101")
    cover(halted)
    cover(rEX_byte && rEX_type === InstrType.LD)
    cover(rEX_byte && rEX_type === InstrType.ST)
    cover(rEX_type === InstrType.ALU && exIsGrpBImm)
    cover(rEX_type === InstrType.BR && rEX_brCC === B"00" && exBrTaken)
    cover(rEX_type === InstrType.BR && rEX_brCC === B"01" && exBrTaken)
    cover(rEX_type === InstrType.BR && rEX_brCC === B"10" && exBrTaken)
    cover(rEX_type === InstrType.BR && rEX_brCC === B"11" && exBrTaken)
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
