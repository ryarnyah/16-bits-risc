#include <verilated.h>
#include <verilated_vcd_c.h>
#include <iostream>
#include <fstream>
#include <vector>
#include <cstdint>
#include <cstring>
#include <string>
#include "VCore.h"

using u16 = uint16_t;
using u32 = uint32_t;
using u8 = uint8_t;
using u64 = uint64_t;

class Emulator {
    VCore* core;
    VerilatedVcdC* tfp;
    u64 cycle;

public:
    Emulator() : core(new VCore), tfp(nullptr), cycle(0) {
        Verilated::traceEverOn(true);
        tfp = new VerilatedVcdC;
        core->trace(tfp, 99);
        tfp->open("sim.vcd");
        reset();
    }

    ~Emulator() {
        tfp->close();
        delete core;
        delete tfp;
    }

    void reset() {
        core->clk = 0;
        core->resetn = 0;
        core->io_bus_cmd_valid = 0;
        core->io_bus_cmd_payload = 0;
        core->io_bus_rsp_ready = 0;
        for (int i = 0; i < 10; i++) tick();
        core->resetn = 1;
        for (int i = 0; i < 10; i++) tick();
    }

    void tick() {
        core->clk = 1;
        core->eval();
        tfp->dump(cycle * 10 + 5);
        core->clk = 0;
        core->eval();
        tfp->dump(cycle * 10 + 10);
        cycle++;
    }

    void sendCmd(u8 cmd, u8 addr, u16 data) {
        u32 word = ((u32)cmd << 24) | ((u32)addr << 16) | data;
        u8 bytes[4] = {
            u8((word >> 24) & 0xFF),
            u8((word >> 16) & 0xFF),
            u8((word >> 8) & 0xFF),
            u8(word & 0xFF)
        };

        for (int i = 0; i < 4; i++) {
            core->io_bus_cmd_valid = 1;
            core->io_bus_cmd_payload = bytes[i];
            tick();
            while (!core->io_bus_cmd_ready) tick();
        }
        core->io_bus_cmd_valid = 0;
        tick();
    }

    void waitAck() {
        tick();
        int tries = 0;
        while (!core->io_bus_ack && tries < 1000) {
            tick();
            tries++;
        }
        tick();
    }

    u32 readRsp() {
        u32 result = 0;
        for (int i = 0; i < 4; i++) {
            int tries = 0;
            while (!core->io_bus_rsp_valid && tries < 1000) {
                tick();
                tries++;
            }
            u8 byte = core->io_bus_rsp_payload & 0xFF;
            core->io_bus_rsp_ready = 1;
            tick();
            core->io_bus_rsp_ready = 0;
            result = (result << 8) | byte;
        }
        return result;
    }

    void loadProgram(const std::vector<u16>& words) {
        sendCmd(0x01, 0, 0);
        waitAck();
        for (u16 w : words) {
            sendCmd(0x02, 0, w);
            waitAck();
        }
    }

    void step() {
        sendCmd(0x04, 0, 0);
        waitAck();
    }

    u16 readReg(int regIdx) {
        sendCmd(0x06, regIdx & 0xFF, 0);
        u32 rsp = readRsp();
        return u16(rsp & 0xFFFF);
    }

    u16 readMem(u16 addr) {
        sendCmd(0x07, 0, addr);
        u32 rsp = readRsp();
        return u16(rsp & 0xFFFF);
    }

    u16 readPC() {
        sendCmd(0x08, 0, 0);
        u32 rsp = readRsp();
        return u16(rsp & 0xFFFF);
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

    Emulator emu;

    if (argc > 1) {
        auto prog = loadHexFile(argv[1]);
        emu.loadProgram(prog);
    }

    std::cout << "RISC Core Emulator ready. Commands:\n"
              << "  s [n]    - step n times (default 1), show PC after each\n"
              << "  r [n]    - read register n (default 0)\n"
              << "  m [addr] - read memory at addr (default 0)\n"
              << "  p        - read PC\n"
              << "  q        - quit\n";

    auto parseArg = [](const std::string& line, int defaultVal) -> int {
        auto pos = line.find_first_of(" \t");
        if (pos == std::string::npos) return defaultVal;
        return std::stoi(line.substr(pos + 1));
    };

    std::string line;
    while (true) {
        std::cout << "> ";
        if (!std::getline(std::cin, line)) break;
        if (line.empty()) continue;

        char cmd = line[0];
        if (cmd == 'q') break;
        else if (cmd == 's') {
            int n = parseArg(line, 1);
            for (int i = 0; i < n; i++) {
                emu.step();
                if (n > 1) std::cout << "  PC=" << std::hex << emu.readPC() << std::dec << " ";
            }
            if (n == 1) std::cout << "PC=" << std::hex << emu.readPC() << std::dec << " ";
            std::cout << "R3=" << std::hex << emu.readReg(3) << std::dec << "\n";
        }
        else if (cmd == 'r') {
            int reg = parseArg(line, 0);
            std::cout << "R" << reg << " = 0x" << std::hex << emu.readReg(reg) << std::dec << "\n";
        }
        else if (cmd == 'm') {
            u16 addr = u16(parseArg(line, 0));
            if (line.size() > 2 && line.substr(2, 2) == "0x")
                addr = u16(std::stoul(line.substr(2), nullptr, 16));
            std::cout << "mem[0x" << std::hex << addr << "] = 0x" << emu.readMem(addr) << std::dec << "\n";
        }
        else if (cmd == 'p') {
            std::cout << "PC = 0x" << std::hex << emu.readPC() << std::dec
                      << "  R3 = 0x" << emu.readReg(3) << std::dec << "\n";
        }
        else {
            std::cout << "Unknown command\n";
        }
    }

    return 0;
}
