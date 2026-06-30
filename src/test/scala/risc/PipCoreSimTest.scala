package risc

import org.scalatest.funsuite.AnyFunSuite

class PipCoreSimTest extends AnyFunSuite {

  // ══════════════════════════════════════════════════════════════════════════
  // ALU R-type
  // ══════════════════════════════════════════════════════════════════════════

  for ((name, op, rs, rt, exp) <- PipCoreSim.ALU_TESTS) {
    test(s"ALU R-type: $name") {
      PipCoreSim.checkSingle(name, PipCoreSim.aluRtypeProg(op, rs, rt), exp)
    }
  }

  // ══════════════════════════════════════════════════════════════════════════
  // Immediate ALU
  // ══════════════════════════════════════════════════════════════════════════

  test("ADDI R2(10) +5 → 15") {
    PipCoreSim.checkSingle("ADDI",
      Seq(
        PipCoreSim.LDI(2), PipCoreSim.imm16(10),
        PipCoreSim.ADDI(1, 2, 5),
        PipCoreSim.ST(1, 0, 0), PipCoreSim.INF_LOOP
      ), 15)
  }

  test("ADDI R2(0) +(-1) → 0xFFFF") {
    PipCoreSim.checkSingle("ADDI",
      Seq(
        PipCoreSim.LDI(2), PipCoreSim.imm16(0),
        PipCoreSim.ADDI(1, 2, -1),
        PipCoreSim.ST(1, 0, 0), PipCoreSim.INF_LOOP
      ), 0xFFFF)
  }

  test("XORI 0x0F0F ^ 0x05 → 0x0F0A") {
    PipCoreSim.checkSingle("XORI",
      Seq(
        PipCoreSim.LDI(2), PipCoreSim.imm16(0x0F0F),
        PipCoreSim.XORI(1, 2, 5),
        PipCoreSim.ST(1, 0, 0), PipCoreSim.INF_LOOP
      ), 0x0F0A)
  }

  // ══════════════════════════════════════════════════════════════════════════
  // LD / ST
  // ══════════════════════════════════════════════════════════════════════════

  test("LD/ST: store 42, load back") {
    PipCoreSim.checkSingle("LD/ST",
      Seq(
        PipCoreSim.LDI(2), PipCoreSim.imm16(42),
        PipCoreSim.ST(2, 0, 2),
        PipCoreSim.LDI(3), PipCoreSim.imm16(0),
        PipCoreSim.LD(1, 0, 2),
        PipCoreSim.ST(1, 0, 0), PipCoreSim.INF_LOOP
      ), 42)
  }

  test("LD/ST: load-use forwarding LD→ADDI") {
    val prog = Seq(
      PipCoreSim.LDI(2), PipCoreSim.imm16(100),
      PipCoreSim.ST(2, 0, 2),
      PipCoreSim.LD(1, 0, 2),
      PipCoreSim.ADDI(1, 1, 1),
      PipCoreSim.ST(1, 0, 0), PipCoreSim.INF_LOOP
    )
    PipCoreSim.checkSingle("LD/ST fwd", prog, 101)
  }



  // ══════════════════════════════════════════════════════════════════════════
  // JMP
  // ══════════════════════════════════════════════════════════════════════════

  test("JMP: skip over instructions") {
    // JMP target is byte address = wordIdx * 2.
    // Target path starts at word 7 (byte 14).
    // JMP at word 2. If JMP fails, falls through to FAIL at word 3.
    PipCoreSim.checkSingle("JMP",
      Seq(
        PipCoreSim.LDI(4), PipCoreSim.imm16(14),  // R4 = byte addr of target (word 7)
        PipCoreSim.JMP(4),
        PipCoreSim.LDI(1), PipCoreSim.FAIL,
        PipCoreSim.ST(1, 0, 0), PipCoreSim.INF_LOOP,
        PipCoreSim.LDI(1), PipCoreSim.PASS,
        PipCoreSim.ST(1, 0, 0), PipCoreSim.INF_LOOP
      ), PipCoreSim.PASS)
  }

  // ══════════════════════════════════════════════════════════════════════════
  // Branches
  // ══════════════════════════════════════════════════════════════════════════

  test("BEQ taken (42==42)") {
    PipCoreSim.checkSingle("BEQ taken",
      PipCoreSim.branchProg(PipCoreSim.BEQ, 42, 42, taken=true), PipCoreSim.PASS)
  }
  test("BEQ not taken (42!=7)") {
    PipCoreSim.checkSingle("BEQ ntaken",
      PipCoreSim.branchProg(PipCoreSim.BEQ, 42, 7, taken=false), PipCoreSim.PASS)
  }

  test("BNE taken (42!=7)") {
    PipCoreSim.checkSingle("BNE taken",
      PipCoreSim.branchProg(PipCoreSim.BNE, 42, 7, taken=true), PipCoreSim.PASS)
  }
  test("BNE not taken (42==42)") {
    PipCoreSim.checkSingle("BNE ntaken",
      PipCoreSim.branchProg(PipCoreSim.BNE, 42, 42, taken=false), PipCoreSim.PASS)
  }

  test("BLT taken (3<7)") {
    PipCoreSim.checkSingle("BLT taken",
      PipCoreSim.branchProg(PipCoreSim.BLT, 3, 7, taken=true), PipCoreSim.PASS)
  }
  test("BLT not taken (7<3)") {
    PipCoreSim.checkSingle("BLT ntaken",
      PipCoreSim.branchProg(PipCoreSim.BLT, 7, 3, taken=false), PipCoreSim.PASS)
  }
  test("BLT not taken (5==5)") {
    PipCoreSim.checkSingle("BLT eq",
      PipCoreSim.branchProg(PipCoreSim.BLT, 5, 5, taken=false), PipCoreSim.PASS)
  }
  test("BLT taken (-3<0)") {
    PipCoreSim.checkSingle("BLT neg",
      PipCoreSim.branchProg(PipCoreSim.BLT, -3, 0, taken=true), PipCoreSim.PASS)
  }
  test("BLT not taken (0<-3)") {
    PipCoreSim.checkSingle("BLT nneg",
      PipCoreSim.branchProg(PipCoreSim.BLT, 0, -3, taken=false), PipCoreSim.PASS)
  }

  // ══════════════════════════════════════════════════════════════════════════
  // LDI
  // ══════════════════════════════════════════════════════════════════════════

  test("LDI R1,#0x1234") {
    PipCoreSim.checkSingle("LDI 0x1234",
      Seq(PipCoreSim.LDI(1), PipCoreSim.imm16(0x1234),
        PipCoreSim.ST(1, 0, 0), PipCoreSim.INF_LOOP), 0x1234)
  }
  test("LDI R1,#0xFFFF") {
    PipCoreSim.checkSingle("LDI 0xFFFF",
      Seq(PipCoreSim.LDI(1), PipCoreSim.imm16(0xFFFF),
        PipCoreSim.ST(1, 0, 0), PipCoreSim.INF_LOOP), 0xFFFF)
  }
  test("LDI R3,#0x0055") {
    PipCoreSim.checkSingle("LDI 0x0055",
      Seq(PipCoreSim.LDI(3), PipCoreSim.imm16(0x0055),
        PipCoreSim.ST(3, 0, 0), PipCoreSim.INF_LOOP), 0x0055)
  }
}
