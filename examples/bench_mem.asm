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
    LDI R2, #-204
    ADD R7, R7, R2

    XOR R1, R0, R0
    ST R1, [R7]
    ADDI R7, R7, #-2
    LDI R1, #-204
    ADD R1, R1, R6
    ADDI R7, R7, #2
    LD R2, [R7]
    ST R2, [R1 + 0]
    XOR R1, R0, R0
    ST R1, [R7]
    ADDI R7, R7, #-2
    LDI R1, #-202
    ADD R1, R1, R6
    ADDI R7, R7, #2
    LD R2, [R7]
    ST R2, [R1 + 0]
.fc1:
    LDI R1, #-202
    ADD R1, R1, R6
    LD R1, [R1 + 0]
    ST R1, [R7]
    ADDI R7, R7, #-2
    LDI R1, #0x0064
    ADDI R7, R7, #2
    LD R2, [R7]
    BLT R2, R1, .cl4
    JMP .fe3
.cl4:
    LDI R1, #-200
    ADD R1, R1, R6
    ST R1, [R7]
    ADDI R7, R7, #-2
    LDI R1, #-202
    ADD R1, R1, R6
    LD R1, [R1 + 0]
    ADDI R3, R0, #1
    SLL R1, R1, R3
    ADDI R7, R7, #2
    LD R2, [R7]
    ADD R1, R2, R1
    ST R1, [R7]
    ADDI R7, R7, #-2
    LDI R1, #-202
    ADD R1, R1, R6
    LD R1, [R1 + 0]
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R1, R0, #3
    ADDI R7, R7, #2
    LD R2, [R7]
    ST R5, [R7]
    ADDI R7, R7, #-2
    ST R1, [R7]
    ADDI R7, R7, #-2
    LDI R5, #.mul16_ret5
    LDI R4, #__mul16
    LD R1, [R7 +2]
    JMP R4
.mul16_ret5:
    ADDI R7, R7, #2
    ADDI R7, R7, #2
    LD R5, [R7]
    ADDI R7, R7, #2
    LD R2, [R7]
    ST R1, [R2 + 0]
.fi2:
    LDI R1, #-202
    ADD R1, R1, R6
    LD R1, [R1 + 0]
    ADDI R2, R1, #1
    ST R2, [R7]
    ADDI R7, R7, #-2
    LDI R1, #-202
    ADD R1, R1, R6
    ADDI R7, R7, #2
    LD R2, [R7]
    ST R2, [R1 + 0]
    JMP .fc1
.fe3:
    XOR R1, R0, R0
    ST R1, [R7]
    ADDI R7, R7, #-2
    LDI R1, #-202
    ADD R1, R1, R6
    ADDI R7, R7, #2
    LD R2, [R7]
    ST R2, [R1 + 0]
.fc6:
    LDI R1, #-202
    ADD R1, R1, R6
    LD R1, [R1 + 0]
    ST R1, [R7]
    ADDI R7, R7, #-2
    LDI R1, #0x0064
    ADDI R7, R7, #2
    LD R2, [R7]
    BLT R2, R1, .cl9
    JMP .fe8
.cl9:
    LDI R1, #-204
    ADD R1, R1, R6
    LD R1, [R1 + 0]
    ST R1, [R7]
    ADDI R7, R7, #-2
    LDI R1, #-200
    ADD R1, R1, R6
    ST R1, [R7]
    ADDI R7, R7, #-2
    LDI R1, #-202
    ADD R1, R1, R6
    LD R1, [R1 + 0]
    ADDI R3, R0, #1
    SLL R1, R1, R3
    ADDI R7, R7, #2
    LD R2, [R7]
    ADD R1, R2, R1
    LD R1, [R1 + 0]
    ADDI R7, R7, #2
    LD R2, [R7]
    ADD R1, R2, R1
    ST R1, [R7]
    ADDI R7, R7, #-2
    LDI R1, #-204
    ADD R1, R1, R6
    ADDI R7, R7, #2
    LD R2, [R7]
    ST R2, [R1 + 0]
.fi7:
    LDI R1, #-202
    ADD R1, R1, R6
    LD R1, [R1 + 0]
    ADDI R2, R1, #1
    ST R2, [R7]
    ADDI R7, R7, #-2
    LDI R1, #-202
    ADD R1, R1, R6
    ADDI R7, R7, #2
    LD R2, [R7]
    ST R2, [R1 + 0]
    JMP .fc6
.fe8:
    LDI R1, #-204
    ADD R1, R1, R6
    LD R1, [R1 + 0]
main_epi:
    LDI R2, #204
    ADD R7, R7, R2
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
