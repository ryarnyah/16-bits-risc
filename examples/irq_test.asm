; Phase 4 interrupt test (zero-ISA model — ISA-3 §10.4): fixed vector at
; 0x0008, hardware auto-DI on entry, EPC via the 0x2002 read window,
; SWIRQ + IMASK via 0x2000 CTRL, return with the existing LD/JMP pair.
; Covers: entry redirect, EPC link correctness (the handler must resume
; main exactly where it left off), mask drop on entry, software ack, and
; the registered int-register read path on BOTH cores.
; Style: R1 = 0x5A iff the interrupt fired, the handler ran, and main
; resumed; any failure leaves R1 = 0 (HALT trap or step-budget timeout).

        .org 0x0000
        JMP  start               ; LDI8 R4,#start ; JMP R4 (2 words, start < 256)
        ADD  R0, R0, R0          ; 0x0004 filler (wide JMP would use 0..6)
        ADD  R0, R0, R0          ; 0x0006 filler

        .org 0x0008              ; --- IRQ vector (hardware enters here) ---
handler:
        LDI  R4, #0x2000         ; INT_CTRL
        ST   R0, [R4 + 0]        ; IMASK=0 (DI), SWIRQ=0 (ack) — hw already auto-DI'd
        LDI  R4, #0x2002         ; EPC read window
        LD   R6, [R4 + 0]        ; return address = next unexecuted instruction
        LDI  R2, #1              ; flag: interrupt serviced
        JMP  R6                  ; return (existing ISA — no RETI needed)

start:
        LDI  R2, #0              ; clear the flag
        LDI  R4, #0x2000         ; INT_CTRL
        LDI  R5, #3              ; IMASK=1 | SWIRQ=1
        ST   R5, [R4 + 0]        ; arm + trigger — irq rises when this write lands
wait:
        BR   Z, R2, wait         ; spin until the handler sets R2 (EPC returns here)
        LDI  R1, #0x5A           ; success marker (core-independent value)
done:
        B    done                ; spin forever

bad:                             ; unreachable fail trap kept for symmetry
        HALT
