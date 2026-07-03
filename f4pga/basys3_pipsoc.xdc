## Basys3 pin constraints for 16-bit RISC PipSoc (pipelined core)
##
## Top module: PipSoc
##   Ports: clk, resetn, io_uartTx, io_uartRx,
##          io_dbgState[2:0], io_dbgRunning, io_dbgPc[15:0]
##
## NOTE: resetn is active-LOW. The Basys3 center button (U18) is
## active-HIGH.  To use the button as reset, invert externally or
## use the FPGA's internal POR by tying resetn high.  The default
## below leaves resetn unassigned (internal POR).

# ── 100 MHz system clock ──────────────────────────────────────────────
set_property PACKAGE_PIN W5   [get_ports clk]
set_property IOSTANDARD LVCMOS33 [get_ports clk]
create_clock -period 10.000 -name sys_clk -waveform {0.000 5.000} [get_ports clk]

# ── Reset (center button, active-LOW; button is active-HIGH,
#    so pullup external or use internal POR.  IOSTANDARD required
#    by nextpnr even if unconnected.) ──────────────────────────────────
set_property IOSTANDARD LVCMOS33 [get_ports resetn]

# ── UART (USB-UART bridge, FTDI FT2232) ───────────────────────────────
set_property PACKAGE_PIN A18  [get_ports io_uartRx]
set_property IOSTANDARD LVCMOS33 [get_ports io_uartRx]
set_property PACKAGE_PIN B18  [get_ports io_uartTx]
set_property IOSTANDARD LVCMOS33 [get_ports io_uartTx]

# ── Debug: Pipeline State (LD0-LD2) ────────────────────────────────────
set_property PACKAGE_PIN W4  [get_ports {io_dbgState[0]}]
set_property IOSTANDARD LVCMOS33 [get_ports {io_dbgState[0]}]
set_property PACKAGE_PIN V4  [get_ports {io_dbgState[1]}]
set_property IOSTANDARD LVCMOS33 [get_ports {io_dbgState[1]}]
set_property PACKAGE_PIN U2  [get_ports {io_dbgState[2]}]
set_property IOSTANDARD LVCMOS33 [get_ports {io_dbgState[2]}]

# ── Debug: Running (LD3) ───────────────────────────────────────────────
set_property PACKAGE_PIN U3  [get_ports io_dbgRunning]
set_property IOSTANDARD LVCMOS33 [get_ports io_dbgRunning]

# ── Debug: Program Counter bits on LEDs (LD4-LD15) ─────────────────────
# NOTE: LD7 (V1) and LD12 (K1) are not I/O pins in package CPG236
# per prjxray database.  Pins below skip those two.
set_property PACKAGE_PIN V2  [get_ports {io_dbgPc[0]}]
set_property IOSTANDARD LVCMOS33 [get_ports {io_dbgPc[0]}]
set_property PACKAGE_PIN W3  [get_ports {io_dbgPc[1]}]
set_property IOSTANDARD LVCMOS33 [get_ports {io_dbgPc[1]}]
set_property PACKAGE_PIN W2  [get_ports {io_dbgPc[2]}]
set_property IOSTANDARD LVCMOS33 [get_ports {io_dbgPc[2]}]
set_property PACKAGE_PIN M1  [get_ports {io_dbgPc[3]}]
set_property IOSTANDARD LVCMOS33 [get_ports {io_dbgPc[3]}]
set_property PACKAGE_PIN L2  [get_ports {io_dbgPc[4]}]
set_property IOSTANDARD LVCMOS33 [get_ports {io_dbgPc[4]}]
set_property PACKAGE_PIN L1  [get_ports {io_dbgPc[5]}]
set_property IOSTANDARD LVCMOS33 [get_ports {io_dbgPc[5]}]
set_property PACKAGE_PIN K2  [get_ports {io_dbgPc[6]}]
set_property IOSTANDARD LVCMOS33 [get_ports {io_dbgPc[6]}]
set_property PACKAGE_PIN H2  [get_ports {io_dbgPc[7]}]
set_property IOSTANDARD LVCMOS33 [get_ports {io_dbgPc[7]}]
set_property PACKAGE_PIN H1  [get_ports {io_dbgPc[8]}]
set_property IOSTANDARD LVCMOS33 [get_ports {io_dbgPc[8]}]
set_property PACKAGE_PIN J3  [get_ports {io_dbgPc[9]}]
set_property IOSTANDARD LVCMOS33 [get_ports {io_dbgPc[9]}]

# PC[15:10] are unassigned — still need IOSTANDARD to placate nextpnr
set_property IOSTANDARD LVCMOS33 [get_ports {io_dbgPc[10]}]
set_property IOSTANDARD LVCMOS33 [get_ports {io_dbgPc[11]}]
set_property IOSTANDARD LVCMOS33 [get_ports {io_dbgPc[12]}]
set_property IOSTANDARD LVCMOS33 [get_ports {io_dbgPc[13]}]
set_property IOSTANDARD LVCMOS33 [get_ports {io_dbgPc[14]}]
set_property IOSTANDARD LVCMOS33 [get_ports {io_dbgPc[15]}]
