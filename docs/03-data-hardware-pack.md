# 03 — Data & Hardware Pack

Covers the data dictionary, data provenance, the MQTT protocol, simulator
behaviour, and the physical sensor node.

---

## 1. Data Dictionary

### Readings — `SensorReading` (metric stream)

| Field | Type | Unit | Source | Notes |
|---|---|---|---|---|
| `roomId` | string | — | topic | e.g. `room101` |
| `metric` | enum | — | topic suffix | `co2` \| `pm25` \| `pm10` \| `occupancy` |
| `value` | double | per metric | device/simulator | see units below |
| `unit` | string | per metric | device/simulator | `ppm`, `ugm3`, `people` |
| `recordedAt` | Instant ISO-8601 UTC | — | device OR backend | if missing, backend stamps receipt time (D3) |

Metric units:
- `co2` → ppm (0–5000 typical; MH-Z19B range 400–5000)
- `pm25` → µg/m³ (PMS5003)
- `pm10` → µg/m³ (PMS5003)
- `occupancy` → integer people (from override or future PIR)

### Derived values (not raw)

| Field | Derivation |
|---|---|
| Room `status` | `OK`/`WARNING`/`ALERT` from occupancy-aware CO2 model + fixed PM2.5 bands (`RoomTwinService.statusOf`) |
| Recommendation | rule-based text from status + ventilation state |
| Projection | minutes until CO2 reaches a target using the well-mixed box model |
| Daily summary | per-room/daily: peak, avg, minutes above threshold, ventilation-on time |

---

## 2. Data Provenance

| Data item | Origin | Trust | Persisted | Auditable |
|---|---|---|---|---|
| CO2 / PM readings | ESP32 or simulator | Medium (calibration offsets logged) | `sensor_readings` | replay |
| Alert events | Backend derivation on ingest | High | `alert_event` | timestamped |
| Ventilation actions | Human (FM) via API with `actor` | High | `ventilation_action` | actor + ts + note |
| Occupancy | Manual override (`occupants`) | Medium | `occupancy_snapshot` + live state | source + ts |
| Calibration | Admin via API with `calibratedBy` | High | `calibration_profile` | who/what/when |
| Daily summary | Backend job (rollup of readings) | High | `daily_exposure_summary` | recomputable |

Every record that a human can change (ventilation action, occupancy override,
calibration) captures **who**, **what**, and **when**.

---

## 3. MQTT Protocol Contract (D3)

```
Topic:   sensors/{roomId}/{metric}      metric ∈ co2 | pm25 | pm10 | occupancy
Payload: {"value": <number>, "unit": "<string>", "ts": "<ISO-8601 UTC>"}
```

- `ts` optional; backend stamps receipt time if absent.
- Malformed messages are logged and dropped (robustness evidence).
- Broker: Mosquitto (`mosquitto/config/mosquitto.conf`), dev = anonymous
  localhost only (D4).

---

## 4. Simulator Behaviour

Two interchangeable sources behind the `SensorDataSource` seam:

1. **Java `SimulatorFeed`** (default, `sensor.mode=simulator`) — generates a
   realistic CO2 story (occupancy ramp → CO2 climb → ventilation → decay) using
   an exponential decay model. Publishes to the same MQTT topics, or can be
   driven directly. Model-time acceleration lets a 30-minute story play out in
   ~2 real minutes for demos.
   - interval 5000 ms, time-factor 5, capacity 8, outdoor 420 ppm,
     infiltration ACH 0.5, ventilation ACH 6.0.
2. **Standalone Python `simulator/simulator.py`** — external MQTT publisher for
   load tests / cross-machine setups. Backend runs with `sensor.mode=hardware`.

**Hardware swap:** flash ESP32 → `sensor.mode=hardware` → done. No downstream
changes (D2).

---

## 5. Physical Sensor Node

### Bill of materials (core, ~₹3,900)

| Component | Spec | Cost (₹) | Notes |
|---|---|---|---|
| ESP32 DevKit-C | WiFi + dual UART | 600 | USB on board, no adapter needed |
| MH-Z19B | NDIR CO2 400–5000 ppm | 1500 | 5 V supply; needs ~5 s warm-up between reads |
| PMS5003 | PM2.5/PM10 laser | 1000 | 5 V; 32-byte frames @1 Hz |
| 5 V/2 A USB charger + cable | power | 400 | powers ESP + both sensors |
| Jumper wires ×2 packs | wiring | 250 | breadboard proto |
| Breadboard | proto | 150 | |

Optional demo extras: DHT22 (temp/humidity, ₹300), OLED 0.96" (₹300),
enclosure (₹400) — total optional ~₹1,000.

### Wiring summary (`esp32/src/main.cpp`)

| Sensor | Pin on sensor | ESP32 pin |
|---|---|---|
| MH-Z19B | VCC | 5 V |
| MH-Z19B | GND | GND |
| MH-Z19B | TX | RX2 (GPIO16) |
| MH-Z19B | RX | TX2 (GPIO17) via 1k for level safety |
| PMS5003 | VCC | 5 V |
| PMS5003 | GND | GND |
| PMS5003 | TX | GPIO5 (SoftwareSerial RX) |

### Firmware facts

- CO2 read via 9-byte command `FF 01 86 00 00 00 00 00 79`; checksum-validated.
- PMS5003 frame parse searches for `42 4D` start marker; PM2.5/PM10 extracted.
- Publishes JSON to `sensors/{roomId}/co2` and `sensors/{roomId}/pm25` every 5 s.
- Dates from NTP (`pool.ntp.org`); falls back to millis timestamp if no time.
- Reconnects WiFi/MQTT automatically.

### Sensor accuracy & limitations

| Sensor | Accuracy | Limitation | Calibration hook |
|---|---|---|---|
| MH-Z19B | ±50 ppm + 5% reading | Inaccurate after long indoor exposure → auto-calibrate against outdoor occasionally | offset + scale saved per room/metric |
| PMS5003 | ±15% typical | CF=1 values vs gravimetric | offset from a reference |

The calibration screen lets an ADMIN record an offset+scale correction and a
note per room/metric, preserving provenance.
