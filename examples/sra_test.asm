; v2.1 SRA / SRAI — arithmetic vs logical right shift
; R1 = 0xC001
        LDI  R2, #-16          ; 0xFFF0 (2-word form: -16 not in 0..255)
        LDI  R3, #2
        SRA  R4, R2, R3        ; 0xFFF0 >>> 2 = 0xFFFC (-4)
        SRL  R5, R2, R3        ; 0xFFF0 >>  2 = 0x3FFC
        XOR  R1, R4, R5        ; 0xC000
        SRAI R2, #2            ; in-place: R2 = 0xFFFC
        SLT  R4, R2, R0        ; -4 < 0 -> 1
        ADD  R1, R1, R4        ; 0xC001
        HALT
