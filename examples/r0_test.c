/* R0 read edge case (R0 always 0) */
int main() {
    int x;
    x = 0;      // R0-based value
    x = x + 0;  // ADDI with R0
    x = x ^ 0;  // XOR with R0
    return x;   // 0
}
