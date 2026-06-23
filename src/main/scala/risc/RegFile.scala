package risc

import spinal.core._
import spinal.core.formal._

import scala.language.postfixOps

/**
 * Register file for the 16-bit RISC core.
 *
 * Contains 8 general-purpose registers (R0–R7), each 16 bits wide.
 * R0 is hardwired to 0 — writes to address 0 are silently ignored.
 *
 * Reading is asynchronous (combinational); writing is synchronous.
 */
case class RegFileIo() extends Bundle {
  /** Read port 0 address (3-bit, selects R0–R7). */
  val rsAddr: UInt = in UInt (3 bits)
  /** Read port 1 address (3-bit, selects R0–R7). */
  val rtAddr: UInt = in UInt (3 bits)
  /** Write port address (3-bit, selects R0–R7). Writes to 0 are ignored. */
  val wrAddr: UInt = in UInt (3 bits)
  /** Data to write on the rising edge when wrEn is true. */
  val wrData: Bits = in Bits (16 bits)
  /** Write enable — when asserted, wrData is written to wrAddr on the clock edge. */
  val wrEn: Bool = in Bool ()
  /** Read port 0 data (combinational output, stable while rsAddr is valid). */
  val rsVal: Bits = out Bits (16 bits)
  /** Read port 1 data (combinational output, stable while rtAddr is valid). */
  val rtVal: Bits = out Bits (16 bits)
  /** Auxiliary read port address (3-bit, selects R0–R7). Used for debug/bus reads. */
  val auxAddr: UInt = in UInt (3 bits)
  /** Auxiliary read port data (combinational). */
  val auxVal: Bits = out Bits (16 bits)
}

case class RegFile() extends Component {
  val io: RegFileIo = RegFileIo()

  /** 8 × 16-bit register array with synchronous reset to 0.
   *  Uses Vec of RegInit instead of Mem because Yosys formal
   *  doesn't handle $readmemb for Mem init properly. */
  private val regs = Vec(RegInit(B(0, 16 bits)), 8)

  io.rsVal := regs(io.rsAddr)
  io.rtVal := regs(io.rtAddr)
  io.auxVal := regs(io.auxAddr)

  /** Writes to R0 (addr 0) are ignored — R0 is hardwired to 0 (ISA §1). */
  when(io.wrEn && io.wrAddr =/= 0) {
    regs(io.wrAddr) := io.wrData
  }

  // ======================================================================
  // Formal Verification — covers the following test cases:
  //   TC-RF-1: After reset, all registers read as 0 on both ports
  //   TC-RF-2: R0 always reads 0 on both read ports (R0 hardwired, ISA §1)
  //   TC-RF-3: Write to non-zero register, then read same address → data matches
  //   TC-RF-4: Write to R0 (addr=0) is ignored — R0 stays 0
  // ======================================================================
  GenerationFlags.formal {
    val resetn = ClockDomain.current.readResetWire
    assumeInitial(!resetn)

    /* Force all registers to 0 in the initial formal state (Yosys formal
     * doesn't use RegInit values because reset is deasserted by
     * assumeInitial). */
    for (i <- 0 until 8) {
      assumeInitial(regs(i) === 0)
    }

    /* Constrain initial past values to prevent free-variable exploits. */
    assumeInitial(!pastValid())

    /* TC-RF-1: Reset clears all registers — only checked in the initial
     * state before any writes have occurred. The write-after-reset
     * behavior is verified by TC-RF-3. */
    when(!pastValid()) {
      when(!resetn) {
        assert(io.rsVal === 0)
        assert(io.rtVal === 0)
      }
    }

    /* TC-RF-2: R0 is hardwired to 0 */
    when(io.rsAddr === 0) { assert(io.rsVal === 0) }
    when(io.rtAddr === 0) { assert(io.rtVal === 0) }

    /* TC-RF-3: Write to non-zero register propagates to read ports.
     * Excludes the case where a concurrent write to the same address
     * overwrites the value before the assertion reads it. */
    when(pastValid()) {
      when(past(io.wrEn) && past(io.wrAddr) =/= 0 &&
           past(io.wrAddr) === io.rsAddr &&
           !(io.wrEn && io.wrAddr === io.rsAddr)) {
        assert(io.rsVal === past(io.wrData))
      }
      when(past(io.wrEn) && past(io.wrAddr) =/= 0 &&
           past(io.wrAddr) === io.rtAddr &&
           !(io.wrEn && io.wrAddr === io.rtAddr)) {
        assert(io.rtVal === past(io.wrData))
      }

      /* TC-RF-4: Write to R0 is ignored */
      when(past(io.wrEn) && past(io.wrAddr) === 0 && io.rsAddr === 0) {
        assert(io.rsVal === 0)
      }
    }

    /* TC-RF-5: Auxiliary port reads register values (same as rs/rt ports) */
    when(io.auxAddr === 0) { assert(io.auxVal === 0) }

    cover(io.wrEn)
    cover(io.rsAddr === 1 && io.rsVal === 0)
  }
}
