SHELL := /bin/bash
EMU_DIR := emulator
BUILD_DIR := $(EMU_DIR)/build
TARGET_DIR := target/gen
HEX_DIR := examples

SBT := sbt
VERILATOR := verilator
VERILATOR_BIN := $(shell readlink -f $$(which $(VERILATOR)))
VERILATOR_ROOT := $(dir $(VERILATOR_BIN))../share/verilator
VERILATOR_INC := $(VERILATOR_ROOT)/include

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
	$(SBT) "runMain risc.Soc"

formal: rtl
	$(SBT) "testOnly risc.CoreFormalTest"

emulator: $(TARGET_DIR)/Soc.sv
	mkdir -p $(BUILD_DIR)
	cd $(BUILD_DIR) && $(VERILATOR) --cc --trace -Wall \
		-Wno-DECLFILENAME -Wno-UNUSED -Wno-UNOPTFLAT \
		-Wno-WIDTH -Wno-CASEINCOMPLETE -Wno-PINCONNECTEMPTY \
		-Wno-UNDRIVEN \
		-CFLAGS "-std=c++17 -O2" --top-module Soc \
		$(abspath $(TARGET_DIR))/Soc.sv
	$(MAKE) -C $(BUILD_DIR)/obj_dir -f VSoc.mk
	$(CXX) -std=c++17 -O2 -I$(BUILD_DIR)/obj_dir -I$(VERILATOR_ROOT)/include \
		-o $(BUILD_DIR)/emulator $(EMU_DIR)/main.cpp \
		$(BUILD_DIR)/obj_dir/VSoc__ALL.a \
		$(BUILD_DIR)/obj_dir/verilated.o \
		$(BUILD_DIR)/obj_dir/verilated_vcd_c.o \
		$(BUILD_DIR)/obj_dir/verilated_threads.o

$(TARGET_DIR)/Soc.sv: $(shell find src/main -name '*.scala')
	$(SBT) "runMain risc.Soc"

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
