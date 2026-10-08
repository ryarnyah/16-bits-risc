#!/usr/bin/env python3
"""Static ISA coverage of the tests run by `make test-programs-all`.

Scans the .asm sources of every test in run_tests.py (TESTS + ASM_TESTS),
expands pseudo-ops through asm.expand(), and reports which hardware
instructions / pseudo-ops are exercised by at least one test.

Run from anywhere:  python3 isa_coverage.py

Note: this is a source-level audit — an instruction counted as covered
appears in a test that runs to completion and is output-checked, but the
tool does not trace retirement.
"""
import sys, os, re

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
os.chdir(HERE)

import run_tests, asm

# Full hardware instruction set (ISA v3.2: BR replaces BEQ/BNE/BLT)
HW = (set(asm.OPCODES) | set(asm.FUNCT3) | set(asm.GRPBF) |
      {"LDI8", "CALL", "HALT", "LDB", "STB"})
HW_ORDER = ["ADD", "ADDI", "XOR", "XORI", "SUB", "SLT", "SLTU", "AND", "OR",
            "SLL", "SRL", "SRA", "LD", "ST", "JMP", "CALL", "CALLR", "JMPR",
            "HALT",
            "ANDI", "ORI", "SLLI", "SRLI", "SRAI",
            "BR", "LDI", "LDI8", "LDB", "STB"]

PSEUDO = {"MOV", "NEG", "NOT", "CLR", "LSL", "LSR", "ASR",
          "B", "BGT", "BGE", "BLE", "BLTU", "BGEU", "BLEU", "RET"}
PSEUDO_ORDER = ["MOV", "NEG", "NOT", "CLR", "LSL", "LSR", "ASR",
                "B", "BGT", "BGE", "BLE", "BLTU", "BGEU", "BLEU", "RET"]

assert set(HW_ORDER) == HW, set(HW_ORDER) ^ HW
assert set(PSEUDO_ORDER) == PSEUDO


def parse_lines(path):
    """Yield (mnemonic, args) for each instruction line, pseudo or not."""
    for raw in open(path):
        line = raw.split(';')[0].strip()
        if not line:
            continue
        # strip leading label(s) ("fn:  LDI R1, #21")
        while True:
            m = re.match(r'^[A-Za-z_][A-Za-z0-9_]*:\s*(.*)$', line)
            if not m:
                break
            line = m.group(1).strip()
        if not line or line.startswith('.'):   # .word/.org/.scratch
            continue
        toks = line.split(None, 1)
        mn = toks[0].upper()
        rest = toks[1] if len(toks) > 1 else ''
        args = [a.strip() for a in rest.split(',')] if rest.strip() else []
        yield mn, args


def expand_all(mn, args, scratch=4, depth=0):
    """Expand a mnemonic to hardware instructions (recursive)."""
    assert depth < 5, 'expansion too deep'
    out = []
    for m, a in asm.expand(mn, args, scratch):
        if m in PSEUDO:
            out += expand_all(m, a, scratch, depth + 1)
        else:
            assert m in HW, f'unknown mnemonic {m}'
            out.append(m)
    return out


def main():
    tests = run_tests.TESTS + run_tests.ASM_TESTS
    hw_cov = {m: [] for m in HW_ORDER}
    ps_cov = {p: [] for p in PSEUDO_ORDER}
    unknown = []

    for name, _exp, _steps in tests:
        path = os.path.join('examples', name.replace('.c', '.asm'))
        seen_hw, seen_ps = set(), set()
        for mn, args in parse_lines(path):
            if mn not in HW and mn not in PSEUDO:
                unknown.append((path, mn))
                continue
            if mn in PSEUDO:
                seen_ps.add(mn)
            for h in expand_all(mn, args):
                seen_hw.add(h)
        for h in seen_hw:
            hw_cov[h].append(name)
        for p in seen_ps:
            ps_cov[p].append(name)

    print(f'== Hardware instructions ({len(HW_ORDER)}) ==')
    missing = []
    for m in HW_ORDER:
        ts = hw_cov[m]
        if ts:
            print(f'  OK   {m:5s} {len(ts):2d} tests: '
                  + ', '.join(t.split('.')[0] for t in ts[:4])
                  + (' ...' if len(ts) > 4 else ''))
        else:
            missing.append(m)
            print(f'  MISS {m:5s} -- not present in any executed test')

    print(f'\n== Pseudo-ops ({len(PSEUDO_ORDER)}) ==')
    pmissing = []
    for p in PSEUDO_ORDER:
        ts = ps_cov[p]
        if ts:
            print(f'  OK   {p:5s} {len(ts):2d} tests: '
                  + ', '.join(t.split('.')[0] for t in ts[:4])
                  + (' ...' if len(ts) > 4 else ''))
        else:
            pmissing.append(p)
            print(f'  MISS {p:5s} -- not used by any executed test')

    if unknown:
        print('\n== Unknown mnemonics ==')
        for p, mn in sorted(set(unknown)):
            print(f'  {p}: {mn}')

    print(f'\nSUMMARY: {len(tests)} tests; '
          f'hardware missing={missing or "none"}, '
          f'pseudo missing={pmissing or "none"}')
    return 1 if (missing or pmissing or unknown) else 0


if __name__ == '__main__':
    sys.exit(main())
