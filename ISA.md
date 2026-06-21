# 16-bit RISC Microcontroller ISA Specification

**Version 2.0 – Optimized for Verilog Implementation**  
**Instruction width:** 16 bits (fixed)  
**Data width:** 16 bits  
**Address space:** 16‑bit byte‑addressable (64 KB)  
**Registers:** 8 general‑purpose registers `R0`–`R7`, each 16 bits  
**PC:** 16‑bit byte address, increments by 2 (except `LDI`, which advances by 4)

---

## 1. Design Philosophy

- **Hardware‑friendly opcode assignment:**  
  ALU operations share identical function codes; only the `imm_en` flag toggles between register and immediate operand.  
- **Minimal yet practical:**  
  Contains all essential operations for microcontroller tasks (GPIO, timers, loops, bit manipulation).  
- **Full 4‑bit opcode space:** 16 instructions, no wider decode needed.

---

## 2. Opcode Map (Optimized for Decoder)

| Hex | Binary `[3:0]` | Mnemonic | Type | ALU Function |
| :--- | :--- | :--- | :--- | :--- |
| 0 | `0000` | `ADD`  | ALU (reg) | `000` |
| 1 | `0001` | `ADDI` | ALU (imm) | `000` |
| 2 | `0010` | `XOR`  | ALU (reg) | `001` |
| 3 | `0011` | `XORI` | ALU (imm) | `001` |
| 4 | `0100` | `SUB`  | ALU (reg) | `010` |
| 5 | `0101` | `AND`  | ALU (reg) | `011` |
| 6 | `0110` | `OR`   | ALU (reg) | `100` |
| 7 | `0111` | `SLL`  | ALU (reg) | `101` |
| 8 | `1000` | `SRL`  | ALU (reg) | `110` |
| 9 | `1001` | `LD`   | Memory load | – |
| A | `1010` | `ST`   | Memory store | – |
| B | `1011` | `JMP`  | Unconditional jump | – |
| C | `1100` | `BEQ`  | Branch if equal | – |
| D | `1101` | `BNE`  | Branch if not equal | – |
| E | `1110` | `BLT`  | Branch if less than (signed) | – |
| F | `1111` | `LDI`  | Load immediate 16‑bit (2‑word) | – |

> **Key optimization:**  
> - `ADD` and `ADDI` share the same ALU function `000`; `opcode[0]` selects immediate mode.  
> - `XOR` and `XORI` share `001`; `opcode[0]` selects immediate mode.  
> - All ALU opcodes have `opcode[3]==0`, which directly enables the ALU result write‑back.  
> - All memory/branch/jump opcodes have `opcode[3]==1`.

---

## 3. Instruction Formats

### 3.1 Three‑Register ALU Instructions
**Bits:** `[15:12]` opcode, `[11:9]` Rd, `[8:6]` Rs, `[5:3]` Rt, `[2:0]` unused (must be `000`)

```
15 14 13 12 | 11 10  9 | 8  7  6 | 5  4  3 | 2  1  0
  OPCODE    |   Rd    |   Rs    |   Rt    |  000
```

Used by: `ADD`, `XOR`, `SUB`, `AND`, `OR`, `SLL`, `SRL`.

---

### 3.2 Immediate ALU Instructions
**Bits:** `[15:12]` opcode, `[11:9]` Rd, `[8:6]` Rs, `[5:0]` imm6 (signed 6‑bit)

```
15 14 13 12 | 11 10  9 | 8  7  6 | 5  4  3  2  1  0
  OPCODE    |   Rd    |   Rs    |      imm6
```

Used by: `ADDI`, `XORI`.  
The 6‑bit immediate is **sign‑extended** to 16 bits before the operation.

---

### 3.3 Load/Store Instructions
**Bits:** `[15:12]` opcode, `[11:9]` Rd, `[8:6]` Rs, `[5:0]` offset (signed 6‑bit)

```
15 14 13 12 | 11 10  9 | 8  7  6 | 5  4  3  2  1  0
  OPCODE    |   Rd    |   Rs    |      offset
```

- `LD Rd, [Rs + off]` : `Rd = M[ Rs + sext(off) ]`
- `ST Rd, [Rs + off]` : `M[ Rs + sext(off) ] = Rd`
- Address must be word‑aligned (even). The offset is in **bytes**, range –64 to +62.

---

### 3.4 Branch Instructions
**Bits:** `[15:12]` opcode, `[11:9]` Rs, `[8:6]` Rt, `[5:0]` offset (signed 6‑bit)

```
15 14 13 12 | 11 10  9 | 8  7  6 | 5  4  3  2  1  0
  OPCODE    |   Rs    |   Rt    |      offset
```

- `BEQ Rs, Rt, off` : if `Rs == Rt` then `PC += sext(off)*2`
- `BNE Rs, Rt, off` : if `Rs != Rt` then `PC += sext(off)*2`
- `BLT Rs, Rt, off` : if `Rs < Rt` (signed) then `PC += sext(off)*2`
- Offset is in **instructions** (not bytes), range ±32.

---

### 3.5 Jump Instruction
**Bits:** `[15:12]` opcode, `[11:9]` Rs, `[8:0]` unused (must be zero)

```
15 14 13 12 | 11 10  9 | 8  7  6  5  4  3  2  1  0
  OPCODE    |   Rs    |        000000000
```

`JMP Rs` : `PC = Rs` (unconditional absolute jump).

---

### 3.6 Load Immediate (2‑word)
**First word:**
```
15 14 13 12 | 11 10  9 | 8  7  6  5  4  3  2  1  0
    1111    |   Rd    |        000000000
```
**Second word:** 16‑bit immediate value (any 16‑bit constant).

`LDI Rd, #imm16` : `Rd = imm16`  
The PC advances by **4 bytes** after fetching both words.

---

## 4. Detailed Semantics

### 4.1 Arithmetic & Logic
All operations are modulo 2¹⁶ (no carry/overflow flags).

| Instruction | Operation |
| :--- | :--- |
| `ADD Rd, Rs, Rt` | `R[Rd] = R[Rs] + R[Rt]` |
| `ADDI Rd, Rs, #imm6` | `R[Rd] = R[Rs] + sext(imm6)` |
| `XOR Rd, Rs, Rt` | `R[Rd] = R[Rs] ^ R[Rt]` |
| `XORI Rd, Rs, #imm6` | `R[Rd] = R[Rs] ^ sext(imm6)` |
| `SUB Rd, Rs, Rt` | `R[Rd] = R[Rs] - R[Rt]` (two’s complement) |
| `AND Rd, Rs, Rt` | `R[Rd] = R[Rs] & R[Rt]` |
| `OR Rd, Rs, Rt` | `R[Rd] = R[Rs] \| R[Rt]` |
| `SLL Rd, Rs, Rt` | `R[Rd] = R[Rs] << (R[Rt] & 0xF)` (logical left, shift amount 0–15) |
| `SRL Rd, Rs, Rt` | `R[Rd] = R[Rs] >> (R[Rt] & 0xF)` (logical right, shift amount 0–15) |

---

### 4.2 Memory
- `LD Rd, [Rs + off]` : effective address = `R[Rs] + sext(off)`.  
  Loads 16‑bit word from that address into `R[Rd]`.
- `ST Rd, [Rs + off]` : stores the 16‑bit word from `R[Rd]` to the effective address.

**Alignment:** effective address must be even; undefined behavior otherwise.

---

### 4.3 Control Flow
- `BEQ Rs, Rt, off` : branch if registers are equal.
- `BNE Rs, Rt, off` : branch if registers are not equal.
- `BLT Rs, Rt, off` : branch if `R[Rs] < R[Rt]` using **signed** comparison.
- `JMP Rs` : set `PC = R[Rs]`.
- `LDI Rd, #imm16` : no effect on PC beyond normal advance (PC += 4).

All branches use the **relative offset** `sext(off) * 2` added to the **already incremented PC** (i.e., PC points to the next instruction before the branch is taken).

---

## 5. Assembler Syntax

```
ADD  Rd, Rs, Rt          ; Rd = Rs + Rt
ADDI Rd, Rs, #imm        ; Rd = Rs + sext(imm), imm in [-32,31]
XOR  Rd, Rs, Rt          ; Rd = Rs ^ Rt
XORI Rd, Rs, #imm        ; Rd = Rs ^ sext(imm)
SUB  Rd, Rs, Rt          ; Rd = Rs - Rt
AND  Rd, Rs, Rt          ; Rd = Rs & Rt
OR   Rd, Rs, Rt          ; Rd = Rs | Rt
SLL  Rd, Rs, Rt          ; Rd = Rs << (Rt & 0xF)
SRL  Rd, Rs, Rt          ; Rd = Rs >> (Rt & 0xF)
LD   Rd, [Rs + off]      ; Rd = M[Rs + sext(off)], off in [-32,31]
ST   Rd, [Rs + off]      ; M[Rs + sext(off)] = Rd
JMP  Rs                  ; PC = Rs
BEQ  Rs, Rt, off         ; if (Rs == Rt) PC += sext(off)*2
BNE  Rs, Rt, off         ; if (Rs != Rt) PC += sext(off)*2
BLT  Rs, Rt, off         ; if (Rs < Rt) PC += sext(off)*2 (signed)
LDI  Rd, #imm16          ; Rd = imm16  (2-word instruction)
```

- Immediates can be decimal, hexadecimal (`0x...`), or binary (`0b...`).
- Offsets are in bytes for `LD/ST`, in instructions for branches.

---

## 6. Programming Examples

### Example 1: 16‑bit Sum of 10‑word Array
```
    LDI  R1, #0x1000       ; base address
    LDI  R2, #10           ; counter
    LDI  R3, #0            ; sum
    LDI  R7, #loop         ; loop address

loop:
    LD   R4, [R1 + 0]      ; load word
    ADD  R3, R3, R4        ; accumulate
    ADDI R1, R1, #2        ; next word
    ADDI R2, R2, #-1       ; decrement
    BNE  R0, R2, loop      ; if counter != 0, loop

    ; Result in R3
```

### Example 2: Bit‑packing two 8‑bit values
```
    LDI  R1, #0x12         ; low byte
    LDI  R2, #0x34         ; high byte
    LDI  R3, #8
    SLL  R2, R2, R3        ; R2 = 0x3400
    OR   R4, R2, R1        ; R4 = 0x3412
```

### Example 3: Signed loop (for i = 0; i < 10; i++)
```
    LDI  R1, #0            ; i = 0
    LDI  R2, #10           ; limit
    LDI  R7, #loop
loop:
    ; ... use R1 as i ...
    ADDI R1, R1, #1
    BLT  R1, R2, loop      ; if i < 10 loop
```

### Example 4: 16‑bit Down‑Counter with Toggle Output
```
    LDI  R1, #1000         ; counter
    LDI  R5, #0x2000       ; GPIO base

tick:
    ADDI R1, R1, #-1
    BNE  R0, R1, exit
    ; counter reached zero – toggle bit 0 of GPIO
    LD   R2, [R5 + 0]
    LDI  R3, #1
    XOR  R2, R2, R3
    ST   R2, [R5 + 0]
    LDI  R1, #1000         ; reload
exit:
    JMP  R7                ; return from ISR
```

---

## 7. Verilog Implementation Notes

### 7.1 Decode Logic Optimisation

The 4‑bit opcode is decoded as follows:

```
wire [3:0] op = inst[15:12];
wire is_alu = ~op[3];               // opcodes 0x0..0x8

// ALU function = op[2:0]  (except for ADD/ADDI where op[2:0]=000, XOR/XORI where=001)
wire [2:0] alu_func = is_alu ? op[2:0] : 3'b0;
wire imm_en = (op == 4'b0001) || (op == 4'b0011);   // ADDI or XORI

// Operand B mux
wire [15:0] operand_b = imm_en ? { {10{inst[5]}}, inst[5:0] } : regfile[Rt];
```

The ALU computes all functions in parallel; the result is muxed by `alu_func`.  
The `BLT` instruction reuses the subtractor output and checks the sign bit:

```
wire less = (R[Rs] < operand_b);    // signed comparison uses the subtractor
// For BLT, branch_taken = less & is_branch_cond[2] where is_branch_cond is from opcode.
```

### 7.2 Control Signals Summary

| Opcode Group | `reg_we` | `mem_we` | `branch` | `jmp` | `imm_en` | `alu_func` |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| 0x0–0x8 (ALU) | 1 | 0 | 0 | 0 | as above | op[2:0] |
| 0x9 (LD) | 1 | 0 (read) | 0 | 0 | 0 | – |
| 0xA (ST) | 0 | 1 (write) | 0 | 0 | 0 | – |
| 0xB (JMP) | 0 | 0 | 0 | 1 | 0 | – |
| 0xC–0xE (Branches) | 0 | 0 | 1 | 0 | 0 | – |
| 0xF (LDI) | 1 | 0 | 0 | 0 | – | – |

The branch condition is decoded from opcode bits:  
- `BEQ` (0xC) : `Rs == Rt`  
- `BNE` (0xD) : `Rs != Rt`  
- `BLT` (0xE) : `Rs < Rt` (signed)  
This fits into a tiny 3‑bit comparator.

---

## 8. Rationale for Included Instructions

### 8.1 Why `SUB` is included although it can be emulated
`SUB` is **not** emulated because:
- `BLT` (signed branch) already requires a subtractor for comparison.
- Adding `SUB` reuses that same subtractor with zero extra hardware cost.
- It eliminates a 3‑instruction emulation (`XORI` + `ADDI` + `ADD`) in common arithmetic loops.

### 8.2 Immediate variants (`ADDI`, `XORI`)
- Dramatically reduce code size (no need for memory tables).
- `XORI` is the only way to get a constant `-1` for inversion or clearing without memory.
- Used with `R0=0` to load small constants.

### 8.3 `LDI` – 2‑word immediate load
- Provides full 16‑bit constants without needing a data section.
- The extra word is fetched once; the decoder handles the 2‑cycle fetch automatically.

### 8.4 Shift instructions (`SLL`, `SRL`)
- Essential for bit‑packing, addressing (array index × 2), and fast multiplication/division by powers of two.
- The shift amount is limited to 0–15 (lower 4 bits of `Rt`) to keep the barrel shifter small.

---

## 9. Full Opcode Table (Binary and Hex)

| Hex | Binary `[3:0]` | Mnemonic | Operands |
| :--- | :--- | :--- | :--- |
| 0 | `0000` | `ADD` | `Rd, Rs, Rt` |
| 1 | `0001` | `ADDI` | `Rd, Rs, #imm6` |
| 2 | `0010` | `XOR` | `Rd, Rs, Rt` |
| 3 | `0011` | `XORI` | `Rd, Rs, #imm6` |
| 4 | `0100` | `SUB` | `Rd, Rs, Rt` |
| 5 | `0101` | `AND` | `Rd, Rs, Rt` |
| 6 | `0110` | `OR` | `Rd, Rs, Rt` |
| 7 | `0111` | `SLL` | `Rd, Rs, Rt` |
| 8 | `1000` | `SRL` | `Rd, Rs, Rt` |
| 9 | `1001` | `LD` | `Rd, [Rs + off6]` |
| A | `1010` | `ST` | `Rd, [Rs + off6]` |
| B | `1011` | `JMP` | `Rs` |
| C | `1100` | `BEQ` | `Rs, Rt, off6` |
| D | `1101` | `BNE` | `Rs, Rt, off6` |
| E | `1110` | `BLT` | `Rs, Rt, off6` |
| F | `1111` | `LDI` | `Rd, #imm16` (2‑word) |

---

## 10. Acknowledgements & Extensions

- **Reserved opcodes:** none – all 16 slots are used.
- **Future extensions:** If more instructions are ever needed, the ISA can be expanded to 5‑bit opcodes by increasing instruction width to 32 bits, or by using a prefix mechanism. For a minimalist 16‑bit µC, this set is complete and production‑ready.

---

*This specification defines a balanced, Verilog‑friendly, microcontroller‑grade ISA that is both minimalist and practical.*