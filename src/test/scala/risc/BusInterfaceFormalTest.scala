package risc

import org.scalatest.funsuite.AnyFunSuite
import spinal.core.formal._

class BusInterfaceFormalTest extends AnyFunSuite {
  test("TC-BI-1 through TC-BI-6: BusInterface formal verification") {
    FormalConfig.withBMC(30).withAsync.doVerify(BusInterface())
  }
}
