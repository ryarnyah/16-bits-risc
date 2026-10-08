; v3.3 CALLR / JMPR — PC-relative link-call + loop back-edge
; R1 = 42: fn counts R1 = 7,14,21 via a JMPR loop, returns, caller doubles it
        CALLR R5, fn          ; R5 = return address, no materialization
        ADD  R1, R1, R1       ; runs only if control really returned here
        HALT
fn:     LDI  R1, #0
        LDI  R2, #3
lp:     ADDI R1, R1, #7
        ADDI R2, R2, #-1
        BR   Z, R2, done
        JMPR lp               ; PC-relative back-edge (no LDI+JMP)
done:   RET  R5
