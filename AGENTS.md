# RISC Core Implementation Progress

## Project: 16-bit RISC Microcore (SpinalHDL)

### Status

- [x] ISA analysis — two errors found and fixed
- [x] Project structure (build.sbt, directories)
- [x] Core types & enums (Opcodes, ALUOp, CoreState, Bus)
- [x] Component decomposition (RegFile, ALU, Decoder, BusInterface) — individually verified
- [x] Core refactored to use sub-components (RegFile, ALU, Decoder, BusInterface) — compiles, generates Verilog
- [x] LD bug fix: write `dataLoad` to register file instead of stale `aluRes`
- [x] Emulator (emulator/main.cpp) — Verilator compiles VCore, emulator binary works
- [x] Emulator end-to-end test: counter.asm loads and loops correctly (R3 1..9, BLT branch, reset to 0)
- [x] PipCore load-use hazard fix: ID→EX gated by `!stallId` (includes hazard stall, not just bus stall), `loadUseWb` covers phase 0 (waiting for bus data)
- [x] PipCore formal: BMC(30) passes
- [x] PipCore LD/ST end-to-end: ldst_test.asm (LDI→ST→LD→SUB) passes, R3=0

### Design Decisions

- **Package:** `risc`
- **Bus:** 8-bit Stream cmd/rsp + ack, per INSTRUCTIONS.md
- **Core:** Multi-cycle FSM (FETCH→DECODE→LDI_FETCH→WRITEBACK)
- **Memory:** Synchronous single-port RAM, word-addressable (16-bit words)
- **R0:** Hardwired to 0, writes ignored
- **Formal:** SpinalFormalConfig with BMC/prove/cover at depth 50
- **Emulator:** C++ via Verilator, reads program from hex file

### Key Fixes Applied

1. **ISA fixes** (in ISA.md):
   - SRL decode: `is_alu = ~op[3] | (op == 4'b1000)` includes opcode 0x8
   - LD/ST offset range: –32 to +31 (6-bit signed immediate)

2. **Bus timing fixes** (Core.scala):
   - `rspValid` register stays high until `busWordRsp.fire`
   - `cmdDone` registered via `RegNext` makes `ack` visible for 1 full cycle

3. **StreamWidthAdapter bug fix**:
   - Replaced with manual byte assembly using `Counter(4, inc = ...)` + shift register
   - Avoids Verilog width-truncation bug: `{8'b_payload, 24'b_shifted}` → 24-bit reg drops payload

4. **LD write bug fix** (Core.scala):
   - `wrData := Mux(isLD, dataLoad, aluRes)` — writes loaded data instead of stale pipeline register
   - Original code wrote `aluRes` to regFile in WRITEBACK before `aluRes := dataLoad` took effect

5. **Component decomposition** (all components):
   - Core now instantiates RegFile, ALU, Decoder, BusInterface as sub-components
   - Redundant inline logic removed; formal assertions focus on FSM/pipeline integration

6. **cmdStrb registered** (BusInterface.scala):
   - Changed from combinational (same cycle as 4th byte) to registered
   - Ensures `cmdWord` has the full assembled 4-byte value when cmdStrb fires
   - TC-BI-3/TC-BI-5 updated for new timing

7. **Core formal reset guard** (Core.scala):
   - Added `resetn` guard to all temporal assertions
   - Hardware reset overrides pipeline registers — assertions must be skipped when `resetn=0`
   - Without this guard, BMC found counterexample at step 20 (reset fires while past state was WRITEBACK)

8. **Emulator rewritten** (emulator/main.cpp, emulator/Makefile):
   - Uses Verilator 5 `--build --exe` for single-step compilation
   - Uses clean Verilog port names (no more `_zz_` mangling)
   - Supports `s [n]` for multi-step; shows PC after each step

 9. **BLT branch relaxation in assembler** (asm.py):
    - BEQ/BNE/BLT with offset outside ±32 range are relaxed to inverted condition + JMP
    - Example: `BEQ Rs,Rt,far` → `BNE Rs,Rt,3; JMP far` (1+3 words)
    - BLT added BGE as opposite opcode; relaxation follows same pattern
    - Prevents silent truncation of 6-bit signed offset field

10. **C compiler bug fixes** (cc.py):
    - `_gen_cond` constant condition fix: `cv_bool != invert` was inverted, causing `while(1)` to exit immediately
    - `_gen_cond` TOK_GE dead code removed: leftover `emit_lbl(m)` referenced undefined label `m`
    - `_gen_cond` TOK_LOR `invert=True` fix: replaced wrong jump logic with `_gen_cond(left, m, True); JMP end; m: _gen_cond(right, target, True); end:`
    - `_gen_if` label chain fix: nested if-else (else-if) now passes outer end label to inner if, preventing inner return from falling through to the outer loop-back

11. **Emulator stdin queue** (emulator/main.cpp):
    - Replaced single-char `stdinChar/stdinAvailable` with `std::queue<char>` + mutex
    - Allows multiple input characters to be buffered while UART TX is busy

12. **PipCore load-use hazard fix — deadlock fix** (PipCore.scala):
    - Root cause: ID→EX transfer was gated by `!stallEx` (bus stalls only), not `!stallId` (which includes load-use hazard stalls). When a LD in EX had SUB in ID needing the loaded register, SUB entered EX with stale regfile value before the LD data was available via the bus.
    - Fix 1: Changed ID→EX transfer condition from `!stallEx` to `!stallId` so load-use hazards stall the ID stage
    - Fix 2: Changed `loadUseWb` from covering phases 1-2 (data already available via forwarding) to phase 0 (waiting for data from bus), where forwarding can't help
    - Bug introduced by Fix 1: `ldUseStall` in `stallId` blocked ID→EX, but `EX→WB` still fired (on `!stallWb`), transferring the stale LD to WB. When the LD finished (`ldActive` went 0), `loadUseEx` re-detected the stale `exVld=1, exIsLD=1`, and `EX→WB` immediately re-entered `ldActive` — no pending bus response, state machine stuck at phase 0 forever, all regfile writes gated by `ldActive=1`.
    - Fix 3 (exServiced): Added `exServiced` register (set by EX→WB, cleared by ID→EX). Used as `!exServiced` guard in both `loadUseEx` and the `ldActive` trigger. Prevents stale EX from re-entering the LD state machine or re-triggering hazard detection after its instruction has already left EX.
    - Verified: formal BMC(30) passes, ldst_test.asm shows R3=0 (R2-R1=42-42), data init loop no longer deadlocks

13. **PipCore SpinalEnum + pipeline refactoring** (PipCore.scala):
    - Replaced 7 individual EX Bool registers (`exIsALU`, `exIsLD`, `exIsST`, `exIsJMP`, `exIsBranch`, `exIsLDI`, `exIsImmEn`) with single `rEX_type`: `InstrType()` SpinalEnum register
    - `InstrType`: `EMPTY, ALU, LD, ST, JMP, BR, LDI` — makes illegal state combinations impossible
    - Replaced manual LD state machine (`ldPending`/`ldRspPending` registers + `wbLdPhase`) with `ldState`: `LdPhase()` register (IDLE/WAIT_BUS/DATA_READY)
    - `exIsImmEn` now computed combinatorially from opcode at EX stage (no longer piped from ID)
    - Removed `stallWb` (WB no longer stalls independently)
    - Merged IF/ID pipeline registers (`ifInstr`→`rID_instr`, `idVld`→`vID`, etc.)
    - Moved LD state FSM before stall logic (defines `ldPending`/`ldRspPending` before use)
    - LD FSM uses `rEX_type =/= InstrType.LD` instead of `exBrTaken` for abort-on-flush (no circular dependency)
    - Debug bus consolidated: `flsPipeline()` helper for cmds 0x03/0x04/0x05
    - Formal BMC(30) passes with comprehensive assertions

### Verification Results

- **RTL Generation**: ✓ SystemVerilog generated successfully
- **Formal Verification**: ✓ All 5 components pass at BMC(30): Core, ALU, Decoder, RegFile, BusInterface
- **PipCore Formal Verification**: ✓ PipCore passes BMC(30)
- **Verilator Emulator**: ✓ Compiles and runs, responds to bus commands (LOAD_ADDR, LOAD_DATA, STEP, RUN, READ_REG, READ_MEM, READ_PC)
- **End-to-end counter program**: ✓ counter.asm loads, loops, counts R3 1..9, BLT branch, resets to 0
- **C99 Compiler (cc.py)**: ✓ Compiles C programs to RISC assembly, with peephole optimizer and runtime lib (mul/div/mod)
- **Assembler (asm.py)**: ✓ `JMP label` pseudo-op emits `LDI R4,#label; JMP R4` (3 words) to avoid ±32-word branch limit
- **UART program (fib_uart.c)**: ✓ Compiles via cc.py, runs via `make test-fib-uart` with piped input

### PipCore Verification

- **PipSoc Emulator**: ✓ Compiles and runs (pipsoc-emu), separate emulator using PipSoc Verilog
- **PipCore Formal Verification**: ✓ PipCore passes BMC(30)
- **End-to-end LD/ST test**: ✓ ldst_test.asm: LDI 42, ST to mem, LD to R2, SUB R2-R1→R3, BEQ loop — R3=0 (correct: 42-42=0)
- **C compiled tests on PipSoc**: 2/24 pass (mulonly, div_test) — **pre-existing failures** (PipCore was always broken for C tests; old multi-cycle Core passes all 24)

#### ISA Coverage (24 test programs, all pass via `make test-programs`)

| Opcode | Mnemonic | Test |
|--------|----------|------|
| 0x0 | ADD | `all_alu_test.c`, `fib.c`, `multest.c` |
| 0x1 | ADDI | everywhere (stack ops, load const) |
| 0x2 | XOR | `all_alu_test.c`, `__mul16` (XOR R3,R3,R3) |
| 0x3 | XORI | `xori_test.c` (`~0x00FF` via XORI #-1) |
| 0x4 | SUB | `all_alu_test.c`, `fib.c`, `dec.c` |
| 0x5 | AND | `all_alu_test.c`, `__mul16` |
| 0x6 | OR | `or_test.c`, `all_alu_test.c` |
| 0x7 | SLL | `all_alu_test.c`, `__mul16` |
| 0x8 | SRL | `all_alu_test.c`, `__mul16` |
| 0x9 | LD | everywhere (load args, locals, stack) |
| 0xA | ST | everywhere (store args, locals, stack) |
| 0xB | JMP | `fib.c`, `dec.c` (call/return via R5) |
| 0xC | BEQ | `beq_test.c`, `__mul16` loops |
| 0xD | BNE | `bne_test.c` (`while (i != 0)`) |
| 0xE | BLT | `fib.c`, `all_alu_test.c`, `collatz.c` |
| 0xF | LDI | everywhere (load addresses, constants) |

- **SRA** (arithmetic right shift): NOT implemented — no aluFunc encoding, no assembler mnemonic, no decoder entry. 16 opcode slots are all filled.

### FPGA Flow (F4PGA for Basys3 / xc7a35tcpg236-1)

- [x] `vendor-f4pga` installs self-contained toolchain into `vendor/`:
  - oss-cad-suite (Yosys, openFPGALoader) – 684 MB pre-built
  - Boost 1.90 built from source (5 libs: filesystem, program_options, iostreams, system, thread)
  - Eigen3 headers from GitLab
  - nextpnr-xilinx built from gatecat/xilinx-upstream
  - Chipdb `xc7a35tcpg236-1.chipdb` (87.9 MB) generated via bbaexport + bbasm
  - prjxray built from source (xc7frames2bit for frames→bitstream)
  - FASM Python stub (replaces uninstallable fasm package)
- [x] `make f4pga` runs synth → PnR → bitstream end-to-end:
  - Yosys `synth_xilinx` with `delete {t:$scopeinfo}` fix
  - nextpnr-xilinx PnR with basys3.xdc constraints
  - FASM → frames (minimal Python parser + prjxray fasm_assembler)
  - Frames → .bit (xc7frames2bit), valid Xilinx sync word `0009 0ff0...`
- [ ] `f4pga_program` needs a Basys3 board connected via USB

### GCC 15 Compatibility

Several third-party C++ projects used `uint8_t` without `#include <cstdint>`:
- `json11/json11.cpp` (nextpnr-xilinx) – patched after every fresh clone
- `memory_mapped_file.h` (prjxray) – patched after every fresh clone

Both patches are applied automically by the Makefile targets via `sed -i`.