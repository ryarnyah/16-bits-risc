/* Interleaved LD and ST operations */
int main() {
    int a, b, c;
    a = 10;
    b = 20;
    c = a + b;  // LD a, LD b, ADD
    a = 30;
    b = 40;
    c = c + a + b;  // LD a, LD b, ADD c
    return c;   // 100
}
