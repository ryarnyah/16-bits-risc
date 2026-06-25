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
    ST R2, [R6 -2]

    LD R1, [R6 -2]
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R1, R0, #1
    ADDI R7, R7, #2
    LD R2, [R7]
    BLT R1, R2, .el1
    LD R1, [R6 -2]
    JMP fib_epi
.el1:
.ei2:
    LD R1, [R6 -2]
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R1, R0, #1
    ADDI R7, R7, #2
    LD R2, [R7]
    SUB R1, R2, R1
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    LDI R5, #.cr3
    LDI R1, #fib
    JMP R1
.cr3:
    ST R1, [R7]
    ADDI R7, R7, #-2
    LD R1, [R6 -2]
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R1, R0, #2
    ADDI R7, R7, #2
    LD R2, [R7]
    SUB R1, R2, R1
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    LDI R5, #.cr4
    LDI R1, #fib
    JMP R1
.cr4:
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
    LDI R5, #.cr5
    LDI R1, #fib
    JMP R1
.cr5:
main_epi:
    ADDI R7, R7, #2
    LD R6, [R7]
    ADDI R7, R7, #2
    LD R5, [R7]
    JMP R5
