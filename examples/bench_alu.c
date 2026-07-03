int main() {
    int i;
    int a, b, c;
    a = 1;
    b = 2;
    c = 0;
    for (i = 0; i < 500; i++) {
        a = a + b;
        b = b ^ a;
        c = c + a - b;
        a = a & 0x7F;
        b = b | 0xF0;
        c = c * 3;
    }
    return c;
}
