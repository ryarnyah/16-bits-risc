package risc

import spinal.core._
import spinal.core.formal._
import spinal.lib._

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

  // === Bus interface ===
  io.bus.cmd >> busIf.io.cmd
  busIf.io.rsp >> io.bus.rsp
  io.bus.ack := busIf.io.ack

  // === Core state ===
  private val pc = Reg(UInt(16 bits)) init 0
  private val running = Reg(Bool()) init True
  private val resetn = ClockDomain.current.readResetWire
  private val state = RegInit(CoreState.FETCH)

  // Pipeline active when executing (not in IDLE debug state)
  private val pipelineActive = state =/= CoreState.IDLE && resetn

  // PC is output for instruction fetch
  io.instrAddr := pc

  // =========================================================================
  // Instruction Fetch Buffer — registers the raw fetch from the ROM
  // =========================================================================
  private val fetchBufPc = Reg(UInt(16 bits)) init 0
  private val fetchBufData = Reg(Bits(16 bits)) init 0
  private val fetchBufVld = Reg(Bool()) init False

  // =========================================================================
  // IF Stage — Instruction Fetch
  // =========================================================================
  // LDI (opcode 0xF) is a 2-word instruction. IF fetches header then immediate
  // in consecutive cycles, presenting the complete LDI to ID on cycle 2.

  private val ifLdiPending = Reg(Bool()) init False
  private val ifLdiHeader = Reg(Bits(16 bits))
  private val ifLdiHeaderPc = Reg(UInt(16 bits))

  // IF→ID pipeline register (output of IF, input to ID)
  private val ifInstr = Reg(Bits(16 bits))
  private val ifPc = Reg(UInt(16 bits))
  private val ifVld = Reg(Bool()) init False
  private val ifLdiData = Reg(Bits(16 bits))

  // Pipeline register IF→ID
  private val idInstr = Reg(Bits(16 bits))
  private val idPc = Reg(UInt(16 bits))
  private val idVld = Reg(Bool()) init False
  private val idLdiData = Reg(Bits(16 bits))

  // Decoder (combinational from idInstr)
  decoder.io.instr := idInstr

  // Register file addressing (same as Core.scala)
  private val idRsAddr = ((decoder.io.isBranch || decoder.io.isJMP) ?
    decoder.io.instr(11 downto 9).asBits | decoder.io.rsReg.asBits).asUInt
  private val idRtAddr = (decoder.io.isST ?
    decoder.io.rdField.asBits |
    (decoder.io.isBranch ? decoder.io.instr(8 downto 6).asBits | decoder.io.rtReg.asBits)).asUInt

  private val idSextVal = decoder.io.imm6.asSInt.resize(16).asBits
  private val idEffAddr = (regFile.io.rsVal.asSInt + idSextVal.asSInt).asBits.resized
  private val idBrShifted = idSextVal(14 downto 0) ## B"0"
  private val idBrTarget = (idPc.asSInt + 2 + idBrShifted.asSInt).asBits.resized

  regFile.io.rsAddr := idRsAddr
  regFile.io.rtAddr := idRtAddr

  // =========================================================================
  // EX Stage — ALU + Memory Address + Branch
  // =========================================================================

  // Pipeline register ID→EX
  private val exVld = Reg(Bool()) init False
  private val exInstr = Reg(Bits(16 bits))
  private val exPc = Reg(UInt(16 bits))
  private val exRd = Reg(UInt(3 bits))
  private val exHasRd = Reg(Bool()) init False
  private val exIsALU = Reg(Bool()) init False
  private val exIsLD = Reg(Bool()) init False
  private val exIsST = Reg(Bool()) init False
  private val exIsJMP = Reg(Bool()) init False
  private val exIsBEQ = Reg(Bool()) init False
  private val exIsBNE = Reg(Bool()) init False
  private val exIsBLT = Reg(Bool()) init False
  private val exIsLDI = Reg(Bool()) init False
  private val exIsImmEn = Reg(Bool()) init False
  private val exAluFunc = Reg(Bits(3 bits))
  private val exRsVal = Reg(Bits(16 bits))
  private val exRtVal = Reg(Bits(16 bits))
  private val exSextVal = Reg(Bits(16 bits))
  private val exEffAddr = Reg(Bits(16 bits))
  private val exBrTarget = Reg(Bits(16 bits))
  private val exLdiData = Reg(Bits(16 bits))

  // Source register addresses from the EX instruction
  private val exRdField = exInstr(11 downto 9)
  private val exRsReg = exInstr(8 downto 6)
  private val exRtReg = exInstr(5 downto 3)
  private val exRsAddr = ((exIsBEQ || exIsBNE || exIsBLT || exIsJMP) ? exRdField | exRsReg).asUInt
  private val exRtAddr = Mux(exIsST, exRdField.asUInt,
    Mux(exIsBEQ || exIsBNE || exIsBLT, exInstr(8 downto 6).asUInt, exRtReg.asUInt))

  // =========================================================================
  // WB Stage — Writeback (register declarations only)
  // =========================================================================

  private val wbRd = Reg(UInt(3 bits))
  private val wbHasRd = Reg(Bool()) init False
  private val wbResult = Reg(Bits(16 bits))
  private val wbIsLD = Reg(Bool()) init False
  private val wbIsLDI = Reg(Bool()) init False

  // Shadow of previous WB values — preserves the previous wbResult for
  // an extra cycle so that forwarding still works when a back-to-back WB
  // write (e.g. LDI then LD) overwrites wbResult before EX can read it.
  // Without this, the classic pattern:
  //   LDI R4, #addr;  LD R1, [R7];  JMP R4
  // loses R4's value when LD enters WB on the same cycle JMP enters EX.
  private val prevWbHasRd = Reg(Bool()) init False
  private val prevWbRd = Reg(UInt(3 bits))
  private val prevWbResult = Reg(Bits(16 bits))

  // =========================================================================
  // Data Bus Response Capture (latches single-cycle rsp pulse in WB)
  // =========================================================================

  // LD write state machine — uses ldActive instead of wbIsLD so that
  // the LD state is not lost when EX→WB overwrites the pipeline registers.
  // 0 = idle
  // 1 = response captured, write pending
  // 2 = writing LD data (holds pipeline for 1 cycle so non-LD write can happen next)
  private val wbLdPhase = RegInit(U(0, 2 bits))
  private val wbLdData = Reg(Bits(16 bits))
  private val ldActive = Reg(Bool()) init False
  private val ldRd = Reg(UInt(3 bits))

  // stallWb defined here (before ldActive/ldRd setting) to break circular dep
  private val stallWb = ldActive && (wbLdPhase === 1 || wbLdPhase === 2)

  // Tracks whether the current EX instruction has been transferred to WB.
  // Set by EX→WB, cleared by ID→EX. Prevents stale EX from re-entering
  // the LD state machine or re-triggering load-use hazard detection.
  private val exServiced = Reg(Bool()) init False

  // Set when LD enters WB, cleared when LD completes
  // !exServiced guard prevents re-entry after LD was already serviced
  when(!stallWb && exVld && exIsLD && !exServiced) {
    ldActive := True
    ldRd := exRd
  }
  when(ldActive && wbLdPhase === 2) {
    ldActive := False
  }

  when(io.dataBus.rsp.fire) {
    wbLdData := io.dataBus.rsp.payload
    wbLdPhase := 1  // write_pending
  }
  when(ldActive && wbLdPhase === 1) {
    wbLdPhase := 2  // writing
  }
  when(ldActive && wbLdPhase === 2) {
    wbLdPhase := 0  // done
  }

  // LD write happens in phase 2 (cycle after response, while pipeline stalled)
  private val ldWriteNow = ldActive && wbLdPhase === 2
  private val ldWriteAddr = ldRd

  // =========================================================================
  // EX Stage — Forwarding and Computation
  // =========================================================================

  // Forward from WB to EX: when WB has a valid result that EX needs
  // Forwarding is gated with exVld to prevent a combinational feedback
  // loop when exVld=0 (bubble).  The prevWb* tier handles the case where
  // the current wbResult was overwritten by a back-to-back WB write
  // (e.g. LDI → LD) before EX could read it in the previous cycle.
  private val memWbLdDataAvail = ldActive && ldRd =/= 0 && (io.dataBus.rsp.fire || wbLdPhase === 1 || wbLdPhase === 2)
  private val memWbLdData = Mux(io.dataBus.rsp.fire, io.dataBus.rsp.payload, wbLdData)
  private val exFwdRsVal = Mux(memWbLdDataAvail && ldRd === exRsAddr,
                    memWbLdData,
                    Mux(exVld && wbHasRd && wbRd =/= 0 && wbRd === exRsAddr && !ldActive,
                        wbResult,
                    Mux(exVld && prevWbHasRd && prevWbRd =/= 0 && prevWbRd === exRsAddr,
                        prevWbResult,
                        exRsVal)))
  private val exFwdRtVal = Mux(memWbLdDataAvail && ldRd === exRtAddr,
                    memWbLdData,
                    Mux(exVld && wbHasRd && wbRd =/= 0 && wbRd === exRtAddr && !ldActive,
                        wbResult,
                    Mux(exVld && prevWbHasRd && prevWbRd =/= 0 && prevWbRd === exRtAddr,
                        prevWbResult,
                        exRtVal)))

  // ALU
  alu.io.rsVal := exFwdRsVal
  alu.io.opB := Mux(exIsImmEn, exSextVal, exFwdRtVal)
  alu.io.aluFunc := exAluFunc

  // LD/ST effective address (recompute from forwarded values)
  private val exEffAddr2 = (exFwdRsVal.asSInt + exSextVal.asSInt).asBits.resized

  // Branch condition evaluation
  private val exBrTaken = (exIsJMP ||
    (exIsBEQ && (exFwdRsVal === exFwdRtVal)) ||
    (exIsBNE && (exFwdRsVal =/= exFwdRtVal)) ||
    (exIsBLT && (exFwdRsVal.asSInt < exFwdRtVal.asSInt))) && exVld

  // Branch/JMP redirect target
  private val exBrShifted = exSextVal(14 downto 0) ## B"0"
  private val exBrTarget2 = (exPc.asSInt + 2 + exBrShifted.asSInt).asBits.resized
  private val exJmpTarget = exFwdRsVal.asUInt

  private val exBranchTaken = exBrTaken

  // =========================================================================
  // Stall Logic
  // =========================================================================

  // EX stalls when WB stalled, or EX has unserviced LD/ST
  private val stallEx = stallWb ||
                (exVld && exIsLD && !io.dataBus.req.fire) ||
                (exVld && exIsST && !io.dataBus.req.fire)

  // Load-use hazard: ID needs a register that EX or WB is loading
  private val idUsesRs = !decoder.io.isLDI && idVld
  private val idUsesRt = (decoder.io.isALU || decoder.io.isST || decoder.io.isBranch) && idVld

  // Only detect load-use hazard when LD is genuinely in EX, not after
  // it's already been transferred to WB (exServiced=1 means EX→WB fired,
  // so the exVld/exIsLD values are stale). Without this guard, the stale
  // LD in EX would cause a deadlock: loadUseEx blocks ID→EX forever
  // because nothing clears exVld, and EX→WB re-enters the LD state machine.
  private val loadUseEx = exVld && exIsLD && exHasRd && exRd =/= 0 &&
    !exServiced &&
    ((idUsesRs && idRsAddr === exRd) || (idUsesRt && idRtAddr === exRd))

  private val loadUseWb = ldActive && wbLdPhase === 0 && ldRd =/= 0 &&
    ((idUsesRs && idRsAddr === ldRd) || (idUsesRt && idRtAddr === ldRd))

  private val ldUseStall = loadUseEx || loadUseWb

  private val stallId = ldUseStall || stallEx
  private val stallIf = stallId

  // Gate instruction fetch — external ready signal used for IF stall gating
  io.instrRsp.ready := !stallIf && pipelineActive

  // =========================================================================
  // Fetch Buffer Logic — updates from the instruction stream
  // =========================================================================
  // The fetch buffer is gated on !stallIf so that a fetched instruction
  // is not lost when the pipeline stalls before IF can consume it.
  when(io.instrRsp.fire && pipelineActive) {
    fetchBufPc := pc
    fetchBufData := io.instrRsp.payload
    fetchBufVld := True
    pc := pc + 2
  } otherwise {
    when(!io.instrRsp.fire && !stallIf) {
      fetchBufVld := False
    }
  }

  // Part-select on REGISTERED data (safe from Verilator bug)
  private val instrIsLDI = fetchBufVld && fetchBufData(15 downto 12) === B"1111"

  // =========================================================================
  // IF Stage — processes the registered fetch buffer
  // =========================================================================
  // This logic is placed here (after stallIf is defined) so it can preserve
  // ifVld during pipeline stalls by checking !stallIf.
  // Gate fetchBuf processing on !stallIf so that instructions already in IF
  // are not overwritten during a pipeline stall (e.g. LD write-back delay).
  when(fetchBufVld && !stallIf) {
    val fetchedAt = fetchBufPc
    when(!ifLdiPending) {
      when(instrIsLDI) {
        ifLdiPending := True
        ifLdiHeader := fetchBufData
        ifLdiHeaderPc := fetchedAt
        ifVld := False
      } otherwise {
        ifInstr := fetchBufData
        ifPc := fetchedAt
        ifVld := True
        ifLdiData := 0
      }
    } otherwise {
      ifInstr := ifLdiHeader
      ifPc := ifLdiHeaderPc
      ifVld := True
      ifLdiData := fetchBufData
      ifLdiPending := False
    }
  } otherwise {
    when(!ifLdiPending && !stallIf) {
      ifVld := False
    }
  }

  // =========================================================================
  // Data Bus Request
  // =========================================================================

  // LD/ST sends data bus request when in EX
  io.dataBus.req.valid := exVld && (exIsLD || exIsST) && pipelineActive

  // Accept data bus response when WB or EX has a pending LD
  io.dataBus.rsp.ready := (ldActive || (exVld && exIsLD)) && pipelineActive

  // =========================================================================
  // Pipeline Transfer Logic
  // =========================================================================

  // Non-LD result (used by both EX→WB and regfile non-LD write)
  private val nonLdResult = Mux(exIsLDI, exLdiData,
                    Mux(exIsALU || exIsImmEn, alu.io.result, B(0, 16 bits)))

  // EX → WB
  // NOTE: Uses data-path Mux on exVld instead of clock-enable gating,
  // because Verilator 5's CLKENA dead-code elimination removes exVld
  // from `if (!stallWb && exVld)` regardless of -fno-const flags.
  // When exVld=0 (bubble or flushed), the Mux holds the current value.
  // Also captures prevWb* BEFORE overwriting, so forwarding can still
  // see the previous wbResult when a back-to-back write overwrites it.
  when(!stallWb) {
    prevWbHasRd := wbHasRd
    prevWbRd := wbRd
    prevWbResult := wbResult

    wbRd := Mux(exVld, exRd, wbRd)
    wbHasRd := Mux(exVld && !exIsLD, exHasRd, wbHasRd)
    wbResult := Mux(exVld, nonLdResult, wbResult)
    wbIsLD := Mux(exVld, exIsLD, wbIsLD)
    wbIsLDI := Mux(exVld, exIsLDI, wbIsLDI)

    exServiced := True
  }

  // IF → ID (blocked by branch redirect so stale instructions don't enter EX)
  when(!stallId && !exBranchTaken) {
    idInstr := ifInstr
    idPc := ifPc
    idVld := ifVld
    idLdiData := ifLdiData
  }

  // ID → EX (when not stalled by EX or branch flush)
  when(!stallId && !exBranchTaken) {
    exVld := idVld
    exInstr := idInstr
    exPc := idPc
    exRd := (decoder.io.hasRd ? decoder.io.rdField | B"000").asUInt
    exHasRd := decoder.io.hasRd
    exIsALU := decoder.io.isALU
    exIsLD := decoder.io.isLD
    exIsST := decoder.io.isST
    exIsJMP := decoder.io.isJMP
    exIsBEQ := decoder.io.isBEQ
    exIsBNE := decoder.io.isBNE
    exIsBLT := decoder.io.isBLT
    exIsLDI := decoder.io.isLDI
    exIsImmEn := decoder.io.isImmEn
    exAluFunc := decoder.io.aluFunc
    exRsVal := regFile.io.rsVal
    exRtVal := regFile.io.rtVal
    exSextVal := idSextVal
    exEffAddr := idEffAddr
    exBrTarget := idBrTarget
    exLdiData := idLdiData

    exServiced := False
  }

  // Branch/JMP redirect — placed here AFTER pipeline transfers so clearing
  // of pipeline stage valid bits takes priority over the transfer logic.
  when(exBranchTaken) {
    pc := Mux(exIsJMP, exJmpTarget, exBrTarget2.asUInt)
    fetchBufVld := False
    ifVld := False
    ifLdiPending := False
    idVld := False
    exVld := False
  }

  // =========================================================================
  // Data Bus Payload
  // =========================================================================

  io.dataBus.req.payload.addr := exEffAddr2.asUInt
  io.dataBus.req.payload.wrData := exFwdRtVal
  io.dataBus.req.payload.wr := exIsST

  // =========================================================================
  // Register File Write
  // =========================================================================

  // LD writes use ldWriteNow (phase 2) with ldWriteAddr = wbRd.
  // Non-LD writes use WB-stage values: when an instruction with a destination
  // register is in WB and the LD is not writing, it writes its result.
  regFile.io.wrAddr := Mux(ldWriteNow, ldWriteAddr, wbRd)
  regFile.io.wrData := Mux(ldWriteNow, wbLdData, wbResult)
  regFile.io.wrEn := (ldWriteNow && ldWriteAddr =/= 0) ||
                     (wbHasRd && wbRd =/= 0 && !ldActive)

  // Debug bus read port
  regFile.io.auxAddr := Mux(
    busIf.io.cmdStrb && busIf.io.cmdWord(31 downto 24) === 0x06,
    busIf.io.cmdWord(18 downto 16).asUInt,
    U"001"
  )
  io.dbgRegFile1 := regFile.io.auxVal

  // =========================================================================
  // Debug Bus
  // =========================================================================

  busIf.io.cmdDone := False
  busIf.io.rspStrb := False
  busIf.io.rspWord := 0

  when(busIf.io.cmdStrb) {
    switch(busIf.io.cmdWord(31 downto 24)) {
      is(0x03) {
        state := CoreState.IDLE; pc := 0; running := False
        ifLdiPending := False; ifVld := False; idVld := False; exVld := False
        busIf.io.cmdDone := True
      }
      is(0x04) {
        state := CoreState.FETCH; ifLdiPending := False; ifVld := False; idVld := False; exVld := False
        busIf.io.cmdDone := True
      }
      is(0x05) {
        state := CoreState.FETCH; running := True; ifLdiPending := False; ifVld := False; idVld := False; exVld := False
        busIf.io.cmdDone := True
      }
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
  io.dbgState := state.asBits.resize(3 bits)
  io.dbgPC := pc
  io.dbgRunning := running
  io.dbgRd := exRd
  io.dbgAluRes := alu.io.result
  io.dbgInstr := exInstr
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
    assumeInitial(state === CoreState.FETCH)
    assumeInitial(pc === 0)
    assumeInitial(running)
    assumeInitial(!idVld)
    assumeInitial(!exVld)
    assumeInitial(!ifLdiPending)
    assumeInitial(!ifVld)
    assumeInitial(!stallWb)
    assumeInitial(!stallEx)
    assumeInitial(!stallId)
    assumeInitial(!wbIsLD)
    assumeInitial(!wbHasRd)
    assumeInitial(wbRd === 0)

    when(!pastValid()) {
      when(!resetn) {
        assert(state === CoreState.FETCH)
        assert(pc === 0)
        assert(running)
      }
    }

    when(regFile.io.rsAddr === 0) { assert(regFile.io.rsVal === 0) }

    when(pastValid()) {
      when(past(pipelineActive && !stallIf) && !past(ifLdiPending) &&
           !past(exBranchTaken) && !past(stallIf) && resetn) {
        assert(pc === past(pc) + 2 || pc === past(pc))
      }
    }

    cover(state === CoreState.FETCH)
    cover(state === CoreState.FETCH)
    cover(exVld && exIsALU)
    cover(exVld && exIsLD)
    cover(exVld && exIsLDI)
    cover(io.bus.ack)
    cover(io.bus.rsp.valid)
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
