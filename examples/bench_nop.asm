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

    XOR R1, R0, R0
    ST R1, [R6 -2]
    XOR R1, R0, R0
    ST R1, [R6 -1]
.fc1:
    LD R1, [R6 -1]
    MOV R2, R1
    LDI R1, #0x03E8
    SLT R3, R2, R1
    BR Z, R3, .fe3
    LD R1, [R6 -2]
    ADDI R1, R1, #1
    ST R1, [R6 -2]
.fi2:
    LD R1, [R6 -1]
    ADDI R2, R1, #1
    ST R2, [R6 -1]
    JMPR .fc1
.fe3:
    LD R1, [R6 -2]
main_epi:
    ADDI R7, R7, #4
    ADDI R7, R7, #2
    LD R6, [R7]
    ADDI R7, R7, #2
    LD R5, [R7]
    JMP R5
