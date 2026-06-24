int result;

int mul(int a, int b) {
    return a * b;
}

int main() {
    int x;
    x = 42;
    result = x + 1;
    result = mul(result, 3);
    return result;
}
