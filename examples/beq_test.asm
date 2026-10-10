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
    ADDI R7, R7, #-4

    ADDI R1, R0, #3
    ST R1, [R6 -1]
    ADDI R1, R0, #3
    ST R1, [R6 -2]
    LD R1, [R6 -1]
    MOV R2, R1
    LD R1, [R6 -2]
    XOR R1, R2, R1
    BR NZ, R1, .el1
    LDI R1, #0x002A
    JMPR .ei2
.el1:
    XOR R1, R0, R0
    JMPR main_epi
.ei2:
main_epi:
    ADDI R7, R7, #4
    ADDI R7, R7, #2
    LD R6, [R7]
    ADDI R7, R7, #2
    LD R5, [R7]
    JMP R5
