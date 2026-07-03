int main() {
    int arr[15];
    int i;
    int sum;
    sum = 0;
    for (i = 0; i < 15; i++) {
        arr[i] = i;
    }
    for (i = 0; i < 15; i++) {
        sum = sum + arr[i];
    }
    return sum;
}
