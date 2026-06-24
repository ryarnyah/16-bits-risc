int dec(int x) {
    if (x <= 0) return 0;
    return dec(x-1) + 1;
}

int main() {
    return dec(5);
}
