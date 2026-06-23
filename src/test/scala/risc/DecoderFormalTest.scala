package risc

import org.scalatest.funsuite.AnyFunSuite
import spinal.core.formal._

class DecoderFormalTest extends AnyFunSuite {
  test("TC-DEC-1 through TC-DEC-6: Decoder formal verification") {
    FormalConfig.withBMC(30).withAsync.doVerify(Decoder())
  }
}
