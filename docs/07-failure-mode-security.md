# 07 — Failure-Mode, Robustness & Security Experiment

How the system behaves under failure and attack, what was tested, and the
remaining production hardening. This is the "failure-mode/robustness/security
experiment" deliverable.

---

## 1. Failure Modes & Behaviour

| Failure | Detected by | System behaviour | Verified |
|---|---|---|---|
| MQTT broker down at startup | MQTT connect fails | Backend still starts; ingest retries/logs; dashboard serves last state | ✔ (design; log path) |
| Broker drops mid-run | pub/sub disconnect | Reconnect loop, no crash; state preserved in memory | ✔ |
| Sensor stops reporting | no new readings | Stale `updatedAt` visible; no false alert; status uses last known + occupancy | ✔ (design) |
| Malformed MQTT payload | parse exception | Logged and dropped — never crashes pipeline (D3) | ✔ (ingest guard) |
| Unknown metric in topic | switch default | Ignored (`RoomTwinService.applyReading` default branch) | ✔ |
| Occupancy missing | null occupants | Defaults to 0 → room judged safe (no indoor source) | ✔ (`missingOccupancyDefaultsToZeroPeople`) |
| CO2 above outdoor baseline | — | Stays OK at 420 (no false alert) | ✔ (`co2AtOrBelowOutdoorStaysOk`) |
| Empty room, high CO2 | occupancy=0 | Always OK (no source) | ✔ (`emptyRoomIsAlwaysSafeEvenIfCo2IsHigh`) |
| Sensor value zero/negative | validation | Sanitised / treated as absent in twin, not stored as alarm | ✔ (ingest) |

## 2. Robustness Evidence (tests)

- `TwinEdgeCaseTest` — 8 cases: empty-room, crowded escalation, outdoor
  baseline, independent PM2.5, projection negatives/futures/none, occupancy
  default to 0.
- `AuthServiceTest` — null/empty tokens are rejected gracefully (`roleForToken`
  returns null), null login credentials do not NPE.
- Malformed-data guard in ingest logs-and-drops rather than bubbling up.

## 3. Security Posture

### Authentication & Authorisation (enforced)
- Bearer-token login; `AuthInterceptor` guards **every** `/api/**` path,
  excluding only `/api/auth/login` (`WebConfig`).
- Role hierarchy: **ADMIN > FACILITY_MANAGER > VIEWER**, checked per endpoint.
  - VIEWER: read-only (rooms, readings, alerts, actions, summary, replay).
  - FACILITY_MANAGER: + toggle ventilation, set occupancy.
  - ADMIN: + thresholds, calibration, user management.

### Auth tests
- Login valid/wrong/unknown; role-hierarchy checks (admin can everything;
  manager can't admin; viewer read-only); logout invalidates token; null input
  handled.

### Threat-model coverage (see 02-design-pack)
- Spoofing (login/token, sensor) — mitigated via auth + local broker.
- Repudiation — ventilation/calibration/occupancy always record actor+time.
- Disclosure — role-gated endpoints.
- Elevation — strict hierarchy per endpoint.
- Config secrets — passwords only via env vars; dev defaults marked unsafe.

### Production hardening checklist (documented, not yet applied — dev-only env)
- [ ] Mosquitto TLS (8883) + username/password (remove `allow_anonymous`).
- [ ] Network isolation: broker + DB not exposed publicly.
- [ ] Long-lived tokens with expiry + refresh.
- [ ] Rate limiting on login (brute-force).
- [ ] Parameterised queries / JPA (already safe) + input validation.
- [ ] Secret rotation + secrets manager in CI/CD.
- [ ] HTTPS termination in front of frontend.

## 4. Experiment: what we deliberately tested vs what remains

| Test | Done now | Outstanding (needs hardware/net) |
|---|---|---|
| Logic robustness under edge inputs | ✔ unit tests | — |
| Broker/sensor loss behaviour | ✔ design + logs | live failover demo |
| Auth enforcement across roles | ✔ tests + interceptor | — |
| Real TLS broker + network attack | — | production hardening |
| Physical sensor noise/accuracy | — | field calibration |
