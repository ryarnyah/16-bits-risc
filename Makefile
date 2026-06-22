SHELL := /bin/bash
EMU_DIR := emulator
BUILD_DIR := $(EMU_DIR)/build
TARGET_DIR := target/gen
HEX_DIR := examples

SBT := sbt
VERILATOR := verilator

.PHONY: all help rtl formal emulator test clean assemble \
        examples/prime.hex examples/echo.hex uart

all: rtl formal emulator

help:
	@echo "Targets:"
	@echo "  all                  - Build RTL + formal + emulator"
	@echo "  rtl                  - Generate SystemVerilog via sbt"
	@echo "  formal               - Run formal verification (BMC 50)"
	@echo "  emulator             - Build Verilator emulator"
	@echo "  test                 - Run sbt unit tests"
	@echo "  assemble             - Assemble all .asm files in examples/"
	@echo "  uart                 - Run UART echo demo"
	@echo "  clean                - Remove all build artifacts"

rtl:
	$(SBT) run

formal: rtl
	$(SBT) "testOnly risc.CoreFormalTest"

emulator: $(TARGET_DIR)/Core.sv
	mkdir -p $(BUILD_DIR)
	cd $(BUILD_DIR) && $(VERILATOR) --cc --trace -Wall \
		-Wno-DECLFILENAME -Wno-UNUSED -Wno-UNOPTFLAT \
		-Wno-WIDTH -Wno-CASEINCOMPLETE -Wno-PINCONNECTEMPTY \
		-CFLAGS "-std=c++17 -O2" --top-module Core \
		$(abspath $(TARGET_DIR))/Core.sv
	cd $(BUILD_DIR) && $(MAKE) -f VCore.mk
	$(CXX) -std=c++17 -O2 -I$(BUILD_DIR) -I/usr/share/verilator/include \
		-o $(BUILD_DIR)/emulator $(EMU_DIR)/main.cpp \
		$(BUILD_DIR)/VCore__ALL.a \
		-L/usr/share/verilator/include -lverilator

$(TARGET_DIR)/Core.sv: $(shell find src/main -name '*.scala')
	$(SBT) run

test:
	$(SBT) test

assemble: examples/echo.hex examples/counter.hex

examples/%.hex: examples/%.asm asm.py
	python3 asm.py $< -o $@

examples/echo.hex: asm.py
examples/counter.hex: asm.py

uart: examples/echo.hex emulator
	$(BUILD_DIR)/emulator --uart examples/echo.hex

clean:
	rm -rf $(TARGET_DIR) $(BUILD_DIR) target/sim target/formal
	rm -f examples/*.hex
