CORE_DIR   = $(abspath ../target/gen)
BUILD_DIR  = $(abspath ./build)
EMULATOR   = $(BUILD_DIR)/obj_dir/emulator
VSOC_SV    = $(CORE_DIR)/Soc.sv
MAIN_CPP   = $(abspath ./main.cpp)
VERILATOR  = verilator
VERILATOR_FLAGS = --cc --trace -Wall -Wno-DECLFILENAME -Wno-UNUSED -Wno-UNOPTFLAT -Wno-WIDTH -Wno-CASEINCOMPLETE -Wno-PINCONNECTEMPTY -Wno-UNDRIVEN -CFLAGS "-std=c++17 -O2" --top-module Soc

all: $(EMULATOR)

$(BUILD_DIR):
	mkdir -p $(BUILD_DIR)/obj_dir

$(EMULATOR): $(VSOC_SV) main.cpp | $(BUILD_DIR)
	cd $(BUILD_DIR) && $(VERILATOR) $(VERILATOR_FLAGS) --build --exe $(VSOC_SV) $(MAIN_CPP) -o emulator

run: $(EMULATOR)
	$(EMULATOR) $(PROG)

clean:
	rm -rf $(BUILD_DIR)

.PHONY: all run clean