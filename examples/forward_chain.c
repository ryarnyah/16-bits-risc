int main() {
    int a, b, c, d, e, f;
    a = 3;
    b = a + 5;    // forward: a from WB, result b
    c = b ^ a;    // forward: b from EX, a from WB, result c
    d = c & 7;    // forward: c from EX, result d
    e = d | 1;    // forward: d from EX, result e
    f = e + c;    // forward: e from EX, c from WB, result f
    return f - a; // forward: f from EX, a from WB? or f from WB, a long past
}
