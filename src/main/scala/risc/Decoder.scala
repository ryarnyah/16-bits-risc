package risc

import spinal.core._

import scala.language.postfixOps

/**
 * Instruction decoder for the 16-bit RISC core (ISA v2.1).
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
 *   [5:0]   imm6    — 6-bit immediate/offset (overlaps Rt)
 * }}}
 *
 * == Opcode map (v3.2) ==
 * {{{
 *   0x0 : ADD    0x4 : SUB    0x8 : SRL    0xC : BR (v3.2, cond in [8:7])
 *   0x1 : ADDI  0x5 : AND    0x9 : LD     0xD : reserved (v3.2, was BNE)
 *   0x2 : XOR   0x6 : OR     0xA : ST     0xE : reserved (v3.2, was BLT)
 *   0x3 : XORI  0x7 : SLL    0xB : JMP+gB 0xF : LDI+gF
 * }}}
 *
 * == v2.1 sub-opcode banks (ISA v2.1 §3.1) ==
 * {{{
 *   Bank A — reg-ALU funct3 (instr[2:0], was "must be 000"):
 *     op=4, f3=001 → SLT ; op=4, f3=010 → SLTU ; op=8, f3=001 → SRA
 *     any other f3≠000 on op≤8 → reserved (NOP)
 *   Bank B — group 0xB (instr[8:0] was "must be zero" for JMP):
 *     cf=000 payload=0        → JMP Rs
 *     cf=001 [2:0]=000        → CALL Rlink,Rtarget   (Rtarget = [5:3])
 *     cf=010 Rd=000 payload=0 → HALT
 *     cf≥011                  → ANDI/ORI/SLLI/SRLI/SRAI (Rd in place)
 *   Bank F — group 0xF (word-1 bits[8:0] were "must be zero" for LDI):
 *     b8=1                    → LDI8 (1-word constant)
 *     b8=0 mf=00 payload=0    → LDI  (2-word, v2.0)
 *     b8=0 mf=01              → LDB
 *     b8=0 mf=10              → STB
 *     everything else         → reserved (NOP)
 * }}}
 *
 * ALU covers opcodes 0x0–0x8 (valid funct3 only) plus group-B immediates.
 * Reserved encodings assert isReserved and must execute as NOP.
 */
case class DecoderIo() extends Bundle {
  /** 16-bit instruction word fetched from memory. */
  val instr: Bits = in Bits (16 bits)
  /** True when the instruction computes via the ALU (opcodes 0x0–0x8 with a
    * valid funct3, plus group-B immediates). */
  val isALU: Bool = out Bool ()
  /** True when instruction is LD (load, opcode 0x9). */
  val isLD: Bool = out Bool ()
  /** True when instruction is ST (store, opcode 0xA). */
  val isST: Bool = out Bool ()
  /** True when instruction is JMP (cf=000, payload=0). */
  val isJMP: Bool = out Bool ()
  /** True when instruction is BR (v3.2 single-register branch, opcode 0xC). */
  val isBR: Bool = out Bool ()
  /** Branch condition (v3.2, mirrors instr[8:7]): 00=Z, 01=NZ, 10=MI, 11=PL. */
  val brCC: Bits = out Bits (2 bits)
  /** True when instruction is the 2-word LDI (v2.0 form). */
  val isLDI: Bool = out Bool ()
  /** True when instruction is a branch (== isBR since v3.2). */
  val isBranch: Bool = out Bool ()
  /** True when instruction uses immediate format (ADDI opcode 0x1, XORI opcode 0x3);
    * operand B is the sign-extended imm6. */
  val isImmEn: Bool = out Bool ()
  /** True for group-B immediates (ANDI/ORI/SLLI/SRLI/SRAI): operand B is the
    * ZERO-extended imm6 (masks/shift amounts are unsigned, ISA v2.1 §4.5). */
  val immZext: Bool = out Bool ()
  /** True when instruction writes to a destination register
    * (ALU/LD/LDI/LDI8/LDB/CALL). */
  val hasRd: Bool = out Bool ()
  /** Destination register address Rd (instr[11:9]). */
  val rdField: Bits = out Bits (3 bits)
  /** Source register Rs (instr[8:6]). */
  val rsReg: Bits = out Bits (3 bits)
  /** Source register Rt (instr[5:3]). */
  val rtReg: Bits = out Bits (3 bits)
  /** 6-bit immediate field (instr[5:0]), extended by the core per immZext. */
  val imm6: Bits = out Bits (6 bits)
  /** ALU function select (4 bits, ISA v2.1 §9.5). */
  val aluFunc: Bits = out Bits (4 bits)

  // ── v2.1 new sub-opcode decodes ──
  /** True for group-B 2-operand immediates: ANDI/ORI/SLLI/SRLI/SRAI (cf≥011). */
  val isGrpBImm: Bool = out Bool ()
  /** True for CALL Rlink,Rtarget (cf=001, payload[2:0]=000). */
  val isCALL: Bool = out Bool ()
  /** True for HALT (cf=010, Rd=000, payload=0). */
  val isHALT: Bool = out Bool ()
  /** True for LDI8 (b8=1): 1-word zero-extended constant load. */
  val isLDI8: Bool = out Bool ()
  /** True for LDB (mf=01): byte load. */
  val isLDB: Bool = out Bool ()
  /** True for STB (mf=10): byte store. */
  val isSTB: Bool = out Bool ()
  /** True for any encoding that is not legal in v2.1 → must execute as NOP. */
  val isReserved: Bool = out Bool ()
}

case class Decoder() extends Component {
  val io: DecoderIo = DecoderIo()

  private val opc: Bits = io.instr(15 downto 12)
  private val f3: Bits = io.instr(2 downto 0)
  private val cf: Bits = io.instr(8 downto 6)
  private val payload: Bits = io.instr(5 downto 0)
  private val b8: Bool = io.instr(8)
  private val mf: Bits = io.instr(7 downto 6)

  private val opcU: UInt = opc.asUInt
  private val cfU: UInt = cf.asUInt

  // ── Bank A: reg-ALU funct3 validity ────────────────────────────────
  // Opcodes 1/3 (ADDI/XORI) have no funct field — imm6 covers [2:0].
  // Opcode 4 (SUB): f3 000=SUB, 001=SLT, 010=SLTU. Opcode 8 (SRL):
  // f3 000=SRL, 001=SRA.  All other f3≠000 → reserved.
  private val immOp = (opc === B"0001") || (opc === B"0011")
  private val bankAValid =
    immOp ||
    (f3 === B"000") ||
    ((opc === B"0100") && ((f3 === B"001") || (f3 === B"010"))) ||
    ((opc === B"1000") && (f3 === B"001"))
  private val baseAlu = (opcU <= 8) && bankAValid

  private val isSLT = (opc === B"0100") && (f3 === B"001")
  private val isSLTU = (opc === B"0100") && (f3 === B"010")
  private val isSRA = (opc === B"1000") && (f3 === B"001")

  // ── v2.0 groups ────────────────────────────────────────────────────
  io.isLD  := opc === B"1001"
  io.isST  := opc === B"1010"
  // v3.2: single branch opcode 0xC (cond in [8:7]); 0xD/0xE are reserved.
  io.isBR := opc === B"1100"
  io.brCC := io.instr(8 downto 7)
  io.isBranch := io.isBR

  // ── Bank B: group 0xB ──────────────────────────────────────────────
  io.isJMP := (opc === B"1011") && (cf === B"000") && (payload === B"000000")
  io.isCALL := (opc === B"1011") && (cf === B"001") && (io.instr(2 downto 0) === B"000")
  io.isHALT := (opc === B"1011") && (cf === B"010") &&
    (io.instr(11 downto 9) === B"000") && (payload === B"000000")
  io.isGrpBImm := (opc === B"1011") && (cfU >= 3)

  // ── Bank F: group 0xF ──────────────────────────────────────────────
  io.isLDI := (opc === B"1111") && !b8 && (mf === B"00") && (payload === B"000000")
  io.isLDI8 := (opc === B"1111") && b8
  io.isLDB := (opc === B"1111") && !b8 && (mf === B"01")
  io.isSTB := (opc === B"1111") && !b8 && (mf === B"10")

  // ── Reserved = NOP (ISA v2.1 §2.6; v3.2 adds freed 0xD/0xE) ─────────
  io.isReserved :=
    ((opcU <= 8) && !bankAValid) ||
    ((opc === B"1011") && !(io.isJMP || io.isCALL || io.isHALT || io.isGrpBImm)) ||
    ((opc === B"1111") && !(io.isLDI || io.isLDI8 || io.isLDB || io.isSTB)) ||
    (opc === B"1101") || (opc === B"1110")

  // ── Aggregate classes ──────────────────────────────────────────────
  io.isALU := baseAlu || io.isGrpBImm
  io.isImmEn := immOp
  io.immZext := io.isGrpBImm
  io.hasRd := io.isALU || io.isLD || io.isLDI || io.isLDI8 || io.isLDB || io.isCALL

  io.rdField := io.instr(11 downto 9)
  io.rsReg   := io.instr(8 downto 6)
  io.rtReg   := io.instr(5 downto 3)
  io.imm6    := io.instr(5 downto 0)

  // ── ALU function decode (4 bits, ISA v2.1 §9.1/§9.5) ───────────────
  //   opc < 4  → aluFunc = opc >> 1   (0→ADD, 2→XOR, 3→XORI: 001)
  //   opc >= 4 → aluFunc = opc -  2   (4→SUB 0010, 5→AND 0011,
  //                                     6→OR 0100, 7→SLL 0101, 8→SRL 0110)
  // v2.1 overrides: SLT=1000, SLTU=1001, SRA=1010; group-B immediates
  //   cf 011..110 → {1'b0, cf} = AND/OR/SLL/SRL codes, cf 111 → SRA.
  io.aluFunc := B"0000"
  when(opcU < 4) { io.aluFunc := (opcU >> 1).asBits.resized }
  when(opcU >= 4) { io.aluFunc := (opcU - 2).asBits.resized }
  when(isSLT)  { io.aluFunc := B"1000" }
  when(isSLTU) { io.aluFunc := B"1001" }
  when(isSRA)  { io.aluFunc := B"1010" }
  when(io.isGrpBImm) {
    io.aluFunc := (cf === B"111") ? B"1010" | (B"0" ## cf)
  }

  // ======================================================================
  // Formal Verification — covers the following test cases:
  //   TC-DEC-1: isALU true iff opcode 0–8 with valid funct3, or group-B imm
  //   TC-DEC-2: Each opcode group correctly decoded (LD=0x9, ST=0xA,
  //             JMP=0xB/cf0, BR=0xC, 0xD/0xE reserved, LDI=0xF/2-word)
  //   TC-DEC-3: isImmEn true for ADDI (0x1) and XORI (0x3) only
  //   TC-DEC-4: hasRd matches ALU || LD || LDI || LDI8 || LDB || CALL
  //   TC-DEC-5: Field extraction (rdField, rsReg, rtReg, imm6) matches bits
  //   TC-DEC-6: aluFunc matches opcode/funct3/cf-based derivation
  //   TC-DEC-7 (v2.1): bank A — SLT/SLTU/SRA decode
  //   TC-DEC-8 (v2.1): bank B — JMP/CALL/HALT/immediates decode
  //   TC-DEC-9 (v2.1): bank F — LDI/LDI8/LDB/STB decode
  //   TC-DEC-10 (v2.1): reserved → no architectural class asserted
  //   TC-DEC-11 (v2.1): v2.0 compatibility — every v2.0-legal encoding
  //             (f3=000 / all imm / JMP payload=0 / LDI payload=0) decodes
  //             with the v2.0 equations (aluFunc zero-extends the 3-bit code)
  // ======================================================================
  GenerationFlags.formal {
    val resetn = ClockDomain.current.readResetWire
    assumeInitial(!resetn)

    /* TC-DEC-1: ALU range detection with bank-A validity */
    when((opcU <= 8) && bankAValid) { assert(io.isALU) }
    when((opcU <= 8) && !bankAValid) { assert(!io.isALU) }
    when(io.isGrpBImm) { assert(io.isALU) }
    when((opcU > 8) && !io.isGrpBImm) { assert(!io.isALU) }

    /* TC-DEC-2: Per-opcode group detection (v3.2: single branch 0xC) */
    when(opc === B"1001") { assert(io.isLD);  assert(!io.isST) }
    when(opc === B"1010") { assert(io.isST);  assert(!io.isLD) }
    when(opc === B"1011") { assert(!io.isBranch) }
    when(opc === B"1100") { assert(io.isBR); assert(io.isBranch); assert(!io.isReserved) }
    when(opc === B"1101") { assert(io.isReserved); assert(!io.isBranch) }
    when(opc === B"1110") { assert(io.isReserved); assert(!io.isBranch) }
    when(opc === B"1111") { assert(!io.isALU) }
    when(io.isLDI) { assert(opc === B"1111"); assert(!io.isLDI8) }
    /* v3.2: branch condition mirrors instr[8:7] */
    assert(io.brCC === io.instr(8 downto 7))

    /* TC-DEC-3: Immediate-format detection */
    when(opc === B"0001" || opc === B"0011") {
      assert(io.isImmEn)
    } otherwise {
      assert(!io.isImmEn)
    }
    /* v2.1: immZext only for group-B immediates (ANDI/ORI/SLLI/SRLI/SRAI) */
    assert(io.immZext === io.isGrpBImm)
    assert(io.isGrpBImm === ((opc === B"1011") && (cfU >= 3)))

    /* TC-DEC-4: hasRd derivation */
    assert(io.hasRd === (io.isALU || io.isLD || io.isLDI || io.isLDI8 ||
      io.isLDB || io.isCALL))
    /* Stores/branches/jumps/HALT never write a register */
    when(io.isST || io.isSTB || io.isBranch || io.isJMP || io.isHALT ||
      io.isReserved) { assert(!io.hasRd) }

    /* TC-DEC-5: Field extraction */
    assert(io.rdField === io.instr(11 downto 9))
    assert(io.rsReg   === io.instr(8 downto 6))
    assert(io.rtReg   === io.instr(5 downto 3))
    assert(io.imm6    === io.instr(5 downto 0))

    /* TC-DEC-6: ALU function derivation */
    val expAlu = Bits(4 bits)
    expAlu := 0
    when(opcU < 4)  { expAlu := (opcU >> 1).asBits.resized }
    when(opcU >= 4) { expAlu := (opcU - 2).asBits.resized }
    when(isSLT)  { expAlu := B"1000" }
    when(isSLTU) { expAlu := B"1001" }
    when(isSRA)  { expAlu := B"1010" }
    when(io.isGrpBImm) { expAlu := (cf === B"111") ? B"1010" | (B"0" ## cf) }
    assert(io.aluFunc === expAlu)

    /* TC-DEC-7 (v2.1): bank A */
    when(io.instr === (B"0100" ## B"001" ## B"010" ## B"011" ## B"001")) {
      // opcode=4, Rd=1, Rs=2, Rt=3, f3=1 → SLT
      assert(isSLT && io.isALU)
    }
    when((opc === B"0100") && (f3 === B"001")) { assert(io.isALU) }
    when((opc === B"0100") && (f3 === B"010")) { assert(io.isALU) }
    when((opc === B"1000") && (f3 === B"001")) { assert(io.isALU) }
    when((opcU <= 8) && !immOp && (f3 =/= B"000") && !isSLT && !isSLTU && !isSRA) {
      assert(io.isReserved)
      assert(!io.isALU && !io.hasRd)
    }

    /* TC-DEC-8 (v2.1): bank B */
    when(io.isJMP) { assert(!io.isCALL && !io.isHALT && !io.isGrpBImm && !io.isBranch) }
    when(io.isCALL) { assert(io.hasRd); assert(!io.isJMP) }
    when(io.isHALT) { assert(!io.hasRd); assert(!io.isJMP && !io.isCALL) }
    when(io.isGrpBImm) { assert(io.hasRd); assert(io.immZext) }
    when((opc === B"1011") && (cf === B"000") && (payload =/= 0)) { assert(io.isReserved) }
    when((opc === B"1011") && (cf === B"001") && (io.instr(2 downto 0) =/= 0)) {
      assert(io.isReserved)
    }
    when((opc === B"1011") && (cf === B"010") &&
      ((io.instr(11 downto 9) =/= 0) || (payload =/= 0))) { assert(io.isReserved) }

    /* TC-DEC-9 (v2.1): bank F */
    when(io.isLDI) { assert(!io.isLDI8 && !io.isLDB && !io.isSTB) }
    when(io.isLDI8) { assert(!io.isLDI && !io.isLDB && !io.isSTB); assert(io.hasRd) }
    when(io.isLDB) { assert(!io.isLDI && !io.isLDI8 && !io.isSTB); assert(io.hasRd) }
    when(io.isSTB) { assert(!io.isLDI && !io.isLDI8 && !io.isLDB); assert(!io.hasRd) }
    when((opc === B"1111") && (io.instr(8 downto 6) === B"011")) { assert(io.isReserved) }
    when((opc === B"1111") && !b8 && (mf === B"00") && (payload =/= 0)) {
      assert(io.isReserved)
    }

    /* TC-DEC-10 (v2.1): reserved encodings are inert (NOP, §2.6) */
    when(io.isReserved) {
      assert(!io.isALU && !io.isLD && !io.isST && !io.isJMP && !io.isBranch &&
        !io.isLDI && !io.isLDI8 && !io.isLDB && !io.isSTB && !io.isCALL &&
        !io.isHALT && !io.isGrpBImm && !io.hasRd && !io.isImmEn)
    }

    /* TC-DEC-11 (v2.1): v2.0 compatibility — v2.0-legal words decode with
     * the v2.0 equations.  A v2.0-legal word is any opcode 0–8 word
     * (v2.0 ignored funct3 bits — they were required 000), plus v2.0 group
     * words with the padding fields zero.  Here: f3=000 or imm op. */
    when((opcU <= 8) && ((f3 === B"000") || immOp)) {
      assert(io.isALU)
      val v20 = Bits(4 bits)
      v20 := 0
      when(opcU < 4)  { v20 := (opcU >> 1).asBits.resized }
      when(opcU >= 4) { v20 := (opcU - 2).asBits.resized }
      assert(io.aluFunc === v20)
      assert(!io.isReserved)
    }

    cover(opc === B"0000")
    cover(opc === B"1001")
    cover(opc === B"1111")
    cover(io.isCALL)
    cover(io.isHALT)
    cover(io.isGrpBImm)
    cover(io.isLDI8)
    cover(io.isLDB)
    cover(io.isSTB)
    cover(io.isReserved)
  }
}
