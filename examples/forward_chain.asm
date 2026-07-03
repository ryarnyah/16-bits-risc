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
    ADDI R7, R7, #-12

    ADDI R1, R0, #3
    ST R1, [R6 -2]
    LD R1, [R6 -2]
    ADDI R1, R1, #5
    ST R1, [R6 -4]
    LD R1, [R6 -4]
    ST R1, [R7]
    ADDI R7, R7, #-2
    LD R1, [R6 -2]
    ADDI R7, R7, #2
    LD R2, [R7]
    XOR R1, R2, R1
    ST R1, [R6 -6]
    LD R1, [R6 -6]
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R1, R0, #7
    ADDI R7, R7, #2
    LD R2, [R7]
    AND R1, R2, R1
    ST R1, [R6 -8]
    LD R1, [R6 -8]
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R1, R0, #1
    ADDI R7, R7, #2
    LD R2, [R7]
    OR R1, R2, R1
    ST R1, [R6 -10]
    LD R1, [R6 -10]
    ST R1, [R7]
    ADDI R7, R7, #-2
    LD R1, [R6 -6]
    ADDI R7, R7, #2
    LD R2, [R7]
    ADD R1, R2, R1
    ST R1, [R6 -12]
    LD R1, [R6 -12]
    ST R1, [R7]
    ADDI R7, R7, #-2
    LD R1, [R6 -2]
    ADDI R7, R7, #2
    LD R2, [R7]
    SUB R1, R2, R1
main_epi:
    ADDI R7, R7, #12
    ADDI R7, R7, #2
    LD R6, [R7]
    ADDI R7, R7, #2
    LD R5, [R7]
    JMP R5
