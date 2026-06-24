; C runtime startup
    LDI R7, #0x1FFA
    LDI R5, #_exit
    LDI R1, #main
    JMP R1
_exit:
    JMP R5


__data_init:
__data_init_end:
;--- gcd(...)
gcd:
    ST R5, [R7]
    ADDI R7, R7, #-2
    ST R6, [R7]
    ADDI R7, R7, #-2
    ADD R6, R7, R0
    ADDI R6, R6, #2
    ADDI R7, R7, #-6
    ST R2, [R6 -4]
    ST R3, [R6 -6]

.w1:
    LD R1, [R6 -6]
    ST R1, [R7]
    ADDI R7, R7, #-2
    XOR R1, R0, R0
    ADDI R7, R7, #2
    LD R2, [R7]
    XOR R1, R2, R1
    BEQ R1, R0, .we2
    LD R1, [R6 -4]
    ST R1, [R7]
    ADDI R7, R7, #-2
    LD R1, [R6 -6]
    ADDI R7, R7, #2
    LD R2, [R7]
    ST R5, [R7]
    ADDI R7, R7, #-2
    ST R1, [R7]
    ADDI R7, R7, #-2
    LDI R5, #.mod16_ret3
    LDI R4, #__mod16
    LD R1, [R7 +2]
    JMP R4
.mod16_ret3:
    ADDI R7, R7, #2
    ADDI R7, R7, #2
    LD R5, [R7]
    ST R1, [R6 -2]
    LD R1, [R6 -6]
    ST R1, [R6 -4]
    LD R1, [R6 -2]
    ST R1, [R6 -6]
    JMP .w1
.we2:
    LD R1, [R6 -4]
gcd_epi:
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

    ADDI R1, R0, #18
    ST R1, [R7]
    ADDI R7, R7, #-2
    LDI R1, #0x0030
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    ADDI R7, R7, #2
    LD R3, [R7]
    LDI R5, #.cr4
    LDI R1, #gcd
    JMP R1
.cr4:
main_epi:
    ADDI R7, R7, #2
    LD R6, [R7]
    ADDI R7, R7, #2
    LD R5, [R7]
    JMP R5


; ===== Runtime Library =====

__mod16:
    BEQ R1, R0, __div_exit
    ST R5, [R7]
    ADDI R7, R7, #-2
    ST R6, [R7]
    ADDI R7, R7, #-2
    XOR R3, R3, R3
    ADDI R4, R0, #16
    XOR R5, R5, R5
__mod_lp:
    ADDI R6, R0, #1
    SLL R5, R5, R6
    LDI R6, #0x8000
    AND R6, R2, R6
    BEQ R6, R0, __mod_nb
    ADDI R6, R0, #1
    OR R5, R5, R6
__mod_nb:
    ADDI R6, R0, #1
    SLL R2, R2, R6
    SUB R6, R5, R1
    BLT R5, R1, __mod_sk
    ADD R5, R0, R6
__mod_sk:
    ADDI R4, R4, #-1
    BNE R4, R0, __mod_lp
    ADD R1, R0, R5
    ADDI R7, R7, #2
    LD R6, [R7]
    ADDI R7, R7, #2
    LD R5, [R7]
    JMP R5

__div_exit:
    XOR R1, R0, R0
    JMP R5