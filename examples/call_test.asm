; v2.1 CALL / RET — link register + jump, return must resume correctly
; R1 = 42: fn sets 21, returns, caller doubles it
        LDI  R2, #fn           ; label < 256 -> LDI8 (1 word)
        CALL R5, R2            ; R5 = return address
        ADD  R1, R1, R1        ; runs only if control really returned here
        HALT
fn:     LDI  R1, #21
        RET  R5
