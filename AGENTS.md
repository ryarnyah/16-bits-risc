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

9. **Branch relaxation in assembler** (asm.py):
   - BEQ/BNE with offset outside ±32 range are relaxed to inverted condition + JMP
   - Example: `BEQ Rs,Rt,far` → `BNE Rs,Rt,3; JMP far` (1+3 words)
   - Prevents silent truncation of 6-bit signed offset field
   - BLT relaxation not yet supported (error on out-of-range)

### Verification Results

- **RTL Generation**: ✓ SystemVerilog generated successfully
- **Formal Verification**: ✓ All 5 components pass at BMC(30): Core, ALU, Decoder, RegFile, BusInterface
- **Verilator Emulator**: ✓ Compiles and runs, responds to bus commands (LOAD_ADDR, LOAD_DATA, STEP, RUN, READ_REG, READ_MEM, READ_PC)
- **End-to-end counter program**: ✓ counter.asm loads, loops, counts R3 1..9, BLT branch, resets to 0
- **C99 Compiler (cc.py)**: ✓ Compiles C programs to RISC assembly, with peephole optimizer and runtime lib (mul/div/mod)
- **Assembler (asm.py)**: ✓ `JMP label` pseudo-op emits `LDI R4,#label; JMP R4` (3 words) to avoid ±32-word branch limit

#### ISA Coverage (14 test programs, all pass via `make test-programs`)

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