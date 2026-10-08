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
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R1, R0, #2
    ADDI R7, R7, #2
    LD R2, [R7]
    SLT R3, R2, R1
    BR Z, R3, .el1
    LD R1, [R6 -1]
    JMP fib_epi
.el1:
.ei2:
    LD R1, [R6 -1]
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
    LD R1, [R6 -1]
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

.w5:
    LD R1, [R6 -1]
    LD R1, [R1 + 0]
    BR Z, R1, .we6
    LD R1, [R6 -1]
    LD R1, [R1 + 0]
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    LDI R5, #.cr7
    LDI R1, #putchar
    JMP R1
.cr7:
    LD R1, [R6 -1]
    ADDI R2, R1, #1
    ST R2, [R6 -1]
    JMP .w5
.we6:
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
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R1, R0, #10
    ADDI R7, R7, #2
    LD R2, [R7]
    SLT R3, R2, R1
    BR Z, R3, .el8
    LDI R1, #0x0030
    ST R1, [R7]
    ADDI R7, R7, #-2
    LD R1, [R6 -1]
    ADDI R7, R7, #2
    LD R2, [R7]
    ADD R1, R2, R1
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    LDI R5, #.cr10
    LDI R1, #putchar
    JMP R1
.cr10:
    XOR R1, R0, R0
    JMP print_dec_epi
.el8:
.ei9:
    LD R1, [R6 -1]
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R1, R0, #10
    ADDI R7, R7, #2
    LD R2, [R7]
    ST R5, [R7]
    ADDI R7, R7, #-2
    ST R1, [R7]
    ADDI R7, R7, #-2
    LDI R5, #.div16_ret11
    LDI R4, #__div16
    LD R1, [R7 +1]
    JMP R4
.div16_ret11:
    ADDI R7, R7, #2
    ADDI R7, R7, #2
    LD R5, [R7]
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    LDI R5, #.cr12
    LDI R1, #print_dec
    JMP R1
.cr12:
    LDI R1, #0x0030
    ST R1, [R7]
    ADDI R7, R7, #-2
    LD R1, [R6 -1]
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R1, R0, #10
    ADDI R7, R7, #2
    LD R2, [R7]
    ST R5, [R7]
    ADDI R7, R7, #-2
    ST R1, [R7]
    ADDI R7, R7, #-2
    LDI R5, #.mod16_ret13
    LDI R4, #__mod16
    LD R1, [R7 +1]
    JMP R4
.mod16_ret13:
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
    LDI R5, #.cr14
    LDI R1, #putchar
    JMP R1
.cr14:
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
.w15:
    LDI R5, #.cr17
    LDI R1, #getchar
    JMP R1
.cr17:
    ST R1, [R6 -2]
    LD R1, [R6 -2]
    ST R1, [R7]
    ADDI R7, R7, #-2
    LDI R1, #0x0030
    ADDI R7, R7, #2
    LD R2, [R7]
    SLT R3, R2, R1
    BR Z, R3, .el18
    LD R1, [R6 -2]
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R1, R0, #10
    ADDI R7, R7, #2
    LD R2, [R7]
    XOR R1, R2, R1
    BR NZ, R1, .lo22
    JMP .lor23
.lo22:
    LD R1, [R6 -2]
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R1, R0, #13
    ADDI R7, R7, #2
    LD R2, [R7]
    XOR R1, R2, R1
    BR NZ, R1, .el20
.lor23:
    ADDI R1, R0, #10
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    LDI R5, #.cr24
    LDI R1, #putchar
    JMP R1
.cr24:
    LD R1, [R6 -1]
    JMP read_dec_epi
.el20:
.ei21:
    JMP .ei19
.el18:
    LD R1, [R6 -2]
    ST R1, [R7]
    ADDI R7, R7, #-2
    LDI R1, #0x0039
    ADDI R7, R7, #2
    LD R2, [R7]
    SLT R3, R1, R2
    BR Z, R3, .el25
    JMP .ei19
.el25:
    LD R1, [R6 -2]
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    LDI R5, #.cr26
    LDI R1, #putchar
    JMP R1
.cr26:
    LD R1, [R6 -1]
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R1, R0, #10
    ADDI R7, R7, #2
    LD R2, [R7]
    ST R5, [R7]
    ADDI R7, R7, #-2
    ST R1, [R7]
    ADDI R7, R7, #-2
    LDI R5, #.mul16_ret27
    LDI R4, #__mul16
    LD R1, [R7 +1]
    JMP R4
.mul16_ret27:
    ADDI R7, R7, #2
    ADDI R7, R7, #2
    LD R5, [R7]
    ST R1, [R7]
    ADDI R7, R7, #-2
    LD R1, [R6 -2]
    ST R1, [R7]
    ADDI R7, R7, #-2
    LDI R1, #0x0030
    ADDI R7, R7, #2
    LD R2, [R7]
    SUB R1, R2, R1
    ADDI R7, R7, #2
    LD R2, [R7]
    ADD R1, R2, R1
    ST R1, [R6 -1]
.ei19:
    JMP .w15
.we16:
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

.w28:
    LDI R1, #0x0045
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    LDI R5, #.cr30
    LDI R1, #putchar
    JMP R1
.cr30:
    LDI R1, #0x006E
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    LDI R5, #.cr31
    LDI R1, #putchar
    JMP R1
.cr31:
    LDI R1, #0x0074
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    LDI R5, #.cr32
    LDI R1, #putchar
    JMP R1
.cr32:
    LDI R1, #0x0065
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    LDI R5, #.cr33
    LDI R1, #putchar
    JMP R1
.cr33:
    LDI R1, #0x0072
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    LDI R5, #.cr34
    LDI R1, #putchar
    JMP R1
.cr34:
    LDI R1, #0x0020
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    LDI R5, #.cr35
    LDI R1, #putchar
    JMP R1
.cr35:
    LDI R1, #0x004E
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    LDI R5, #.cr36
    LDI R1, #putchar
    JMP R1
.cr36:
    LDI R1, #0x003A
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    LDI R5, #.cr37
    LDI R1, #putchar
    JMP R1
.cr37:
    LDI R1, #0x0020
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    LDI R5, #.cr38
    LDI R1, #putchar
    JMP R1
.cr38:
    LDI R5, #.cr39
    LDI R1, #read_dec
    JMP R1
.cr39:
    ST R1, [R6 -1]
    LD R1, [R6 -1]
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    LDI R5, #.cr40
    LDI R1, #fib
    JMP R1
.cr40:
    ST R1, [R6 -2]
    LDI R1, #0x0066
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    LDI R5, #.cr41
    LDI R1, #putchar
    JMP R1
.cr41:
    LDI R1, #0x0069
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    LDI R5, #.cr42
    LDI R1, #putchar
    JMP R1
.cr42:
    LDI R1, #0x0062
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    LDI R5, #.cr43
    LDI R1, #putchar
    JMP R1
.cr43:
    LDI R1, #0x0028
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    LDI R5, #.cr44
    LDI R1, #putchar
    JMP R1
.cr44:
    LD R1, [R6 -1]
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    LDI R5, #.cr45
    LDI R1, #print_dec
    JMP R1
.cr45:
    LDI R1, #0x0029
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    LDI R5, #.cr46
    LDI R1, #putchar
    JMP R1
.cr46:
    LDI R1, #0x0020
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    LDI R5, #.cr47
    LDI R1, #putchar
    JMP R1
.cr47:
    LDI R1, #0x003D
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    LDI R5, #.cr48
    LDI R1, #putchar
    JMP R1
.cr48:
    LDI R1, #0x0020
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    LDI R5, #.cr49
    LDI R1, #putchar
    JMP R1
.cr49:
    LD R1, [R6 -2]
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    LDI R5, #.cr50
    LDI R1, #print_dec
    JMP R1
.cr50:
    ADDI R1, R0, #10
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    LDI R5, #.cr51
    LDI R1, #putchar
    JMP R1
.cr51:
    JMP .w28
.we29:
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