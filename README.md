# Indoor Air Quality Digital Twin with Ventilation Recommendation Engine

T.Y. B.Sc IT capstone prototype: a live digital twin of indoor air quality
with threshold alerts, a ventilation audit trail, event replay, and
what-if scenario simulation.

## Architecture

```
Sensor / Simulator ──MQTT──▶ Mosquitto broker
                                  │
                                  ▼ (subscribe sensors/+/+)
                          Spring Boot backend
                    calibrate → twin update → thresholds
                                  │
                        PostgreSQL ◀ write │ read ▶ REST /api/*
                                                     │
                                          React dashboard ◀── WebSocket /ws (live push)
                                                     │
                                        "Ventilation ON" POST → audit log in DB
```

Rules enforced by the code:
- The frontend never touches the database — only backend REST/WebSocket.
- The database never touches MQTT — the backend is the single gatekeeper.
- User actions return through `POST /api/rooms/{id}/ventilation` and are
  logged with who/when (audit trail).

## Repository layout

| Path | What it is |
|---|---|
| `backend/` | Spring Boot (Java 21) API, MQTT ingestion, digital twin, JPA entities |
| `frontend/` | React + Vite dashboard |
| `simulator/` | Standalone Python sensor simulator (external publisher) |
| `mosquitto/` | MQTT broker config for local dev |
| `docs/` | Decision log, design pack, evaluation dossier (growing) |

## Module coverage (7 mandatory)

1. Sensor station → simulator + `sensor/` package (swap-ready for ESP32)
2. Room dashboard → `frontend/`
3. Occupancy context → `occupancy` metric + `OccupancySnapshot`
4. Threshold alerts → `ReadingIngestService.evaluateThreshold` + `AlertEvent`
5. Ventilation action log → `POST /api/rooms/{id}/ventilation` + `VentilationAction`
6. Daily exposure summary → `DailyExposureSummary` (rollup job in later phase)
7. Calibration screen → `CalibrationProfile` applied on ingest (UI later phase)

Innovation layer: live twin (`RoomTwinService`), replay (planned, data already
persisted), scenario simulation (`projectMinutesToCo2Threshold` seed).

## Quickstart (local dev)

Prerequisites: Docker (with your user in the `docker` group), Java 21+, Node 20+.

```bash
# 1. Infrastructure: PostgreSQL + Mosquitto
docker compose up -d

# 2. Backend (built-in simulator active by default)
cd backend && ./mvnw spring-boot:run

# 3. Watch it work: readings arrive every 5s; CO2 climbs with occupancy
curl http://localhost:8080/api/rooms

# 4. Frontend
cd frontend && npm install && npm run dev   # http://localhost:5173
```

Useful variations:

```bash
# External Python simulator instead of the built-in one:
SENSOR_MODE=hardware ./mvnw spring-boot:run        # terminal 1
cd simulator && pip install -r requirements.txt    # terminal 2
python3 simulator.py
```

## Configuration

All knobs live in `backend/src/main/resources/application.properties` and can
be overridden with environment variables (`SENSOR_MODE`, `DB_*`, `MQTT_BROKER`,
thresholds). See comments inline. Secrets are never committed — local defaults
only.

## Engineering practices

- Feature branches + pull requests into `main` (no direct pushes)
- Unit tests run without infrastructure (`./mvnw test`)
- Decision log with explicit trade-offs: `docs/DECISIONS.md`
