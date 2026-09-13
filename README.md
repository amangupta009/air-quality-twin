# Air Quality Digital Twin

T.Y. B.Sc IT capstone: live CO2 digital twin of indoor air quality with
threshold alerts, ventilation audit trail, event replay, and what-if
simulation. ESP32 + MQ-135 sensor → MQTT → Spring Boot → PostgreSQL → React.

## Architecture

```
ESP32 (MQ-135) ──MQTT──▶ Mosquitto broker (1884)
                              │  subscribe sensors/+/+
                              ▼
                      Spring Boot backend (18080)
                 calibrate → twin update → thresholds
                              │
            PostgreSQL (5433) ◀ write │ read ▶ REST /api/*
                              │
                      React dashboard (5173)
```

## Quickstart

```bash
bash run.sh                 # starts Postgres + Mosquitto + backend + frontend
```

Then open **http://localhost:5173**.

Logins: `admin/admin123` (ADMIN), `manager/manager123` (FM), `viewer/viewer123` (VIEWER).

Stop: `bash scripts/stop-all.sh`

## Layout

| Path | What it is |
|---|---|
| `backend/` | Spring Boot (Java 21) API, MQTT ingestion, digital twin |
| `frontend/` | React + Vite dashboard |
| `esp32/` | ESP32 firmware (PlatformIO) — reads MQ-135, publishes CO2 |
| `simulator/` | Standalone Python MQTT simulator |
| `scripts/` | start-all.sh / stop-all.sh / run.sh |
| `docs/` | Decisions, design pack, evaluation dossier |

## Pages/ports

- Backend **18080** (health: `/actuator/health`)
- Frontend **5173**
- Mosquitto **1884**
- PostgreSQL **5433** (db `airquality`, user `aq_user`)

## ESP32 sensor

Wiring: VCC→3.3V, GND→GND, AO→GPIO34, DO→not connected.
Firmware: 2-min warm-up, 20-sample averaging, 5s publish to `sensors/{room}/co2`.

⚠️ `esp32/src/main.cpp` me WiFi SSID/password/broker IP ki jagah
placeholders hain — flash se pehle apne values bharna.

## Tests

```bash
cd backend && ./mvnw test
```