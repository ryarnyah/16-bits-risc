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

  private val isIoAddr = core.io.dataBus.req.addr >= 0x1FFC
  private val dataWordAddr = core.io.dataBus.req.addr(15 downto 1).resize(log2Up(4096))

  core.io.dataBus.req.ready := Mux(isIoAddr,
    Mux(core.io.dataBus.req.wr, uart.io.txReady, True),
    True)

  // === Instruction ROM (async read, compile-time init only when hexPath provided) ===
  private val instrWordAddr = core.io.instrAddr(15 downto 1).resize(log2Up(4096))
  core.io.instrRsp.valid := True
  core.io.instrRsp.payload := instrRom.readAsync(instrWordAddr)

  uart.io.txVld := core.io.dataBus.req.fire && isIoAddr && core.io.dataBus.req.wr
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
  when(core.io.dataBus.req.fire && isIoAddr && !core.io.dataBus.req.wr) {
    uartReadPending := True
  }

  uart.io.rxPop := uartReadPending && uart.io.rxVld

  private val ramRdData = dataRam.readAsync(dataWordAddr)
  private val ramRspVld = core.io.dataBus.req.fire && !isIoAddr && !core.io.dataBus.req.wr

  dataRam.write(dataWordAddr, core.io.dataBus.req.wrData,
    core.io.dataBus.req.fire && !isIoAddr && core.io.dataBus.req.wr)

  core.io.dataBus.rsp.valid := ramRspVld || uartRspVld
  core.io.dataBus.rsp.payload := Mux(ramRspVld, ramRdData, B(0, 8 bits) ## uartRdData)

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
    assumeInitial(!pReqFire)
    assumeInitial(!pIsIo)
    assumeInitial(!pReqWr)
    assumeInitial(!pRamRdReq)
    assumeInitial(!pUartCond)
    assumeInitial(!ramRspVld)
    assumeInitial(!uartReadPending)
    assumeInitial(!uartRspVld)
    assumeInitial(uartRdData === 0)

    assert(isIoAddr === (core.io.dataBus.req.addr >= 0x1FFC))

    // Async RAM: ramRspVld is combinational (same cycle as req.fire)
    when(core.io.dataBus.req.fire && !isIoAddr && !core.io.dataBus.req.wr) {
      assert(ramRspVld)
    }

    when(core.io.dataBus.req.fire && isIoAddr && core.io.dataBus.req.wr) {
      assert(uart.io.txVld)
      assert(uart.io.txData === core.io.dataBus.req.wrData(7 downto 0))
    }
    when(!(core.io.dataBus.req.fire && isIoAddr && core.io.dataBus.req.wr)) {
      assert(!uart.io.txVld)
    }

    when(!isIoAddr) {
      assert(core.io.dataBus.req.ready)
    }
    when(isIoAddr && core.io.dataBus.req.wr) {
      assert(core.io.dataBus.req.ready === uart.io.txReady)
    }
    when(isIoAddr && !core.io.dataBus.req.wr) {
      assert(core.io.dataBus.req.ready)
    }

    when(pastValid()) {
      when(pReqFire && pIsIo && !pReqWr) {
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

    assert(core.io.dataBus.rsp.valid === (ramRspVld || uartRspVld))

    when(ramRspVld && !uartRspVld) {
      assert(core.io.dataBus.rsp.payload === ramRdData)
    }
    when(!ramRspVld && uartRspVld) {
      assert(core.io.dataBus.rsp.payload === B(0, 8 bits) ## uartRdData)
    }
    when(!ramRspVld && !uartRspVld) {
      assert(!core.io.dataBus.rsp.valid)
    }

    cover(!resetn && pastValid())
    cover(core.io.dataBus.req.fire)
    cover(uartReadPending)
    cover(uartRspVld)
  }
}

object PipSoc extends App {
  val hexPath = if (args.length > 0) args(0) else ""
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
  ).generateSystemVerilog(new PipSoc(hexPath))
}
