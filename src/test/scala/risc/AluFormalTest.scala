package risc

import spinal.core._
import spinal.core.formal._
import org.scalatest.funsuite.AnyFunSuite

class AluFormalTest extends AnyFunSuite {
  test("TC-ALU-1 through TC-ALU-8: ALU formal verification") {
    FormalConfig.withBMC(30).withAsync.doVerify(ALU())
  }
}
