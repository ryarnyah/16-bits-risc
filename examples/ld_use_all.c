int main() {
    int arr[4];
    int x, y;
    arr[0] = 10;
    arr[1] = 3;
    arr[2] = 4;
    arr[3] = 5;
    x = arr[0];    // LD -> ADD (load-use)
    y = x + 1;
    x = arr[1];    // LD -> SUB (load-use)
    y = y + (x - 1);
    x = arr[2];    // LD -> AND (load-use)
    y = y + (x & 6);
    x = arr[3];    // LD -> OR (load-use)
    y = y + (x | 1);
    return y;
}
