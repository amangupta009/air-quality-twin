# 08 — Administrator & User Guide

How to run, log in, and use every screen. Roles: **VIEWER**, **FACILITY_MANAGER**,
**ADMIN**.

---

## 1. Running the Stack

### Dev (local, no Docker daemon needed)
```bash
# 1) Database (must be running). If you have Docker:
docker run -d --name aq-db -e POSTGRES_DB=air_quality \
  -e POSTGRES_USER=air -e POSTGRES_PASSWORD=air \
  -p 5432:5432 postgres:16-alpine

# 2) Backend (simulator mode by default — no sensor needed)
cd backend && ./mvnw spring-boot:run

# 3) Frontend (Vite dev server)
cd frontend && npm install && npm run dev   # → http://localhost:5173
```

### Docker compose (full stack)
```bash
docker-compose up --build      # DB + backend + frontend
# Frontend → http://localhost:5173  ·  API → http://localhost:8080
```

### Real hardware (when sensors arrive)
Flash `esp32/` (PlatformIO), set the WiFi/broker/room constants in
`src/main.cpp`, then run backend with `SENSOR_MODE=hardware`. See
`docs/03-data-hardware-pack.md` for wiring and the BOM.

---

## 2. Logging In

`GET / → LoginGate` prompts for username/password. On success a bearer token
is stored and used on every subsequent request.

| Username | Password | Role |
|---|---|---|
| `viewer` | `viewer123` | VIEWER (read-only) |
| `manager` | `manager123` | FACILITY_MANAGER |
| `admin` | `admin123` | ADMIN |

> Defaults are for dev only. Change via `DataSeeder` / env and never ship
> production creds.

## 3. Screens & Permissions

### Room Dashboard (all roles)
- Grid of room cards: live CO2, PM2.5, occupancy, ventilation, and derived
  status OK/WARNING/ALERT with a color cue.
- Each card shows the recommendation and a CO2 timeline.

### Ventilation control (FACILITY_MANAGER+)
- On a room card: toggle ventilation and add a **note**. Every action is
  timestamped with the actor and appended to the audit trail.

### Thresholds (ADMIN)
- Raise/lower the CO2 (ppm) and PM2.5 (µg/m³) alert limits. Changes apply to
  the live model immediately.

### Calibration screen (ADMIN)
- For each room/metric, record an offset + scale and a free-text note, signed
  by `calibratedBy`. Saved to `calibration_profile` with a timestamp
  (provenance). Review/edit history is available.

### Daily Exposure summary (VIEWER+)
- Pick a room (+ date) → avg/max CO2, avg/max PM2.5, and estimated minutes
  above the CO2 limit for the day. Stable, persisted rollup.

### Innovation Lab — Replay (VIEWER+)
- Pick a window (1h / 3h / 24h). Replay animates the CO2 line while alert and
  ventilation events are overlaid on the timeline — see the whole story, not
  just a chart.

### Innovation Lab — Scenario Simulation (FACILITY_MANAGER+)
- Enter hypothetical **people**, **ventilation ON/OFF**, and a **target ppm**,
  then "Predict". The twin returns when the room would reach the target under
  those conditions (already / in ~N min / never).
- Useful for planning occupancy changes or sizing ventilation.

---

## 4. API Cheat-Sheet (see `backend/src/main/resources/openapi.yaml`)

```
POST /api/auth/login                      {username,password} → token
POST /api/auth/logout                     {token}
GET  /api/rooms                           live twin snapshots
POST /api/rooms/{id}/ventilation          {action,actor,note}  (FM+)
POST /api/rooms/{id}/occupancy            {occupants}          (FM+)
GET  /api/rooms/{id}/readings?minutes=30                       (VIEWER)
GET  /api/rooms/{id}/actions             audit trail
GET  /api/rooms/{id}/alerts              alert history
GET/PUT /api/settings/thresholds                               (GET all, PUT ADMIN)
POST /api/calibration                     (ADMIN)
GET  /api/calibration/{roomId}            (ADMIN)
GET  /api/summary/daily/{roomId}?date=    (VIEWER)
GET  /api/replay/{roomId}?minutes=        (VIEWER)
POST /api/simulate/{roomId}               (FM+)
```

Send `Authorization: Bearer <token>` on every `/api/**` call except login.
