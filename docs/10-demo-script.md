# 10 — Demo Script & Final Deliverables Outline

## A. Demo Script (target 5–8 min)

### 0. Hook (30 s)
"Indoor air quality drives health and cognition, but most rooms are 'closed up'
with no one noticing until someone complains. This is a low-cost digital twin
that *knows* when a room is unsafe and tells you what to do."

### 1. Stack runs (1 min)
`docker-compose up --build` → frontend at :5173, backend at :8080. Show
Postgres has 7 tables via the data (or a quick DB listing).

### 2. Live dashboard (1 min)
- Log in as `manager` → room cards with CO2/PM2.5/occupancy and
  **OK/WARNING/ALERT** status.
- Point out occupancy-awareness: an empty room with high-looking CO2 still
  shows OK (no indoor source).

### 3. Escalation → recommendation (1 min)
- Start a crowded-room scenario → watch CO2 climb → card flips WARNING → ALERT
  and *recommends* "increase ventilation now."

### 4. Projection + what-if (1.5 min)
- Show "we hit the limit in ~N min if nothing changes."
- Scenario simulation: set ventilation ON → prediction flips to "never";
  raise occupancy → crosses sooner. Demonstrates the twin's predictive power.

### 5. Act + audit trail (1 min)
- Toggle ventilation ON with a note as `manager` → action logged with actor+time.

### 6. Replay (1 min) — the "time travel" differentiator
- Innovation Lab → Replay: animate the CO2 timeline with alert + ventilation
  events overlaid. "You can reconstruct exactly what happened."

### 7. Daily exposure + calibration (VIEWER/ADMIN) (1 min)
- Show daily summary (avg/max CO2, minutes above limit).
- As `admin`: calibration screen — save an offset with a signed note (provenance).

### 8. Close (30 s)
- Recap: sensing → twin state → recommendation → action → audit. Hardening +
  real sensors are the next step.

---

## B. Final Report Outline (markdown doc to author)

1. **Executive summary** — problem, solution, results, headline numbers.
2. **Problem & requirements** (from 01-industry-brief).
3. **Design & architecture** (from 02-design-pack; C4 + schema + decisions).
4. **Implementation** — modules map to features; key code locations.
5. **Innovation layer** — digital twin state model, projection, what-if, replay
   (06-baseline-comparison for the "why").
6. **Engineering evidence** — 22 tests, CI, Docker, OpenAPI, git history
   (04-engineering-evidence).
7. **Evaluation** — calibration, latency, alert precision, usability
   (05-evaluation-dossier) + honest hardware caveat.
8. **Failure-mode, robustness, security** (07).
9. **Cost & hardware** (03 BOM, ~₹3,900 core).
10. **Limitations & future work** — PIR occupancy, field calibration, HVAC
    integration, fleet.
11. **Conclusion**.

## C. Presentation (10-slide deck plan)

1. Title — "Low-Cost IAQ Digital Twin with Ventilation Recommendations"
2. Problem & motivation
3. Solution overview (context diagram)
4. Architecture (containers + 7-table schema)
5. Innovation: state model + projection (concept)
6. Innovation: replay + what-if (screenshots)
7. Trust & audit: auth, calibration, daily exposure
8. Engineering evidence (tests/CI/Docker/OpenAPI)
9. Evaluation + honest limits (+ demo video link)
10. Cost, future work, thanks

## D. Video deliverable mapping

- 5–8 min screen recording following Section A.
- Reference the opencode-assistant workflow in the README's contributor note
  for reproducibility of the engineering-records side.
