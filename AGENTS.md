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
- [x] PipCore all 24 C tests pass (PipSoc): collatz fix — ldWbVld not cleared on exBrTaken

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

14. **PipCore stallBrId fix — vClr/Mux ordering bug** (PipCore.scala):
    - Added `stallBrId`: when a branch/JMP is in ID, stall IF to prevent
      speculative fetch of sequential instructions that would need flushing.
    - Bug: `when(!io.instrRsp.fire) { vID := False }` (vClr) fires before the
      `Mux(vID, idType, EMPTY)` reads vID in the same cycle. When stallBrId
      stalls IF, vClr clears vID to 0, and the Mux sees vID=0 → rEX_type =
      EMPTY, losing the branch/JMP instruction.
    - Fix: replaced the Mux with a nested `when(vID) / otherwise` structure.
      The outer `when(!stallID && !exBrTaken)` fires unconditionally (for the
      no-stall/no-flush case), but the `when(vID)` guard enters the transfer
      block *before* vClr can clear vID. If vClr fires inside the block, the
      transfer already committed. The `otherwise` branch clears rEX_type to
      EMPTY when vID=0, preventing stale LD/ST from re-triggering bus requests.
    - Verified: addloop.asm BLT loop works (R3=3); C tests 4/24 pass on
      PipSoc (up from 2/24); standalone 24/24 pass; formal BMC(30) passes

15. **PipCore collatz fix — ldWbVld cleared by exBrTaken** (PipCore.scala):
    - Bug: `exBrTaken` unconditionally cleared `ldWbVld := False`, killing LD
      writeback when `JMP R5` immediately followed `LD R6,[R7]` (as in
      `__mul16` epilogue). The bus response arrived after `ldWbVld` was cleared,
      so `ldWbFiring = ldRspPending && ldWbVld` was False and R6 never restored.
      This corrupted the frame pointer in `__mul16`, causing `collatz.c` to
      return 0x51 (81) instead of 0x6F (111).
    - Fix: removed `ldWbVld := False` from the `exBrTaken` block. A LD can only
      reach EX after the branch resolves (ID→EX gated by `!exBrTaken`), so no
      speculative LD writeback needs suppression. Without the guard, legitimate
      back-to-back `LD`+`JMP` sequences complete correctly.
    - Verified: all 31 C tests pass on both PipSoc and multi-cycle core; formal
      BMC(30) passes.

16. **ADDI offset overflow fix** (cc.py):
    - Bug: `ADDI Rd, Rs, #imm6` with offset outside ±32 range was silently
      truncated by the assembler (`-34 & 0x3F = 30`, encoding +30 instead
      of -34). This caused the stack pointer (R7) to point into I/O space
      (≥0x1FFC), making all subsequent LD/ST access the UART instead of
      data RAM, causing program hangs/wrong results.
    - Fix: replaced direct `ADDI` emissions with range check + LDI+ADD
      fallback (via R2 to avoid clobbering R1 return value in epilogue) in:
      - Prologue stack allocation (`_gen_func`, line 467-468)
      - Epilogue stack deallocation (`_gen_func`, line 477-478)
      - VarRef array base address (`_gen_expr`, line 775)
      - `&var` address-of operator (`_gen_unop`, line 946)
      - Function call stack cleanup (`_gen_call`, line 1077)
    - Verified: all 41 C tests pass on both emulators, bench_15.c (15-element
      array, offset -34 for sum) correctly returns 0x69.

17. **ISA v2.1 implemented** (spec: `ISA-2.1.md`; RTL + assembler + tests):
    - RTL: `Decoder` banks A/B/F (SLT/SLTU/SRA via funct3, group-B immediates
      ANDI/ORI/SLLI/SRLI/SRAI cf≥011, CALL cf=001, HALT cf=010, LDI8/LDB/STB),
      4-bit `aluFunc`, `CoreState.HALT`, PipCore `InstrType.CALL` + `halted`
      register, `DataBusReq.isByte`, byte-lane RAM write mask in Soc/PipSoc
      (`Mem.write(..., mask)` granularity 8 → memory emitted as
      `dataRam_symbol0/1`).
    - `asm.py`: encoders for all 13 new instructions, `LDI`/`JMP label`
      auto-narrowing to `LDI8` (joint layout fixpoint over relaxation +
      narrowing + labels, widest-layout fallback), pseudo-ops per §7.2
      (MOV NEG NOT CLR LSL LSR ASR B BGT BGE BLE BLTU BGEU BLEU RET,
      3-op `ANDI Rd,Rs,#imm` expansion, `.scratch Rn`).
    - `run_tests.py`: 7 new ISA-level asm tests (`ASM_TESTS`) → 48 total.

18. **PipCore formal frame-0 state gap**: new state-consistency assert
    (`ldRspPending && ldIsByte → ldData[15:8]==0`) failed BMC at step 1 —
    clk2fflogic does not apply `init` attributes to the frame-0 state and
    `ldState/ldIsByte/ldAddr0/ldWbVld` were missing from the `assumeInitial`
    list (pre-existing LD asserts were transition properties, self-consistent
    under any frame-0 state). Fix: `assumeInitial` the LD FSM regs.

19. **PipCore adjacent-load loss — `ldIssueStall`**: a load in EX whose
    `req.fire` had not been captured yet could be replaced by the next ID→EX
    transfer: with a same-cycle bus response `ldRspPending` is still 0 during
    the issue cycle, so neither `ldActiveStall` (checks `ldRspPending`) nor
    `loadUseHazard` (requires `!ldRspPending`) stalls ID — the load's request
    was never issued and its destination never written. Caught by
    `byte_test.asm` (back-to-back `LDB`). Fix: `ldIssueStall =
    rEX_type===LD && ldState===IDLE` added to `stallID` (holds EX until the
    LD FSM owns the access; mirrors `stStall` for stores).

20. **PipSoc emulator lane-split RAM read**: byte write mask makes
    SpinalHDL emit `dataRam_symbol0`/`dataRam_symbol1` (2×8-bit lanes);
    `main_pipsoc.cpp` `readDataMem` reads both lanes.

21. **F4PGA `$buf` no-BEL fix + PipCore timing redesign** (first pass,
    85.5 MHz — timing closed in #22):
    - Root cause of `nextpnr: no BELs remaining for $buf`: the F4PGA flow
      generated `target/gen/PipSoc.sv` **without** a hex path → `instrRom`
      uninitialized → Yosys don't-care collapse (hollow netlist,
      `core.rID_pc[15:2]` undriven → `$buf` with 14× `z` inputs).
      Fix: `object Soc`/`object PipSoc` take `args(1)` = target directory;
      Makefile rule builds `f4pga/build/{Soc,PipSoc}.sv` from
      `F4PGA_PROG ?= examples/fib_uart.hex` (baked in), keeping
      `target/gen/*.sv` uninitialized for the emulator (`loadProgram()`
      pokes `instrRom` directly).
    - PipCore FPGA critical path was 15 levels (69.96 MHz):
      `rWB_rd → exFwd compare → ALU → idFwdExData → idFwdRsVal →
      idEffAddr → rEX_effAddr`. Redesign (all in `PipCore.scala`):
      1. EX ALU result no longer forwarded into ID (`idFwdExData =
         rEX_ldiData`, register only — LDI keeps its ID forward).
      2. `exIdFwdHazard`: ID reads used *combinationally* in ID (mem
         address rs, branch rs/rt, JMP rs / CALL rt) stall 1 cycle when
         EX holds an ALU/CALL result for that reg, until it reaches WB
         (`rWbHasExRes` clears the stall; self-clearing, no deadlock).
      3. `fwdFromWb` registered (mirrors `vWB && rWB_hasRd && rWB_rd=/=0`,
         incl. `dbgFlush` 0x03/04/05 → exact equivalence), saves 2 LUT
         levels on every forwarding path.
      4. Precise self-forward guard `!rWbHasExRes` replaces
         `!(rEX_hasRd && rWB_rd === rEX_rd)` (the old rd-based guard would
         wrongly block a *different* producer with the same rd, now that
         ID no longer latches the EX ALU result).
      5. EX→WB **holds** `rWB_result` while EX is retained
         (`rWbHasExRes`): the ID operand latch may be stale for an ALU
         whose predecessor writes its source, so only the first cycle in
         EX computes a correct `exResult` (test-caught: retained
         `ADDI R7,R7,#2` re-committed `1ff4` over `1ff8`).
    - `main_pipsoc.cpp` debug prints use `exResult` (Verilator no longer
      exposes `alu_1_io_result` after it left the ID path).
    - Result: PnR completes, 69.96 → **85.52 MHz** (was FAIL earlier in
      placement); critical path now `dataWordAddr[7] → … → FF CE`
      (2.3 ns logic + 9.4 ns routing).

22. **PipCore timing closure at 100 MHz** (PipCore.scala +
    emulator/main_pipsoc.cpp) — rounds on top of #21:
    - Round 1 (85.52 → 95.51 MHz, default seed):
      1. Store handshake `stFired`: ST leaves EX one cycle after
         `req.fire`, decided by a registered flag — the core no longer
         reads `req.ready` at all. Removes the critical cone
         `rEX_effAddr → isIoAddr(16b cmp) → ready → stStall → stallID
         → CE`; `req.valid` for ST is gated by `!stFired` so the held
         cycle cannot re-issue.
      2. IF-latched `rID_rsAddr`/`rID_rtAddr` (`rsAddrOf`/`rtAddrOf`
         mirror the Decoder equations on the incoming word, formally
         asserted `when(vID)`), so regfile reads, all forwarding
         compares and both hazard detects start from registers instead
         of a 2-3 level decode of `rID_instr`.
      3. `idEffAddr`: single adder with the byte/word immediate
         pre-muxed BEFORE the add (was two parallel adders muxed after;
         bit-identical modular addition).
    - Round 2 (95.51 → **103.83 MHz PASS** at 100 MHz, default seed):
      EX-stage re-decode eliminated — `rEX_rsAddr`/`rEX_rtAddr`/
      `rEX_immEn` are latched with `rEX_instr` in the ID→EX transfer
      (`rEX_immEn := decoder.io.isImmEn || decoder.io.isGrpBImm`), so
      the forwarding compares and the ALU opB mux select no longer wait
      on opcode/group-B/byte decode of `rEX_instr`.  The old
      combinational equations survive as formal-only references
      (`exRsAddrRef`/`exRtAddrRef` + immEn re-derivation, guarded by
      `rEX_type =/= EMPTY` — frame-0 safe because
      `assumeInitial(rEX_type === EMPTY)` and rEX_* are written only
      together in an ID→EX transfer).
    - Side effect: `rEX_instr`, `exRsAddr`, `exRtAddr` now have only
      formal consumers → pruned from the hardware (16 FFs + decode
      logic saved); `main_pipsoc.cpp` debug reads switched to the
      registered equivalents (`rEX_rsAddr`/`rEX_rtAddr`), and the
      `EX:instr=` debug fields now print `rs=/rt=`.
    - Result: `make f4pga-pipsoc` completes end-to-end
      (synth → PnR → fasm → frames → `f4pga/build/PipSoc.bit`, valid
      sync word `0009 0ff0`); post-route 85.52 → 95.51 → **103.83 MHz
      PASS at 100 MHz** (seed sweep on the round-1 netlist peaked at
      99.38 — the structural round-2 fix was required; default seed
      passes, no seed pinning needed).  New critical path starts at a
      register (`ldRd → … → FF D`, 2.0 ns logic + 7.7 ns routing).
    - Validated: 48/48 C+asm tests on BOTH emulators; 34/34 formal
      (BMC 30 incl. the new `stFired` set/clear and
      `rEX_rsAddr`/`rEX_rtAddr`/`rEX_immEn` equivalence asserts).

### Verification Results

- **RTL Generation**: ✓ SystemVerilog generated successfully
- **Formal Verification**: ✓ All 5 components pass at BMC(30): Core, ALU, Decoder, RegFile, BusInterface
- **PipCore Formal Verification**: ✓ PipCore passes BMC(30) (incl. v2.1 CALL/HALT/LDI8/LDB/STB/GrpBImm asserts)
- **SoC Formal**: ✓ Soc + PipSoc pass BMC(10) incl. byte write-mask TC
- **Full suite**: ✓ `sbt test` — 34/34 tests pass
- **Verilator Emulator**: ✓ Compiles and runs, responds to bus commands (LOAD_ADDR, LOAD_DATA, STEP, RUN, READ_REG, READ_MEM, READ_PC)
- **End-to-end counter program**: ✓ counter.asm loads, loops, counts R3 1..9, BLT branch, resets to 0
- **C99 Compiler (cc.py)**: ✓ Compiles C programs to RISC assembly, with peephole optimizer and runtime lib (mul/div/mod)
  - Fixed local array base address: uses `ADDI R1, R6, #off` instead of loading from uninitialized stack slot
  - Fixed stack allocation: correctly accounts for `array_size * 2` bytes
  - Fixed for-loop body parsing: `)` token sets `phase = 3` so body is not silently dropped
- **Assembler (asm.py)**: ✓ `JMP label` pseudo-op emits `LDI R4,#label; JMP R4` (3 words) to avoid ±32-word branch limit; v2.1: auto-narrows `LDI`/`JMP label` to `LDI8` when the target resolves to 0..255
- **UART program (fib_uart.c)**: ✓ Compiles via cc.py, runs via `make test-fib-uart` with piped input

### PipCore Verification

- **PipSoc Emulator**: ✓ Compiles and runs (pipsoc-emu), separate emulator using PipSoc Verilog
- **PipCore Formal Verification**: ✓ PipCore passes BMC(30)
- **C + asm tests on both cores**: **48/48 pass** (41 C tests + 7 v2.1 ISA asm tests, on both PipSoc and multi-cycle core)
  - Pipeline patterns: `ld_use_all` (LD→ADD/SUB/AND/OR), `ld_st_addr` (LDI address for ST),
    `forward_chain` (6-op ALU forwarding), `ldi_burst` (back-to-back LDI),
    `br_chain` (BEQ/BNE/BLT), `ld_ld_ld` (3 LDs), `st_ld_test` (ST→LD aliasing)
  - New: `shift_chain` (SLL→SRL forwarding), `addi_chain` (ADDI→ADDI→ADDI),
    `jmp_reg` (JMP R5 call/return), `not_taken` (branch not-taken + fall-through),
    `ld_st_mix` (interleaved LD/ST), `ptr_chain` (**pp double dereference),
    `r0_test` (R0 is always 0), `store_zero` (ST 0 to stack),
    `loop_array` (local array in for loop), `call_deep` (nested function calls)

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

v2.1 additions (encoded in padding/`must-be-zero` fields, reserved encodings
still NOP — see `ISA-2.1.md` §11):

| Mnemonic | Encoding home | Test |
|----------|---------------|------|
| SLT / SLTU / SRA | op 4/8 + funct3 001/010 | `slt_test.asm`, `sra_test.asm` |
| ANDI/ORI/SLLI/SRLI/SRAI | group B (JMP), cf≥011, in-place | `imm_test.asm` |
| CALL / HALT | group B, cf=001 / 010 | `call_test.asm`, `ldi8_rsvd_test.asm` |
| LDI8 / LDB / STB | group F (LDI), b8 / mf=01/10 | `byte_test.asm`, `ldi8_rsvd_test.asm` |
| BGE/BLE/BLTU/BGEU/BLEU/B/BGT/MOV/… | assembler pseudo-ops (§7.2) | `bge_test.asm` |

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
- [x] `make f4pga-pipsoc` (PipSoc): **complete end-to-end through
  bitstream** — `F4PGA_PROG` baked into `f4pga/build/PipSoc.sv` (fixes
  `$buf` no-BEL); timing **103.83 MHz PASS at 100 MHz** (fixes #21/#22)
- [ ] `f4pga_program` needs a Basys3 board connected via USB

### GCC 15 Compatibility

Several third-party C++ projects used `uint8_t` without `#include <cstdint>`:
- `json11/json11.cpp` (nextpnr-xilinx) – patched after every fresh clone
- `memory_mapped_file.h` (prjxray) – patched after every fresh clone

Both patches are applied automically by the Makefile targets via `sed -i`.