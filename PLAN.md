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

### V3.3 Single-word constants: `LDIH` + retire 2-word `LDI` — CLOSED AS IMPOSSIBLE
Bit budget proof: group F leaves 9 bits after `op+Rd`; `LDI8` spends
1 (marker) + 8 (imm). Any sibling needs `b8=0,mf=11` markers, leaving only
`payload[5:0]` = 6 bits. Byte-granular high/low synthesis in 2 words is
therefore unrepresentable; 3+-word syntheses lose to `LDI` on both size
*and* cycles, and a preserving `LDIH` would need a new EX read-merge
forwarding path (net complexity gain, not deletion). `LDI` stays;
revisit only if the format ever grows a byte.
- What V3.3 becomes instead: PC-relative control (old V3.4 core, now
  encodable in the freed `0xD/0xE`): `CALLR`/`JMPR` below. Dual immediate
  forms (`ADDI` 3-op vs group-B 2-op) stay deliberately — unifying saves
  ~10 assembler lines at the price of breaking every group-B user.

### V3.3 PC-relative control: `CALLR` / `JMPR` (in freed `0xD`/`0xE`) [DONE]
Why: every label call/jump currently pays `LDI` materialization (2 words)
plus the jump (1 word); runtime calls cost 5 words. Both new ops fit
*exactly* with no reserved remnants, delete most remaining `LDI` uses
(the fetch FSM stays, but goes rare), and shrink loop bodies (helps the
3 far-`BR` sites from V3.2).
- `CALLR Rlink, off9`: `1101 | Rlink | off9` (off9 = instr[8:0] signed,
  words, ±256). `R[Rlink] = PC_next`; `PC = PC_next + sext(off9)*2`.
  All 512 patterns legal. Assembler `CALLR Rlink, label`; far targets
  expand to `LDI R4,#label ; CALL Rlink,R4` (Rlink≠R4; cc.py uses R5).
- `JMPR off12`: `1110 | off12` (off12 = instr[11:0] signed, words, ±2048).
  `PC = PC_next + sext(off12)*2`. All 4096 patterns legal. Assembler
  `JMPR label`; far targets expand to `JMP label`.
- Register `JMP Rs` / `CALL Rlink,Rtarget` unchanged (RET, returns,
  computed targets).
- Files: `Decoder.scala` (isCALLR/isJMPR, hasRd+=CALLR, drop 0xD/0xE
  reserved clause), both cores (ID-resolved target rides `rEX_jmpTarget`,
  EX untouched), `asm.py` (encoders + relaxation entries + far
  expansions), `cc.py` (all label jumps → JMPR, runtime calls → CALLR),
  new `callr_test.asm`, `isa_coverage.py`, `run_tests.py` (+1 test).
- Gate: 50/50 both emus ✓, `sbt test` 35/35 ✓ (BMC30 incl. new
  CALLR/JMPR asserts), zero `LDI+JMP` sequences in fresh output ✓.

### V3.4 Clean ALU opcode map — CLOSED AS WONTFIX
`func = opc<4 ? opc>>1 : opc-2` is ~2 mux levels in a non-critical block
(EX consumes the latched `rEX_aluFunc`; the #21/#22 timing work never
touched decode). Renumbering re-breaks every binary, encoding, test, and
formal TC for zero functional gain. The map is full *and* coherent
post-V3.3; the derivation stays documented (§9.1) and formally pinned
(TC-DEC-6/11). Revisit only if decode ever lands on a critical path
(then: move ADDI/XORI to group B, `func = op[2:0]`).
- Files: none. Gate: none (decision record).

### V3.5 Byte-offset widening — DEFERRED (no demander)
`cc.py` emits no `LDB`/`STB` (C subset has no byte type); the ops are
asm-level only and their `off3` range is unpressured. Revisit with
profiler evidence only.

Phase-1 exit gate: all V3.x implemented/closed + `ISA-3.0.md`
normative-complete + `sbt test` 35/35 + `test-programs-all` 50/50 both
emus + coverage audit extends to new encodings ✓ + F4PGA flow completes:
`make f4pga-pipsoc` → valid bitstream (sync `0009 0ff0`, 2.19 MB),
post-route **106.19 MHz PASS at 100 MHz** (v2.1 baseline was 103.83 —
v3 ISA timing-neutral). PHASE 1 CLOSED.

---

## Phase 2 — Maintainability (after ISA is stable) [IN PROGRESS]

1. **Shared ISA helpers** (`risc.Isa`, DONE): `rsAddrOf`/`rtAddrOf`,
   `brTaken`, `sext6`/`zext6`, `laneSel` in one object; `PipCore`
   private copies + `Core` anonymous muxes deleted. `Decoder` keeps raw
   field extraction (correct split: classes vs raw fields). PipCore
   formal latch asserts kept as second net (they now verify the move was
   verbatim). Gate: `sbt test` 35/35 ✓, 50/50 both emus ✓, zero
   `examples/` diff (hex-identical) ✓.
2. **Pipeline bundles — DEFERRED (except enums, DONE).** Rationale:
   flush is already single-point (`rEX_type:=EMPTY`, `vID/vWB:=False`);
   the real fragilities are ordering + feedback protocols + the LD FSM's
   direct `rEX_type` write (tasks 3/4, which restructure rather than
   rename). A ~150-site bundle rename carries init-semantics risk for
   readability only — revisit post-Phase-3 if stages are added/removed.
   `InstrType`/`LdPhase` moved to `Types.scala` (same package, zero-risk).
3. **Explicit next-state**: replace source-order-dependent
   EX→WB-before-ID→EX (`PipCore.scala:583`) + `rWbHasExRes`/`stFired`
   feedback protocols with `next_*` wires + single register assignment.
4. **Extract `DebugBridge`** (cmd `0x03/04/05/06/08` handling duplicated in
   `Core.scala:163-177`, `PipCore.scala:752-766`) and `LoadStoreUnit`
   (LD FSM + `ldIssueStall` + byte-lane logic).
5. **Inline formal structure — CLOSED, no change.** Evaluated: the
   `*FormalTest.scala` runners only invoke `GenerationFlags.formal` blocks
   that MUST live inside the components (they reference private pipeline
   signals; SpinalHDL has no out-of-component access). Inline formal with
   TC headers is the correct pattern for this toolchain, not a smell.
6. **Docs** (DONE): deleted stale `ANALYZE.md` (documented removed
   `stallBrId`, deleted EX-ALU→ID forwarding, pre-v3 ISA — actively
   misleading, unmaintained). Single source of truth: `ISA-3.0.md` +
   code comments + this plan.

Phase-2 exit gate: zero duplicated decode equations (grep), no behavior
change (`test-programs-all` 50/50 byte-identical `.hex` before/after).

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
