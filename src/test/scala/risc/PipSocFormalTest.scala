package risc

import org.scalatest.funsuite.AnyFunSuite
import spinal.core.formal._

class PipSocFormalTest extends AnyFunSuite {
  test("PipSoc BMC 50") {
    FormalConfig.withBMC(50).withAsync.doVerify(new PipSoc(""))
  }
}
