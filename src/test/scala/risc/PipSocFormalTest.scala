package risc

import org.scalatest.funsuite.AnyFunSuite
import spinal.core.formal._

class PipSocFormalTest extends AnyFunSuite {
  test("PipSoc BMC 30") {
    FormalConfig.withBMC(30).withAsync.doVerify(new PipSoc(""))
  }
}
