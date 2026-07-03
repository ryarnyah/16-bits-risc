/* Nested calls with register pressure */
int add(int a, int b) {
    return a + b;
}
int triple(int x) {
    return add(x, add(x, x));
}
int main() {
    return triple(7);  // 7 + (7 + 7) = 21
}
