/* UART Fibonacci: read N from UART, compute fib(N), print result */
int fib(int n) {
    if (n < 2)
        return n;
    return fib(n - 1) + fib(n - 2);
}

void putchar(char c) {
    unsigned short *tx = (unsigned short *)0x1FFE;
    *tx = c;
}

char getchar(void) {
    unsigned short *rx = (unsigned short *)0x1FFC;
    return *rx;
}

void print_str(char *s) {
    while (*s) {
        putchar(*s);
        s++;
    }
}

void print_dec(int n) {
    if (n < 10) {
        putchar('0' + n);
        return;
    }
    print_dec(n / 10);
    putchar('0' + (n % 10));
}

int read_dec(void) {
    int n = 0;
    char c;
    while (1) {
        c = getchar();
        if (c < '0') {
            if (c == 10 || c == 13) {
                putchar(10);
                return n;
            }
        } else if (c > '9') {
        } else {
            putchar(c);
            n = n * 10 + (c - '0');
        }
    }
}

int main() {
    int n;
    int result;

    while (1) {
        putchar('E');
        putchar('n');
        putchar('t');
        putchar('e');
        putchar('r');
        putchar(' ');
        putchar('N');
        putchar(':');
        putchar(' ');

        n = read_dec();
        result = fib(n);

        putchar('f');
        putchar('i');
        putchar('b');
        putchar('(');
        print_dec(n);
        putchar(')');
        putchar(' ');
        putchar('=');
        putchar(' ');
        print_dec(result);
        putchar(10);
    }

    return 0;
}