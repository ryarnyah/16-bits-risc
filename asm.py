#!/usr/bin/env python3
"""16-bit RISC assembler: asm -> hex word list (ISA v2.1).

v2.1 notes:
  * LDI auto-selects the 1-word LDI8 form when the immediate resolves to
    0..255 (needs a layout fixpoint because instruction sizes depend on
    resolved label values).
  * New mnemonics: SLT/SLTU/SRA, CALL, HALT, LDI8, LDB/STB and the
    in-place group-B immediates ANDI/ORI/SLLI/SRLI/SRAI.
  * Pseudo-ops: MOV NEG NOT CLR LSL LSR ASR B BGT BGE BLE BLTU BGEU BLEU
    RET, 3-op immediate forms, `.scratch Rn`.
"""

import sys, re, argparse

OPCODES = {
    "ADD":  0, "ADDI": 1, "XOR": 2, "XORI": 3,
    "SUB":  4, "AND":  5, "OR":   6, "SLL":  7,
    "SRL":  8, "LD":   9, "ST":   0xA, "JMP": 0xB,
    "BEQ": 0xC, "BNE": 0xD, "BLT": 0xE, "LDI": 0xF,
}

REGS = {f"R{i}": i for i in range(8)}

# v2.1 bank A: register ALU ops with funct3
FUNCT3 = {"SLT": (4, 1), "SLTU": (4, 2), "SRA": (8, 1)}
# v2.1 bank B: in-place group-B immediates (cf field)
GRPBF  = {"ANDI": 0b011, "ORI": 0b100, "SLLI": 0b101,
          "SRLI": 0b110, "SRAI": 0b111}
# single-branch mnemonics (participate in relaxation)
BRANCH1 = ("BEQ", "BNE", "BLT", "B", "BGT")
# fixed 2-word (4 byte) compare-and-branch pseudos
BRANCH2 = ("BGE", "BLE", "BLTU", "BGEU", "BLEU")
# everything else that always assembles to exactly 1 word
ONEWORD = ("LDI8", "CALL", "HALT", "LDB", "STB",
           "MOV", "NEG", "NOT", "CLR", "LSL", "LSR", "ASR", "RET", "B")

def strip_hash(s):
    return s.lstrip("#")

def parse_num(s):
    s = strip_hash(s)
    if s.startswith("0x"):
        return int(s, 16)
    if s.startswith("0b"):
        return int(s, 2)
    return int(s)

def tokenize(line):
    line = re.sub(r";.*", "", line).strip()
    return re.findall(r'\.[A-Za-z_]\w*:|\.[A-Za-z_]\w*|[A-Za-z_]\w*:|[A-Za-z_]\w*|#?[+\-]?\w+|[\[\],:+()]', line)

def get_args(toks):
    return [strip_hash(t) for t in toks[1:]
            if t not in (",", "[", "]", "+", "#") and t != "+"]

def resolve(val, labels):
    """Resolve a label reference or numeric literal to an integer."""
    if val in labels:
        return labels[val]
    return parse_num(val)

# ---------------------------------------------------------------------------
# Instruction encodings (ISA v2.1 §3)
# ---------------------------------------------------------------------------

def enc_alu(op, rd, rs, rt, f3=0):
    """Register ALU: op Rd, Rs, Rt [with funct3]."""
    return (op << 12) | (rd << 9) | (rs << 6) | (rt << 3) | f3

def enc_imm(op, rd, rs, imm):
    """Immediate ALU: ADDI/XORI Rd, Rs, #imm6 (sign-extended by core)."""
    return (op << 12) | (rd << 9) | (rs << 6) | (imm & 0x3F)

def enc_branch(op, rs, rt, off):
    return (op << 12) | (rs << 9) | (rt << 6) | (off & 0x3F)

def enc_grpb(cf, rd, imm):
    """Group-B immediate: ANDI/ORI/SLLI/SRLI/SRAI Rd, #imm6 (zext)."""
    return (0xB << 12) | (rd << 9) | (cf << 6) | (imm & 0x3F)

def enc_call(rl, rtgt):
    """CALL Rlink, Rtarget: cf=001, payload = Rtarget || 000."""
    return (0xB << 12) | (rl << 9) | (1 << 6) | (rtgt << 3)

def enc_halt():
    """HALT: cf=010, Rd=000, payload=0."""
    return (0xB << 12) | (2 << 6)

def enc_ldi8(rd, imm):
    """LDI8 Rd, #imm8: b8=1, imm8 in [7:0]."""
    return (0xF << 12) | (rd << 9) | (1 << 8) | (imm & 0xFF)

def enc_ldstb(mf, rd, rs, off):
    """LDB (mf=01) / STB (mf=10) Rd, [Rs + off3]."""
    return (0xF << 12) | (rd << 9) | (mf << 6) | (rs << 3) | (off & 7)

# ---------------------------------------------------------------------------
# Pseudo-op expansion -> list of (mnemonic, args)
# ---------------------------------------------------------------------------

def expand(m, args, scratch):
    """Expand a pseudo-op into one or two real instructions."""
    if m == "MOV":
        return [("ADD", [args[0], args[1], "R0"])]
    if m == "NEG":
        return [("SUB", [args[0], "R0", args[1]])]
    if m == "NOT":
        return [("XORI", [args[0], args[1], "-1"])]
    if m == "CLR":
        return [("XOR", [args[0], args[0], args[0]])]
    if m == "LSL":
        return [("SLL", args)]
    if m == "LSR":
        return [("SRL", args)]
    if m == "ASR":
        return [("SRA", args)]
    if m == "B":
        return [("BEQ", ["R0", "R0", args[0]])]
    if m == "BGT":
        return [("BLT", [args[1], args[0], args[2]])]
    if m == "RET":
        return [("JMP", [args[0] if args else "R5"])]
    if m == "BGE":    # SLT tmp,Rs,Rt ; BEQ tmp,R0,L
        t = f"R{scratch}"
        return [("SLT", [t, args[0], args[1]]), ("BEQ", [t, "R0", args[2]])]
    if m == "BLE":    # SLT tmp,Rt,Rs ; BEQ tmp,R0,L
        t = f"R{scratch}"
        return [("SLT", [t, args[1], args[0]]), ("BEQ", [t, "R0", args[2]])]
    if m == "BLTU":   # SLTU tmp,Rs,Rt ; BNE tmp,R0,L
        t = f"R{scratch}"
        return [("SLTU", [t, args[0], args[1]]), ("BNE", [t, "R0", args[2]])]
    if m == "BGEU":   # SLTU tmp,Rs,Rt ; BEQ tmp,R0,L
        t = f"R{scratch}"
        return [("SLTU", [t, args[0], args[1]]), ("BEQ", [t, "R0", args[2]])]
    if m == "BLEU":   # SLTU tmp,Rt,Rs ; BEQ tmp,R0,L
        t = f"R{scratch}"
        return [("SLTU", [t, args[1], args[0]]), ("BEQ", [t, "R0", args[2]])]
    if m in GRPBF and len(args) == 3:
        # 3-op form: ANDI Rd, Rs, #imm -> ADD Rd, Rs, R0 + ANDI Rd, #imm
        # (copy skipped when Rs == Rd, the natural in-place form)
        rd, rs, imm = args
        if rs == rd:
            return [(m, [rd, imm])]
        return [("ADD", [rd, rs, "R0"]), (m, [rd, imm])]
    return [(m, args)]

# ---------------------------------------------------------------------------
# Sizes / labels / relaxation / LDI narrowing (layout fixpoint)
# ---------------------------------------------------------------------------

def instr_size(toks, relaxed, narrow, jmp_narrow, line_no):
    """Return instruction size in bytes.  relaxed = branch lines expanded to
       8 bytes; narrow = LDI lines using the 1-word LDI8 form; jmp_narrow =
       JMP-label lines whose label fits the 1-word constant load."""
    if not toks:
        return 0
    if toks[0].startswith("."):
        if toks[0] in (".word", ".dw", ".WORD", ".DW"):
            cnt = sum(1 for t in toks[1:] if t != ",")
            return cnt * 2
        return 0  # .org and other directives emit nothing
    m = toks[0]
    args = get_args(toks)
    if m == "LDI":
        return 2 if line_no in narrow else 4
    if m == "LDI8":
        return 2
    if m == "JMP" and len(args) >= 1 and args[0] not in REGS:
        return 4 if line_no in jmp_narrow else 6
    if m in BRANCH1 and line_no in relaxed:
        # Inverted condition + LDI/JMP (4 words).  BLT (and BGT, which
        # expands to a swapped BLT) needs an extra BEQ for the equality
        # case -> 5 words.
        return 10 if m in ("BLT", "BGT") else 8
    if m in BRANCH2:
        # SLT/SLTU tmp + branch; a relaxed line escapes via LDI/JMP.
        return 10 if line_no in relaxed else 4
    if m in GRPBF:
        # 3-op form with Rs != Rd needs the copy instruction
        if len(args) >= 3 and args[1] in REGS and args[1] != args[0]:
            return 4
        return 2
    if m in OPCODES or m in ONEWORD or m in FUNCT3 or m in BRANCH1:
        return 2
    return 0

def compute_labels(lines, relaxed, narrow, jmp_narrow):
    labels = {}
    addr = 0
    for i, line in enumerate(lines):
        toks = tokenize(line)
        if not toks:
            continue
        if toks[0].endswith(":"):
            labels[toks[0][:-1]] = addr
            toks = toks[1:]
        addr += instr_size(toks, relaxed, narrow, jmp_narrow, i)
    return labels

def compute_relaxed(lines, relaxed, narrow, jmp_narrow):
    """Monotonically grow `relaxed` until every branch whose target lies
       outside the +/-32-word range is expanded.  Iterates to a fixed point
       because expanding a branch changes addresses of the others."""
    relaxed = set(relaxed)
    while True:
        labels = compute_labels(lines, relaxed, narrow, jmp_narrow)
        changed = False
        addr = 0
        for i, line in enumerate(lines):
            toks = tokenize(line)
            if not toks:
                continue
            if toks[0].endswith(":"):
                toks = toks[1:]
            sz = instr_size(toks, relaxed, narrow, jmp_narrow, i)
            if toks and not toks[0].startswith("."):
                m = toks[0]
                args = get_args(toks)
                ops = expand(m, args, 4)
                # Check EVERY emitted branch at its own address: BRANCH2
                # pseudos emit SLT/SLTU first, so their branch sits 2 bytes
                # later.  (Previously only ops[0] was inspected, which left
                # far BGE/BLE/BLTU/BGEU/BLEU unrelaxed -> their branch
                # offset was silently truncated to 6 bits.)
                a = addr
                for mn, ar in ops:
                    if mn in ("BEQ", "BNE", "BLT") and len(ar) >= 3 and ar[2] in labels:
                        off = (labels[ar[2]] - a - 2) // 2
                        if off < -32 or off > 31:
                            if i not in relaxed:
                                relaxed.add(i)
                                changed = True
                            break
                    a += 2
            addr += sz
        if not changed:
            return relaxed

def compute_narrow(lines, labels):
    """LDI lines whose immediate resolves to 0..255 use the 1-word LDI8
       form; JMP label likewise.  (Numeric literals converge immediately;
       label references are re-checked by the layout fixpoint.)"""
    narrow, jmp_narrow = set(), set()
    for i, line in enumerate(lines):
        toks = tokenize(line)
        if not toks:
            continue
        if toks[0].endswith(":"):
            toks = toks[1:]
        if not toks:
            continue
        m = toks[0]
        args = get_args(toks)
        try:
            if m == "LDI" and len(args) >= 2:
                v = resolve(args[1], labels)
                if 0 <= v <= 255:
                    narrow.add(i)
            elif m == "JMP" and len(args) >= 1 and args[0] not in REGS:
                v = resolve(args[0], labels)
                if 0 <= v <= 255:
                    jmp_narrow.add(i)
        except (ValueError, KeyError):
            pass  # undefined symbol: reported later by second_pass
    return narrow, jmp_narrow

def solve_layout(lines, max_iter=64):
    """Joint fixed point over (relaxation, LDI narrowing, label addresses)."""
    relaxed, narrow, jmp_narrow = set(), set(), set()
    for _ in range(max_iter):
        relaxed = compute_relaxed(lines, relaxed, narrow, jmp_narrow)
        labels = compute_labels(lines, relaxed, narrow, jmp_narrow)
        n2, j2 = compute_narrow(lines, labels)
        if n2 == narrow and j2 == jmp_narrow:
            return labels, relaxed, narrow, jmp_narrow
        narrow, jmp_narrow = n2, j2
    # No convergence (pathological input): fall back to the widest, always
    # correct layout (2-word LDI everywhere, relaxation from a fresh pass).
    narrow, jmp_narrow, relaxed = set(), set(), set()
    relaxed = compute_relaxed(lines, relaxed, narrow, jmp_narrow)
    labels = compute_labels(lines, relaxed, narrow, jmp_narrow)
    return labels, relaxed, narrow, jmp_narrow

# ---------------------------------------------------------------------------
# Emission
# ---------------------------------------------------------------------------

def emit(output, addr, m, args, labels, relaxed, narrow, jmp_narrow, line_no):
    """Encode one instruction at `addr`; returns the next address."""
    if m == "JMP":
        if args[0] in REGS:
            output.append((addr, (0xB << 12) | (REGS[args[0]] << 9)))
            return addr + 2
        # Pseudo-op: JMP label -> LDI8/LDI R4, #label + JMP R4
        v = resolve(args[0], labels)
        if line_no in jmp_narrow:
            output.append((addr, enc_ldi8(4, v)))
            output.append((addr + 2, (0xB << 12) | (4 << 9)))
            return addr + 4
        output.append((addr, ((0xF << 12) | (4 << 9)) & 0xFFFF))
        output.append((addr + 2, v & 0xFFFF))
        output.append((addr + 4, (0xB << 12) | (4 << 9)))
        return addr + 6

    if m in ("BEQ", "BNE", "BLT"):
        op = {"BEQ": 0xC, "BNE": 0xD, "BLT": 0xE}[m]
        rs = REGS[args[0]]
        rt = REGS[args[1]]
        if args[2] in labels:
            off = (labels[args[2]] - addr - 2) // 2
        else:
            off = parse_num(args[2])
        if line_no in relaxed:
            # Inverted condition jumps over LDI R4, #label / JMP R4.
            # BEQ->BNE and BNE->BEQ are exact inverses.  BLT (and BGT,
            # which expands to a swapped BLT) cannot be inverted by
            # swapping operands alone: NOT(rs < rt) = rs >= rt, but the
            # swapped BLT encodes only rs > rt — with EQUAL operands it
            # fell into the LDI/JMP and jumped instead of falling
            # through.  Skip on equality with an extra BEQ (5 words).
            target = resolve(args[2], labels)
            if op == 0xE:
                output.append((addr, enc_branch(0xC, rs, rt, 4)))
                output.append((addr + 2, enc_branch(0xE, rt, rs, 3)))
                output.append((addr + 4, ((0xF << 12) | (4 << 9)) & 0xFFFF))
                output.append((addr + 6, target & 0xFFFF))
                output.append((addr + 8, (0xB << 12) | (4 << 9)))
                return addr + 10
            if op == 0xC:
                opp = enc_branch(0xD, rs, rt, 3)
            else:
                opp = enc_branch(0xC, rs, rt, 3)
            output.append((addr, opp))
            output.append((addr + 2, ((0xF << 12) | (4 << 9)) & 0xFFFF))
            output.append((addr + 4, target & 0xFFFF))
            output.append((addr + 6, (0xB << 12) | (4 << 9)))
            return addr + 8
        if off < -32 or off > 31:
            print(f"Warning: branch at byte {addr} offset {off} exceeds ±32 range",
                  file=sys.stderr)
        output.append((addr, enc_branch(op, rs, rt, off)))
        return addr + 2

    if m in FUNCT3:                      # SLT / SLTU / SRA
        op, f3 = FUNCT3[m]
        output.append((addr, enc_alu(op, REGS[args[0]], REGS[args[1]],
                                     REGS[args[2]], f3)))
        return addr + 2

    if m in GRPBF:                       # ANDI/ORI/SLLI/SRLI/SRAI (in-place)
        output.append((addr, enc_grpb(GRPBF[m], REGS[args[0]],
                                      parse_num(args[1]))))
        return addr + 2

    if m == "CALL":
        output.append((addr, enc_call(REGS[args[0]], REGS[args[1]])))
        return addr + 2

    if m == "HALT":
        output.append((addr, enc_halt()))
        return addr + 2

    if m == "LDI8":
        output.append((addr, enc_ldi8(REGS[args[0]], resolve(args[1], labels))))
        return addr + 2

    if m in ("LDB", "STB"):
        rd = REGS[args[0]]
        rs = REGS[args[1]]
        off = parse_num(args[2]) if len(args) > 2 else 0
        if off < 0 or off > 7:
            print(f"Warning: {m} offset {off} exceeds 0..7", file=sys.stderr)
        output.append((addr, enc_ldstb(1 if m == "LDB" else 2, rd, rs, off)))
        return addr + 2

    if m in ("LD", "ST"):                # word LD/ST Rd, [Rs + off6]
        op = OPCODES[m]
        rd = REGS[args[0]]
        rs = REGS[args[1]]
        off = parse_num(args[2]) if len(args) > 2 else 0
        output.append((addr, (op << 12) | (rd << 9) | (rs << 6) | (off & 0x3F)))
        return addr + 2

    if m in ("ADDI", "XORI"):            # sign-extended imm6
        output.append((addr, enc_imm(OPCODES[m], REGS[args[0]], REGS[args[1]],
                                     parse_num(args[2]))))
        return addr + 2

    if m == "LDI":                       # 1- or 2-word constant load
        rd = REGS[args[0]]
        v = resolve(args[1], labels)
        if line_no in narrow:
            output.append((addr, enc_ldi8(rd, v)))
            return addr + 2
        output.append((addr, (0xF << 12) | (rd << 9)))
        output.append((addr + 2, v & 0xFFFF))
        return addr + 4

    if m in OPCODES:                     # 3-reg ALU: ADD XOR SUB AND OR SLL SRL
        output.append((addr, enc_alu(OPCODES[m], REGS[args[0]],
                                     REGS[args[1]], REGS[args[2]])))
        return addr + 2

    sys.exit(f"asm.py: line {line_no + 1}: unknown instruction '{m}'")

def second_pass(lines, labels, relaxed, narrow, jmp_narrow):
    output = []
    addr = 0
    scratch = 4          # default `.scratch R4`
    for i, line in enumerate(lines):
        toks = tokenize(line)
        if not toks:
            continue
        if toks[0].endswith(":"):
            toks = toks[1:]
        if not toks:
            continue
        if toks[0] in (".org", ".ORG"):
            addr = parse_num(toks[1])
            continue
        if toks[0] in (".word", ".dw", ".WORD", ".DW"):
            for t in toks[1:]:
                if t == ",":
                    continue
                v = labels.get(t, parse_num(t)) if t in labels else parse_num(t)
                output.append((addr, v & 0xFFFF))
                addr += 2
            continue
        if toks[0] == ".scratch":
            if toks[1] in REGS:
                scratch = REGS[toks[1]]
            continue

        m = toks[0]
        args = get_args(toks)
        for mn, ar in expand(m, args, scratch):
            try:
                addr = emit(output, addr, mn, ar, labels, relaxed,
                            narrow, jmp_narrow, i)
            except KeyError as e:
                sys.exit(f"asm.py: line {i + 1}: bad operand {e} "
                         f"in '{line.strip()}'")

    return output

def main():
    ap = argparse.ArgumentParser(description="RISC assembler")
    ap.add_argument("input", help=".asm input file")
    ap.add_argument("-o", "--output", help="output hex file")
    args = ap.parse_args()

    with open(args.input) as f:
        lines = f.readlines()

    labels, relaxed, narrow, jmp_narrow = solve_layout(lines)
    prog = second_pass(lines, labels, relaxed, narrow, jmp_narrow)

    lines_out = []
    for _, word in prog:
        lines_out.append(f"{word:04X}\n")
    data = "".join(lines_out)

    if args.output:
        with open(args.output, "w") as f:
            f.write(data)
    else:
        sys.stdout.write(data)

if __name__ == "__main__":
    main()
