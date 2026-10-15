# 16-bit RISC Microcore

16-bit RISC processor in SpinalHDL with two core implementations: a multi-cycle FSM core and a 4-stage pipelined core.

Current ISA: **v3** — normative spec in [`ISA-3.md`](ISA-3.md) (v2.1 frozen in [`ISA-2.1.md`](ISA-2.1.md); v3 change
tracker in [`ISA-3.0.md`](ISA-3.0.md); bus protocol in [`INSTRUCTIONS.md`](INSTRUCTIONS.md); history in
[`PLAN.md`](PLAN.md) / [`AGENTS.md`](AGENTS.md)).

## Cores

### Multi-cycle Core (`Core.scala`)

FSM: `FETCH → DECODE → (LDI_FETCH) → WRITEBACK`, plus `HALT` (left by reset only). Interrupts are accepted on
`FETCH` (fixed vector `0x0008`, platform model — `ISA-3.md` §10.4).

Buses use **0-cycle** instruction ROM (`readAsync`, combinational) and **1-cycle** data RAM (`readSync` + registered
valid).

| Instruction                                             | Cycles | State Walk   | Notes                                                     |
|---------------------------------------------------------|--------|--------------|-----------------------------------------------------------|
| Register ALU (ADD, SUB, AND, OR, XOR, SLL, SRL, SLT, SLTU, SRA) | 3 | FE→DE→WB  | ALU is combinational in DECODE                            |
| Immediate ALU (ADDI, XORI, ANDI, ORI, SLLI, SRLI, SRAI) | 3      | FE→DE→WB     |                                                           |
| LD (word; offset in **words** ×2)                        | 3      | FE→DE→WB     | DECODE sends req, WB waits 1 cycle for data bus rsp       |
| ST (word)                                                | 2      | FE→DE        | No writeback; completes when req.fire                     |
| LDB / STB (byte lanes)                                   | 3 / 2  | FE→DE→WB / FE→DE | Loaded byte zero-extended                             |
| BR (Z / NZ / MI / PL, ±64 words)                         | 2      | FE→DE        | Same cost taken or not; far targets relax to inverse BR + LDI + JMP |
| JMP / CALL (register-indirect)                           | 2      | FE→DE        | `CALL` writes the link register (R5 by convention)        |
| CALLR / JMPR (PC-relative ±256 / ±2048 words)            | 2      | FE→DE        | v3.3                                                      |
| LDI (2-word immediate)                                   | 4      | FE→DE→LI→WB  | LI = second instruction fetch for the immediate word      |
| LDI8 (8-bit immediate)                                   | 3      | FE→DE→WB     | Auto-narrowed from `LDI` when the value fits              |
| HALT                                                     | —      | FE→DE→HALT   | Trapped until reset                                       |

`BEQ/BNE/BLT/BGE/BLE/BLTU/…`, `MOV`, `RET`, … are assembler pseudo-ops that lower to `BR` (plus `SLT/SLTU` when a
second register operand is needed) — see `ISA-3.md` §7.2.

**Estimated average CPI:** ~2.8

### Pipelined Core (`PipCore.scala`)

4-stage pipeline: `IF → ID → EX → WB`, with forwarding and hazard detection.

Buses: 0-cycle instruction ROM (`readAsync`), 1-cycle data RAM (`readSync`) with an EX-side **store-to-load
bypass**: a `LD` that aliases a preceding `ST` is served from the store-tracker snapshot without a bus read
(completion +1 cycle).

| Instruction                                   | CPI | Stall Source     | Notes                                                                                   |
|-----------------------------------------------|-----|------------------|-----------------------------------------------------------------------------------------|
| Register / immediate ALU                      | 1   | —                | Full forwarding resolves all RAW hazards                                                 |
| ST                                             | 1   | `stStall`        | Held in EX until `req.fire` (UART backpressure can add a cycle)                          |
| LD                                             | 1   | —                | Result forwarded from EX/WB; same-address LD-after-ST served from the store snapshot     |
| LD (load-use: operand consumed in ID)          | 2   | `loadUseHazard` / `exIdFwdHazard` | 1-cycle stall; consumers in EX/WB forward without stalling           |
| BR (not taken)                                 | 1   | —                | Predict not-taken: sequential fetch continues, 0 stall                                   |
| BR (taken)                                     | 2   | `exBrTaken`      | +1 cycle: speculative ID instruction flushed, PC redirected                              |
| JMP / CALL / CALLR / JMPR                      | 2   | `exBrTaken`      | +1 cycle: sequential fetch flushed, PC redirected to the new target                      |
| LDI (2-word)                                   | 3   | `stallLdiId`     | 2 IF cycles (opcode + immediate) + 1 cycle stall of IF while LDI is in ID               |

**Estimated average CPI:** ~1.2

## Memory Map (identical in `Soc` and `PipSoc`)

`ISA-3.md` §10.1/§10.4:

| Range         | Function                                                                                     |
|---------------|----------------------------------------------------------------------------------------------|
| `0x0000`–`0x1FFF` | Data RAM (8 KB single-port, word-addressable; all of it usable by software)               |
| `0x2000`–`0xFEFF` | Unmapped: reads return 0, writes are dropped (deterministic for every address pattern)     |
| `0xFF00`–`0xFFF7` | UART: read = RX byte (zero-extended), write = TX low byte                                   |
| `0xFFF8`      | Interrupt CTRL: b0 IMASK (R/W, **cleared by hardware on entry**), b1 SWIRQ (R/W), b2 raw pending (read) |
| `0xFFFA`      | Interrupt EPC read window — RETI = `LD Rn,[0xFFFA]; JMP Rn`                                   |
| `0xFFFC`–`0xFFFF` | UART alias (conventional `0xFFFC` RX / `0xFFFE` TX) — the same UART as `0xFF00..0xFFF7`    |

Interrupt vector: fixed `0x0008`. No interrupt instructions exist — entry/return/masking are platform mechanisms
(`ISA-3.md` §10.4).

## Verification

- **52/52 program tests** (41 C + 11 asm) pass end-to-end on **both** emulators:
  `python3 run_tests.py --emulator emulator/build/emulator` and
  `python3 run_tests.py --emulator emulator/build/obj_dir/pipsoc-emu`
- **Instruction coverage audit** — `python3 isa_coverage.py` walks every test source through the assembler and
  reports missing hardware instructions / pseudo-ops
- **Formal (BMC)** — `sbt test`: Core, ALU, Decoder, RegFile, BusInterface, PipCore, PipCoreSim, Soc, PipSoc and
  Uart suites (pipeline/hazard/interrupt/memory-map properties)
- **Cycle benchmark** — `python3 bench.py` (nop/alu/mem on both cores, compared against recorded baselines)
- Targeted pipeline tests: load-use chains, ALU forwarding, LDI bursts, branch chains, consecutive loads,
  ST→LD aliasing, byte lanes, far-branch relaxation, interrupts
- Emulator via Verilator with interactive debug (step, run, read regs/mem); helpers:
  `make test-programs-all`, `make test-fib-uart`

## FPGA (F4PGA, Basys3 xc7a35tcpg236-1)

- `make f4pga` — multi-cycle `Soc` end-to-end (synth → PnR → FASM → frames → `.bit`):
  **127.02 MHz** at the default seed; seed sweep 7/8 finals PASS **109.5–131.3 MHz**
  (the 8th run was externally killed mid-routing after its 109.09 mid-route PASS)
- `make f4pga-pipsoc` — pipelined `PipSoc` end-to-end: **106.62 MHz** at the default seed;
  seed sweep 8/8 PASS **106.1–112.7 MHz**
- Both pass the 100 MHz target; `F4PGA_SEED=<n>` picks the nextpnr seed (deterministic per netlist + seed)
- `make vendor-f4pga` builds the self-contained toolchain under `vendor/`

## PipCore Speed Enhancements (Phase 3 history)

| # | Enhancement | Impact | Complexity | Status |
|---|-------------|--------|------------|--------|
| 1 | Branch prediction (predict not-taken) | Branch penalty 2→1 cycle | Moderate | **Done** |
| 2 | Async data RAM read | LD min 3→2 cycles, load-use stall eliminated | Low | **Done** (later superseded: v3.1 word LD/ST uses the sync 1-cycle RAM + store-to-load bypass; a 1-cycle load-use stall remains for operands consumed in ID) |
| 3 | Reduce forwarding mux depth | ID forwarding 4→2 levels, higher Fmax | Low | **Done** |
| 4 | Decoupled LD unit | Remove LD→use stall for independent instructions | High | Cancelled (limited to UART reads only) |
| 5 | Deeper pipeline (formal WB stage) | Higher Fmax | Moderate | Cancelled (adds 1 cycle latency to all instructions) |

Later timing rounds (Key Fixes 24/29) additionally re-sourced the stall/handshake cones from registers, and Key
Fix 30 did the same for the multi-cycle SoC — see `AGENTS.md`.

## Tools

- `cc.py` — C99 compiler to RISC assembly
- `asm.py` — assembler to hex
- `run_tests.py` — program test suite for both emulators
- `isa_coverage.py` — static instruction / pseudo-op coverage audit
- `bench.py` — cycle-count benchmark (nop/alu/mem, both cores)
- `emulator/main.cpp` — Verilator-based emulator with interactive debug (step, run, read regs/mem)
- `Makefile` — `emulator`, `test-programs-all`, `test-fib-uart`, `f4pga`, `f4pga-pipsoc`, `vendor-f4pga`
