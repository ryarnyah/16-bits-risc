package risc

import spinal.core._
import spinal.core.sim._

import scala.language.postfixOps

/** PipCore wrapper with simulation-accessible memories for SpinalSim tests. */
class SimPipCore extends Component {
  val core = PipCore()
  val instrRom = Mem(Bits(16 bits), 4096)
  val dataRam  = Mem(Bits(16 bits), 4096)

  instrRom.simPublic()
  dataRam.simPublic()

  val instrWordAddr = core.io.instrAddr(15 downto 1).resize(log2Up(4096))
  core.io.instrRsp.valid := True
  core.io.instrRsp.payload := instrRom.readAsync(instrWordAddr)

  val dataWordAddr = core.io.dataBus.req.addr(15 downto 1).resize(log2Up(4096))
  core.io.dataBus.req.ready := True
  dataRam.write(dataWordAddr, core.io.dataBus.req.wrData,
    core.io.dataBus.req.fire && core.io.dataBus.req.wr)
  val ramRdData = dataRam.readSync(dataWordAddr)
  val ramRspVld = RegNext(core.io.dataBus.req.fire && !core.io.dataBus.req.wr, False)
  core.io.dataBus.rsp.valid := ramRspVld
  core.io.dataBus.rsp.payload := ramRdData

  core.io.bus.cmd.valid := False
  core.io.bus.cmd.payload := 0
  core.io.bus.rsp.ready := False
}

object PipCoreSim {

  // ═════════════════════════════════════════════════════════════════════════
  // Instruction encoding
  // ═════════════════════════════════════════════════════════════════════════

  def ADD (rd: Int, rs: Int, rt: Int): Int = (0x0<<12)|(rd<<9)|(rs<<6)|(rt<<3)
  def ADDI(rd: Int, rs: Int, i6: Int): Int = (0x1<<12)|(rd<<9)|(rs<<6)|(i6&0x3F)
  def XOR (rd: Int, rs: Int, rt: Int): Int = (0x2<<12)|(rd<<9)|(rs<<6)|(rt<<3)
  def XORI(rd: Int, rs: Int, i6: Int): Int = (0x3<<12)|(rd<<9)|(rs<<6)|(i6&0x3F)
  def SUB (rd: Int, rs: Int, rt: Int): Int = (0x4<<12)|(rd<<9)|(rs<<6)|(rt<<3)
  def AND (rd: Int, rs: Int, rt: Int): Int = (0x5<<12)|(rd<<9)|(rs<<6)|(rt<<3)
  def OR  (rd: Int, rs: Int, rt: Int): Int = (0x6<<12)|(rd<<9)|(rs<<6)|(rt<<3)
  def SLL (rd: Int, rs: Int, rt: Int): Int = (0x7<<12)|(rd<<9)|(rs<<6)|(rt<<3)
  def SRL (rd: Int, rs: Int, rt: Int): Int = (0x8<<12)|(rd<<9)|(rs<<6)|(rt<<3)
  def LD  (rd: Int, rs: Int, off: Int): Int = (0x9<<12)|(rd<<9)|(rs<<6)|(off&0x3F)
  def ST  (rt: Int, rs: Int, off: Int): Int = (0xA<<12)|(rt<<9)|(rs<<6)|(off&0x3F)
  def JMP (rs: Int): Int = (0xB<<12)|(rs<<9)
  def BEQ (rs: Int, rt: Int, off: Int): Int = (0xC<<12)|(rs<<9)|(rt<<6)|(off&0x3F)
  def BNE (rs: Int, rt: Int, off: Int): Int = (0xD<<12)|(rs<<9)|(rt<<6)|(off&0x3F)
  def BLT (rs: Int, rt: Int, off: Int): Int = (0xE<<12)|(rs<<9)|(rt<<6)|(off&0x3F)
  def LDI (rd: Int): Int = (0xF<<12)|(rd<<9)

  def imm16(v: Int): Int = v & 0xFFFF
  val INF_LOOP: Int = BEQ(0, 0, -2)
  val PASS = 0x5A5A
  val FAIL = 0x0BAD

  /** Branch offset (in instructions) from branchWordIdx to targetWordIdx. */
  private def brOff(branchWordIdx: Int, targetWordIdx: Int): Int =
    targetWordIdx - branchWordIdx - 1

  // ═════════════════════════════════════════════════════════════════════════
  // Test harness
  // ═════════════════════════════════════════════════════════════════════════

  def checkSingle(
    name:      String,
    program:   Seq[Int],
    expected:  Int,
    maxCycles: Int = 300
  ): Unit = {
    SimConfig.withVerilator.doSim(new SimPipCore) { dut =>
      for ((w, i) <- program.zipWithIndex)
        dut.instrRom.setBigInt(i, BigInt(w & 0xFFFF))
      for (i <- 0 until 32)
        dut.dataRam.setBigInt(i, BigInt(0))

      dut.clockDomain.forkStimulus(period = 10)
      dut.clockDomain.assertReset()
      dut.clockDomain.waitSampling(5)
      dut.clockDomain.deassertReset()

      for (_ <- 0 until maxCycles)
        dut.clockDomain.waitSampling()

      val got = dut.dataRam.getBigInt(0)
      assert(got == expected,
        f"$name: expected 0x${expected.toInt}%04X, got 0x${got.toInt}%04X")
    }
  }

  // ═════════════════════════════════════════════════════════════════════════
  // ALU R-type  (ADD, SUB, AND, OR, XOR, SLL, SRL)
  // ═════════════════════════════════════════════════════════════════════════

  def aluRtypeProg(op: Int, rs: Int, rt: Int): Seq[Int] = Seq(
    LDI(2), imm16(rs), LDI(3), imm16(rt),
    op | (1<<9) | (2<<6) | (3<<3),
    ST(1, 0, 0), INF_LOOP
  )

  val ALU_TESTS: Seq[(String, Int, Int, Int, Int)] = Seq(
    ("ADD  (5+3)",           0x0<<12, 5,      3,      8),
    ("SUB  (10-3)",          0x4<<12, 10,     3,      7),
    ("AND  (0x0F0F&0x00FF)", 0x5<<12, 0x0F0F, 0x00FF, 0x000F),
    ("OR   (0xF000|0x0F00)", 0x6<<12, 0xF000, 0x0F00, 0xFF00),
    ("XOR  (0xFFFF^0x00FF)", 0x2<<12, 0xFFFF, 0x00FF, 0xFF00),
    ("SLL  (0x00FF<<3)",     0x7<<12, 0x00FF, 3,      0x07F8),
    ("SRL  (0xFF00>>4)",     0x8<<12, 0xFF00, 4,      0x0FF0),
  )

  // ═════════════════════════════════════════════════════════════════════════
  // Branch program builder
  // ═════════════════════════════════════════════════════════════════════════

  def branchProg(
    branch: (Int, Int, Int) => Int,
    rs: Int, rt: Int,
    taken: Boolean
  ): Seq[Int] = {
    val off = brOff(4, 9)
    val (passV, failV) = if (taken) (PASS, FAIL) else (FAIL, PASS)
    Seq(
      LDI(2), imm16(rs), LDI(3), imm16(rt),
      branch(2, 3, off),
      LDI(1), failV, ST(1, 0, 0), INF_LOOP,
      LDI(1), passV, ST(1, 0, 0), INF_LOOP
    )
  }
}
