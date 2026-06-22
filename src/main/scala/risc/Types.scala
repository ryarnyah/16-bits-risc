package risc

import spinal.core._
import spinal.lib._
import scala.language.postfixOps

object CoreState extends SpinalEnum {
  val IDLE, FETCH, DECODE, EXECUTE, LDI_FETCH, WRITEBACK = newElement()
}

object ALUOp extends SpinalEnum {
  val ADD, SUB, AND, OR, XOR, SLL, SRL = newElement()
}

case class CoreConfig(memWordCount: Int = 4096) {
  require(isPow2(memWordCount), "memWordCount must be power of 2")
}

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

case class CoreBundle() extends Bundle {
  val bus = CoreBusIo()
}
