# Air Quality Digital Twin

T.Y. B.Sc IT capstone: live digital twin of indoor air quality — CO2 with
temperature & humidity monitoring, threshold alerts, ventilation audit trail,
event replay, and what-if simulation. ESP32 + MQ-135 + DHT11 → MQTT →
Spring Boot → PostgreSQL → React.

## Architecture

```
ESP32 (MQ-135 + DHT11) ──MQTT──▶ Mosquitto broker (1884)
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
| `esp32/` | ESP32 firmware (PlatformIO) — reads MQ-135 + DHT11, publishes CO2, temperature, humidity |
| `simulator/` | Standalone Python MQTT simulator |
| `scripts/` | start-all.sh / stop-all.sh / run.sh |
| `docs/` | Decisions, design pack, evaluation dossier |

## Pages/ports

- Backend **18080** (health: `/actuator/health`)
- Frontend **5173**
- Mosquitto **1884**
- PostgreSQL **5433** (db `airquality`, user `aq_user`)

## ESP32 sensor

Wiring: MQ-135 — VCC→5V, GND→GND, AO→GPIO34, DO→not connected;
DHT11 — VCC→3.3V, DATA→GPIO4, GND→GND.

Metrics published every **2s**:

| Topic | Metric | Use |
|---|---|---|
| `sensors/{room}/co2` | CO₂ (ppm) | Alerting (1000/800 thresholds) |
| `sensors/{room}/temperature` | Temperature (°C) | Dashboard display |
| `sensors/{room}/humidity` | Humidity (%) | Dashboard display |

Firmware: 2-min warm-up, 20-sample averaging, median-of-5 + ±120 ppm slew
smoothing, baseline self-heal.

⚠️ `esp32/src/main.cpp` me WiFi SSID/password/broker IP ki jagah
placeholders hain — flash se pehle apne values bharna.

## Tests

```bash
cd backend && ./mvnw test
```