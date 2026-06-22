package risc

import spinal.core._
import spinal.core.formal._
import org.scalatest.funsuite.AnyFunSuite

class CoreFormalTest extends AnyFunSuite {
  test("Core BMC 30") {
    FormalConfig.withBMC(30).withAsync.doVerify(Core(CoreConfig(memWordCount = 8)))
  }
}
