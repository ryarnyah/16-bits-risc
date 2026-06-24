int is_prime(int n) {
    int i = 2;
    while (i < n) {
        if (n % i == 0) return 0;
        i = i + 1;
    }
    return 1;
}
int main() {
    int cnt = 0;
    int n = 2;
    while (n < 30) {
        cnt = cnt + is_prime(n);
        n = n + 1;
    }
    return cnt;  // 10 primes under 30: 2,3,5,7,11,13,17,19,23,29
}
