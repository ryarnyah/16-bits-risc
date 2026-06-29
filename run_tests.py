#!/usr/bin/env python3
import subprocess, sys, os, re, argparse

BUILD_DIR = 'emulator/build/obj_dir'
DEFAULT_EMULATOR = os.path.join(BUILD_DIR, 'emulator')
CC = sys.executable + ' cc.py'

TESTS = [
    ('minimal.c',      0x2A,  20000),    #  42
    ('multest.c',      0x81,  20000),    # 129
    ('test.c',         0x81,  20000),    # 129
    ('fib.c',          0x37,  20000),    #  55
    ('dec.c',          0x05,  20000),    #   5
    ('double.c',       0x0A,  20000),    #  10
    ('mulonly.c',      0x81,  20000),    # 129
    ('or_test.c',      0x0FA5, 20000),  # 4005
    ('bne_test.c',     0x0F,  20000),    #  15
    ('xori_test.c',    0xFF00, 20000),   # 65280
    ('all_alu_test.c', 0x03FE, 20000),  # 1022
    ('beq_test.c',     0x2A,  20000),    #  42
    ('ldst_test.c',    0x16,  20000),    #  22
    ('collatz.c',      0x6F,  400000),   # 111
    ('div_test.c',     0x29,  2000),    #  41
    ('mod_test.c',     0x21,  2000),    #  33
    ('sum.c',          0x13BA, 10000),  # 5050
    ('fib5.c',         0x05,  3000),    #   5
    ('fact.c',         0x2D0, 6000),    # 720
    ('gcd.c',          0x06,  5000),    #   6
    ('prime_cnt.c',    0x0A,  100000),  #  10
    ('fib15.c',        0x262, 300000),  # 610
    ('mod_simple.c',   0x01,  10000),  #   1  (7 % 3)
    ('mod_simple2.c',  0x01,  10000),  #   1  (7 % 3 with locals)
]

def compile_c(c_file):
    base = os.path.splitext(c_file)[0]
    hex_file = base + '.hex'
    c_path = os.path.join('examples', c_file)
    hex_path = os.path.join('examples', hex_file)
    r = subprocess.run([sys.executable, 'cc.py', c_path, '--hex', hex_path],
        capture_output=True, text=True)
    if r.returncode != 0:
        print(f'COMPILE FAIL\n{r.stderr}')
        return False
    return True

def run_hex(emulator, hex_file, steps=20000):
    hex_path = os.path.join('examples', hex_file)
    input_str = f's {steps}\nr 1\nq\n'
    r = subprocess.run([emulator, hex_path], input=input_str,
        capture_output=True, text=True, timeout=30)
    for line in r.stdout.split('\n'):
        m = re.search(r'R1 = 0x([0-9A-Fa-f]+)', line)
        if m:
            return int(m.group(1), 16)
    print(f'  stderr: {r.stderr[:200]}')
    return None

def main():
    parser = argparse.ArgumentParser(description='Run C test programs on RISC emulator')
    parser.add_argument('--emulator', '-e',
        default=DEFAULT_EMULATOR,
        help=f'Emulator binary path (default: {DEFAULT_EMULATOR})')
    args = parser.parse_args()

    emulator = os.path.abspath(args.emulator)

    if not os.path.exists(emulator):
        print(f'ERROR: emulator not found at: {emulator}')
        print('Build it first with: make emulator  (old Soc)')
        print('  or: make -C emulator -f Makefile.pipsoc  (new PipSoc)')
        return 1

    failures = 0
    passed = 0
    for entry in TESTS:
        c_file, expected, steps = entry
        base = os.path.splitext(c_file)[0]
        hex_file = base + '.hex'
        print(f'  {c_file:20s} ...', end=' ')
        sys.stdout.flush()
        if not compile_c(c_file):
            print('COMPILE FAIL')
            failures += 1
            continue
        result = run_hex(emulator, hex_file, steps)
        if result is None:
            print('RUN FAIL')
            failures += 1
            continue
        if result == expected:
            print(f'OK (0x{result:X})')
            passed += 1
        else:
            print(f'FAIL: got 0x{result:X}, expected 0x{expected:X}')
            failures += 1
    print()
    if failures:
        print(f'{failures} test(s) FAILED, {passed} passed')
        return 1
    else:
        print(f'All {passed} tests PASSED')
        return 0

if __name__ == '__main__':
    sys.exit(main())
