#include <verilated.h>
#include <verilated_vcd_c.h>
#include <iostream>
#include <fstream>
#include <vector>
#include <cstdint>
#include <cstring>
#include <string>
#include <thread>
#include <atomic>
#include <mutex>
#include <queue>
#include <condition_variable>
#include <unistd.h>
#include "VSoc.h"
#include "VSoc___024root.h"
#include "VSoc__Syms.h"

using u16 = uint16_t;
using u32 = uint32_t;
using u8 = uint8_t;
using u64 = uint64_t;

static constexpr int BIT_CYCLES = 868;

class Emulator {
    VSoc* soc;
    VerilatedVcdC* tfp;
    u64 cycle;
    bool traceOn;

    // UART TX state
    int txState;  // 0=idle, 1=sample start, 2-9=data bits, 10=stop
    int txCnt;
    u8 txByte;
    bool prevTx;

    // UART RX state
    int rxState;
    int rxCnt;
    u8 rxByte;
    u8 rxPending;
    bool rxHasData;

    // stdin reader thread
    std::thread stdinThread;
    std::atomic<bool> stdinRunning;
    std::mutex stdinMutex;
    std::queue<char> stdinQueue;
    std::condition_variable stdinCond;

    static void stdinReader(std::atomic<bool>* running,
                            std::mutex* mtx,
                            std::queue<char>* queue,
                            std::condition_variable* cond) {
        char c;
        std::cerr << "[DBG] stdinReader started\n";
        while (*running) {
            if (read(STDIN_FILENO, &c, 1) > 0) {
                std::cerr << "[DBG] stdinReader read: " << (int)c << " '" << c << "'\n";
                std::lock_guard<std::mutex> lock(*mtx);
                queue->push(c);
                cond->notify_one();
            }
        }
        std::cerr << "[DBG] stdinReader exiting\n";
    }

public:
    Emulator(bool vcd = true, bool enableStdin = false) : soc(new VSoc), tfp(nullptr), cycle(0),
        traceOn(vcd), txState(0), txCnt(0), prevTx(true),
        rxState(0), rxCnt(0), rxHasData(false),
        stdinRunning(true) {
        if (traceOn) {
            Verilated::traceEverOn(true);
            tfp = new VerilatedVcdC;
            soc->trace(tfp, 99);
            tfp->open("sim.vcd");
        }
        if (enableStdin) {
            stdinThread = std::thread(stdinReader, &stdinRunning, &stdinMutex, &stdinQueue, &stdinCond);
        }
        reset();
    }

    ~Emulator() {
        stdinRunning = false;
        if (stdinThread.joinable()) stdinThread.join();
        if (tfp) tfp->close();
        delete soc;
        delete tfp;
    }

    void reset() {
        soc->clk = 0;
        soc->resetn = 0;
        soc->io_uartRx = 1;
        for (int i = 0; i < 10; i++) tick();
        soc->resetn = 1;
        for (int i = 0; i < 10; i++) tick();
    }

    void tick() {
        // UART RX: drive from pending data
        driveRx();

        soc->clk = 1;
        soc->eval();
        if (traceOn) tfp->dump(cycle * 10 + 5);
        soc->clk = 0;
        soc->eval();
        if (traceOn) tfp->dump(cycle * 10 + 10);

        // UART TX: sample after negedge
        sampleTx();

        cycle++;
    }

    void sampleTx() {
        bool tx = soc->io_uartTx;
        if (txState == 0) {
            // Idle: wait for start bit (falling edge)
            if (prevTx && !tx) {
                txState = 1;
                txCnt = 0;
                txByte = 0;
            }
        } else if (txState == 1) {
            // Start bit (should be low)
            txCnt++;
            if (txCnt >= BIT_CYCLES / 2) {
                txCnt = 0;
                txState = 2;
            }
        } else if (txState >= 2 && txState <= 9) {
            // Data bits (LSB first)
            txCnt++;
            if (txCnt >= BIT_CYCLES) {
                txCnt = 0;
                txByte |= (tx ? 1 : 0) << (txState - 2);
                txState++;
            }
        } else if (txState == 10) {
            // Stop bit
            txCnt++;
            if (txCnt >= BIT_CYCLES / 2) {
                write(STDOUT_FILENO, &txByte, 1);
                txState = 0;
            }
        }
        prevTx = tx;
    }

    void driveRx() {
        if (!rxHasData) {
            char c = 0;
            bool hasChar = false;
            {
                std::lock_guard<std::mutex> lock(stdinMutex);
                if (!stdinQueue.empty()) {
                    c = stdinQueue.front();
                    stdinQueue.pop();
                    hasChar = true;
                    std::cerr << "[UART RX] Popped char: " << (int)c << " ('" << (c >= 32 && c < 127 ? c : '?') << "') queue size=" << stdinQueue.size() << "\n";
                }
            }
            if (hasChar) {
                rxByte = (u8)c;
                rxState = 1;
                rxCnt = 0;
                rxHasData = true;
                soc->io_uartRx = 0;  // start bit
                return;  // skip rxCnt++ this tick — bit period is BIT_CYCLES ticks
            }
            soc->io_uartRx = 1;
            return;
        }
        rxCnt++;
        if (rxState == 1) {
            if (rxCnt >= BIT_CYCLES) {
                rxCnt = 0;
                rxState = 2;
                soc->io_uartRx = (rxByte >> 0) & 1;
            }
        } else if (rxState >= 2 && rxState <= 9) {
            if (rxCnt >= BIT_CYCLES) {
                rxCnt = 0;
                int bitIdx = rxState - 2;
                if (bitIdx < 7) {
                    soc->io_uartRx = (rxByte >> (bitIdx + 1)) & 1;
                } else {
                    // rxState = 9 → 10: after bit 7, drive line idle (1)
                    soc->io_uartRx = 1;
                }
                rxState++;
            }
        } else if (rxState == 10) {
            // Stop bit: drive line idle (1) throughout
            soc->io_uartRx = 1;
            if (rxCnt >= BIT_CYCLES) {
                rxState = 0;
                rxHasData = false;
            }
        }
    }

    void loadProgram(const std::vector<u16>& words) {
        for (size_t i = 0; i < words.size(); i++) {
            soc->rootp->Soc__DOT__instrRom[i] = words[i];
        }
    }

    void run(int steps) {
        for (int i = 0; i < steps; i++) {
            tick();
        }
    }

    u16 readState() {
        return soc->rootp->Soc__DOT__core_1__DOT__state;
    }

    u16 readAluRes() {
        return soc->rootp->Soc__DOT__core_1__DOT__aluRes;
    }

    u16 readInstr() {
        return soc->rootp->Soc__DOT__core_1__DOT__instr;
    }

    void start() {
        soc->rootp->Soc__DOT__core_1__DOT__running = 1;
        soc->rootp->Soc__DOT__core_1__DOT__state = 1;  // FETCH
    }

    u16 readPC() {
        return soc->rootp->Soc__DOT__core_1__DOT__PC;
    }

    u64 readCycle() {
        return cycle;
    }

    u16 readReg(int idx) {
        switch (idx) {
            case 0: return soc->rootp->Soc__DOT__core_1__DOT__regFile_1__DOT__regs_0;
            case 1: return soc->rootp->Soc__DOT__core_1__DOT__regFile_1__DOT__regs_1;
            case 2: return soc->rootp->Soc__DOT__core_1__DOT__regFile_1__DOT__regs_2;
            case 3: return soc->rootp->Soc__DOT__core_1__DOT__regFile_1__DOT__regs_3;
            case 4: return soc->rootp->Soc__DOT__core_1__DOT__regFile_1__DOT__regs_4;
            case 5: return soc->rootp->Soc__DOT__core_1__DOT__regFile_1__DOT__regs_5;
            case 6: return soc->rootp->Soc__DOT__core_1__DOT__regFile_1__DOT__regs_6;
            case 7: return soc->rootp->Soc__DOT__core_1__DOT__regFile_1__DOT__regs_7;
            default: return 0;
        }
    }

    u16 readMem(u16 addr) {
        return soc->rootp->Soc__DOT__instrRom[addr];
    }
};

std::vector<u16> loadHexFile(const std::string& filename) {
    std::ifstream file(filename);
    std::vector<u16> words;
    std::string line;
    while (std::getline(file, line)) {
        if (line.empty() || line[0] == '#' || line[0] == ';') continue;
        u32 val = std::stoul(line, nullptr, 16);
        words.push_back(u16(val & 0xFFFF));
    }
    return words;
}

int main(int argc, char** argv) {
    Verilated::commandArgs(argc, argv);

    bool uartMode = false;
    const char* hexFile = nullptr;
    for (int i = 1; i < argc; i++) {
        if (strcmp(argv[i], "--uart") == 0) uartMode = true;
        else hexFile = argv[i];
    }

    Emulator emu(!uartMode, uartMode);

    if (hexFile) {
        auto prog = loadHexFile(hexFile);
        emu.loadProgram(prog);
        emu.start();
    }

    if (uartMode && hexFile) {
        // Continuous UART mode
        std::cout << "UART mode: running Soc, I/O via stdin/stdout\n";
        int chunk = 0;
        std::cerr << "[DBG] Starting UART loop\n";
        while (true) {
            emu.run(1000);  // run in chunks
            chunk++;
            if (chunk % 10 == 0) {
                std::cerr << "[DBG] chunk=" << chunk << " PC=0x" << std::hex << emu.readPC() << std::dec << " cycles=" << emu.readCycle() << "\n";
            }
            if (Verilated::gotFinish()) {
                std::cerr << "[DBG] Verilated::gotFinish()=true at chunk=" << chunk << " PC=0x" << std::hex << emu.readPC() << std::dec << "\n";
                break;
            }
            if (chunk > 10000) {
                std::cerr << "[DBG] Chunk limit reached\n";
                break;
            }
        }
        std::cerr << "[DBG] Exiting UART loop\n";
        return 0;
    }

    std::cout << "RISC Soc Emulator ready. Commands:\n"
              << "  s [n]    - step n ticks (default 1000), show PC/cycles after\n"
              << "  r [n]    - read register n (default 0)\n"
              << "  m [addr] - read memory at addr (default 0)\n"
              << "  p        - read PC\n"
              << "  c        - read cycle count\n"
              << "  g        - go (continuous run with UART I/O)\n"
              << "  q        - quit\n";

    std::string line;
    while (true) {
        std::cout << "> ";
        if (!std::getline(std::cin, line)) break;
        if (line.empty()) continue;

        char cmd = line[0];
        if (cmd == 'q') break;
        else if (cmd == 's') {
            int n = 1000;
            auto pos = line.find_first_of(" \t");
            if (pos != std::string::npos) n = std::stoi(line.substr(pos + 1));
            emu.run(n);
            std::cout << "PC=0x" << std::hex << emu.readPC() << std::dec
                      << "  R3=0x" << emu.readReg(3) << std::dec
                      << "  cycles=" << std::dec << emu.readCycle() << "\n";
        }
        else if (cmd == 'c') {
            std::cout << "cycles=" << std::dec << emu.readCycle() << "\n";
        }
        else if (cmd == 'r') {
            int reg = 0;
            auto pos = line.find_first_of(" \t");
            if (pos != std::string::npos) reg = std::stoi(line.substr(pos + 1));
            std::cout << "R" << reg << " = 0x" << std::hex << emu.readReg(reg) << std::dec << "\n";
        }
        else if (cmd == 'm') {
            u16 addr = 0;
            auto pos = line.find_first_of(" \t");
            if (pos != std::string::npos) addr = u16(std::stoul(line.substr(pos + 1), nullptr, 0));
            std::cout << "mem[0x" << std::hex << addr << "] = 0x" << emu.readMem(addr) << std::dec << "\n";
        }
        else if (cmd == 'p') {
            std::cout << "PC = 0x" << std::hex << emu.readPC() << std::dec
                      << "  R3 = 0x" << emu.readReg(3) << std::dec
                      << "  state=" << emu.readState()
                      << "  aluRes=0x" << std::hex << emu.readAluRes() << std::dec
                      << "  instr=0x" << std::hex << emu.readInstr() << std::dec << "\n";
        }
        else if (cmd == 'g') {
            std::cout << "Running... Ctrl-C to stop\n";
            while (true) {
                emu.run(10000);
                if (Verilated::gotFinish()) break;
            }
        }
        else {
            std::cout << "Unknown command\n";
        }
    }

    return 0;
}
