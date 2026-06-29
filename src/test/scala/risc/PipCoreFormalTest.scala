package risc

import org.scalatest.funsuite.AnyFunSuite
import spinal.core.formal._

class PipCoreFormalTest extends AnyFunSuite {
  test("PipCore BMC 30") {
    FormalConfig.withBMC(30).withAsync.doVerify(PipCore())
  }
}
