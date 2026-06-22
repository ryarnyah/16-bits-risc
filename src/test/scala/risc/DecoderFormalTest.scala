package risc

import spinal.core._
import spinal.core.formal._
import org.scalatest.funsuite.AnyFunSuite

class DecoderFormalTest extends AnyFunSuite {
  test("TC-DEC-1 through TC-DEC-6: Decoder formal verification") {
    FormalConfig.withBMC(30).withAsync.doVerify(Decoder())
  }
}
