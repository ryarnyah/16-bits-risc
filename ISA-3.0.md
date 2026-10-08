# 16-bit RISC ISA Specification — Version 3.0 (DRAFT)

v3 is a **breaking** revision of v2.1 (`ISA-2.1.md`, frozen). Binaries do
not carry across; everything recompiles from source. Each step below is
normative only once its status reads IMPLEMENTED (process in `PLAN.md`).

## V3.1 Word-scaled LD/ST offsets — IMPLEMENTED

Replaces v2.1 §2.3 for **word** accesses (`LD`/`ST` only):

- `LD Rd,[Rs+off]` / `ST Rd,[Rs+off]`: effective address
  `= R[Rs] + sext(off6)*2`. `off` is in **words**, range −32..+31 words
  (−64..+62 bytes). All 64 encodings are distinct — the v2.1 odd-address
  aliasing (`bit 0 ignored`, half the encodings wasted) is gone.
- Odd effective addresses are impossible by construction (base is even in
  practice; `off*2` is always even), so no aliasing rule is needed.
- Unchanged: `LDB`/`STB` (`Rs + zext(off3)`, bytes, any address),
  `ADDI`/SP arithmetic (bytes), branch offsets (instructions), fetch
  (`PC[15:1]`), endianness, reset, reserved=NOP.

Assembler syntax v3.1: `LD Rd, [Rs + off]` with `off` in words,
`-32..+31` (assembler warns outside range, encodes low 6 bits as before).

## V3.2 Unified branch opcode — IMPLEMENTED

Replaces v2.1 opcodes `0xC/0xD/0xE` (`BEQ`/`BNE`/`BLT`):

- `BR cc, Rs, off`: `1100 | Rs | cc | off7`. `Rs` = instr[11:9] (sole
  register operand), `cc` = instr[8:7], `off7` = instr[6:0] signed —
  offset in **words**, range −64..+63, added to the already-incremented
  PC (unchanged rule).
- `cc`: `Z` (00, Rs==0), `NZ` (01), `MI` (10, signed Rs<0 — sign bit),
  `PL` (11, signed Rs>=0). MI/PL need no comparator, only bit 15.
- Opcodes `0xD`, `0xE` are **reserved → NOP** (freed for V3.4).
- No Rt field, no second compare register. Register-register relations
  lower to `SLT/SLTU/SUB` + `BR` (2 words — the same cost v2.1 already
  paid for `BGE/BLE/BLTU/BGEU/BLEU`, now uniform for all six).
- Far targets (±64 words exceeded): the assembler emits the exact-inverse
  far form `BR invcc, Rs, +3 ; LDI R4, #target ; JMP R4` (4 words).
  All four conditions invert exactly (Z↔NZ, MI↔PL), so the v2.1 relaxed-
  BLT equality bug class cannot occur. Only 3 sites in machine-generated
  code need it (large loop bodies in collatz/bench_alu); everything else
  is 1 word.
- Pseudos: `B L` = `BR Z, R0, L`; `BGT` = `SLT+BR NZ`;
  `BGE/BLE` = `SLT+BR Z`; `BLTU` = `SLTU+BR NZ`;
  `BGEU/BLEU` = `SLTU+BR Z` (scratch R4 default, `.scratch` overridable).

## V3.3 Single-word constants — CLOSED AS IMPOSSIBLE (see `PLAN.md`)

Group F has 9 bits after `op+Rd`; `LDI8` spends 1 (marker) + 8 (imm). Any
sibling needs `b8=0,mf=11`, leaving only `payload[5:0]` = 6 bits — a
byte-granular high half cannot be encoded, and 3+-word syntheses lose to
2-word `LDI` on size and cycles. `LDI` stays.

## V3.4 PC-relative control — IMPLEMENTED AS V3.3 (renumbered)

### V3.3 `CALLR` / `JMPR` (opcodes `0xD`/`0xE`, fully legal, no remnants) — IMPLEMENTED

| Mnemonic | Encoding | Operation |
|:--|:--|:--|
| `CALLR Rlink, off9` | `1101 Rlink off9` | `R[Rlink] = PC_next`; `PC = PC_next + sext(off9)*2` |
| `JMPR off12` | `1110 off12` | `PC = PC_next + sext(off12)*2` |

- `off9` = instr[8:0] signed, words, ±256. `off12` = instr[11:0] signed,
  words, ±2048. Same already-incremented-PC rule as branches.
- Register `JMP Rs` / `CALL Rlink,Rtarget` unchanged.
- Assembler: `CALLR Rlink, label` (far → `LDI R4,#label ; CALL Rlink,R4`),
  `JMPR label` (far → `JMP label`), via the shared relaxation fixpoint.

## V3.4 Clean ALU opcode map — CLOSED AS WONTFIX (see `PLAN.md`)

## V3.5 Byte-offset widening — DEFERRED (no demander: `cc.py` emits no
byte ops; revisit with profiler evidence only)
