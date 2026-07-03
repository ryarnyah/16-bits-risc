/* Forwarding chain through shift operations */
int main() {
    int a, b, c;
    a = 3;
    b = a << 1;   // SLL: forward a from WB, b=6
    c = b >> 1;   // SRL: forward b from EX, c=3
    return c << 2; // SLL: forward c from EX, 12
}
