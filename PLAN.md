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
3. **Explicit next-state (DONE)**: all 9 multi-driver regs
   (`vID/rEX_type/rEX_brTaken/vWB/halted/stFired/ldWbVld/ldiPending/pc`)
   via hold-default `next*` wires + file-order override whens, committed
   once in "State update". Priority == old elaboration order (verified
   bit-identical by BMC30, not by inspection). Stale MUST-come-BEFORE/
   AFTER ordering comments corrected (reads were always pre-edge).
   Single-driver regs untouched. Gate: 35/35 ✓, 50/50 ✓, zero
   `examples/` diff ✓.
4. **Debug read-path shared (DONE); LoadStoreUnit CLOSED as no-go.**
   `DebugReads.attach` (`BusInterface.scala`) owns aux-mux + `0x06`/`0x08`
   + rsp defaults; flush commands stay per-core (actions differ).
   LoadStoreUnit NOT extracted: the LD FSM is inseparable from the
   single-Stream dataBus master (EX issue), WB writeback, and 5 forwarding/
   stall consumers — a component boundary would be wider than the logic
   and split formally-pinned protocol. Its Phase-3 changes (ldIssue
   capture, store buffer) reshape it anyway — extract-if-needed there.
5. **Inline formal structure — CLOSED, no change.** Evaluated: the
   `*FormalTest.scala` runners only invoke `GenerationFlags.formal` blocks
   that MUST live inside the components (they reference private pipeline
   signals; SpinalHDL has no out-of-component access). Inline formal with
   TC headers is the correct pattern for this toolchain, not a smell.
6. **Docs** (DONE): deleted stale `ANALYZE.md` (documented removed
   `stallBrId`, deleted EX-ALU→ID forwarding, pre-v3 ISA — actively
   misleading, unmaintained). Single source of truth: `ISA-3.0.md` +
   code comments + this plan.

Phase-2 exit gate: zero duplicated decode equations (grep: single
`rsAddrOf`/`rtAddrOf`/`brTaken`/`laneSel` defs in `Isa.scala`) ✓, no
behavior change (50/50 byte-identical `.hex`) ✓. PHASE 2 CLOSED
(deferred items recorded with rationale: bundles, LoadStoreUnit split).

---

## Phase 3 — Speed (on stable, clean v3)

Baseline (v3.3 + Phase 2, `bench.py` cycles-to-completion):
| bench | Multi-cycle | PipSoc | note |
|:--|--:|--:|:--|
| nop (1000 ADDI iters) | 41,080 † | ≥26,048 † | pipeline WINS ~1.6×: overlap hides the per-iter LD latency the multi-cycle core pays serially |
| alu (500 iters, incl. 500 `__mul16`) | 290,583 | 181,554 | call + runtime dominated |
| mem (100-elem LD/ST) | 68,972 | 43,011 | 1.6× on streaming memory |
† nop corrections: the originally recorded numbers (multi 18,667 /
PipSoc 27,977, and the post-snapshot 21,014) were **artifacts** of
bench.py's R1-only end predicate — bench_nop reloads `0x3E8` into R1
every loop iteration, so "first step where R1==expected" oscillates and
the binary search converged to a mid-loop transition.  Ground truth
re-measured with the fixed predicate (see "bench.py monotone end
predicate" below): multi 41,080; PipSoc post-snapshot RTL 26,048
(pre-snapshot truth not recoverable without re-running old RTL).
alu/mem numbers were validated (R1-only within ≤46 cycles of truth), so
all recorded alu/mem deltas stand.
Fmax baseline (post-route): **106.19 MHz PASS** (Phase-1 exit).
Measurement fixes this pass: `bench.py` never passed the hex file
(measured empty ROM), stale multi-cycle path, stale `bench_alu`
expectation (`0x1428`, ground truth `0x2E0B` via python model),
`max_steps` 200k→600k (multi-cycle `bench_alu` needs ~290k).

Fmax (Phase-3 outcome — recorded regression with reason, per the exit
gate; snapshot+bypass trades depth for IPC):

| stage | post-route Fmax |
|:--|--:|
| Phase-1 exit (v3.3 + Phase 2) | **106.19 MHz PASS** |
| after snapshot + bypass (bus cone) | 86.63 FAIL |
| + `rBusAddr`/`rBusByte` bus-issue regs | 90.13 FAIL |
| + `bypassTake` isIoAddr-term removal | (cone cleared) |
| + boolean `isIoAddr` rewrite | **87.57 FAIL** (default seed) |
| + dual write ports (RegFile port-2 write mux) | **101.38 MHz PASS** (new netlist placement — the ~88-90 ceiling was netlist-specific) |

- Tool-level sweeps are exhausted: seeds 2-5 on the pre-boolean netlist
  79.08-91.31; `--placer sa` crashes in nextpnr-xilinx (exit 255);
  `--placer-budgets` 83.27; `--placer-heap-timingweight` 10/100 =
  88.62/87.57.  ~88-90 MHz is the structural ceiling of this netlist.
- Critical path (twin ~11.3 ns cones, routing-dominated ~8 ns):
  `rID_rsAddr → regfile/forwarding → idEffAddr CARRY4 → (idEffAddr[15:1]
  == stTrackAddr[15:1]) → { CE of snapRd/snapAddr0 (when l833),
  ldState/ldWbVld next-state (bypassTake) }`.  The bypass word compare
  (2 LUT levels + routing after the adder) is exactly the delta vs
  Phase 1.
- Why it is not fixable without an IPC loss (analysed this pass):
  the bypass MUST decide atomically at ID→EX transfer (the tracker can
  change in the transfer cycle via an ST fire; stStall's one-cycle
  shadow is what makes the pre-edge tracker read committed); a
  fire-time (SNAPSHOT) decision saves nothing because the async RAM
  responds in the SNAPSHOT cycle already — the bypass's entire value is
  skipping that cycle; comparing `rs == tracked - imm - c0` only
  re-serialises the same carry depth through the `c0 = rs[0]&imm[0]`
  term; narrowing the compare to the 12-bit RAM word index does not
  reduce LUT levels (both 2) and lengthens the carry to bit 12.

### Change: RegFile dual write ports (correctness fix found by profiling)

Profiling (per "profile with bench.py first") showed `bench_nop` is
compiler-bound: 13 instructions / 9 memory ops per loop iteration for
two scalars — every operand handoff round-trips through the memory
stack (`ST R1,[R7]; ADDI R7,#-2; …; LD R2,[R7]`).  The first cc.py fix
(MOV the left operand to R2 instead of the push/pop when the right side
is R1-pure) failed **4 PipSoc tests** (multest/gcd/prime_cnt/
mod_simple2) while multi-cycle passed all 51 — an RTL latent bug:

- Root cause: `ldWbFiring` (LD direct regfile write) and `vWB`
  (WB-stage ALU/CALL write) shared ONE write port with a priority mux
  (`wrAddr = Mux(ldWb, ldRd, rWB_rd)`) — same cycle, different rds ⇒
  the WB write was **silently dropped** (PipSoc trace: `wbf=1 wad=1`
  next to `vwb=1` → the `MOV R2,R1` result never landed, `__mul16`
  computed with dividend 0).
- Reachability: a store-to-load bypass LD enters DATA_READY/writeback
  at transfer+1 while its EX predecessor sits in WB at exactly that
  cycle.  The old codegen shielded this for years: LDs were always
  preceded by `ADDI R7, …` (exIdFwdHazard stalls the LD one cycle → WB
  bubble) or by stores (no writeback).  `LD R1; MOV R2,R1; LD R1`
  has no hazard between MOV and the second LD → collision.  A delayed
  bus/UART response can likewise complete next to an unrelated ALU
  writeback.
- Fix: RegFile gets a second write port (8×16 FFs — registers, no
  BRAM).  PipCore port 1 keeps the byte-identical original muxed
  equation (all existing formal properties unchanged); port 2 carries
  the WB write only when port 1 is occupied by the LD writeback
  (`ldWbTerm && wbWrTerm`); same-address conflicts still resolve to
  port 1 (unchanged WAW semantics: the bypassing LD is the younger
  instruction).  Multi-cycle Core ties port 2 off (WRITEBACK state
  serialises writes).
- Formal: RegFile TC-RF-3b (port-2 readback under concurrent writes —
  the machine-check that no write is dropped; free inputs make it
  exhaustive), TC-RF-7 (same-address priority), `cover(wrEn && wr2En)`;
  PipCore asserts pin the concurrent LD+WB wiring.

Gates: `sbt test` 35/35; 51/51 on both emulators; coverage clean;
bench cycle-neutral (18667/21014, 290583/174012, 68972/41504 —
identical to baseline); F4PGA Fmax **101.38 MHz PASS at 100 MHz**
(default seed; prior 103.83 — −2.45 MHz for the port-2 write mux,
still clears the 100 MHz gate).
- `isIoAddr` rewritten as `(addr[15:13] =/= 0) || &addr[12:2]` (pure
  boolean, exhaustively equivalent over all 65,536 addresses and
  formally asserted in both SoCs' TC `isIoAddr === (addr >= 0x1FFC)`):
  removes the LUT+CARRY4 `>=` compare from the SoC response cone; the
  rewrite is Fmax-neutral overall (the ID cone became worst instead).
- Remaining PLAN levers do not address the critical cone: the `dataBus.req`
  skid + `instrRsp.ready` decoupling cut the SoC-response cone, and
  `ldWbFiring` registration cuts the regfile-WE cone — all deferred.
- Recorded reason for the 106 → ~88 MHz regression (exit gate):
  bench gains nop 27,977→21,014 (−25%), alu 181,554→174,012 (−4%),
  mem 43,011→41,504 (−3.5%).  [Corrected later: the nop figure was a
  method artifact — true post-snapshot nop is 26,048; the alu/mem
  gains stand.]
IPC (profile with `bench.py` first):
- Capture `ldRd/ldIsByte/ldAddr0` in ID→EX transfer → delete
  `ldIssueStall` 1-bubble-per-load (`PipCore.scala:369-370`).
  **DONE** (`LdPhase.SNAPSHOT`, snapshot regs `snapRd/snapByte/
  snapAddr0/snapAddr`; bus is LD-unit-owned — request valid iff
  `snapPending && !ldRspPending`).  Bench (async RAM): nop
  27,977→**21,014**, alu 181,554→**174,012**, mem 43,011→**41,504**
  (nop figure later corrected: R1-only predicate artifact, true
  post-snapshot nop 26,048).
- **DONE** store-to-load bypass: `{stTrackVld,stTrackAddr,stTrackData}`
  updated at ST fire (STB/I-O invalidate), word LD to the tracked word
  completes at ID→EX into DATA_READY without the bus
  (`examples/bypass_test.asm`, formal tracker/bypass asserts).
- 1-deep store buffer → ST to full UART no longer stalls IF/ID/EX.
- Narrow EX→ID forward to pre-compute consumers only
  (`idEffAddr/idBrTaken/idJmpTarget`) instead of full-operand latch.
- 1-bit BTB for loop back-edges (compiler output is loop-heavy).
- Revisit `Core.scala` role: debug/golden model only, not a perf path.

Known limitation (sync data RAM): with a 1-cycle-late (readSync)
response the LD writeback lands after later instructions have retired —
a **WAW inversion** (the older LD's late write clobbers a younger
register write, since port 1 = ldWb wins same-address conflicts).  The
separate drop observed in the same experiment (LD writeback concurrent
with the WB write → ALU/CALL write lost, e.g. `ADDI R7` frame updates)
is FIXED by the dual-port RegFile above.  Supporting sync RAM (BRAM)
still needs in-order writeback (e.g. stall ID→EX while a load is
outstanding, or route load data through the WB port).

Phase-3 exit gate: `bench-all` cycles/instr + nextpnr Fmax reported per
change; no change accepted that regresses either without a recorded reason.

### Change: bench.py monotone end-of-benchmark predicate (measurement fix)

`bench.py` binary-searched "first step where R1 == expected".  bench_nop's
loop reloads the expected value **every iteration** (`LDI R1, #0x3E8`), so
the predicate oscillates and the search converged to a mid-loop
transition: the recorded nop baselines (multi 18,667 / PipSoc 27,977,
post-snapshot 21,014) were artifacts.  Ground truth re-measured with the
fixed predicate (all 8 registers equal the final register file —
monotone because the `_exit: JMP R5` spin loop never changes registers
and mid-loop R7/R5 differ from the restored epilogue state), old cc.py:

| bench | Multi-cycle | PipSoc (post-snapshot RTL) |
|:--|--:|--:|
| nop | **41,080** (was recorded 18,667) | **26,048** (was recorded 21,014) |
| alu | 290,596 (recorded 290,583 ✓) | 174,058 (recorded 174,012 ✓) |
| mem | 68,988 (recorded 68,972 ✓) | 41,511 (recorded 41,504 ✓) |

Fix: `measure_cycles` binary-searches "whole register file == final
state" (final state read at `max_steps`; expected-value check unchanged).
alu/mem never collided (their expected values only occur at the end), so
all previously recorded alu/mem numbers and deltas stand — R1-only was
within ≤46 cycles of truth there.  Also corrected: the baseline note
"pipeline LOSES on nop" was an artifact of the broken metric — the
pipeline actually WINS ~1.6× on nop (41,080 → 26,048).  No RTL change.

Gates: reproduces on both emulators; `bench.py --list` unchanged.

### Change: cc.py MOV handoff for R1-pure right operands

When the right side of a binop/condition/compound assignment writes only
R1 (constant, VarRef, sizeof, cast, pure unop), the left operand moves
to R2 with a single `MOV R2, R1` instead of the 4-instruction /
2-memory-op stack round-trip (`ST R1,[R7]; ADDI R7,#-2; …; ADDI R7,#2;
LD R2,[R7]`).  Register state at the operation is byte-identical to the
old pop.

- First attempt FAILED 4 PipSoc tests (multest/gcd/prime_cnt/mod_simple2)
  while multi-cycle passed → uncovered the latent shared-write-port
  collision fixed by the dual-port RegFile above (the new codegen's
  `LD R1; MOV R2,R1; LD R1` sequence reaches the LD/WB same-cycle
  writeback collision that the old push/pop codegen never formed).
- Bench with the fixed predicate (cycles-to-completion, old → new):

| bench | Multi-cycle | Δ | PipSoc | Δ |
|:--|--:|--:|--:|--:|
| nop | 41,080 → 33,071 | **−19.5%** | 26,048 → 21,042 | **−19.2%** |
| alu | 290,596 → 258,581 | **−11.0%** | 174,058 → 154,048 | **−11.5%** |
| mem | 68,988 → 66,571 | −3.5% | 41,511 → 40,000 | −3.6% |

Gates: 51/51 on both emulators (incl. the 4 formerly failing), coverage
clean, no RTL change. Fmax re-measured end-to-end with the regenerated
(new-codegen) `fib_uart.hex`: **101.38 MHz PASS at 100 MHz** — identical
to the dual-port measurement (ROM-content noise only).

### Change: structural 100 MHz assurance — register-re-sourced critical cones

Fmax on the Phase-4b netlist was seed-lottery: the default nextpnr
seed was deadlock-class (~88–90 FAIL@100) and even the best seed needed
a sweep to find (101.81, seed 22; typical seeds 83.7–99.1).
Dissecting every failing timing path showed the same pattern —
**freshly-decoded instruction fields and the SoC's request-ready
compute combinationally feeding handshake/CE paths**, stacking 4–6 LUT
levels of decode + SoC muxes on top of register-to-register logic.
Three fixes re-source those inputs from registers (CPI cost measured
and accepted per the Phase-3 exit gate):

1. **EX-side store-to-load bypass** (was ID-side): the store-tracker
   snapshot is taken unconditionally at the ID→EX transfer, and the
   bypass decision (`exBypassTake`) is made one cycle later in EX from
   registers (`rEX_effAddr`, `stTrack*`) — provably the same operands.
   Completion moves +1 cycle; the stray 1-cycle RAM read response is
   dropped in DATA_READY (`rsp.ready` low there), no drain FSM.
2. **Latched stall-flag bank**: a second `Decoder` decodes the same
   incoming word (`incWord` = LDI-8 header or payload — fixed point of
   the wide-immediate scheme), and 7 flags (`isLd/isSt/isLdi/usesRs/
   usesRt/needsRs/needsRt`) are latched with `rID_instr` under the same
   fire guard.  Every stall-logic decoder read now starts from a
   register; formal equivalence asserts pin flags == live decode
   whenever `vID`.
3. **`snapStStall` register-only**: dropped `&& !io.dataBus.req.fire`
   from the snapshot-vs-ST serialization stall — that term pulled the
   SoC ready-compute (`rBusAddr → isIoAddr → UART-tx mux → req.fire`)
   into `stallID → instrRsp.fire → ID-register enable` (the seed-6
   98.98 tail).  Blocking ST entry for the whole held-snapshot window
   is a strictly stronger serialization (superset of the unfired-window
   property, machine-checked in both same-cycle and temporal form) and
   leaves `stallID` register-sourced end-to-end.

Bench trade (PipSoc; multi-cycle bit-identical at
33,071/258,581/66,571): the two +1-cycle mechanisms cost
21,042 → 22,043 / 154,048 → 156,049 / 40,000 → 40,705 cycles on
nop/alu/mem (+4.8% / +1.3% / +1.8%).  At the worst passing Fmax
(106.09) every benchmark is still **faster in wall clock** than on the
old best-seed 101.81 netlist, and the Fmax floor rose from
"83.7–99.1 typical with a deadlocking default seed" to **106.09–112.65
MHz across 8/8 sampled seeds (default seed 106.62)** — 100 MHz
assured, not seed-lottered.  Also added: `make f4pga-pipsoc
F4PGA_SEED=<n>` (nextpnr is deterministic per json+seed, so every seed
result is reproducible).  Note: formal properties are stripped from the
generated Verilog (zero `assert` statements in `PipSoc.sv`), so the
swept netlist is exactly the committed tree's hardware.

Gates: 52/52 both emulators after each fix, coverage clean, bench
numbers above, `make f4pga-pipsoc` default-seed end-to-end PASS
(106.62 MHz post-route → frames → bitstream), formal asserts rewritten
(EX-side convert, flag equivalence, snapStStall same-cycle +
temporal); full `sbt test` pending (running).

## Phase 4 — Interrupts (v3.4 platform model, zero-ISA) [DONE]

Scope decision: **interrupts only** (DMA declined).  ISA impact: none —
this is the v3.4 platform-level addition specified in ISA-3.md §10.4;
entry/return/masking are platform mechanisms built on existing
instructions.  (Note: the *proposed* v3.4 of Phase 1 was closed as
wontfix and never shipped; version v3.4 is first used here.)

### Design (zero-ISA interrupt model)

- Vector `0x0008`, driven into the core as `io.irqVector` (platform
  constant in both SoCs).
- Level-sensitive request = (UART RX non-empty || SWIRQ) && IMASK —
  all three live in the SoC I/O decode, not in the core.
- MMIO: `0xFFF8` CTRL (b0 IMASK R/W, b1 SWIRQ R/W, b2 raw pending on
  read), `0xFFFA` EPC read window.  Decoded exactly (top-of-page bytes;
  a byte access reaches the same registers via the word decode); every
  other address in the I/O rows keeps the UART alias, so the map is
  disjoint (ISA-3 §10.1).
- Core ports (both cores, via the shared `CoreIo`): `irq`/`irqVector`
  in, `irqAccepted` (1-cycle pulse) / `irqLink` (EPC value) out.  The
  SoC latches `irqLink` as EPC and clears IMASK on `irqAccepted`
  (auto-DI; the entry block is placed after the CTRL-write block, so a
  same-cycle software write cannot override the mask drop).
- Multi-cycle Core: accept in FETCH only — PC already holds the next
  unexecuted instruction, all older work has retired, no bus transaction
  is outstanding; a coincident debug-bus command defers entry one cycle
  (RESET/STEP/RUN keep their semantics).  HALT/IDLE never accept
  (§6.6: HALT exits by reset only).
- PipCore: accept = `irqTake = irq && vID && !stallID && !rEX_brTaken &&
  !halted && !dbgFlush`.  The ID instruction is the next unexecuted one:
  it is squashed (EPC = `rID_pc`) and EX is emptied exactly as the normal
  vID=0 otherwise-branch does — no stale occupant, hence no double
  EX→WB commit and no re-issued store (`req.valid = ST && !stFired`).
  IF redirects to the vector; because the instruction ROM is async, the
  cycle after the redirect already returns the vector word (no stale
  in-flight fetch response exists — same reasoning as the exBrTaken
  discard).  An in-flight LD is deliberately left behind: its FSM,
  request and writeback are snapshot/ldState-driven (rEX_type-
  independent) and single-outstanding is enforced ID-side
  (ldBusyStall/snapLdStall block a second request), so the handler
  cannot collide with it.
- Interrupt controller (byte-identical in Soc.scala and PipSoc.scala):
  registered CTRL/EPC read response (1-cycle, the same latency as Soc's
  sync RAM; PipCore consumes it in ldState WAIT_BUS like the UART-read
  path); int-register writes never reach the UART (txVld, txReady and
  the UART read FSM are all gated by `!isIntReg`); the response mux
  gains a third source with priority RAM > UART > int.

### Verification

- Formal: Core TC-IRQ-C1..C3 (accept only in FETCH; link = PC; redirect
  to the vector; the FETCH-flow assert is exempted for the accept
  cycle).  PipCore TC-IRQ-P1..P3 (ID squashed after accept, link =
  rID_pc, pc = vector) plus `!past(irqTake)` exemptions on the eight
  ID→EX-transfer/PC-progression properties and `cover(irqTake)`.
  SoC + PipSoc TC-IRQ-S1..S7 (UART isolation of int accesses, EPC
  latch, auto-DI, mask gating of the core request, CTRL write semantics,
  int-read response timing, decode) plus `cover(core.io.irqAccepted)`.
- End-to-end: `examples/irq_test.asm` on both emulators — vector layout
  at 0x0008, SWIRQ trigger, EPC return precision (main resumes inside
  its spin loop and completes), hardware auto-DI, software ack, and the
  registered EPC read path on both response mechanisms (sync RAM timing
  on Soc, delayed WAIT_BUS response on PipSoc).

### Gates

- Formal suites (the long ones run individually first): Core BMC(30)
  PASS (1m42s), PipCore BMC(30) PASS (37m24s), Soc BMC(10) PASS,
  PipSoc BMC(30) PASS (1h20m) — no counterexamples on any of the new
  TC-IRQ-* properties or the `!past(irqTake)`-exempted transfer
  properties; `cover(irqTake)` reachable.  Full `sbt test` re-run
  pending as the final gate.
- `run_tests.py`: **52/52 on both emulators** — `irq_test` returns
  R1 = 0x5A on the multi-cycle Soc and on PipSoc (vector entry, EPC
  precision through the mid-loop resume, auto-DI, software ack, and the
  registered EPC read on both response mechanisms).
- `isa_coverage.py`: 52 tests; hardware missing=none, pseudo missing=none.
- `bench.py`: **cycle-identical to the Phase-3 baseline** (nop 33,071 /
  21,042; alu 258,581 / 154,048; mem 66,571 / 40,000; multi / PipSoc) —
  the interrupt logic is cycle-neutral, as designed (off the execute
  path; imask resets to 0).
- Fmax re-measure (`make f4pga-pipsoc`, end-to-end through bitstream):
  **103.70 MHz PASS at 100 MHz** post-route (mid-route estimate 89.56;
  previous Phase-3 measurement 101.38 — the new I/O decode compare in
  the ready cone costs nothing measurable, the delta is placement/ROM
  noise).  `f4pga/build/PipSoc.bit` regenerated.
- Full `sbt test`: pending (background re-run of every suite).

### Phase 4b — memory map: full RAM, I/O + interrupt at the top (user request)

Requested after the disjoint-table fix: enlarge the data RAM and move
I/O and the interrupt registers to the end of the address space; if the
enlargement does not fit the Basys3, keep RAM at `0x1FFF` + UART +
interrupt at the top (the accepted fallback — the async-read data RAM
is LUTRAM on FPGA, so growth is placement-risky; cf. item 24).

- New map (ISA-3 §10.1, disjoint rows): RAM `0x0000..0x1FFF` (all 8 KB
  usable — the old 4-byte I/O shadow at the top is gone), unmapped hole
  `0x2000..0xFEFF` (reads return 0 via the registered responder, writes
  dropped — deterministic for every address pattern, design goal 2),
  I/O alias rows `0xFF00..0xFFF7` and `0xFFFC..0xFFFF` (the same
  single UART; conventional `0xFFFC` RX / `0xFFFE` TX — the old
  FC/FE mnemonics survive as page FC), interrupt CTRL `0xFFF8` / EPC
  `0xFFFA` at the top of the I/O page.
- Decode: `isRam = addr[15:13]==0`, `isIoAddr = addr[15:8]==0xFF`
  (pure boolean one-level compares — cheaper than the old
  `>= 0x1FFC` form in the req.ready cone), int regs =
  `isIoAddr && addr[7:2]==0x3E` with `addr[1]` selecting CTRL/EPC;
  every path previously gated on `!isIoAddr` is now gated on `isRam`;
  dead reads share the interrupt responder (payload 0).
- Toolchain: cc.py initial SP `0x1FFA` → `0x1FFE` (top word of the full
  RAM — the C prologue stores `R5` at `[R7]` BEFORE the first
  decrement, so SP itself must be a valid RAM word; the suite caught
  `0x2000` immediately: first push dropped into the dead hole, `RET`
  jumped to 0); UART examples retargeted (`fib_uart.c/.asm`,
  `soc_test.c/.asm`, `echo.asm`); `irq_test` uses `0xFFF8`/`0xFFFA`.
- Formal: TC-SOC-2 equivalence rewritten (RAM/I/O decodes + region
  disjointness), TC-IRQ-S7 decode at the new addresses, new
  TC-IRQ-S8 (dead-space reads → registered 0; writes → no RAM write,
  no UART byte, interrupt state unchanged) in both SoCs.
- Gates: 52/52 both emulators; `isa_coverage.py` clean; bench
  cycle-identical; SoC/PipSoc formal + Fmax + full `sbt test` pending.
