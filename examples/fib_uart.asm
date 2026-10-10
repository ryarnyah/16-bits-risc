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
    ADDI R1, R0, #2
    SLT R3, R2, R1
    BR Z, R3, .el1
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

;--- putchar(...)
putchar:
    ST R5, [R7]
    ADDI R7, R7, #-2
    ST R6, [R7]
    ADDI R7, R7, #-2
    ADD R6, R7, R0
    ADDI R6, R6, #2
    ADDI R7, R7, #-4
    ST R2, [R6 -2]

    LDI R1, #0x1FFE
    ST R1, [R6 -1]
    LD R1, [R6 -1]
    ST R1, [R7]
    ADDI R7, R7, #-2
    LD R1, [R6 -2]
    ADDI R7, R7, #2
    LD R2, [R7]
    ST R1, [R2 + 0]
putchar_epi:
    ADDI R7, R7, #4
    ADDI R7, R7, #2
    LD R6, [R7]
    ADDI R7, R7, #2
    LD R5, [R7]
    JMP R5

;--- getchar(...)
getchar:
    ST R5, [R7]
    ADDI R7, R7, #-2
    ST R6, [R7]
    ADDI R7, R7, #-2
    ADD R6, R7, R0
    ADDI R6, R6, #2
    ADDI R7, R7, #-4
    ST R2, [R6 -2]

    LDI R1, #0x1FFC
    ST R1, [R6 -1]
    LD R1, [R6 -1]
    LD R1, [R1 + 0]
getchar_epi:
    ADDI R7, R7, #4
    ADDI R7, R7, #2
    LD R6, [R7]
    ADDI R7, R7, #2
    LD R5, [R7]
    JMP R5

;--- print_str(...)
print_str:
    ST R5, [R7]
    ADDI R7, R7, #-2
    ST R6, [R7]
    ADDI R7, R7, #-2
    ADD R6, R7, R0
    ADDI R6, R6, #2
    ADDI R7, R7, #-2
    ST R2, [R6 -1]

.w3:
    LD R1, [R6 -1]
    LD R1, [R1 + 0]
    BR Z, R1, .we4
    LD R1, [R6 -1]
    LD R1, [R1 + 0]
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    CALLR R5, putchar
    LD R1, [R6 -1]
    ADDI R2, R1, #1
    ST R2, [R6 -1]
    JMPR .w3
.we4:
print_str_epi:
    ADDI R7, R7, #2
    ADDI R7, R7, #2
    LD R6, [R7]
    ADDI R7, R7, #2
    LD R5, [R7]
    JMP R5

;--- print_dec(...)
print_dec:
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
    ADDI R1, R0, #10
    SLT R3, R2, R1
    BR Z, R3, .el5
    LDI R1, #0x0030
    MOV R2, R1
    LD R1, [R6 -1]
    ADD R1, R2, R1
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    CALLR R5, putchar
    XOR R1, R0, R0
    JMPR print_dec_epi
.el5:
.ei6:
    LD R1, [R6 -1]
    MOV R2, R1
    ADDI R1, R0, #10
    ST R5, [R7]
    ADDI R7, R7, #-2
    ST R1, [R7]
    ADDI R7, R7, #-2
    CALLR R5, __div16
.div16_ret7:
    ADDI R7, R7, #2
    ADDI R7, R7, #2
    LD R5, [R7]
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    CALLR R5, print_dec
    LDI R1, #0x0030
    ST R1, [R7]
    ADDI R7, R7, #-2
    LD R1, [R6 -1]
    MOV R2, R1
    ADDI R1, R0, #10
    ST R5, [R7]
    ADDI R7, R7, #-2
    ST R1, [R7]
    ADDI R7, R7, #-2
    CALLR R5, __mod16
.mod16_ret8:
    ADDI R7, R7, #2
    ADDI R7, R7, #2
    LD R5, [R7]
    ADDI R7, R7, #2
    LD R2, [R7]
    ADD R1, R2, R1
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    CALLR R5, putchar
print_dec_epi:
    ADDI R7, R7, #2
    ADDI R7, R7, #2
    LD R6, [R7]
    ADDI R7, R7, #2
    LD R5, [R7]
    JMP R5

;--- read_dec(...)
read_dec:
    ST R5, [R7]
    ADDI R7, R7, #-2
    ST R6, [R7]
    ADDI R7, R7, #-2
    ADD R6, R7, R0
    ADDI R6, R6, #2
    ADDI R7, R7, #-6
    ST R2, [R6 -3]

    XOR R1, R0, R0
    ST R1, [R6 -1]
.w9:
    CALLR R5, getchar
    ST R1, [R6 -2]
    LD R1, [R6 -2]
    MOV R2, R1
    LDI R1, #0x0030
    SLT R3, R2, R1
    BR Z, R3, .el11
    LD R1, [R6 -2]
    MOV R2, R1
    ADDI R1, R0, #10
    XOR R1, R2, R1
    BR NZ, R1, .lo15
    JMPR .lor16
.lo15:
    LD R1, [R6 -2]
    MOV R2, R1
    ADDI R1, R0, #13
    XOR R1, R2, R1
    BR NZ, R1, .el13
.lor16:
    ADDI R1, R0, #10
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    CALLR R5, putchar
    LD R1, [R6 -1]
    JMPR read_dec_epi
.el13:
.ei14:
    JMPR .ei12
.el11:
    LD R1, [R6 -2]
    MOV R2, R1
    LDI R1, #0x0039
    SLT R3, R1, R2
    BR Z, R3, .el17
    JMPR .ei12
.el17:
    LD R1, [R6 -2]
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    CALLR R5, putchar
    LD R1, [R6 -1]
    MOV R2, R1
    ADDI R1, R0, #10
    ST R5, [R7]
    ADDI R7, R7, #-2
    ST R1, [R7]
    ADDI R7, R7, #-2
    CALLR R5, __mul16
.mul16_ret18:
    ADDI R7, R7, #2
    ADDI R7, R7, #2
    LD R5, [R7]
    ST R1, [R7]
    ADDI R7, R7, #-2
    LD R1, [R6 -2]
    MOV R2, R1
    LDI R1, #0x0030
    SUB R1, R2, R1
    ADDI R7, R7, #2
    LD R2, [R7]
    ADD R1, R2, R1
    ST R1, [R6 -1]
.ei12:
    JMPR .w9
.we10:
read_dec_epi:
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

.w19:
    LDI R1, #0x0045
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    CALLR R5, putchar
    LDI R1, #0x006E
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    CALLR R5, putchar
    LDI R1, #0x0074
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    CALLR R5, putchar
    LDI R1, #0x0065
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    CALLR R5, putchar
    LDI R1, #0x0072
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    CALLR R5, putchar
    LDI R1, #0x0020
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    CALLR R5, putchar
    LDI R1, #0x004E
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    CALLR R5, putchar
    LDI R1, #0x003A
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    CALLR R5, putchar
    LDI R1, #0x0020
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    CALLR R5, putchar
    CALLR R5, read_dec
    ST R1, [R6 -1]
    LD R1, [R6 -1]
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    CALLR R5, fib
    ST R1, [R6 -2]
    LDI R1, #0x0066
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    CALLR R5, putchar
    LDI R1, #0x0069
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    CALLR R5, putchar
    LDI R1, #0x0062
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    CALLR R5, putchar
    LDI R1, #0x0028
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    CALLR R5, putchar
    LD R1, [R6 -1]
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    CALLR R5, print_dec
    LDI R1, #0x0029
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    CALLR R5, putchar
    LDI R1, #0x0020
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    CALLR R5, putchar
    LDI R1, #0x003D
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    CALLR R5, putchar
    LDI R1, #0x0020
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    CALLR R5, putchar
    LD R1, [R6 -2]
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    CALLR R5, print_dec
    ADDI R1, R0, #10
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    CALLR R5, putchar
    JMPR .w19
.we20:
    XOR R1, R0, R0
main_epi:
    ADDI R7, R7, #4
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
    BR Z, R6, __mul_sk
    ADD R3, R3, R2
__mul_sk:
    ADDI R6, R0, #1
    SLL R2, R2, R6
    SRL R1, R1, R6
    ADDI R4, R4, #-1
    BR NZ, R4, __mul_lp
    ADD R1, R0, R3
    ADDI R7, R7, #2
    LD R6, [R7]
    JMP R5

__div16:
    BR Z, R1, __div_exit
    ST R5, [R7]
    ADDI R7, R7, #-2
    ST R6, [R7]
    ADDI R7, R7, #-2
    XOR R3, R3, R3
    ADDI R4, R0, #16
    XOR R5, R5, R5
__div_lp:
    ADDI R6, R0, #1
    SLL R5, R5, R6
    SLL R3, R3, R6
    LDI R6, #0x8000
    AND R6, R2, R6
    BR Z, R6, __div_nb
    ADDI R6, R0, #1
    OR R5, R5, R6
__div_nb:
    ADDI R6, R0, #1
    SLL R2, R2, R6
    SLT R6, R5, R1
    BR NZ, R6, __div_sk
    SUB R5, R5, R1
    ADDI R6, R0, #1
    OR R3, R3, R6
__div_sk:
    ADDI R4, R4, #-1
    BR NZ, R4, __div_lp
    ADD R1, R0, R3
    ADDI R7, R7, #2
    LD R6, [R7]
    ADDI R7, R7, #2
    LD R5, [R7]
    JMP R5

__mod16:
    BR Z, R1, __div_exit
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
    BR Z, R6, __mod_nb
    ADDI R6, R0, #1
    OR R5, R5, R6
__mod_nb:
    ADDI R6, R0, #1
    SLL R2, R2, R6
    SLT R6, R5, R1
    BR NZ, R6, __mod_sk
    SUB R5, R5, R1
__mod_sk:
    ADDI R4, R4, #-1
    BR NZ, R4, __mod_lp
    ADD R1, R0, R5
    ADDI R7, R7, #2
    LD R6, [R7]
    ADDI R7, R7, #2
    LD R5, [R7]
    JMP R5

__div_exit:
    XOR R1, R0, R0
    JMP R5