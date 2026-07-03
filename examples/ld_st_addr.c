int global;
int main() {
    int x;
    int *p;
    global = 0;
    x = 42;
    p = &global;   // address from LDI
    *p = x;         // ST using LDI address
    x = global;     // LD to verify
    return x;       // 42
}
