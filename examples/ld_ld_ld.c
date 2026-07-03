int main() {
    int arr[4];
    int a, b, c, r;
    arr[0] = 7;
    arr[1] = 11;
    arr[2] = 13;
    arr[3] = 42;
    a = arr[0];   // LD
    b = arr[1];   // LD
    c = arr[2];   // LD
    r = a + b + c;
    return r;     // 31
}
