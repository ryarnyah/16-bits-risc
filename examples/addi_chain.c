/* ADDI forwarding chain */
int main() {
    int a, b, c;
    a = 5;        // ADDI R1, R0, #5
    b = a + 7;    // ADDI: forward a from WB, b=12
    c = b + 3;    // ADDI: forward b from EX, c=15
    return c + 5; // ADDI: forward c from EX, 20
}
