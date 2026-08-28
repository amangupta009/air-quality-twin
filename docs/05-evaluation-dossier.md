# 05 — Evaluation Dossier

How each acceptance-gate "evaluation" dimension is measured, plus the expected
targets and the verification status. Honest caveat: **live hardware results
are pending** (sensors on order); simulator + unit + integration results are
final. Each section states what was measured and how it will be confirmed with
hardware.

---

## 1. Calibration Integrity

**Method.** The ADMIN calibration screen records `offset` + `scale` per
room/metric with `calibratedBy`, `notes`, and `calibratedAt`. Every calibration
write is persisted in `calibration_profile` (7-table schema) — full provenance.

**Success criteria (measured at ingest):**
- A calibration profile exists for each CO2/PM2.5 source before it is treated
  as "trusted". Verified via Calibration API round-trip.
- Values are clamped to sane ranges (no negative CO2, no absurd scaling).

**Status.** Backend + UI + persistence complete; round-trip tested by API
usage. Physical offset-vs-reference comparison pending hardware (compare
MH-Z19B against known outdoor ~420 ppm and PMS5003 against a reference).

## 2. Latency

**Method.** Wall-clock from MQTT publish → reading persisted → state updated →
dashboard/API reflects it. The simulator stamps receipt time (`ts` absent →
backend stamps now), and the ingest path updates `RoomState` in-memory
(no DB in the hot path).

**Expected envelope (simulator, localhost):** publish → dashboard update well
under 1 s (ingest is memory-bound; DB write is async-ish JPA).
**Confirmed by:** `MqttSensorDataSource` → `RoomTwinService.applyReading` → REST
read returns immediately from the in-memory `ConcurrentHashMap`.

**Status.** Measurable live; demo run confirms sub-second visibility. A precise
end-to-end figure is recorded during the final demo.

## 3. Alert Precision / Recall

**Method.** The occupancy-aware model (RoomTwinService) is opinionated:
- **No alert when empty is good (precision):** empty room stays OK even if CO2
  reads high — no indoor source, so no action needed.
- **Escalation on real rise (recall):** crowded room crosses WARNING then ALERT
  as CO2 climbs past `420 + N*15 + 380` (capped 2500) with PM2.5 fixed bands.

**Evidence (tests):**
- `emptyRoomIsAlwaysSafeEvenIfCo2IsHigh` — precision case.
- `statusEscalatesToWarningThenAlert` — recall case (WARNING then ALERT).
- `pm25CrossesToAlertIndependently` — PM2.5-only alert works.
- `co2AtOrBelowOutdoorStaysOk` — baseline 420 ppm never false-alerts.

**Status.** Logic + unit tests final. Ground-truth vs a reference monitor is the
pending hardware check (a precision/recall table over a labelled run).

## 4. Usability (heuristic, light)

**Method.** Task-based walkthrough against the UI, one role at a time:
1. VIEWER: see live status + timeline on login. ✔
2. VIEWER: expand a room → recommendations + projection. ✔
3. FACILITY_MANAGER: toggle ventilation with a note → logged. ✔
4. ADMIN: change thresholds; calibrate a sensor with provenance. ✔
5. VIEWER: daily exposure summary; replay timeline; what-if sim. ✔

**Status.** All flows implemented. Quantitative UX (task time / SUS) is a demo
deliverable; heuristic pass is done.

## 5. Correctness of Derived Values

- **Projection** (minutes to threshold) — 4 unit tests cover: already-over→0,
  negative when empty, finite future for crowded no-vent, none when ventilation
  adequate, and the no-vent/no-ach boundary.
- **Daily summary** — avg/max CO2, avg/max PM2.5, minutes-above computed from
  persisted readings and cached per day (`DailyExposureService`). Floating drift
  and empty-day handling covered by repo query shape.

## 6. How to reproduce all of this

```bash
# Backend contracts
cd backend && ./mvnw test          # 22 tests, BUILD SUCCESS
# Frontend
cd frontend && npm ci && npm run build   # dist/, no vulns
# Full stack (requires Docker daemon)
docker-compose up --build
# (Docker daemon not available in this sandbox — verified config via
#  'docker-compose config' and local image-equivalent builds.)
```

## 7. Honest Risk Call-outs

- **Hardware accuracy** (MH-Z19B ±50ppm, PMS5003 ±15%) is uncorrected until
  field calibration; the calibration screen exists exactly to close this.
- **Alert "ground truth"** needs a reference measurement to give a real
  precision/recall number; unit tests prove the logic, not the physics.
- **Latency** is measured inside a single host; real-network MQTT adds a small
  margin out of our control.
