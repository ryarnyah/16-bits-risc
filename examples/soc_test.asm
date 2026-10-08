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
    SLT R3, R1, R4
    BR NZ, R3, .init1
    LDI R5, #_exit
    LDI R1, #main
    JMP R1
_exit:
    JMP R5


.Lstr0:
    .word 0x2020
    .word 0x505B
    .word 0x5341
    .word 0x5D53
    .word 0x0020
.Lstr1:
    .word 0x2020
    .word 0x465B
    .word 0x4941
    .word 0x5D4C
    .word 0x0020
.Lstr2:
    .word 0x2020
    .word 0x7954
    .word 0x6570
    .word 0x7420
    .word 0x6F77
    .word 0x6320
    .word 0x6168
    .word 0x7372
    .word 0x6620
    .word 0x726F
    .word 0x6520
    .word 0x6863
    .word 0x206F
    .word 0x6574
    .word 0x7473
    .word 0x203A
    .word 0x0000
.Lstr3:
    .word 0x3D3D
    .word 0x3D3D
    .word 0x3D3D
    .word 0x3D3D
    .word 0x3D3D
    .word 0x3D3D
    .word 0x3D3D
    .word 0x3D3D
    .word 0x3D3D
    .word 0x3D3D
    .word 0x3D3D
    .word 0x3D3D
    .word 0x3D3D
    .word 0x3D3D
    .word 0x3D3D
    .word 0x3D3D
    .word 0x3D3D
    .word 0x3D3D
    .word 0x3D3D
    .word 0x3D3D
    .word 0x000A
.Lstr4:
    .word 0x2020
    .word 0x3631
    .word 0x622D
    .word 0x7469
    .word 0x5220
    .word 0x5349
    .word 0x2043
    .word 0x6F53
    .word 0x2043
    .word 0x6554
    .word 0x7473
    .word 0x5320
    .word 0x6975
    .word 0x6574
    .word 0x000A
.Lstr5:
    .word 0x3D3D
    .word 0x3D3D
    .word 0x3D3D
    .word 0x3D3D
    .word 0x3D3D
    .word 0x3D3D
    .word 0x3D3D
    .word 0x3D3D
    .word 0x3D3D
    .word 0x3D3D
    .word 0x3D3D
    .word 0x3D3D
    .word 0x3D3D
    .word 0x3D3D
    .word 0x3D3D
    .word 0x3D3D
    .word 0x3D3D
    .word 0x3D3D
    .word 0x3D3D
    .word 0x3D3D
    .word 0x000A
.Lstr6:
    .word 0x2D2D
    .word 0x202D
    .word 0x4C41
    .word 0x2055
    .word 0x704F
    .word 0x7265
    .word 0x7461
    .word 0x6F69
    .word 0x736E
    .word 0x2D20
    .word 0x2D2D
    .word 0x000A
.Lstr7:
    .word 0x4C41
    .word 0x0055
.Lstr8:
    .word 0x4C41
    .word 0x0055
.Lstr9:
    .word 0x2D2D
    .word 0x202D
    .word 0x7242
    .word 0x6E61
    .word 0x6863
    .word 0x4920
    .word 0x736E
    .word 0x7274
    .word 0x6375
    .word 0x6974
    .word 0x6E6F
    .word 0x2073
    .word 0x2D2D
    .word 0x0A2D
    .word 0x0000
.Lstr10:
    .word 0x7242
    .word 0x6E61
    .word 0x6863
    .word 0x7365
    .word 0x0000
.Lstr11:
    .word 0x7242
    .word 0x6E61
    .word 0x6863
    .word 0x7365
    .word 0x0000
.Lstr12:
    .word 0x2D2D
    .word 0x202D
    .word 0x6144
    .word 0x6174
    .word 0x4D20
    .word 0x6D65
    .word 0x726F
    .word 0x2079
    .word 0x4C28
    .word 0x2F44
    .word 0x5453
    .word 0x2029
    .word 0x2D2D
    .word 0x0A2D
    .word 0x0000
.Lstr13:
    .word 0x654D
    .word 0x6F6D
    .word 0x7972
    .word 0x0000
.Lstr14:
    .word 0x654D
    .word 0x6F6D
    .word 0x7972
    .word 0x0000
.Lstr15:
    .word 0x2D2D
    .word 0x202D
    .word 0x7453
    .word 0x6361
    .word 0x206B
    .word 0x2026
    .word 0x7546
    .word 0x636E
    .word 0x6974
    .word 0x6E6F
    .word 0x4320
    .word 0x6C61
    .word 0x736C
    .word 0x2D20
    .word 0x2D2D
    .word 0x000A
.Lstr16:
    .word 0x7453
    .word 0x6361
    .word 0x006B
.Lstr17:
    .word 0x7453
    .word 0x6361
    .word 0x006B
.Lstr18:
    .word 0x2D2D
    .word 0x202D
    .word 0x7552
    .word 0x746E
    .word 0x6D69
    .word 0x2065
    .word 0x694C
    .word 0x7262
    .word 0x7261
    .word 0x2079
    .word 0x6D28
    .word 0x6C75
    .word 0x642F
    .word 0x7669
    .word 0x6D2F
    .word 0x646F
    .word 0x2029
    .word 0x2D2D
    .word 0x0A2D
    .word 0x0000
.Lstr19:
    .word 0x7552
    .word 0x746E
    .word 0x6D69
    .word 0x4C65
    .word 0x6269
    .word 0x0000
.Lstr20:
    .word 0x7552
    .word 0x746E
    .word 0x6D69
    .word 0x4C65
    .word 0x6269
    .word 0x0000
.Lstr21:
    .word 0x2D2D
    .word 0x202D
    .word 0x7241
    .word 0x6172
    .word 0x2079
    .word 0x6341
    .word 0x6563
    .word 0x7373
    .word 0x2D20
    .word 0x2D2D
    .word 0x000A
.Lstr22:
    .word 0x7241
    .word 0x6172
    .word 0x7379
    .word 0x0000
.Lstr23:
    .word 0x7241
    .word 0x6172
    .word 0x7379
    .word 0x0000
.Lstr24:
    .word 0x2D2D
    .word 0x202D
    .word 0x6F50
    .word 0x6E69
    .word 0x6574
    .word 0x2072
    .word 0x6544
    .word 0x6572
    .word 0x6566
    .word 0x6572
    .word 0x636E
    .word 0x2065
    .word 0x2D2D
    .word 0x0A2D
    .word 0x0000
.Lstr25:
    .word 0x6F50
    .word 0x6E69
    .word 0x6574
    .word 0x7372
    .word 0x0000
.Lstr26:
    .word 0x6F50
    .word 0x6E69
    .word 0x6574
    .word 0x7372
    .word 0x0000
.Lstr27:
    .word 0x2D2D
    .word 0x202D
    .word 0x614C
    .word 0x6772
    .word 0x2065
    .word 0x6F43
    .word 0x736E
    .word 0x6174
    .word 0x746E
    .word 0x2073
    .word 0x4C28
    .word 0x4944
    .word 0x2029
    .word 0x2D2D
    .word 0x0A2D
    .word 0x0000
.Lstr28:
    .word 0x444C
    .word 0x0049
.Lstr29:
    .word 0x444C
    .word 0x0049
.Lstr30:
    .word 0x2D2D
    .word 0x202D
    .word 0x4155
    .word 0x5452
    .word 0x5420
    .word 0x6172
    .word 0x736E
    .word 0x696D
    .word 0x2074
    .word 0x2D2D
    .word 0x0A2D
    .word 0x0000
.Lstr31:
    .word 0x4155
    .word 0x5452
    .word 0x5420
    .word 0x0058
.Lstr32:
    .word 0x4155
    .word 0x5452
    .word 0x5420
    .word 0x0058
.Lstr33:
    .word 0x2D2D
    .word 0x202D
    .word 0x4155
    .word 0x5452
    .word 0x5220
    .word 0x6365
    .word 0x6965
    .word 0x6576
    .word 0x2F20
    .word 0x4520
    .word 0x6863
    .word 0x206F
    .word 0x2D2D
    .word 0x0A2D
    .word 0x0000
.Lstr34:
    .word 0x4155
    .word 0x5452
    .word 0x5220
    .word 0x0058
.Lstr35:
    .word 0x4155
    .word 0x5452
    .word 0x5220
    .word 0x0058
.Lstr36:
    .word 0x3D3D
    .word 0x3D3D
    .word 0x3D3D
    .word 0x3D3D
    .word 0x3D3D
    .word 0x3D3D
    .word 0x3D3D
    .word 0x3D3D
    .word 0x3D3D
    .word 0x3D3D
    .word 0x3D3D
    .word 0x3D3D
    .word 0x3D3D
    .word 0x3D3D
    .word 0x3D3D
    .word 0x3D3D
    .word 0x3D3D
    .word 0x3D3D
    .word 0x3D3D
    .word 0x3D3D
    .word 0x000A
.Lstr37:
    .word 0x2020
    .word 0x6552
    .word 0x7573
    .word 0x746C
    .word 0x3A73
    .word 0x0020
.Lstr38:
    .word 0x002F
.Lstr39:
    .word 0x7020
    .word 0x7361
    .word 0x6573
    .word 0x0A64
    .word 0x0000
.Lstr40:
    .word 0x3D3D
    .word 0x3D3D
    .word 0x3D3D
    .word 0x3D3D
    .word 0x3D3D
    .word 0x3D3D
    .word 0x3D3D
    .word 0x3D3D
    .word 0x3D3D
    .word 0x3D3D
    .word 0x3D3D
    .word 0x3D3D
    .word 0x3D3D
    .word 0x3D3D
    .word 0x3D3D
    .word 0x3D3D
    .word 0x3D3D
    .word 0x3D3D
    .word 0x3D3D
    .word 0x3D3D
    .word 0x000A
__data_init:
    .word 0x0000
__data_init_end:
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

;--- print(...)
print:
    ST R5, [R7]
    ADDI R7, R7, #-2
    ST R6, [R7]
    ADDI R7, R7, #-2
    ADD R6, R7, R0
    ADDI R6, R6, #2
    ADDI R7, R7, #-2
    ST R2, [R6 -1]

.w2:
    LD R1, [R6 -1]
    LD R1, [R1 + 0]
    BR Z, R1, .we3
    LD R1, [R6 -1]
    LD R1, [R1 + 0]
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    CALLR R5, putchar
    LD R1, [R6 -1]
    ADDI R1, R1, #1
    ST R1, [R6 -1]
    JMPR .w2
.we3:
print_epi:
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
    XOR R1, R0, R0
    ADDI R7, R7, #2
    LD R2, [R7]
    SLT R3, R2, R1
    BR Z, R3, .el4
    LDI R1, #0x002D
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    CALLR R5, putchar
    XOR R1, R0, R0
    ST R1, [R7]
    ADDI R7, R7, #-2
    LD R1, [R6 -1]
    ADDI R7, R7, #2
    LD R2, [R7]
    SUB R1, R2, R1
    ST R1, [R6 -1]
.el4:
.ei5:
    LD R1, [R6 -1]
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R1, R0, #10
    ADDI R7, R7, #2
    LD R2, [R7]
    SLT R3, R2, R1
    BR NZ, R3, .el6
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
    CALLR R5, __div16
.div16_ret8:
    ADDI R7, R7, #2
    ADDI R7, R7, #2
    LD R5, [R7]
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    CALLR R5, print_dec
.el6:
.ei7:
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
    CALLR R5, __mod16
.mod16_ret9:
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

;--- print_hex(...)
print_hex:
    ST R5, [R7]
    ADDI R7, R7, #-2
    ST R6, [R7]
    ADDI R7, R7, #-2
    ADD R6, R7, R0
    ADDI R6, R6, #2
    ADDI R7, R7, #-6
    ST R2, [R6 -3]

    XOR R1, R0, R0
    ST R1, [R6 -2]
.w10:
    LD R1, [R6 -2]
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R1, R0, #4
    ADDI R7, R7, #2
    LD R2, [R7]
    SLT R3, R2, R1
    BR Z, R3, .we11
    LD R1, [R6 -3]
    SRAI R1, R1, #12
    ST R1, [R6 -1]
    LD R1, [R6 -1]
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R1, R0, #15
    ADDI R7, R7, #2
    LD R2, [R7]
    SLT R3, R1, R2
    BR Z, R3, .el12
    ADDI R1, R0, #15
    ST R1, [R6 -1]
.el12:
.ei13:
    LD R1, [R6 -1]
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R1, R0, #10
    ADDI R7, R7, #2
    LD R2, [R7]
    SLT R3, R2, R1
    BR Z, R3, .el14
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
    CALLR R5, putchar
    JMPR .ei15
.el14:
    LDI R1, #0x0041
    ST R1, [R7]
    ADDI R7, R7, #-2
    LD R1, [R6 -1]
    ADDI R7, R7, #2
    LD R2, [R7]
    ADD R1, R2, R1
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R1, R0, #10
    ADDI R7, R7, #2
    LD R2, [R7]
    SUB R1, R2, R1
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    CALLR R5, putchar
.ei15:
    LD R1, [R6 -3]
    SLLI R1, R1, #4
    ST R1, [R6 -3]
    LD R1, [R6 -2]
    ADDI R1, R1, #1
    ST R1, [R6 -2]
    JMPR .w10
.we11:
print_hex_epi:
    ADDI R7, R7, #6
    ADDI R7, R7, #2
    LD R6, [R7]
    ADDI R7, R7, #2
    LD R5, [R7]
    JMP R5

;--- nl(...)
nl:
    ST R5, [R7]
    ADDI R7, R7, #-2
    ST R6, [R7]
    ADDI R7, R7, #-2
    ADD R6, R7, R0
    ADDI R6, R6, #2
    ADDI R7, R7, #-2
    ST R2, [R6 -1]

    ADDI R1, R0, #10
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    CALLR R5, putchar
nl_epi:
    ADDI R7, R7, #2
    ADDI R7, R7, #2
    LD R6, [R7]
    ADDI R7, R7, #2
    LD R5, [R7]
    JMP R5

;--- pass(...)
pass:
    ST R5, [R7]
    ADDI R7, R7, #-2
    ST R6, [R7]
    ADDI R7, R7, #-2
    ADD R6, R7, R0
    ADDI R6, R6, #2
    ADDI R7, R7, #-2
    ST R2, [R6 -1]

    LDI R1, #.Lstr0
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    CALLR R5, print
    LD R1, [R6 -1]
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    CALLR R5, print
    ADDI R1, R0, #10
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    CALLR R5, putchar
pass_epi:
    ADDI R7, R7, #2
    ADDI R7, R7, #2
    LD R6, [R7]
    ADDI R7, R7, #2
    LD R5, [R7]
    JMP R5

;--- fail(...)
fail:
    ST R5, [R7]
    ADDI R7, R7, #-2
    ST R6, [R7]
    ADDI R7, R7, #-2
    ADD R6, R7, R0
    ADDI R6, R6, #2
    ADDI R7, R7, #-2
    ST R2, [R6 -1]

    LDI R1, #.Lstr1
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    CALLR R5, print
    LD R1, [R6 -1]
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    CALLR R5, print
    ADDI R1, R0, #10
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    CALLR R5, putchar
fail_epi:
    ADDI R7, R7, #2
    ADDI R7, R7, #2
    LD R6, [R7]
    ADDI R7, R7, #2
    LD R5, [R7]
    JMP R5

;--- test_alu(...)
test_alu:
    ST R5, [R7]
    ADDI R7, R7, #-2
    ST R6, [R7]
    ADDI R7, R7, #-2
    ADD R6, R7, R0
    ADDI R6, R6, #2
    ADDI R7, R7, #-10
    ST R2, [R6 -5]

    ADDI R1, R0, #1
    ST R1, [R6 -4]
    ADDI R1, R0, #10
    ST R1, [R6 -1]
    ADDI R1, R0, #20
    ST R1, [R6 -2]
    LD R1, [R6 -1]
    ST R1, [R7]
    ADDI R7, R7, #-2
    LD R1, [R6 -2]
    ADDI R7, R7, #2
    LD R2, [R7]
    ADD R1, R2, R1
    ST R1, [R6 -3]
    LD R1, [R6 -3]
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R1, R0, #30
    ADDI R7, R7, #2
    LD R2, [R7]
    XOR R1, R2, R1
    BR Z, R1, .el16
    XOR R1, R0, R0
    ST R1, [R6 -4]
.el16:
.ei17:
    LDI R1, #0x0064
    ST R1, [R6 -1]
    LD R1, [R6 -1]
    ST R1, [R7]
    ADDI R7, R7, #-2
    LDI R1, #0x0032
    ADDI R7, R7, #2
    LD R2, [R7]
    ADD R1, R2, R1
    ST R1, [R6 -3]
    LD R1, [R6 -3]
    ST R1, [R7]
    ADDI R7, R7, #-2
    LDI R1, #0x0096
    ADDI R7, R7, #2
    LD R2, [R7]
    XOR R1, R2, R1
    BR Z, R1, .el18
    XOR R1, R0, R0
    ST R1, [R6 -4]
.el18:
.ei19:
    LDI R1, #0x0032
    ST R1, [R6 -1]
    ADDI R1, R0, #30
    ST R1, [R6 -2]
    LD R1, [R6 -1]
    ST R1, [R7]
    ADDI R7, R7, #-2
    LD R1, [R6 -2]
    ADDI R7, R7, #2
    LD R2, [R7]
    SUB R1, R2, R1
    ST R1, [R6 -3]
    LD R1, [R6 -3]
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R1, R0, #20
    ADDI R7, R7, #2
    LD R2, [R7]
    XOR R1, R2, R1
    BR Z, R1, .el20
    XOR R1, R0, R0
    ST R1, [R6 -4]
.el20:
.ei21:
    LDI R1, #0x00FF
    ST R1, [R6 -1]
    ADDI R1, R0, #15
    ST R1, [R6 -2]
    LD R1, [R6 -1]
    ST R1, [R7]
    ADDI R7, R7, #-2
    LD R1, [R6 -2]
    ADDI R7, R7, #2
    LD R2, [R7]
    AND R1, R2, R1
    ST R1, [R6 -3]
    LD R1, [R6 -3]
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R1, R0, #15
    ADDI R7, R7, #2
    LD R2, [R7]
    XOR R1, R2, R1
    BR Z, R1, .el22
    XOR R1, R0, R0
    ST R1, [R6 -4]
.el22:
.ei23:
    LDI R1, #0x00F0
    ST R1, [R6 -1]
    ADDI R1, R0, #15
    ST R1, [R6 -2]
    LD R1, [R6 -1]
    ST R1, [R7]
    ADDI R7, R7, #-2
    LD R1, [R6 -2]
    ADDI R7, R7, #2
    LD R2, [R7]
    OR R1, R2, R1
    ST R1, [R6 -3]
    LD R1, [R6 -3]
    ST R1, [R7]
    ADDI R7, R7, #-2
    LDI R1, #0x00FF
    ADDI R7, R7, #2
    LD R2, [R7]
    XOR R1, R2, R1
    BR Z, R1, .el24
    XOR R1, R0, R0
    ST R1, [R6 -4]
.el24:
.ei25:
    LDI R1, #0x00FF
    ST R1, [R6 -1]
    LDI R1, #0x00FF
    ST R1, [R6 -2]
    LD R1, [R6 -1]
    ST R1, [R7]
    ADDI R7, R7, #-2
    LD R1, [R6 -2]
    ADDI R7, R7, #2
    LD R2, [R7]
    XOR R1, R2, R1
    ST R1, [R6 -3]
    LD R1, [R6 -3]
    ST R1, [R7]
    ADDI R7, R7, #-2
    XOR R1, R0, R0
    ADDI R7, R7, #2
    LD R2, [R7]
    XOR R1, R2, R1
    BR Z, R1, .el26
    XOR R1, R0, R0
    ST R1, [R6 -4]
.el26:
.ei27:
    LDI R1, #0x00FF
    ST R1, [R6 -1]
    XOR R1, R0, R0
    ST R1, [R6 -2]
    LD R1, [R6 -1]
    ST R1, [R7]
    ADDI R7, R7, #-2
    LD R1, [R6 -2]
    ADDI R7, R7, #2
    LD R2, [R7]
    XOR R1, R2, R1
    ST R1, [R6 -3]
    LD R1, [R6 -3]
    ST R1, [R7]
    ADDI R7, R7, #-2
    LDI R1, #0x00FF
    ADDI R7, R7, #2
    LD R2, [R7]
    XOR R1, R2, R1
    BR Z, R1, .el28
    XOR R1, R0, R0
    ST R1, [R6 -4]
.el28:
.ei29:
    ADDI R1, R0, #1
    ST R1, [R6 -1]
    ADDI R1, R0, #3
    ST R1, [R6 -2]
    LD R1, [R6 -1]
    ST R1, [R7]
    ADDI R7, R7, #-2
    LD R1, [R6 -2]
    ADDI R7, R7, #2
    LD R2, [R7]
    SLL R1, R2, R1
    ST R1, [R6 -3]
    LD R1, [R6 -3]
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R1, R0, #8
    ADDI R7, R7, #2
    LD R2, [R7]
    XOR R1, R2, R1
    BR Z, R1, .el30
    XOR R1, R0, R0
    ST R1, [R6 -4]
.el30:
.ei31:
    ADDI R1, R0, #16
    ST R1, [R6 -1]
    ADDI R1, R0, #2
    ST R1, [R6 -2]
    LD R1, [R6 -1]
    ST R1, [R7]
    ADDI R7, R7, #-2
    LD R1, [R6 -2]
    ADDI R7, R7, #2
    LD R2, [R7]
    SRA R1, R2, R1
    ST R1, [R6 -3]
    LD R1, [R6 -3]
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R1, R0, #4
    ADDI R7, R7, #2
    LD R2, [R7]
    XOR R1, R2, R1
    BR Z, R1, .el32
    XOR R1, R0, R0
    ST R1, [R6 -4]
.el32:
.ei33:
    LDI R1, #0x00FF
    ST R1, [R6 -1]
    LD R1, [R6 -1]
    ST R1, [R7]
    ADDI R7, R7, #-2
    LDI R1, #0xFF00
    ADDI R7, R7, #2
    LD R2, [R7]
    XOR R1, R2, R1
    ST R1, [R6 -3]
    LD R1, [R6 -3]
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R1, R0, #-1
    ADDI R7, R7, #2
    LD R2, [R7]
    XOR R1, R2, R1
    BR Z, R1, .el34
    XOR R1, R0, R0
    ST R1, [R6 -4]
.el34:
.ei35:
    LD R1, [R6 -5]
    ST R1, [R7]
    ADDI R7, R7, #-2
    LD R1, [R6 -4]
    ADDI R7, R7, #2
    LD R2, [R7]
    ST R1, [R2 + 0]
    LD R1, [R6 -4]
test_alu_epi:
    ADDI R7, R7, #10
    ADDI R7, R7, #2
    LD R6, [R7]
    ADDI R7, R7, #2
    LD R5, [R7]
    JMP R5

;--- test_branches(...)
test_branches:
    ST R5, [R7]
    ADDI R7, R7, #-2
    ST R6, [R7]
    ADDI R7, R7, #-2
    ADD R6, R7, R0
    ADDI R6, R6, #2
    ADDI R7, R7, #-6
    ST R2, [R6 -3]

    LDI R1, #0x002A
    ST R1, [R6 -1]
    LDI R1, #0x002A
    ST R1, [R6 -2]
    LD R1, [R6 -1]
    ST R1, [R7]
    ADDI R7, R7, #-2
    LD R1, [R6 -2]
    ADDI R7, R7, #2
    LD R2, [R7]
    XOR R1, R2, R1
    BR Z, R1, .el36
    LD R1, [R6 -3]
    ST R1, [R7]
    ADDI R7, R7, #-2
    XOR R1, R0, R0
    ADDI R7, R7, #2
    LD R2, [R7]
    ST R1, [R2 + 0]
    XOR R1, R0, R0
    JMPR test_branches_epi
.el36:
.ei37:
    ADDI R1, R0, #10
    ST R1, [R6 -1]
    ADDI R1, R0, #20
    ST R1, [R6 -2]
    LD R1, [R6 -1]
    ST R1, [R7]
    ADDI R7, R7, #-2
    LD R1, [R6 -2]
    ADDI R7, R7, #2
    LD R2, [R7]
    XOR R1, R2, R1
    BR NZ, R1, .el38
    LD R1, [R6 -3]
    ST R1, [R7]
    ADDI R7, R7, #-2
    XOR R1, R0, R0
    ADDI R7, R7, #2
    LD R2, [R7]
    ST R1, [R2 + 0]
    XOR R1, R0, R0
    JMPR test_branches_epi
.el38:
.ei39:
    ADDI R1, R0, #5
    ST R1, [R6 -1]
    ADDI R1, R0, #10
    ST R1, [R6 -2]
    LD R1, [R6 -1]
    ST R1, [R7]
    ADDI R7, R7, #-2
    LD R1, [R6 -2]
    ADDI R7, R7, #2
    LD R2, [R7]
    SLT R3, R2, R1
    BR NZ, R3, .el40
    LD R1, [R6 -3]
    ST R1, [R7]
    ADDI R7, R7, #-2
    XOR R1, R0, R0
    ADDI R7, R7, #2
    LD R2, [R7]
    ST R1, [R2 + 0]
    XOR R1, R0, R0
    JMPR test_branches_epi
.el40:
.ei41:
    LD R1, [R6 -2]
    ST R1, [R7]
    ADDI R7, R7, #-2
    LD R1, [R6 -1]
    ADDI R7, R7, #2
    LD R2, [R7]
    SLT R3, R2, R1
    BR Z, R3, .el42
    LD R1, [R6 -3]
    ST R1, [R7]
    ADDI R7, R7, #-2
    XOR R1, R0, R0
    ADDI R7, R7, #2
    LD R2, [R7]
    ST R1, [R2 + 0]
    XOR R1, R0, R0
    JMPR test_branches_epi
.el42:
.ei43:
    ADDI R1, R0, #10
    ST R1, [R6 -1]
    ADDI R1, R0, #5
    ST R1, [R6 -2]
    LD R1, [R6 -1]
    ST R1, [R7]
    ADDI R7, R7, #-2
    LD R1, [R6 -2]
    ADDI R7, R7, #2
    LD R2, [R7]
    SLT R3, R1, R2
    BR NZ, R3, .el44
    LD R1, [R6 -3]
    ST R1, [R7]
    ADDI R7, R7, #-2
    XOR R1, R0, R0
    ADDI R7, R7, #2
    LD R2, [R7]
    ST R1, [R2 + 0]
    XOR R1, R0, R0
    JMPR test_branches_epi
.el44:
.ei45:
    LD R1, [R6 -3]
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R1, R0, #1
    ADDI R7, R7, #2
    LD R2, [R7]
    ST R1, [R2 + 0]
    ADDI R1, R0, #1
test_branches_epi:
    ADDI R7, R7, #6
    ADDI R7, R7, #2
    LD R6, [R7]
    ADDI R7, R7, #2
    LD R5, [R7]
    JMP R5

;--- test_memory(...)
test_memory:
    ST R5, [R7]
    ADDI R7, R7, #-2
    ST R6, [R7]
    ADDI R7, R7, #-2
    ADD R6, R7, R0
    ADDI R6, R6, #2
    ADDI R7, R7, #-20
    ST R2, [R6 -10]

    ADDI R1, R6, #-16
    ST R1, [R7]
    ADDI R7, R7, #-2
    XOR R1, R0, R0
    ADDI R3, R0, #1
    SLL R1, R1, R3
    ADDI R7, R7, #2
    LD R2, [R7]
    ADD R1, R2, R1
    ST R1, [R7]
    ADDI R7, R7, #-2
    LDI R1, #0x0064
    ADDI R7, R7, #2
    LD R2, [R7]
    ST R1, [R2 + 0]
    ADDI R1, R6, #-16
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R1, R0, #1
    ADDI R3, R0, #1
    SLL R1, R1, R3
    ADDI R7, R7, #2
    LD R2, [R7]
    ADD R1, R2, R1
    ST R1, [R7]
    ADDI R7, R7, #-2
    LDI R1, #0x00C8
    ADDI R7, R7, #2
    LD R2, [R7]
    ST R1, [R2 + 0]
    ADDI R1, R6, #-16
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R1, R0, #2
    ADDI R3, R0, #1
    SLL R1, R1, R3
    ADDI R7, R7, #2
    LD R2, [R7]
    ADD R1, R2, R1
    ST R1, [R7]
    ADDI R7, R7, #-2
    LDI R1, #0x012C
    ADDI R7, R7, #2
    LD R2, [R7]
    ST R1, [R2 + 0]
    ADDI R1, R6, #-16
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R1, R0, #3
    ADDI R3, R0, #1
    SLL R1, R1, R3
    ADDI R7, R7, #2
    LD R2, [R7]
    ADD R1, R2, R1
    ST R1, [R7]
    ADDI R7, R7, #-2
    LDI R1, #0x0190
    ADDI R7, R7, #2
    LD R2, [R7]
    ST R1, [R2 + 0]
    ADDI R1, R6, #-16
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R1, R0, #4
    ADDI R3, R0, #1
    SLL R1, R1, R3
    ADDI R7, R7, #2
    LD R2, [R7]
    ADD R1, R2, R1
    ST R1, [R7]
    ADDI R7, R7, #-2
    LDI R1, #0x01F4
    ADDI R7, R7, #2
    LD R2, [R7]
    ST R1, [R2 + 0]
    ADDI R1, R6, #-16
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R1, R0, #5
    ADDI R3, R0, #1
    SLL R1, R1, R3
    ADDI R7, R7, #2
    LD R2, [R7]
    ADD R1, R2, R1
    ST R1, [R7]
    ADDI R7, R7, #-2
    LDI R1, #0x0258
    ADDI R7, R7, #2
    LD R2, [R7]
    ST R1, [R2 + 0]
    ADDI R1, R6, #-16
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R1, R0, #6
    ADDI R3, R0, #1
    SLL R1, R1, R3
    ADDI R7, R7, #2
    LD R2, [R7]
    ADD R1, R2, R1
    ST R1, [R7]
    ADDI R7, R7, #-2
    LDI R1, #0x02BC
    ADDI R7, R7, #2
    LD R2, [R7]
    ST R1, [R2 + 0]
    ADDI R1, R6, #-16
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R1, R0, #7
    ADDI R3, R0, #1
    SLL R1, R1, R3
    ADDI R7, R7, #2
    LD R2, [R7]
    ADD R1, R2, R1
    ST R1, [R7]
    ADDI R7, R7, #-2
    LDI R1, #0x0320
    ADDI R7, R7, #2
    LD R2, [R7]
    ST R1, [R2 + 0]
    XOR R1, R0, R0
    ST R1, [R6 -9]
    ADDI R1, R6, #-16
    ST R1, [R7]
    ADDI R7, R7, #-2
    XOR R1, R0, R0
    ADDI R3, R0, #1
    SLL R1, R1, R3
    ADDI R7, R7, #2
    LD R2, [R7]
    ADD R1, R2, R1
    LD R1, [R1 + 0]
    ST R1, [R7]
    ADDI R7, R7, #-2
    LDI R1, #0x0064
    ADDI R7, R7, #2
    LD R2, [R7]
    XOR R1, R2, R1
    BR Z, R1, .el46
    LD R1, [R6 -10]
    ST R1, [R7]
    ADDI R7, R7, #-2
    XOR R1, R0, R0
    ADDI R7, R7, #2
    LD R2, [R7]
    ST R1, [R2 + 0]
    XOR R1, R0, R0
    JMPR test_memory_epi
.el46:
.ei47:
    ADDI R1, R6, #-16
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R1, R0, #3
    ADDI R3, R0, #1
    SLL R1, R1, R3
    ADDI R7, R7, #2
    LD R2, [R7]
    ADD R1, R2, R1
    LD R1, [R1 + 0]
    ST R1, [R7]
    ADDI R7, R7, #-2
    LDI R1, #0x0190
    ADDI R7, R7, #2
    LD R2, [R7]
    XOR R1, R2, R1
    BR Z, R1, .el48
    LD R1, [R6 -10]
    ST R1, [R7]
    ADDI R7, R7, #-2
    XOR R1, R0, R0
    ADDI R7, R7, #2
    LD R2, [R7]
    ST R1, [R2 + 0]
    XOR R1, R0, R0
    JMPR test_memory_epi
.el48:
.ei49:
    ADDI R1, R6, #-16
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R1, R0, #7
    ADDI R3, R0, #1
    SLL R1, R1, R3
    ADDI R7, R7, #2
    LD R2, [R7]
    ADD R1, R2, R1
    LD R1, [R1 + 0]
    ST R1, [R7]
    ADDI R7, R7, #-2
    LDI R1, #0x0320
    ADDI R7, R7, #2
    LD R2, [R7]
    XOR R1, R2, R1
    BR Z, R1, .el50
    LD R1, [R6 -10]
    ST R1, [R7]
    ADDI R7, R7, #-2
    XOR R1, R0, R0
    ADDI R7, R7, #2
    LD R2, [R7]
    ST R1, [R2 + 0]
    XOR R1, R0, R0
    JMPR test_memory_epi
.el50:
.ei51:
    ADDI R1, R6, #-16
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R1, R0, #2
    ADDI R3, R0, #1
    SLL R1, R1, R3
    ADDI R7, R7, #2
    LD R2, [R7]
    ADD R1, R2, R1
    ST R1, [R7]
    ADDI R7, R7, #-2
    LDI R1, #0x03E7
    ADDI R7, R7, #2
    LD R2, [R7]
    ST R1, [R2 + 0]
    ADDI R1, R6, #-16
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R1, R0, #2
    ADDI R3, R0, #1
    SLL R1, R1, R3
    ADDI R7, R7, #2
    LD R2, [R7]
    ADD R1, R2, R1
    LD R1, [R1 + 0]
    ST R1, [R7]
    ADDI R7, R7, #-2
    LDI R1, #0x03E7
    ADDI R7, R7, #2
    LD R2, [R7]
    XOR R1, R2, R1
    BR Z, R1, .el52
    LD R1, [R6 -10]
    ST R1, [R7]
    ADDI R7, R7, #-2
    XOR R1, R0, R0
    ADDI R7, R7, #2
    LD R2, [R7]
    ST R1, [R2 + 0]
    XOR R1, R0, R0
    JMPR test_memory_epi
.el52:
.ei53:
    ADDI R1, R0, #5
    ST R1, [R6 -9]
    ADDI R1, R6, #-16
    ST R1, [R7]
    ADDI R7, R7, #-2
    LD R1, [R6 -9]
    ADDI R3, R0, #1
    SLL R1, R1, R3
    ADDI R7, R7, #2
    LD R2, [R7]
    ADD R1, R2, R1
    LD R1, [R1 + 0]
    ST R1, [R7]
    ADDI R7, R7, #-2
    LDI R1, #0x0258
    ADDI R7, R7, #2
    LD R2, [R7]
    XOR R1, R2, R1
    BR Z, R1, .el54
    LD R1, [R6 -10]
    ST R1, [R7]
    ADDI R7, R7, #-2
    XOR R1, R0, R0
    ADDI R7, R7, #2
    LD R2, [R7]
    ST R1, [R2 + 0]
    XOR R1, R0, R0
    JMPR test_memory_epi
.el54:
.ei55:
    LD R1, [R6 -10]
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R1, R0, #1
    ADDI R7, R7, #2
    LD R2, [R7]
    ST R1, [R2 + 0]
    ADDI R1, R0, #1
test_memory_epi:
    ADDI R7, R7, #20
    ADDI R7, R7, #2
    LD R6, [R7]
    ADDI R7, R7, #2
    LD R5, [R7]
    JMP R5

;--- deep(...)
deep:
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
    XOR R1, R0, R0
    ADDI R7, R7, #2
    LD R2, [R7]
    SLT R3, R1, R2
    BR NZ, R3, .el56
    LDI R1, #0x002A
    JMPR deep_epi
.el56:
.ei57:
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
    CALLR R5, deep
    ADDI R1, R1, #1
deep_epi:
    ADDI R7, R7, #2
    ADDI R7, R7, #2
    LD R6, [R7]
    ADDI R7, R7, #2
    LD R5, [R7]
    JMP R5

;--- test_stack(...)
test_stack:
    ST R5, [R7]
    ADDI R7, R7, #-2
    ST R6, [R7]
    ADDI R7, R7, #-2
    ADD R6, R7, R0
    ADDI R6, R6, #2
    ADDI R7, R7, #-4
    ST R2, [R6 -2]

    ADDI R1, R0, #5
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    CALLR R5, deep
    ST R1, [R6 -1]
    LD R1, [R6 -1]
    ST R1, [R7]
    ADDI R7, R7, #-2
    LDI R1, #0x002F
    ADDI R7, R7, #2
    LD R2, [R7]
    XOR R1, R2, R1
    BR Z, R1, .el58
    LD R1, [R6 -2]
    ST R1, [R7]
    ADDI R7, R7, #-2
    XOR R1, R0, R0
    ADDI R7, R7, #2
    LD R2, [R7]
    ST R1, [R2 + 0]
    XOR R1, R0, R0
    JMPR test_stack_epi
.el58:
.ei59:
    LD R1, [R6 -2]
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R1, R0, #1
    ADDI R7, R7, #2
    LD R2, [R7]
    ST R1, [R2 + 0]
    ADDI R1, R0, #1
test_stack_epi:
    ADDI R7, R7, #4
    ADDI R7, R7, #2
    LD R6, [R7]
    ADDI R7, R7, #2
    LD R5, [R7]
    JMP R5

;--- test_runlib(...)
test_runlib:
    ST R5, [R7]
    ADDI R7, R7, #-2
    ST R6, [R7]
    ADDI R7, R7, #-2
    ADD R6, R7, R0
    ADDI R6, R6, #2
    ADDI R7, R7, #-8
    ST R2, [R6 -4]

    ADDI R1, R0, #7
    ST R1, [R6 -1]
    ADDI R1, R0, #8
    ST R1, [R6 -2]
    LD R1, [R6 -1]
    ST R1, [R7]
    ADDI R7, R7, #-2
    LD R1, [R6 -2]
    ADDI R7, R7, #2
    LD R2, [R7]
    ST R5, [R7]
    ADDI R7, R7, #-2
    ST R1, [R7]
    ADDI R7, R7, #-2
    CALLR R5, __mul16
.mul16_ret60:
    ADDI R7, R7, #2
    ADDI R7, R7, #2
    LD R5, [R7]
    ST R1, [R6 -3]
    LD R1, [R6 -3]
    ST R1, [R7]
    ADDI R7, R7, #-2
    LDI R1, #0x0038
    ADDI R7, R7, #2
    LD R2, [R7]
    XOR R1, R2, R1
    BR Z, R1, .el61
    LD R1, [R6 -4]
    ST R1, [R7]
    ADDI R7, R7, #-2
    XOR R1, R0, R0
    ADDI R7, R7, #2
    LD R2, [R7]
    ST R1, [R2 + 0]
    XOR R1, R0, R0
    JMPR test_runlib_epi
.el61:
.ei62:
    LDI R1, #0x007B
    ST R1, [R6 -1]
    LDI R1, #0x002D
    ST R1, [R6 -2]
    LD R1, [R6 -1]
    ST R1, [R7]
    ADDI R7, R7, #-2
    LD R1, [R6 -2]
    ADDI R7, R7, #2
    LD R2, [R7]
    ST R5, [R7]
    ADDI R7, R7, #-2
    ST R1, [R7]
    ADDI R7, R7, #-2
    CALLR R5, __mul16
.mul16_ret63:
    ADDI R7, R7, #2
    ADDI R7, R7, #2
    LD R5, [R7]
    ST R1, [R6 -3]
    LD R1, [R6 -3]
    ST R1, [R7]
    ADDI R7, R7, #-2
    LDI R1, #0x159F
    ADDI R7, R7, #2
    LD R2, [R7]
    XOR R1, R2, R1
    BR Z, R1, .el64
    LD R1, [R6 -4]
    ST R1, [R7]
    ADDI R7, R7, #-2
    XOR R1, R0, R0
    ADDI R7, R7, #2
    LD R2, [R7]
    ST R1, [R2 + 0]
    XOR R1, R0, R0
    JMPR test_runlib_epi
.el64:
.ei65:
    LDI R1, #0x0064
    ST R1, [R6 -1]
    ADDI R1, R0, #7
    ST R1, [R6 -2]
    LD R1, [R6 -1]
    ST R1, [R7]
    ADDI R7, R7, #-2
    LD R1, [R6 -2]
    ADDI R7, R7, #2
    LD R2, [R7]
    ST R5, [R7]
    ADDI R7, R7, #-2
    ST R1, [R7]
    ADDI R7, R7, #-2
    CALLR R5, __div16
.div16_ret66:
    ADDI R7, R7, #2
    ADDI R7, R7, #2
    LD R5, [R7]
    ST R1, [R6 -3]
    LD R1, [R6 -3]
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R1, R0, #14
    ADDI R7, R7, #2
    LD R2, [R7]
    XOR R1, R2, R1
    BR Z, R1, .el67
    LD R1, [R6 -4]
    ST R1, [R7]
    ADDI R7, R7, #-2
    XOR R1, R0, R0
    ADDI R7, R7, #2
    LD R2, [R7]
    ST R1, [R2 + 0]
    XOR R1, R0, R0
    JMPR test_runlib_epi
.el67:
.ei68:
    LDI R1, #0x0064
    ST R1, [R6 -1]
    ADDI R1, R0, #7
    ST R1, [R6 -2]
    LD R1, [R6 -1]
    ST R1, [R7]
    ADDI R7, R7, #-2
    LD R1, [R6 -2]
    ADDI R7, R7, #2
    LD R2, [R7]
    ST R5, [R7]
    ADDI R7, R7, #-2
    ST R1, [R7]
    ADDI R7, R7, #-2
    CALLR R5, __mod16
.mod16_ret69:
    ADDI R7, R7, #2
    ADDI R7, R7, #2
    LD R5, [R7]
    ST R1, [R6 -3]
    LD R1, [R6 -3]
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R1, R0, #2
    ADDI R7, R7, #2
    LD R2, [R7]
    XOR R1, R2, R1
    BR Z, R1, .el70
    LD R1, [R6 -4]
    ST R1, [R7]
    ADDI R7, R7, #-2
    XOR R1, R0, R0
    ADDI R7, R7, #2
    LD R2, [R7]
    ST R1, [R2 + 0]
    XOR R1, R0, R0
    JMPR test_runlib_epi
.el70:
.ei71:
    LD R1, [R6 -4]
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R1, R0, #1
    ADDI R7, R7, #2
    LD R2, [R7]
    ST R1, [R2 + 0]
    ADDI R1, R0, #1
test_runlib_epi:
    ADDI R7, R7, #8
    ADDI R7, R7, #2
    LD R6, [R7]
    ADDI R7, R7, #2
    LD R5, [R7]
    JMP R5

;--- test_arrays(...)
test_arrays:
    ST R5, [R7]
    ADDI R7, R7, #-2
    ST R6, [R7]
    ADDI R7, R7, #-2
    ADD R6, R7, R0
    ADDI R6, R6, #2
    ADDI R7, R7, #-18
    ST R2, [R6 -9]

    ADDI R1, R6, #-12
    ST R1, [R7]
    ADDI R7, R7, #-2
    XOR R1, R0, R0
    ADDI R3, R0, #1
    SLL R1, R1, R3
    ADDI R7, R7, #2
    LD R2, [R7]
    ADD R1, R2, R1
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R1, R0, #10
    ADDI R7, R7, #2
    LD R2, [R7]
    ST R1, [R2 + 0]
    ADDI R1, R6, #-12
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R1, R0, #1
    ADDI R3, R0, #1
    SLL R1, R1, R3
    ADDI R7, R7, #2
    LD R2, [R7]
    ADD R1, R2, R1
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R1, R0, #20
    ADDI R7, R7, #2
    LD R2, [R7]
    ST R1, [R2 + 0]
    ADDI R1, R6, #-12
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R1, R0, #2
    ADDI R3, R0, #1
    SLL R1, R1, R3
    ADDI R7, R7, #2
    LD R2, [R7]
    ADD R1, R2, R1
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R1, R0, #30
    ADDI R7, R7, #2
    LD R2, [R7]
    ST R1, [R2 + 0]
    ADDI R1, R6, #-12
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R1, R0, #3
    ADDI R3, R0, #1
    SLL R1, R1, R3
    ADDI R7, R7, #2
    LD R2, [R7]
    ADD R1, R2, R1
    ST R1, [R7]
    ADDI R7, R7, #-2
    LDI R1, #0x0028
    ADDI R7, R7, #2
    LD R2, [R7]
    ST R1, [R2 + 0]
    ADDI R1, R6, #-12
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R1, R0, #4
    ADDI R3, R0, #1
    SLL R1, R1, R3
    ADDI R7, R7, #2
    LD R2, [R7]
    ADD R1, R2, R1
    ST R1, [R7]
    ADDI R7, R7, #-2
    LDI R1, #0x0032
    ADDI R7, R7, #2
    LD R2, [R7]
    ST R1, [R2 + 0]
    ADDI R1, R6, #-12
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R1, R0, #5
    ADDI R3, R0, #1
    SLL R1, R1, R3
    ADDI R7, R7, #2
    LD R2, [R7]
    ADD R1, R2, R1
    ST R1, [R7]
    ADDI R7, R7, #-2
    LDI R1, #0x003C
    ADDI R7, R7, #2
    LD R2, [R7]
    ST R1, [R2 + 0]
    XOR R1, R0, R0
    ST R1, [R6 -8]
    XOR R1, R0, R0
    ST R1, [R6 -7]
.w72:
    LD R1, [R6 -7]
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R1, R0, #6
    ADDI R7, R7, #2
    LD R2, [R7]
    SLT R3, R2, R1
    BR Z, R3, .we73
    LD R1, [R6 -8]
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R1, R6, #-12
    ST R1, [R7]
    ADDI R7, R7, #-2
    LD R1, [R6 -7]
    ADDI R3, R0, #1
    SLL R1, R1, R3
    ADDI R7, R7, #2
    LD R2, [R7]
    ADD R1, R2, R1
    LD R1, [R1 + 0]
    ADDI R7, R7, #2
    LD R2, [R7]
    ADD R1, R2, R1
    ST R1, [R6 -8]
    LD R1, [R6 -7]
    ADDI R1, R1, #1
    ST R1, [R6 -7]
    JMPR .w72
.we73:
    LD R1, [R6 -8]
    ST R1, [R7]
    ADDI R7, R7, #-2
    LDI R1, #0x00D2
    ADDI R7, R7, #2
    LD R2, [R7]
    XOR R1, R2, R1
    BR Z, R1, .el74
    LD R1, [R6 -9]
    ST R1, [R7]
    ADDI R7, R7, #-2
    XOR R1, R0, R0
    ADDI R7, R7, #2
    LD R2, [R7]
    ST R1, [R2 + 0]
    XOR R1, R0, R0
    JMPR test_arrays_epi
.el74:
.ei75:
    ADDI R1, R0, #1
    ST R1, [R6 -7]
    ADDI R1, R6, #-12
    ST R1, [R7]
    ADDI R7, R7, #-2
    LD R1, [R6 -7]
    ADDI R3, R0, #1
    SLL R1, R1, R3
    ADDI R7, R7, #2
    LD R2, [R7]
    ADD R1, R2, R1
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R1, R0, #25
    ADDI R7, R7, #2
    LD R2, [R7]
    ST R1, [R2 + 0]
    ADDI R1, R6, #-12
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R1, R0, #1
    ADDI R3, R0, #1
    SLL R1, R1, R3
    ADDI R7, R7, #2
    LD R2, [R7]
    ADD R1, R2, R1
    LD R1, [R1 + 0]
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R1, R0, #25
    ADDI R7, R7, #2
    LD R2, [R7]
    XOR R1, R2, R1
    BR Z, R1, .el76
    LD R1, [R6 -9]
    ST R1, [R7]
    ADDI R7, R7, #-2
    XOR R1, R0, R0
    ADDI R7, R7, #2
    LD R2, [R7]
    ST R1, [R2 + 0]
    XOR R1, R0, R0
    JMPR test_arrays_epi
.el76:
.ei77:
    LD R1, [R6 -9]
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R1, R0, #1
    ADDI R7, R7, #2
    LD R2, [R7]
    ST R1, [R2 + 0]
    ADDI R1, R0, #1
test_arrays_epi:
    ADDI R7, R7, #18
    ADDI R7, R7, #2
    LD R6, [R7]
    ADDI R7, R7, #2
    LD R5, [R7]
    JMP R5

;--- test_pointers(...)
test_pointers:
    ST R5, [R7]
    ADDI R7, R7, #-2
    ST R6, [R7]
    ADDI R7, R7, #-2
    ADD R6, R7, R0
    ADDI R6, R6, #2
    ADDI R7, R7, #-6
    ST R2, [R6 -3]

    LDI R1, #0x004D
    ST R1, [R6 -1]
    ADDI R1, R6, #-2
    ST R1, [R6 -2]
    LD R1, [R6 -2]
    LD R1, [R1 + 0]
    ST R1, [R7]
    ADDI R7, R7, #-2
    LDI R1, #0x004D
    ADDI R7, R7, #2
    LD R2, [R7]
    XOR R1, R2, R1
    BR Z, R1, .el78
    LD R1, [R6 -3]
    ST R1, [R7]
    ADDI R7, R7, #-2
    XOR R1, R0, R0
    ADDI R7, R7, #2
    LD R2, [R7]
    ST R1, [R2 + 0]
    XOR R1, R0, R0
    JMPR test_pointers_epi
.el78:
.ei79:
    LD R1, [R6 -2]
    ST R1, [R7]
    ADDI R7, R7, #-2
    LDI R1, #0x0058
    ADDI R7, R7, #2
    LD R2, [R7]
    ST R1, [R2 + 0]
    LD R1, [R6 -1]
    ST R1, [R7]
    ADDI R7, R7, #-2
    LDI R1, #0x0058
    ADDI R7, R7, #2
    LD R2, [R7]
    XOR R1, R2, R1
    BR Z, R1, .el80
    LD R1, [R6 -3]
    ST R1, [R7]
    ADDI R7, R7, #-2
    XOR R1, R0, R0
    ADDI R7, R7, #2
    LD R2, [R7]
    ST R1, [R2 + 0]
    XOR R1, R0, R0
    JMPR test_pointers_epi
.el80:
.ei81:
    LD R1, [R6 -3]
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R1, R0, #1
    ADDI R7, R7, #2
    LD R2, [R7]
    ST R1, [R2 + 0]
    ADDI R1, R0, #1
test_pointers_epi:
    ADDI R7, R7, #6
    ADDI R7, R7, #2
    LD R6, [R7]
    ADDI R7, R7, #2
    LD R5, [R7]
    JMP R5

;--- test_ldi(...)
test_ldi:
    ST R5, [R7]
    ADDI R7, R7, #-2
    ST R6, [R7]
    ADDI R7, R7, #-2
    ADD R6, R7, R0
    ADDI R6, R6, #2
    ADDI R7, R7, #-4
    ST R2, [R6 -2]

    ADDI R1, R0, #-1
    ST R1, [R6 -1]
    LD R1, [R6 -1]
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R1, R0, #-1
    ADDI R7, R7, #2
    LD R2, [R7]
    XOR R1, R2, R1
    BR Z, R1, .el82
    LD R1, [R6 -2]
    ST R1, [R7]
    ADDI R7, R7, #-2
    XOR R1, R0, R0
    ADDI R7, R7, #2
    LD R2, [R7]
    ST R1, [R2 + 0]
    XOR R1, R0, R0
    JMPR test_ldi_epi
.el82:
.ei83:
    LDI R1, #0x7FFF
    ST R1, [R6 -1]
    LD R1, [R6 -1]
    ST R1, [R7]
    ADDI R7, R7, #-2
    LDI R1, #0x7FFF
    ADDI R7, R7, #2
    LD R2, [R7]
    XOR R1, R2, R1
    BR Z, R1, .el84
    LD R1, [R6 -2]
    ST R1, [R7]
    ADDI R7, R7, #-2
    XOR R1, R0, R0
    ADDI R7, R7, #2
    LD R2, [R7]
    ST R1, [R2 + 0]
    XOR R1, R0, R0
    JMPR test_ldi_epi
.el84:
.ei85:
    ADDI R1, R0, #1
    ST R1, [R6 -1]
    LD R1, [R6 -1]
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R1, R0, #1
    ADDI R7, R7, #2
    LD R2, [R7]
    XOR R1, R2, R1
    BR Z, R1, .el86
    LD R1, [R6 -2]
    ST R1, [R7]
    ADDI R7, R7, #-2
    XOR R1, R0, R0
    ADDI R7, R7, #2
    LD R2, [R7]
    ST R1, [R2 + 0]
    XOR R1, R0, R0
    JMPR test_ldi_epi
.el86:
.ei87:
    LD R1, [R6 -2]
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R1, R0, #1
    ADDI R7, R7, #2
    LD R2, [R7]
    ST R1, [R2 + 0]
    ADDI R1, R0, #1
test_ldi_epi:
    ADDI R7, R7, #4
    ADDI R7, R7, #2
    LD R6, [R7]
    ADDI R7, R7, #2
    LD R5, [R7]
    JMP R5

;--- test_uart_tx(...)
test_uart_tx:
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
    ADDI R1, R0, #1
    ADDI R7, R7, #2
    LD R2, [R7]
    ST R1, [R2 + 0]
    ADDI R1, R0, #1
test_uart_tx_epi:
    ADDI R7, R7, #2
    ADDI R7, R7, #2
    LD R6, [R7]
    ADDI R7, R7, #2
    LD R5, [R7]
    JMP R5

;--- test_uart_rx(...)
test_uart_rx:
    ST R5, [R7]
    ADDI R7, R7, #-2
    ST R6, [R7]
    ADDI R7, R7, #-2
    ADD R6, R7, R0
    ADDI R6, R6, #2
    ADDI R7, R7, #-8
    ST R2, [R6 -4]

    LDI R1, #.Lstr2
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    CALLR R5, print
    CALLR R5, getchar
    ST R1, [R6 -1]
    LD R1, [R6 -1]
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    CALLR R5, putchar
    CALLR R5, getchar
    ST R1, [R6 -2]
    LD R1, [R6 -2]
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    CALLR R5, putchar
    ADDI R1, R0, #10
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    CALLR R5, putchar
    ADDI R1, R0, #1
    ST R1, [R6 -3]
    LD R1, [R6 -1]
    ST R1, [R7]
    ADDI R7, R7, #-2
    LDI R1, #0x0020
    ADDI R7, R7, #2
    LD R2, [R7]
    SLT R3, R2, R1
    BR Z, R3, .el88
    XOR R1, R0, R0
    ST R1, [R6 -3]
.el88:
.ei89:
    LD R1, [R6 -2]
    ST R1, [R7]
    ADDI R7, R7, #-2
    LDI R1, #0x0020
    ADDI R7, R7, #2
    LD R2, [R7]
    SLT R3, R2, R1
    BR Z, R3, .el90
    XOR R1, R0, R0
    ST R1, [R6 -3]
.el90:
.ei91:
    LD R1, [R6 -4]
    ST R1, [R7]
    ADDI R7, R7, #-2
    LD R1, [R6 -3]
    ADDI R7, R7, #2
    LD R2, [R7]
    ST R1, [R2 + 0]
    LD R1, [R6 -3]
test_uart_rx_epi:
    ADDI R7, R7, #8
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
    ADDI R7, R7, #-6

    XOR R1, R0, R0
    ST R1, [R6 -1]
    XOR R1, R0, R0
    ST R1, [R6 -2]
    CALLR R5, nl
    LDI R1, #.Lstr3
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    CALLR R5, print
    LDI R1, #.Lstr4
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    CALLR R5, print
    LDI R1, #.Lstr5
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    CALLR R5, print
    CALLR R5, nl
    LDI R1, #.Lstr6
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    CALLR R5, print
    ADDI R1, R6, #-6
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    CALLR R5, test_alu
    BR Z, R1, .el92
    LDI R1, #.Lstr7
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    CALLR R5, pass
    LD R1, [R6 -1]
    ADDI R1, R1, #1
    ST R1, [R6 -1]
    JMPR .ei93
.el92:
    LDI R1, #.Lstr8
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    CALLR R5, fail
    LD R1, [R6 -2]
    ADDI R1, R1, #1
    ST R1, [R6 -2]
.ei93:
    LDI R1, #.Lstr9
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    CALLR R5, print
    ADDI R1, R6, #-6
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    CALLR R5, test_branches
    BR Z, R1, .el94
    LDI R1, #.Lstr10
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    CALLR R5, pass
    LD R1, [R6 -1]
    ADDI R1, R1, #1
    ST R1, [R6 -1]
    JMPR .ei95
.el94:
    LDI R1, #.Lstr11
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    CALLR R5, fail
    LD R1, [R6 -2]
    ADDI R1, R1, #1
    ST R1, [R6 -2]
.ei95:
    LDI R1, #.Lstr12
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    CALLR R5, print
    ADDI R1, R6, #-6
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    CALLR R5, test_memory
    BR Z, R1, .el96
    LDI R1, #.Lstr13
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    CALLR R5, pass
    LD R1, [R6 -1]
    ADDI R1, R1, #1
    ST R1, [R6 -1]
    JMPR .ei97
.el96:
    LDI R1, #.Lstr14
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    CALLR R5, fail
    LD R1, [R6 -2]
    ADDI R1, R1, #1
    ST R1, [R6 -2]
.ei97:
    LDI R1, #.Lstr15
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    CALLR R5, print
    ADDI R1, R6, #-6
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    CALLR R5, test_stack
    BR Z, R1, .el98
    LDI R1, #.Lstr16
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    CALLR R5, pass
    LD R1, [R6 -1]
    ADDI R1, R1, #1
    ST R1, [R6 -1]
    JMPR .ei99
.el98:
    LDI R1, #.Lstr17
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    CALLR R5, fail
    LD R1, [R6 -2]
    ADDI R1, R1, #1
    ST R1, [R6 -2]
.ei99:
    LDI R1, #.Lstr18
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    CALLR R5, print
    ADDI R1, R6, #-6
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    CALLR R5, test_runlib
    BR Z, R1, .el100
    LDI R1, #.Lstr19
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    CALLR R5, pass
    LD R1, [R6 -1]
    ADDI R1, R1, #1
    ST R1, [R6 -1]
    JMPR .ei101
.el100:
    LDI R1, #.Lstr20
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    CALLR R5, fail
    LD R1, [R6 -2]
    ADDI R1, R1, #1
    ST R1, [R6 -2]
.ei101:
    LDI R1, #.Lstr21
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    CALLR R5, print
    ADDI R1, R6, #-6
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    CALLR R5, test_arrays
    BR Z, R1, .el102
    LDI R1, #.Lstr22
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    CALLR R5, pass
    LD R1, [R6 -1]
    ADDI R1, R1, #1
    ST R1, [R6 -1]
    JMPR .ei103
.el102:
    LDI R1, #.Lstr23
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    CALLR R5, fail
    LD R1, [R6 -2]
    ADDI R1, R1, #1
    ST R1, [R6 -2]
.ei103:
    LDI R1, #.Lstr24
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    CALLR R5, print
    ADDI R1, R6, #-6
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    CALLR R5, test_pointers
    BR Z, R1, .el104
    LDI R1, #.Lstr25
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    CALLR R5, pass
    LD R1, [R6 -1]
    ADDI R1, R1, #1
    ST R1, [R6 -1]
    JMPR .ei105
.el104:
    LDI R1, #.Lstr26
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    CALLR R5, fail
    LD R1, [R6 -2]
    ADDI R1, R1, #1
    ST R1, [R6 -2]
.ei105:
    LDI R1, #.Lstr27
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    CALLR R5, print
    ADDI R1, R6, #-6
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    CALLR R5, test_ldi
    BR Z, R1, .el106
    LDI R1, #.Lstr28
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    CALLR R5, pass
    LD R1, [R6 -1]
    ADDI R1, R1, #1
    ST R1, [R6 -1]
    JMPR .ei107
.el106:
    LDI R1, #.Lstr29
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    CALLR R5, fail
    LD R1, [R6 -2]
    ADDI R1, R1, #1
    ST R1, [R6 -2]
.ei107:
    LDI R1, #.Lstr30
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    CALLR R5, print
    ADDI R1, R6, #-6
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    CALLR R5, test_uart_tx
    BR Z, R1, .el108
    LDI R1, #.Lstr31
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    CALLR R5, pass
    LD R1, [R6 -1]
    ADDI R1, R1, #1
    ST R1, [R6 -1]
    JMPR .ei109
.el108:
    LDI R1, #.Lstr32
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    CALLR R5, fail
    LD R1, [R6 -2]
    ADDI R1, R1, #1
    ST R1, [R6 -2]
.ei109:
    LDI R1, #.Lstr33
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    CALLR R5, print
    ADDI R1, R6, #-6
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    CALLR R5, test_uart_rx
    BR Z, R1, .el110
    LDI R1, #.Lstr34
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    CALLR R5, pass
    LD R1, [R6 -1]
    ADDI R1, R1, #1
    ST R1, [R6 -1]
    JMPR .ei111
.el110:
    LDI R1, #.Lstr35
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    CALLR R5, fail
    LD R1, [R6 -2]
    ADDI R1, R1, #1
    ST R1, [R6 -2]
.ei111:
    CALLR R5, nl
    LDI R1, #.Lstr36
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    CALLR R5, print
    LDI R1, #.Lstr37
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    CALLR R5, print
    LD R1, [R6 -1]
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    CALLR R5, print_dec
    LDI R1, #.Lstr38
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    CALLR R5, print
    LD R1, [R6 -1]
    ST R1, [R7]
    ADDI R7, R7, #-2
    LD R1, [R6 -2]
    ADDI R7, R7, #2
    LD R2, [R7]
    ADD R1, R2, R1
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    CALLR R5, print_dec
    LDI R1, #.Lstr39
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    CALLR R5, print
    LDI R1, #.Lstr40
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    CALLR R5, print
    CALLR R5, nl
.w112:
    JMPR .w112
.we113:
main_epi:
    ADDI R7, R7, #6
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