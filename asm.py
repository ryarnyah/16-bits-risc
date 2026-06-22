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
    return re.findall(r'[A-Za-z_]\w*:|[A-Za-z_]\w*|#?[+\-]?\w+|[\[\],:+()]', line)

def first_pass(lines):
    labels = {}
    addr = 0
    for line in lines:
        toks = tokenize(line)
        if not toks:
            continue
        if toks[0].endswith(":"):
            label = toks[0][:-1]
            labels[label] = addr
            toks = toks[1:]
        if toks and toks[0] in (".org", ".ORG"):
            addr = parse_num(toks[1])
            continue
        if toks and toks[0] in (".word", ".dw", ".WORD", ".DW"):
            for t in toks[1:]:
                if t == ",":
                    continue
                addr += 2
            continue
        if toks and toks[0] in OPCODES:
            if toks[0] == "LDI":
                addr += 4
            else:
                addr += 2
    return labels

def second_pass(lines, labels):
    output = []
    addr = 0
    for line in lines:
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

        if op == 0xB:   # JMP Rs
            rs = REGS[args[0]]
            instr = (op << 12) | (rs << 9)

        elif op in (0xC, 0xD, 0xE):   # BEQ BNE BLT Rs, Rt, offset
            rs = REGS[args[0]]
            rt = REGS[args[1]]
            if args[2] in labels:
                target = labels[args[2]]
                offset = (target - addr - 2) // 2
            else:
                offset = parse_num(args[2])
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

    labels = first_pass(lines)
    prog = second_pass(lines, labels)

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
