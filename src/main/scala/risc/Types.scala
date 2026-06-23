package risc

import spinal.core._
import spinal.lib._

import scala.language.postfixOps

object CoreState extends SpinalEnum {
  val IDLE, FETCH, DECODE, LDI_FETCH, WRITEBACK = newElement()
}

case class CoreConfig()

case class CoreBusIo() extends Bundle with IMasterSlave {
  val cmd = Stream(Bits(8 bits))
  val rsp = Stream(Bits(8 bits))
  val ack = Bool()

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
}

case class DataBusIo() extends Bundle with IMasterSlave {
  val req: Stream[DataBusReq] = Stream(new DataBusReq)
  val rsp: Stream[Bits] = Stream(Bits(16 bits))

  override def asMaster(): Unit = {
    master(req)
    slave(rsp)
  }
}
