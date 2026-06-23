package risc

import org.scalatest.funsuite.AnyFunSuite
import spinal.core.formal._

class SocFormalTest extends AnyFunSuite {
  test("TC-SOC-1 through TC-SOC-10: SoC formal verification") {
    FormalConfig.withBMC(10).withAsync.doVerify(new Soc())
  }
}
