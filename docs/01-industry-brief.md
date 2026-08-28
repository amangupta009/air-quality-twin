# 01 — Industry Problem Brief

## 1. Problem Statement

Indoor air quality (IAQ) is a primary driver of health, cognition, and safety
in occupied buildings, yet it is almost never measured or acted upon in real
time in small to mid-size facilities (schools, classrooms, offices). Without
live sensing, a room can sit "closed up" for hours while CO2 climbs above
1,000 ppm and particulate levels rise, with no one noticing until occupants
report headaches, drowsiness, or reduced concentration.

Facility managers typically act on **reactive complaints** rather than
**proactive measurement**. There is no way to know, in the moment, whether a
room is safe, trending toward unsafe, or already in an alert state — and no
grounded recommendation for when to open a window or switch on ventilation.

This project delivers a **low-cost indoor air quality digital twin** with a
ventilation recommendation engine: a small ESP32 + CO2/PM2.5 sensor node that
publishes readings over MQTT, a backend that maintains a live model of each
room's state, an alert and recommendation layer, and a role-based dashboard.

## 2. Goals

1. Continuously measure CO2 and PM2.5 in occupied rooms.
2. Derive a **live, queryable state model** (a "twin") per room — not just a
   passive chart.
3. Produce explainable status (OK / WARNING / ALERT) and ventilation
   recommendations grounded in sensor + occupancy data.
4. Let facility managers act (toggle ventilation) and auditors review
   everything later (replay, daily exposure, calibration, action log).
5. Keep total hardware cost under ~¥4,000 / ~$50 for an entire sensing node.

## 3. Non-Goals (explicitly out of scope)

- Active HVAC control (we recommend; we do not drive motors/returns).
- Compliance certification (ASHRAE 62.1 note — see Model/System Card).
- Multi-building / fleet orchestration.
- Weather-based forecasting integration.

## 4. Stakeholders

| Stakeholder | Interest | Success measure |
|---|---|---|
| Facility Manager | Health & comfort of occupants; operational cost | Sees alerts before complaints; can act on recommendations |
| Building Occupants | Comfort, health, productivity | Rarely exposed to poor air without notification |
| Health & Safety Officer | Regulatory posture, incident record | Audit trail of alerts + actions per room/day |
| Maintainer / Operator | Low-touch, reliable system | Configurable thresholds, calibratable sensors, sensible failure behaviour |
| Project Reviewer | Demonstrate engineering rigour | Evidence: tests, PRs, decisions, evaluation |

## 5. Personas

### Priya — Facility Manager (primary user; FACILITY_MANAGER role)
- Runs a mid-size college block, 12 occupied rooms.
- Wants a single dashboard: which rooms are OK / warning / alert *right now*.
- Acts on a clear recommendation ("increase ventilation now") and needs the
  action logged so it can be reviewed.
- **Pain today:** relies on complaints; no record of when ventilation was on.

### Sam — Health & Safety Officer (VIEWER / auditor)
- Reviews compliance-style summaries at end of day.
- Wants: per-room daily exposure summary, alert history, and replay to
  reconstruct what happened.
- **Pain today:** no data trail at all.

### Dr. Rao — System Administrator / Maintainer (ADMIN)
- Configures alert thresholds, calibrates sensors, manages users.
- Needs to know calibration happened and by whom; wants the system to degrade
  gracefully when the network or a sensor fails.
- **Pain today:** every IAQ box is a black box; cannot maintain or trust it.

### Aman — Developer (this capstone)
- Delivers the end-to-end system with engineering evidence.

## 6. User Stories (prioritised)

| ID | Story | Priority | Role needed |
|---|---|---|---|
| US1 | As a FM I want a dashboard of all rooms' live status so I can spot problems at a glance | MUST | VIEWER |
| US2 | As a FM I want a per-room CO2/PM timeline so I can understand trends | MUST | VIEWER |
| US3 | As a FM I want an alert when a room crosses its threshold so I can act | MUST | VIEWER |
| US4 | As a FM I want a ventilation recommendation so I know what to do | MUST | VIEWER |
| US5 | As a FM I want to toggle ventilation and log why so there is a record | MUST | FACILITY_MANAGER |
| US6 | As an ADMIN I want to change CO2/PM thresholds so settings match policy | SHOULD | ADMIN |
| US7 | As an ADMIN I want to calibrate a sensor and log it so data stays trustworthy | SHOULD | ADMIN |
| US8 | As an HSO I want a daily exposure summary per room so I can review exposure | SHOULD | VIEWER |
| US9 | As an HSO I want to replay events so I can reconstruct incidents | COULD | VIEWER |
| US10 | As a FM I want a what-if simulation so I can plan occupancy/ventilation | COULD | FACILITY_MANAGER |
| US11 | As an operator I want a forecast of when a room will hit its limit | COULD | VIEWER |

## 7. Acceptance Criteria

1. Backend builds and all automated tests pass in CI.
2. Live status reflects the occupancy-aware CO2/PM2.5 model.
3. Every CLI-facing role works: viewer read-only, FM can act, ADMIN can admin.
4. All data (readings, alerts, actions, calibration, summaries) is persisted
   and replayable.
5. Sensor failure / broker loss does not crash the system (logged degradation).
6. Full API contract documented (OpenAPI).

## 8. Initial Backlog (raw, then prioritised into the plan above)

- [ ] Sensor node firmware (ESP32 + CO2 + PM2.5) — DONE
- [ ] MQTT ingest pipeline — DONE
- [ ] Persistent readings — DONE
- [ ] Digital twin state model — DONE
- [ ] Alerts + recommendations — DONE
- [ ] Ventilation action log — DONE
- [ ] Auth + roles — DONE
- [ ] Calibration — DONE
- [ ] Daily exposure summary — DONE
- [ ] Event replay — DONE
- [ ] Scenario simulation — DONE
- [ ] CI/CD, Docker, OpenAPI — DONE
- [ ] Documentation & demo — in progress
