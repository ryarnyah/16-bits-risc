package risc

import spinal.core._

import scala.language.postfixOps

/**
 * Shared ISA decode helpers — the single source of truth for field
 * extraction and address/condition derivation.
 *
 * `Decoder` produces class signals (isBranch, isJMP, …) from the ID word;
 * `Core` and `PipCore` derive register addresses, branch decisions, and
 * effective addresses from those classes.  All three used to carry their
 * own copy of the class→field equations (`rsAddrOf`/`rtAddrOf` in
 * `PipCore`, anonymous muxes in `Core`), which could drift apart silently.
 * Every consumer below calls these functions instead; the cores' formal
 * latch-equivalence asserts remain as a second net.
 */
object Isa {
  /** Destination/source field positions (all instructions). */
  def rdOf(i: Bits): UInt = i(11 downto 9).asUInt
  def rsOf(i: Bits): UInt = i(8 downto 6).asUInt
  def rtOf(i: Bits): UInt = i(5 downto 3).asUInt

  /** Register read address for the Rs port, mirroring the decoder classes:
    * single-Rs instructions (BR/JMP/group-B imm) read [11:9],
    * byte-memory ops (LDB/STB) read the base in [5:3], others [8:6]. */
  def rsAddrOf(i: Bits): UInt = {
    val opc = i(15 downto 12)
    val cf = i(8 downto 6)
    val isBr = (opc === B"1100")
    val isJmp = (opc === B"1011") && (cf === B"000") && (i(5 downto 0) === 0)
    val isGb = (opc === B"1011") && (cf.asUInt >= 3)
    val isByte = (opc === B"1111") && !i(8) &&
      ((i(7 downto 6) === B"01") || (i(7 downto 6) === B"10"))
    Mux(isBr || isJmp || isGb, i(11 downto 9).asUInt,
      Mux(isByte, i(5 downto 3).asUInt, i(8 downto 6).asUInt))
  }

  /** Register read address for the Rt port: stores (incl. STB) read the
    * source in [11:9]; v3.2+ branches read no Rt (falls to [5:3], unused). */
  def rtAddrOf(i: Bits): UInt = {
    val opc = i(15 downto 12)
    val isSt = (opc === B"1010") ||
      ((opc === B"1111") && !i(8) && (i(7 downto 6) === B"10"))
    Mux(isSt, i(11 downto 9).asUInt, i(5 downto 3).asUInt)
  }

  /** v3.2 branch decision: single Rs vs 0 (Z/NZ) or its sign bit (MI/PL). */
  def brTaken(rsVal: Bits, cc: Bits): Bool =
    (cc === B"00" && rsVal === 0) ||
      (cc === B"01" && rsVal =/= 0) ||
      (cc === B"10" && rsVal(15)) ||
      (cc === B"11" && !rsVal(15))

  /** Sign/zero extension of the 6-bit immediate field. */
  def sext6(imm6: Bits): Bits = imm6.asSInt.resize(16).asBits
  def zext6(imm6: Bits): Bits = B(0, 10 bits) ## imm6

  /** Little-endian byte-lane select (v2.1 §2.2/§5.6): zero-extended
    * addressed lane for byte loads, full word otherwise. */
  def laneSel(word: Bits, isByte: Bool, a0: Bool): Bits =
    isByte ? (B(0, 8 bits) ## Mux(a0, word(15 downto 8), word(7 downto 0))) | word

  /** Scale a sign-extended offset by 2 (word offsets, branch/PC-rel/LD-ST).
    * Input is a 16-bit sign-extended value; output is (in*2) mod 2^16. */
  def scaled2(x: Bits): Bits = x(14 downto 0) ## B"0"
}
