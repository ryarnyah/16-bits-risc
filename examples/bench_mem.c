int main() {
    int arr[100];
    int i;
    int sum;
    sum = 0;
    for (i = 0; i < 100; i++) {
        arr[i] = i * 3;
    }
    for (i = 0; i < 100; i++) {
        sum = sum + arr[i];
    }
    return sum;
}
