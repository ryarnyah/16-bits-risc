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
;--- main(...)
main:
    ST R5, [R7]
    ADDI R7, R7, #-2
    ST R6, [R7]
    ADDI R7, R7, #-2
    ADD R6, R7, R0
    ADDI R6, R6, #2

    LDI R1, #0x007B
    ST R1, [R0 +0]
    LD R1, [R0 +0]
main_epi:
    ADDI R7, R7, #2
    LD R6, [R7]
    ADDI R7, R7, #2
    LD R5, [R7]
    JMP R5
