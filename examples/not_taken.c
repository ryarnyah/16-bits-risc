/* Branch not-taken paths */
int main() {
    int x;
    x = 0;
    if (x != 0)     // BNE not-taken
        x = 99;
    if (x == 0)     // BEQ taken
        x = 5;
    if (x < 0)      // BLT not-taken
        x = 99;
    if (x < 10)     // BLT taken
        x = x + 7;
    return x;       // 12
}
