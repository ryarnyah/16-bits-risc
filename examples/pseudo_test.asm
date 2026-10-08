; v3.2 pseudo-op + BR coverage test: NEG NOT CLR LSL LSR ASR B BGT BLE
; BLTU BGEU BLEU + explicit LDI8 + BR Z/NZ/MI/PL (taken and fall-through,
; incl. the equal-operands and negative-operands cases).
; Style: each check jumps OVER an inline HALT trap on success; any failure
; executes the trap with R1 still 0.  All jumps are +-2 words (far targets
; would use the exact-inverse far form, but this file needs none).  R1 = 0x2A iff every check passed.
; MOV / BGE / RET are covered by byte_test / bge_test / call_test.
; Scratch for the 2-word pseudos is R4 (default) — no check relies on
; R4 across them.

        CLR  R1                    ; fail marker = 0
        LDI  R2, #7
        LDI  R3, #7

        ; --- BR cases that must NOT be taken (share one near trap) ---
        BR   Z, R2, bad           ; 7 != 0 -> must fall through
        BR   MI, R2, bad          ; 7 >= 0 -> must fall through
        LDI  R6, #0x8001           ; -32767 signed
        BR   PL, R6, bad          ; negative -> must fall through
        B    brt                  ; all fell through -> continue
bad:    HALT                      ; taken = failure, R1 stays 0
brt:
        ; --- BR taken cases (jump over the trap) ---
        BR   NZ, R2, br1           ; 7 != 0 -> take
        HALT
br1:    BR   Z, R0, br2            ; 0 == 0 -> take
        HALT
br2:    BR   MI, R6, br3           ; -32767 < 0 -> take
        HALT
br3:    BR   PL, R2, checks        ; 7 >= 0 -> take
        HALT

checks:
        ; --- NEG / NOT value checks (compare via SUB + BR) ---
        LDI  R2, #5
        NEG  R3, R2                ; -5 = 0xFFFB  (-> SUB R3,R0,R2)
        LDI  R4, #0xFFFB
        SUB  R4, R3, R4
        BR   Z, R4, ok4
        HALT
ok4:    NOT  R3, R2                ; ~5 = 0xFFFA  (-> XORI R3,R2,#-1)
        LDI  R4, #0xFFFA
        SUB  R4, R3, R4
        BR   Z, R4, ok5
        HALT
ok5:
        ; --- shifts through LSL / LSR / ASR ---
        LDI  R6, #3
        LSL  R3, R2, R6            ; 5 << 3 = 40 (-> SLL)
        LDI  R4, #40
        SUB  R4, R3, R4
        BR   Z, R4, ok6
        HALT
ok6:    LDI  R2, #0x80
        LSR  R3, R2, R6            ; 0x80 >> 3 = 16 (-> SRL)
        LDI  R4, #16
        SUB  R4, R3, R4
        BR   Z, R4, ok7
        HALT
ok7:    LDI  R2, #-8
        LDI  R6, #1
        ASR  R3, R2, R6            ; -8 >>> 1 = -4 = 0xFFFC (-> SRA)
        LDI  R4, #0xFFFC
        SUB  R4, R3, R4
        BR   Z, R4, ok8
        HALT
ok8:
        ; --- explicit LDI8 (1-word constant load) ---
        LDI8 R3, #0x5A
        LDI  R4, #0x5A
        SUB  R4, R3, R4
        BR   Z, R4, ok9
        HALT
ok9:
        ; --- BGT / BLE ---
        LDI  R2, #7
        LDI  R3, #3
        BGT  R2, R3, sgt1          ; 7 > 3 -> take
        HALT
sgt1:   BGT  R3, R2, badA          ; 3 > 7 -> must fall through
        B    okA
badA:   HALT
okA:    BLE  R3, R2, sbl1          ; 3 <= 7 -> take
        HALT
sbl1:   BLE  R2, R3, badB          ; 7 <= 3 -> must fall through
        B    okB
badB:   HALT
okB:
        ; --- BGE (bge_test covers the equal case) ---
        BGE  R2, R3, sbge1         ; 7 >= 3 -> take
        HALT
sbge1:  BGE  R3, R2, badC          ; 3 >= 7 -> must fall through
        B    okC
badC:   HALT
okC:
        ; --- unsigned branches (0x8001 = 32769 unsigned) ---
        LDI  R6, #0x8001
        BLTU R3, R6, sbu1          ; 3 <u 0x8001 -> take
        HALT
sbu1:   BLTU R6, R3, badD          ; 0x8001 <u 3 -> must fall through
        B    okD
badD:   HALT
okD:    BGEU R6, R3, sbg1          ; 0x8001 >=u 3 -> take
        HALT
sbg1:   BGEU R3, R6, badE          ; 3 >=u 0x8001 -> must fall through
        B    okE
badE:   HALT
okE:    BLEU R3, R6, sblu1         ; 3 <=u 0x8001 -> take
        HALT
sblu1:  BLEU R6, R3, badF          ; 0x8001 <=u 3 -> must fall through
        B    okF
badF:   HALT
okF:
        ; --- B: unconditional over a trap ---
        B    done
        HALT                       ; skipped if B jumps
done:   LDI  R1, #0x2A
        HALT
