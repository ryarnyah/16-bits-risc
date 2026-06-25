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
    .word 0x6E5C
    .word 0x0000
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
    .word 0x6E5C
    .word 0x0000
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
    .word 0x6E5C
    .word 0x0000
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
    .word 0x6E5C
    .word 0x0000
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
    .word 0x5C2D
    .word 0x006E
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
    .word 0x5C2D
    .word 0x006E
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
    .word 0x6E5C
    .word 0x0000
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
    .word 0x5C2D
    .word 0x006E
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
    .word 0x6E5C
    .word 0x0000
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
    .word 0x5C2D
    .word 0x006E
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
    .word 0x5C2D
    .word 0x006E
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
    .word 0x5C2D
    .word 0x006E
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
    .word 0x5C2D
    .word 0x006E
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
    .word 0x6E5C
    .word 0x0000
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
    .word 0x5C64
    .word 0x006E
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
    .word 0x6E5C
    .word 0x0000
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
    ST R2, [R6 -4]

    LDI R1, #0x1FFE
    ST R1, [R6 -2]
    LD R1, [R6 -2]
    ST R1, [R7]
    ADDI R7, R7, #-2
    LD R1, [R6 -4]
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
    ST R2, [R6 -4]

    LDI R1, #0x1FFC
    ST R1, [R6 -2]
    LD R1, [R6 -2]
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
    ST R2, [R6 -2]

.w2:
    LD R1, [R6 -2]
    LD R1, [R1 + 0]
    BEQ R1, R0, .we3
    LD R1, [R6 -2]
    LD R1, [R1 + 0]
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    LDI R5, #.cr4
    LDI R1, #putchar
    JMP R1
.cr4:
    LD R1, [R6 -2]
    ADDI R1, R1, #1
    ST R1, [R6 -2]
    JMP .w2
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
    ST R2, [R6 -2]

    LD R1, [R6 -2]
    ST R1, [R7]
    ADDI R7, R7, #-2
    XOR R1, R0, R0
    ADDI R7, R7, #2
    LD R2, [R7]
    BLT R2, R1, .cl7
    JMP .el5
.cl7:
    LDI R1, #0x002D
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    LDI R5, #.cr8
    LDI R1, #putchar
    JMP R1
.cr8:
    XOR R1, R0, R0
    ST R1, [R7]
    ADDI R7, R7, #-2
    LD R1, [R6 -2]
    ADDI R7, R7, #2
    LD R2, [R7]
    SUB R1, R2, R1
    ST R1, [R6 -2]
.el5:
.ei6:
    LD R1, [R6 -2]
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R1, R0, #10
    ADDI R7, R7, #2
    LD R2, [R7]
    BLT R2, R1, .el9
    LD R1, [R6 -2]
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
    LD R1, [R7 +2]
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
.el9:
.ei10:
    LDI R1, #0x0030
    ST R1, [R7]
    ADDI R7, R7, #-2
    LD R1, [R6 -2]
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
    LD R1, [R7 +2]
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

;--- print_hex(...)
print_hex:
    ST R5, [R7]
    ADDI R7, R7, #-2
    ST R6, [R7]
    ADDI R7, R7, #-2
    ADD R6, R7, R0
    ADDI R6, R6, #2
    ADDI R7, R7, #-6
    ST R2, [R6 -6]

    XOR R1, R0, R0
    ST R1, [R6 -4]
.w15:
    LD R1, [R6 -4]
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R1, R0, #4
    ADDI R7, R7, #2
    LD R2, [R7]
    BLT R2, R1, .cl17
    JMP .we16
.cl17:
    LD R1, [R6 -6]
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R1, R0, #12
    ADDI R7, R7, #2
    LD R2, [R7]
    SRL R1, R2, R1
    ST R1, [R6 -2]
    LD R1, [R6 -2]
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R1, R0, #15
    ADDI R7, R7, #2
    LD R2, [R7]
    BLT R1, R2, .cl20
    JMP .el18
.cl20:
    ADDI R1, R0, #15
    ST R1, [R6 -2]
.el18:
.ei19:
    LD R1, [R6 -2]
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R1, R0, #10
    ADDI R7, R7, #2
    LD R2, [R7]
    BLT R2, R1, .cl23
    JMP .el21
.cl23:
    LDI R1, #0x0030
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
    LDI R5, #.cr24
    LDI R1, #putchar
    JMP R1
.cr24:
    JMP .ei22
.el21:
    LDI R1, #0x0041
    ST R1, [R7]
    ADDI R7, R7, #-2
    LD R1, [R6 -2]
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
    LDI R5, #.cr25
    LDI R1, #putchar
    JMP R1
.cr25:
.ei22:
    LD R1, [R6 -6]
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R1, R0, #4
    ADDI R7, R7, #2
    LD R2, [R7]
    SLL R1, R2, R1
    ST R1, [R6 -6]
    LD R1, [R6 -4]
    ADDI R1, R1, #1
    ST R1, [R6 -4]
    JMP .w15
.we16:
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
    ST R2, [R6 -2]

    ADDI R1, R0, #10
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    LDI R5, #.cr26
    LDI R1, #putchar
    JMP R1
.cr26:
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
    ST R2, [R6 -2]

    LDI R1, #.Lstr0
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    LDI R5, #.cr27
    LDI R1, #print
    JMP R1
.cr27:
    LD R1, [R6 -2]
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    LDI R5, #.cr28
    LDI R1, #print
    JMP R1
.cr28:
    ADDI R1, R0, #10
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    LDI R5, #.cr29
    LDI R1, #putchar
    JMP R1
.cr29:
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
    ST R2, [R6 -2]

    LDI R1, #.Lstr1
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    LDI R5, #.cr30
    LDI R1, #print
    JMP R1
.cr30:
    LD R1, [R6 -2]
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    LDI R5, #.cr31
    LDI R1, #print
    JMP R1
.cr31:
    ADDI R1, R0, #10
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    LDI R5, #.cr32
    LDI R1, #putchar
    JMP R1
.cr32:
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
    ST R2, [R6 -10]

    ADDI R1, R0, #1
    ST R1, [R6 -8]
    ADDI R1, R0, #10
    ST R1, [R6 -2]
    ADDI R1, R0, #20
    ST R1, [R6 -4]
    LD R1, [R6 -2]
    ST R1, [R7]
    ADDI R7, R7, #-2
    LD R1, [R6 -4]
    ADDI R7, R7, #2
    LD R2, [R7]
    ADD R1, R2, R1
    ST R1, [R6 -6]
    LD R1, [R6 -6]
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R1, R0, #30
    ADDI R7, R7, #2
    LD R2, [R7]
    XOR R1, R2, R1
    BEQ R1, R0, .el33
    XOR R1, R0, R0
    ST R1, [R6 -8]
.el33:
.ei34:
    LDI R1, #0x0064
    ST R1, [R6 -2]
    LD R1, [R6 -2]
    ST R1, [R7]
    ADDI R7, R7, #-2
    LDI R1, #0x0032
    ADDI R7, R7, #2
    LD R2, [R7]
    ADD R1, R2, R1
    ST R1, [R6 -6]
    LD R1, [R6 -6]
    ST R1, [R7]
    ADDI R7, R7, #-2
    LDI R1, #0x0096
    ADDI R7, R7, #2
    LD R2, [R7]
    XOR R1, R2, R1
    BEQ R1, R0, .el35
    XOR R1, R0, R0
    ST R1, [R6 -8]
.el35:
.ei36:
    LDI R1, #0x0032
    ST R1, [R6 -2]
    ADDI R1, R0, #30
    ST R1, [R6 -4]
    LD R1, [R6 -2]
    ST R1, [R7]
    ADDI R7, R7, #-2
    LD R1, [R6 -4]
    ADDI R7, R7, #2
    LD R2, [R7]
    SUB R1, R2, R1
    ST R1, [R6 -6]
    LD R1, [R6 -6]
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R1, R0, #20
    ADDI R7, R7, #2
    LD R2, [R7]
    XOR R1, R2, R1
    BEQ R1, R0, .el37
    XOR R1, R0, R0
    ST R1, [R6 -8]
.el37:
.ei38:
    LDI R1, #0x00FF
    ST R1, [R6 -2]
    ADDI R1, R0, #15
    ST R1, [R6 -4]
    LD R1, [R6 -2]
    ST R1, [R7]
    ADDI R7, R7, #-2
    LD R1, [R6 -4]
    ADDI R7, R7, #2
    LD R2, [R7]
    AND R1, R2, R1
    ST R1, [R6 -6]
    LD R1, [R6 -6]
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R1, R0, #15
    ADDI R7, R7, #2
    LD R2, [R7]
    XOR R1, R2, R1
    BEQ R1, R0, .el39
    XOR R1, R0, R0
    ST R1, [R6 -8]
.el39:
.ei40:
    LDI R1, #0x00F0
    ST R1, [R6 -2]
    ADDI R1, R0, #15
    ST R1, [R6 -4]
    LD R1, [R6 -2]
    ST R1, [R7]
    ADDI R7, R7, #-2
    LD R1, [R6 -4]
    ADDI R7, R7, #2
    LD R2, [R7]
    OR R1, R2, R1
    ST R1, [R6 -6]
    LD R1, [R6 -6]
    ST R1, [R7]
    ADDI R7, R7, #-2
    LDI R1, #0x00FF
    ADDI R7, R7, #2
    LD R2, [R7]
    XOR R1, R2, R1
    BEQ R1, R0, .el41
    XOR R1, R0, R0
    ST R1, [R6 -8]
.el41:
.ei42:
    LDI R1, #0x00FF
    ST R1, [R6 -2]
    LDI R1, #0x00FF
    ST R1, [R6 -4]
    LD R1, [R6 -2]
    ST R1, [R7]
    ADDI R7, R7, #-2
    LD R1, [R6 -4]
    ADDI R7, R7, #2
    LD R2, [R7]
    XOR R1, R2, R1
    ST R1, [R6 -6]
    LD R1, [R6 -6]
    ST R1, [R7]
    ADDI R7, R7, #-2
    XOR R1, R0, R0
    ADDI R7, R7, #2
    LD R2, [R7]
    XOR R1, R2, R1
    BEQ R1, R0, .el43
    XOR R1, R0, R0
    ST R1, [R6 -8]
.el43:
.ei44:
    LDI R1, #0x00FF
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
    ST R1, [R6 -6]
    LD R1, [R6 -6]
    ST R1, [R7]
    ADDI R7, R7, #-2
    LDI R1, #0x00FF
    ADDI R7, R7, #2
    LD R2, [R7]
    XOR R1, R2, R1
    BEQ R1, R0, .el45
    XOR R1, R0, R0
    ST R1, [R6 -8]
.el45:
.ei46:
    ADDI R1, R0, #1
    ST R1, [R6 -2]
    ADDI R1, R0, #3
    ST R1, [R6 -4]
    LD R1, [R6 -2]
    ST R1, [R7]
    ADDI R7, R7, #-2
    LD R1, [R6 -4]
    ADDI R7, R7, #2
    LD R2, [R7]
    SLL R1, R2, R1
    ST R1, [R6 -6]
    LD R1, [R6 -6]
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R1, R0, #8
    ADDI R7, R7, #2
    LD R2, [R7]
    XOR R1, R2, R1
    BEQ R1, R0, .el47
    XOR R1, R0, R0
    ST R1, [R6 -8]
.el47:
.ei48:
    ADDI R1, R0, #16
    ST R1, [R6 -2]
    ADDI R1, R0, #2
    ST R1, [R6 -4]
    LD R1, [R6 -2]
    ST R1, [R7]
    ADDI R7, R7, #-2
    LD R1, [R6 -4]
    ADDI R7, R7, #2
    LD R2, [R7]
    SRL R1, R2, R1
    ST R1, [R6 -6]
    LD R1, [R6 -6]
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R1, R0, #4
    ADDI R7, R7, #2
    LD R2, [R7]
    XOR R1, R2, R1
    BEQ R1, R0, .el49
    XOR R1, R0, R0
    ST R1, [R6 -8]
.el49:
.ei50:
    LDI R1, #0x00FF
    ST R1, [R6 -2]
    LD R1, [R6 -2]
    ST R1, [R7]
    ADDI R7, R7, #-2
    LDI R1, #0xFF00
    ADDI R7, R7, #2
    LD R2, [R7]
    XOR R1, R2, R1
    ST R1, [R6 -6]
    LD R1, [R6 -6]
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R1, R0, #-1
    ADDI R7, R7, #2
    LD R2, [R7]
    XOR R1, R2, R1
    BEQ R1, R0, .el51
    XOR R1, R0, R0
    ST R1, [R6 -8]
.el51:
.ei52:
    LD R1, [R6 -10]
    ST R1, [R7]
    ADDI R7, R7, #-2
    LD R1, [R6 -8]
    ADDI R7, R7, #2
    LD R2, [R7]
    ST R1, [R2 + 0]
    LD R1, [R6 -8]
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
    ST R2, [R6 -6]

    LDI R1, #0x002A
    ST R1, [R6 -2]
    LDI R1, #0x002A
    ST R1, [R6 -4]
    LD R1, [R6 -2]
    ST R1, [R7]
    ADDI R7, R7, #-2
    LD R1, [R6 -4]
    ADDI R7, R7, #2
    LD R2, [R7]
    XOR R1, R2, R1
    BEQ R1, R0, .el53
    LD R1, [R6 -6]
    ST R1, [R7]
    ADDI R7, R7, #-2
    XOR R1, R0, R0
    ADDI R7, R7, #2
    LD R2, [R7]
    ST R1, [R2 + 0]
    XOR R1, R0, R0
    JMP test_branches_epi
.el53:
.ei54:
    ADDI R1, R0, #10
    ST R1, [R6 -2]
    ADDI R1, R0, #20
    ST R1, [R6 -4]
    LD R1, [R6 -2]
    ST R1, [R7]
    ADDI R7, R7, #-2
    LD R1, [R6 -4]
    ADDI R7, R7, #2
    LD R2, [R7]
    XOR R1, R2, R1
    BNE R1, R0, .el55
    LD R1, [R6 -6]
    ST R1, [R7]
    ADDI R7, R7, #-2
    XOR R1, R0, R0
    ADDI R7, R7, #2
    LD R2, [R7]
    ST R1, [R2 + 0]
    XOR R1, R0, R0
    JMP test_branches_epi
.el55:
.ei56:
    ADDI R1, R0, #5
    ST R1, [R6 -2]
    ADDI R1, R0, #10
    ST R1, [R6 -4]
    LD R1, [R6 -2]
    ST R1, [R7]
    ADDI R7, R7, #-2
    LD R1, [R6 -4]
    ADDI R7, R7, #2
    LD R2, [R7]
    BLT R2, R1, .el57
    LD R1, [R6 -6]
    ST R1, [R7]
    ADDI R7, R7, #-2
    XOR R1, R0, R0
    ADDI R7, R7, #2
    LD R2, [R7]
    ST R1, [R2 + 0]
    XOR R1, R0, R0
    JMP test_branches_epi
.el57:
.ei58:
    LD R1, [R6 -4]
    ST R1, [R7]
    ADDI R7, R7, #-2
    LD R1, [R6 -2]
    ADDI R7, R7, #2
    LD R2, [R7]
    BLT R2, R1, .cl61
    JMP .el59
.cl61:
    LD R1, [R6 -6]
    ST R1, [R7]
    ADDI R7, R7, #-2
    XOR R1, R0, R0
    ADDI R7, R7, #2
    LD R2, [R7]
    ST R1, [R2 + 0]
    XOR R1, R0, R0
    JMP test_branches_epi
.el59:
.ei60:
    ADDI R1, R0, #10
    ST R1, [R6 -2]
    ADDI R1, R0, #5
    ST R1, [R6 -4]
    LD R1, [R6 -2]
    ST R1, [R7]
    ADDI R7, R7, #-2
    LD R1, [R6 -4]
    ADDI R7, R7, #2
    LD R2, [R7]
    BLT R1, R2, .el62
    LD R1, [R6 -6]
    ST R1, [R7]
    ADDI R7, R7, #-2
    XOR R1, R0, R0
    ADDI R7, R7, #2
    LD R2, [R7]
    ST R1, [R2 + 0]
    XOR R1, R0, R0
    JMP test_branches_epi
.el62:
.ei63:
    LD R1, [R6 -6]
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
    ADDI R7, R7, #-6
    ST R2, [R6 -6]

    LD R1, [R6 -2]
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
    LD R1, [R6 -2]
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
    LD R1, [R6 -2]
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
    LD R1, [R6 -2]
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
    LD R1, [R6 -2]
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
    LD R1, [R6 -2]
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
    LD R1, [R6 -2]
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
    LD R1, [R6 -2]
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
    ST R1, [R6 -4]
    LD R1, [R6 -2]
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
    BEQ R1, R0, .el64
    LD R1, [R6 -6]
    ST R1, [R7]
    ADDI R7, R7, #-2
    XOR R1, R0, R0
    ADDI R7, R7, #2
    LD R2, [R7]
    ST R1, [R2 + 0]
    XOR R1, R0, R0
    JMP test_memory_epi
.el64:
.ei65:
    LD R1, [R6 -2]
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
    BEQ R1, R0, .el66
    LD R1, [R6 -6]
    ST R1, [R7]
    ADDI R7, R7, #-2
    XOR R1, R0, R0
    ADDI R7, R7, #2
    LD R2, [R7]
    ST R1, [R2 + 0]
    XOR R1, R0, R0
    JMP test_memory_epi
.el66:
.ei67:
    LD R1, [R6 -2]
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
    BEQ R1, R0, .el68
    LD R1, [R6 -6]
    ST R1, [R7]
    ADDI R7, R7, #-2
    XOR R1, R0, R0
    ADDI R7, R7, #2
    LD R2, [R7]
    ST R1, [R2 + 0]
    XOR R1, R0, R0
    JMP test_memory_epi
.el68:
.ei69:
    LD R1, [R6 -2]
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
    LD R1, [R6 -2]
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
    BEQ R1, R0, .el70
    LD R1, [R6 -6]
    ST R1, [R7]
    ADDI R7, R7, #-2
    XOR R1, R0, R0
    ADDI R7, R7, #2
    LD R2, [R7]
    ST R1, [R2 + 0]
    XOR R1, R0, R0
    JMP test_memory_epi
.el70:
.ei71:
    ADDI R1, R0, #5
    ST R1, [R6 -4]
    LD R1, [R6 -2]
    ST R1, [R7]
    ADDI R7, R7, #-2
    LD R1, [R6 -4]
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
    BEQ R1, R0, .el72
    LD R1, [R6 -6]
    ST R1, [R7]
    ADDI R7, R7, #-2
    XOR R1, R0, R0
    ADDI R7, R7, #2
    LD R2, [R7]
    ST R1, [R2 + 0]
    XOR R1, R0, R0
    JMP test_memory_epi
.el72:
.ei73:
    LD R1, [R6 -6]
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R1, R0, #1
    ADDI R7, R7, #2
    LD R2, [R7]
    ST R1, [R2 + 0]
    ADDI R1, R0, #1
test_memory_epi:
    ADDI R7, R7, #6
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
    ST R2, [R6 -2]

    LD R1, [R6 -2]
    ST R1, [R7]
    ADDI R7, R7, #-2
    XOR R1, R0, R0
    ADDI R7, R7, #2
    LD R2, [R7]
    BLT R1, R2, .el74
    LDI R1, #0x002A
    JMP deep_epi
.el74:
.ei75:
    LD R1, [R6 -2]
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
    LDI R5, #.cr76
    LDI R1, #deep
    JMP R1
.cr76:
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
    ST R2, [R6 -4]

    ADDI R1, R0, #5
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    LDI R5, #.cr77
    LDI R1, #deep
    JMP R1
.cr77:
    ST R1, [R6 -2]
    LD R1, [R6 -2]
    ST R1, [R7]
    ADDI R7, R7, #-2
    LDI R1, #0x002F
    ADDI R7, R7, #2
    LD R2, [R7]
    XOR R1, R2, R1
    BEQ R1, R0, .el78
    LD R1, [R6 -4]
    ST R1, [R7]
    ADDI R7, R7, #-2
    XOR R1, R0, R0
    ADDI R7, R7, #2
    LD R2, [R7]
    ST R1, [R2 + 0]
    XOR R1, R0, R0
    JMP test_stack_epi
.el78:
.ei79:
    LD R1, [R6 -4]
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
    ST R2, [R6 -8]

    ADDI R1, R0, #7
    ST R1, [R6 -2]
    ADDI R1, R0, #8
    ST R1, [R6 -4]
    LD R1, [R6 -2]
    ST R1, [R7]
    ADDI R7, R7, #-2
    LD R1, [R6 -4]
    ADDI R7, R7, #2
    LD R2, [R7]
    ST R5, [R7]
    ADDI R7, R7, #-2
    ST R1, [R7]
    ADDI R7, R7, #-2
    LDI R5, #.mul16_ret80
    LDI R4, #__mul16
    LD R1, [R7 +2]
    JMP R4
.mul16_ret80:
    ADDI R7, R7, #2
    ADDI R7, R7, #2
    LD R5, [R7]
    ST R1, [R6 -6]
    LD R1, [R6 -6]
    ST R1, [R7]
    ADDI R7, R7, #-2
    LDI R1, #0x0038
    ADDI R7, R7, #2
    LD R2, [R7]
    XOR R1, R2, R1
    BEQ R1, R0, .el81
    LD R1, [R6 -8]
    ST R1, [R7]
    ADDI R7, R7, #-2
    XOR R1, R0, R0
    ADDI R7, R7, #2
    LD R2, [R7]
    ST R1, [R2 + 0]
    XOR R1, R0, R0
    JMP test_runlib_epi
.el81:
.ei82:
    LDI R1, #0x007B
    ST R1, [R6 -2]
    LDI R1, #0x002D
    ST R1, [R6 -4]
    LD R1, [R6 -2]
    ST R1, [R7]
    ADDI R7, R7, #-2
    LD R1, [R6 -4]
    ADDI R7, R7, #2
    LD R2, [R7]
    ST R5, [R7]
    ADDI R7, R7, #-2
    ST R1, [R7]
    ADDI R7, R7, #-2
    LDI R5, #.mul16_ret83
    LDI R4, #__mul16
    LD R1, [R7 +2]
    JMP R4
.mul16_ret83:
    ADDI R7, R7, #2
    ADDI R7, R7, #2
    LD R5, [R7]
    ST R1, [R6 -6]
    LD R1, [R6 -6]
    ST R1, [R7]
    ADDI R7, R7, #-2
    LDI R1, #0x159F
    ADDI R7, R7, #2
    LD R2, [R7]
    XOR R1, R2, R1
    BEQ R1, R0, .el84
    LD R1, [R6 -8]
    ST R1, [R7]
    ADDI R7, R7, #-2
    XOR R1, R0, R0
    ADDI R7, R7, #2
    LD R2, [R7]
    ST R1, [R2 + 0]
    XOR R1, R0, R0
    JMP test_runlib_epi
.el84:
.ei85:
    LDI R1, #0x0064
    ST R1, [R6 -2]
    ADDI R1, R0, #7
    ST R1, [R6 -4]
    LD R1, [R6 -2]
    ST R1, [R7]
    ADDI R7, R7, #-2
    LD R1, [R6 -4]
    ADDI R7, R7, #2
    LD R2, [R7]
    ST R5, [R7]
    ADDI R7, R7, #-2
    ST R1, [R7]
    ADDI R7, R7, #-2
    LDI R5, #.div16_ret86
    LDI R4, #__div16
    LD R1, [R7 +2]
    JMP R4
.div16_ret86:
    ADDI R7, R7, #2
    ADDI R7, R7, #2
    LD R5, [R7]
    ST R1, [R6 -6]
    LD R1, [R6 -6]
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R1, R0, #14
    ADDI R7, R7, #2
    LD R2, [R7]
    XOR R1, R2, R1
    BEQ R1, R0, .el87
    LD R1, [R6 -8]
    ST R1, [R7]
    ADDI R7, R7, #-2
    XOR R1, R0, R0
    ADDI R7, R7, #2
    LD R2, [R7]
    ST R1, [R2 + 0]
    XOR R1, R0, R0
    JMP test_runlib_epi
.el87:
.ei88:
    LDI R1, #0x0064
    ST R1, [R6 -2]
    ADDI R1, R0, #7
    ST R1, [R6 -4]
    LD R1, [R6 -2]
    ST R1, [R7]
    ADDI R7, R7, #-2
    LD R1, [R6 -4]
    ADDI R7, R7, #2
    LD R2, [R7]
    ST R5, [R7]
    ADDI R7, R7, #-2
    ST R1, [R7]
    ADDI R7, R7, #-2
    LDI R5, #.mod16_ret89
    LDI R4, #__mod16
    LD R1, [R7 +2]
    JMP R4
.mod16_ret89:
    ADDI R7, R7, #2
    ADDI R7, R7, #2
    LD R5, [R7]
    ST R1, [R6 -6]
    LD R1, [R6 -6]
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R1, R0, #2
    ADDI R7, R7, #2
    LD R2, [R7]
    XOR R1, R2, R1
    BEQ R1, R0, .el90
    LD R1, [R6 -8]
    ST R1, [R7]
    ADDI R7, R7, #-2
    XOR R1, R0, R0
    ADDI R7, R7, #2
    LD R2, [R7]
    ST R1, [R2 + 0]
    XOR R1, R0, R0
    JMP test_runlib_epi
.el90:
.ei91:
    LD R1, [R6 -8]
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
    ADDI R7, R7, #-8
    ST R2, [R6 -8]

    LD R1, [R6 -2]
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
    LD R1, [R6 -2]
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
    LD R1, [R6 -2]
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
    LD R1, [R6 -2]
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
    LD R1, [R6 -2]
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
    LD R1, [R6 -2]
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
    ST R1, [R6 -6]
    XOR R1, R0, R0
    ST R1, [R6 -4]
.w92:
    LD R1, [R6 -4]
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R1, R0, #6
    ADDI R7, R7, #2
    LD R2, [R7]
    BLT R2, R1, .cl94
    JMP .we93
.cl94:
    LD R1, [R6 -6]
    ST R1, [R7]
    ADDI R7, R7, #-2
    LD R1, [R6 -2]
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
    ST R1, [R6 -6]
    LD R1, [R6 -4]
    ADDI R1, R1, #1
    ST R1, [R6 -4]
    JMP .w92
.we93:
    LD R1, [R6 -6]
    ST R1, [R7]
    ADDI R7, R7, #-2
    LDI R1, #0x00D2
    ADDI R7, R7, #2
    LD R2, [R7]
    XOR R1, R2, R1
    BEQ R1, R0, .el95
    LD R1, [R6 -8]
    ST R1, [R7]
    ADDI R7, R7, #-2
    XOR R1, R0, R0
    ADDI R7, R7, #2
    LD R2, [R7]
    ST R1, [R2 + 0]
    XOR R1, R0, R0
    JMP test_arrays_epi
.el95:
.ei96:
    ADDI R1, R0, #1
    ST R1, [R6 -4]
    LD R1, [R6 -2]
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
    ADDI R1, R0, #25
    ADDI R7, R7, #2
    LD R2, [R7]
    ST R1, [R2 + 0]
    LD R1, [R6 -2]
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
    BEQ R1, R0, .el97
    LD R1, [R6 -8]
    ST R1, [R7]
    ADDI R7, R7, #-2
    XOR R1, R0, R0
    ADDI R7, R7, #2
    LD R2, [R7]
    ST R1, [R2 + 0]
    XOR R1, R0, R0
    JMP test_arrays_epi
.el97:
.ei98:
    LD R1, [R6 -8]
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R1, R0, #1
    ADDI R7, R7, #2
    LD R2, [R7]
    ST R1, [R2 + 0]
    ADDI R1, R0, #1
test_arrays_epi:
    ADDI R7, R7, #8
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
    ST R2, [R6 -6]

    LDI R1, #0x004D
    ST R1, [R6 -2]
    ADDI R1, R6, #-2
    ST R1, [R6 -4]
    LD R1, [R6 -4]
    LD R1, [R1 + 0]
    ST R1, [R7]
    ADDI R7, R7, #-2
    LDI R1, #0x004D
    ADDI R7, R7, #2
    LD R2, [R7]
    XOR R1, R2, R1
    BEQ R1, R0, .el99
    LD R1, [R6 -6]
    ST R1, [R7]
    ADDI R7, R7, #-2
    XOR R1, R0, R0
    ADDI R7, R7, #2
    LD R2, [R7]
    ST R1, [R2 + 0]
    XOR R1, R0, R0
    JMP test_pointers_epi
.el99:
.ei100:
    LD R1, [R6 -4]
    ST R1, [R7]
    ADDI R7, R7, #-2
    LDI R1, #0x0058
    ADDI R7, R7, #2
    LD R2, [R7]
    ST R1, [R2 + 0]
    LD R1, [R6 -2]
    ST R1, [R7]
    ADDI R7, R7, #-2
    LDI R1, #0x0058
    ADDI R7, R7, #2
    LD R2, [R7]
    XOR R1, R2, R1
    BEQ R1, R0, .el101
    LD R1, [R6 -6]
    ST R1, [R7]
    ADDI R7, R7, #-2
    XOR R1, R0, R0
    ADDI R7, R7, #2
    LD R2, [R7]
    ST R1, [R2 + 0]
    XOR R1, R0, R0
    JMP test_pointers_epi
.el101:
.ei102:
    LD R1, [R6 -6]
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
    ST R2, [R6 -4]

    ADDI R1, R0, #-1
    ST R1, [R6 -2]
    LD R1, [R6 -2]
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R1, R0, #-1
    ADDI R7, R7, #2
    LD R2, [R7]
    XOR R1, R2, R1
    BEQ R1, R0, .el103
    LD R1, [R6 -4]
    ST R1, [R7]
    ADDI R7, R7, #-2
    XOR R1, R0, R0
    ADDI R7, R7, #2
    LD R2, [R7]
    ST R1, [R2 + 0]
    XOR R1, R0, R0
    JMP test_ldi_epi
.el103:
.ei104:
    LDI R1, #0x7FFF
    ST R1, [R6 -2]
    LD R1, [R6 -2]
    ST R1, [R7]
    ADDI R7, R7, #-2
    LDI R1, #0x7FFF
    ADDI R7, R7, #2
    LD R2, [R7]
    XOR R1, R2, R1
    BEQ R1, R0, .el105
    LD R1, [R6 -4]
    ST R1, [R7]
    ADDI R7, R7, #-2
    XOR R1, R0, R0
    ADDI R7, R7, #2
    LD R2, [R7]
    ST R1, [R2 + 0]
    XOR R1, R0, R0
    JMP test_ldi_epi
.el105:
.ei106:
    ADDI R1, R0, #1
    ST R1, [R6 -2]
    LD R1, [R6 -2]
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R1, R0, #1
    ADDI R7, R7, #2
    LD R2, [R7]
    XOR R1, R2, R1
    BEQ R1, R0, .el107
    LD R1, [R6 -4]
    ST R1, [R7]
    ADDI R7, R7, #-2
    XOR R1, R0, R0
    ADDI R7, R7, #2
    LD R2, [R7]
    ST R1, [R2 + 0]
    XOR R1, R0, R0
    JMP test_ldi_epi
.el107:
.ei108:
    LD R1, [R6 -4]
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
    ST R2, [R6 -2]

    LD R1, [R6 -2]
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
    ST R2, [R6 -8]

    LDI R1, #.Lstr2
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    LDI R5, #.cr109
    LDI R1, #print
    JMP R1
.cr109:
    LDI R5, #.cr110
    LDI R1, #getchar
    JMP R1
.cr110:
    ST R1, [R6 -2]
    LD R1, [R6 -2]
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    LDI R5, #.cr111
    LDI R1, #putchar
    JMP R1
.cr111:
    LDI R5, #.cr112
    LDI R1, #getchar
    JMP R1
.cr112:
    ST R1, [R6 -4]
    LD R1, [R6 -4]
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    LDI R5, #.cr113
    LDI R1, #putchar
    JMP R1
.cr113:
    ADDI R1, R0, #10
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    LDI R5, #.cr114
    LDI R1, #putchar
    JMP R1
.cr114:
    ADDI R1, R0, #1
    ST R1, [R6 -6]
    LD R1, [R6 -2]
    ST R1, [R7]
    ADDI R7, R7, #-2
    LDI R1, #0x0020
    ADDI R7, R7, #2
    LD R2, [R7]
    BLT R2, R1, .cl117
    JMP .el115
.cl117:
    XOR R1, R0, R0
    ST R1, [R6 -6]
.el115:
.ei116:
    LD R1, [R6 -4]
    ST R1, [R7]
    ADDI R7, R7, #-2
    LDI R1, #0x0020
    ADDI R7, R7, #2
    LD R2, [R7]
    BLT R2, R1, .cl120
    JMP .el118
.cl120:
    XOR R1, R0, R0
    ST R1, [R6 -6]
.el118:
.ei119:
    LD R1, [R6 -8]
    ST R1, [R7]
    ADDI R7, R7, #-2
    LD R1, [R6 -6]
    ADDI R7, R7, #2
    LD R2, [R7]
    ST R1, [R2 + 0]
    LD R1, [R6 -6]
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
    ST R1, [R6 -2]
    XOR R1, R0, R0
    ST R1, [R6 -4]
    LDI R5, #.cr121
    LDI R1, #nl
    JMP R1
.cr121:
    LDI R1, #.Lstr3
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    LDI R5, #.cr122
    LDI R1, #print
    JMP R1
.cr122:
    LDI R1, #.Lstr4
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    LDI R5, #.cr123
    LDI R1, #print
    JMP R1
.cr123:
    LDI R1, #.Lstr5
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    LDI R5, #.cr124
    LDI R1, #print
    JMP R1
.cr124:
    LDI R5, #.cr125
    LDI R1, #nl
    JMP R1
.cr125:
    LDI R1, #.Lstr6
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    LDI R5, #.cr126
    LDI R1, #print
    JMP R1
.cr126:
    ADDI R1, R6, #-6
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    LDI R5, #.cr129
    LDI R1, #test_alu
    JMP R1
.cr129:
    BEQ R1, R0, .el127
    LDI R1, #.Lstr7
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    LDI R5, #.cr130
    LDI R1, #pass
    JMP R1
.cr130:
    LD R1, [R6 -2]
    ADDI R1, R1, #1
    ST R1, [R6 -2]
    JMP .ei128
.el127:
    LDI R1, #.Lstr8
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    LDI R5, #.cr131
    LDI R1, #fail
    JMP R1
.cr131:
    LD R1, [R6 -4]
    ADDI R1, R1, #1
    ST R1, [R6 -4]
.ei128:
    LDI R1, #.Lstr9
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    LDI R5, #.cr132
    LDI R1, #print
    JMP R1
.cr132:
    ADDI R1, R6, #-6
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    LDI R5, #.cr135
    LDI R1, #test_branches
    JMP R1
.cr135:
    BEQ R1, R0, .el133
    LDI R1, #.Lstr10
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    LDI R5, #.cr136
    LDI R1, #pass
    JMP R1
.cr136:
    LD R1, [R6 -2]
    ADDI R1, R1, #1
    ST R1, [R6 -2]
    JMP .ei134
.el133:
    LDI R1, #.Lstr11
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    LDI R5, #.cr137
    LDI R1, #fail
    JMP R1
.cr137:
    LD R1, [R6 -4]
    ADDI R1, R1, #1
    ST R1, [R6 -4]
.ei134:
    LDI R1, #.Lstr12
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    LDI R5, #.cr138
    LDI R1, #print
    JMP R1
.cr138:
    ADDI R1, R6, #-6
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    LDI R5, #.cr141
    LDI R1, #test_memory
    JMP R1
.cr141:
    BEQ R1, R0, .el139
    LDI R1, #.Lstr13
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    LDI R5, #.cr142
    LDI R1, #pass
    JMP R1
.cr142:
    LD R1, [R6 -2]
    ADDI R1, R1, #1
    ST R1, [R6 -2]
    JMP .ei140
.el139:
    LDI R1, #.Lstr14
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    LDI R5, #.cr143
    LDI R1, #fail
    JMP R1
.cr143:
    LD R1, [R6 -4]
    ADDI R1, R1, #1
    ST R1, [R6 -4]
.ei140:
    LDI R1, #.Lstr15
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    LDI R5, #.cr144
    LDI R1, #print
    JMP R1
.cr144:
    ADDI R1, R6, #-6
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    LDI R5, #.cr147
    LDI R1, #test_stack
    JMP R1
.cr147:
    BEQ R1, R0, .el145
    LDI R1, #.Lstr16
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    LDI R5, #.cr148
    LDI R1, #pass
    JMP R1
.cr148:
    LD R1, [R6 -2]
    ADDI R1, R1, #1
    ST R1, [R6 -2]
    JMP .ei146
.el145:
    LDI R1, #.Lstr17
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    LDI R5, #.cr149
    LDI R1, #fail
    JMP R1
.cr149:
    LD R1, [R6 -4]
    ADDI R1, R1, #1
    ST R1, [R6 -4]
.ei146:
    LDI R1, #.Lstr18
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    LDI R5, #.cr150
    LDI R1, #print
    JMP R1
.cr150:
    ADDI R1, R6, #-6
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    LDI R5, #.cr153
    LDI R1, #test_runlib
    JMP R1
.cr153:
    BEQ R1, R0, .el151
    LDI R1, #.Lstr19
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    LDI R5, #.cr154
    LDI R1, #pass
    JMP R1
.cr154:
    LD R1, [R6 -2]
    ADDI R1, R1, #1
    ST R1, [R6 -2]
    JMP .ei152
.el151:
    LDI R1, #.Lstr20
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    LDI R5, #.cr155
    LDI R1, #fail
    JMP R1
.cr155:
    LD R1, [R6 -4]
    ADDI R1, R1, #1
    ST R1, [R6 -4]
.ei152:
    LDI R1, #.Lstr21
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    LDI R5, #.cr156
    LDI R1, #print
    JMP R1
.cr156:
    ADDI R1, R6, #-6
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    LDI R5, #.cr159
    LDI R1, #test_arrays
    JMP R1
.cr159:
    BEQ R1, R0, .el157
    LDI R1, #.Lstr22
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    LDI R5, #.cr160
    LDI R1, #pass
    JMP R1
.cr160:
    LD R1, [R6 -2]
    ADDI R1, R1, #1
    ST R1, [R6 -2]
    JMP .ei158
.el157:
    LDI R1, #.Lstr23
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    LDI R5, #.cr161
    LDI R1, #fail
    JMP R1
.cr161:
    LD R1, [R6 -4]
    ADDI R1, R1, #1
    ST R1, [R6 -4]
.ei158:
    LDI R1, #.Lstr24
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    LDI R5, #.cr162
    LDI R1, #print
    JMP R1
.cr162:
    ADDI R1, R6, #-6
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    LDI R5, #.cr165
    LDI R1, #test_pointers
    JMP R1
.cr165:
    BEQ R1, R0, .el163
    LDI R1, #.Lstr25
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    LDI R5, #.cr166
    LDI R1, #pass
    JMP R1
.cr166:
    LD R1, [R6 -2]
    ADDI R1, R1, #1
    ST R1, [R6 -2]
    JMP .ei164
.el163:
    LDI R1, #.Lstr26
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    LDI R5, #.cr167
    LDI R1, #fail
    JMP R1
.cr167:
    LD R1, [R6 -4]
    ADDI R1, R1, #1
    ST R1, [R6 -4]
.ei164:
    LDI R1, #.Lstr27
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    LDI R5, #.cr168
    LDI R1, #print
    JMP R1
.cr168:
    ADDI R1, R6, #-6
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    LDI R5, #.cr171
    LDI R1, #test_ldi
    JMP R1
.cr171:
    BEQ R1, R0, .el169
    LDI R1, #.Lstr28
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    LDI R5, #.cr172
    LDI R1, #pass
    JMP R1
.cr172:
    LD R1, [R6 -2]
    ADDI R1, R1, #1
    ST R1, [R6 -2]
    JMP .ei170
.el169:
    LDI R1, #.Lstr29
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    LDI R5, #.cr173
    LDI R1, #fail
    JMP R1
.cr173:
    LD R1, [R6 -4]
    ADDI R1, R1, #1
    ST R1, [R6 -4]
.ei170:
    LDI R1, #.Lstr30
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    LDI R5, #.cr174
    LDI R1, #print
    JMP R1
.cr174:
    ADDI R1, R6, #-6
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    LDI R5, #.cr177
    LDI R1, #test_uart_tx
    JMP R1
.cr177:
    BEQ R1, R0, .el175
    LDI R1, #.Lstr31
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    LDI R5, #.cr178
    LDI R1, #pass
    JMP R1
.cr178:
    LD R1, [R6 -2]
    ADDI R1, R1, #1
    ST R1, [R6 -2]
    JMP .ei176
.el175:
    LDI R1, #.Lstr32
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    LDI R5, #.cr179
    LDI R1, #fail
    JMP R1
.cr179:
    LD R1, [R6 -4]
    ADDI R1, R1, #1
    ST R1, [R6 -4]
.ei176:
    LDI R1, #.Lstr33
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    LDI R5, #.cr180
    LDI R1, #print
    JMP R1
.cr180:
    ADDI R1, R6, #-6
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    LDI R5, #.cr183
    LDI R1, #test_uart_rx
    JMP R1
.cr183:
    BEQ R1, R0, .el181
    LDI R1, #.Lstr34
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    LDI R5, #.cr184
    LDI R1, #pass
    JMP R1
.cr184:
    LD R1, [R6 -2]
    ADDI R1, R1, #1
    ST R1, [R6 -2]
    JMP .ei182
.el181:
    LDI R1, #.Lstr35
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    LDI R5, #.cr185
    LDI R1, #fail
    JMP R1
.cr185:
    LD R1, [R6 -4]
    ADDI R1, R1, #1
    ST R1, [R6 -4]
.ei182:
    LDI R5, #.cr186
    LDI R1, #nl
    JMP R1
.cr186:
    LDI R1, #.Lstr36
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    LDI R5, #.cr187
    LDI R1, #print
    JMP R1
.cr187:
    LDI R1, #.Lstr37
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    LDI R5, #.cr188
    LDI R1, #print
    JMP R1
.cr188:
    LD R1, [R6 -2]
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    LDI R5, #.cr189
    LDI R1, #print_dec
    JMP R1
.cr189:
    LDI R1, #.Lstr38
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    LDI R5, #.cr190
    LDI R1, #print
    JMP R1
.cr190:
    LD R1, [R6 -2]
    ST R1, [R7]
    ADDI R7, R7, #-2
    LD R1, [R6 -4]
    ADDI R7, R7, #2
    LD R2, [R7]
    ADD R1, R2, R1
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    LDI R5, #.cr191
    LDI R1, #print_dec
    JMP R1
.cr191:
    LDI R1, #.Lstr39
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    LDI R5, #.cr192
    LDI R1, #print
    JMP R1
.cr192:
    LDI R1, #.Lstr40
    ST R1, [R7]
    ADDI R7, R7, #-2
    ADDI R7, R7, #2
    LD R2, [R7]
    LDI R5, #.cr193
    LDI R1, #print
    JMP R1
.cr193:
    LDI R5, #.cr194
    LDI R1, #nl
    JMP R1
.cr194:
.w195:
    JMP .w195
.we196:
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

__div16:
    BEQ R1, R0, __div_exit
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
    BEQ R6, R0, __div_nb
    ADDI R6, R0, #1
    OR R5, R5, R6
__div_nb:
    ADDI R6, R0, #1
    SLL R2, R2, R6
    SUB R6, R5, R1
    BLT R5, R1, __div_sk
    ADD R5, R0, R6
    ADDI R6, R0, #1
    OR R3, R3, R6
__div_sk:
    ADDI R4, R4, #-1
    BNE R4, R0, __div_lp
    ADD R1, R0, R3
    ADDI R7, R7, #2
    LD R6, [R7]
    ADDI R7, R7, #2
    LD R5, [R7]
    JMP R5

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