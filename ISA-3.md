# ISA-3 — 16-bit RISC Microcontroller Instruction Set Architecture, Version 3

```
   Document:       ISA-3
   Version:        3.3 (final)
   Category:       Standards Track (internal)
   Status:         APPROVED — implemented and verified
   Obsoletes:      ISA-2.1.md (frozen, historical), ISA.md (v2.0, obsolete)
   Process:        PLAN.md (Phase 1 — breaking ISA changes)
   Date:           October 2026
```

## Status of This Memo

This document is the complete, self-contained specification of the version 3
instruction set architecture (ISA) of the 16-bit RISC microcontroller core.
It is a standards-track internal document: every requirement stated here is
enforced by the implementation (`src/main/scala/risc/`), the toolchain
(`asm.py`, `cc.py`), formal verification, and the regression suite. Where
this document and older specifications (`ISA.md`, `ISA-2.1.md`,
`ISA-3.0.md`) disagree, **this document takes precedence**; the older files
are frozen historical records and MUST NOT be consulted for v3 behaviour.

Version 3 is a **breaking** revision: v2.x binaries do not execute with v3
semantics and MUST be rebuilt from source. Distribution of this memo is
unlimited within the project.

## Copyright Notice

Zero IPR: every encoding defined herein was invented in this repository.
No third-party intellectual property is implicated by any part of the
encoding space.

## Abstract

ISA-3 defines a fixed 16-bit instruction word ISA with 8 general-purpose
registers, byte-addressable 64 KB little-endian memory, and a single
address space. It contains 29 architectural instructions plus a documented
`NOP`, covers arithmetic, logic, shifts, signed/unsigned comparison, word
and byte memory access, absolute and PC-relative control transfer,
one-word constants up to 255, two-word constants up to 65535, subroutine
calls with register link, and a `HALT` state. All unassigned encodings are
normatively defined as `NOP`, making the machine deterministic under
corrupted input. The ISA is implemented twice (multi-cycle `Core` and
3-stage `PipCore`) and verified by bounded model checking plus 51
regression programs on both implementations.

## Table of Contents

```
 1.  Requirements Language
 2.  Introduction
 3.  Specification Notation
 4.  Architectural Model
 5.  Instruction Encoding
 6.  Instruction Semantics
 7.  Control-Transfer Model
 8.  Assembler Specification
 9.  Encoding Space Accounting
10.  Platform Profile
11.  Programming Examples (informative)
12.  Implementation Notes (informative)
13.  Verification (informative)
14.  Security and Robustness Considerations
15.  IANA Considerations
16.  Change History
Appendix A.  Complete Encoding Tables
Appendix B.  Pseudo-Op Reference
Appendix C.  Relaxation and Layout Algorithm
Appendix D.  Migration from v2.1
Appendix E.  Reserved Space and Future Extensions
Appendix F.  Rationale
```

---

## 1. Requirements Language

The key words "MUST", "MUST NOT", "REQUIRED", "SHALL", "SHALL NOT",
"SHOULD", "SHOULD NOT", "RECOMMENDED", "MAY", and "OPTIONAL" in this
document are to be interpreted as described in RFC 2119.

## 2. Introduction

### 2.1 Purpose and Scope

ISA-3 specifies everything a conforming implementation, assembler, or
compiler must do, and nothing else. It covers the architectural state, the
bit-exact encoding of every instruction, the semantics of every
instruction, the behaviour of every unassigned encoding, and the
source-level contract of the assembler. Micro-architectural matters
(pipeline depth, bus protocol, timing) are out of scope; see
`INSTRUCTIONS.md` and Section 10 for the platform profile.

### 2.2 Relationship to Previous Versions

- v2.0 (`ISA.md`): the original 16-instruction set. Obsolete.
- v2.1 (`ISA-2.1.md`): a binary-compatible superset of v2.0 that filled
  padding fields with 13 new instructions (`SLT`, `SLTU`, `SRA`, `CALL`,
  `HALT`, `ANDI`, `ORI`, `SLLI`, `SRLI`, `SRAI`, `LDI8`, `LDB`, `STB`)
  and documented `NOP`, alignment, endianness, reset, and the
  reserved-is-NOP rule. Frozen.
- v3.x (this document): a breaking revision. All v2.1 instructions survive
  except the three register-register branches `BEQ`/`BNE`/`BLT`, which are
  replaced by one unified conditional branch `BR`; the freed opcodes
  `0xD`/`0xE` are reassigned to PC-relative `CALLR`/`JMPR`; and `LD`/`ST`
  offsets become word-scaled. See Appendix D.

### 2.3 Design Goals (informative)

1. Every common operation in one word.
2. Deterministic behaviour for *every* 16-bit pattern (reserved = NOP).
3. Decode-friendly field layout (opcodes grouped by control signals).
4. Exact condition inversion, so branch relaxation needs no special cases.
5. No state beyond registers, memory, and PC — no flags, no privilege
   levels, no interrupts (a future revision may add a vector model).

## 3. Specification Notation

### 3.1 Symbols

| Symbol | Meaning |
|:--|:--|
| `Rn` | general-purpose register `n` (0..7), 16 bits |
| `PC` | program counter, 16-bit byte address of the current instruction |
| `PC_next` | address of the next instruction: `PC + 2`, or `PC + 4` for the two-word `LDI` (Section 7.1) |
| `M8[A]` | 8-bit memory cell at byte address `A` |
| `Mword[A]` | 16-bit memory word at byte address `A` (little-endian, `A` even) |
| `sext_k(x)` | sign-extend bit pattern `x` of width `k` to 16 bits |
| `zext_k(x)` / `zext(x)` | zero-extend `x` to 16 bits |
| `x << n`, `x >> n` | logical shifts, `n` in 0..15 |
| `x >>> n` | arithmetic (sign-filling) right shift |
| `~x`, `x & y`, `x \| y`, `x ^ y` | bitwise operations |
| `+`, `-` | modulo-2^16 two's-complement arithmetic |
| `inst` | the 16-bit instruction word currently executing; `inst[i]` is bit `i` |

### 3.2 Bit Numbering and Fields

Bits are numbered 15 (most significant) to 0 (least significant). Field
ranges are written `hi..lo`, inclusive. A field extraction `inst[8:7]` is a
2-bit value.

### 3.3 Units

- **Byte offsets** are added directly to byte addresses.
- **Word offsets** are multiplied by 2 before being added.
- **Instruction offsets** ("words" in this document's control-transfer
  definitions) are multiplied by 2 and added to `PC_next`.
- Assembly source MAY use any mix of these; the unit is fixed per
  instruction class (Section 6) and MUST NOT be inferred from context.

## 4. Architectural Model

### 4.1 Registers

The machine has eight 16-bit general-purpose registers `R0`..`R7`.

- `R0` is hardwired to zero: reads return `0x0000`; writes are ignored
  (the instruction still completes normally, `PC` advances, memory is
  unaffected).
- `R1`..`R7` are ordinary read/write registers.
- There is no condition-code register and no status word. Instructions
  produce results, not flags.

### 4.2 Program Counter and Fetch

- Instructions live in the same byte-addressable space as data (von
  Neumann, single address space).
- The instruction executed at byte address `A` is `Mword[A & ~1]` —
  fetch ignores `PC[0]`. A control transfer to an odd address executes
  the word at `target & ~1`; software SHOULD use even targets.
- Every instruction advances `PC` by **2** bytes, except the two-word
  `LDI`, which advances by **4**. The second word of `LDI` is immediate
  payload and MUST NEVER be fetched as an instruction.

### 4.3 Memory Model

- The address space is 64 KB (`0x0000`..`0xFFFF`); all address arithmetic
  wraps modulo 2^16.
- Memory is **little-endian**: `Mword[A] = M8[A] | (M8[A+1] << 8)` for
  even `A`. Bits [7:0] reside at the even (lower) address.
- Byte accesses (`LDB`/`STB`) are legal at any address, aligned or not.
- Word accesses (`LD`/`ST`) operate on `Mword[EA & ~1]` where `EA` is the
  effective address of Section 6.3. Because the word offset is scaled by
  2, an odd `EA` can only arise from an odd base register; bit 0 of the
  base is then ignored (Section 4.4).
- Access to addresses not backed by RAM or I/O is platform-defined
  (Section 10). The ISA imposes no fault behaviour: there are no traps.

### 4.4 Alignment

- `LD`/`ST`: the effective address `EA = R[Rs] + 2*sext6(off)` has an
  always-even offset term, so `EA` is odd iff `R[Rs]` is odd, in which
  case bit 0 is dropped and the word at `EA-1` is accessed. This is
  deterministic; no alignment exceptions exist.
- `LDB`/`STB`, and all instruction fetches handled per Sections 4.2/4.3.

### 4.5 Reset

| State | Reset value |
|:--|:--|
| `PC` | `0x0000` |
| `R0` | `0x0000` (always) |
| `R1`..`R7` | `0x0000` |
| Memory | unchanged |

### 4.6 Execution Model

Execution is sequential and in-order. There is no out-of-order commit, no
speculative state visible to software, and no interrupts: `HALT` is exited
only by reset (Section 6.6). Implementations MAY pipeline internally
provided the architectural outcome is identical to sequential execution.

## 5. Instruction Encoding

### 5.1 Instruction Word Formats

All instructions are exactly one 16-bit word, except the two-word `LDI`
(word 1 as below, word 2 = raw 16-bit immediate).

```
Format R  (register ALU)      15..12 op   11..9 Rd   8..6 Rs   5..3 Rt   2..0 f3
Format I  (immediate ALU)     15..12 op   11..9 Rd   8..6 Rs   5..0 imm6
Format M  (word load/store)   15..12 op   11..9 Rd   8..6 Rs   5..0 off6
Format B  (group B)           15..12 1011 11..9 A    8..6 cf   5..0 payload
Format F  (group F)           15..12 1111 11..9 Rd   8 b8      7..6 mf   5..0 payload
Format BR (conditional br.)   15..12 1100 11..9 Rs   8..7 cc   6..0 off7
Format C  (PC-rel. call)      15..12 1101 11..9 Rlink 8..0 off9
Format J  (PC-rel. jump)      15..12 1110 11..0 off12
Format L  (LDI word 1)        15..12 1111 11..9 Rd   8 0       7..6 00   5..0 000000
```

### 5.2 Opcode Map

| Hex | Binary | Mnemonic(s) | Class | Format |
|:--:|:--|:--|:--|:--|
| 0 | `0000` | `ADD` (f3=000) | register ALU | R |
| 1 | `0001` | `ADDI` | immediate ALU | I |
| 2 | `0010` | `XOR` (f3=000) | register ALU | R |
| 3 | `0011` | `XORI` | immediate ALU | I |
| 4 | `0100` | `SUB` (f3=000), `SLT` (001), `SLTU` (010) | register ALU + bank A | R |
| 5 | `0101` | `AND` (f3=000) | register ALU | R |
| 6 | `0110` | `OR` (f3=000) | register ALU | R |
| 7 | `0111` | `SLL` (f3=000) | register ALU | R |
| 8 | `1000` | `SRL` (f3=000), `SRA` (f3=001) | register ALU + bank A | R |
| 9 | `1001` | `LD` | word load | M |
| A | `1010` | `ST` | word store | M |
| B | `1011` | `JMP`, `CALL`, `HALT`, `ANDI`, `ORI`, `SLLI`, `SRLI`, `SRAI` | group B | B |
| C | `1100` | `BR` | conditional branch | BR |
| D | `1101` | `CALLR` | PC-relative call | C |
| E | `1110` | `JMPR` | PC-relative jump | J |
| F | `1111` | `LDI`, `LDI8`, `LDB`, `STB` | group F | F/L |

Decode invariants (MUST hold in every implementation):

- The register-ALU group is `op <= 8` — NOT `op[3] == 0` (`SRL` is 0x8).
- Immediate mode is `imm_en = (op == 1) || (op == 3)` — NOT `op[0]`.
- ALU function is NOT `op[2:0]` (the correct derivation is in
  Section 12.1).

### 5.3 Bank A — funct3 Sub-opcodes (register ALU)

Within the register-ALU opcodes, `f3 = inst[2:0]` selects extensions; only
the following are defined:

```
op = 0x4 : f3 = 001 → SLT        op = 0x8 : f3 = 001 → SRA
           f3 = 010 → SLTU
```

`f3 = 000` selects the base operation. Every other `op`/`f3` combination
is reserved (Section 5.6).

### 5.4 Bank B — Control and Two-Operand Immediates (opcode 0xB)

```
15..12 = 1011 | 11..9 = A | 8..6 = cf | 5..0 = payload
```

| cf | A | payload | Instruction |
|:--:|:--:|:--|:--|
| `000` | Rs | `000000` | `JMP Rs` |
| `001` | Rlink | `Rtarget ‖ 000` | `CALL Rlink, Rtarget` |
| `010` | `000` | `000000` | `HALT` |
| `011` | Rd | imm6 | `ANDI Rd, #imm6` |
| `100` | Rd | imm6 | `ORI Rd, #imm6` |
| `101` | Rd | imm6 | `SLLI Rd, #imm6` |
| `110` | Rd | imm6 | `SRLI Rd, #imm6` |
| `111` | Rd | imm6 | `SRAI Rd, #imm6` |

All cells not listed above are reserved (Section 5.6). The immediate
operations are **two-operand (in-place)**: the encoding budget
(4+3+3+6 = 16 bits) leaves no field for a separate `Rs`; the three-operand
source form is a pseudo-op (Section 8.4). Group-B immediates are
**zero-extended**; shift amounts use `imm6 & 0xF`.

### 5.5 Bank F — Constants and Byte Memory (opcode 0xF)

```
15..12 = 1111 | 11..9 = Rd | 8 = b8 | 7..6 = mf | 5..0 = payload
```

| b8 | mf | payload | Instruction |
|:--:|:--:|:--|:--|
| `1` | – | imm8 = `inst[7:0]` | `LDI8 Rd, #imm8` |
| `0` | `00` | `000000` | `LDI Rd, #imm16` (2-word; word 2 = imm16) |
| `0` | `01` | Rs ‖ off3 | `LDB Rd, [Rs + zext(off3)]` |
| `0` | `10` | Rs ‖ off3 | `STB Rd, [Rs + zext(off3)]` |

All other group-F patterns (`mf = 11`; `mf = 00` with nonzero payload) are
reserved (Section 5.6).

### 5.6 Reserved Encodings

Any 16-bit pattern that is not listed as legal in this section and in
Appendix A is **reserved**. A conforming implementation MUST execute every
reserved encoding exactly as `NOP`: no register write, no memory write, no
`HALT`, and `PC += 2` (fetch of a reserved word inside an immediate
payload cannot occur — `LDI` word 2 is never decoded). This rule is
normative, testable by formal assertion, and replaces v2.0's silent
"must be zero" padding.

## 6. Instruction Semantics

All ALU results are modulo 2^16. Writes to `R0` are discarded as per
Section 4.1. In the tables below, operands are read before any write
(e.g. `ADDI R7, R7, #-2` reads the old `R7`).

### 6.1 Arithmetic, Logic, Shifts, Comparison

| Instruction | Semantics |
|:--|:--|
| `ADD Rd, Rs, Rt` | `R[Rd] = R[Rs] + R[Rt]` |
| `ADDI Rd, Rs, #imm6` | `R[Rd] = R[Rs] + sext6(imm6)` |
| `XOR Rd, Rs, Rt` | `R[Rd] = R[Rs] ^ R[Rt]` |
| `XORI Rd, Rs, #imm6` | `R[Rd] = R[Rs] ^ sext6(imm6)` |
| `SUB Rd, Rs, Rt` | `R[Rd] = R[Rs] - R[Rt]` |
| `AND Rd, Rs, Rt` | `R[Rd] = R[Rs] & R[Rt]` |
| `OR Rd, Rs, Rt` | `R[Rd] = R[Rs] \| R[Rt]` |
| `SLL Rd, Rs, Rt` | `R[Rd] = R[Rs] << (R[Rt] & 0xF)` |
| `SRL Rd, Rs, Rt` | `R[Rd] = R[Rs] >> (R[Rt] & 0xF)` |
| `SRA Rd, Rs, Rt` | `R[Rd] = signed(R[Rs]) >>> (R[Rt] & 0xF)` |
| `SLT Rd, Rs, Rt` | `R[Rd] = (signed(R[Rs]) < signed(R[Rt])) ? 1 : 0` |
| `SLTU Rd, Rs, Rt` | `R[Rd] = (R[Rs] < R[Rt]) ? 1 : 0` (unsigned) |
| `ANDI Rd, #imm6` | `R[Rd] = R[Rd] & zext(imm6)` |
| `ORI Rd, #imm6` | `R[Rd] = R[Rd] \| zext(imm6)` |
| `SLLI Rd, #imm6` | `R[Rd] = R[Rd] << (imm6 & 0xF)` |
| `SRLI Rd, #imm6` | `R[Rd] = R[Rd] >> (imm6 & 0xF)` |
| `SRAI Rd, #imm6` | `R[Rd] = signed(R[Rd]) >>> (imm6 & 0xF)` |

`SLT`/`SLTU` produce exactly 0 or 1 and set nothing else (there are no
flags). Sign extension applies to `ADDI`/`XORI` immediates only; the
group-B immediates (`ANDI`/`ORI`/shifts) are zero-extended per Section 5.4.

### 6.2 Constants

| Instruction | Semantics |
|:--|:--|
| `LDI8 Rd, #imm8` | `R[Rd] = zext8(imm8)`; 1 word |
| `LDI Rd, #imm16` | `R[Rd] = imm16`; 2 words; `PC_next = PC + 4` |

The assembler MUST auto-select `LDI8` when the source immediate of an
`LDI` resolves to 0..255 (Section 8.5).

### 6.3 Memory

| Instruction | Semantics |
|:--|:--|
| `LD Rd, [Rs + off]` | `R[Rd] = Mword[(R[Rs] + 2*sext6(off)) & ~1]` |
| `ST Rd, [Rs + off]` | `Mword[(R[Rs] + 2*sext6(off)) & ~1] = R[Rd]` |
| `LDB Rd, [Rs + off]` | `R[Rd] = zext8(M8[R[Rs] + zext3(off)])` |
| `STB Rd, [Rs + off]` | `M8[R[Rs] + zext3(off)] = R[Rd][7:0]` |

`LD`/`ST` offsets are in **words**, range −32..+31 (−64..+62 bytes); all
64 encodings are distinct and legal (v3.1). `LDB`/`STB` offsets are in
**bytes**, range 0..7. Field roles are unchanged from v2.x: `Rd` is the
load destination / store source; `Rs` is the base.

### 6.4 Unconditional Control Transfer

| Instruction | Semantics |
|:--|:--|
| `JMP Rs` | `PC = R[Rs]` (fetch uses `R[Rs] & ~1`, Section 4.2) |
| `JMPR off12` | `PC = PC_next + 2*sext12(off12)` |
| `CALL Rlink, Rtarget` | `R[Rlink] = PC_next`; `PC = R[Rtarget]` |
| `CALLR Rlink, off9` | `R[Rlink] = PC_next`; `PC = PC_next + 2*sext9(off9)` |

`CALL`/`CALLR` have **no memory side effect**: the return address is
stored in `Rlink` only. Nested calls MUST save the link register (for
example on the stack, per Section 10.3). `CALLR R0, off` is legal: the
link write is discarded and the call is effectively `JMPR off`.

### 6.5 Conditional Branch

| Instruction | Semantics |
|:--|:--|
| `BR cc, Rs, off7` | if `cond(cc, R[Rs])` then `PC = PC_next + 2*sext7(off7)` else `PC = PC_next` |

The four conditions (cc = `inst[8:7]`):

| cc | Mnemonic | `cond` |
|:--:|:--|:--|
| `00` | `Z`  | `R[Rs] == 0` |
| `01` | `NZ` | `R[Rs] != 0` |
| `10` | `MI` | `signed(R[Rs]) < 0` (bit 15 set) |
| `11` | `PL` | `signed(R[Rs]) >= 0` (bit 15 clear) |

There is exactly **one** branch instruction. Register-register relations
(`Rs` vs `Rt`) are expressed by materialising a boolean or difference
(`SLT`, `SLTU`, `SUB`) into a scratch register and branching on it —
two words. All four conditions have exact inverses (Z↔NZ, MI↔PL), which
Section 8.6 relies on. `Rs` may be any register including `R0`.

### 6.6 HALT

`HALT` stops instruction fetch and freezes all architectural state
(registers, memory, `PC`) until reset or an enabled interrupt. This
platform has no interrupts, so only reset (including the debug bus reset,
Section 10.2) exits `HALT`.

### 6.7 NOP

`NOP` = `ADD R0, R0, R0` = `0x0000`. Completes normally with all writes
discarded, `PC += 2`. Every reserved encoding behaves identically
(Section 5.6).

### 6.8 Complete Instruction Reference (29 instructions)

| Mnemonic | Encoding | Operands | Words |
|:--|:--|:--|:--:|
| `ADD`  | `0000 Rd Rs Rt 000` | `Rd, Rs, Rt` | 1 |
| `ADDI` | `0001 Rd Rs imm6` | `Rd, Rs, #imm6` (sext) | 1 |
| `XOR`  | `0010 Rd Rs Rt 000` | `Rd, Rs, Rt` | 1 |
| `XORI` | `0011 Rd Rs imm6` | `Rd, Rs, #imm6` (sext) | 1 |
| `SUB`  | `0100 Rd Rs Rt 000` | `Rd, Rs, Rt` | 1 |
| `AND`  | `0101 Rd Rs Rt 000` | `Rd, Rs, Rt` | 1 |
| `OR`   | `0110 Rd Rs Rt 000` | `Rd, Rs, Rt` | 1 |
| `SLL`  | `0111 Rd Rs Rt 000` | `Rd, Rs, Rt` | 1 |
| `SRL`  | `1000 Rd Rs Rt 000` | `Rd, Rs, Rt` | 1 |
| `SLT`  | `0100 Rd Rs Rt 001` | `Rd, Rs, Rt` | 1 |
| `SLTU` | `0100 Rd Rs Rt 010` | `Rd, Rs, Rt` | 1 |
| `SRA`  | `1000 Rd Rs Rt 001` | `Rd, Rs, Rt` | 1 |
| `LD`   | `1001 Rd Rs off6` | `Rd, [Rs + off6]` (words) | 1 |
| `ST`   | `1010 Rd Rs off6` | `Rd, [Rs + off6]` (words) | 1 |
| `JMP`  | `1011 Rs 000 000000` | `Rs` | 1 |
| `CALL` | `1011 Rlink 001 Rtarget 000` | `Rlink, Rtarget` | 1 |
| `HALT` | `1011 000 010 000000` | – | 1 |
| `ANDI` | `1011 Rd 011 imm6` | `Rd, #imm6` (zext) | 1 |
| `ORI`  | `1011 Rd 100 imm6` | `Rd, #imm6` (zext) | 1 |
| `SLLI` | `1011 Rd 101 imm6` | `Rd, #imm6` | 1 |
| `SRLI` | `1011 Rd 110 imm6` | `Rd, #imm6` | 1 |
| `SRAI` | `1011 Rd 111 imm6` | `Rd, #imm6` | 1 |
| `BR`   | `1100 Rs cc off7` | `cc, Rs, off7` (words) | 1 |
| `CALLR`| `1101 Rlink off9` | `Rlink, off9` (words) | 1 |
| `JMPR` | `1110 off12` | `off12` (words) | 1 |
| `LDI`  | `1111 Rd 0 00 000000` + imm16 | `Rd, #imm16` | 2 |
| `LDI8` | `1111 Rd 1 imm8` | `Rd, #imm8` | 1 |
| `LDB`  | `1111 Rd 0 01 Rs off3` | `Rd, [Rs + off3]` (bytes) | 1 |
| `STB`  | `1111 Rd 0 10 Rs off3` | `Rd, [Rs + off3]` (bytes) | 1 |
| `NOP`  | `0000 000 000 000 000` | – (encoding alias of `ADD R0,R0,R0`; no assembler mnemonic, §8.2) | 1 |

## 7. Control-Transfer Model

### 7.1 The PC_next Rule

`PC_next` denotes the address of the instruction following the current one:
`PC + 2`, or `PC + 4` for the two-word `LDI` (which is never
PC-relative). **All** PC-relative targets (`BR`, `JMPR`, `CALLR`) are
computed from `PC_next`, i.e. the offset is relative to the *already
incremented* PC:

```
PC_taken = PC_next + 2 * sext(offset)
```

A conforming assembler MUST encode `offset = (target - addr - 2) / 2`
where `addr` is the byte address of the branch instruction. The link value
written by `CALL`/`CALLR` is also `PC_next`, so `JMP Rlink`/`JMPR`
returns to the instruction after the call.

### 7.2 Offset Ranges

| Instruction | Field | Range (words) | Range (bytes) |
|:--|:--|:--|:--|
| `BR` | off7 | −64 .. +63 | ±128 |
| `CALLR` | off9 | −256 .. +255 | ±512 |
| `JMPR` | off12 | −2048 .. +2047 | ±4 KB |
| `LD`/`ST` | off6 | −32 .. +31 | −64 .. +62 |

Targets outside the range MUST be handled by assembler relaxation
(Section 8.6); out-of-range literal offsets encode the low bits and MUST
be diagnosed with a warning (they do not saturate or trap).

### 7.3 Call/Return Idioms (informative)

- Direct call to a label: `CALLR R5, target` (1 word; range ±512 bytes).
- Indirect call: `LDI8 R4, #fn; CALL R5, R4` (2–3 words; anywhere).
- Return: `JMPR -1`-style (assembler: `JMPR label`) or `JMP Rlink`.
- Tail call: `JMPR label`.

## 8. Assembler Specification

### 8.1 Syntax

- One instruction or directive per line; comments start with `;`.
- Mnemonics and register names are **case-sensitive and uppercase**
  (`add`, `r1` are errors). Directives are lowercase (`.org`, `.word`,
  `.dw`, `.scratch`).
- Immediates: optional `#` prefix, decimal, `0x…`, or `0b…`; negatives
  allowed where the field is signed.
- Labels: `name:` at the start of a line; label references resolve to the
  label's byte address.
- Branch/control operands that name a label are PC-relative; numeric
  operands are literal word offsets.

### 8.2 Mnemonics

All 29 architectural instructions of Section 6.8 are accepted, plus the
pseudo-ops of Section 8.4 and the directives of Section 8.3. `NOP` is an
*encoding* (Section 6.7), not a mnemonic: the assembler has no `NOP`
token and sources spell it `ADD R0, R0, R0`. The v2.x mnemonics
**`BEQ`, `BNE`, and `BLT` do not exist in v3** and MUST be rejected by the
assembler (they are not silently reinterpreted; see Appendix D).

### 8.3 Directives

| Directive | Effect |
|:--|:--|
| `.org ADDR` | set the current assembly address to `ADDR` |
| `.word w1, w2, …` (`.dw` alias) | emit raw 16-bit words |
| `.scratch Rn` | set the scratch register for pseudo-op expansion (default `R4`) |

### 8.4 Pseudo-Ops

`t` denotes the scratch register (`R4` unless `.scratch` overrides it).

| Pseudo | Expansion | Words |
|:--|:--|:--:|
| `MOV Rd, Rs` | `ADD Rd, Rs, R0` | 1 |
| `NEG Rd, Rs` | `SUB Rd, R0, Rs` | 1 |
| `NOT Rd, Rs` | `XORI Rd, Rs, #-1` | 1 |
| `CLR Rd` | `XOR Rd, Rd, Rd` | 1 |
| `LSL/LSR/ASR Rd, Rs, Rt` | `SLL/SRL/SRA Rd, Rs, Rt` | 1 |
| `B label` | `BR Z, R0, label` | 1 (4 far) |
| `BGT Rs, Rt, L` | `SLT t, Rt, Rs` ; `BR NZ, t, L` | 2 (5 far) |
| `BGE Rs, Rt, L` | `SLT t, Rs, Rt` ; `BR Z, t, L` | 2 (5 far) |
| `BLE Rs, Rt, L` | `SLT t, Rt, Rs` ; `BR Z, t, L` | 2 (5 far) |
| `BLTU Rs, Rt, L` | `SLTU t, Rs, Rt` ; `BR NZ, t, L` | 2 (5 far) |
| `BGEU Rs, Rt, L` | `SLTU t, Rs, Rt` ; `BR Z, t, L` | 2 (5 far) |
| `BLEU Rs, Rt, L` | `SLTU t, Rt, Rs` ; `BR Z, t, L` | 2 (5 far) |
| `RET [Rlink]` | `JMP Rlink` (default `R5`) | 1 |
| `ANDI/ORI/SLLI/SRLI/SRAI Rd, Rs, #imm` | `ADD Rd, Rs, R0` ; op `Rd, #imm` (copy elided if `Rs == Rd`) | 1–2 |
| `JMP label` | `LDI8 R4, #label` ; `JMP R4` (or 2-word `LDI`) | 2–3 |

Notes:
- Signed comparisons lower to `SLT` (signed) or `SLTU` (unsigned) plus a
  single `BR`; equality branches on a relation use `SUB` or `SLT` and
  `BR Z`.
- The pseudo-ops clobber the scratch register (and, for the 3-op
  immediates, write `Rd` twice). Programs where `Rd == t` for a
  compare-and-branch pair are the programmer's responsibility to avoid
  or to route with `.scratch`.

### 8.5 LDI Auto-Narrowing

`LDI Rd, #imm` with a resolved immediate in 0..255 MUST assemble to the
one-word `LDI8` form; otherwise the two-word form is used. Explicit
`LDI8` always assembles to one word (with a range diagnostic outside
0..255). Because label values depend on instruction sizes, which depend on
narrowing, resolution is a joint fixed point (Appendix C).

### 8.6 Branch and Control Relaxation

When a PC-relative target exceeds the instruction's field range, the
assembler MUST substitute the following exact forms (bit patterns are
normative; `invcc` is the exact inverse condition, Z↔NZ, MI↔PL):

**`BR cc, Rs, target` beyond ±64 words — 4 words:**

```
    BR   invcc, Rs, +3          ; skip the absolute jump when NOT taken
    LDI  R4, #target            ; always the 2-word form
    JMP  R4
```

**`JMPR target` beyond ±2048 words — 3 words:**

```
    LDI  R4, #target            ; always the 2-word form
    JMP  R4
```

**`CALLR Rlink, target` beyond ±256 words — 7 words:**

```
    ST   t, [R7 + 14]           ; spill scratch at SP+14 (off6 = 7 words)
    ADDI R7, R7, #-2            ; allocate
    LDI  t, #target             ; always the 2-word form
    CALL Rlink, t               ; link reads the OLD t value first
    LD   t, [R7 + 14]           ; restore scratch
    ADDI R7, R7, #2             ; release
```

with `t = R4`, or `t = R3` when `Rlink == R4`. The stack traffic is
balanced and therefore transparent to all code. Because every condition
inverts exactly, relaxation cannot introduce the equality bug class that
v2.1's relaxed `BLT` suffered (Appendix D).

### 8.7 Diagnostics

The assembler MUST warn (not error) on literal offsets outside their field
range (Section 7.2), on out-of-range `LDB`/`STB`/`LDI8` immediates, and on
unknown symbols; it MUST error on unknown mnemonics (including `NOP`,
`BEQ`/`BNE`/`BLT`) and bad operands.

## 9. Encoding Space Accounting

All 65 536 16-bit patterns, classified (details in Appendix A):

| Opcode(s) | Total | Legal | Reserved (→ NOP) |
|:--|--:|--:|--:|
| 0,2,4,5,6,7,8 (register ALU) | 28 672 | 5 120 | 23 552 |
| 1,3 (`ADDI`/`XORI`) | 8 192 | 8 192 | 0 |
| 9,A (`LD`/`ST`) | 8 192 | 8 192 | 0 |
| B (group B) | 4 096 | 2 633 | 1 463 |
| C (`BR`) | 4 096 | 4 096 | 0 |
| D (`CALLR`) | 4 096 | 4 096 | 0 |
| E (`JMPR`) | 4 096 | 4 096 | 0 |
| F (group F) | 4 096 | 3 080 | 1 016 |
| **Total** | **65 536** | **39 505** | **26 031** |

The v3 encoding space has the same legal/reserved totals as v2.1 (the
three branch opcodes were fully legal then and are fully legal now, just
with different meanings). Instruction count: 29 architectural instructions
+ `NOP`.

## 10. Platform Profile

This section is informative for the ISA and normative for the reference
SoC (`Soc`/`PipSoc`); the bus protocol is specified in `INSTRUCTIONS.md`.

### 10.1 Memory Map

| Range | Contents |
|:--|:--|
| `0x0000`..`0x1FFB` | data RAM (single-port, synchronous) |
| `0x1FFC`..`0xFFFF` | I/O region: reads return the UART RX byte (zero-extended); writes emit the low byte on UART TX |

The conventional addresses are `0x1FFC` (RX/GPIO-in) and `0x1FFE`
(TX/GPIO-out); any address ≥ `0x1FFC` aliases into this region. The
program image occupies the instruction ROM from address 0; instruction and
data spaces are physically separate in the reference SoC despite sharing
the numeric space.

### 10.2 HALT and Debug

`HALT` (Section 6.6) is exited by reset, including the debug bus reset
(command `0x03`). The debug interface (register/memory read, single-step)
is platform-defined; see `INSTRUCTIONS.md`.

### 10.3 Software Conventions (ABI)

The C compiler (`cc.py`) and runtime use:

| Register | Role |
|:--|:--|
| `R0` | zero |
| `R1` | accumulator / first argument / return value |
| `R2` | second argument / temp |
| `R3`, `R4` | third argument / temps |
| `R5` | link register (`CALLR R5, fn`) |
| `R6` | frame pointer |
| `R7` | stack pointer (grows down, initialised to `0x1FFA`) |

Arguments beyond three are passed on the stack. Functions save `R5`/`R6`
in their prologue when nested. `__mul16`/`__div16`/`__mod16` runtime
routines implement multiply/divide/modulo in software.

## 11. Programming Examples (informative)

### 11.1 Sum of a 10-Word Array

```
        LDI  R1, #0x1000       ; base address (2 words)
        LDI  R2, #10           ; counter    (LDI8, 1 word)
        LDI  R3, #0            ; sum        (LDI8, 1 word)
loop:   LD   R4, [R1 + 0]      ; load word  (offset 0 words)
        ADD  R3, R3, R4
        ADDI R1, R1, #2        ; next byte address
        ADDI R2, R2, #-1
        BR   NZ, R2, loop      ; while counter != 0
        HALT                   ; result in R3
```

### 11.2 Signed and Unsigned Loop Conditions

```
        SLT  R4, R1, R2        ; R4 = (signed)i < limit ? 1 : 0
        BR   NZ, R4, loop      ; signed  i <  limit
        SLTU R4, R1, R2        ; unsigned
        BR   NZ, R4, loop      ; unsigned i < limit
        SUB  R4, R1, R2        ; equality/other relations via differences
        BR   Z,  R4, equal
```

### 11.3 Call, Return, Far Call

```
        CALLR R5, worker       ; 1 word, ±512 bytes; R5 = return address
        JMPR  done             ; PC-relative jump
worker: ADDI  R6, R6, #-4      ; frame
        ST    R5, [R6 + 0]     ; save link (nested calls clobber R5)
        ; ...
        LD    R5, [R6 + 0]
        ADDI  R6, R6, #2       ; offsets are WORDS (v3.1): +0, +1, ...
        JMPR  label_far        ; assembler relaxes if beyond ±2048 words
```

### 11.4 Byte I/O

```
        LDI  R1, #0x1FFC       ; RX port
        LDI  R2, #0x1FFE       ; TX port
loop:   LDB  R3, [R1 + 0]      ; read byte, zero-extended
        STB  R3, [R2 + 0]      ; echo byte
        B    loop
```

## 12. Implementation Notes (informative)

### 12.1 Decoder Equations

```
op  = inst[15:12]
is_alu   = (op <= 8)                                    ; NOT op[3]
imm_en   = (op == 1) || (op == 3)
aluFunc  = (op < 4) ? {2'b00, op[1]} : (op[2:0] - 2)    ; 3-bit, then extended:
;   4-bit map actually used by ALU.scala:
;   0000 ADD  0001 XOR  0010 SUB  0011 AND  0100 OR
;   0101 SLL  0110 SRL  1000 SLT  1001 SLTU 1010 SRA
;   reserved aluFunc codes produce 0
f3    = inst[2:0]
cf    = inst[8:6]           ; group B
b8    = inst[8]             ; group F
mf    = inst[7:6]           ; group F
cc    = inst[8:7]           ; BR
isBR    = (op == 0xC)       isCALLR = (op == 0xD)       isJMPR = (op == 0xE)
```

### 12.2 Datapath Notes

- `SLT`/`SLTU` reuse/extend the branch comparator; `SRA` extends the
  shifter with sign fill.
- The effective address adder pre-muxes the scaled word offset
  (`off6 * 2`) before adding (one adder, not two).
- `CALL`/`CALLR` write `PC_next` through the normal regfile write port
  and select the branch target path; `JMPR`/`BR` share the PC-add path.
- Pipeline implementations must handle load-use hazards and may forward;
  the architectural result MUST match sequential execution.

## 13. Verification (informative)

- **Formal** (Bounded Model Checking, `sbt test`): component suites at
  depth 30 (`ALU`, `Decoder`, `Core`, `PipCore`, `RegFile`,
  `BusInterface`) and SoC suites at depth 10 (`Soc`, `PipSoc`), plus UART
  and pipeline-sim suites — 35 test cases, all passing. Assertions cover
  per-instruction semantics, reserved-is-NOP, link values, byte-lane
  masks, and write-port arbitration (including the dual-write-port
  concurrent LD+WB case).
- **Regression** (`run_tests.py`): 51/51 programs on **both**
  implementations — 41 C programs (compiled by `cc.py`) and 10
  ISA-level assembly tests (`slt_test`, `sra_test`, `imm_test`,
  `call_test`, `callr_test`, `bypass_test`, `byte_test`,
  `ldi8_rsvd_test`, `bge_test`, `pseudo_test`) covering every
  instruction, pseudo-op, and relaxation form.
- **Coverage audit** (`isa_coverage.py`): every hardware instruction and
  pseudo-op appears in at least one executed test.

## 14. Security and Robustness Considerations

- **Determinism under corruption:** reserved encodings execute as `NOP`
  (Section 5.6); a corrupted instruction stream degrades gracefully
  (typically to a spin) rather than executing unintended side effects.
- **No privilege model:** there is no user/supervisor split, no memory
  protection, and no MMU. Any code can access any address, including the
  I/O region. Physical security is the system's responsibility.
- **`HALT` is not a security boundary:** it freezes the core until reset;
  the debug bus can step and write registers.
- **Denial of service:** infinite loops are unpreemptible (no
  interrupts); watchdog functionality must be external.
- **Wrap-around:** all arithmetic wraps mod 2^16; pointer overflow is
  silent, as on typical small microcontrollers.

## 15. IANA Considerations

This document fully accounts for the 16-bit encoding space (Section 9);
no further number assignments are possible without a new ISA revision.
There are no protocol parameter registries.

## 16. Change History

```
v2.0   Original 16-instruction set (ISA.md).
v2.1   Padding-field expansion: +13 instructions, documented NOP,
       alignment/endianness/reset/reserved rules (ISA-2.1.md, frozen).
v3.0   Scaffold for breaking changes (PLAN.md Phase 1).
v3.1   LD/ST offsets word-scaled (all 64 encodings distinct).
v3.2   Unified branch BR cc,Rs,off7 (Z/NZ/MI/PL); BEQ/BNE/BLT removed.
v3.3   PC-relative CALLR Rlink,off9 and JMPR off12 on opcodes 0xD/0xE.
       (LDIH single-word constants: closed as impossible; clean ALU
       opcode map: closed as wontfix; byte-offset widening: deferred.)
```

---

## Appendix A. Complete Encoding Tables

### A.1 Opcode 0x0..0x8 — Register ALU (Format R)

| op | f3 | Mnemonic | Reserved f3 values |
|:--:|:--:|:--|:--|
| 0x0 | 000 | `ADD` | 001..111 |
| 0x2 | 000 | `XOR` | 001..111 |
| 0x4 | 000 | `SUB` | 011..111 |
| 0x4 | 001 | `SLT` | |
| 0x4 | 010 | `SLTU` | |
| 0x5 | 000 | `AND` | 001..111 |
| 0x6 | 000 | `OR` | 001..111 |
| 0x7 | 000 | `SLL` | 001..111 |
| 0x8 | 000 | `SRL` | 010..111 |
| 0x8 | 001 | `SRA` | |

Legal: 10 operations × 512 (8×8×8 field combinations) = 5 120 of 28 672.

### A.2 Opcodes 0x1/0x3 — Immediate ALU (Format I)

`ADDI`/`XORI`, all 8 192 patterns legal (8 Rd × 8 Rs × 64 imm).

### A.3 Opcodes 0x9/0xA — Word Load/Store (Format M)

`LD`/`ST`, all 8 192 patterns legal (offsets are word-scaled, v3.1 — no
aliased encodings remain).

### A.4 Opcode 0xB — Group B (Format B)

| cf | A | payload | Legal? | Count |
|:--:|:--:|:--|:--|--:|
| 000 | any | 000000 | `JMP` | 8 |
| 000 | any | ≠0 | reserved | 504 |
| 001 | any | [2:0]=000 | `CALL` | 64 |
| 001 | any | [2:0]≠000 | reserved | 448 |
| 010 | 000 | 000000 | `HALT` | 1 |
| 010 | ≠000 / payload≠0 | | reserved | 511 |
| 011..111 | any | any | `ANDI`/`ORI`/`SLLI`/`SRLI`/`SRAI` | 2 560 |

Legal: 2 633 of 4 096.

### A.5 Opcodes 0xC/0xD/0xE — Control (Formats BR, C, J)

All patterns legal: `BR` 8×4×128 = 4 096; `CALLR` 8×512 = 4 096;
`JMPR` 4 096.

### A.6 Opcode 0xF — Group F (Formats F, L)

| b8 | mf | payload | Legal? | Count |
|:--:|:--:|:--|:--|--:|
| 1 | – | any | `LDI8` | 2 048 |
| 0 | 00 | 000000 | `LDI` (2-word) | 8 |
| 0 | 00 | ≠0 | reserved | 504 |
| 0 | 01 | any | `LDB` | 512 |
| 0 | 10 | any | `STB` | 512 |
| 0 | 11 | any | reserved | 512 |

Legal: 3 080 of 4 096. `LDI` word 2 carries the immediate and is never
decoded (Section 4.2).

## Appendix B. Pseudo-Op Reference

See Section 8.4 for the expansion table. Additional notes:

- Every compare-and-branch pseudo writes the scratch register; the
  default is `R4`, overridden per-file or per-region with
  `.scratch Rn` (recommended before a block of pseudo-branches, and
  restored with `.scratch R4` afterwards).
- `JMP label` and `LDI` share the narrowing machinery: a label below 256
  yields `LDI8 R4, #label` (2 words total with `JMP R4`).
- `RET` defaults to `R5` (the ABI link register) and is exactly
  `JMP R5`.
- `NOT` uses the sign-extended `XORI #-1`, i.e. all-ones; `CLR` is
  `XOR Rd,Rd,Rd` (works even with the pipeline's forwarding rules).

## Appendix C. Relaxation and Layout Algorithm

Assembly is a joint fixed point over three decisions, iterated to
convergence (default implementation caps at 64 iterations and falls back
to the widest layout: two-word `LDI` everywhere, relaxation recomputed):

1. **Relaxation** (`compute_relaxed`): every emitted `BR` is checked at
   its own address (a compare-and-branch pseudo's `BR` sits 2 bytes after
   the `SLT`); out-of-range `BR`/`CALLR`/`JMPR` label references grow to
   the far forms of Section 8.6. Growth moves other branches, so this
   iterates.
2. **Labels** (`compute_labels`): byte addresses under the current sizes.
3. **Narrowing** (`compute_narrow`): `LDI`/`JMP label` with resolved
   values in 0..255 use the one-word forms.

Size table (bytes):

| Construct | Near | Far |
|:--|--:|--:|
| `BR`, `B` | 2 | 8 |
| `CALLR` | 2 | 14 |
| `JMPR` | 2 | 6 |
| `JMP label` | 4 (LDI8) | 6 (LDI) |
| `LDI` | 2 | 4 |
| compare-and-branch pseudo | 4 | 10 |
| 3-op immediate pseudo | 2 | — (never relaxed) |

Conformance requirement: the encoded offset of every non-relaxed
PC-relative instruction MUST equal `(target - addr - 2) / 2`, and far
forms MUST match Section 8.6 bit-for-bit (they are covered by
`pseudo_test.asm` in both directions, including taken and not-taken with
equal operands).

## Appendix D. Migration from v2.1

### D.1 Opcode Reinterpretation (breaking)

| Opcode | v2.1 | v3 |
|:--:|:--|:--|
| 0xC | `BEQ Rs, Rt, off6` (bytes) | `BR cc, Rs, off7` (words) |
| 0xD | `BNE Rs, Rt, off6` | `CALLR Rlink, off9` |
| 0xE | `BLT Rs, Rt, off6` | `JMPR off12` |

Old binaries MUST NOT be run: opcode 0xC..0xE execute different
instructions. Rebuild from source.

### D.2 Source-Level Migration

| v2.1 source | v3 source |
|:--|:--|
| `BEQ Rs, Rt, L` | `SUB t, Rs, Rt` ; `BR Z, t, L` (or `BGE`/`BLE` pseudos) |
| `BNE Rs, Rt, L` | `SUB t, Rs, Rt` ; `BR NZ, t, L` |
| `BLT Rs, Rt, L` | `SLT t, Rs, Rt` ; `BR NZ, t, L` |
| `LD Rd, [Rs + 8]` (bytes) | `LD Rd, [Rs + 4]` (words) — divide byte offsets by 2 |
| `ST Rd, [Rs -2]` (byte-aliased) | `ST Rd, [Rs -1]` (words) |
| `CALL R5, R7` | unchanged, or `CALLR R5, label` (1 word) |
| `JMP label` | unchanged, or `JMPR label` |

The v2.1 odd-offset LD/ST aliasing (bit 0 ignored, half the encodings
wasted) disappears: offsets are word-scaled and every encoding is
distinct.

### D.3 Closed and Deferred v3 Proposals

- **LDIH (single-word 16-bit constants):** impossible — group F has 9
  bits after `op‖Rd`; `LDI8` consumes marker + 8, leaving 6 payload bits
  for any sibling, which cannot express a byte-granular high half.
- **Clean ALU opcode map:** wontfix — regrouping opcodes would break the
  v2.0/v2.1 binary contract for no measurable gain.
- **Byte-offset widening for `LDB`/`STB`:** deferred — `cc.py` emits no
  byte ops today; revisit with profiler evidence.

## Appendix E. Reserved Space and Future Extensions

| Location | Count | Candidates |
|:--|--:|:--|
| Bank A, undefined f3 | 23 552 | `MUL` (f3=001 under `ADD`), `MULU`, byte-swap/`REV`, `NOR` |
| Group B, malformed cells | 1 463 | a second two-operand immediate family |
| Group F, `mf=11` | 512 | `LDBS` (sign-extending byte load), halfword ops |
| Group F, `mf=00`, payload≠0 | 504 | sign-extended 6-bit constant load (`LDIS`) |
| — | — | interrupts/traps (vector + saved-PC model first), `WFI` |

New instructions MUST land in reserved cells (never re-interpret legal
patterns), MUST default to `NOP` until implemented, and MUST be added to
`isa_coverage.py`'s audit when they gain tests.

## Appendix F. Rationale (informative)

- **Unified `BR` (why one branch):** the v2.1 set needed six
  compare-and-branch pseudos but only three hardware comparisons; a
  single `cc` field on a single-register branch (a) frees the `Rt` field
  for a 7-bit offset (±64 words vs ±32), (b) makes MI/PL — the two
  conditions that need only bit 15 — free of comparator logic, and
  (c) provides exact inverses for every condition, which makes far-branch
  relaxation trivially correct (no equality special case, the v2.1
  relaxed-`BLT` bug class is impossible by construction).
- **PC-relative `CALLR`/`JMPR` (why opcodes 0xD/0xE):** the compiler
  emits calls and loop back-edges constantly; `CALLR R5, fn` is one word
  versus `LDI8 R4,#fn; CALL R5,R4` (2–3 words), and loop edges cost one
  word instead of a materialised address. The freed branch opcodes were
  the only fully-free slots with room for a link (9 bits) or a wide
  offset (12 bits).
- **Word-scaled `LD`/`ST` offsets (why v3.1):** byte offsets wasted half
  the encoding space on odd-address aliases, and byte-addressable arrays
  (`+= 2` per element) are the dominant addressing idiom; scaling the
  offset by 2 doubles the reachable window (±64 bytes) at zero cost.
- **Two-operand immediates with zero extension:** masks and shift amounts
  are unsigned; sign extension would reserve the useful low range for
  negative values nobody masks with. `ADDI`/`XORI` remain sign-extended
  because they do arithmetic.
- **Reserved = NOP:** makes illegal-word behaviour deterministic,
  formally assertable (`reserved |-> no side effects`), and safe against
  corrupted memory — the RTL default of "ignore the funct field and
  decode the base op" would otherwise execute a *different instruction*.

---

*This document defines the complete version 3 instruction set: 29
instructions, deterministic legalisation of all unassigned encodings, an
assembler contract with exact relaxation forms, and a full accounting of
the 16-bit encoding space. It supersedes ISA.md and ISA-2.1.md.*
