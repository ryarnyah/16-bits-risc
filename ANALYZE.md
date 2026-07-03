# PipCore Analysis — 16-bit RISC Pipelined Core (SpinalHDL)

## Architecture Overview

PipCore is a 5-stage pipelined RISC core: **IF → ID → EX → MEM → WB**. MEM is handled by an explicit multi-cycle FSM for LD; ST issues a single data-bus request from EX.

### Top-Level I/O

| Signal | Dir | Width | Description |
|--------|-----|-------|-------------|
| `bus.cmd` | Slave | Stream(Bits(8)) | Debug/control command bytes |
| `bus.rsp` | Master | Stream(Bits(8)) | Debug/control response bytes |
| `bus.ack` | Out | Bool | Debug transaction complete |
| `instrAddr` | Out | UInt(16) | Current PC for instruction fetch |
| `instrRsp` | Slave | Stream(Bits(16)) | Instruction data from memory |
| `dataBus.req` | Master | Stream(DataBusReq) | LD/ST memory request |
| `dataBus.rsp` | Slave | Stream(Bits(16)) | LD read data response |
| `dbg*` | Out | various | Debug observation signals |

### Sub-Components

- **Decoder**: Opcode→control signals (isALU, isLD, isST, isJMP, isBranch, isLDI, hasRd, etc.)
- **RegFile**: 8×16-bit register file (R0 hardwired to 0), 2R1W + 1 aux debug port
- **ALU**: Combinational ALU (ADD, XOR, SUB, AND, OR, SLL, SRL)
- **BusInterface**: 8-bit Stream→32-bit command assembly, 32-bit→8-bit response, ack gen.

---

## Pipeline Stages

### 1. IF (Instruction Fetch)

**Registers**: `pc`, `ldiPending`, `ldiHeader`, `ldiHeaderPc`

**Combinational**: `io.instrAddr := pc`

**Stall**: `instrRsp.ready := !stallIF` — stalls when ID is stalled or branch/JMP in ID.

**LDI handling** (2-word instruction):
- Cycle 1: `ldiPending=False`, first word arrives → `ldiPending=True`, store header+PC, advance PC, **clear vID**
- Cycle 2: `ldiPending=True`, second word arrives → `rID_instr=ldiHeader`, `rID_ldiData=data`, `vID=True`, pc+=2, `ldiPending=False`

**Branch redirect**: When `exBrTaken`:
- Incoming instruction response discarded (stale in-flight data)
- `ldiPending := False`
- `vID := False`

**IF→ID clearing**: `when(!stallIF && !instrRsp.fire && !ldiPending) { vID := False }` — happens when IF is stalled but not in LDI.

---

### 2. ID (Instruction Decode)

**Pipeline registers**: `rID_instr`, `rID_pc`, `rID_ldiData`, `vID`

**Decoder**: `decoder.io.instr := rID_instr`

**Register file read address muxing**:
- `idRsAddr` — normal: instr[8:6] (Rs). Branch/JMP: instr[11:9] (Rd field encodes first compare reg)
- `idRtAddr` — normal: instr[5:3] (Rt). ST: rdField instr[11:9] (data register). Branch: instr[8:6] (second compare reg)

**Forwarding for ID addr computation** (priority order):
1. WB forwarding: `fwdFromWb && rWB_rd === idRsAddr`
2. LD data forwarding: `ldRspPending && ldRd === idRsAddr && ldRd =/= 0`
3. EX ALU forwarding: `rEX_type === ALU && rEX_hasRd && rEX_rd === idRsAddr && rEX_rd =/= 0`
4. EX LDI forwarding: `rEX_type === LDI && rEX_hasRd && rEX_rd === idRsAddr && rEX_rd =/= 0`
5. Fallback: regFile.io.rsVal

**Computed values**:
- `idEffAddr = idFwdRsVal + sext(imm6)` — LD/ST effective address
- `idBrTarget = rID_pc + 2 + sext(imm6) << 1` — branch target

**ID→EX transfer**: `when(!stallID && !exBrTaken) { when(vID) { transfer... } otherwise { rEX_type := EMPTY }`
On `exBrTaken`: `rEX_type := EMPTY` (flush).

**Stall signals in ID**:
- `stallBrId = vID && (decoder.io.isBranch || decoder.io.isJMP)` — stalls IF only
- `loadUseHazard` — see §Stall
- `ldWaitStall` — see §Stall
- `ldActiveStall` — see §Stall
- `stallID = loadUseHazard || ldWaitStall || ldActiveStall`
- `stallIF = stallID || stallBrId`

---

### 3. EX (Execute)

**Pipeline registers**: `rEX_instr`, `rEX_pc`, `rEX_rd`, `rEX_hasRd`, `rEX_rsVal`, `rEX_rtVal`, `rEX_sext`, `rEX_effAddr`, `rEX_brTarget`, `rEX_ldiData`, `rEX_type`, `rEX_aluFunc`

**Computed from `rEX_instr`**:
- `rEX_isBEQ` = opcode == 0xC
- `rEX_isBNE` = opcode == 0xD
- `rEX_isBLT` = opcode == 0xE
- `exIsImmEn` = opcode == 0x1 (ADDI) || opcode == 0x3 (XORI)

**Forwarding for EX operands** (priority order):
- `exFwdRsVal`:
  1. WB: `fwdFromWb && rWB_rd === exRsAddr`
  2. LD data: `ldRspPending && ldRd === exRsAddr`
  3. Fallback: `rEX_rsVal`
- `exFwdRtVal` (same pattern for Rt)

**NOTE**: ID-stage LD-data forwarding checks `ldRd =/= 0`; EX-stage LD-data forwarding does NOT.

**ALU**: `rsVal := exFwdRsVal`, `opB := Mux(exIsImmEn, rEX_sext, exFwdRtVal)`

**EX result**: `Mux(rEX_type === LDI, rEX_ldiData, Mux(rEX_type === ALU || exIsImmEn, alu.io.result, 0))`

**Data bus request** (combinational):
- `addr := rEX_effAddr`, `wrData := exFwdRtVal`, `wr := (rEX_type === ST)`
- `valid := (rEX_type === LD && !ldRspPending) || (rEX_type === ST)`

**Branch condition** (`exBrTaken`):
- JMP: unconditional
- BEQ: `exFwdRsVal === exFwdRtVal`
- BNE: `exFwdRsVal =/= exFwdRtVal`
- BLT: `exFwdRsVal.asSInt < exFwdRtVal.asSInt`

**Branch/JMP target**:
- JMP: `pc := exFwdRsVal.asUInt`
- BR: `pc := rEX_brTarget.asUInt`

---

### 4. MEM (Load Data)

**Registers**: `ldState` (LdPhase: IDLE, WAIT_BUS, DATA_READY), `ldRd`, `ldData`

**LD State Machine**:

| Transition | Condition | Action |
|------------|-----------|--------|
| IDLE → WAIT_BUS | `rEX_type===LD && dataBus.req.fire` | `ldRd := rEX_rd`, `ldState := WAIT_BUS` |
| WAIT_BUS → DATA_READY | `dataBus.rsp.fire` | `ldData := rsp.payload`, `ldState := DATA_READY` |
| DATA_READY → IDLE | unconditional (next cycle) | `ldState := IDLE` |

**Combinational**:
- `ldPending = ldState === WAIT_BUS`
- `ldRspPending = ldState === DATA_READY`
- `io.dataBus.rsp.ready := ldPending`

---

### 5. WB (Writeback)

**Pipeline registers**: `rWB_rd`, `rWB_hasRd`, `rWB_result`, `vWB`

**EX→WB transfer order**: Must precede ID→EX so EX→WB sees old rEX_* before ID→EX overwrites them. In code: EX→WB (line ~291), then ID→EX (line ~328).

**Non-LD/ST**:
```
when(rEX_type !== EMPTY && !== LD && !== ST && !exBrTaken && !stallID)
  rWB_rd := rEX_rd; rWB_hasRd := rEX_hasRd
  rWB_result := exResult; vWB := True
```

**LD**:
```
when(ldRspPending && rEX_type === LD)
  rWB_rd := ldRd; rWB_hasRd := True
  rWB_result := ldData; vWB := True
```

**Register file write**:
`wrEn := vWB && rWB_hasRd && rWB_rd =/= 0`

`fwdFromWb = rWB_hasRd && rWB_rd =/= 0` — WB forwarding is used by both ID and EX.

---

## Stall and Hazard Logic — Detailed Conditions

### `loadUseHazard` — EX has LD, ID needs same register, data not yet available

```
loadUseHazard = rEX_type === LD
             && !ldRspPending
             && rEX_hasRd && rEX_rd =/= 0
             && ( (idNeedsRs && idRsAddr === rEX_rd)
                || (idNeedsRt && idRtAddr === rEX_rd) )
```

Where:
```
idNeedsRs = vID && !decoder.io.isLDI
idNeedsRt = vID && (decoder.io.isALU || decoder.io.isST || decoder.io.isBranch)
```

NOTE: `idNeedsRt` does NOT exclude immediate-format ALU (ADDI/XORI, `isImmEn`), even though these instructions don't use the Rt field (Rt overlaps with imm6). This can create **false-positive** load-use hazard detections when `idRtAddr` (derived from the immediate encoding) happens to match `rEX_rd`.

### `ldWaitStall` — EX has LD in WAIT_BUS phase

```
ldWaitStall = rEX_type === LD && ldPending && !ldRspPending
```

### `ldActiveStall` — DATA_READY (or entering it) and ID has another LD

```
ldRespNow = ldState === WAIT_BUS && dataBus.rsp.fire
ldActiveStall = (ldRspPending || ldRespNow) && vID && decoder.io.isLD
```

Prevents a second LD from entering EX while the first LD's data is in DATA_READY.

### `stallBrId` — Branch/JMP in ID

```
stallBrId = vID && (decoder.io.isBranch || decoder.io.isJMP)
```

Prevents speculative sequential fetch while a branch/JMP is in ID.

### Derived stalls

```
stallID = loadUseHazard || ldWaitStall || ldActiveStall
stallIF = stallID || stallBrId
```

---

## Forwarding Paths Summary

| Source | Destination | Condition | R0 guard? |
|--------|------------|-----------|-----------|
| WB result | ID addr comp | `fwdFromWb && rWB_rd === idRsAddr` | Yes (built into fwdFromWb) |
| LD data (DATA_READY) | ID addr comp | `ldRspPending && ldRd === idRsAddr && ldRd =/= 0` | **Yes** |
| EX ALU result | ID addr comp | `rEX_type===ALU && rEX_hasRd && rEX_rd===idRsAddr && rEX_rd=/=0` | Yes |
| EX LDI data | ID addr comp | `rEX_type===LDI && rEX_hasRd && rEX_rd===idRsAddr && rEX_rd=/=0` | Yes |
| WB result | EX operand | `fwdFromWb && rWB_rd === exRsAddr` (same for Rt) | Yes (built into fwdFromWb) |
| **LD data (DATA_READY)** | **EX operand** | **`ldRspPending && ldRd === exRsAddr` (same for Rt)** | **NO** (`ldRd =/= 0` missing) |

---

## Debug Bus Commands

| Cmd | Action |
|-----|--------|
| 0x03 | Flush pipeline (`ldiPending=False, vID=False, rEX_type=EMPTY, vWB=False, cmdDone=True`) |
| 0x04 | Flush pipeline (same) |
| 0x05 | Flush pipeline (same) |
| 0x06 | Read register file via aux port (register selected by bits [18:16]) |
| 0x08 | Read PC |

Default for `busIf.io.cmdDone := False` (line 397), then overridden by flsPipeline().

---

## Reset Behavior

- Synchronous reset, active LOW
- `pc`=0, `vID`=False, `rEX_type`=EMPTY, `vWB`=False, `ldiPending`=False, `ldState`=IDLE

---

## Formal Verification Coverage (lines 443–681)

- PC progression (stalled/branch cases)
- R0 always reads 0 (assert check), never written
- Data bus req properties (ST writes, LD reads, addr matches)
- EX result correctness (LDI uses ldiData)
- Pipeline progression ID→EX, EX→WB
- LD state machine (data capture, writeback on DATA_READY)
- Per-instruction correctness (ALU, imm ALU, LDI, LD, ST, JMP, branch)
- Temporal: EX→WB matches past(rEX_*)
- Temporal: WB→regfile matches rWB_*
- Temporal: PC update after branch/JMP
- Temporal: Branch clears vID, flushes pipeline
- Temporal: ldRd captures rEX_rd on req.fire
- Coverage for all instruction types, hazards, ALU funcs, branch conditions

**NOTE**: Formal verification does NOT check ldRd =/= 0 in EX forwarding.

---

## Code Execution Order (SpinalHDL `when` block sequence)

The statements in PipCore execute in this order (line reference):
1. LD State Machine transitions (lines 143–165)
2. EX→WB transfer (lines 290–316)
3. Branch target update (lines 321–325)
4. ID→EX transfer (lines 328–375)

This order is critical: EX→WB reads old rEX_* values, then ID→EX overwrites them with new values.

---

## Bug Check Results

### Bug #1 (Functional) — EX LD-data forwarding missing R0 guard

**Location**: `exFwdRsVal` / `exFwdRtVal` forwarding in EX stage.

**Evidence from analysis**:
- Forwarding Table: "LD data (DATA_READY) → EX operand" condition is `ldRspPending && ldRd === exRsAddr` (same for Rt) — **NO `ldRd =/= 0` check**
- Compare: All other LD-data forwarding paths (ID addr comp) include `&& ldRd =/= 0`
- Formal verification: "Does NOT check ldRd =/= 0 in EX forwarding" (line 273)

**Description**: When an `LD R0, [Rx]` instruction is in DATA_READY, the loaded data in `ldData` is forwarded to any subsequent EX-stage instruction reading R0 via `exFwdRsVal` or `exFwdRtVal`. Since R0 is hardwired to 0 and writes to address 0 are silently ignored by the regfile, the loaded data should NOT be forwarded — R0 must always read as 0. But the EX forwarding bypasses the regfile without checking `ldRd =/= 0`.

**Trigger sequence**:
```
LD R0, [R1]    ; Load to R0 (no-op), ldRd = 0
ADD R2, R0, R3 ; Should compute R0(0)+R3, but gets ldData+R3
```

### Bug #2 (Performance) — False load-use hazard for ADDI/XORI

**Location**: `idNeedsRt` in loadUseHazard (line 186).

**Evidence from analysis**:
- `idNeedsRt = vID && (decoder.io.isALU || decoder.io.isST || decoder.io.isBranch)` — does NOT exclude immediate ALU
- Note at line 189: "NOTE: `idNeedsRt` does NOT exclude immediate-format ALU (ADDI/XORI, `isImmEn`)"

**Description**: For ADDI/XORI, the Rt field (instr[5:3]) is part of the immediate encoding, not a register address. But `idNeedsRt` is True for all ALU instructions, including immediates. If the immediate's upper bits happen to encode a register number matching `rEX_rd` (from a LD in EX), a false load-use hazard fires, stalling the pipeline unnecessarily. The instruction doesn't actually use Rt.

**Impact**: Performance bubble only. Masked by `ldWaitStall` during WAIT_BUS phase. No functional incorrectness.
