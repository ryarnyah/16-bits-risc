; C runtime startup
    LDI R7, #0x1FFA
    LDI R5, #_exit
    LDI R1, #main
    JMP R1
_exit:
    JMP R5


__data_init:
__data_init_end:
;--- fib(...)
fib:
    ST R5, [R7]
    ADDI R7, R7, #-2
    ST R6, [R7]
    ADDI R7, R7, #-2
    ADD R6, R7, R0
    ADDI R6, R6, #2
    ADDI R7, R7, #-2
    ST R2, [R6 -1]

    LD R1, [R6 -1]
    MOV R2, R1
    ADDI R1, R0, #1
    SLT R3, R1, R2
    BR NZ, R3, .el1
    LD R1, [R6 -1]
    JMPR fib_epi
.el1:
.ei2:
    LD R1, [R6 -1]
    MOV R2, R1
    ADDI R1, R0, #1
    SUB R1, R2, R1
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    CALLR R5, fib
    ST R1, [R7]
    ADDI R7, R7, #-2
    LD R1, [R6 -1]
    MOV R2, R1
    ADDI R1, R0, #2
    SUB R1, R2, R1
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    CALLR R5, fib
    ADDI R7, R7, #2
    LD R2, [R7]
    ADD R1, R2, R1
fib_epi:
    ADDI R7, R7, #2
    ADDI R7, R7, #2
    LD R6, [R7]
    ADDI R7, R7, #2
    LD R5, [R7]
    JMP R5

;--- main(...)
main:
    ST R5, [R7]
    ADDI R7, R7, #-2
    ST R6, [R7]
    ADDI R7, R7, #-2
    ADD R6, R7, R0
    ADDI R6, R6, #2

    ADDI R1, R0, #15
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    CALLR R5, fib
main_epi:
    ADDI R7, R7, #2
    LD R6, [R7]
    ADDI R7, R7, #2
    LD R5, [R7]
    JMP R5
