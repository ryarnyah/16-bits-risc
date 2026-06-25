; C runtime startup
    LDI R7, #0x1FFA
    LDI R5, #_exit
    LDI R1, #main
    JMP R1
_exit:
    JMP R5


__data_init:
__data_init_end:
;--- test_beq_backward(...)
test_beq_backward:
    ST R5, [R7]
    ADDI R7, R7, #-2
    ST R6, [R7]
    ADDI R7, R7, #-2
    ADD R6, R7, R0
    ADDI R6, R6, #2
    ADDI R7, R7, #-6
    ST R2, [R6 -6]

    ADDI R1, R0, #1
    ST R1, [R6 -2]
    XOR R1, R0, R0
    ST R1, [R6 -4]
    LD R1, [R6 -2]
    ST R1, [R7]
    ADDI R7, R7, #-2
    LD R1, [R6 -4]
    ADDI R7, R7, #2
    LD R2, [R7]
    XOR R1, R2, R1
    BNE R1, R0, .el1
    XOR R1, R0, R0
    JMP .ei2
.el1:
.ei2:
    ADDI R1, R0, #5
    ST R1, [R6 -2]
    LD R1, [R6 -2]
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R1, R0, #5
    ADDI R7, R7, #2
    LD R2, [R7]
    XOR R1, R2, R1
    BNE R1, R0, .el3
    ADDI R1, R0, #1
    JMP .ei4
.el3:
.ei4:
    LDI R1, #0x00FF
test_beq_backward_epi:
    ADDI R7, R7, #6
    ADDI R7, R7, #2
    LD R6, [R7]
    ADDI R7, R7, #2
    LD R5, [R7]
    JMP R5

;--- test_bne_forward(...)
test_bne_forward:
    ST R5, [R7]
    ADDI R7, R7, #-2
    ST R6, [R7]
    ADDI R7, R7, #-2
    ADD R6, R7, R0
    ADDI R6, R6, #2
    ADDI R7, R7, #-6
    ST R2, [R6 -6]

    ADDI R1, R0, #1
    ST R1, [R6 -2]
    ADDI R1, R0, #2
    ST R1, [R6 -4]
    LD R1, [R6 -2]
    ST R1, [R7]
    ADDI R7, R7, #-2
    LD R1, [R6 -4]
    ADDI R7, R7, #2
    LD R2, [R7]
    XOR R1, R2, R1
    BEQ R1, R0, .el5
    ADDI R1, R0, #2
    JMP .ei6
.el5:
.ei6:
    LDI R1, #0x00FF
test_bne_forward_epi:
    ADDI R7, R7, #6
    ADDI R7, R7, #2
    LD R6, [R7]
    ADDI R7, R7, #2
    LD R5, [R7]
    JMP R5

;--- test_blt_forward(...)
test_blt_forward:
    ST R5, [R7]
    ADDI R7, R7, #-2
    ST R6, [R7]
    ADDI R7, R7, #-2
    ADD R6, R7, R0
    ADDI R6, R6, #2
    ADDI R7, R7, #-6
    ST R2, [R6 -6]

    ADDI R1, R0, #1
    ST R1, [R6 -2]
    ADDI R1, R0, #10
    ST R1, [R6 -4]
    LD R1, [R6 -2]
    ST R1, [R7]
    ADDI R7, R7, #-2
    LD R1, [R6 -4]
    ADDI R7, R7, #2
    LD R2, [R7]
    BLT R2, R1, .cl9
    JMP .el7
.cl9:
    ADDI R1, R0, #3
    JMP .ei8
.el7:
.ei8:
    LDI R1, #0x00FF
test_blt_forward_epi:
    ADDI R7, R7, #6
    ADDI R7, R7, #2
    LD R6, [R7]
    ADDI R7, R7, #2
    LD R5, [R7]
    JMP R5

;--- test_beq_large_forward(...)
test_beq_large_forward:
    ST R5, [R7]
    ADDI R7, R7, #-2
    ST R6, [R7]
    ADDI R7, R7, #-2
    ADD R6, R7, R0
    ADDI R6, R6, #2
    ADDI R7, R7, #-8
    ST R2, [R6 -8]

    XOR R1, R0, R0
    ST R1, [R6 -2]
    XOR R1, R0, R0
    ST R1, [R6 -4]
.fc10:
    LD R1, [R6 -6]
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R1, R0, #20
    ADDI R7, R7, #2
    LD R2, [R7]
    BLT R2, R1, .cl13
    JMP .fe12
.cl13:
.fi11:
    JMP .fc10
.fe12:
    LD R1, [R6 -2]
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R1, R0, #20
    ADDI R7, R7, #2
    LD R2, [R7]
    XOR R1, R2, R1
    BNE R1, R0, .el14
    ADDI R1, R0, #4
    JMP .ei15
.el14:
.ei15:
    LDI R1, #0x00FF
test_beq_large_forward_epi:
    ADDI R7, R7, #8
    ADDI R7, R7, #2
    LD R6, [R7]
    ADDI R7, R7, #2
    LD R5, [R7]
    JMP R5

;--- test_blt_negative(...)
test_blt_negative:
    ST R5, [R7]
    ADDI R7, R7, #-2
    ST R6, [R7]
    ADDI R7, R7, #-2
    ADD R6, R7, R0
    ADDI R6, R6, #2
    ADDI R7, R7, #-6
    ST R2, [R6 -6]

    ADDI R1, R0, #-5
    ST R1, [R6 -2]
    ADDI R1, R0, #-10
    ST R1, [R6 -4]
    LD R1, [R6 -2]
    ST R1, [R7]
    ADDI R7, R7, #-2
    LD R1, [R6 -4]
    ADDI R7, R7, #2
    LD R2, [R7]
    BLT R2, R1, .cl18
    JMP .el16
.cl18:
    LDI R1, #0x00FF
    JMP .ei17
.el16:
.ei17:
    ADDI R1, R0, #5
test_blt_negative_epi:
    ADDI R7, R7, #6
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
    ADDI R7, R7, #-4
    ST R2, [R6 -4]

    XOR R1, R0, R0
    ST R1, [R6 -2]
    LD R1, [R6 -2]
    ST R1, [R7]
    ADDI R7, R7, #-2
    LDI R5, #.cr19
    LDI R1, #test_beq_backward
    JMP R1
.cr19:
    ADDI R7, R7, #2
    LD R2, [R7]
    ADD R1, R2, R1
    ST R1, [R6 -2]
    LD R1, [R6 -2]
    ST R1, [R7]
    ADDI R7, R7, #-2
    LDI R5, #.cr20
    LDI R1, #test_bne_forward
    JMP R1
.cr20:
    ADDI R7, R7, #2
    LD R2, [R7]
    ADD R1, R2, R1
    ST R1, [R6 -2]
    LD R1, [R6 -2]
    ST R1, [R7]
    ADDI R7, R7, #-2
    LDI R5, #.cr21
    LDI R1, #test_blt_forward
    JMP R1
.cr21:
    ADDI R7, R7, #2
    LD R2, [R7]
    ADD R1, R2, R1
    ST R1, [R6 -2]
    LD R1, [R6 -2]
    ST R1, [R7]
    ADDI R7, R7, #-2
    LDI R5, #.cr22
    LDI R1, #test_beq_large_forward
    JMP R1
.cr22:
    ADDI R7, R7, #2
    LD R2, [R7]
    ADD R1, R2, R1
    ST R1, [R6 -2]
    LD R1, [R6 -2]
    ST R1, [R7]
    ADDI R7, R7, #-2
    LDI R5, #.cr23
    LDI R1, #test_blt_negative
    JMP R1
.cr23:
    ADDI R7, R7, #2
    LD R2, [R7]
    ADD R1, R2, R1
    ST R1, [R6 -2]
    LD R1, [R6 -2]
main_epi:
    ADDI R7, R7, #4
    ADDI R7, R7, #2
    LD R6, [R7]
    ADDI R7, R7, #2
    LD R5, [R7]
    JMP R5
