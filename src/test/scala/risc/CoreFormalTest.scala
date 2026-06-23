package risc

import org.scalatest.funsuite.AnyFunSuite
import spinal.core.formal._

class CoreFormalTest extends AnyFunSuite {
  test("Core BMC 30") {
    FormalConfig.withBMC(30).withAsync.doVerify(Core())
  }
}
