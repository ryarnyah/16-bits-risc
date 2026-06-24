int main() {
    int a = 0x00FF;
    return ~a;  // XORI R1, R1, #-1 => 0xFF00 = 65280 (unsigned) / -256 (signed)
}
