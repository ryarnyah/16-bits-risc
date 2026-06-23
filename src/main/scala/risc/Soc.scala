package risc

import spinal.core._

import scala.language.postfixOps

/**
 * Top-level SoC — 16-bit RISC core with external data RAM and serial UART.
 *
 * Core fetches instructions from its internal program memory and accesses data
 * via a stream-based data bus.  SoC provides:
 *   - Data RAM for LD/ST at addresses < 0x1FFC
 *   - UART for LD/ST at addresses >= 0x1FFC (0x1FFC = RX, 0x1FFE = TX)
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
    instrRom.initBigInt(words)
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

  // ── Address decode ──
  private val isIoAddr = core.io.dataBus.req.addr >= 0x1FFC
  private val dataWordAddr = core.io.dataBus.req.addr(15 downto 1).resize(log2Up(4096))

  // ── Data bus request: accept ──
  // RAM: always ready; UART write: ready when TX FIFO not full; UART read: always ready
  core.io.dataBus.req.ready := Mux(isIoAddr,
    Mux(core.io.dataBus.req.wr, uart.io.txReady, True),
    True)

  // ── UART write ──
  uart.io.txVld := core.io.dataBus.req.fire && isIoAddr && core.io.dataBus.req.wr
  uart.io.txData := core.io.dataBus.req.wrData(7 downto 0)

  // ── UART read (pending/response state machine) ──
  private val uartReadPending = RegInit(False)
  private val uartRspVld = RegInit(False)
  private val uartRdData = Reg(Bits(8 bits))

  when(core.io.dataBus.req.fire && isIoAddr && !core.io.dataBus.req.wr) {
    uartReadPending := True
  }
  when(uartReadPending && uart.io.rxVld) {
    uartRdData := uart.io.rxData
    uartRspVld := True
    uartReadPending := False
  }
  when(core.io.dataBus.rsp.fire) {
    uartRspVld := False
  }

  uart.io.rxPop := uartReadPending && uart.io.rxVld

  // ── Data RAM ──
  private val ramRdData = dataRam.readSync(dataWordAddr)
  private val ramRspVld = RegNext(core.io.dataBus.req.fire && !isIoAddr && !core.io.dataBus.req.wr)

  dataRam.write(dataWordAddr, core.io.dataBus.req.wrData,
    core.io.dataBus.req.fire && !isIoAddr && core.io.dataBus.req.wr)

  // ── Data bus response ──
  core.io.dataBus.rsp.valid := ramRspVld || uartRspVld
  core.io.dataBus.rsp.payload := Mux(ramRspVld, ramRdData, B(0, 8 bits) ## uartRdData)
}

object Soc extends App {
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
  ).generateSystemVerilog(new Soc(hexPath))
}
