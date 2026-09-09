package risc

import spinal.core._

import scala.language.postfixOps

/**
 * Instruction decoder for the 16-bit RISC core.
 *
 * Extracts all fields from a 16-bit instruction word and produces
 * decoded control signals for the execution pipeline.
 *
 * == Instruction format ==
 * {{{
 *   [15:12] opcode  — 4-bit operation code
 *   [11:9]  Rd      — destination register address (3 bits)
 *   [8:6]   Rs      — source register address      (3 bits)
 *   [5:3]   Rt      — source register address      (3 bits)
 *   [5:0]   imm6    — 6-bit signed immediate/offset (overlaps Rt)
 * }}}
 *
 * == Opcode map ==
 * {{{
 *   0x0 : ADD    0x4 : SUB    0x8 : SRL    0xC : BEQ
 *   0x1 : ADDI  0x5 : AND    0x9 : LD     0xD : BNE
 *   0x2 : XOR   0x6 : OR     0xA : ST     0xE : BLT
 *   0x3 : XORI  0x7 : SLL    0xB : JMP    0xF : LDI
 * }}}
 *
 * Note: ALU covers opcodes 0x0–0x8 (isALU formula).
 * ADDI (0x1) and XORI (0x3) are immediate-form ALU instructions.
 */
case class DecoderIo() extends Bundle {
  /** 16-bit instruction word fetched from memory. */
  val instr: Bits = in Bits (16 bits)
  /** True when opcode is an ALU operation (0x0–0x8). */
  val isALU: Bool = out Bool ()
  /** True when instruction is LD (load, opcode 0x9). */
  val isLD: Bool = out Bool ()
  /** True when instruction is ST (store, opcode 0xA). */
  val isST: Bool = out Bool ()
  /** True when instruction is JMP (jump, opcode 0xB). */
  val isJMP: Bool = out Bool ()
  /** True when instruction is BEQ (branch if equal, opcode 0xC). */
  val isBEQ: Bool = out Bool ()
  /** True when instruction is BNE (branch if not equal, opcode 0xD). */
  val isBNE: Bool = out Bool ()
  /** True when instruction is BLT (branch if less than, opcode 0xE). */
  val isBLT: Bool = out Bool ()
  /** True when instruction is LDI (load immediate, opcode 0xF). */
  val isLDI: Bool = out Bool ()
  /** True when instruction is any branch (BEQ/BNE/BLT). */
  val isBranch: Bool = out Bool ()
  /** True when instruction uses immediate format (ADDI opcode 0x1, XORI opcode 0x3). */
  val isImmEn: Bool = out Bool ()
  /** True when instruction writes to a destination register (ALU/LD/LDI). */
  val hasRd: Bool = out Bool ()
  /** Destination register address Rd (instr[11:9]). */
  val rdField: Bits = out Bits (3 bits)
  /** Source register Rs (instr[8:6]). */
  val rsReg: Bits = out Bits (3 bits)
  /** Source register Rt (instr[5:3]). */
  val rtReg: Bits = out Bits (3 bits)
  /** 6-bit immediate field (instr[5:0]), sign-extended by the core. */
  val imm6: Bits = out Bits (6 bits)
  /** ALU function select (3 bits, derived from opcode). */
  val aluFunc: Bits = out Bits (3 bits)
}

case class Decoder() extends Component {
  val io: DecoderIo = DecoderIo()

  private val opc: Bits = io.instr(15 downto 12)

  io.isALU := !opc(3) || (opc === B"1000")
  io.isLD  := opc === B"1001"
  io.isST  := opc === B"1010"
  io.isJMP := opc === B"1011"
  io.isBEQ := opc === B"1100"
  io.isBNE := opc === B"1101"
  io.isBLT := opc === B"1110"
  io.isLDI := opc === B"1111"

  io.isBranch := io.isBEQ || io.isBNE || io.isBLT
  io.isImmEn  := (opc === B"0001") || (opc === B"0011")
  io.hasRd    := io.isALU || io.isLD || io.isLDI

  io.rdField := io.instr(11 downto 9)
  io.rsReg   := io.instr(8 downto 6)
  io.rtReg   := io.instr(5 downto 3)
  io.imm6    := io.instr(5 downto 0)

  /* ALU function decode from opcode:
   *   opc < 4  → aluFunc = opc >> 1
   *   opc >= 4 → aluFunc = opc - 2
   */
  private val opcU: UInt = opc.asUInt
  io.aluFunc := B"000"
  when(opcU < 4) {
    io.aluFunc := (opcU >> 1).asBits.resized
  }.otherwise {
    io.aluFunc := (opcU - 2).asBits.resized
  }

  // ======================================================================
  // Formal Verification — covers the following test cases:
  //   TC-DEC-1: isALU true for opcodes 0–8, false for opcodes 9–15
  //   TC-DEC-2: Each opcode group correctly decoded (LD=0x9, ST=0xA, JMP=0xB,
  //             BEQ=0xC, BNE=0xD, BLT=0xE, LDI=0xF)
  //   TC-DEC-3: isImmEn true for ADDI (0x1) and XORI (0x3)
  //   TC-DEC-4: hasRd matches isALU || isLD || isLDI
  //   TC-DEC-5: Field extraction (rdField, rsReg, rtReg, imm6) matches bits
  //   TC-DEC-6: aluFunc matches opcode-based derivation
  // ======================================================================
  GenerationFlags.formal {
    val resetn = ClockDomain.current.readResetWire
    assumeInitial(!resetn)

    /* TC-DEC-1: ALU range detection */
    when(opc.asUInt <= 8)  { assert(io.isALU) }
    when(opc.asUInt > 8)   { assert(!io.isALU) }

    /* TC-DEC-2: Per-opcode group detection */
    when(opc === B"1001") { assert(io.isLD);  assert(!io.isST) }
    when(opc === B"1010") { assert(io.isST);  assert(!io.isLD) }
    when(opc === B"1011") { assert(io.isJMP); assert(!io.isBranch) }
    when(opc === B"1100") { assert(io.isBEQ); assert(io.isBranch) }
    when(opc === B"1101") { assert(io.isBNE); assert(io.isBranch) }
    when(opc === B"1110") { assert(io.isBLT); assert(io.isBranch) }
    when(opc === B"1111") { assert(io.isLDI); assert(!io.isALU)  }

    /* TC-DEC-3: Immediate-format detection */
    when(opc === B"0001" || opc === B"0011") {
      assert(io.isImmEn)
    } otherwise {
      assert(!io.isImmEn)
    }

    /* TC-DEC-4: hasRd derivation */
    assert(io.hasRd === (io.isALU || io.isLD || io.isLDI))

    /* TC-DEC-5: Field extraction */
    assert(io.rdField === io.instr(11 downto 9))
    assert(io.rsReg   === io.instr(8 downto 6))
    assert(io.rtReg   === io.instr(5 downto 3))
    assert(io.imm6    === io.instr(5 downto 0))

    /* TC-DEC-6: ALU function derivation */
    val expAlu: Bits = Bits(3 bits)
    expAlu := 0
    when(opcU < 4)  { expAlu := (opcU >> 1).asBits.resized }
    when(opcU >= 4) { expAlu := (opcU - 2).asBits.resized }
    assert(io.aluFunc === expAlu)

    cover(opc === B"0000")
    cover(opc === B"1001")
    cover(opc === B"1111")
  }
}
