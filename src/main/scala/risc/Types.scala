package risc

import spinal.core._
import spinal.lib._

import scala.language.postfixOps

object CoreState extends SpinalEnum {
  val IDLE, FETCH, DECODE, LDI_FETCH, WRITEBACK, HALT = newElement()
}

/** ID→EX instruction class (PipCore). Single enum makes illegal
  * state combinations unrepresentable. */
object InstrType extends SpinalEnum {
  val EMPTY, ALU, LD, ST, JMP, BR, LDI, CALL = newElement()
}

/** Load-data FSM phases (PipCore LD unit). SNAPSHOT (Phase 3): request
  * captured at ID→EX entry, not yet issued on the bus. */
object LdPhase extends SpinalEnum {
  val IDLE, SNAPSHOT, WAIT_BUS, DATA_READY = newElement()
}

case class CoreBusIo() extends Bundle with IMasterSlave {
  val cmd: Stream[Bits] = Stream(Bits(8 bits))
  val rsp: Stream[Bits] = Stream(Bits(8 bits))
  val ack: Bool = Bool()

  override def asMaster(): Unit = {
    master(cmd)
    slave(rsp)
    in(ack)
  }
}

trait CoreBusIoComponent {
  def bus(): CoreBusIo
}

case class DataBusReq() extends Bundle {
  val addr: UInt = UInt(16 bits)
  val wrData: Bits = Bits(16 bits)
  val wr: Bool = Bool()
  /** ISA v2.1 §4.6: byte access (LDB/STB) — masks a single byte lane on
    * writes; reads return the full word (the core selects the lane by addr[0]). */
  val isByte: Bool = Bool()
}

case class DataBusIo() extends Bundle with IMasterSlave {
  val req: Stream[DataBusReq] = Stream(new DataBusReq)
  val rsp: Stream[Bits] = Stream(Bits(16 bits))

  override def asMaster(): Unit = {
    master(req)
    slave(rsp)
  }
}
