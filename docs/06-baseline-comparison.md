# 06 — Baseline Comparison: Digital Twin vs Static Dashboard

A required comparison showing the value the innovation layer adds over a plain
"static dashboard" (raw charts, no live model, no projection, no
recommendation).

---

## 1. The Two Systems Compared

| Capability | A. Static Dashboard (baseline) | B. Digital Twin (this project) |
|---|---|---|
| Show last CO2/PM reading | ✔ (mutable chart) | ✔ + live model snapshot |
| Occupancy awareness | ✘ (ignores who is in the room) | ✔ empty room never false-alerts |
| Live status (OK/WARN/ALERT) | ✘ (raw numbers; user must interpret) | ✔ derived & explainable |
| Ventilation recommendation | ✘ (user guesses) | ✔ rule-based, context-aware |
| Projection (when will we hit limit?) | ✘ | ✔ "now + N min" using box model |
| What-if (change occupancy/ventilation) | ✘ | ✔ scenario simulation |
| Event replay | ✘ (chart only) | ✔ correlated timeline |
| Action audit trail | ✘ | ✔ actor/note/timestamp |

## 2. Why the model (not raw data) is the difference

A static dashboard shows **"CO2 = 1280 ppm"**. A user must know that's high, must
know it matters for *this* room right now, must decide what to do.

A digital twin shows **"ALERT — increase ventilation now"**, derived from
occupancy (N people → dynamic limit), current CO2, and ventilation state — and
can answer **"if we do nothing, we hit the limit in ~12 min"**.

The twin turns **reactive data** into **actionable state**.

## 3. Measured distinction (via code + tests)

| Metric | Static | Twin |
|---|---|---|
| Empty-room false alert | would alert on high CO2 | stays OK (`emptyRoomIsAlwaysSafeEvenIfCo2IsHigh`) |
| Escalation fidelity | flat threshold | WARNING then ALERT (`statusEscalatesToWarningThenAlert`) |
| Standing decision time | minutes of interpretation | instant (field `recommendation`) |

## 4. Demo narrative to show the difference

1. Start a crowded room scenario → watch CO2 climb on both "dashboards".
2. On the static chart the reviewer must *decide* it's a problem.
3. The twin flags WARNING → ALERT automatically and prescribes ventilation.
4. Click "what-if": raise ventilation → projection flip to "stays safe"; lower
   occupancy → same. Shows the model's predictive power a static chart cannot.
5. Toggle ventilation ON → action logged → replay shows the whole story with
   who/when/why.

## 5. Conclusion

The twin is strictly more informative under the same data. The only cost is the
in-memory state model (`RoomTwinService` + `RoomState`), which is cheap and
tested. This justifies the "innovation layer" investment and directly supports
the acceptance-gate "baseline comparison" requirement.
