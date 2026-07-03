/* Pointer chasing: LD through pointer */
int main() {
    int a;
    int *p;
    a = 42;
    p = &a;
    return *p;  // LD from pointer: 42
}
