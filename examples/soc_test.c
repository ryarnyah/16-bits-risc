// SoC test suite — exercises all hardware features:
//   ALU (ADD/ADDI/XOR/XORI/SUB/AND/OR/SLL/SRL), branches (BEQ/BNE/BLT),
//   memory (LD/ST), stack, JMP call/return, runtime lib (mul/div/mod),
//   UART TX, UART RX (echo test)
//
// All output via UART TX. Pipe input for UART RX echo test.
// Run: echo -e "AB" | make test-soc

void putchar(char c) {
    int* tx;
    tx = (int*)0x1FFE;
    *tx = c;
}

char getchar(void) {
    int* rx;
    rx = (int*)0x1FFC;
    return *rx;
}

void print(const char* s) {
    while (*s) {
        putchar(*s);
        s = s + 1;
    }
}

void print_dec(int n) {
    if (n < 0) {
        putchar(45);
        n = 0 - n;
    }
    if (n >= 10) {
        print_dec(n / 10);
    }
    putchar(48 + (n % 10));
}

void print_hex(int n) {
    int d;
    int i;
    i = 0;
    while (i < 4) {
        d = (n >> 12);
        if (d > 15) d = 15;
        if (d < 10) putchar(48 + d);
        else putchar(65 + d - 10);
        n = n << 4;
        i = i + 1;
    }
}

void nl(void) {
    putchar(10);
}

// Use global or volatile-style vars to prevent constant folding
int result;

void pass(const char* name) {
    print("  [PASS] ");
    print(name);
    putchar(10);
}

void fail(const char* name) {
    print("  [FAIL] ");
    print(name);
    putchar(10);
}

// ========== Test 1: ALU operations ==========
int test_alu(int* r) {
    int a;
    int b;
    int t;
    int ok;
    ok = 1;

    // ADD
    a = 10; b = 20; t = a + b;
    if (t != 30) ok = 0;

    // ADDI (via constant right)
    a = 100; t = a + 50;
    if (t != 150) ok = 0;

    // SUB
    a = 50; b = 30; t = a - b;
    if (t != 20) ok = 0;

    // AND
    a = 255; b = 15; t = a & b;
    if (t != 15) ok = 0;

    // OR
    a = 240; b = 15; t = a | b;
    if (t != 255) ok = 0;

    // XOR
    a = 255; b = 255; t = a ^ b;
    if (t != 0) ok = 0;
    a = 255; b = 0; t = a ^ b;
    if (t != 255) ok = 0;

    // SLL
    a = 1; b = 3; t = a << b;
    if (t != 8) ok = 0;

    // SRL
    a = 16; b = 2; t = a >> b;
    if (t != 4) ok = 0;

    // Test XORI via XOR with constant
    a = 255; t = a ^ 65280;
    if (t != 65535) ok = 0;

    *r = ok;
    return ok;
}

// ========== Test 2: Branches ==========
int test_branches(int* r) {
    int a;
    int b;
    a = 42; b = 42;
    if (a != b) { *r = 0; return 0; }  // BEQ should match

    a = 10; b = 20;
    if (a == b) { *r = 0; return 0; }  // BNE should not match

    a = 5; b = 10;
    if (a >= b) { *r = 0; return 0; }  // BLT: a < b is true
    if (b < a) { *r = 0; return 0; }   // BLT: b < a is false

    a = 10; b = 5;
    if (a <= b) { *r = 0; return 0; }  // BLT: a > b (via GT/LE inversion)

    *r = 1;
    return 1;
}

// ========== Test 3: Memory (LD/ST) ==========
int test_memory(int* r) {
    int arr[8];
    int i;
    arr[0] = 100; arr[1] = 200; arr[2] = 300; arr[3] = 400;
    arr[4] = 500; arr[5] = 600; arr[6] = 700; arr[7] = 800;

    i = 0;
    if (arr[0] != 100) { *r = 0; return 0; }
    if (arr[3] != 400) { *r = 0; return 0; }
    if (arr[7] != 800) { *r = 0; return 0; }

    // Write and read back
    arr[2] = 999;
    if (arr[2] != 999) { *r = 0; return 0; }

    // LD immediate
    i = 5;
    if (arr[i] != 600) { *r = 0; return 0; }

    *r = 1;
    return 1;
}

// ========== Test 4: Stack / function calls ==========
int deep(int n) {
    if (n <= 0) return 42;
    return deep(n - 1) + 1;
}

int test_stack(int* r) {
    int t;
    t = deep(5);
    if (t != 47) { *r = 0; return 0; }  // 42 + 5 = 47
    *r = 1;
    return 1;
}

// ========== Test 5: Runtime library ==========
int test_runlib(int* r) {
    int a;
    int b;
    int t;

    // Multiplication: explicit call or via *
    a = 7; b = 8; t = a * b;
    if (t != 56) { *r = 0; return 0; }
    a = 123; b = 45; t = a * b;
    if (t != 5535) { *r = 0; return 0; }

    // Division
    a = 100; b = 7; t = a / b;
    if (t != 14) { *r = 0; return 0; }

    // Modulo
    a = 100; b = 7; t = a % b;
    if (t != 2) { *r = 0; return 0; }

    *r = 1;
    return 1;
}

// ========== Test 6: Array access ==========
int test_arrays(int* r) {
    int arr[6];
    int i;
    int t;

    arr[0] = 10;
    arr[1] = 20;
    arr[2] = 30;
    arr[3] = 40;
    arr[4] = 50;
    arr[5] = 60;

    t = 0;
    i = 0;
    while (i < 6) {
        t = t + arr[i];
        i = i + 1;
    }
    if (t != 210) { *r = 0; return 0; }

    // Write via index var
    i = 1;
    arr[i] = 25;
    if (arr[1] != 25) { *r = 0; return 0; }

    *r = 1;
    return 1;
}

// ========== Test 7: Pointer dereference ==========
int test_pointers(int* r) {
    int val;
    int* ptr;
    val = 77;
    ptr = &val;
    if (*ptr != 77) { *r = 0; return 0; }
    *ptr = 88;
    if (val != 88) { *r = 0; return 0; }
    *r = 1;
    return 1;
}

// ========== Test 8: LDI large constant ==========
int test_ldi(int* r) {
    int a;
    a = 65535;
    if (a != 65535) { *r = 0; return 0; }
    a = 32767;
    if (a != 32767) { *r = 0; return 0; }
    a = 1;
    if (a != 1) { *r = 0; return 0; }
    *r = 1;
    return 1;
}

// ========== Test 9: UART TX ==========
int test_uart_tx(int* r) {
    // If we got this far, UART TX works
    *r = 1;
    return 1;
}

// ========== Test 10: UART RX echo ==========
int test_uart_rx(int* r) {
    char c1;
    char c2;
    int ok;

    print("  Type two chars for echo test: ");
    c1 = getchar();
    putchar(c1);
    c2 = getchar();
    putchar(c2);
    putchar(10);

    ok = 1;
    if (c1 < 32) ok = 0;
    if (c2 < 32) ok = 0;
    *r = ok;
    return ok;
}

// ========== Test suite runner ==========

int main() {
    int passed;
    int failed;
    int r;

    passed = 0;
    failed = 0;

    nl();
    print("========================================\n");
    print("  16-bit RISC SoC Test Suite\n");
    print("========================================\n");
    nl();

    // Test 1: ALU
    print("--- ALU Operations ---\n");
    if (test_alu(&r)) { pass("ALU"); passed = passed + 1; }
    else { fail("ALU"); failed = failed + 1; }

    // Test 2: Branches
    print("--- Branch Instructions ---\n");
    if (test_branches(&r)) { pass("Branches"); passed = passed + 1; }
    else { fail("Branches"); failed = failed + 1; }

    // Test 3: Memory
    print("--- Data Memory (LD/ST) ---\n");
    if (test_memory(&r)) { pass("Memory"); passed = passed + 1; }
    else { fail("Memory"); failed = failed + 1; }

    // Test 4: Stack
    print("--- Stack & Function Calls ---\n");
    if (test_stack(&r)) { pass("Stack"); passed = passed + 1; }
    else { fail("Stack"); failed = failed + 1; }

    // Test 5: Runtime library
    print("--- Runtime Library (mul/div/mod) ---\n");
    if (test_runlib(&r)) { pass("RuntimeLib"); passed = passed + 1; }
    else { fail("RuntimeLib"); failed = failed + 1; }

    // Test 6: Arrays
    print("--- Array Access ---\n");
    if (test_arrays(&r)) { pass("Arrays"); passed = passed + 1; }
    else { fail("Arrays"); failed = failed + 1; }

    // Test 7: Pointers
    print("--- Pointer Dereference ---\n");
    if (test_pointers(&r)) { pass("Pointers"); passed = passed + 1; }
    else { fail("Pointers"); failed = failed + 1; }

    // Test 8: LDI
    print("--- Large Constants (LDI) ---\n");
    if (test_ldi(&r)) { pass("LDI"); passed = passed + 1; }
    else { fail("LDI"); failed = failed + 1; }

    // Test 9: UART TX
    print("--- UART Transmit ---\n");
    if (test_uart_tx(&r)) { pass("UART TX"); passed = passed + 1; }
    else { fail("UART TX"); failed = failed + 1; }

    // Test 10: UART RX echo
    print("--- UART Receive / Echo ---\n");
    if (test_uart_rx(&r)) { pass("UART RX"); passed = passed + 1; }
    else { fail("UART RX"); failed = failed + 1; }

    // Summary
    nl();
    print("========================================\n");
    print("  Results: ");
    print_dec(passed);
    print("/");
    print_dec(passed + failed);
    print(" passed\n");
    print("========================================\n");
    nl();

    while (1) {}
}
