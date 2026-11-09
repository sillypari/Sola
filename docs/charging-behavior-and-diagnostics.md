# Ola MoveOS: Charging Behavior Under Active UDS Diagnostic Sessions

## 1. Overview & Problem Statement

When connecting diagnostic software (such as the official Ola Diagnostic app or third-party diagnostic tooling like Sola) to an Ola S1 electric scooter, active high-voltage charging halts immediately. The battery pack stops drawing current, and the charger transitions into an idle or standby state.

This behavior is not an application bug or communication collision. It is an intentional, firmware-level safety interlock implemented inside the Ola Battery Management System (BMS) and Vehicle Control Unit (VCU) in compliance with automotive safety standards (ISO 14229 and ISO 26262 ASIL-D).

---

## 2. System Architecture & Communication Topology

The scooter architecture separates telematics and user interface from powertrain and energy control:

```
+------------------------------------------------------------------+
|                    HEAD UNIT / SOM (Android)                     |
|  - Diagnostic gRPC Server (TCP port 50005)                       |
|  - SocketCAN Interface (vcan0 / can0)                            |
+---------------------------------+--------------------------------+
                                  |
                   Internal High-Speed CAN Bus
                                  |
       +--------------------------+-------------------------+
       |                          |                         |
+------v-----+             +------v-----+            +------v-----+
|    VCU     |             |    BMS     |            |  CHARGER   |
| CAN: 0x786 |             | CAN: 0x78E |            | (OBC/Ext)  |
+------------+             +------------+            +------------+
```

During normal charging:
1. The physical charger is connected to the vehicle charging port.
2. The charger negotiates with the BMS over CAN / pilot lines.
3. The BMS controls the high-voltage charging contactor / solid-state FETs and continuously dictates the maximum permissible charging current (`Overall_Charge_Current_Limit` on CAN ID `0x121`).
4. The charger supplies current up to the exact limit commanded by the BMS.

---

## 3. The Deep-Dive Mechanism: What Triggers the Cutoff

### Step 1: Client Issues UDS Extended Diagnostic Session

When a diagnostic client initializes or attempts to keep ECUs awake, it sends an ISO 14229 Unified Diagnostic Services (UDS) request:

* **Target ECU:** BMS (`0x78E` physical request CAN ID)
* **UDS Frame:** `02 10 03 00 00 00 00 00`
  * `02`: Single Frame length (2 data bytes follow)
  * `10`: Service Identifier (SID) - `DiagnosticSessionControl`
  * `03`: Sub-function - `extendedDiagnosticSession`

### Step 2: BMS Operational State Machine Transition

The BMS firmware runs a deterministic finite state machine with defined operational modes:
* `STATE_INIT`
* `STATE_STANDBY`
* `STATE_DRIVE`
* `STATE_CHARGING`
* `STATE_FAULT`
* `STATE_DIAGNOSTIC` (or `STATE_WORKSHOP`)

Upon receiving `10 03` on `0x78E`, the BMS transitions immediately out of `STATE_CHARGING` into `STATE_DIAGNOSTIC`.

```
               [ STATE_CHARGING ]
             (Pack charging normally)
                       |
                       |  Received UDS 0x10 0x03 on CAN 0x78E
                       v
              [ STATE_DIAGNOSTIC ]
    -------------------------------------------
    - Charge current demand set to 0.0 A
    - Charge contactor / FET opened
    - High-voltage loop decoupled from charger
```

### Step 3: Current Commanded to Zero & Loop Cutoff

As soon as `STATE_DIAGNOSTIC` is entered:
1. The BMS immediately broadcasts `Overall_Charge_Current_Limit = 0` on CAN ID `0x121`.
2. The BMS de-asserts the hardware charge enable / pilot line to the charger.
3. The external charger detects zero current demand from the BMS and ceases power delivery, falling back into idle / standby mode.

### Step 4: Indefinite Session Retention via Tester Present

ISO 14229 defines a standard session timeout timer (`S3Server`, typically 5000 ms). If no further diagnostic requests arrive within 5 seconds, an ECU must automatically drop back to Default Session (`10 01`) and resume normal vehicle state machine operations.

However, diagnostic clients send periodic keep-alive frames:

* **UDS Frame:** `02 3E 80 00 00 00 00 00`
  * `02`: Single Frame length (2 data bytes follow)
  * `3E`: Service Identifier (SID) - `TesterPresent`
  * `80`: Sub-function with MSB bit set (`suppressPosRspMsgIndicationBit = TRUE`)

When `3E 80` is transmitted at intervals faster than 5000 ms (e.g. every 2000 ms to 2500 ms), the `S3Server` timer is continually refreshed. The BMS is held in `STATE_DIAGNOSTIC` indefinitely, and charging remains blocked as long as the diagnostic connection is active.

### Step 5: Session Termination and Resumption

When the diagnostic connection is terminated:
1. The transmission of `3E 80` stops.
2. The BMS `S3Server` timer expires after 5000 ms.
3. The BMS transitions back from `STATE_DIAGNOSTIC` to `STATE_STANDBY`.
4. Detecting valid AC/DC charger pilot presence, the BMS re-enters `STATE_CHARGING`, performs pre-charge checks, closes contactors/FETs, and ramps charge current back up to nominal values.

---

## 4. Why the Official Ola Diagnostic App Behaves This Way

The official Ola Diagnostic application (`app_production_debug_215`) was developed specifically for workshop environments:
* **Workshop Bay Safety:** When an Authorized Service Center (ASC) technician connects a diagnostic tablet, the vehicle is assumed to be mounted on a service lift or undergoing physical inspection.
* **Actuator and Flash Testing:** Diagnostic routines include flashing microcontroller firmware (SOM, MCU, BMS, BCM), running actuator sweeps (horns, lights, steering locks), and measuring contactor impedances. Running high charging currents through the pack during these routines poses serious risks of electrical shock, thermal transients, and communication bus arbitration contention.
* **OEM Intent:** MoveOS firmware explicitly enforces that charging and extended diagnostic sessions are mutually exclusive.

---

## 5. Architectural Implications for Third-Party Developers

If you are developing telemetry monitors, dash HUDs, or companion apps for MoveOS:

### Case A: Passive Telemetry (Charging Monitored While In Progress)
If the goal is to monitor charging voltage, cell balancing delta, and SOC without interrupting charge flow:
* **Do NOT send `10 03` or `3E 80` to the BMS (`0x78E`).**
* When charging, the BMS and VCU are already awake and transmitting periodic broadcast frames over the CAN bus (`vcan0`).
* You can read all charging parameters passively by tapping the broadcast CAN messages:
  * `BMS00_Cloud_Data_1` (`0x65D`): Instantaneous pack voltage, current, and cell temperatures.
  * `BMS00_Cloud_Data_2` (`0x66D`): Cell series voltages (Cell 1 to Cell 14).
  * `BMS00_Parameter_1` (`0x11C`): Charging state, contactor status, and current limits.
  * `Overall_Battery_Current_Limit` (`0x121`): Current limits for charge and regen.

### Case B: Waking a Sleeping Scooter Without Halting the BMS
If the scooter is asleep / locked and needs to be woken up to inspect parameters:
* Transmit only the CAN Network Management (NM) Wakeup frame:
  ```bash
  cansend vcan0 003#0100000000000000
  ```
* If sending diagnostic session requests to wake up the gateway, address only the VCU (`0x786`) instead of the BMS (`0x78E`). Putting the VCU into diagnostic session does not directly force the BMS to decouple its charging loop.

---

## 6. CAN ID & UDS Reference Summary

| Identifier | Subsystem | Description | Interaction With Charging |
|:---|:---|:---|:---|
| `0x003` | CAN NM | Network Management Wakeup | Wakes up CAN transceivers; does NOT halt charging |
| `0x786` | VCU | Vehicle Control Unit UDS Request | Manages vehicle state; minimal impact on BMS charge loop |
| `0x78E` | BMS | Battery Management System UDS Request | **Halts charging** if Service `0x10 0x03` is sent |
| `0x7B0` | VCU | VCU UDS Response | Response channel from VCU |
| `0x7B1` | BMS | BMS UDS Response | Response channel from BMS |
| `0x11C` | BMS | `BMS00_Parameter_1` | Broadcasts live charging and contactor states |
| `0x121` | BMS | `Overall_Battery_Current_Limit` | Broadcasts max charge/discharge current limits |
| `0x411` | BMS | `BMS00_Fault_2` | Broadcasts operational fault flags (including `SOCRegenWarn`) |

