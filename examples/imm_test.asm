; v2.1 in-place group-B immediates: ANDI ORI SLLI SRLI SRAI
; R1 = 0x3E
        LDI  R1, #0x00F0       ; LDI8 (auto-narrowed)
        ORI  R1, #0x0F         ; 0xFF
        ANDI R1, #0x1F         ; 0x1F
        SLLI R1, #4            ; 0x1F0
        SRLI R1, #2            ; 0x7C
        ORI  R1, #1            ; 0x7D
        SRAI R1, #1            ; 0x7D >>> 1 = 0x3E
        HALT
