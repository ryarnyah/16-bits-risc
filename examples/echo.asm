; UART Echo Program
; R1 = UART_RX address (0xFFFC)
; R2 = UART_TX address (0xFFFE)
; R6 = loop address
    LDI R1, #0xFFFC
    LDI R2, #0xFFFE
    LDI R6, #loop
loop:
    LD  R3, [R1 + 0]
    ST  R3, [R2 + 0]
    JMP R6
