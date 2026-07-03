/* Array iteration with LD inside loop */
int main() {
    int arr[3];
    int i, sum;
    arr[0] = 5;
    arr[1] = 10;
    arr[2] = 15;
    sum = 0;
    for (i = 0; i < 3; i++)
        sum = sum + arr[i];
    return sum;  // 30
}
