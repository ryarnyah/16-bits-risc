# 16-bit RISC Microcontroller ISA Specification — Version 2.1

**Status: IMPLEMENTED (v2.1 final)** — supersedes `ISA.md` (v2.0). Implemented
in RTL (`Core`, `PipCore`), `asm.py`, `run_tests.py`; verified by formal BMC,
41 C tests + 7 ISA-level asm tests on both SoCs.
**Instruction width:** 16 bits (fixed); sole exception: `LDI` (2-word immediate)
**Data width:** 16 bits · **Address space:** 16-bit byte-addressable (64 KB)
**Registers:** 8 GPRs `R0`–`R7`, 16 bits; `R0` hardwired to 0
**PC:** 16-bit byte address, advances by 2 (by 4 for 2-word `LDI`)

---

## 0. What Changed in v2.1

v2.1 is a **strict, binary-compatible superset** of v2.0:

> **Compatibility guarantee.** Every encoding that was *legal* in v2.0 has
> bit-identical meaning in v2.1. v2.1 only assigns meaning to encodings v2.0
> declared *illegal* (padding fields marked "must be zero"). Encodings that
> remain unassigned are normatively defined as `NOP`.

### 0.1 New instructions (13 + documented `NOP`)

| # | Instr | Group | Closes gap |
|---|-------|-------|-----------|
| 1 | `SLT`   | funct3 under `SUB` | signed set-less-than (compare without branch) |
| 2 | `SLTU`  | funct3 under `SUB` | **unsigned compare** (missing entirely in v2.0) |
| 3 | `SRA`   | funct3 under `SRL` | arithmetic right shift / signed division by 2 |
| 4 | `CALL`  | group B (JMP slot) | **link + jump in one word** (v2.0 had no call mechanism) |
| 5 | `HALT`  | group B (JMP slot) | sleep/stop until reset or interrupt |
| 6 | `ANDI`  | group B (JMP slot) | **immediate AND / masks** (v2.0 had none) |
| 7 | `ORI`   | group B (JMP slot) | immediate OR / flag bits |
| 8 | `SLLI`  | group B (JMP slot) | shift by immediate (v2.0 needed a register + `LDI`) |
| 9 | `SRLI`  | group B (JMP slot) | idem |
| 10| `SRAI`  | group B (JMP slot) | idem, arithmetic |
| 11| `LDI8`  | group F (LDI slot) | **1-word constant load** for 0..255 (v2.0: always 2 words) |
| 12| `LDB`   | group F (LDI slot) | **byte load** (v2.0: word-only despite byte-addressable space) |
| 13| `STB`   | group F (LDI slot) | **byte store** |
| — | `NOP`   | `ADD R0,R0,R0` = `0x0000` | was legal but never documented |

### 0.2 Normative fixes (v2.0 errata)

| v2.0 defect | v2.1 fix |
|---|---|
| §7.1 claims `alu_func = op[2:0]` — contradicts §2's own table (e.g. XOR opcode 2 → `010`, table says `001`); RTL uses `opc>>1` / `opc-2` (Decoder.scala) | Corrected derivation in §9.1 |
| §2: "All ALU opcodes have `opcode[3]==0`" — false, `SRL` = 0x8 | Corrected: ALU group is `op ≤ 8` |
| §2: "`opcode[0]` selects immediate mode" — false, `AND`=0x5 and `SLL`=0x7 have `op[0]=1` | Corrected: `imm_en = (op==1) \|\| (op==3)` |
| `R0 = 0` used by every example and by §8.2, never defined | §2.1: R0 hardwired, writes ignored |
| §4.2: misaligned access = "undefined behavior" | §2.3: address bit 0 is ignored (deterministic aliasing, matches RTL) |
| Odd jump/fetch behavior undefined | §2.4: fetch uses `PC[15:1]` |
| No endianness, no reset values, no reserved-encoding behavior, no `NOP` | §2.2, §2.5, §2.6, §5.2 |
| §8.1: negation "eliminates a 3-instruction emulation" — it is 2 instructions (`XORI #-1; ADDI #1`), and `NEG` is 1 word (`SUB Rd,R0,Rt`) | Corrected in §10 |
| §6 Examples 1 & 3 contain dead `LDI R7,#loop` (loop uses relative branch); Example 4 jumps to never-initialized R7 | Examples rewritten (§8) |
| §3.3 "offset –32..+31" while odd effective addresses were UB → half the encodings unusable | All 64 offsets legal (bit 0 ignored) |

### 0.3 What was deliberately *not* added

`SLTI`/`SLTIU` (no non-destructive encoding fits; see §10.2), `SUBI` (redundant —
`ADDI Rd,Rd,#-k` already covers it), `MUL`/`DIV`, byte-swap, trap/interrupt
instructions (need a vector model first). Slots for all of them are inventoried
in §13.

---

## 1. Design Philosophy & Extension Rules

- **4-bit opcode map unchanged.** All 16 top-level opcodes keep their v2.0
  meaning; existing binaries execute identically.
- **Sub-opcodes live in padding bits.** v2.0 wasted three kinds of padding;
  v2.1 uses exactly those, and nothing else:

  | Bank | Where padding was | What it becomes |
  |------|-------------------|-----------------|
  | **A** | reg-ALU `[2:0]` ("must be `000`") | 3-register function extension (`SLT`, `SLTU`, `SRA`) |
  | **B** | `JMP` bits `[8:0]` ("must be zero") | control group: `JMP`/`CALL`/`HALT` + 2-operand immediates |
  | **F** | `LDI` word-1 bits `[8:0]` ("must be zero") | constant/byte group: `LDI`/`LDI8`/`LDB`/`STB` |

- **Reserved = NOP.** Any encoding not listed as legal MUST complete with no
  architectural side effect (no register write, no memory write, no PC change
  beyond the normal +2, no `HALT`). This is testable by formal assertions and
  replaces v2.0's silent "must be zero" padding.

- **Source compatibility.** Existing assembly sources assemble unchanged; the
  assembler may pick shorter encodings (e.g. `LDI Rd, #n` with `n ≤ 255` →
  1-word `LDI8`) and expands new pseudo-ops (§7).

- **No widening.** Everything fits the 16-bit fixed format. No prefix, no
  32-bit escape, no funct field collision with immediates (the classic
  `[2:0] funct` vs `[5:0] imm6` overlap is why register-form and
  immediate-form instructions can never share an opcode — group B exists for
  exactly this reason, see §10.2).

---

## 2. Architectural State, Memory, Reset

### 2.1 Registers

- `R0`–`R7`, 16 bits each. **`R0` is hardwired to zero:** reads always return
  `0x0000`; writes are ignored (the instruction still completes normally).
  `MOV Rd, Rs` is therefore `ADD Rd, Rs, R0`, `NEG Rd, Rs` is `SUB Rd, R0, Rs`,
  and `CMP`-style discards are `SUB R0, Rs, Rt`.
- `R1`–`R7` are ordinary read/write registers.

### 2.2 Memory, endianness

- Byte-addressable, 64 KB (`0x0000`–`0xFFFF`). All address arithmetic wraps
  modulo 2¹⁶.
- **Little-endian.** The word at even address `A` is
  `Mword[A] = M8[A] | (M8[A+1] << 8)` — bits `[7:0]` live at the even
  (lower) address. Matches the platform convention that a byte-wide device
  (UART data register) presents its value in the low lane.
- **Byte operations** (`LDB`/`STB`) access `M8[A]` for any `A`, no alignment
  constraint.
- Access to addresses not backed by RAM/I/O is **platform-defined** (the SoC
  maps `≥ 0x1FFC` to peripherals); the ISA only constrains aligned,
  RAM-resident accesses.

### 2.3 Alignment (replaces v2.0's "undefined behavior")

For 16-bit `LD`/`ST` and for all branches, **bit 0 of the computed address is
ignored** (the word index is `addr >> 1`). Consequences, all deterministic:

- Even effective address → normal word access.
- Odd effective address → accesses the *same word* as `addr-1`. Legal, but
  software SHOULD use even addresses; odd offsets are never required (all 64
  `off6` encodings are usable and behave identically to `off-1` with an even
  base).
- Implementations therefore need no alignment trap and no undefined cases.

### 2.4 Instruction fetch and PC

- The instruction executed at byte address `A` is the word at index
  `A >> 1` — **fetch ignores `PC[0]`**. A jump/call/branch to an odd target
  executes the word at `target & ~1`; software MUST use even targets.
- Every instruction advances the PC by **2**, except the 2-word `LDI`, which
  advances by **4**. The second word of `LDI` is an immediate payload and is
  never fetched as an instruction.
- Branch offsets are added to the **already-incremented** PC
  (v2.0 §4.3 rule, unchanged): `PC_taken = PC_next + sext(off6) * 2`.

### 2.5 Reset

| State | Reset value |
|---|---|
| `PC` | `0x0000` |
| `R0` | `0x0000` (always) |
| `R1`–`R7` | `0x0000` |
| Memory | unchanged |

### 2.6 `NOP` and reserved encodings

- `NOP` = `ADD R0, R0, R0` = `0x0000` (1 word). Any instruction with `Rd = R0`
  is a natural NOP-like encoding (write discarded).
- **Reserved encodings execute as `NOP`.** See §11 for the full accounting
  (39 505 legal / 26 031 reserved patterns).

---

## 3. Opcode Map (unchanged from v2.0)

| Hex | Binary | Mnemonic | Type | ALU func |
|:--:|:--|:--|:--|:--|
| 0 | `0000` | `ADD`  | ALU (reg) | `000` |
| 1 | `0001` | `ADDI` | ALU (imm) | `000` |
| 2 | `0010` | `XOR`  | ALU (reg) | `001` |
| 3 | `0011` | `XORI` | ALU (imm) | `001` |
| 4 | `0100` | `SUB`  | ALU (reg) | `010` |
| 5 | `0101` | `AND`  | ALU (reg) | `011` |
| 6 | `0110` | `OR`   | ALU (reg) | `100` |
| 7 | `0111` | `SLL`  | ALU (reg) | `101` |
| 8 | `1000` | `SRL`  | ALU (reg) | `110` |
| 9 | `1001` | `LD`   | word load | – |
| A | `1010` | `ST`   | word store | – |
| B | `1011` | `JMP` + **group B** | control / 2-operand imm | – |
| C | `1100` | `BEQ`  | branch | – |
| D | `1101` | `BNE`  | branch | – |
| E | `1110` | `BLT`  | branch (signed) | – |
| F | `1111` | `LDI` + **group F** | constants / byte memory | – |

**Corrected decode properties (v2.0 errata):**

- ALU opcodes are `op ≤ 8` — **not** `op[3]==0`; `SRL` (0x8) has `op[3]=1`.
- Immediate mode is `imm_en = (op == 4'h1) || (op == 4'h3)` — **not**
  `opcode[0]` (`AND`=5, `SLL`=7 have `op[0]=1` but are register ops).
- ALU function is **not** `op[2:0]`; the correct derivation is in §9.1 and
  matches the §3 table above and `Decoder.scala`.

### 3.1 Sub-opcode banks at a glance

```
op = 0x4 : f3 = 001 → SLT      op = 0x8 : f3 = 001 → SRA
           f3 = 010 → SLTU                 other f3 → NOP

op = 0xB : cf = inst[8:6]
           000 + A=Rs + payload=0                  → JMP Rs
           001 + A=Rlink + payload=Rtarget||000   → CALL Rlink, Rtarget
           010 + A=000 + payload=0                → HALT
           011..111                               → ANDI/ORI/SLLI/SRLI/SRAI (Rd = inst[11:9])

op = 0xF : b8 = inst[8], mf = inst[7:6]
           b8=1                 → LDI8 Rd, #imm8
           b8=0, mf=00, p=0     → LDI Rd, #imm16   (2-word, v2.0)
           b8=0, mf=01          → LDB  Rd, [Rs + off3]
           b8=0, mf=10          → STB  Rd, [Rs + off3]
           everything else      → NOP
```

---

## 4. Instruction Formats

### 4.1 Three-register ALU (+ funct3 sub-opcode, bank A)

```
15 14 13 12 | 11 10  9 | 8  7  6 | 5  4  3 | 2  1  0
   OPCODE   |   Rd    |   Rs    |   Rt    |   f3
```

- `f3 = 000` → the v2.0 register op (`ADD`, `XOR`, `SUB`, `AND`, `OR`,
  `SLL`, `SRL`) — unchanged.
- `f3 ≠ 000` → v2.1 extension, decoded per opcode (§3.1); all other
  combinations are reserved → `NOP`.
- Only opcodes 0, 2, 4, 5, 6, 7, 8 use this format (opcodes 1, 3 use 4.2).

### 4.2 Immediate ALU (unchanged)

```
15 14 13 12 | 11 10  9 | 8  7  6 | 5  4  3  2  1  0
   OPCODE   |   Rd    |   Rs    |     imm6 (sign-extended)
```

`ADDI`, `XORI` — all 64 immediate values legal, no funct field exists here
(this is why `ANDI` cannot live at opcode 5 and must use bank B; see §10.2).

### 4.3 Load / Store (unchanged)

```
15 14 13 12 | 11 10  9 | 8  7  6 | 5  4  3  2  1  0
   OPCODE   |   Rd    |   Rs    |     offset6
```

`LD Rd,[Rs+off]` · `ST Rd,[Rs+off]` · effective address `= R[Rs] + sext(off6)`,
bit 0 ignored (§2.3), offset in **bytes** (–32..+31). All 64 values legal.

### 4.4 Branches (unchanged)

```
15 14 13 12 | 11 10  9 | 8  7  6 | 5  4  3  2  1  0
   OPCODE   |   Rs    |   Rt    |     offset6
```

`PC ← PC_next + sext(off6)*2`, offset in instructions (–32..+31).

### 4.5 Group B — control & 2-operand immediate (opcode `0xB`)

```
15 14 13 12 | 11 10  9 | 8  7  6 | 5  4  3  2  1  0
   1  0  1  1|    A    |    cf   |     payload
```

| cf | A | payload | Instruction | Words |
|:--:|:--:|:--|:--|:--:|
| `000` | Rs | `000000` | **`JMP Rs`** (v2.0, unchanged) | 1 |
| `001` | Rlink | `Rtarget` `‖` `000` | **`CALL Rlink, Rtarget`** | 1 |
| `010` | `000` | `000000` | **`HALT`** | 1 |
| `011` | Rd | imm6 | **`ANDI Rd, #imm6`** | 1 |
| `100` | Rd | imm6 | **`ORI Rd, #imm6`** | 1 |
| `101` | Rd | imm6 | **`SLLI Rd, #imm6`** | 1 |
| `110` | Rd | imm6 | **`SRLI Rd, #imm6`** | 1 |
| `111` | Rd | imm6 | **`SRAI Rd, #imm6`** | 1 |
| `000` | any | ≠0 | reserved → `NOP` | 1 |
| `001` | any | `[2:0]≠000` | reserved → `NOP` | 1 |
| `010` | ≠000 or payload ≠0 | | reserved → `NOP` | 1 |

Notes:
- The immediate ops are **2-operand (in-place)**: `Rd ← Rd op imm`. This is
  forced by the encoding budget: 4 (opcode) + 3 (Rd) + 3 (cf) + 6 (imm) = 16
  exactly, leaving no field for `Rs`. The 3-operand source form is a
  pseudo-op (§7).
- Logic immediates (`ANDI`, `ORI`) are **zero-extended** (0..63): masks and
  flag bits are unsigned quantities. `ADDI`/`XORI` remain sign-extended as in
  v2.0. Wider masks: `LDI8` + register `AND`/`OR` (§8.3); high-bit clearing
  (`& ~3`): `SRLI #k; SLLI #k` (§10.3).
- Shift immediates use `imm6 & 0xF` as the shift amount (upper bits ignored).

### 4.6 Group F — constants & byte memory (opcode `0xF`)

```
15 14 13 12 | 11 10  9 |  8  | 7  6 | 5  4  3 | 2  1  0
   1  1  1  1|   Rd    | b8  |  mf  |  payload...
```

| b8 | mf | payload | Instruction | Words |
|:--:|:--:|:--|:--|:--:|
| `1` | – | imm8 = `inst[7:0]` | **`LDI8 Rd, #imm8`** — `Rd ← zext(imm8)` | 1 |
| `0` | `00` | `000000` | **`LDI Rd, #imm16`** (v2.0, followed by imm16 word) | 2 |
| `0` | `01` | Rs `‖` off3 | **`LDB Rd, [Rs + zext(off3)]`** | 1 |
| `0` | `10` | Rs `‖` off3 | **`STB Rd, [Rs + zext(off3)]`** | 1 |
| `0` | `00` | ≠0 | reserved → `NOP` | 1 |
| `0` | `11` | any | reserved → `NOP` | 1 |

- `LDB`: `Rd ← zext8(M8[A])` (zero-extended; sign-extend if needed with
  `SLLI Rd,#8; SRAI Rd,#8`).
- `STB`: `M8[A] ← R[Rd][7:0]`.
- `A = R[Rs] + zext(off3)` — byte offset 0..7, no alignment rule.
- Field roles (`Rd` = destination for loads, source for stores) match v2.0
  `LD`/`ST` exactly.

### 4.7 `LDI` — 2-word (unchanged)

Word 1: `1111 | Rd | 0 | 00 | 000000` · Word 2: any 16-bit immediate.
`PC ← PC + 4`.

---

## 5. Detailed Semantics

### 5.1 v2.0 instructions (unchanged)

| Instruction | Operation |
|:--|:--|
| `ADD  Rd,Rs,Rt` | `R[Rd] = R[Rs] + R[Rt]` |
| `ADDI Rd,Rs,#imm6` | `R[Rd] = R[Rs] + sext(imm6)` |
| `XOR  Rd,Rs,Rt` | `R[Rd] = R[Rs] ^ R[Rt]` |
| `XORI Rd,Rs,#imm6` | `R[Rd] = R[Rs] ^ sext(imm6)` |
| `SUB  Rd,Rs,Rt` | `R[Rd] = R[Rs] - R[Rt]` |
| `AND  Rd,Rs,Rt` | `R[Rd] = R[Rs] & R[Rt]` |
| `OR   Rd,Rs,Rt` | `R[Rd] = R[Rs] \| R[Rt]` |
| `SLL  Rd,Rs,Rt` | `R[Rd] = R[Rs] << (R[Rt] & 0xF)` |
| `SRL  Rd,Rs,Rt` | `R[Rd] = R[Rs] >> (R[Rt] & 0xF)` (logical) |
| `LD   Rd,[Rs+off]` | `R[Rd] = Mword[ R[Rs] + sext(off) ]` (bit 0 of addr ignored) |
| `ST   Rd,[Rs+off]` | `Mword[ R[Rs] + sext(off) ] = R[Rd]` |
| `JMP  Rs` | `PC = R[Rs]` (fetch uses `PC[15:1]`) |
| `BEQ/BNE/BLT Rs,Rt,off` | conditional, `PC += sext(off)*2` on already-incremented PC |
| `LDI  Rd,#imm16` | `R[Rd] = imm16`; `PC += 4` |

All ALU ops are modulo 2¹⁶, no flags. `BLT` is signed.

### 5.2 `NOP`

`ADD R0,R0,R0`. Completes in one step, writes discarded, `PC += 2`.
**Every reserved encoding behaves exactly as `NOP`.**

### 5.3 New: register ALU extensions (bank A)

| Instruction | Encoding | Operation |
|:--|:--|:--|
| `SLT  Rd,Rs,Rt` | `0100 Rd Rs Rt 001` | `R[Rd] = (signed(R[Rs]) < signed(R[Rt])) ? 1 : 0` |
| `SLTU Rd,Rs,Rt` | `0100 Rd Rs Rt 010` | `R[Rd] = (unsigned(R[Rs]) < unsigned(R[Rt])) ? 1 : 0` |
| `SRA  Rd,Rs,Rt` | `1000 Rd Rs Rt 001` | `R[Rd] = signed(R[Rs]) >>> (R[Rt] & 0xF)` (sign-fill) |

`SLT`/`SLTU` produce exactly `0` or `1` and never set flags (there are none).

### 5.4 New: group B control

| Instruction | Operation |
|:--|:--|
| `CALL Rlink, Rtarget` | `R[Rlink] = PC_next` (address of the instruction after the CALL); `PC = R[Rtarget]`. **No memory side effect** — the return address lives in a register; nested calls MUST save `Rlink` (e.g. on the stack, §8.2). |
| `HALT` | The core stops fetching and freezes all architectural state (registers, memory, PC) until reset or an enabled interrupt. With no interrupt support in the platform, only reset exits `HALT`. Debug/bus reset (cmd `0x03`) exits `HALT` on this SoC. |

### 5.5 New: group B immediate (2-operand)

| Instruction | Operation |
|:--|:--|
| `ANDI Rd,#imm6` | `R[Rd] = R[Rd] & zext(imm6)` |
| `ORI  Rd,#imm6` | `R[Rd] = R[Rd] \| zext(imm6)` |
| `SLLI Rd,#imm6` | `R[Rd] = R[Rd] << (imm6 & 0xF)` |
| `SRLI Rd,#imm6` | `R[Rd] = R[Rd] >> (imm6 & 0xF)` (logical) |
| `SRAI Rd,#imm6` | `R[Rd] = signed(R[Rd]) >>> (imm6 & 0xF)` |

`ANDI R0, …` / `ORI R0, …` etc. write `R0` → discarded → `NOP`.

### 5.6 New: group F

| Instruction | Operation |
|:--|:--|
| `LDI8 Rd,#imm8` | `R[Rd] = zext8(imm8)`; `PC += 2` |
| `LDB Rd,[Rs+off3]` | `R[Rd] = zext8(M8[ R[Rs] + zext(off3) ])` |
| `STB Rd,[Rs+off3]` | `M8[ R[Rs] + zext(off3) ] = R[Rd][7:0]` |

---

## 6. Complete Instruction Reference (29 instructions)

| Mnemonic | Encoding (16 bits) | Operands | Words |
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
| `SLT`  | `0100 Rd Rs Rt 001` | `Rd, Rs, Rt` *(new)* | 1 |
| `SLTU` | `0100 Rd Rs Rt 010` | `Rd, Rs, Rt` *(new)* | 1 |
| `SRA`  | `1000 Rd Rs Rt 001` | `Rd, Rs, Rt` *(new)* | 1 |
| `LD`   | `1001 Rd Rs off6` | `Rd, [Rs + off6]` | 1 |
| `ST`   | `1010 Rd Rs off6` | `Rd, [Rs + off6]` | 1 |
| `JMP`  | `1011 Rs 000 000000` | `Rs` | 1 |
| `CALL` | `1011 Rlink 001 Rtarget 000` | `Rlink, Rtarget` *(new)* | 1 |
| `HALT` | `1011 000 010 000000` | – *(new)* | 1 |
| `ANDI` | `1011 Rd 011 imm6` | `Rd, #imm6` (zext) *(new)* | 1 |
| `ORI`  | `1011 Rd 100 imm6` | `Rd, #imm6` (zext) *(new)* | 1 |
| `SLLI` | `1011 Rd 101 imm6` | `Rd, #imm6` *(new)* | 1 |
| `SRLI` | `1011 Rd 110 imm6` | `Rd, #imm6` *(new)* | 1 |
| `SRAI` | `1011 Rd 111 imm6` | `Rd, #imm6` *(new)* | 1 |
| `BEQ`  | `1100 Rs Rt off6` | `Rs, Rt, off6` | 1 |
| `BNE`  | `1101 Rs Rt off6` | `Rs, Rt, off6` | 1 |
| `BLT`  | `1110 Rs Rt off6` | `Rs, Rt, off6` (signed) | 1 |
| `LDI`  | `1111 Rd 0 00 000000` + imm16 | `Rd, #imm16` | 2 |
| `LDI8` | `1111 Rd 1 imm8` | `Rd, #imm8` *(new)* | 1 |
| `LDB`  | `1111 Rd 0 01 Rs off3` | `Rd, [Rs + off3]` *(new)* | 1 |
| `STB`  | `1111 Rd 0 10 Rs off3` | `Rd, [Rs + off3]` *(new)* | 1 |
| `NOP`  | `0000 000 000 000 000` | – (alias of `ADD R0,R0,R0`) | 1 |

Every instruction except `LDI` is exactly one word.

### 6.1 Density comparison (v2.0 → v2.1)

| Idiom | v2.0 | v2.1 |
|:--|:--|:--|
| load constant 0..255 | `LDI` (2 words) | `LDI8` (**1 word**) |
| `x &= mask` (mask ≤ 0x3F) | `LDI`+`AND` (3 words + temp reg) | `ANDI` (**1 word**) |
| `x \|= flag` (flag ≤ 0x3F) | `LDI`+`OR` (3 words + temp) | `ORI` (**1 word**) |
| `x <<= k` / `>>= k` | `LDI`+`SLL` (3 words + temp) | `SLLI`/`SRLI` (**1 word**) |
| `y = x < z` (signed) | impossible | `SLT` (**1 word**) |
| unsigned compare | multi-instruction sign trick | `SLTU` + branch (2 words) |
| arithmetic `>>` | impossible | `SRA` (**1 word**) |
| call + return address | no link mechanism at all | `CALL` (**1 word**) |
| read/write a byte | impossible (word only) | `LDB`/`STB` (**1 word**) |
| sleep / stop | impossible | `HALT` (**1 word**) |

---

## 7. Assembler Syntax

### 7.1 Mnemonics

Unchanged v2.0 syntax, plus:

```
SLT   Rd, Rs, Rt          ; signed set-less-than
SLTU  Rd, Rs, Rt          ; unsigned set-less-than
SRA   Rd, Rs, Rt          ; arithmetic right shift
ANDI  Rd, #imm            ; in-place:  Rd = Rd & zext(imm), imm in [0,63]
ORI   Rd, #imm            ; in-place:  Rd = Rd | zext(imm), imm in [0,63]
SLLI  Rd, #imm            ; in-place:  Rd = Rd << (imm & 0xF)
SRLI  Rd, #imm            ; in-place:  Rd = Rd >> (imm & 0xF)
SRAI  Rd, #imm            ; in-place:  Rd = Rd >>> (imm & 0xF)
CALL  Rlink, Rtarget      ; Rlink = return address, jump to Rtarget
HALT                      ; stop until reset/interrupt
LDI8  Rd, #imm            ; explicit 1-word form, imm in [0,255]
LDB   Rd, [Rs + off]      ; byte load,  off in [0,7]
STB   Rd, [Rs + off]      ; byte store, off in [0,7]
```

`LDI Rd, #imm` keeps its meaning; the assembler **auto-selects** the 1-word
`LDI8` encoding when `0 ≤ imm ≤ 255`, and the 2-word form otherwise.

### 7.2 Pseudo-ops (expanded by the assembler)

Temporaries default to `R4`, overridable with a `.scratch Rn` directive.

| Pseudo | Expansion | Size |
|:--|:--|:--|
| `MOV Rd, Rs` | `ADD Rd, Rs, R0` | 1 |
| `NEG Rd, Rs` | `SUB Rd, R0, Rs` | 1 |
| `NOT Rd, Rs` | `XORI Rd, Rs, #-1` | 1 |
| `CLR Rd` | `XOR Rd, Rd, Rd` | 1 |
| `LSL/LSR/ASR Rd, Rs, Rt` | `SLL/SRL/SRA Rd, Rs, Rt` | 1 |
| `ANDI Rd, Rs, #imm` (3-op) | `ADD Rd, Rs, R0` + `ANDI Rd, #imm` (skip copy if `Rs==Rd`) | 1–2 |
| `ORI Rd, Rs, #imm` (3-op) | same pattern | 1–2 |
| `SLLI/SRLI/SRAI Rd, Rs, #imm` (3-op) | same pattern | 1–2 |
| `B label` | `BEQ R0, R0, label` | 1 (+relaxation) |
| `BGT Rs, Rt, L` | `BLT Rt, Rs, L` | 1 |
| `BGE Rs, Rt, L` | `SLT tmp,Rs,Rt` + `BEQ tmp,R0,L` | 2 |
| `BLE Rs, Rt, L` | `SLT tmp,Rt,Rs` + `BEQ tmp,R0,L` | 2 |
| `BLTU Rs, Rt, L` | `SLTU tmp,Rs,Rt` + `BNE tmp,R0,L` | 2 |
| `BGEU Rs, Rt, L` | `SLTU tmp,Rs,Rt` + `BEQ tmp,R0,L` | 2 |
| `BLEU Rs, Rt, L` | `SLTU tmp,Rt,Rs` + `BEQ tmp,R0,L` | 2 |
| `RET Rlink` | `JMP Rlink` | 1 |
| `JMP label` | `LDI R4,#label` + `JMP R4` (existing; shrinks to 2 words if label < 256) | 2–3 |

`BGE`/`BLE` may alternatively expand without a scratch register as
`BLT Rs,Rt,skip; B label; skip:` (also 2 words) — assembler option.

### 7.3 Notation

Immediates: decimal, `0x…`, `0b…` as in v2.0. Offsets: bytes for `LD/ST`,
instructions for branches, bytes for `LDB/STB`.

---

## 8. Programming Examples

### 8.1 Sum of a 10-word array (v2.0 Example 1, corrected)

v2.0's version contained a dead `LDI R7, #loop` (the loop is a relative
branch) and paid 2 words per constant.

```
    LDI  R1, #0x1000       ; base address (2 words, > 255)
    LDI  R2, #10           ; counter  → LDI8, 1 word
    LDI  R3, #0            ; sum      → LDI8, 1 word

loop:
    LD   R4, [R1 + 0]
    ADD  R3, R3, R4
    ADDI R1, R1, #2
    ADDI R2, R2, #-1
    BNE  R0, R2, loop
    ; result in R3
```

Size: v2.0 = 13 words (26 bytes) → v2.1 = **9 words (18 bytes), −31 %**.

### 8.2 Recursive sum with `CALL`/`JMP` (link register discipline)

```
; int sum(int n): R1 = n + (n-1) + ... + 1,  sum(0) = 0
; R5 = link register, R6 = stack pointer, R7 = scratch

        LDI  R6, #0x1000     ; stack top (grows down)
        LDI  R1, #10
        LDI  R7, #sum        ; LDI8 (1 word) if sum < 256
        CALL R5, R7          ; R5 = return address
        HALT                 ; result in R1 (= 55)

sum:    BEQ  R0, R1, ret     ; n == 0 → return (R1 already 0)
        ADDI R6, R6, #-4     ; frame
        ST   R5, [R6 + 0]    ; save link  (nested CALL would clobber R5)
        ST   R1, [R6 + 2]    ; save n
        ADDI R1, R1, #-1     ; n-1
        LDI  R7, #sum
        CALL R5, R7          ; R1 = sum(n-1)
        LD   R4, [R6 + 2]    ; n
        ADD  R1, R1, R4      ; + n
        LD   R5, [R6 + 0]    ; restore link
        ADDI R6, R6, #4      ; pop frame
        JMP  R5              ; return
ret:    JMP  R5
```

v2.0 had no way to obtain the return address at all; v2.1 does it in one word.

### 8.3 Masks, flags, shifts (group B immediates, in-place)

```
        LDI  R1, #0xAB35     ; data
        ANDI R1, #0x3F       ; keep low 6 bits   (1 word; impossible in v2.0)
        SLLI R1, #4          ; field << 4        (1 word)
        ORI  R1, #0x20       ; set flag bit 5    (1 word)

        ; mask wider than 6 bits (e.g. keep low byte):
        LDI  R3, #0xFF       ; LDI8, 1 word
        AND  R1, R1, R3      ; register AND      (2 words total, 1 temp)

        ; clear low 2 bits (word-align) without an 8-bit mask:
        SRLI R1, #2
        SLLI R1, #2          ; R1 &= 0xFFFC       (2 words, no temp)
```

### 8.4 Byte I/O with `LDB`/`STB`

```
        LDI  R1, #0x1FFC     ; input port  (platform: e.g. UART RX)
        LDI  R2, #0x1FFE     ; output port (platform: e.g. UART TX)
loop:   LDB  R3, [R1 + 0]    ; read byte, zero-extended
        STB  R3, [R2 + 0]    ; echo byte
        BEQ  R0, R0, loop    ; unconditional back-edge
```

Byte loads/stores need no alignment and touch exactly one byte lane —
impossible in v2.0, which could only do read-modify-write word accesses.

### 8.5 Signed halving with `SRA`

```
        LDI  R1, #1000       ; counter (works for negative values too)
        LDI  R3, #1          ; shift amount
tick:   SRA  R2, R1, R3      ; R2 = R1 >>> 1  (arithmetic: -4 → -2, keeps sign)
        ADDI R1, R1, #-1
        BNE  R0, R1, tick
```

---

## 9. Implementation Notes (Verilog/SystemVerilog)

### 9.1 Corrected decode (v2.0 §7.1 errata)

```verilog
wire [3:0] op = inst[15:12];

// v2.0 ALU group: opcodes 0x0..0x8  (NOT op[3]==0 — SRL is 0x8)
wire is_alu_v20 = (op <= 4'h8);

// ALU function — CORRECTED: matches §3 table and Decoder.scala
//   opc < 4  → aluFunc = opc >> 1   (0→000 ADD, 1→000 ADDI, 2→001 XOR, 3→001 XORI)
//   opc >= 4 → aluFunc = opc -  2   (4→010 SUB, 5→011 AND, 6→100 OR,
//                                     7→101 SLL, 8→110 SRL)
wire [2:0] alu_func_v20 = (op < 4) ? {2'b00, op[1]}     // = low 3 bits of (op >> 1)
                                   : (op[2:0] - 3'd2);   // 8: 000-2 = 110 wraps correctly

// Immediate mode: NOT opcode[0]
wire imm_en = (op == 4'h0001) || (op == 4'h0011);
wire [15:0] operand_b = imm_en ? { {10{inst[5]}}, inst[5:0] }
                               : regfile[inst[5:3]];
```

### 9.2 Bank A — funct3 sub-opcodes

```verilog
wire [2:0] f3 = inst[2:0];
wire is_SLT  = (op == 4'h4) && (f3 == 3'b001);
wire is_SLTU = (op == 4'h4) && (f3 == 3'b010);
wire is_SRA  = (op == 4'h8) && (f3 == 3'b001);
// any other f3 != 000 on op <= 8 → reserved → reg_we = 0
```

### 9.3 Bank B — group `0xB`

```verilog
wire [2:0] cf  = inst[8:6];
wire [2:0] rdA = inst[11:9];

wire jmp_v21  = (cf == 3'b000) && (inst[5:0] == 6'b0);
wire call_v21 = (cf == 3'b001) && (inst[2:0] == 3'b000);
wire halt_v21 = (cf == 3'b010) && (rdA == 3'b000) && (inst[5:0] == 6'b0);
wire immB_v21 = (cf >= 3'b011);           // ANDI/ORI/SLLI/SRLI/SRAI

// ALU function selection for group-B immediates (reuse v2.0 codes + new SRA)
//   cf 011 → 011 (AND)   cf 100 → 100 (OR)    cf 101 → 101 (SLL)
//   cf 110 → 110 (SRL)   cf 111 → new code (SRA)
// operand A = regfile[inst[11:9]] (Rd read in place), operand B = {10'b0, inst[5:0]}
wire [15:0] operandB_B = {10'b0, inst[5:0]};   // ZERO-extended (masks)
```

### 9.4 Bank F — group `0xF`

```verilog
wire b8   = inst[8];
wire [1:0] mf = inst[7:6];
wire ldi8_v21 =  b8;                                 // 1-word constant
wire ldi2_v20 = !b8 && (mf == 2'b00) && (inst[5:0] == 6'b0);
wire ldb_v21  = !b8 && (mf == 2'b01);
wire stb_v21  = !b8 && (mf == 2'b10);
// decoder.isLDI (2-word fetch) must now be gated: isLDI = opc==0xF && ldi2_v20
```

### 9.5 ALU function allocation

v2.0 used 7 of 8 three-bit ALU codes (`111` was the only spare). v2.1 needs
three new ALU operations, so **`aluFunc` must be widened to 4 bits** (or get
sideband controls). Suggested internal map — *not architecturally visible*:

| Code | Op | | Code | Op |
|:--:|:--|---|:--:|:--|
| `0000` | ADD | | `0100` | OR |
| `0001` | XOR | | `0101` | SLL |
| `0010` | SUB | | `0110` | SRL |
| `0011` | AND | | `0111` | (v2.0 "zero" spare) |
| `1000` | **SLT** (new) | | `1001` | **SLTU** (new) |
| `1010` | **SRA** (new) | | | |

The 4-bit map is the v2.0 3-bit code zero-extended, so existing decode logic
is unchanged.

Notes: `SLT` reuses the branch comparator's signed `less`; `SLTU` needs an
unsigned compare (sign-extend both operands to 17 bits and compare, or a
second carry-out compare); `SRA` extends the existing shifter with sign fill.

### 9.6 Datapath / FSM deltas

- **`Core.scala`**: `CALL` = write `PC_next` to Rlink + jump (like `JMP`
  plus a regfile write); `HALT` = new terminal state until reset; `LDI8`
  skips `LDI_FETCH` (PC += 2, not 4); group-B immediates classified as
  ALU-type (`reg_we = 1`, no memory/branch).
- **`PipCore.scala`**: same decode deltas; `InstrType` needs the group-B
  immediates to ride the ALU path (fold into `InstrType.ALU` with
  `operandB`/`aluFunc` preselected in ID); `HALT` freezes the fetch stage;
  `LDI8` must not enter the 2-word LDI state machine.
- **`Soc.scala` / `PipSoc.scala`** (and BusInterface): byte lane support —
  word RAM writes gain a 2-bit mask (`Mem.write(..., mask)`); `LDB` muxes
  `Mword[7:0]`/`Mword[15:8]` by address bit 0; `STB` drives the byte in the
  low lane (UART TX already takes `wrData[7:0]`, so `STB` to `0x1FFE` works
  unchanged).
- **Reset**: nothing to do — `RegFile` already resets to 0, `PC` inits to 0.

---

## 10. Rationale

### 10.1 Why banks, and why these three

v2.0 filled all 16 opcodes yet left 26 104 encodings *inside* formats unused
(padding fields marked "must be zero": 25 088 reg-ALU funct3 slots, 504 `JMP`
payloads, 512 `LDI` word-1 payloads) while claiming "no room for expansion".
v2.1 exploits exactly those holes, so **no opcode, no format, and no existing
binary changes**. Bank A gives register-register extensions (funct3 was free),
bank B gives control ops (JMP wasted 9 bits) and immediate ALU ops, bank F
gives constants and byte access (LDI wasted 9 bits in word 1).

### 10.2 Why the immediate ops are 2-operand

A 3-operand immediate instruction needs `op(4) + Rd(3) + Rs(3) + funct(3) +
imm(?)` — even a 1-bit funct leaves only 5 immediate bits, and the funct bits
cannot be carved out of `imm6` without colliding with `JMP`'s decode. Putting
`Rd` in the free A-field and `funct ‖ imm6` in the free payload gives
`4+3+3+6 = 16` exactly: a clean 3-way split with **no ambiguous encoding**
(the `cf=000, payload=0` cell remains `JMP`). In-place form is also the
*usual* form in compiler output for `x = x & mask`, and the 3-operand
expansion costs one extra word with **no extra register** — versus v2.0's
3 words plus a register for the same operation.

`SLTI` was considered for the same bank and **rejected**: a destructive
2-operand compare destroys the loop variable in the single most common use
(`i < k`), so it would save nothing; `LDI8` + `SLT` + branch (2 words, one
temp) remains correct.

### 10.3 Why zero-extension for `ANDI`/`ORI`

With only 6 bits, extension choice matters: sign-extension would make masks
`0xFFE0..0xFFFF` (clear-low-bits) available but no mask above `0x1F`;
zero-extension makes `0x00..0x3F` available (`0x0F`, `0x1F`, `0x3F` — the
common field masks) plus flag bits up to `0x20`. Clearing low bits is covered
by `SRLI k; SLLI k` (2 words, no temp), and anything wider by `LDI8` +
register op (2 words). Masks are conceptually unsigned; arithmetic
immediates stay sign-extended as in v2.0.

### 10.4 Why byte ops live under opcode `0xF`

`LD`/`ST` (0x9/0xA) are bit-exhausted: all 16 bits carry `op|Rd|Rs|off6`, and
`off6` parity cannot encode a byte/word choice (parity of the *effective*
address depends on the runtime base). Opcode `0xF`'s word-1 padding is the
only place left with a free marker bit plus `Rs` + offset fields — so
`LDI`'s group becomes "constants & byte memory". The mnemonic split (`LDI8`,
`LDB`, `STB`) keeps each sub-opcode obvious at the source level.

### 10.5 Why `CALL` is register-target, not PC-relative

A PC-relative call needs `link(3) + offset(≥6)`, but `cf` occupies 3 of the
9 free bits, leaving at most 6 payload bits (±32 instructions) — useless once
functions spread beyond 64 bytes. `CALL Rlink, Rtarget` reaches anywhere, at
the cost of one `LDI`/`LDI8` to materialise the target (2 words / 4 bytes for
targets < 256), and mirrors `JMP Rs` so the decoder reuses the same
target-mux.

### 10.6 Why `NOP` for reserved encodings

v2.0 said only "must be zero" — a conforming behaviour for such words was
undefined, and the natural RTL default (ignore `f3`, decode the base op)
executes a *different instruction*. Specifying "reserved = NOP" makes
illegal-word behaviour deterministic, formally checkable
(`assert reserved |-> no_side_effects`), and safe against corrupted memory.

### 10.7 Corrections to v2.0's rationale

- §8.1: negation emulation is **2** instructions (`XORI Rd,Rs,#-1` +
  `ADDI Rd,Rd,#1`), and `NEG Rd,Rs` = `SUB Rd,R0,Rs` is **1** — the point
  (SUB reuses the subtractor) still stands.
- §8.2: `XORI #-1` is not the "only way" to get −1; `LDI/ADDI Rd,R0,#-1`
  also works. `ANDI`/`ORI` now cover the mask/flag constants that `XORI`
  could not express.

---

## 11. Encoding Space Accounting

All 65 536 16-bit patterns classified:

| Opcode(s) | Total | Legal | Reserved (→ NOP) |
|:--|--:|--:|--:|
| 0,2,4,5,6,7,8 (reg-ALU) | 28 672 | 5 120 | 23 552 |
| 1,3 (ADDI/XORI) | 8 192 | 8 192 | 0 |
| 9,A (LD/ST) | 8 192 | 8 192 | 0 |
| B (group B) | 4 096 | 2 633 | 1 463 |
| C,D,E (branches) | 12 288 | 12 288 | 0 |
| F (group F) | 4 096 | 3 080 | 1 016 |
| **Total** | **65 536** | **39 505** | **26 031** |

Breakdown of new-pattern legality:

- Bank A: 7 f3 slots per reg-ALU opcode × 512 field combinations = 25 088
  padding patterns → **1 536** legal (`SLT` 512, `SLTU` 512, `SRA` 512).
- Bank B: `CALL` 64, `HALT` 1, five immediates × 8 × 64 = 2 560, `JMP` 8 →
  2 633 legal; malformed `CALL`/`HALT` and `cf=000,payload≠0` stay reserved.
- Bank F: `LDI8` 2 048, `LDB` 512, `STB` 512, 2-word `LDI` 8 → 3 080 legal.

---

## 12. Compatibility, Migration & Verification

### 12.1 Compatibility

1. **Bit-exact:** all v2.0-legal encodings keep their meaning (§0).
2. **Source-exact:** v2.0 assembly reassembles to identical semantics;
   `LDI Rd,#n` with `n ≤ 255` may emit the shorter 1-word form (all labels
   re-resolved by the two-pass assembler; existing prebuilt `.hex` files are
   untouched and still run).
3. **Emulator:** `emulator/main.cpp` and `pipsoc-emu` drive the Verilated
   RTL — no ISA-model changes expected; only new instructions must exist in
   RTL before tests use them.

### 12.2 RTL deltas (by file)

| File | Change |
|:--|:--|
| `Decoder.scala` | banks A/B/F sub-decode (§9.2–9.4); `isLDI` gated to the 2-word form; reserved → NOP gating of `reg_we`; group-B immediates classified ALU-type with `aluFunc`/operand-B preselect |
| `ALU.scala` | widen `aluFunc` to 4 bits; add SLT, SLTU, SRA (§9.5) |
| `Core.scala` | `CALL` (link write + jump), `HALT` state, `LDI8` single-word path |
| `PipCore.scala` | same, folded into `InstrType`/ID-EX; freeze fetch on `HALT`; skip LDI FSM for `LDI8` |
| `BusInterface.scala` | byte write strobe (2-bit mask) for `STB` |
| `Soc.scala`, `PipSoc.scala` | RAM byte mask; `LDB` byte-lane mux by `addr[0]` |
| `asm.py` | encoders for 13 instructions; `LDI` auto-narrowing; pseudo-ops (§7.2); relaxation unchanged |
| `cc.py` | optional peephole: emit `ANDI/ORI/SLLI/SRLI/SRAI`, use `LDB/STB` for `char`, `SLT` for comparisons |
| formal tests | new TCs below |

### 12.3 Verification results (implemented)

- **Regression gate:** all 41 C tests pass on both `Soc` and `PipSoc`
  (no v2.0 behavior moved).
- **New ISA-level asm tests** (`run_tests.py`, 7 programs, both cores):
  `slt_test`, `sra_test`, `imm_test`, `call_test`, `byte_test`,
  `ldi8_rsvd_test` (LDI8 auto-narrow + reserved-word-is-NOP + HALT),
  `bge_test` (branch pseudo on equality) — 48/48 total.
- **Formal (per-component BMC):** `ALU`, `Decoder`, `Core`, `PipCore` at
  depth 30; `Soc`, `PipSoc` at depth 10 — all pass, including:
  - `SLT/SLTU/SRA`: result equals reference signed/unsigned/arithmetic
    computation (exhaustive over operand encodings in the ALU harness).
  - `CALL`: link `== PC+2`, target rides the jmp mux, no memory write.
  - `HALT`: state frozen until a bus command (`PC |-> stable`).
  - `ANDI/ORI/SLLI/SRLI/SRAI`: zero-extension, in-place read/write.
  - `LDI8`: `Rd == {8'b0, imm8}`; 2-word `LDI` decode unchanged.
  - `LDB/STB`: byte-lane selection by `addr[0]`, `LDB` zero-extension,
    no write to the other lane on `STB` (SoC write-mask TC).
  - **Reserved-is-NOP** and **v2.0-compat** decode equality (Decoder TCs).
- **Pipeline hazard:** adjacent loads (`LDB`/`LD` back-to-back) fixed by
  `ldIssueStall` — ID holds while an EX load's request has not yet been
  captured by the LD FSM (`ldState == IDLE`).

---

## 13. Reserved Space & Future Extensions

| Location | Count | Candidates |
|:--|--:|:--|
| Bank A, unused f3 (op 0,2,5,6,7 + op4/8 extras) | 23 552 | `MUL` (f3=001 under `ADD`), byte-swap/`REV` (under `XOR`), `NOR`, future 3-reg ops |
| Bank B, `cf=000, payload≠0` | 504 | a second 2-operand immediate (would need a non-conflicting funct split) |
| Bank B, malformed `CALL` (`cf=001, payload[2:0]≠0`) | 448 | two-register control ops |
| Bank B, `cf=010` with `A≠000`/payload≠0 | 511 | one 2-operand immediate (e.g. a future `SUBI`-style op if ever wanted) |
| Bank F, `mf=11` | 512 | `LDBS` (sign-extending byte load), `SWAP`/`REV16`, halfword ops |
| Bank F, `mf=00, payload≠0` | 504 | sign-extended 6-bit constant load (`LDIS`) |
| — | — | **Interrupts/traps** need a vector + saved-PC model first (`TRAP`, `RETI`, interrupt-enable bit); a `WFI`-style variant could join `HALT`'s encoding family |

Instruction-count ledger: v2.0 = 16 architectural instructions; v2.1 = **29**
(+ documented `NOP`), with 39 505 legal encodings and a fully specified
legalisation rule for everything else.

---

*This v2.1 revision keeps v2.0's hardware-friendly decode, closes its
documented errata, and adds the instructions a microcontroller actually
needs (masks, immediate shifts, calls, unsigned/byte access) — all inside the
existing 16-bit format and without invalidating a single existing binary.*
