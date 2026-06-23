package risc

import org.scalatest.funsuite.AnyFunSuite
import spinal.core.formal._

class UartFormalTest extends AnyFunSuite {
  test("TC-UART-1 through TC-UART-10: UART formal verification") {
    FormalConfig.withBMC(30).withAsync.doVerify(new Uart())
  }
}
