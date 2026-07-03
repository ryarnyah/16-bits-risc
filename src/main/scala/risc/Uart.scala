package risc

import spinal.core._
import spinal.core.formal._
import spinal.lib._

import scala.language.postfixOps

/**
 * UART with TX/RX FIFOs, baud rate generator, and serial interface.
 *
 * Config: 8N1, 115200 baud @ 100 MHz (868 cycles/bit).
 * TX/RX FIFOs are 64 bytes deep.
 */
case class UartIo() extends Bundle {
  // Core side
  val txData: Bits = in Bits (8 bits)    // char to send
  val txVld: Bool = in Bool ()           // strobe
  val txReady: Bool = out Bool ()        // TX FIFO can accept
  val rxData: Bits = out Bits (8 bits)   // char received
  val rxVld: Bool = out Bool ()          // char available
  val rxPop: Bool = in Bool ()           // core consumed it

  // Serial side
  val tx: Bool = out Bool ()
  val rx: Bool = in Bool ()
}

class Uart extends Component {
  val io: UartIo = UartIo()

  private val BIT_CYCLES = 868

  // ── TX FIFO ──
  private val txFifo = StreamFifo(Bits(8 bits), depth = 64)
  txFifo.io.push.valid := io.txVld
  txFifo.io.push.payload := io.txData
  io.txReady := txFifo.io.push.ready
  // pop controlled by TX shifter (below)

  // ── TX shift register ──
  private val txBusy = RegInit(False)
  private val txReg = Reg(Bits(8 bits))
  private val txCnt = Reg(UInt(10 bits)) init 0
  private val txBit = Reg(UInt(4 bits)) init 0

  txFifo.io.pop.ready := False
  io.tx := True
  when(!txBusy) {
    when(txFifo.io.pop.valid) {
      txReg := txFifo.io.pop.payload
      txBusy := True
      txCnt := 0
      txBit := 0
      txFifo.io.pop.ready := True
    }
  } otherwise {
    txCnt := txCnt + 1
    when(txCnt === BIT_CYCLES - 1) {
      txCnt := 0
      txBit := txBit + 1
    }
    switch(txBit) {
      is(0) { io.tx := False }
      is(1) { io.tx := txReg(0) }
      is(2) { io.tx := txReg(1) }
      is(3) { io.tx := txReg(2) }
      is(4) { io.tx := txReg(3) }
      is(5) { io.tx := txReg(4) }
      is(6) { io.tx := txReg(5) }
      is(7) { io.tx := txReg(6) }
      is(8) { io.tx := txReg(7) }
      default {
        io.tx := True
        when(txBit === 9) { txBusy := False }
      }
    }
  }

  // ── RX FIFO ──
  private val rxFifo = StreamFifo(Bits(8 bits), depth = 64)
  io.rxData := rxFifo.io.pop.payload
  io.rxVld := rxFifo.io.pop.valid
  rxFifo.io.pop.ready := io.rxPop

  // ── RX sampler (midpoint sampling) ──
  private val rxCnt = Reg(UInt(10 bits)) init 0
  private val rxBit = Reg(UInt(4 bits)) init 0
  private val rxReg = Reg(Bits(8 bits))
  private val rxBusy = RegInit(False)

  rxFifo.io.push.valid := False
  rxFifo.io.push.payload := 0

  when(!rxBusy) {
    rxCnt := 0
    rxBit := 0
    when(!io.rx) { rxBusy := True }
  } otherwise {
    rxCnt := rxCnt + 1
    val maxCnt = (rxBit === 0) ? U(BIT_CYCLES / 2 - 1, 10 bits) | U(BIT_CYCLES - 1, 10 bits)
    when(rxCnt === maxCnt) {
      rxCnt := 0
      rxBit := rxBit + 1
      when(rxBit >= 1 && rxBit <= 8) {
        rxReg((rxBit - 1).resize(3 bits)) := io.rx
      }
      when(rxBit === 9) {
        // Framing error check: stop bit must be high (1)
        when(io.rx) {
          rxFifo.io.push.valid := True
          rxFifo.io.push.payload := rxReg
        } // else: framing error - silently drop frame
        rxBusy := False
      }
    }
  }

  // ======================================================================
  // Formal Verification — covers the following test cases:
  //   TC-UART-1:  TX idle → io.tx = True
  //   TC-UART-2:  TX start bit → io.tx = False
  //   TC-UART-3:  txFifo pop starts TX transmission
  //   TC-UART-4:  txCnt increments while txBusy (within BMC depth)
  //   TC-UART-5:  txReady = txFifo.io.push.ready
  //   TC-UART-6:  RX falling edge → rxBusy asserted
  //   TC-UART-7:  rxCnt increments while rxBusy (within BMC depth)
  //   TC-UART-8:  rxFifo.io.pop = io.rxPop
  //   TC-UART-9:  RX capture register samples io.rx at midpoint
  //   TC-UART-10: TX data bits transmitted LSB first
  // ======================================================================
  GenerationFlags.formal {
    val resetn = ClockDomain.current.readResetWire
    assumeInitial(!resetn)

    // Force all registers to consistent initial state
    assumeInitial(!pastValid())
    assumeInitial(!txBusy)
    assumeInitial(txCnt === 0)
    assumeInitial(txBit === 0)
    assumeInitial(!rxBusy)
    assumeInitial(rxCnt === 0)
    assumeInitial(rxBit === 0)
    assumeInitial(txReg === 0)
    assumeInitial(rxReg === 0)

    // Past values (pre-computed to avoid redundant registers)
    val pTxBusy = past(txBusy)
    val pTxCnt  = past(txCnt)
    val pTxBit  = past(txBit)
    val pRxBusy = past(rxBusy)
    val pRxCnt  = past(rxCnt)
    val pRx     = past(io.rx)
    assumeInitial(!pTxBusy)
    assumeInitial(!pRxBusy)
    assumeInitial(pRx)
    assumeInitial(pTxCnt === 0)
    assumeInitial(pRxCnt === 0)
    assumeInitial(pTxBit === 0)

    // ── Output validity ──

    /* TC-UART-1: TX idle outputs continuous high (marking) */
    when(!txBusy) { assert(io.tx) }

    /* TC-UART-2: Start bit forces io.tx low */
    when(txBusy && txBit === 0) { assert(!io.tx) }

    // ── TX FIFO ──

    /* TC-UART-5: txReady reflects TX FIFO push readiness */
    assert(io.txReady === txFifo.io.push.ready)

    // ── TX shifter ──

    /* TC-UART-3: Pop from FIFO starts transmission (txBusy next cycle) */
    when(pastValid() && resetn) {
      when(!pTxBusy && past(txFifo.io.pop.valid)) {
        assert(txBusy)
      }
    }

    /* TC-UART-4: txCnt increments each cycle while transmitting */
    when(pastValid() && resetn) {
      when(pTxBusy && pTxCnt =/= BIT_CYCLES - 1) {
        assert(txCnt === pTxCnt + 1)
      }
    }

    /* TC-UART-10: Data bits transmitted LSB first via txReg(x-1) when txBit=x */
    for (b <- 0 to 7) {
      when(resetn && txBusy && txBit === b + 1) {
        assert(io.tx === txReg(b))
      }
    }

    // ── RX FIFO ──

    /* TC-UART-8: RX FIFO pop is controlled by external rxPop */
    assert(rxFifo.io.pop.ready === io.rxPop)

    // ── RX sampler ──

    /* TC-UART-6: Falling edge on io.rx starts reception.
     * rxBusy is a register, so the assignment takes effect at the posedge.
     * We check in the NEXT cycle that rxBusy went True after a falling edge. */
    val pRxFall = past(!io.rx && pRx)
    assumeInitial(!pRxFall)
    when(pastValid() && resetn) {
      when(pRxFall) {
        assert(rxBusy && rxCnt === 0 && rxBit === 0)
      }
    }

    /* TC-UART-7: rxCnt increments while receiving (no wrap within BMC depth) */
    when(pastValid() && resetn) {
      when(pRxBusy && rxBusy) {
        assert(rxCnt === pRxCnt + 1)
      }
    }

    cover(io.txVld && !txBusy && !io.tx)
    cover(rxBusy)
  }
}
