; v2.1 pseudo-op coverage test: NEG NOT CLR LSL LSR ASR B BGT BLE BLTU
; BGEU BLEU + explicit LDI8 + far-branch relaxation (targets beyond the
; +-32-word range: taken and not-taken, incl. the equal-operands case
; the relaxed inverse of BLT/BGT used to get wrong, and the far
; BRANCH2 pseudos that used to be silently truncated).
; MOV / BGE / RET are covered by byte_test / bge_test / call_test.
; Scratch for the 2-word pseudos is R4 (default) — no check relies on
; R4 across them.  R1 = 0x2A if every check passed, 0 otherwise.

        CLR  R1                    ; fail marker = 0
        LDI  R2, #7
        LDI  R3, #7

        ; --- far branches to `fail` (relaxed): must NOT be taken ---
        BNE  R2, R3, fail          ; equal -> fall (relaxed BNE inverse)
        BLT  R2, R3, fail          ; EQUAL -> fall (relaxed BLT equality case)
        BGT  R2, R3, fail          ; EQUAL -> fall (relaxed BGT = swapped BLT)
        LDI  R6, #0x8001
        BLTU R6, R2, fail          ; 0x8001 !<u 7 (relaxed BLTU; unsigned)

        ; --- far branches TAKEN: jump over the filler to `checks` ---
        BEQ  R2, R3, checks        ; 7 == 7 (relaxed BEQ)
        LDI  R3, #3
        BLT  R3, R2, checks        ; 3 < 7 (relaxed BLT taken)
        BGT  R2, R3, checks        ; 7 > 3 (relaxed BGT taken)
        BLE  R3, R2, checks        ; 3 <= 7 (relaxed BLE = BRANCH2)
        BLTU R2, R6, checks        ; 7 <u 0x8001 (relaxed BLTU = BRANCH2)
        B    fail                  ; nothing jumped -> broken

        ; filler: pushes `checks` beyond the +-32-word branch range
        ; (never executed: every far-taken branch jumps over it)
        .word 0, 0, 0, 0, 0, 0, 0, 0, 0, 0
        .word 0, 0, 0, 0, 0, 0, 0, 0, 0, 0
        .word 0, 0, 0, 0, 0, 0, 0, 0, 0, 0
        .word 0, 0, 0, 0

checks:
        ; --- NEG / NOT value checks ---
        LDI  R2, #5
        NEG  R3, R2                ; -5 = 0xFFFB  (-> SUB R3,R0,R2)
        LDI  R4, #0xFFFB
        BNE  R3, R4, fail
        NOT  R3, R2                ; ~5 = 0xFFFA  (-> XORI R3,R2,#-1)
        LDI  R4, #0xFFFA
        BNE  R3, R4, fail

        ; --- shifts through LSL / LSR / ASR ---
        LDI  R6, #3
        LSL  R3, R2, R6            ; 5 << 3 = 40 (-> SLL)
        LDI  R4, #40
        BNE  R3, R4, fail
        LDI  R2, #0x80
        LSR  R3, R2, R6            ; 0x80 >> 3 = 16 (-> SRL)
        LDI  R4, #16
        BNE  R3, R4, fail
        LDI  R2, #-8
        LDI  R6, #1
        ASR  R3, R2, R6            ; -8 >>> 1 = -4 = 0xFFFC (-> SRA)
        LDI  R4, #0xFFFC
        BNE  R3, R4, fail

        ; --- explicit LDI8 (1-word constant load) ---
        LDI8 R3, #0x5A
        LDI  R4, #0x5A
        BNE  R3, R4, fail

        ; --- BGT / BLE, short range ---
        LDI  R2, #7
        LDI  R3, #3
        BGT  R2, R3, sgt1          ; 7 > 3 -> take
        B    fail
sgt1:   BGT  R3, R2, fail          ; 3 > 7 -> fall
        BLE  R3, R2, sbl1          ; 3 <= 7 -> take
        B    fail
sbl1:   BLE  R2, R3, fail          ; 7 <= 3 -> fall

        ; --- BGE, short range (bge_test covers the equal case) ---
        BGE  R2, R3, sbge1         ; 7 >= 3 -> take
        B    fail
sbge1:  BGE  R3, R2, fail          ; 3 >= 7 -> fall

        ; --- unsigned branches (0x8001 = signed -32767) ---
        LDI  R6, #0x8001
        BLTU R3, R6, sbu1          ; 3 <u 0x8001 -> take (signed: false)
        B    fail
sbu1:   BLTU R6, R3, fail          ; 0x8001 <u 3 -> fall
        BGEU R6, R3, sbg1          ; 0x8001 >=u 3 -> take
        B    fail
sbg1:   BGEU R3, R6, fail          ; 3 >=u 0x8001 -> fall
        BLEU R3, R6, sblu1         ; 3 <=u 0x8001 -> take
        B    fail
sblu1:  BLEU R6, R3, fail          ; 0x8001 <=u 3 -> fall

        ; --- B: unconditional over a trap ---
        B    done
        B    fail                  ; skipped if B jumps
done:   LDI  R1, #0x2A
        HALT
fail:   HALT                       ; R1 stays 0 on any failed check
