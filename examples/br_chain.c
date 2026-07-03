int main() {
    int x;
    x = 0;
    if (x == 0)
        x = 1;
    if (x != 2)
        x = 3;
    if (x < 5)
        x = x + 7;
    return x;  // 10
}
