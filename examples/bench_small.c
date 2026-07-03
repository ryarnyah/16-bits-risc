int main() {
    int arr[3];
    int i;
    int sum;
    sum = 0;
    for (i = 0; i < 3; i++) {
        arr[i] = i;
    }
    for (i = 0; i < 3; i++) {
        sum = sum + arr[i];
    }
    return sum;
}
