; v2.1 SLT / SLTU — signed vs unsigned compare
; R1 = 3: SLT(-1,1)=1, shift, SLTU(-1,1)=0, SLTU(1,-1)=1
        LDI  R2, #-1
        LDI  R3, #1
        SLT  R1, R2, R3        ; signed:   -1 < 1   -> 1
        SLLI R1, #1            ; -> 2
        SLTU R4, R2, R3        ; unsigned: FFFF < 1 -> 0
        ADD  R1, R1, R4        ; -> 2
        SLTU R4, R3, R2        ; unsigned: 1 < FFFF -> 1
        ADD  R1, R1, R4        ; -> 3
        HALT
