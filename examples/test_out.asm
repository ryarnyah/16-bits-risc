; C runtime startup
    LDI R7, #0x1FFA
    LDI R1, #__data_init
    LDI R2, #0
.init1:
    LD R3, [R1 + 0]
    ST R3, [R2 + 0]
    ADDI R1, R1, #2
    ADDI R2, R2, #2
    LDI R4, #__data_init_end
    BLT R1, R4, .init1
    LDI R5, #_exit
    LDI R1, #main
    JMP R1
_exit:
    JMP R5


__data_init:
    .word 0x0000
__data_init_end:
;--- mul(...)
mul:
    ST R5, [R7]
    ADDI R7, R7, #-2
    ST R6, [R7]
    ADDI R7, R7, #-2
    ADD R6, R7, R0
    ADDI R6, R6, #2
    ADDI R7, R7, #-4
    ST R2, [R6 -2]
    ST R3, [R6 -4]

    LD R1, [R6 -2]
    ST R1, [R7]
    ADDI R7, R7, #-2
    LD R1, [R6 -4]
    ADDI R7, R7, #2
    LD R2, [R7]
    ST R5, [R7]
    ADDI R7, R7, #-2
    ST R1, [R7]
    ADDI R7, R7, #-2
    LDI R5, #.mul16_ret2
    LDI R4, #__mul16
    LD R1, [R7 +2]
    JMP R4
.mul16_ret2:
    ADDI R7, R7, #2
    ADDI R7, R7, #2
    LD R5, [R7]
mul_epi:
    ADDI R7, R7, #4
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
    ADDI R7, R7, #-2

    LDI R1, #0x002A
    ST R1, [R6 -2]
    LD R1, [R6 -2]
    ADDI R1, R1, #1
    ST R1, [R0 +0]
    ADDI R1, R0, #3
    ST R1, [R7]
    ADDI R7, R7, #-2
    LD R1, [R0 +0]
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    ADDI R7, R7, #2
    LD R3, [R7]
    LDI R5, #.cr3
    LDI R1, #mul
    JMP R1
.cr3:
    ST R1, [R0 +0]
    LD R1, [R0 +0]
main_epi:
    ADDI R7, R7, #2
    ADDI R7, R7, #2
    LD R6, [R7]
    ADDI R7, R7, #2
    LD R5, [R7]
    JMP R5


; ===== Runtime Library =====

__mul16:
    ST R6, [R7]
    ADDI R7, R7, #-2
    XOR R3, R3, R3
    ADDI R4, R0, #16
__mul_lp:
    ADDI R6, R0, #1
    AND R6, R1, R6
    BEQ R6, R0, __mul_sk
    ADD R3, R3, R2
__mul_sk:
    ADDI R6, R0, #1
    SLL R2, R2, R6
    SRL R1, R1, R6
    ADDI R4, R4, #-1
    BNE R4, R0, __mul_lp
    ADD R1, R0, R3
    ADDI R7, R7, #2
    LD R6, [R7]
    JMP R5
