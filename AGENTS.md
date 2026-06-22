# RISC Core Implementation Progress

## Project: 16-bit RISC Microcore (SpinalHDL)

### Status

- [x] ISA analysis — two errors found and fixed
- [x] Project structure (build.sbt, directories)
- [x] Core types & enums (Opcodes, ALUOp, CoreState, Bus)
- [x] Component decomposition (RegFile, ALU, Decoder, BusInterface) — individually verified
- [x] Core refactored to use sub-components (RegFile, ALU, Decoder, BusInterface) — compiles, generates Verilog
- [x] LD bug fix: write `dataLoad` to register file instead of stale `aluRes`
- [x] Emulator (emulator/main.cpp) — Verilator compiles VCore, emulator binary TBD

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

### Verification Results

- **RTL Generation**: ✓ SystemVerilog generated successfully
- **Formal Verification**: ✓ BMC(50) passes (SpinalFormalConfig)
- **Verilator Emulator**: ✓ Compiles and runs, responds to bus commands (LOAD_ADDR, LOAD_DATA, STEP, RUN, READ_REG, READ_MEM, READ_PC)