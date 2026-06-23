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
        examples/prime.hex examples/echo.hex uart \
        f4pga f4pga_synth f4pga_pnr f4pga_bitstream f4pga_program \
        vendor-f4pga

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
	@echo "  vendor-f4pga         - Install F4PGA toolchain into vendor/"
	@echo "  f4pga                - Full FPGA flow (synth + PnR + bitstream)"
	@echo "  f4pga_synth          - Run Yosys synthesis (RTL -> JSON)"
	@echo "  f4pga_pnr            - Place-and-route (JSON -> FASM)"
	@echo "  f4pga_bitstream      - Assemble bitstream (FASM -> frames -> BIT)"
	@echo "  f4pga_program        - Program Basys3 via openFPGALoader (needs HW)"
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

# ======================================================================
# F4PGA — Basys3 (Artix-7 XC7A35T) FPGA implementation
#
# Tools are resolved in this order:
#   1. vendor/bin/<tool>          — installed by `make vendor-f4pga`
#   2. vendor/miniconda/envs/f4pga/bin/<tool>
#   3. $PATH
# ======================================================================

VENDOR_DIR     := vendor
VENDOR_BIN     := $(VENDOR_DIR)/bin
OSS_CAD_DIR    := $(VENDOR_DIR)/oss-cad-suite

# Tool resolution — first existing wins
f4pga_tool = $(or \
	$(realpath $(VENDOR_BIN)/$(1) 2>/dev/null), \
	$(shell which $(1) 2>/dev/null))

F4PGA_DIR     := f4pga/build
F4PGA_JSON    := $(F4PGA_DIR)/Soc.json
F4PGA_FASM    := $(F4PGA_DIR)/Soc.fasm
F4PGA_BIT     := $(F4PGA_DIR)/Soc.bit
F4PGA_XDC     := f4pga/basys3.xdc
F4PGA_DEVICE  := artix7
F4PGA_PART    := xc7a35tcpg236-1
F4PGA_FRM     := $(F4PGA_DIR)/Soc.frm
F4PGA_DB      := $(VENDOR_DIR)/nextpnr-xilinx-src/xilinx/external/prjxray-db

PRJXRAY_SRC   := $(VENDOR_DIR)/prjxray-src
PRJXRAY_BLD   := $(VENDOR_DIR)/prjxray-build
XC7FRAMES2BIT := $(PRJXRAY_BLD)/tools/xc7frames2bit
FASM_STUB     := $(VENDOR_DIR)/fasm_stub

# ── Vendor installation ────────────────────────────────────────────────
#
# Downloads oss-cad-suite (pre-built Yosys, openFPGALoader, prjxray
# tools, etc.) and builds nextpnr-xilinx from source for xc7 PnR.
#
#   oss-cad-suite : https://github.com/YosysHQ/oss-cad-suite-build
#   nextpnr-xilinx: https://github.com/gatecat/nextpnr-xilinx

OSS_CAD_TAG  := $(shell curl -sL --connect-timeout 10 \
	https://api.github.com/repos/YosysHQ/oss-cad-suite-build/releases/latest 2>/dev/null \
	| python3 -c "import json,sys; print(json.load(sys.stdin)['tag_name'])" 2>/dev/null)
OSS_CAD_VER  := $(subst -,,$(OSS_CAD_TAG))
OSS_CAD_URL  := https://github.com/YosysHQ/oss-cad-suite-build/releases/download/$(OSS_CAD_TAG)/oss-cad-suite-linux-x64-$(OSS_CAD_VER).tgz
OSS_CAD_DIR  := $(VENDOR_DIR)/oss-cad-suite
OSS_CAD_BIN  := $(OSS_CAD_DIR)/bin

NEXTPNR_SRC  := $(VENDOR_DIR)/nextpnr-xilinx-src
NEXTPNR_BLD  := $(VENDOR_DIR)/nextpnr-xilinx-build
NEXTPNR_BIN  := $(VENDOR_DIR)/nextpnr-xilinx/bin/nextpnr-xilinx

vendor-f4pga: $(VENDOR_BIN)/nextpnr-xilinx $(VENDOR_BIN)/openFPGALoader $(XC7FRAMES2BIT) $(FASM_STUB)
	@echo "[VENDOR] F4PGA toolchain ready in $(VENDOR_DIR)"

# ── oss-cad-suite (yosys, prjxray, openFPGALoader, etc.) ──────────────

$(OSS_CAD_BIN)/yosys:
	@echo "[VENDOR] Downloading oss-cad-suite ($(OSS_CAD_TAG), ~684 MB) ..."
	mkdir -p $(VENDOR_DIR)
	curl -#L -o $(VENDOR_DIR)/oss-cad-suite.tgz "$(OSS_CAD_URL)"
	@echo "[VENDOR] Extracting ..."
	tar -xzf $(VENDOR_DIR)/oss-cad-suite.tgz -C $(VENDOR_DIR)
	rm -f $(VENDOR_DIR)/oss-cad-suite.tgz
	@echo "[VENDOR] oss-cad-suite ready at $(OSS_CAD_DIR)"

$(VENDOR_BIN)/yosys: $(OSS_CAD_BIN)/yosys
	mkdir -p $(VENDOR_BIN)
	for tool in yosys openFPGALoader; do \
		ln -sf $$(realpath $(OSS_CAD_BIN)/$$tool) $(VENDOR_BIN)/$$tool 2>/dev/null || true; \
	done

$(VENDOR_BIN)/openFPGALoader: $(VENDOR_BIN)/yosys ;

# ── nextpnr-xilinx (xc7 P&R, built from source) ──────────────────────

$(NEXTPNR_BIN): $(VENDOR_BIN)/yosys
	@echo "[VENDOR] Cloning nextpnr-xilinx (gatecat/xilinx-upstream) ..."
	git clone --depth 1 --branch xilinx-upstream \
		--recursive https://github.com/gatecat/nextpnr-xilinx.git $(abspath $(NEXTPNR_SRC))
	@echo "[VENDOR] Building nextpnr-xilinx (this may take 15-30 min) ..."
	mkdir -p $(abspath $(NEXTPNR_BLD))
	cd $(abspath $(NEXTPNR_BLD)) && cmake $(abspath $(NEXTPNR_SRC)) -DARCH=xilinx \
		-DCMAKE_INSTALL_PREFIX=$(abspath $(VENDOR_DIR)/nextpnr-xilinx) \
		-DBOOST_ROOT=$(abspath $(VENDOR_DIR)/usr) \
		-DBOOST_INCLUDEDIR=/usr/include \
		-DBoost_NO_SYSTEM_PATHS=ON \
		-DBoost_NO_BOOST_CMAKE=ON \
		-DBoost_USE_STATIC_LIBS=ON \
		-DUSE_OPENMP=ON -DWERROR=OFF
	$(MAKE) -C $(abspath $(NEXTPNR_BLD)) -j$$(nproc) install
	@echo "[VENDOR] nextpnr-xilinx built successfully"

$(VENDOR_BIN)/nextpnr-xilinx: $(NEXTPNR_BIN)
	mkdir -p $(VENDOR_BIN)
	ln -sf $$(realpath $(NEXTPNR_BIN)) $(VENDOR_BIN)/nextpnr-xilinx

# ── prjxray (xc7frames2bit for bitstream assembly) ──────────────────────

$(PRJXRAY_SRC):
	@echo "[VENDOR] Cloning prjxray ..."
	git clone --depth 1 --recursive https://github.com/f4pga/prjxray.git $(abspath $(PRJXRAY_SRC))

$(XC7FRAMES2BIT): $(PRJXRAY_SRC)
	@echo "[VENDOR] Building prjxray (for xc7frames2bit) ..."
	mkdir -p $(abspath $(PRJXRAY_BLD))
	cd $(abspath $(PRJXRAY_BLD)) && cmake $(abspath $(PRJXRAY_SRC)) \
		-DCMAKE_POLICY_VERSION_MINIMUM=3.5 \
		-DCMAKE_BUILD_TYPE=Release \
		-DPRJXRAY_BUILD_TESTING=OFF
	$(MAKE) -C $(abspath $(PRJXRAY_BLD)) -j$$(nproc) xc7frames2bit
	@echo "[VENDOR] xc7frames2bit built"

# ── FASM stub (minimal fasm parser for PYTHONPATH) ──────────────────────

$(FASM_STUB): $(PRJXRAY_SRC)
	@echo "[VENDOR] Creating FASM stub ..."
	@mkdir -p $(abspath $(FASM_STUB))/fasm
	@cp $(abspath $(PRJXRAY_SRC))/third_party/fasm/fasm/model.py $(abspath $(FASM_STUB))/fasm/model.py
	@python3 $(abspath f4pga)/create_fasm_stub.py $(abspath $(FASM_STUB))
	@echo "[VENDOR] FASM stub created"

# ── FPGA flow ──────────────────────────────────────────────────────────

f4pga: f4pga_bitstream
	@echo "[F4PGA] FPGA flow complete. Use 'make f4pga_program' to program."

f4pga_synth: $(TARGET_DIR)/Soc.sv
	mkdir -p $(F4PGA_DIR)
	@echo "[F4PGA] Synthesizing $(F4PGA_DEVICE):$(F4PGA_PART) top=Soc ..."
	$(if $(call f4pga_tool,yosys), \
		$(call f4pga_tool,yosys) \
			-p "synth_xilinx -flatten -abc9 -arch xc7 -top Soc; \
				delete {t:\$$scopeinfo}; \
				opt_expr -keepdc; opt_clean -purge; \
				write_json $(F4PGA_JSON)" $< \
			-l $(F4PGA_DIR)/synth.log, \
		$(error yosys not found. Install Yosys or run 'make vendor-f4pga'))
	@echo "[F4PGA] Synthesis done."

f4pga_pnr: $(F4PGA_JSON)
	@echo "[F4PGA] Place-and-route ..."
	$(if $(call f4pga_tool,nextpnr-xilinx), \
		CHPDB=$$(find $(VENDOR_DIR) -name '$(F4PGA_PART).chipdb' 2>/dev/null | head -1) && \
		if [ -z "$$CHPDB" ]; then \
			echo "[F4PGA] ERROR: chipdb for $(F4PGA_PART) not found."; \
			echo "  Build nextpnr-xilinx with 'make vendor-f4pga'."; \
			exit 1; \
		fi && \
		$(call f4pga_tool,nextpnr-xilinx) --chipdb $$CHPDB \
			--json $< --xdc $(F4PGA_XDC) \
			--write $(F4PGA_DIR)/Soc.pnr --fasm $(F4PGA_FASM), \
		$(error nextpnr-xilinx not found. Run 'make vendor-f4pga' first))
	@echo "[F4PGA] PnR done."

f4pga_bitstream: $(F4PGA_FASM) $(XC7FRAMES2BIT) $(FASM_STUB)
	@echo "[F4PGA] Assembling bitstream ($(F4PGA_PART)) ..."
	PYTHONPATH=$(abspath $(FASM_STUB)):$(abspath $(PRJXRAY_SRC)):$$PYTHONPATH \
		python3 $(abspath $(PRJXRAY_SRC))/utils/fasm2frames.py \
			--db-root $(abspath $(F4PGA_DB))/$(F4PGA_DEVICE) \
			--part $(F4PGA_PART) \
			--sparse \
			$(abspath $(F4PGA_FASM)) $(abspath $(F4PGA_FRM))
	$(abspath $(XC7FRAMES2BIT)) \
		--frm_file $(abspath $(F4PGA_FRM)) \
		--output_file $(abspath $(F4PGA_BIT)) \
		--part_name $(F4PGA_PART) \
		--part_file $(abspath $(F4PGA_DB))/$(F4PGA_DEVICE)/$(F4PGA_PART)/part.yaml
	@echo "[F4PGA] Bitstream: $(F4PGA_BIT)"

f4pga_program: $(F4PGA_BIT)
	@echo "[F4PGA] Programming Basys3 ..."
	$(if $(call f4pga_tool,openFPGALoader), \
		$(call f4pga_tool,openFPGALoader) -b basys3 $<, \
		$(error openFPGALoader not found. Run 'make vendor-f4pga' or install manually))
	@echo "[F4PGA] Done."

clean:
	rm -rf $(TARGET_DIR) $(BUILD_DIR) $(F4PGA_DIR) $(VENDOR_DIR) target/sim target/formal
	rm -f examples/*.hex
	rm -rf /tmp/fasm_stub
