package risc

import org.scalatest.funsuite.AnyFunSuite
import spinal.core.formal._

class AluFormalTest extends AnyFunSuite {
  test("TC-ALU-1 through TC-ALU-8: ALU formal verification") {
    FormalConfig.withBMC(30).withAsync.doVerify(ALU())
  }
}
