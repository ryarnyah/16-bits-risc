// Tests all ALU ops: ADD, XOR, SUB, AND, OR, SLL, SRL
int main() {
    int a = 0x0F0F;
    int b = 0x00FF;
    int r;
    r = a + b;     // 0x100E (ADD)
    r = r ^ b;     // 0x10F1 (XOR)
    r = r - a;     // 0x01E2 (SUB)
    r = r & a;     // 0x0102 (AND)
    r = r | b;     // 0x01FF (OR)
    r = r << 2;    // 0x07FC (SLL)
    r = r >> 1;    // 0x03FE (SRL)
    return r;      // 0x03FE = 1022
}
