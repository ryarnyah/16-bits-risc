package risc

import spinal.core._
import spinal.lib._
import spinal.core.formal._
import scala.language.postfixOps

case class Core(config: CoreConfig = CoreConfig()) extends Component with CoreBusIoComponent {
  val io = new Bundle {
    val bus = CoreBusIo()
  }
  io.bus.cmd.asSlave()
  io.bus.rsp.asMaster()
  io.bus.ack.asOutput()

  val memAddrBits = log2Up(config.memWordCount)

  val state = RegInit(CoreState.IDLE)
  val running = Reg(Bool) init False
  val PC = Reg(UInt(16 bits)) init 0

  val instr = Reg(Bits(16 bits))
  val rd = Reg(UInt(3 bits))
  val aluRes = Reg(Bits(16 bits))

  val regFile = Mem(Bits(16 bits), wordCount = 8) init Vec(Seq.fill(8)(B(0, 16 bits)))
  val memory = Mem(Bits(16 bits), wordCount = config.memWordCount)

  val byteAddrBits = log2Up(config.memWordCount * 2)
  val pcWordAddr = PC(byteAddrBits - 1 downto 1).resize(memAddrBits)
  val fetchedInstr = memory.readSync(pcWordAddr)

  val cmdBuf = Reg(Bits(32 bits))
  val cmdCnt = Counter(4, inc = io.bus.cmd.fire)
  val cmdPhrase = RegInit(False)
  io.bus.cmd.ready := True

  when(io.bus.cmd.fire) {
    cmdBuf := cmdBuf(23 downto 0) ## io.bus.cmd.payload
    when(cmdCnt.willOverflow) {
      cmdPhrase := True
    }
  }

  val busWordCmd = Stream(Bits(32 bits))
  busWordCmd.valid := cmdPhrase
  busWordCmd.payload := cmdBuf
  busWordCmd.ready := True
  when(busWordCmd.fire) {
    cmdPhrase := False
  }

  val rspBuf = Reg(Bits(32 bits))
  val rspCnt = Counter(4, inc = io.bus.rsp.fire)
  val rspPhrase = RegInit(False)
  io.bus.rsp.valid := rspPhrase
  io.bus.rsp.payload := rspBuf(31 downto 24)

  when(io.bus.rsp.fire) {
    rspBuf := rspBuf(23 downto 0) ## B"00000000"
    when(rspCnt.willOverflow) {
      rspPhrase := False
    }
  }

  val cmdDone = RegInit(False)
  cmdDone := False
  io.bus.ack := RegNext(cmdDone)

  val memPtr = Reg(UInt(memAddrBits bits)) init 0

  val opcode = instr(15 downto 12)
  val rdField = instr(11 downto 9)
  val rsReg = instr(8 downto 6)
  val rtReg = instr(5 downto 3)
  val imm6 = instr(5 downto 0)

  val isALU = !opcode(3) || (opcode === B"1000")
  val isLD  = opcode === B"1001"
  val isST  = opcode === B"1010"
  val isJMP = opcode === B"1011"
  val isBEQ = opcode === B"1100"
  val isBNE = opcode === B"1101"
  val isBLT = opcode === B"1110"
  val isLDI = opcode === B"1111"
  val isBranch = isBEQ || isBNE || isBLT
  val isImmEn = (opcode === B"0001") || (opcode === B"0011")
  val hasRd = isALU || isLD || isLDI

  val rsAddr = ((isBranch || isJMP) ? instr(11 downto 9) | rsReg).asUInt
  val rtAddr = (isBranch ? instr(8 downto 6) | rtReg).asUInt

  val rsVal = regFile.readAsync(rsAddr)
  val rtVal = regFile.readAsync(rtAddr)
  val stVal = regFile.readAsync(rdField.asUInt)

  val sextVal = imm6.asSInt.resize(16).asBits

  val opB = isImmEn ? sextVal | rtVal

  val opcU = opcode.asUInt
  val aluFunc = Bits(3 bits)
  aluFunc := 0
  when(opcU < 4) { aluFunc := (opcU >> 1).asBits.resized }
  when(opcU >= 4) { aluFunc := (opcU - 2).asBits.resized }

  val aluWire = Bits(16 bits)
  aluWire := rsVal
  switch(aluFunc) {
    is(0) { aluWire := (rsVal.asSInt + opB.asSInt).asBits }
    is(1) { aluWire := rsVal ^ opB }
    is(2) { aluWire := (rsVal.asSInt - opB.asSInt).asBits }
    is(3) { aluWire := rsVal & opB }
    is(4) { aluWire := rsVal | opB }
    is(5) { aluWire := rsVal |<< opB(3 downto 0).asUInt }
    is(6) { aluWire := rsVal |>> opB(3 downto 0).asUInt }
  }

  val effAddr = (rsVal.asSInt + sextVal.asSInt).asBits.resized
  val dataWordAddr = effAddr(15 downto 1).asUInt.resize(memAddrBits)
  val dataLoad = memory.readSync(dataWordAddr)

  val brShifted = sextVal(14 downto 0) ## B"0"
  val brTarget = (PC.asSInt + brShifted.asSInt).asBits.resized

  val brTaken = Bool
  brTaken := False
  switch(opcode) {
    is(B"1100") { brTaken := (rsVal === rtVal) }
    is(B"1101") { brTaken := (rsVal =/= rtVal) }
    is(B"1110") { brTaken := (rsVal.asSInt < rtVal.asSInt) }
  }

  when(busWordCmd.fire) {
    switch(busWordCmd.payload(31 downto 24)) {
      is(0x01) { memPtr := busWordCmd.payload(memAddrBits - 1 downto 0).asUInt; cmdDone := True }
      is(0x02) {
        memory.write(memPtr, busWordCmd.payload(15 downto 0), True)
        memPtr := memPtr + 1
        cmdDone := True
      }
      is(0x03) { state := CoreState.IDLE; PC := 0; running := False; cmdDone := True }
      is(0x04) { state := CoreState.FETCH; cmdDone := True }
      is(0x05) { state := CoreState.FETCH; running := True; cmdDone := True }
      is(0x06) {
        val regIdx = busWordCmd.payload(18 downto 16).asUInt
        rspPhrase := True
        rspBuf := B(0, 16 bits) ## regFile.readAsync(regIdx)
      }
      is(0x07) {
        val memIdx = busWordCmd.payload(memAddrBits - 1 downto 0).asUInt
        rspPhrase := True
        rspBuf := B(0, 16 bits) ## memory.readAsync(memIdx)
      }
      is(0x08) {
        rspPhrase := True
        rspBuf := B(0, 16 bits) ## PC.asBits
      }
    }
  }

  when(rspCnt.willOverflow) {
    cmdDone := True
  }

  def done(): Unit = {
    when(running) { state := CoreState.FETCH } otherwise { state := CoreState.IDLE }
  }

  switch(state) {
    is(CoreState.FETCH) {
      instr := fetchedInstr
      PC := PC + 2
      state := CoreState.DECODE
    }
    is(CoreState.DECODE) {
      rd := (hasRd ? rdField | B"000").asUInt
      when(isALU) { aluRes := aluWire; state := CoreState.WRITEBACK }
      when(isLD) { state := CoreState.WRITEBACK }
      when(isST) { memory.write(dataWordAddr, stVal, True); done() }
      when(isJMP) { PC := rsVal.asUInt; done() }
      when(isBranch) {
        when(brTaken) { PC := brTarget.asUInt }
        done()
      }
      when(isLDI) {
        PC := PC + 2
        state := CoreState.LDI_FETCH
      }
    }
    is(CoreState.LDI_FETCH) {
      aluRes := fetchedInstr
      state := CoreState.WRITEBACK
    }
    is(CoreState.WRITEBACK) {
      when(isLD) { aluRes := dataLoad }
      when(rd =/= 0) { regFile.write(rd, aluRes) }
      done()
    }
    default {}
  }

  override def bus(): CoreBusIo = io.bus
}

object CoreFormal {
  GenerationFlags.formal {
    val c = Component.current
    val core = c.asInstanceOf[Core]

    assumeInitial(ClockDomain.current.isResetActive)
    anyseq(core.io.bus.cmd.valid)
    anyseq(core.io.bus.cmd.payload)
    anyseq(core.io.bus.rsp.ready)

    assert(core.state === CoreState.IDLE)
    assert(core.PC === 0)
    assert(!core.running)

    when(pastValid()) {
      when(past(core.state) === CoreState.FETCH) {
        assert(core.state === CoreState.DECODE)
      }
    }

    cover(core.state === CoreState.FETCH)
    cover(core.state === CoreState.DECODE)
    cover(core.state === CoreState.WRITEBACK)
    cover(core.state === CoreState.LDI_FETCH)
    cover(core.io.bus.ack)
    cover(core.io.bus.rsp.valid)
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
