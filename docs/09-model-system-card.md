# 09 — Model / System Card

A concise, honest description of the "model" behind the digital twin, its
assumptions, and its validation status.

---

## 1. What the "model" is

The digital twin is not ML. It is a **physical first-principles model** of CO2
dynamics plus a **rule-based decision layer**, both deterministic and auditable.

### (a) Occupancy-aware status model
- `alertLimit = min(420 + occupants × 15 + 380, 2500)` ppm
- `warningLimit = 0.8 × alertLimit`
- PM2.5 fixed bands: ALERT ≥ 35, WARNING ≥ 28 µg/m³ (configurable thresholds)
- Empty room (occupants ≤ 0) → judged OK (no indoor CO2 source).

### (b) CO2 projection model (well-mixed mass balance)
Closed-form solution of `dC/dt = k(C_eq − C)`:
```
C_eq = C_outdoor + (n × G) / k
       k = ACH / 3600  (s⁻¹)
       G  = 0.045 L/s per person (CO2 generation)
```
Minutes-to-target is derived by inverting the exponential approach to the
equilibrium. States: `already`, `in ~N min`, or `never`.

### (c) Recommendation rules
| Status | Ventilation | Recommendation |
|---|---|---|
| ALERT | ON | Keep ON until below 80% of limit |
| ALERT | OFF | Increase ventilation now |
| WARNING | any | Consider increasing ventilation |
| OK | any | Within safe limits |

## 2. Inputs & Outputs

- **Inputs:** CO2 (ppm), PM2.5 (µg/m³), occupancy (people), ventilation on/off.
- **Outputs:** per-room live status, recommendation, projection (minutes),
  daily exposure rollup.

## 3. Assumptions & Limitations (be explicit)

1. Single well-mixed zone per room (no spatial gradients).
2. Constant outdoor baseline 420 ppm.
3. Fixed CO2 generation 0.045 L/s per person.
4. `ACH = 6.0` when ventilation ON, `0.5` infiltration when OFF (configurable
   but illustrative, not measured in the field yet).
5. Occupancy is a manual override / assumption, not yet measured by a PIR/CO2
   occupant counter.
6. MH-Z19B and PMS5003 accuracy limits (see 03). Not yet field-calibrated.
7. Not a compliance certifier — recommendations only (see note below).

> **ASHRAE note:** 1000 ppm CO2 and PM2.5 < 35 µg/m³ are common operational
> benchmarks; this twin flags against *configurable* thresholds and is a
> decision-support tool, not a substitute for an engineering IAQ assessment.

## 4. Validation status

| Claim | Evidence | Status |
|---|---|---|
| Status logic | 4 unit tests (escalate, empty-safe, pm-independent, outdoor-baseline) | ✔ |
| Projection closed form | 4 tests (already-over, negative-empty, finite-future, never) | ✔ |
| Occupancy defaulting | `missingOccupancyDefaultsToZeroPeople` | ✔ |
| Model vs physics field data | — | pending hardware |

## 5. Intended use & scope

- **In scope:** proactive monitoring, alerting, ventilation recommendation,
  planning (what-if), post-hoc audit (replay/daily summary).
- **Out of scope:** closed-loop HVAC control, compliance certification,
  multi-building fleet, weather forecasting.
