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
    ADDI R7, R7, #-10

    XOR R1, R0, R0
    ST R1, [R6 -5]
    XOR R1, R0, R0
    ST R1, [R6 -4]
.fc1:
    LD R1, [R6 -4]
    MOV R2, R1
    ADDI R1, R0, #3
    SLT R3, R2, R1
    BR Z, R3, .fe3
    ADDI R1, R6, #-6
    ST R1, [R7]
    ADDI R7, R7, #-2
    LD R1, [R6 -4]
    ADDI R3, R0, #1
    SLL R1, R1, R3
    ADDI R7, R7, #2
    LD R2, [R7]
    ADD R1, R2, R1
    ST R1, [R7]
    ADDI R7, R7, #-2
    LD R1, [R6 -4]
    ADDI R7, R7, #2
    LD R2, [R7]
    ST R1, [R2 + 0]
.fi2:
    LD R1, [R6 -4]
    ADDI R2, R1, #1
    ST R2, [R6 -4]
    JMPR .fc1
.fe3:
    XOR R1, R0, R0
    ST R1, [R6 -4]
.fc4:
    LD R1, [R6 -4]
    MOV R2, R1
    ADDI R1, R0, #3
    SLT R3, R2, R1
    BR Z, R3, .fe6
    LD R1, [R6 -5]
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R1, R6, #-6
    ST R1, [R7]
    ADDI R7, R7, #-2
    LD R1, [R6 -4]
    ADDI R3, R0, #1
    SLL R1, R1, R3
    ADDI R7, R7, #2
    LD R2, [R7]
    ADD R1, R2, R1
    LD R1, [R1 + 0]
    ADDI R7, R7, #2
    LD R2, [R7]
    ADD R1, R2, R1
    ST R1, [R6 -5]
.fi5:
    LD R1, [R6 -4]
    ADDI R2, R1, #1
    ST R2, [R6 -4]
    JMPR .fc4
.fe6:
    LD R1, [R6 -5]
main_epi:
    ADDI R7, R7, #10
    ADDI R7, R7, #2
    LD R6, [R7]
    ADDI R7, R7, #2
    LD R5, [R7]
    JMP R5
