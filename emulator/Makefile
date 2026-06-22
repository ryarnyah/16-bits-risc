CORE_DIR = $(abspath ../target/gen)
BUILD_DIR = ./build
VCORE_SV = $(CORE_DIR)/Core.sv
VCORE_CPP = $(BUILD_DIR)/VCore.cpp
VCORE_H = $(BUILD_DIR)/VCore.h
VERILATOR = verilator
VERILATOR_FLAGS = --cc --trace -Wall -Wno-DECLFILENAME -Wno-UNUSED -Wno-UNOPTFLAT -Wno-WIDTH -Wno-CASEINCOMPLETE -Wno-PINCONNECTEMPTY -CFLAGS "-std=c++17 -O2" --top-module Core

all: $(BUILD_DIR)/emulator

$(BUILD_DIR):
	mkdir -p $(BUILD_DIR)

$(VCORE_CPP) $(VCORE_H): $(VCORE_SV) | $(BUILD_DIR)
	cd $(BUILD_DIR) && $(VERILATOR) $(VERILATOR_FLAGS) $(VCORE_SV)

$(BUILD_DIR)/emulator: $(VCORE_CPP) $(VCORE_H) main.cpp | $(BUILD_DIR)
	cd $(BUILD_DIR) && $(MAKE) -f VCore.mk

run: $(BUILD_DIR)/emulator
	$(BUILD_DIR)/emulator

clean:
	rm -rf $(BUILD_DIR)

.PHONY: all run clean