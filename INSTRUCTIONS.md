# Instructions

## 1. Code Organization

### 1.1 Directory Structure

```
src/
  main/scala/<organization>/<project>/
    Top.scala          # Top-level component
    StreamCtrl.scala   # Stream controller
    RandomCore.scala   # Random number generator core
    CryptoCore.scala   # Encryption/KDF core
    NoOpCore.scala     # No-operation core (null core)
    Keys.scala         # Secret key configuration
  test/scala/<organization>/<project>/
    TopTest.scala         # Simulation tests for top-level
    StreamCtrlTest.scala  # Simulation tests for stream controller
    StreamCtrlFormalTest.scala  # Formal verification tests
```

- Each component lives in its own file.
- Test files mirror source files (`StreamCtrl.scala` → `StreamCtrlTest.scala`).
- All hardware logic resides in `src/main/scala/<organization>/<project>/`.
- Simulation and formal tests reside in `src/test/scala/<organization>/<project>/`.

### 1.2 Generic Bus Interface

**Define a reusable bus interface (Bundle + trait) shared by all cores:**

```scala
// Bus Bundle: defines the signals for communication
// Uses IMasterSlave to allow master/slave direction control
case class MyBusIo() extends Bundle with IMasterSlave {
  // Command stream: master sends (8-bit wide)
  val cmd: Stream[Bits] = Stream(Bits(8 bits))
  // Response stream: slave sends back (8-bit wide)
  val rsp: Stream[Bits] = Stream(Bits(8 bits))
  // Acknowledge signal: slave asserts when transaction is complete
  val ack: Bool = Bool()

  // Define signal directions when acting as master
  override def asMaster(): Unit = {
    master(cmd)  // Master drives cmd
    slave(rsp)   // Master receives rsp
    in(ack)      // Master receives ack
  }
}

// Trait that every core must implement to expose its bus
trait MyBusIoComponent {
  def bus(): MyBusIo
}
```

**Every bus-connected component must implement the trait and override `bus()`:**

```scala
// MyCore connects to the system bus via MyBusIoComponent
case class MyCore() extends Component with MyBusIoComponent {
  // IO bundle containing the bus interface
  val io: MyCoreBundle = MyCoreBundle()
  // ...
  // Expose the bus for the top-level router
  override def bus(): MyBusIo = io.bus
}
```

---

## 2. Code Style

### 2.1 File Header

Mandatory order:
1. Package declaration
2. Imports (`spinal.core._` and `spinal.lib._` always first)
3. `scala.language.postfixOps` when using operators like `bits`, `ms`, etc.

```scala
// Package declaration matching the directory structure
package <organization>.<project>

// Core SpinalHDL library (always imported first)
import spinal.core._
// SpinalHDL lib utilities (Stream, Counter, etc.)
import spinal.lib._

// Enables postfix operator syntax (e.g. 8 bits, 100 MHz)
import scala.language.postfixOps
```

### 2.2 Naming Conventions

| Element                     | Convention              | Example                      |
|-----------------------------|-------------------------|------------------------------|
| Case class `Component`      | PascalCase, domain name | `MyCore`                     |
| `Bundle`                    | PascalCase + `Bundle`   | `MyCoreBundle`               |
| `SpinalEnum`                | PascalCase + `State`    | `MyCoreState`                |
| Configuration               | PascalCase + `Config`   | `StreamConfig`               |
| Private variable            | camelCase               | `currentBlock`, `byteCounter`|
| Signal (`Bool`, `Bits`, etc)| camelCase               | `stretchClk`, `syncSignal`   |
| Constant                    | camelCase               | `masterKey`                  |

### 2.3 `Bundle` and `Component`

**Always use `case class` for automatic cloning during signal duplication:**

```scala
// Bundle: groups related signals into a reusable interface
case class MyBundle() extends Bundle {
  val bus = slave(MyBusIo())
}

// Component: a hardware block with its own IO
// case class enables easy instantiation in different contexts
case class MyCore() extends Component with MyBusIoComponent {
  val io: MyCoreBundle = MyCoreBundle()
}
```

### 2.4 IO and Connectivity

- The IO port is always named `io`, typed with a dedicated `Bundle`.

```scala
// Declare the IO bundle (SpinalHDL will infer pin directions from the Bundle)
val io: MyCoreBundle = MyCoreBundle()
```

- `Stream` is used for valid-ready handshake flows. **Do not** use individual `ready`/`valid` signals.

```scala
// A Stream carries data with valid/ready handshake
private val dataStream = Stream(Bits(32 bits))
```

- `StreamWidthAdapter` converts between different data widths:

```scala
// Accumulate 8-bit bus commands into a wider word
private val inStream = Stream(Bits(32 bits))
StreamWidthAdapter(io.bus.cmd, inStream)
// inStream becomes valid when enough bytes are accumulated
```

```scala
// Split a wide result back to 8-bit bus responses
StreamWidthAdapter(outStream, io.bus.rsp, endianness = BIG)
```

- Define the bus using `IMasterSlave` and `asMaster()`:

```scala
// A Bundle with IMasterSlave can change signal directions
// depending on whether it is the master or the slave
case class MyBusIo() extends Bundle with IMasterSlave {
  val cmd: Stream[Bits] = Stream(Bits(8 bits))
  val rsp: Stream[Bits] = Stream(Bits(8 bits))
  val ack: Bool = Bool()

  // Called when this Bundle is used as a master
  // Master drives cmd, receives rsp and ack
  override def asMaster(): Unit = {
    master(cmd)
    slave(rsp)
    in(ack)
  }
}
```

- Use `queue()` or `StreamFifo` for buffering:

```scala
io.rsp.queue(config.fifoDepth)              // Simple FIFO queue
StreamFifo(Bits(8 bits), config.fifoDepth)  // Full FIFO with status signals
```

- `<<`, `>>` to connect Streams with flow control:

```scala
io.cmd << cmdFifo.io.pop  // Pop from FIFO and forward to io.cmd
```

- `<>` to connect bundles bi-directionally:

```scala
io.i2c <> i2cStream.io.i2c  // Connect all matching signals
```

- Use `takeWhen()` and `StreamMux`/`StreamDemux` for routing:

```scala
// Only let transactions through when in IN_TXN state
StreamDemux(i2cStream.io.cmd.takeWhen(status === IN_TXN), sel, n)
```

### 2.5 Registers and Signals

- `RegInit(value)` for registers with a reset value:

```scala
// State register resets to INIT when reset is asserted
private val state = RegInit(MyCoreState.INIT)
```

- `Reg(type)` for registers without explicit reset (latches are NOT allowed):

```scala
// Register that holds data but does not need a known reset value
private val currentBlock = Reg(Bits(128 bits))
```

- `init(value)` for combinatorial signals combined with registers:

```scala
// Wire that defaults to False but can be set inside conditions
private val smDone = False
```

- `Counter` to count cycles/bytes/rounds:

```scala
// Counter that increments when io.bus.rsp.fire is true
// willOverflow is asserted when the counter wraps around
private val byteCounter = Counter(4, inc = io.bus.rsp.fire)
io.bus.ack := byteCounter.willOverflow
```

- `Timeout` for timeout detection:

```scala
// Timeout fires after config.timeout without activity
val timeout = Timeout(config.timeout)
when(timeout.rise()) {
  // Reset FSM on timeout
  state := MyCoreState.IDLE
}
```

### 2.6 State Machines

Two styles are accepted depending on context:

**Style 1 — `switch/is` (preferred for simple FSMs):**

```scala
// Simple FSM using a register and switch statement
private val state = RegInit(MyState.IDLE)

// Combinatorial logic: evaluate on every cycle
switch(state) {
  is(MyState.IDLE) {
    when(startCondition) {
      state := MyState.BUSY  // Transition on condition
    }
  }
  is(MyState.BUSY) {
    when(done) {
      state := MyState.IDLE  // Transition when done
    }
  }
}
```

**Style 2 — `StateMachine` (for complex FSMs with sub-states and clock-domain crossing):**

```scala
// StateMachine provides EntryPoint, state-local logic, and exit conditions
val sm = new StateMachine {
  val sIdle: State = new State with EntryPoint {
    whenIsActive {
      when(startCondition) {
        goto(sBusy)
      }
    }
  }
  val sBusy: State = new State {
    whenIsActive {
      when(done) {
        goto(sIdle)
      }
    }
  }
}
```

### 2.7 Operators and Syntax

- `when` / `elsewhen` / `otherwise` for conditional logic (like `if/else if/else`):

```scala
when(condition) {
  // do something
} elsewhen(otherCondition) {
  // do something else
} otherwise {
  // default
}
```

- Use `?` for ternary muxes (shorthand for `if-else` on signals):

```scala
// If enc is true, use masterKey, otherwise use derivedKey
aesCore.io.cmd.key := (op === MyOp.ENC) ? masterKey | derivedKey
```

- `switch/is` for multiplexing over many values:

```scala
switch(value) {
  is(CASE_A) { /* ... */ }
  is(CASE_B) { /* ... */ }
}
```

- `.muxList()` for generic multiplexers:

```scala
// Address-based selection of core index
val SEL: UInt = address.muxList(0x00, cores.zipWithIndex.map(u => (u._1._2, U(u._2))))
```

- `.resized` for explicit width resizing:

```scala
// Resize to fit the target bit width
io.keySchedule.cmd.round := (cntRound + 1).resized
```

- `.reversed` for bit/byte order reversal:

```scala
// Reverse bit order for endianness conversion
val kdfMasterKey = masterKey.reversed
```

- `.subdivideIn(8 bits)` for slicing into bytes:

```scala
// Split a 128-bit value into 16 bytes
val blockByte = io.engine.cmd.block.subdivideIn(8 bits)
```

- `.asUInt`, `.asBits`, `.asSInt` for type conversions:

```scala
dataState(cntByte) := sBoxMem(dataState(cntByte).asUInt)
```

- `.takeWhen(condition)` to filter streams:

```scala
// Only propagate stream when status is IN_TXN
i2cStream.io.cmd.takeWhen(status === MyState.IN_TXN)
```

### 2.8 Visibility

All internal signals are `private`:

```scala
private val inStream = Stream(...)
private val state = RegInit(...)
private val byteCounter = Counter(...)
```

`io` is a `public val`, everything else is `private val` to enforce encapsulation.

---

## 3. Best Practices

### 3.1 Configuration

Use `case class` configuration with `require` for validation:

```scala
// Centralized configuration with default values
// require() validates parameters at construction time
case class StreamConfig(
  clockDivider: Int = 2,
  samplingDepth: Int = 3,
  deviceAddress: Int = 0x42,
  timeout: TimeNumber = 100 ms,
  fifoDepth: Int = 32,
  withState: Boolean = false
) {
  // Validate address is within 7-bit range
  require(deviceAddress >= 0 && deviceAddress <= 0x7F, "Address must be 7-bit")
  // Validate sampling parameters
  require(samplingDepth >= 3 && samplingDepth <= 7, "Sampling depth must be between 3 and 7")
  require(clockDivider >= 2, "Clock divider must be > 2")
}
```

### 3.2 Keys and Secrets

**Use a mutable object to hold secret key material:**

```scala
// Companion object holding the master key
// var allows overriding in tests for deterministic behavior
object Keys {
  // Generate a random 128-bit master key at startup
  var MASTER_KEY: BigInt = BigInt(128, new SecureRandom())
}
```

The key is randomly initialized by default but can be overridden in tests:

```scala
Keys.MASTER_KEY = BigInt(...)  // Inject known key for testing
```

### 3.3 RTL Generation

Each component has a companion `object extends App` for standalone RTL generation:

```scala
// App companion: generates SystemVerilog when run with `sbt run`
object MyCore extends App {
  // SpinalHDL configuration
  SpinalConfig(
    targetDirectory = "target/gen",          // Output directory
    mergeAsyncProcess = true,                 // Merge async processes
    mergeSyncProcess = true,                   // Merge sync processes
    genLineComments = true,                    // Preserve source line comments in RTL
    removePruned = true,                       // Remove unused signals
    defaultConfigForClockDomains = ClockDomainConfig(
      resetKind = SYNC,                        // Synchronous reset
      resetActiveLevel = LOW                   // Active-low reset
    ),
    defaultClockDomainFrequency = FixedFrequency(100 MHz)
  ).generateSystemVerilog(MyCore())           // Generate SystemVerilog output
}
```

Key settings:
- `resetKind = SYNC`, `resetActiveLevel = LOW`.
- `mergeAsyncProcess` and `mergeSyncProcess` set to `true`.
- Always `genLineComments = true`, `removePruned = true`.
- Use `InOutWrapper` for components with tri-state I/O pins:

```scala
// InOutWrapper wraps bidirectional pins for proper Verilog generation
InOutWrapper(MyStreamCtrl(config))
```

### 3.4 Endianness

The bus data is in **little-endian**. For words wider than 8 bits, use `endianness = BIG` in `StreamWidthAdapter` when the protocol requires most-significant byte first:

```scala
// BIG endian: first byte read = most significant bits
StreamWidthAdapter(outStream, io.bus.rsp, endianness = BIG)
```

---

## 4. Unit Tests (Simulation)

### 4.1 Structure

Tests extend ScalaTest's `AnyFunSuite` and are `case class`:

```scala
// case class allows re-instantiation with different configs
case class MyCoreTest() extends AnyFunSuite {
  // ...
}
```

### 4.2 Simulation Configuration

```scala
// Simulation configuration with VCD wave dumping enabled
private val simConfig = SimConfig
  .withWave                              // Generate VCD waveform traces
  .workspacePath("target/sim")           // Output directory for sim files
  .withConfig(SpinalConfig(
    mergeAsyncProcess = true,
    mergeSyncProcess = true,
    genLineComments = true,
    removePruned = true,
    defaultConfigForClockDomains = ClockDomainConfig(
      resetKind = SYNC,
      resetActiveLevel = LOW,
    ),
    defaultClockDomainFrequency = FixedFrequency(100 MHz)
  ))

// Compile the DUT once, reuse across tests
private val dutSim = simConfig.compile(MyCore(config))
```

- Always enable `.withWave` for VCD traces (debuggability).
- Use `workspacePath("target/sim")`.

### 4.3 Test Body

```scala
// Each test is a named case inside the suite
test("my core should send data correctly") {
  // doSim runs the simulation once per test
  dutSim.doSim { dut =>
    val clockDomain = dut.clockDomain

    // Initialize input signals to default values
    dut.io.signal #= value

    // Fork a 100 MHz clock (10 ns period)
    clockDomain.forkStimulus(10)

    // Assert reset for 10 cycles, then release
    clockDomain.assertReset()
    clockDomain.waitRisingEdge(10)
    clockDomain.deassertReset()

    // Drive inputs, wait, and check outputs
    dut.io.signal #= expectedValue
    clockDomain.waitRisingEdge(cycles)

    // Assert expected behavior
    assert(dut.io.output.toBoolean == true, "output should be high")
  }
}
```

### 4.4 Helper Methods for Protocols

Extract protocol-level helpers as `private def` in the test class:

```scala
// Helper: send one byte over I2C bus
// Simulates SDA transitions synchronized to SCL
private def sendByte(dut: MyDut, data: Int): Unit = {
  for (i <- 7 downto 0) {               // MSB first
    val bit = (data >> i) & 0x01
    dut.io.sda.read #= (bit == 1)        // Drive SDA
    dut.clockDomain.waitRisingEdge(halfPeriod)
    animateScl(dut)                       // Pulse SCL
  }
}

// Helper: check I2C ACK signal
private def checkAck(dut: MyDut, expectedAck: Boolean): Unit = {
  dut.io.sda.read #= true                // Release SDA for slave
  dut.clockDomain.waitRisingEdge(halfPeriod)
  // ...
  assert(ackReceived == expectedAck,
    s"Expected ${if(expectedAck) "ACK" else "NACK"}, got ...")
}
```

Key rules:
- `dut` is **explicitly typed** (not generic `T`).
- `halfPeriod` is computed from the configuration.
- Use `dut.io.signal.write.toBoolean` to read driven output values.
- Use `dut.io.signal.read.toBoolean` to read input values.
- Use `clockDomain.waitRisingEdge(n)` for multi-cycle delays.
- Use `clockDomain.waitRisingEdge()` (default 1 cycle) for single-cycle waits.

---

## 5. Formal Tests

### 5.1 Structure

```scala
// Formal test suite extending AnyFunSuite
case class MyCoreFormalTest() extends AnyFunSuite {
  // SpinalFormalConfig configures the formal verification backend
  val TestFormalConfig: SpinalFormalConfig = SpinalFormalConfig()
    .withConfig(
      // Same SpinalConfig as simulation, plus .includeFormal
      SpinalConfig(
        mergeAsyncProcess = true,
        mergeSyncProcess = true,
        genLineComments = true,
        removePruned = true,
        defaultConfigForClockDomains = ClockDomainConfig(
          resetKind = SYNC,
          resetActiveLevel = LOW,
        ),
        defaultClockDomainFrequency = FixedFrequency(100 MHz)
      ).includeFormal  // Enable formal constructs in the design
    )
    // Output path for formal tools, configurable via env var
    .workspacePath(System.getenv().getOrDefault("FORMAL_WORKSPACE", "./target/formal"))
    .withDebug        // Generate debug information
    .withProve(50)    // Induction depth for proof
    .withBMC(50)      // Bounded model checking depth
    .withCover(50)    // Cover property depth

  // Compile the DUT for formal verification (no simulation)
  private val dutSim = TestFormalConfig.compile(MyCore())

  test("my core formal verification") {
    dutSim.doVerify()
  }
}
```

### 5.2 Writing Formal Assertions

Formal assertions are placed inside the component itself, guarded by `GenerationFlags.formal`:

```scala
// Formal block: only compiled during formal verification
spinal.core.GenerationFlags.formal {
  // Initial assumptions about the environment
  assumeInitial(io.sda.read)                    // SDA starts high
  assumeInitial(io.scl.read)                    // SCL starts high
  assumeInitial(!ClockDomain.current.readResetWire)  // Not in reset

  // Temporal property: data stability during SCL high
  // UM10204 I2C specification: SDA must be stable when SCL is high
  when(pastValid() && stable(io.scl.read) && io.scl.read) {
    assert(stable(io.sda.write))  // SDA must not change while SCL is high
  }
}
```

Key formal methods:
- `assumeInitial(expr)` — constraint on initial state (environment assumption).
- `assert(expr)` — property that must always hold.
- `assume(expr)` — constraint on environment inputs.
- `pastValid()` — true when past values are available (after first cycle).
- `stable(signal)` — signal has not changed since last cycle.
- `rise()`, `fall()` — detects rising/falling edges.
- `edges()` — detects any edge (rise or fall).

---

## 6. Complete Examples

### 6.1 Minimal Component

```scala
package <organization>.<project>

import spinal.core._
import spinal.lib._

// === Bus interface (portable) ===
// Defines a reusable bus protocol that any core can implement

case class MyBusIo() extends Bundle with IMasterSlave {
  val cmd: Stream[Bits] = Stream(Bits(8 bits))  // Command input (8-bit words)
  val rsp: Stream[Bits] = Stream(Bits(8 bits))  // Response output (8-bit words)
  val ack: Bool = Bool()                         // Transaction done signal

  override def asMaster(): Unit = {
    master(cmd)   // Master sends commands
    slave(rsp)    // Master receives responses
    in(ack)       // Master reads acknowledge
  }
}

trait MyBusIoComponent {
  def bus(): MyBusIo  // Expose bus for the router
}

// === Component ===

case class MyCoreBundle() extends Bundle {
  val bus = slave(MyBusIo())  // Core is a slave on the bus
}

case class MyCore() extends Component with MyBusIoComponent {
  val io: MyCoreBundle = MyCoreBundle()

  // Accumulate 4 bytes from 8-bit bus commands into a 32-bit word
  val stream = Stream(Bits(32 bits))
  StreamWidthAdapter(io.bus.cmd, stream)

  // Process: store incoming value in a register
  val result = Reg(Bits(32 bits)) init 0
  result := stream.payload

  // Send result back: split 32-bit word into 4 bytes (MSB first)
  val outStream = Stream(Bits(32 bits))
  outStream.payload := result
  outStream.valid := stream.valid
  StreamWidthAdapter(outStream, io.bus.rsp, endianness = BIG)
  stream.ready := outStream.ready

  // Count response bytes, assert ack when all 4 have been sent
  val byteCounter = Counter(4, inc = io.bus.rsp.fire)
  io.bus.ack := byteCounter.willOverflow

  // Expose bus for the top-level router
  override def bus(): MyBusIo = io.bus
}

// === RTL Generation (standalone) ===

object MyCore extends App {
  SpinalConfig(
    targetDirectory = "target/gen",
    mergeAsyncProcess = true,
    mergeSyncProcess = true,
    genLineComments = true,
    removePruned = true,
    defaultConfigForClockDomains = ClockDomainConfig(
      resetKind = SYNC,
      resetActiveLevel = LOW
    ),
    defaultClockDomainFrequency = FixedFrequency(100 MHz)
  ).generateSystemVerilog(MyCore())
}
```

### 6.2 Minimal Simulation Test

```scala
package <organization>.<project>

import org.scalatest.funsuite.AnyFunSuite
import spinal.core._
import spinal.core.sim._

case class MyCoreTest() extends AnyFunSuite {
  private val simConfig = SimConfig
    .withWave                              // VCD waveform output
    .workspacePath("target/sim")
    .withConfig(SpinalConfig(
      mergeAsyncProcess = true,
      mergeSyncProcess = true,
      genLineComments = true,
      removePruned = true,
      defaultConfigForClockDomains = ClockDomainConfig(
        resetKind = SYNC,
        resetActiveLevel = LOW,
      ),
      defaultClockDomainFrequency = FixedFrequency(100 MHz)
    ))

  // Compile DUT once
  private val dutSim = simConfig.compile(MyCore())

  test("MyCore example test") {
    dutSim.doSim { dut =>
      val clockDomain = dut.clockDomain

      // Generate 100 MHz clock (10 ns period)
      clockDomain.forkStimulus(10)

      // Assert reset for 10 cycles, then release
      clockDomain.assertReset()
      clockDomain.waitRisingEdge(10)
      clockDomain.deassertReset()

      // Send a command over the bus
      dut.io.bus.cmd.valid #= true
      dut.io.bus.cmd.payload #= 0x12345678L  // 32-bit payload
      clockDomain.waitRisingEdge()
      dut.io.bus.cmd.valid #= false

      // Wait for processing to complete
      clockDomain.waitRisingEdge(5)

      // Verify that ack is asserted
      assert(dut.io.bus.ack.toBoolean)
    }
  }
}
```

### 6.3 Minimal Formal Test

```scala
package <organization>.<project>

import org.scalatest.funsuite.AnyFunSuite
import spinal.core.formal.SpinalFormalConfig
import spinal.core._

case class MyCoreFormalTest() extends AnyFunSuite {
  val TestFormalConfig: SpinalFormalConfig = SpinalFormalConfig()
    .withConfig(
      SpinalConfig(
        mergeAsyncProcess = true,
        mergeSyncProcess = true,
        genLineComments = true,
        removePruned = true,
        defaultConfigForClockDomains = ClockDomainConfig(
          resetKind = SYNC,
          resetActiveLevel = LOW,
        ),
        defaultClockDomainFrequency = FixedFrequency(100 MHz)
      ).includeFormal
    )
    .workspacePath(System.getenv().getOrDefault("FORMAL_WORKSPACE", "./target/formal"))
    .withDebug
    .withProve(50)   // Induction depth
    .withBMC(50)     // BMC bound
    .withCover(50)   // Cover depth

  private val dutSim = TestFormalConfig.compile(MyCore())

  test("MyCore formal verification") {
    dutSim.doVerify()
  }
}
```

---

## 7. Dependencies Summary

- Scala 2.13.x, SpinalHDL 1.14.x, ScalaTest 3.2.x.
- `sbt-scalafmt` for automatic formatting.
- `FirtoolPlugin` for Verilator generation (optional).
- Run tests: `sbt test`.
- Generate RTL: `sbt run`.
