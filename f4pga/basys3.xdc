## Basys3 pin constraints for 16-bit RISC SoC
##
## Top module: Soc
##   Ports: clk, resetn, io_uartTx, io_uartRx
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
