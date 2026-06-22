package risc

import spinal.core._
import spinal.core.formal._
import org.scalatest.funsuite.AnyFunSuite

class RegFileFormalTest extends AnyFunSuite {
  test("TC-RF-1 through TC-RF-4: RegFile formal verification") {
    FormalConfig.withBMC(30).withAsync.doVerify(RegFile())
  }
}
