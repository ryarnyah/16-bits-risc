; C runtime startup
    LDI R7, #0x1FFA
    LDI R5, #_exit
    LDI R1, #main
    JMP R1
_exit:
    JMP R5


__data_init:
__data_init_end:
;--- main(...)
main:
    ST R5, [R7]
    ADDI R7, R7, #-2
    ST R6, [R7]
    ADDI R7, R7, #-2
    ADD R6, R7, R0
    ADDI R6, R6, #2
    ADDI R7, R7, #-8
    ST R2, [R6 -8]

    XOR R1, R0, R0
    ST R1, [R6 -2]
    XOR R1, R0, R0
    ST R1, [R6 -4]
    XOR R1, R0, R0
    ST R1, [R6 -6]
    ADDI R1, R0, #1
    ST R1, [R6 -4]
    XOR R1, R0, R0
    ST R1, [R6 -6]
    LD R1, [R6 -4]
    ST R1, [R7]
    ADDI R7, R7, #-2
    LD R1, [R6 -6]
    ADDI R7, R7, #2
    LD R2, [R7]
    XOR R1, R2, R1
    BEQ R1, R0, .el1
    ADDI R1, R0, #1
    ST R1, [R6 -2]
.el1:
.ei2:
    LDI R1, #0x00FF
    ST R1, [R6 -2]
    XOR R1, R0, R0
    ST R1, [R6 -6]
    LD R1, [R6 -4]
    ST R1, [R7]
    ADDI R7, R7, #-2
    LD R1, [R6 -6]
    ADDI R7, R7, #2
    LD R2, [R7]
    XOR R1, R2, R1
    BNE R1, R0, .el3
    LD R1, [R6 -2]
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R1, R0, #8
    ADDI R7, R7, #2
    LD R2, [R7]
    SLL R1, R2, R1
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R1, R0, #2
    ADDI R7, R7, #2
    LD R2, [R7]
    OR R1, R2, R1
    ST R1, [R6 -2]
.el3:
.ei4:
    LDI R1, #0x00FF
    ST R1, [R6 -2]
    ADDI R1, R0, #2
    ST R1, [R6 -6]
    LD R1, [R6 -4]
    ST R1, [R7]
    ADDI R7, R7, #-2
    LD R1, [R6 -6]
    ADDI R7, R7, #2
    LD R2, [R7]
    BLT R2, R1, .cl7
    JMP .el5
.cl7:
    LD R1, [R6 -2]
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R1, R0, #8
    ADDI R7, R7, #2
    LD R2, [R7]
    SLL R1, R2, R1
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R1, R0, #3
    ADDI R7, R7, #2
    LD R2, [R7]
    OR R1, R2, R1
    ST R1, [R6 -2]
.el5:
.ei6:
    LDI R1, #0x00FF
    ST R1, [R6 -2]
    ADDI R1, R0, #5
    ST R1, [R6 -6]
    LD R1, [R6 -4]
    ST R1, [R7]
    ADDI R7, R7, #-2
    LD R1, [R6 -6]
    ADDI R7, R7, #2
    LD R2, [R7]
    BLT R2, R1, .el8
    LD R1, [R6 -2]
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R1, R0, #8
    ADDI R7, R7, #2
    LD R2, [R7]
    SLL R1, R2, R1
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R1, R0, #4
    ADDI R7, R7, #2
    LD R2, [R7]
    OR R1, R2, R1
    ST R1, [R6 -2]
.el8:
.ei9:
    LDI R1, #0x00FF
    ST R1, [R6 -2]
    XOR R1, R0, R0
    ST R1, [R6 -6]
    LD R1, [R6 -4]
    ST R1, [R7]
    ADDI R7, R7, #-2
    LD R1, [R6 -6]
    ADDI R7, R7, #2
    LD R2, [R7]
    XOR R1, R2, R1
    BNE R1, R0, .el10
    LD R1, [R6 -2]
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R1, R0, #8
    ADDI R7, R7, #2
    LD R2, [R7]
    SLL R1, R2, R1
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R1, R0, #5
    ADDI R7, R7, #2
    LD R2, [R7]
    OR R1, R2, R1
    ST R1, [R6 -2]
.el10:
.ei11:
    LDI R1, #0x00FF
    ST R1, [R6 -2]
main_epi:
    ADDI R7, R7, #8
    ADDI R7, R7, #2
    LD R6, [R7]
    ADDI R7, R7, #2
    LD R5, [R7]
    JMP R5
