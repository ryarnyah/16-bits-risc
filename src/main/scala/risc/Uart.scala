package risc

import spinal.core._
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
        rxFifo.io.push.valid := True
        rxFifo.io.push.payload := rxReg
        rxBusy := False
      }
    }
  }
}
