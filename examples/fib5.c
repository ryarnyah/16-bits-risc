int fib(int a) {
    if (a < 2) return a;
    return fib(a-1) + fib(a-2);
}

int main() {
    int result;
    result = fib(5);
    return result;
}
