package risc

import spinal.core._

import scala.language.postfixOps

/**
 * Arithmetic-Logic Unit for the 16-bit RISC core.
 *
 * Performs all computational operations using a 4-bit aluFunc select.
 * All operations are combinational (no state).
 *
 * == ALU function encoding (ISA v2.1 §9.5) ==
 * {{{
 *   0000 — ADD  : signed addition          result = rsVal + opB
 *   0001 — XOR  : bitwise XOR              result = rsVal ^ opB
 *   0010 — SUB  : signed subtraction       result = rsVal - opB
 *   0011 — AND  : bitwise AND              result = rsVal & opB
 *   0100 — OR   : bitwise OR               result = rsVal | opB
 *   0101 — SLL  : logical left shift       result = rsVal << opB[3:0]
 *   0110 — SRL  : logical right shift      result = rsVal >> opB[3:0]
 *   0111 — reserved (no-op, produces 0)
 *   1000 — SLT  : signed set-less-than     result = (rsVal <s opB) ? 1 : 0   (v2.1)
 *   1001 — SLTU : unsigned set-less-than   result = (rsVal <u opB) ? 1 : 0   (v2.1)
 *   1010 — SRA  : arithmetic right shift   result = rsVal >>>s opB[3:0]      (v2.1)
 *   others — reserved (no-op, produces 0)
 *
 * The 4-bit map is the v2.0 3-bit map zero-extended, so v2.0 encodings
 * are bit-compatible.
 * }}}
 */
case class AluIo() extends Bundle {
  /** First operand (typically Rs value from register file). */
  val rsVal: Bits = in Bits (16 bits)
  /** Second operand (either Rt value or immediate). */
  val opB: Bits = in Bits (16 bits)
  /** ALU function select (4 bits, encoded as above). */
  val aluFunc: Bits = in Bits (4 bits)
  /** Computation result (combinational). */
  val result: Bits = out Bits (16 bits)
}

case class ALU() extends Component {
  val io: AluIo = AluIo()

  io.result := 0
  switch(io.aluFunc) {
    is(B"0000") { io.result := (io.rsVal.asSInt + io.opB.asSInt).asBits }   // ADD (ISA §4.1)
    is(B"0001") { io.result := io.rsVal ^ io.opB }                          // XOR (ISA §4.7)
    is(B"0010") { io.result := (io.rsVal.asSInt - io.opB.asSInt).asBits }   // SUB (ISA §4.2)
    is(B"0011") { io.result := io.rsVal & io.opB }                          // AND (ISA §4.5)
    is(B"0100") { io.result := io.rsVal | io.opB }                          // OR  (ISA §4.6)
    is(B"0101") { io.result := io.rsVal |<< io.opB(3 downto 0).asUInt }     // SLL (ISA §4.3)
    is(B"0110") { io.result := io.rsVal |>> io.opB(3 downto 0).asUInt }     // SRL (ISA §4.4)
    is(B"1000") { io.result := (io.rsVal.asSInt < io.opB.asSInt).asBits.resize(16) }  // SLT  (v2.1 §5.3)
    is(B"1001") { io.result := (io.rsVal.asUInt < io.opB.asUInt).asBits.resize(16) }  // SLTU (v2.1 §5.3)
    is(B"1010") { io.result := (io.rsVal.asSInt >> io.opB(3 downto 0).asUInt).asBits } // SRA  (v2.1 §5.3)
  }

  // ======================================================================
  // Formal Verification — covers the following test cases:
  //   TC-ALU-1: ADD (aluFunc=0000) computes signed addition
  //   TC-ALU-2: XOR (aluFunc=0001) computes bitwise XOR
  //   TC-ALU-3: SUB (aluFunc=0010) computes signed subtraction
  //   TC-ALU-4: AND (aluFunc=0011) computes bitwise AND
  //   TC-ALU-5: OR  (aluFunc=0100) computes bitwise OR
  //   TC-ALU-6: SLL (aluFunc=0101) computes logical left shift
  //   TC-ALU-7: SRL (aluFunc=0110) computes logical right shift
  //   TC-ALU-8: reserved aluFunc codes produce 0
  //   TC-ALU-9: SLT (aluFunc=1000) computes signed  0/1 compare  (v2.1)
  //   TC-ALU-10: SLTU (aluFunc=1001) computes unsigned 0/1 compare (v2.1)
  //   TC-ALU-11: SRA (aluFunc=1010) computes arithmetic right shift (v2.1)
  // ======================================================================
  GenerationFlags.formal {
    val resetn = ClockDomain.current.readResetWire
    assumeInitial(!resetn)

    /* TC-ALU-1: ADD */
    when(io.aluFunc === B"0000") {
      assert(io.result === (io.rsVal.asSInt + io.opB.asSInt).asBits)
    }

    /* TC-ALU-2: XOR */
    when(io.aluFunc === B"0001") {
      assert(io.result === (io.rsVal ^ io.opB))
    }

    /* TC-ALU-3: SUB */
    when(io.aluFunc === B"0010") {
      assert(io.result === (io.rsVal.asSInt - io.opB.asSInt).asBits)
    }

    /* TC-ALU-4: AND */
    when(io.aluFunc === B"0011") {
      assert(io.result === (io.rsVal & io.opB))
    }

    /* TC-ALU-5: OR */
    when(io.aluFunc === B"0100") {
      assert(io.result === (io.rsVal | io.opB))
    }

    /* TC-ALU-6: SLL */
    when(io.aluFunc === B"0101") {
      assert(io.result === (io.rsVal |<< io.opB(3 downto 0).asUInt))
    }

    /* TC-ALU-7: SRL */
    when(io.aluFunc === B"0110") {
      assert(io.result === (io.rsVal |>> io.opB(3 downto 0).asUInt))
    }

    /* TC-ALU-8: reserved codes (0111 and 1011..1111) produce 0 */
    when(io.aluFunc === B"0111" ||
         (io.aluFunc(3) && io.aluFunc(2 downto 0).asUInt > 2)) {
      assert(io.result === 0)
    }

    /* TC-ALU-9: SLT — result is exactly 0 or 1, equals signed compare */
    when(io.aluFunc === B"1000") {
      val exp = (io.rsVal.asSInt < io.opB.asSInt) ? B(1, 16 bits) | B(0, 16 bits)
      assert(io.result === exp)
      assert(io.result === B"0000000000000000" || io.result === B"0000000000000001")
    }

    /* TC-ALU-10: SLTU — result is exactly 0 or 1, equals unsigned compare */
    when(io.aluFunc === B"1001") {
      val exp = (io.rsVal.asUInt < io.opB.asUInt) ? B(1, 16 bits) | B(0, 16 bits)
      assert(io.result === exp)
      assert(io.result === B"0000000000000000" || io.result === B"0000000000000001")
    }

    /* TC-ALU-11: SRA — arithmetic right shift (sign fill) */
    when(io.aluFunc === B"1010") {
      assert(io.result === (io.rsVal.asSInt >> io.opB(3 downto 0).asUInt).asBits)
    }

    cover(io.aluFunc === B"0000")
    cover(io.aluFunc === B"1000")
    cover(io.aluFunc === B"1001")
    cover(io.aluFunc === B"1010")
  }
}
