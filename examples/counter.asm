; Counter program: R3 counts from 0 to 9 repeatedly
; R1 = loop address
    LDI R1, #loop
    LDI R3, #0
loop:
    ADDI R3, R3, #1
    XORI R3, R3, #0
    ; branch back if R3 < 10
    LDI R2, #10
    BLT  R3, R2, loop
    ; reset counter and repeat
    LDI R3, #0
    JMP R1
