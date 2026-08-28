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

1. Sensor station → simulator + `sensor/` package + real ESP32 firmware (`esp32/`)
2. Room dashboard → `frontend/`
3. Occupancy context → `occupancy` metric + `OccupancySnapshot`
4. Threshold alerts → `ReadingIngestService.evaluateThreshold` + `AlertEvent`
5. Ventilation action log → `POST /api/rooms/{id}/ventilation` + `VentilationAction`
6. Daily exposure summary → `DailyExposureSummary` + rollup service + UI
7. Calibration screen → `CalibrationProfile` + ADMIN UI with provenance

Innovation layer: live twin (`RoomTwinService` + dynamic occupancy-aware
status), event replay timeline, and scenario simulation
(`projectMinutesToCo2Threshold`).

## Trust & audit layer

- Role-based auth (VIEWER / FACILITY_MANAGER / ADMIN) enforced on every
  `/api/**` (see `backend/.../auth/` + `docs/02-design-pack.md` threat model).
- Ventilation actions, occupancy overrides, and calibrations always record
  who/what/when.

## One-command full stack (no Docker required)

```bash
bash scripts/start-all.sh
# Dashboard : http://localhost:5173
# API       : http://localhost:8080/api/rooms
```

## Engineering evidence

- 22 automated unit tests (`cd backend && ./mvnw test`).
- CI/CD: `.github/workflows/ci.yml` (backend test, frontend build, docker).
- API contract: `backend/src/main/resources/openapi.yaml`.
- Docs: `docs/` (brief, design, data/hardware, evidence, evaluation,
  baseline comparison, failure/security, guides, model card, demo script).

## Quickstart (local dev)

Easiest (starts PostgreSQL + Mosquitto + backend + frontend, no Docker/root):

```bash
bash scripts/start-all.sh        # → http://localhost:5173
```

Or piece-by-piece with Docker: backstage infra via `docker-compose.yml`, then
backend `./mvnw spring-boot:run`, then frontend `npm run dev`.

Default logins (dev only): `viewer/viewer123`, `manager/manager123`,
`admin/admin123`. Full walkthrough in `docs/08-user-admin-guide.md`.

```bash
# Watch readings arrive every 5s; CO2 climbs with occupancy
curl http://localhost:8080/api/rooms
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
