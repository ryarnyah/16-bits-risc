# 16-bit RISC Microcore

16-bit RISC processor in SpinalHDL with two core implementations: a multi-cycle FSM core and a 4-stage pipelined core.

## Cores

### Multi-cycle Core (`Core.scala`)

FSM: `FETCH → DECODE → (LDI_FETCH) → WRITEBACK`

| Instruction | Cycles | Notes |
|---|---|---|
| ALU (ADD, SUB, AND, OR, XOR, SLL, SRL, ADDI, XORI) | 3 | FETCH + DECODE + WRITEBACK |
| LD (load) | 3 | FETCH + DECODE + WRITEBACK; data RAM response arrives in WRITEBACK |
| ST (store) | 2 | FETCH + DECODE; no writeback needed |
| BEQ/BNE/BLT (branch) | 2 | Same whether taken or not |
| JMP (jump) | 2 | |
| LDI (2-word immediate) | 4 | FETCH + DECODE + LDI_FETCH + WRITEBACK |

**Estimated average CPI:** ~2.6

### Pipelined Core (`PipCore.scala`)

4-stage pipeline: `IF → ID → EX → WB`, with forwarding and hazard detection.

| Instruction | CPI | Notes |
|---|---|---|
| ALU | 1 | Full forwarding resolves RAW hazards |
| ST | 1 | Write in EX |
| LD (no hazard) | 1 | 2-cycle bus latency hidden by pipeline |
| LD (load-use) | 2 | +1 stall when next instruction needs loaded register |
| BEQ/BNE/BLT | 2 | +1 stall from `stallBrId` |
| JMP | 2 | +1 stall from `stallBrId` |
| LDI (2-word) | 3 | 2 IF cycles + 1 stall from `stallLdiId` |

**Estimated average CPI:** ~1.3–1.5

## Verification

- Formal BMC(30) passes for both cores and all sub-components (ALU, Decoder, RegFile, BusInterface)
- 24 C test programs pass end-to-end on both cores (collatz, fib, gcd, div, mod, etc.)
- Emulator via Verilator; PipSoc emulator for the pipelined SoC

## Tools

- `cc.py` — C99 compiler to RISC assembly
- `asm.py` — assembler to hex
- `emulator/main.cpp` — Verilator-based emulator with interactive debug (step, run, read regs/mem)
