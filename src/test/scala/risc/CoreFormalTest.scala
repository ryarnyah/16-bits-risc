package risc

import spinal.core._
import spinal.core.formal._
import org.scalatest.funsuite.AnyFunSuite

case class CoreFormalTest() extends AnyFunSuite {
  test("Core must be formally tested") {
    FormalConfig.withBMC(50).doVerify(Core(CoreConfig(memWordCount = 8)))
  }
}
