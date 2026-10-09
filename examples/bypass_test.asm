; Phase 3 store-to-load bypass test: word ST then LD to the same word
; index completes from the tracker (no bus trip); STB invalidates;
; re-track on the next word ST; R0-dest LD leaves the tracker intact.
; R1 = 0x2A iff every check passed (v3.1 word offsets throughout).

        CLR  R1                    ; fail marker = 0
        LDI  R2, #0x1F00           ; base word address
        LDI  R3, #0x55AA
        ST   R3, [R2 + 0]          ; track {0x1F00:0x55AA}
        LD   R4, [R2 + 0]          ; bypass -> 0x55AA
        SUB  R4, R4, R3
        BR   Z, R4, ok1
        HALT
ok1:    LD   R4, [R2 + 0]          ; bypass again (tracker intact)
        SUB  R4, R4, R3
        BR   Z, R4, ok2
        HALT
ok2:    ; STB low lane invalidates the track; word becomes 0x5512 via RAM
        LDI  R3, #0x12
        STB  R3, [R2 + 0]
        LD   R4, [R2 + 0]          ; must NOT bypass -> RAM 0x5512
        LDI  R5, #0x5512
        SUB  R4, R4, R5
        BR   Z, R4, ok3
        HALT
ok3:    ; different word: normal ST re-tracks, LD bypasses fresh entry
        LDI  R5, #0x1234
        ST   R5, [R2 + 1]          ; word 0x1F02 = 0x1234
        LD   R4, [R2 + 1]          ; bypass -> 0x1234
        SUB  R4, R4, R5
        BR   Z, R4, ok4
        HALT
ok4:    ; R0-dest LD still bypasses for the next reader
        LD   R0, [R2 + 1]
        LD   R4, [R2 + 1]
        SUB  R4, R4, R5
        BR   Z, R4, done
        HALT
done:   LDI  R1, #0x2A
        HALT
