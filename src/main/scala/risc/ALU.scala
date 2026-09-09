package risc

import spinal.core._
import spinal.lib.Reverse

import scala.language.postfixOps

/**
 * Arithmetic-Logic Unit for the 16-bit RISC core.
 *
 * Performs all computational operations using three input bits (aluFunc)
 * to select the function. All operations are combinational (no state).
 *
 * == ALU function encoding ==
 * {{{
 *   000 — ADD : signed addition     result = rsVal + opB
 *   001 — XOR : bitwise XOR         result = rsVal ^ opB
 *   010 — SUB : signed subtraction  result = rsVal - opB
 *   011 — AND : bitwise AND         result = rsVal & opB
 *   100 — OR  : bitwise OR          result = rsVal | opB
 *   101 — SLL : logical left shift  result = rsVal << opB[3:0]
 *   110 — SRL : logical right shift result = rsVal >> opB[3:0]
 *   111 — reserved (no-op)
 * }}}
 */
case class AluIo() extends Bundle {
  /** First operand (typically Rs value from register file). */
  val rsVal: Bits = in Bits (16 bits)
  /** Second operand (either Rt value or sign-extended immediate). */
  val opB: Bits = in Bits (16 bits)
  /** ALU function select (3 bits, encoded as above). */
  val aluFunc: Bits = in Bits (3 bits)
  /** Computation result (combinational). */
  val result: Bits = out Bits (16 bits)
}

case class ALU() extends Component {
  val io: AluIo = AluIo()

  io.result := 0

  // ---- 1. Shared adder for ADD (000) and SUB (010) ----
  private val isSub = io.aluFunc === 2
  private val opB_adj = isSub ? ((~io.opB).asUInt + 1) | io.opB.asUInt   // two's complement negation
  private val arithResult = (io.rsVal.asUInt + opB_adj).asBits           // ADD when isSub=0, SUB when isSub=1

  // ---- 2. Shared shifter for SLL (101) and SRL (110) ----
  private val isSRL = io.aluFunc === 6
  private val shiftAmt = io.opB(3 downto 0).asUInt       // 4-bit shift amount
  // Reverse is pure wiring – zero LUTs
  private val shiftInput = isSRL ? Reverse(io.rsVal) | io.rsVal
  private val shifted = shiftInput |<< shiftAmt  // resized to original width
  private val shiftResult = isSRL ? Reverse(shifted) | shifted

  // ---- 3. Logic operations (XOR, AND, OR) ----
  private val logicResult = Bits(io.result.getBitsWidth bits)
  switch(io.aluFunc) {
    is(B"001") { logicResult := io.rsVal ^ io.opB }
    is(B"011") { logicResult := io.rsVal & io.opB }
    is(B"100") { logicResult := io.rsVal | io.opB }
    default { logicResult := 0 }
  }

  // ---- 4. Final small multiplexer (only 4 groups) ----
  switch(io.aluFunc) {
    is(B"000") { io.result := arithResult }   // ADD
    is(B"010") { io.result := arithResult }   // SUB
    is(B"001", B"011", B"100") { io.result := logicResult } // XOR, AND, OR
    is(B"101", B"110")    { io.result := shiftResult } // SLL, SRL
    default     { io.result := 0 }
  }

  // ======================================================================
  // Formal Verification — covers the following test cases:
  //   TC-ALU-1: ADD (aluFunc=000) computes signed addition          (ISA §4.1)
  //   TC-ALU-2: XOR (aluFunc=001) computes bitwise XOR              (ISA §4.7)
  //   TC-ALU-3: SUB (aluFunc=010) computes signed subtraction       (ISA §4.2)
  //   TC-ALU-4: AND (aluFunc=011) computes bitwise AND              (ISA §4.5)
  //   TC-ALU-5: OR  (aluFunc=100) computes bitwise OR               (ISA §4.6)
  //   TC-ALU-6: SLL (aluFunc=101) computes logical left shift       (ISA §4.3)
  //   TC-ALU-7: SRL (aluFunc=110) computes logical right shift      (ISA §4.4)
  //   TC-ALU-8: Reserved (aluFunc=111) produces 0
  // ======================================================================
  GenerationFlags.formal {
    val resetn = ClockDomain.current.readResetWire
    assumeInitial(!resetn)

    /* TC-ALU-1: ADD */
    when(io.aluFunc === B"000") {
      assert(io.result === (io.rsVal.asSInt + io.opB.asSInt).asBits)
    }

    /* TC-ALU-2: XOR */
    when(io.aluFunc === B"001") {
      assert(io.result === (io.rsVal ^ io.opB))
    }

    /* TC-ALU-3: SUB */
    when(io.aluFunc === B"010") {
      assert(io.result === (io.rsVal.asSInt - io.opB.asSInt).asBits)
    }

    /* TC-ALU-4: AND */
    when(io.aluFunc === B"011") {
      assert(io.result === (io.rsVal & io.opB))
    }

    /* TC-ALU-5: OR */
    when(io.aluFunc === B"100") {
      assert(io.result === (io.rsVal | io.opB))
    }

    /* TC-ALU-6: SLL */
    when(io.aluFunc === B"101") {
      assert(io.result === (io.rsVal |<< io.opB(3 downto 0).asUInt))
    }

    /* TC-ALU-7: SRL */
    when(io.aluFunc === B"110") {
      assert(io.result === (io.rsVal |>> io.opB(3 downto 0).asUInt))
    }

    /* TC-ALU-8: Reserved */
    when(io.aluFunc === B"111") {
      assert(io.result === 0)
    }

    cover(io.aluFunc === B"000")
    cover(io.aluFunc === B"001")
  }
}
