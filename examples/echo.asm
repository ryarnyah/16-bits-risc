; UART Echo Program
; R1 = UART_RX address (0x1FFC)
; R2 = UART_TX address (0x1FFE)
; R6 = loop address
    LDI R1, #0x1FFC
    LDI R2, #0x1FFE
    LDI R6, #loop
loop:
    LD  R3, [R1 + 0]
    ST  R3, [R2 + 0]
    JMP R6
