# Indoor Air Quality Digital Twin with Ventilation Recommendation Engine

T.Y. B.Sc IT capstone prototype: a live digital twin of indoor air quality
with threshold alerts, a ventilation audit trail, event replay, and
what-if scenario simulation.

Real-world data flows from an ESP32 + MQ-135 sensor station over MQTT into a
Spring Boot backend, which maintains an in-memory digital twin, persists every
reading to PostgreSQL, and pushes live status to a React dashboard. The whole
stack runs locally with no Docker and no root.

```
ESP32 (MQ-135) ──MQTT──▶ Mosquitto broker (1884)
                              │  subscribe sensors/+/+
                              ▼
                      Spring Boot backend
                 calibrate → twin update → threshold check
                              │
            PostgreSQL (5433) ◀ write │ read ▶ REST /api/*
                              │
                     WebSocket /ws (live push)
                              │
                      React dashboard (5173)
                              │
              "Ventilation ON" POST → audit log in DB
```

Architecture rules enforced by the code:
- The frontend never touches the database — only backend REST/WebSocket.
- The database never touches MQTT — the backend is the single gatekeeper.
- User actions return through `POST /api/rooms/{id}/ventilation` and are
  logged with who/when (audit trail).

## Features

- **Live CO₂ digital twin** — every reading updates an in-memory room state
  instantly; status (`SAFE` / `WARNING` / `ALERT`) is derived dynamically.
- **One-sensor mirroring** — a single physical sensor feeds every registered
  room, so any room you enter shows live data (single-hardware setup).
- **Threshold alerts** — CO₂ ≥ 1000 ppm opens an ALERT, < 900 ppm (hysteresis)
  resolves it; alerts are persisted and shown on the dashboard with a beep.
- **Occupancy-aware thresholds** — dynamic limits scale with head-count.
- **Ventilation audit trail** — every ON/OFF/ENTER action is logged with
  who/when (module 5).
- **Calibration** — per-room CO₂ calibration profiles applied at ingest.
- **What-if simulation** — projected minutes-to-threshold, ventilation ACH
  simulation in the frontend.
- **Stateless auth** — HMAC-signed login tokens, three roles, 24 h TTL.
- **Resilient pipeline** — broker down at startup retries in background;
  on reconnect the subscription is re-issued; malformed messages are skipped,
  never fatal.

## Repository layout

| Path | What it is |
|---|---|
| `backend/` | Spring Boot (Java 21) API, MQTT ingestion, digital twin, JPA entities |
| `frontend/` | React + Vite dashboard (TypeScript-free, plain JSX) |
| `esp32/`    | ESP32 firmware (PlatformIO): reads MQ-135, publishes CO₂ |
| `simulator/` | Standalone Python MQTT simulator (external publisher) |
| `scripts/`  | One-command start/stop for the full local stack |
| `docs/`     | Decision log, design pack, evaluation dossier, guides |

## Module coverage (7 mandatory)

1. **Sensor data ingest** — MQTT subscriber (`sensors/{roomId}/co2`) →
   calibrate → twin update.
2. **Threshold alerts** — rising edge opens alert, hysteresis closes it.
3. **Calibration** — ADMIN manages per-room calibration profiles.
4. **Occupancy control** — set head-count; thresholds adapt to it.
5. **Audit trail** — ventilation/entry actions logged with actor + timestamp.
6. **Dashboard** — live CO₂ tile, 5 s polling + WebSocket push, room cards.
7. **What-if / simulation** — ventilation ACH model & minutes-to-threshold.

## Quickstart (local dev, no Docker / no root)

Easiest — start everything (PostgreSQL + Mosquitto + backend + frontend):

```bash
bash run.sh                      # or: SENSOR_MODE=hardware bash scripts/start-all.sh
```

Then open **http://localhost:5173** and log in.

Default logins (dev only):

| Role            | Username | Password  |
|-----------------|----------|-----------|
| ADMIN           | admin    | admin123  |
| Facility Manager| manager  | manager123|
| Viewer          | viewer   | viewer123 |

Use the **simulator** (no real hardware):

```bash
bash run.sh --simulator
```

Use the **real ESP32 sensor station** (SENSOR_MODE=hardware is the default for
`run.sh`; the ESP32 publishes to MQTT and the backend just listens):

```bash
bash scripts/start-all.sh        # hardware mode (default)
```

Stop everything:

```bash
bash scripts/stop-all.sh
```

## Services & ports

| Service        | Port | Notes |
|----------------|------|-------|
| Backend (Spring)| **18080** | API + WebSocket + actuator health |
| Frontend (Vite) | **5173** | Dashboard, proxies `/api` + `/ws` → 18080 |
| Mosquitto       | **1884** | MQTT broker |
| PostgreSQL      | **5433** | DB `airquality` |

> 8080 is intentionally avoided so the stack never collides with other dev
> projects on the same machine.

Health check:

```bash
curl http://localhost:18080/actuator/health
```

Watch readings arrive from the real sensor (5 s cadence):

```bash
# via API
curl http://localhost:18080/api/rooms
# live feed
PGPASSWORD=aq_pass psql -h localhost -p 5433 -U aq_user -d airquality \
  -c "SELECT room_id, value, recorded_at FROM sensor_readings ORDER BY recorded_at DESC LIMIT 5;"
```

## ESP32 sensor station

Wiring (4-pin MQ-135 module):

| Module pin | ESP32 |
|-----------|-------|
| VCC | 3.3 V |
| GND | GND |
| AO  | GPIO34 (ADC1_CH6) |
| DO  | leave unconnected |

The firmware:
- warms up 2 minutes before trusting readings (`WARMUP_MS`),
- averages 20 ADC samples per read,
- keeps a slow-moving clean-air baseline and maps voltage → ppm estimate
  (400 × ratio^3.5, capped at 5000),
- publishes `sensors/{room}/co2` JSON every 5 s.

Build & flash:

```bash
cd esp32
# edit src/main.cpp: set YOUR_WIFI_SSID / YOUR_WIFI_PASSWORD / YOUR_BACKEND_LAN_IP
pio run -t upload
```

> The MQ-135 is an *approximate* chemiresistor, not a calibrated NDIR sensor:
> the ppm is a room-trend estimate, ideal for showing rising CO₂ and alerts,
> not lab-grade absolute values. For exact ppm use an MH-Z19B.

## Configuration

All knobs live in `backend/src/main/resources/application.properties` and can
be overridden with environment variables:

| Variable | Default | Purpose |
|----------|---------|---------|
| `SERVER_PORT` | 18080 | backend HTTP port |
| `SENSOR_MODE` | simulator | `simulator` or `hardware` |
| `DB_HOST / DB_PORT / DB_NAME / DB_USER / DB_PASSWORD` | localhost:5433/airquality/aq_user/aq_pass | PostgreSQL |
| `MQTT_BROKER` | tcp://localhost:1883 | Mosquitto URI |
| `AUTH_SECRET` | (auto-generated, persisted) | HMAC signing secret |

Secrets are never committed — local defaults only; real credentials come from
the environment.

## Engineering evidence

- Automated unit tests (`cd backend && ./mvnw test`).
- API contract: `backend/src/main/resources/openapi.yaml`.
- Docs: `docs/` — decision log, design pack, data & hardware, evidence,
  baseline comparison, failure/security, user guide, model card, demo script.

## Engineering practices

- Feature branches + pull requests into `main`.
- Unit tests run without infrastructure (`./mvnw test`).
- Decision log with explicit trade-offs: `docs/DECISIONS.md`.