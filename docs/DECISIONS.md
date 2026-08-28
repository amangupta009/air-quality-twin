# Decision Log

Architectural decisions and their trade-offs. One entry per decision, newest
at the bottom. This file is part of the engineering evidence pack.

---

## D1 — Time-series storage: plain PostgreSQL table (accepted trade-off)

**Date:** 2026-08-21 · **Status:** Accepted

The capstone brief suggests a dedicated time-series database for sensor
readings. We store readings in a timestamped PostgreSQL table
(`sensor_readings`) with a composite index on `(roomId, metric, recordedAt)`
instead.

- **Why:** solo beginner project; one database technology to learn, back up,
  and deploy. At our data rates (~3 readings / 5 s ≈ 52k rows/day) a indexed
  Postgres table is comfortably sufficient for replay and daily rollups.
- **Consequence:** if write volume ever grows, the `SensorReadingRepository`
  interface is the only seam that needs a new implementation (e.g. TimescaleDB,
  which is a Postgres extension anyway).
- **Flagged explicitly** per project rules rather than silently dropped.

## D2 — Simulator-first with an abstraction seam

**Date:** 2026-08-21 · **Status:** Accepted

No physical sensor exists yet. The system is built against a simulator that
publishes to the same MQTT topic contract (`sensors/{room}/{metric}`) the
future ESP32 will use.

- The backend pipeline depends on the `SensorDataSource` interface, never on
  where data comes from. Production uses `MqttSensorDataSource`; unit tests
  can inject a fake source with no broker at all.
- Two interchangeable simulators exist:
  1. Built-in Java `SimulatorFeed` (active when `sensor.mode=simulator`) —
     zero-setup demos.
  2. Standalone Python `simulator/simulator.py` — external publisher for load
     tests / cross-machine setups (run backend with `SENSOR_MODE=hardware`).
- **Hardware swap procedure:** flash ESP32 → set `sensor.mode=hardware` → done.
  No downstream code changes; subscriber, DB schema, API, frontend untouched.

## D3 — MQTT topic & payload contract

**Date:** 2026-08-21 · **Status:** Accepted

```
Topic:   sensors/{roomId}/{metric}      metric ∈ co2 | pm25 | pm10 | occupancy
Payload: {"value": <number>, "unit": "<string>", "ts": "<ISO-8601 UTC>"}
```

`ts` is optional; backend stamps receipt time if absent. Malformed messages
are logged and discarded (edge-case handling evidence).

## D4 — Anonymous Mosquitto listener in dev only

**Date:** 2026-08-21 · **Status:** Accepted (dev-scoped)

`allow_anonymous true` in `mosquitto/config/mosquitto.conf` is safe only
because the broker runs on localhost for development. Production checklist:
username/password, TLS (8883), and network isolation. Tracked for the threat
model deliverable.
