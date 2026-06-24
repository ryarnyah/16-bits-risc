/* Fibonacci: compute F(10) = 55 */
int fib(int n) {
    if (n < 2)
        return n;
    return fib(n - 1) + fib(n - 2);
}

int main() {
    int n;
    int r;
    n = 10;
    r = fib(n);
    return r;
}
