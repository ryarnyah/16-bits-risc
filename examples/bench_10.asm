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
    ADDI R7, R7, #-24

    XOR R1, R0, R0
    ST R1, [R6 -24]
    XOR R1, R0, R0
    ST R1, [R6 -22]
.fc1:
    LD R1, [R6 -22]
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R1, R0, #10
    ADDI R7, R7, #2
    LD R2, [R7]
    BLT R2, R1, .cl4
    JMP .fe3
.cl4:
    ADDI R1, R6, #-20
    ST R1, [R7]
    ADDI R7, R7, #-2
    LD R1, [R6 -22]
    ADDI R3, R0, #1
    SLL R1, R1, R3
    ADDI R7, R7, #2
    LD R2, [R7]
    ADD R1, R2, R1
    ST R1, [R7]
    ADDI R7, R7, #-2
    LD R1, [R6 -22]
    ADDI R7, R7, #2
    LD R2, [R7]
    ST R1, [R2 + 0]
.fi2:
    LD R1, [R6 -22]
    ADDI R2, R1, #1
    ST R2, [R6 -22]
    JMP .fc1
.fe3:
    XOR R1, R0, R0
    ST R1, [R6 -22]
.fc5:
    LD R1, [R6 -22]
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R1, R0, #10
    ADDI R7, R7, #2
    LD R2, [R7]
    BLT R2, R1, .cl8
    JMP .fe7
.cl8:
    LD R1, [R6 -24]
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R1, R6, #-20
    ST R1, [R7]
    ADDI R7, R7, #-2
    LD R1, [R6 -22]
    ADDI R3, R0, #1
    SLL R1, R1, R3
    ADDI R7, R7, #2
    LD R2, [R7]
    ADD R1, R2, R1
    LD R1, [R1 + 0]
    ADDI R7, R7, #2
    LD R2, [R7]
    ADD R1, R2, R1
    ST R1, [R6 -24]
.fi6:
    LD R1, [R6 -22]
    ADDI R2, R1, #1
    ST R2, [R6 -22]
    JMP .fc5
.fe7:
    LD R1, [R6 -24]
main_epi:
    ADDI R7, R7, #24
    ADDI R7, R7, #2
    LD R6, [R7]
    ADDI R7, R7, #2
    LD R5, [R7]
    JMP R5
