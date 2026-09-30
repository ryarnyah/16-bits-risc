; v2.1 STB / LDB — byte lanes, no clobber of the other lane, zero-extend
; R1 = 0x112 = R6(0x55) + R4(0x12) + R5(0xAB) + R7(0, zero-extend check)
        LDI  R1, #0x1F00
        LDI  R2, #0x55AA
        ST   R2, [R1 + 0]      ; word 0x1F00 = 0x55AA
        LDI  R3, #0x12
        STB  R3, [R1 + 0]      ; low lane  -> 0x5512 (high untouched)
        LDB  R6, [R1 + 1]      ; 0x55 proves no clobber
        LDI  R3, #0xAB
        STB  R3, [R1 + 1]      ; high lane -> 0xAB12
        LDB  R4, [R1 + 0]      ; 0x12, must zero-extend
        LDB  R5, [R1 + 1]      ; 0xAB, must zero-extend
        MOV  R7, R5
        SRLI R7, #8            ; 0 if zero-extended (0xFF if sign-extended)
        ADD  R1, R4, R5        ; 0xBD
        ADD  R1, R1, R7        ; 0xBD (0x1BC on sign-extension bug)
        ADD  R1, R1, R6        ; 0x112
        HALT
