; v3.2 BGE pseudo — must branch on EQUALITY (5 == 5)
; R1 = 1 if the branch was taken, HALT leaves 0 otherwise
        MOV  R1, R0
        LDI  R2, #5
        LDI  R3, #5
        BGE  R2, R3, eq         ; 2-word pseudo: SLT tmp,R2,R3 ; BR Z,tmp,eq
        HALT
eq:     LDI  R1, #1
        HALT
