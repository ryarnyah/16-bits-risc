; Direct __mod16 test: 123 % 45
    LDI R7, #0x1FFA
    LDI R2, #123           ; dividend
    LDI R1, #45            ; divisor
    LDI R5, #_exit         ; return address
    LDI R4, #__mod16
    JMP R4
_exit:
    JMP R5
__mod16:
    BEQ R1, R0, __div_exit
    ST R5, [R7]
    ADDI R7, R7, #-2
    XOR R3, R3, R3
    ADDI R4, R0, #16
    XOR R5, R5, R5
__mod_lp:
    ADDI R6, R0, #1
    SLL R5, R5, R6
    LDI R6, #0x8000
    AND R6, R2, R6
    BEQ R6, R0, __mod_nb
    ADDI R6, R0, #1
    OR R5, R5, R6
__mod_nb:
    ADDI R6, R0, #1
    SLL R2, R2, R6
    SUB R6, R5, R1
    BLT R5, R1, __mod_sk
    ADD R5, R0, R6
__mod_sk:
    ADDI R4, R4, #-1
    BNE R4, R0, __mod_lp
    ADD R1, R0, R5
    ADDI R7, R7, #2
    LD R5, [R7]
    JMP R5
__div_exit:
    XOR R1, R0, R0
    JMP R5
