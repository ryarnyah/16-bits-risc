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

  test("BR Z taken (0==0)") {
    PipCoreSim.checkSingle("BR Z taken",
      PipCoreSim.branchProg(0, 0, taken=true), PipCoreSim.PASS)
  }
  test("BR Z not taken (42!=0)") {
    PipCoreSim.checkSingle("BR Z ntaken",
      PipCoreSim.branchProg(0, 42, taken=false), PipCoreSim.PASS)
  }

  test("BR NZ taken (42!=0)") {
    PipCoreSim.checkSingle("BR NZ taken",
      PipCoreSim.branchProg(1, 42, taken=true), PipCoreSim.PASS)
  }
  test("BR NZ not taken (0==0)") {
    PipCoreSim.checkSingle("BR NZ ntaken",
      PipCoreSim.branchProg(1, 0, taken=false), PipCoreSim.PASS)
  }

  test("BR MI taken (-3<0)") {
    PipCoreSim.checkSingle("BR MI taken",
      PipCoreSim.branchProg(2, -3, taken=true), PipCoreSim.PASS)
  }
  test("BR MI not taken (3>=0)") {
    PipCoreSim.checkSingle("BR MI ntaken",
      PipCoreSim.branchProg(2, 3, taken=false), PipCoreSim.PASS)
  }
  test("BR MI not taken (0>=0)") {
    PipCoreSim.checkSingle("BR MI zero",
      PipCoreSim.branchProg(2, 0, taken=false), PipCoreSim.PASS)
  }
  test("BR PL taken (3>=0)") {
    PipCoreSim.checkSingle("BR PL taken",
      PipCoreSim.branchProg(3, 3, taken=true), PipCoreSim.PASS)
  }
  test("BR PL taken (0>=0)") {
    PipCoreSim.checkSingle("BR PL zero",
      PipCoreSim.branchProg(3, 0, taken=true), PipCoreSim.PASS)
  }
  test("BR PL not taken (-3<0)") {
    PipCoreSim.checkSingle("BR PL ntaken",
      PipCoreSim.branchProg(3, -3, taken=false), PipCoreSim.PASS)
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
