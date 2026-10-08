# Plan — ISA v3 (breaking) → Maintainability → Speed

Order is deliberate: breaking ISA changes first (they invalidate binaries,
tests, formal, and docs — everything downstream), then structural cleanup of
the code the ISA churn touches, then speed work on the stabilized design.
Do not interleave: speed numbers are meaningless across an ISA break, and
refactors are wasted if the decoder they clean up is about to change.

Baseline (v2.1, before any v3 work) — re-verify before each phase:
- `sbt test` — 34/34 pass (formal: Core/ALU/Decoder/RegFile/BusInterface
  BMC(30), PipCore BMC(30), Soc/PipSoc BMC(10)).
- `make test-programs-all` — 49/49 on both emulators (41 C + 8 asm).
- `python3 isa_coverage.py` — all 29 instructions covered.
- Fmax: PipSoc 103.83 MHz PASS at 100 MHz (`make f4pga-pipsoc`, default seed).

Compat policy: v2.1 `.hex` files are frozen artifacts. Each v3 step
recompiles everything from source (`.c` via `cc.py`, hand `.asm` via
`asm.py`); stale checked-in `examples/*.asm|*.hex` refresh automatically
on the next `make test-programs-all`. Never hand-edit generated `.asm`.

Normative deltas live in `ISA-3.0.md` (this file tracks process only).

---

## Phase 1 — ISA v3 breaking changes (do first)

### V3.0 Spec scaffold
- [x] `ISA-3.0.md` created: status per step (proposed/implemented), §2.3
  word-scale rule.
- [ ] Each step below flips its section from proposed → implemented only
  when its gate passes.

### V3.1 Word-scaled LD/ST offsets [IN PROGRESS — RTL+toolchain this pass]
Why: §2.3 "bit 0 ignored" wastes half the 64 encodings (odd aliases even);
effective range is 32 words, not 64. `off` becomes words:
`addr = Rs + sext(off)*2`, range −64..+62 bytes, all 64 encodings distinct.
- Files: `Core.scala` effAddr, `PipCore.scala` idAddrImm/idEffAddr,
  `asm.py` LD/ST (word units, range −32..+31, reject nothing — all ints
  in range are legal), `cc.py` `_emit_ld/_emit_st` (assert even, `//2`,
  byte range −64..+62), `cc.py:961` `[R7 +2]`→`[R7 +1]`.
  LDB/STB, ADDI, branches: unchanged.
- Gate: `sbt compile` ✓ + `cc.py` over all `examples/*.c` to /tmp ✓
  (even-offset asserts hold, word offsets all in range) + `asm.py`
  spot-checks ✓ (`LD R1,[R6 -1]` → `0x93BF`). Still open: full `sbt test`
  + `test-programs-all` 49/49 with rebuilt emulators.

### V3.2 Unified branch opcode (largest assembler simplification) [DONE]
Why: BEQ/BNE/BLT burned 3 opcodes × 4096 encodings for 3 conditions, each
±32 words → 5-word far-`BLT` relaxation (`AGENTS.md` #23). One `BR` opcode
`1100|Rs|cc|off7` (cc: Z/NZ/MI/PL) frees `0xD/0xE`, gives ±64 words, and
reduces relaxation to a uniform exact-inverse far form
(`BR invcc,+3 ; LDI ; JMP`, no special cases — Z↔NZ/MI↔PL always exact).
- Files: `Decoder.scala` (isBR/brCC, 0xD/0xE→reserved), both cores (single
  Rs vs 0/sign-bit, off7 target), `asm.py` (BR encoder, minimal relaxation,
  B/BGT/BGE/... via SLT/SLTU+BR), `cc.py` (`_gen_cond` → SLT/XOR+BR, all
  single-reg sites → BR, div/mod BLT → SLT+BR+SUB), hand tests
  (pseudo_test rewritten trap-over style, counter SLT+BR), `PipCoreSim*`
  (10 BR tests), `isa_coverage.py` (BR).
- Gate: 49/49 both emus ✓, PipCoreSimTest 26/26 ✓, `sbt test` 35/35 ✓
  (formal BMC30 Decoder/Core/PipCore), isa_coverage clean ✓.

### V3.3 Single-word constants: `LDIH` + retire 2-word `LDI` (largest RTL deletion)
Why: 2-word `LDI` forces `ldiPending/ldiHeader` FSMs, `stallLdiId`,
`LDI_FETCH` state, PC+=4 special cases in both cores + formal + emulator.
`LDIH Rd,#imm8` in reserved `mf=11` slot (`Rd[15:8]=imm8`, low preserved):
any 16-bit const = `LDI8`+`LDIH` (2 words — same size, no fetch FSM).
- Files: `Decoder.scala` (`mf=11`), both cores (delete LDI path, keep
  LDI8/LDIH on ALU path), `asm.py` (synthesize arbitrary const, delete
  narrowing fixpoint), `cc.py` const emission.
- Gate: `ldiPending`/`LDI_FETCH` grep-clean + full suite + formal
  (LDI TCs replaced by LDIH TCs).

### V3.4 PC-relative CALL/JMP + unified immediate forms
Why: `CALL Rlink,Rtarget` needs a preceding `LDI` (2–3 words) per call;
`ADDI`(3-op) vs `ANDI`(2-op in-place) forces assembler copy-expansion.
`CALLR Rlink,+off9` in full 9-bit payload; pick one immediate form
(recommend in-place + `MOV`, smaller decoder).
- Files: `Decoder.scala`, both cores, `asm.py` (delete 3-op expansion),
  `cc.py` call sequences + mask emission.
- Gate: `call_deep.c` + `call_test.asm` pass with fewer words
  (assert word count in test).

### V3.5 Clean ALU opcode map (do last — touches every instruction)
Why: `aluFunc = opc<4 ? opc>>1 : opc-2` (`Decoder.scala:184-185`) is a
historical artifact; renumber ALU ops 0–7 so `aluFunc = op[2:0]`,
single-bit imm select. Only after V3.2 frees opcodes.
- Files: `Decoder.scala`, `ALU.scala` (unchanged codes, new derivation),
  `asm.py` OPCODES, `cc.py` emitters, every example recompiled.
- Gate: full suite + Decoder TC-DEC-6/11 rewritten as identity checks.

### V3.6 Byte-offset widening (if still needed after V3.1–V3.4)
`LDB/STB off3` 0..7 → 6-bit field in space freed by V3.2. Only if
profiler (`bench.py`) shows materialized byte addresses in hot loops.

Phase-1 exit gate: all V3.x implemented + `ISA-3.0.md` normative-complete
+ `sbt test` 34/34 + `test-programs-all` 49/49 both emus + coverage audit
extends to new encodings + F4PGA flow still completes (timing re-baselined,
not yet optimized).

---

## Phase 2 — Maintainability (after ISA is stable)

1. **Shared ISA helpers** (`risc.IsaUtils`): sext/zext, `laneSel`,
   rs/rt-addr mux, `Opcodes`/`CmdCodes` enums. Delete `rsAddrOf/rtAddrOf`
   (`PipCore.scala:58-76`), merge `Core.scala:100-114` /
   `PipCore.scala:144-152` / `Decoder.scala` field equations into one
   source; assembler/compiler import the same table (single source of
   truth with `asm.py`/`cc.py`).
2. **Pipeline bundles**: `EX_Reg`/`ID_Reg` structs replace 12 loose
   `rEX_*` regs (`PipCore.scala:81-109`); `InstrType`/`LdPhase` move to
   `Types.scala`. Flush/stall/halt touch one object.
3. **Explicit next-state**: replace source-order-dependent
   EX→WB-before-ID→EX (`PipCore.scala:583`) + `rWbHasExRes`/`stFired`
   feedback protocols with `next_*` wires + single register assignment.
4. **Extract `DebugBridge`** (cmd `0x03/04/05/06/08` handling duplicated in
   `Core.scala:163-177`, `PipCore.scala:752-766`) and `LoadStoreUnit`
   (LD FSM + `ldIssueStall` + byte-lane logic).
5. **Move inline formal** (`Decoder.scala:194-331`,
   `PipCore.scala:788-1202`, ~40% of files) into `src/test` suites;
   keep only port-level contracts at the component.
6. **Docs**: refresh or delete stale `ANALYZE.md` (documents removed
   `stallBrId`, fixed Bugs #1/#2 — actively misleading); `AGENTS.md`
   per-phase status entries.

Phase-2 exit gate: zero duplicated decode equations (grep), no behavior
change (`test-programs-all` 49/49 byte-identical `.hex` before/after).

---

## Phase 3 — Speed (on stable, clean v3)

Fmax (re-baseline after v3; was 103.83 MHz):
- Register `dataBus.req` (skid) — cuts SoC `isIoAddr` + RAM-addr fanout.
- `instrRsp.ready` decoupled from `stallID` cone (ready=True + discard,
  already proven for `exBrTaken`).
- Register `ldWbFiring` into regfile enable path.
IPC (profile with `bench.py` first):
- Capture `ldRd/ldIsByte/ldAddr0` in ID→EX transfer → delete
  `ldIssueStall` 1-bubble-per-load (`PipCore.scala:369-370`).
- 1-deep store buffer → ST to full UART no longer stalls IF/ID/EX.
- Narrow EX→ID forward to pre-compute consumers only
  (`idEffAddr/idBrTaken/idJmpTarget`) instead of full-operand latch.
- 1-bit BTB for loop back-edges (compiler output is loop-heavy).
- Revisit `Core.scala` role: debug/golden model only, not a perf path.

Phase-3 exit gate: `bench-all` cycles/instr + nextpnr Fmax reported per
change; no change accepted that regresses either without a recorded reason.
