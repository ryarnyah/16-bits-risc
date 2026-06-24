#!/usr/bin/env python3
"""16-bit RISC assembler: asm -> hex word list."""

import sys, re, argparse

OPCODES = {
    "ADD":  0, "ADDI": 1, "XOR": 2, "XORI": 3,
    "SUB":  4, "AND":  5, "OR":   6, "SLL":  7,
    "SRL":  8, "LD":   9, "ST":   0xA, "JMP": 0xB,
    "BEQ": 0xC, "BNE": 0xD, "BLT": 0xE, "LDI": 0xF,
}

REGS = {f"R{i}": i for i in range(8)}

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

OPPOSITE_COND = {"BEQ": "BNE", "BNE": "BEQ", "BLT": None}

def instr_size(toks, relaxed, line_no):
    """Return instruction size in bytes.  relaxed is a set of line numbers
       whose branch instruction should be expanded (relaxed -> 8 bytes)."""
    if not toks:
        return 0
    if toks[0] in (".org", ".ORG"):
        return 0
    if toks[0] in (".word", ".dw", ".WORD", ".DW"):
        cnt = sum(1 for t in toks[1:] if t != ",")
        return cnt * 2
    if toks[0] not in OPCODES:
        return 0
    if toks[0] == "LDI":
        return 4
    if toks[0] == "JMP" and len(toks) > 1 and toks[1] not in REGS:
        return 6  # LDI R4,#label + JMP R4 (3 words)
    if toks[0] in ("BEQ", "BNE", "BLT") and line_no in relaxed:
        return 8  # 1 word inverted cond + 3 words JMP
    return 2

def compute_labels(lines, relaxed):
    labels = {}
    addr = 0
    for i, line in enumerate(lines):
        toks = tokenize(line)
        if not toks:
            continue
        if toks[0].endswith(":"):
            label = toks[0][:-1]
            labels[label] = addr
            toks = toks[1:]
        addr += instr_size(toks, relaxed, i)
    return labels

def compute_relaxed(lines):
    """Iteratively find which branch lines need relaxation (offset > ±32)."""
    relaxed = set()
    while True:
        labels = compute_labels(lines, relaxed)
        changed = False
        addr = 0
        for i, line in enumerate(lines):
            toks = tokenize(line)
            if not toks:
                continue
            if toks[0].endswith(":"):
                toks = toks[1:]
            sz = instr_size(toks, relaxed, i)
            if toks and toks[0] in ("BEQ", "BNE", "BLT"):
                args = [strip_hash(t) for t in toks[1:] if t not in (",", "[", "]", "+", "#") and t != "+"]
                if len(args) >= 3 and args[2] in labels:
                    target = labels[args[2]]
                    offset = (target - addr - 2) // 2
                    if offset < -32 or offset > 31:
                        if i not in relaxed:
                            relaxed.add(i)
                            changed = True
            addr += sz
        if not changed:
            break
    return relaxed

def second_pass(lines, labels, relaxed):
    output = []
    addr = 0
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

        mnemonic = toks[0]
        op = OPCODES[mnemonic]
        args = [strip_hash(t) for t in toks[1:] if t not in (",", "[", "]", "+", "#") and t != "+"]
        instr = 0

        if op == 0xB:   # JMP Rs  (also accepts label via LDI R4,#label + JMP R4)
            if args[0] in REGS:
                rs = REGS[args[0]]
                instr = (op << 12) | (rs << 9)
            else:
                # Pseudo-op: JMP label → LDI R4, #label + JMP R4
                target = labels.get(args[0])
                if target is None:
                    target = parse_num(args[0])
                output.append((addr, ((0xF << 12) | (4 << 9)) & 0xFFFF))
                output.append((addr + 2, target & 0xFFFF))
                instr = (0xB << 12) | (4 << 9)
                addr += 4

        elif op in (0xC, 0xD, 0xE):   # BEQ BNE BLT Rs, Rt, offset
            rs = REGS[args[0]]
            rt = REGS[args[1]]
            if args[2] in labels:
                target = labels[args[2]]
                offset = (target - addr - 2) // 2
                if i in relaxed:
                    if op == 0xC or op == 0xD:
                        opp_op = 0xD if op == 0xC else 0xC
                        opp_instr = (opp_op << 12) | (rs << 9) | (rt << 6) | 3
                        output.append((addr, opp_instr))
                        output.append((addr + 2, ((0xF << 12) | (4 << 9)) & 0xFFFF))
                        output.append((addr + 4, target & 0xFFFF))
                        output.append((addr + 6, (0xB << 12) | (4 << 9)))
                        addr += 8
                        continue
                    else:
                        print(f"Error: BLT at byte {addr} offset {offset} cannot be relaxed", file=sys.stderr)
                        sys.exit(1)
            else:
                offset = parse_num(args[2])
            if offset < -32 or offset > 31:
                print(f"Warning: branch at byte {addr} offset {offset} exceeds ±32 range", file=sys.stderr)
            offset &= 0x3F
            instr = (op << 12) | (rs << 9) | (rt << 6) | offset

        elif op in (9, 0xA):   # LD/ST Rd, [Rs + offset]
            rd = REGS[args[0]]
            rs = REGS[args[1]]
            off = parse_num(args[2]) if len(args) > 2 else 0
            off &= 0x3F
            instr = (op << 12) | (rd << 9) | (rs << 6) | off

        elif op in (1, 3):   # ADDI/XORI Rd, Rs, #imm6
            rd = REGS[args[0]]
            rs = REGS[args[1]]
            imm = parse_num(args[2]) & 0x3F
            instr = (op << 12) | (rd << 9) | (rs << 6) | imm

        elif op == 0xF:   # LDI Rd, #imm16  -> 2 words
            rd = REGS[args[0]]
            instr = (op << 12) | (rd << 9)
            if args[1] in labels:
                imm = labels[args[1]]
            else:
                imm = parse_num(args[1])
            output.append((addr, instr))
            output.append((addr + 2, imm & 0xFFFF))
            addr += 4
            continue

        else:   # 3-reg ALU: ADD XOR SUB AND OR SLL SRL
            rd = REGS[args[0]]
            rs = REGS[args[1]]
            rt = REGS[args[2]]
            instr = (op << 12) | (rd << 9) | (rs << 6) | (rt << 3)

        output.append((addr, instr))
        addr += 2

    return output

def main():
    ap = argparse.ArgumentParser(description="RISC assembler")
    ap.add_argument("input", help=".asm input file")
    ap.add_argument("-o", "--output", help="output hex file")
    args = ap.parse_args()

    with open(args.input) as f:
        lines = f.readlines()

    relaxed = compute_relaxed(lines)
    labels = compute_labels(lines, relaxed)
    prog = second_pass(lines, labels, relaxed)

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
