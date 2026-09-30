; v2.1 LDI auto-narrowing + reserved encoding executes as NOP + HALT
; R1 = 0x1FF: LDI8 (255) + 2-word LDI (256); reserved word must not disturb
        LDI  R1, #255          ; 1 word (LDI8)
        LDI  R2, #256          ; 2 words (does not fit in 8 bits)
        ADD  R1, R1, R2        ; 0x1FF
        .word 0x4003           ; SUB f3=011 — reserved -> NOP
        HALT
