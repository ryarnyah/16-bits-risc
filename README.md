# 16-bit RISC Microcore

16-bit RISC processor in SpinalHDL with two core implementations: a multi-cycle FSM core and a 4-stage pipelined core.

## Cores

### Multi-cycle Core (`Core.scala`)

FSM: `FETCH → DECODE → (LDI_FETCH) → WRITEBACK`

Buses use **0-cycle** instruction ROM (`readAsync`, combinational) and **1-cycle** data RAM (`readSync` + registered
valid).

| Instruction                                        | Cycles | State Walk  | Notes                                               |
|----------------------------------------------------|--------|-------------|-----------------------------------------------------|
| ALU (ADD, SUB, AND, OR, XOR, SLL, SRL, ADDI, XORI) | 3      | FE→DE→WB    | ALU is combinational in DECODE                      |
| LD (load)                                          | 3      | FE→DE→WB    | DECODE sends req, WB waits 1 cycle for data bus rsp |
| ST (store)                                         | 2      | FE→DE       | No writeback; completes when req.fire               |
| BEQ/BNE/BLT (branch)                               | 2      | FE→DE       | Target computed in DECODE; same whether taken/not   |
| JMP (jump)                                         | 2      | FE→DE       |                                                     |
| LDI (2-word immediate)                             | 4      | FE→DE→LI→WB | LI = second instruction fetch for immediate word    |

**Estimated average CPI:** ~2.8

### Pipelined Core (`PipCore.scala`)

4-stage pipeline: `IF → ID → EX → WB`, with forwarding and hazard detection.

Buses: 0-cycle instruction ROM (`readAsync`), 1-cycle data RAM (`readSync`). The 1-cycle data latency is the dominant
stall source.

| Instruction                                        | CPI | Stall Source  | Notes                                                                                    |
|----------------------------------------------------|-----|---------------|------------------------------------------------------------------------------------------|
| ALU (ADD, SUB, AND, OR, XOR, SLL, SRL, ADDI, XORI) | 1   | —             | Full forwarding resolves all RAW hazards; no-stall                                       |
| ST                                                 | 1   | —             | Write in EX; no writeback needed                                                         |
| LD                                                 | 1   | —             | Async data RAM: response same cycle as request, skip WAIT_BUS                            |
| LD (load-use)                                      | 1   | —             | LD data forwarded via async read; no stall for dependent instructions                    |
| BEQ/BNE/BLT (not taken)                            | 1   | —             | Predict not-taken: sequential fetch continues, 0 stall                                   |
| BEQ/BNE/BLT (taken)                                | 2   | `exBrTaken`   | +1 cycle: speculative ID instruction flushed, PC redirected                              |
| JMP                                                | 2   | `exBrTaken`   | +1 cycle: sequential fetch flushed, PC redirected to register target                     |
| LDI (2-word)                                       | 3   | `stallLdiId`  | 2 IF cycles (opcode + immediate) + 1 cycle stall of IF while LDI is in ID                |

**Estimated average CPI:** ~1.2

## PipCore Speed Enhancements (TODO)

| # | Enhancement | Impact | Complexity | Status |
|---|-------------|--------|------------|--------|
| 1 | Branch prediction (predict not-taken) | Branch penalty 2→1 cycle | Moderate | **Done** |
| 2 | Async data RAM read | LD min 3→2 cycles, load-use stall eliminated | Low | **Done** |
| 3 | Reduce forwarding mux depth | ID forwarding 4→2 levels, higher Fmax | Low | **Done** |
| 4 | Decoupled LD unit | Remove LD→use stall for independent instructions | High | Cancelled (limited to UART reads only) |
| 5 | Deeper pipeline (formal WB stage) | Higher Fmax | Moderate | Cancelled (adds 1 cycle latency to all instructions) |

## Verification

- Formal BMC(30) passes for both cores and all sub-components (ALU, Decoder, RegFile, BusInterface)
- 31 C test programs pass end-to-end on both cores (collatz, fib, gcd, div, mod, etc.)
- Targeted tests for pipeline hazards: load-use chaining, ALU forwarding, LDI bursts, branch chains, consecutive loads,
  ST→LD aliasing
- Emulator via Verilator; PipSoc emulator for the pipelined SoC

## Tools

- `cc.py` — C99 compiler to RISC assembly
- `asm.py` — assembler to hex
- `emulator/main.cpp` — Verilator-based emulator with interactive debug (step, run, read regs/mem)
