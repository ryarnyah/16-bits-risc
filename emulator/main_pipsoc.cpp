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
#include "VPipSoc.h"
#include "VPipSoc___024root.h"
#include "VPipSoc__Syms.h"

using u16 = uint16_t;
using u32 = uint32_t;
using u8 = uint8_t;
using u64 = uint64_t;

static constexpr int BIT_CYCLES = 868;

class Emulator {
public:
    VPipSoc* soc;
private:
    VerilatedVcdC* tfp;
    u64 cycle;
    bool traceOn;

    void debugInstrFetch() {
        auto pc_ = soc->rootp->PipSoc__DOT__core__DOT__pc;
        auto instrWordAddr = (pc_ >> 1) & 0xFFF;
        auto memVal = soc->rootp->PipSoc__DOT__instrRom[instrWordAddr];
        printf("[DBG] pc=%04x wordAddr=%03x instrRom=%04x\n", pc_, instrWordAddr, memVal);
    }

    int txState;
    int txCnt;
    u8 txByte;
    bool prevTx;

    int rxState;
    int rxCnt;
    u8 rxByte;
    u8 rxPending;
    bool rxHasData;

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
        while (*running) {
            if (read(STDIN_FILENO, &c, 1) > 0) {
                std::lock_guard<std::mutex> lock(*mtx);
                queue->push(c);
                cond->notify_one();
            }
        }
    }

public:
    Emulator(bool vcd = true, bool enableStdin = false) : soc(new VPipSoc), tfp(nullptr), cycle(0),
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
        // Don't run with resetn=1 here — program must be loaded first.
        // loadProgram() is called after reset() by the emulator main loop.
    }

    void tick() {
        driveRx();
        soc->clk = 1;
        soc->eval();
        if (traceOn) tfp->dump(cycle * 10 + 5);
        soc->clk = 0;
        soc->eval();
        if (traceOn) tfp->dump(cycle * 10 + 10);
        sampleTx();
        cycle++;
    }

    void sampleTx() {
        bool tx = soc->io_uartTx;
        if (txState == 0) {
            if (prevTx && !tx) {
                txState = 1;
                txCnt = 0;
                txByte = 0;
            }
        } else if (txState == 1) {
            txCnt++;
            if (txCnt >= BIT_CYCLES / 2) {
                txCnt = 0;
                txState = 2;
            }
        } else if (txState >= 2 && txState <= 9) {
            txCnt++;
            if (txCnt >= BIT_CYCLES) {
                txCnt = 0;
                txByte |= (tx ? 1 : 0) << (txState - 2);
                txState++;
            }
        } else if (txState == 10) {
            txCnt++;
            if (txCnt >= BIT_CYCLES / 2) {
                (void)write(STDOUT_FILENO, &txByte, 1);
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
                }
            }
            if (hasChar) {
                rxByte = (u8)c;
                rxState = 1;
                rxCnt = 0;
                rxHasData = true;
                soc->io_uartRx = 0;
                return;
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
                    soc->io_uartRx = 1;
                }
                rxState++;
            }
        } else if (rxState == 10) {
            soc->io_uartRx = 1;
            if (rxCnt >= BIT_CYCLES) {
                rxState = 0;
                rxHasData = false;
            }
        }
    }

    void loadProgram(const std::vector<u16>& words) {
        for (size_t i = 0; i < words.size(); i++) {
            soc->rootp->PipSoc__DOT__instrRom[i] = words[i];
        }
        fprintf(stderr, "[DBG] loadProgram: %zu words, rom[0]=%04x rom[1]=%04x\n",
            words.size(),
            soc->rootp->PipSoc__DOT__instrRom[0],
            soc->rootp->PipSoc__DOT__instrRom[1]);
    }

    void run(int steps) {
        for (int i = 0; i < steps; i++) {
            tick();
        }
    }

    void dumpPipe() {
        auto exVld = soc->rootp->PipSoc__DOT__core__DOT__exVld;
        auto stallEx = soc->rootp->PipSoc__DOT__core__DOT__stallId;
        auto stallWb = soc->rootp->PipSoc__DOT__core__DOT__stallWb;
        auto stallId = soc->rootp->PipSoc__DOT__core__DOT__stallId;
        auto exIsST = soc->rootp->PipSoc__DOT__core__DOT__exIsST;
        auto exIsLD = soc->rootp->PipSoc__DOT__core__DOT__exIsLD;
        auto ldActive = soc->rootp->PipSoc__DOT__core__DOT__ldActive;
        auto ldRd = soc->rootp->PipSoc__DOT__core__DOT__ldRd;
        auto ifVld = soc->rootp->PipSoc__DOT__core__DOT__ifVld;
        auto idVld = soc->rootp->PipSoc__DOT__core__DOT__idVld;
        auto exHasRd = soc->rootp->PipSoc__DOT__core__DOT__exHasRd;
        auto exRd = soc->rootp->PipSoc__DOT__core__DOT__exRd;
        auto wbRd = soc->rootp->PipSoc__DOT__core__DOT__wbRd;
        auto pc_ = soc->rootp->PipSoc__DOT__core__DOT__pc;
        auto dbgState = soc->io_dbgState;
        auto reqFire = soc->rootp->PipSoc__DOT__io_dataBus_req_fire;
        auto rspFire = soc->rootp->PipSoc__DOT__io_dataBus_rsp_fire;
        auto addr = soc->rootp->PipSoc__DOT__core_io_dataBus_req_payload_addr;
        auto ifLdiHeader = soc->rootp->PipSoc__DOT__core__DOT__ifLdiHeader;
        auto ifLdiData = soc->rootp->PipSoc__DOT__core__DOT__ifLdiData;
        auto ifInstr = soc->rootp->PipSoc__DOT__core__DOT__ifInstr;
        auto ifPc = soc->rootp->PipSoc__DOT__core__DOT__ifPc;
        auto idInstr = soc->rootp->PipSoc__DOT__core__DOT__idInstr;
        auto idPc = soc->rootp->PipSoc__DOT__core__DOT__idPc;
        auto idLdiData = soc->rootp->PipSoc__DOT__core__DOT__idLdiData;
        auto exInstr = soc->rootp->PipSoc__DOT__core__DOT__exInstr;
        auto exPc = soc->rootp->PipSoc__DOT__core__DOT__exPc;
        auto exLdiData = soc->rootp->PipSoc__DOT__core__DOT__exLdiData;
        auto wbResult = soc->rootp->PipSoc__DOT__core__DOT__wbResult;
        printf("--- Pipeline Dump ---\n");
        printf("PC=%04x exVld=%d exIsLD=%d exIsST=%d ldActive=%d ifVld=%d idVld=%d\n",
            pc_, exVld, exIsLD, exIsST, ldActive, ifVld, idVld);
        printf("stallEx=%d stallWb=%d stallId=%d exHasRd=%d exRd=%d wbRd=%d ldRd=%d\n",
            stallEx, stallWb, stallId, exHasRd, exRd, wbRd, ldRd);
        printf("state=%d reqFire=%d rspFire=%d addr=%04x\n",
            dbgState, reqFire, rspFire, addr);
        printf("IF: vld=%d pc=%04x instr=%04x hdr=%04x data=%04x\n",
            ifVld, ifPc, ifInstr, ifLdiHeader, ifLdiData);
        printf("ID: vld=%d pc=%04x instr=%04x data=%04x\n",
            idVld, idPc, idInstr, idLdiData);
        printf("EX: vld=%d pc=%04x instr=%04x data=%04x\n",
            exVld, exPc, exInstr, exLdiData);
        printf("WB: res=%04x wbRd=%d wbHasRd=%d ldActive=%d\n", wbResult,
            soc->rootp->PipSoc__DOT__core__DOT__wbRd,
            soc->rootp->PipSoc__DOT__core__DOT__wbHasRd,
            soc->rootp->PipSoc__DOT__core__DOT__ldActive);
        printf("  stallWb=%d stallEx=%d stallId=%d wbLdPhase=%d\n",
            soc->rootp->PipSoc__DOT__core__DOT__stallWb,
            soc->rootp->PipSoc__DOT__core__DOT__stallId,
            soc->rootp->PipSoc__DOT__core__DOT__stallId,
            soc->rootp->PipSoc__DOT__core__DOT__wbLdPhase);
    }

    // Single-step trace showing pipeline contents per cycle
    void traceStep(int n) {
        for (int i = 0; i < n; i++) {
            tick();
            auto ifInstr = soc->rootp->PipSoc__DOT__core__DOT__ifInstr;
            auto ifPc = soc->rootp->PipSoc__DOT__core__DOT__ifPc;
            auto ifVld = soc->rootp->PipSoc__DOT__core__DOT__ifVld;
            auto ifLdiHeader = soc->rootp->PipSoc__DOT__core__DOT__ifLdiHeader;
            auto idInstr = soc->rootp->PipSoc__DOT__core__DOT__idInstr;
            auto idPc = soc->rootp->PipSoc__DOT__core__DOT__idPc;
            auto idVld = soc->rootp->PipSoc__DOT__core__DOT__idVld;
            auto idLdiData = soc->rootp->PipSoc__DOT__core__DOT__idLdiData;
            auto exInstr = soc->rootp->PipSoc__DOT__core__DOT__exInstr;
            auto exPc = soc->rootp->PipSoc__DOT__core__DOT__exPc;
            auto exVld = soc->rootp->PipSoc__DOT__core__DOT__exVld;
            auto exIsLD = soc->rootp->PipSoc__DOT__core__DOT__exIsLD;
            auto exIsST = soc->rootp->PipSoc__DOT__core__DOT__exIsST;
            auto exHasRd = soc->rootp->PipSoc__DOT__core__DOT__exHasRd;
            auto exRd = soc->rootp->PipSoc__DOT__core__DOT__exRd;
            auto pc_ = soc->rootp->PipSoc__DOT__core__DOT__pc;
            // Dump ROM at the current fetch address
            auto rom0 = soc->rootp->PipSoc__DOT__instrRom[0];
            auto rom1 = soc->rootp->PipSoc__DOT__instrRom[1];
            auto rom4 = soc->rootp->PipSoc__DOT__instrRom[4];
            auto wbRd_ = soc->rootp->PipSoc__DOT__core__DOT__wbRd;
            auto wbHasRd_ = soc->rootp->PipSoc__DOT__core__DOT__wbHasRd;
            auto ldActive_ = soc->rootp->PipSoc__DOT__core__DOT__ldActive;
            auto ldRd_ = soc->rootp->PipSoc__DOT__core__DOT__ldRd;
            auto wbResult_ = soc->rootp->PipSoc__DOT__core__DOT__wbResult;
            auto fetchBuf = soc->rootp->PipSoc__DOT__core__DOT__fetchBufData;
            auto stallWb_ = soc->rootp->PipSoc__DOT__core__DOT__stallWb;
            auto r2val = soc->rootp->PipSoc__DOT__core__DOT__regFile_1__DOT__regs_2;
            auto stallEx_ = soc->rootp->PipSoc__DOT__core__DOT__stallId;
            auto aluRes_ = soc->rootp->PipSoc__DOT__core__DOT__alu_1_io_result;
            auto exBrTaken_ = soc->rootp->PipSoc__DOT__core__DOT__exBrTaken;
            auto exRsVal_ = soc->rootp->PipSoc__DOT__core__DOT__exRsVal;
            auto r6val = soc->rootp->PipSoc__DOT__core__DOT__regFile_1__DOT__regs_6;
            auto rf_r1 = readReg(1);
            auto rf_r2 = readReg(2);
            auto rf_r3 = readReg(3);
            auto rf_r4 = readReg(4);
            auto rf_r6 = readReg(6);
            printf("[%d] PC=%04x  IF:pc=%04x instr=%04x hdr=%d v=%d  ID:pc=%04x instr=%04x data=%04x v=%d  EX:pc=%04x instr=%04x v=%d isLD=%d isST=%d rd=%d alu=%04x br=%d  WB:rd=%d has=%d ld=%d res=%04x stlWb=%d stlEx=%d  R1=%04x R2=%04x R3=%04x R4=%04x R6=%04x fb=%04x\n",
                cycle, pc_, ifPc, ifInstr, ifLdiHeader, ifVld,
                idPc, idInstr, idLdiData, idVld,
                exPc, exInstr, exVld, exIsLD, exIsST, exRd,
                aluRes_, exBrTaken_,
                wbRd_, wbHasRd_, ldActive_, wbResult_, stallWb_, stallEx_,
                rf_r1, rf_r2, rf_r3, rf_r4, rf_r6,
                fetchBuf);
        }
        u16 r1_s = readReg(1);
        u16 r2_s = readReg(2);
        u16 r3_s = readReg(3);
        u16 r4_s = readReg(4);
        printf("** R1=%04x R2=%04x R3=%04x R4=%04x PC=%04x\n",
            r1_s, r2_s, r3_s, r4_s, readPC());
    }

    u16 readState() {
        return soc->io_dbgState;
    }

    u16 readAluRes() {
        return soc->rootp->PipSoc__DOT__core__DOT__wbResult;
    }

    u16 readInstr() {
        return soc->rootp->PipSoc__DOT__core__DOT__ifInstr;
    }

    u16 readPC() {
        return soc->rootp->PipSoc__DOT__core__DOT__pc;
    }

    u64 readCycle() {
        return cycle;
    }

    u16 readReg(int idx) {
        switch (idx) {
            case 0: return soc->rootp->PipSoc__DOT__core__DOT__regFile_1__DOT__regs_0;
            case 1: return soc->rootp->PipSoc__DOT__core__DOT__regFile_1__DOT__regs_1;
            case 2: return soc->rootp->PipSoc__DOT__core__DOT__regFile_1__DOT__regs_2;
            case 3: return soc->rootp->PipSoc__DOT__core__DOT__regFile_1__DOT__regs_3;
            case 4: return soc->rootp->PipSoc__DOT__core__DOT__regFile_1__DOT__regs_4;
            case 5: return soc->rootp->PipSoc__DOT__core__DOT__regFile_1__DOT__regs_5;
            case 6: return soc->rootp->PipSoc__DOT__core__DOT__regFile_1__DOT__regs_6;
            case 7: return soc->rootp->PipSoc__DOT__core__DOT__regFile_1__DOT__regs_7;
            default: return 0;
        }
    }

    u16 readMem(u16 addr) {
        return soc->rootp->PipSoc__DOT__instrRom[addr];
    }

    u16 readDataMem(u16 addr) {
        return soc->rootp->PipSoc__DOT__dataRam[addr];
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
    bool traceMode = false;
    const char* hexFile = nullptr;
    for (int i = 1; i < argc; i++) {
        if (strcmp(argv[i], "--uart") == 0) uartMode = true;
        else if (strcmp(argv[i], "-load") == 0) {
            // Next arg is the hex file
        }
        else if (strcmp(argv[i], "-trace") == 0) traceMode = true;
        else hexFile = argv[i];
    }

    // Load hex file BEFORE emulator construction so ROM has data during reset
    std::vector<u16> prog;
    if (hexFile) {
        prog = loadHexFile(hexFile);
    }

    // Construct emulator AFTER loading program, write ROM before reset()
    Emulator emu(!uartMode, uartMode);

    // Load program into ROM (overwrites any zeros from construction)
    if (!prog.empty()) {
        emu.loadProgram(prog);
    }

    // Reset again so program is in ROM before execution starts
    if (!prog.empty()) {
        emu.reset();
    }

    // DEBUG: verify ROM contents
    u16 dbg_r0 = emu.soc->rootp->PipSoc__DOT__instrRom[0];
    u16 dbg_r1 = emu.soc->rootp->PipSoc__DOT__instrRom[1];
    fprintf(stderr, "[DBG-main] rom[0]=%04x rom[1]=%04x\n", dbg_r0, dbg_r1);

    if (uartMode && hexFile) {
        std::cout << "UART mode: running PipSoc, I/O via stdin/stdout\n";
        int chunk = 0;
        while (true) {
            emu.run(1000);
            chunk++;
            if (chunk % 10 == 0) {
                std::cerr << "[DBG] chunk=" << chunk << " PC=0x" << std::hex << emu.readPC() << std::dec << " cycles=" << emu.readCycle() << "\n";
            }
            if (Verilated::gotFinish()) {
                break;
            }
            if (chunk > 10000) {
                break;
            }
        }
        return 0;
    }

    std::cout << "RISC PipSoc Emulator ready. Commands:\n"
              << "  s [n]    - step n ticks (default 1000), show PC after\n"
              << "  r [n]    - read register n (default 0)\n"
              << "  m [addr] - read memory at addr (default 0)\n"
              << "  p        - read PC\n"
              << "  D        - dump pipeline state\n"
              << "  g        - go (continuous run)\n"
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
            u16 r1_s = emu.readReg(1);
            u16 r2_s = emu.readReg(2);
            u16 r3_s = emu.readReg(3);
            u16 r4_s = emu.readReg(4);
            std::cout << "PC=0x" << std::hex << emu.readPC() << std::dec
                      << "  R1=0x" << std::hex << r1_s << std::dec
                      << "  R7=0x" << std::hex << emu.readReg(7) << std::dec
                      << "  R3=0x" << std::hex << r3_s << std::dec << "\n";
            fprintf(stderr, "[DBG-s] R1=0x%04x R2=0x%04x R3=0x%04x R4=0x%04x\n",
                r1_s, r2_s, r3_s, r4_s);
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
        else if (cmd == 'd') {
            u16 addr = 0;
            auto pos = line.find_first_of(" \t");
            if (pos != std::string::npos) addr = u16(std::stoul(line.substr(pos + 1), nullptr, 0));
            std::cout << "data[0x" << std::hex << addr << "] = 0x" << emu.readDataMem(addr) << std::dec << "\n";
        }
        else if (cmd == 'p') {
            std::cout << "PC = 0x" << std::hex << emu.readPC() << std::dec
                      << "  R3 = 0x" << emu.readReg(3) << std::dec
                      << "  state=" << emu.readState()
                      << "  instr=0x" << std::hex << emu.readInstr() << std::dec << "\n";
        }
        else if (cmd == 't') {
            int n = 1;
            auto pos = line.find_first_of(" \t");
            if (pos != std::string::npos) n = std::stoi(line.substr(pos + 1));
            emu.traceStep(n);
        }
        else if (cmd == 'D') {
            emu.dumpPipe();
        }
        else if (cmd == 'T') {
            int n = 1;
            auto pos = line.find_first_of(" \t");
            if (pos != std::string::npos) n = std::stoi(line.substr(pos + 1));
            for (int i = 0; i < n; i++) {
                emu.tick();
                auto r = emu.soc->rootp;
                auto reqFire = r->PipSoc__DOT__io_dataBus_req_fire;
                auto rspFire = r->PipSoc__DOT__io_dataBus_rsp_fire;
                auto addr = r->PipSoc__DOT__core_io_dataBus_req_payload_addr;
                auto wrData = r->PipSoc__DOT__core_io_dataBus_req_payload_wrData;
                auto exVld = r->PipSoc__DOT__core__DOT__exVld;
                auto exIsST = r->PipSoc__DOT__core__DOT__exIsST;
                auto exIsLD = r->PipSoc__DOT__core__DOT__exIsLD;
                auto pc_ = r->PipSoc__DOT__core__DOT__pc;
                auto rspData = r->PipSoc__DOT__core_io_dataBus_rsp_payload;
                auto ramRspVld = r->PipSoc__DOT__ramRspVld;
                auto ramRd = r->PipSoc__DOT__dataRam_spinal_port0;
                auto stallEx = r->PipSoc__DOT__core__DOT__stallId;
                auto stallWb = r->PipSoc__DOT__core__DOT__stallWb;
                auto ldActive = r->PipSoc__DOT__core__DOT__ldActive;
                auto exRd = r->PipSoc__DOT__core__DOT__exRd;
                auto exRsAddr = r->PipSoc__DOT__core__DOT__exRsAddr;
                auto exRtAddr = r->PipSoc__DOT__core__DOT__exRtAddr;
                auto wbRd = r->PipSoc__DOT__core__DOT__wbRd;
                auto wbHasRd = r->PipSoc__DOT__core__DOT__wbHasRd;
                auto exIsALU = r->PipSoc__DOT__core__DOT__exIsALU;
                auto exHasRd = r->PipSoc__DOT__core__DOT__exHasRd;
                auto aluRes = r->PipSoc__DOT__core__DOT__alu_1_io_result;
                auto exBrTaken = r->PipSoc__DOT__core__DOT__exBrTaken;
                auto wbResult = r->PipSoc__DOT__core__DOT__wbResult;
                auto wbLdPhase = r->PipSoc__DOT__core__DOT__wbLdPhase;
                auto r1_val = emu.readReg(1);
                auto r2_val = emu.readReg(2);
                auto r3_val = emu.readReg(3);
                auto wbLd = r->PipSoc__DOT__core__DOT__wbLdData;
                auto rspVld = r->PipSoc__DOT__ramRspVld;
                printf("[%ld] PC=%04x exV=%d ALU=%d LD=%d ST=%d rd=%d rs=%d rt=%d br=%d rspF=%d alu=%04x wbRs=%04x stlEx=%d stlWb=%d ldA=%d wbRd=%d wbH=%d ldPh=%d wbLd=%04x rspV=%d R1=%04x R2=%04x R3=%04x\n",
                    (long)emu.readCycle(), pc_, exVld, exIsALU, exIsLD, exIsST, exRd, exRsAddr, exRtAddr, exBrTaken,
                    rspFire, aluRes, wbResult, stallEx, stallWb, ldActive, wbRd, wbHasRd, wbLdPhase,
                    wbLd, rspVld, r1_val, r2_val, r3_val);
            }
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
