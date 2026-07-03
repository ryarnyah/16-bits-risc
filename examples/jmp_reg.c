/* Call/return via register (JMP R5 pattern) */
int callee(int x) {
    return x + 1;
}
int main() {
    int r;
    r = callee(41);
    return r;  // 42
}
