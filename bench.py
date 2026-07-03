#!/usr/bin/env python3
import subprocess, sys, os, re, argparse, time, math

CC = sys.executable + ' cc.py'
BUILD_DIR = 'emulator/build/obj_dir'
EMULATOR = os.path.join(BUILD_DIR, 'emulator')
EMULATOR_PIPSOC = os.path.join(BUILD_DIR, 'pipsoc-emu')

# Benchmark name → (expected R1 value, description)
BENCHMARKS = [
    ('bench_nop.c',   0x3E8,   'ADDI loop (1000 iters)'),
    ('bench_alu.c',   0x1428,  'ALU ops (500 iters)'),
    ('bench_mem.c',   0x3A02,  'LD/ST array (100 elems)'),
]

START_CYCLES = 20  # emulator reset ticks

def compile_c(c_file):
    base = os.path.splitext(c_file)[0]
    hex_file = base + '.hex'
    c_path = os.path.join('examples', c_file)
    hex_path = os.path.join('examples', hex_file)
    r = subprocess.run([sys.executable, 'cc.py', c_path, '--hex', hex_path],
        capture_output=True, text=True)
    if r.returncode != 0:
        print(f'  COMPILE FAIL: {r.stderr.strip()}')
        return False
    return True

def run_steps(emulator_cmd, hex_file, steps):
    hex_path = os.path.join('examples', hex_file)
    input_str = f's {steps}\nr 1\nc\nq\n'
    try:
        r = subprocess.run(emulator_cmd, input=input_str,
            capture_output=True, text=True, timeout=120)
    except subprocess.TimeoutExpired:
        return None, None
    r1 = None
    cycles = None
    for line in r.stdout.split('\n'):
        m = re.search(r'R1 = 0x([0-9A-Fa-f]+)', line)
        if m:
            r1 = int(m.group(1), 16)
        m = re.search(r'cycles=(\d+)', line)
        if m:
            cycles = int(m.group(1))
    return r1, cycles

def measure_cycles(emulator_cmd, hex_file, expected, max_steps=200000):
    lo, hi = 0, max_steps
    # First verify benchmark finishes within max_steps
    r1, _ = run_steps(emulator_cmd, hex_file, max_steps)
    if r1 != expected:
        raise RuntimeError(f'Benchmark failed at {max_steps} steps: got 0x{r1:X} expected 0x{expected:X}')
    # Binary search for minimum steps that yield correct R1
    while lo < hi:
        mid = (lo + hi) // 2
        r1, _ = run_steps(emulator_cmd, hex_file, mid)
        if r1 == expected:
            hi = mid
        else:
            lo = mid + 1
    # lo is the minimum steps where R1 is correct → benchmark cycles
    return lo - 1  # R1 was set in the previous cycle

def main():
    parser = argparse.ArgumentParser(description='RISC Core Benchmark Suite')
    parser.add_argument('--max-steps', type=int, default=200000,
        help='Maximum step count for binary search (default: 200000)')
    parser.add_argument('--list', action='store_true',
        help='List available benchmarks without running')
    args = parser.parse_args()

    if args.list:
        print('Available benchmarks:')
        for name, exp, desc in BENCHMARKS:
            print(f'  {name:20s} → 0x{exp:04X}  ({desc})')
        return

    fail = False
    for name, expected, desc in BENCHMARKS:
        base = os.path.splitext(name)[0]
        hex_file = base + '.hex'
        print(f'\n  {name} ({desc})')
        if not compile_c(name):
            print('  COMPILE FAIL')
            fail = True
            continue

        for emu_label, emu_cmd in [('Multi-cycle', [EMULATOR]),
                                    ('PipSoc     ', [EMULATOR_PIPSOC])]:
            if not os.path.exists(emu_cmd[0]):
                print(f'  [{emu_label}] emulator not found, skipping')
                continue
            sys.stdout.write(f'  [{emu_label}] measuring ... ')
            sys.stdout.flush()
            try:
                steps = measure_cycles(emu_cmd, hex_file, expected, args.max_steps)
                print(f'{steps} cycles')
            except RuntimeError as e:
                print(f'{e}')
                fail = True

    if fail:
        print('\nSome benchmarks failed.')
        sys.exit(1)

if __name__ == '__main__':
    main()
