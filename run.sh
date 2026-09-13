#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# One-command start for the whole stack (no Docker, no root).
#
#   bash run.sh            -> starts Postgres + Mosquitto + backend + frontend
#   bash run.sh --simulator -> same but uses the built-in simulator instead
#                              of the real ESP32/MQ-135 hardware
#
# Then open http://localhost:5173 and log in (admin/admin123).
#
# The backend jar is rebuilt automatically whenever the source has changed,
# so "just works" after a `git pull` or an edit. Idempotent: re-running it
# never duplicates services.
# ---------------------------------------------------------------------------
set -euo pipefail
cd "$(dirname "$0")"

MODE=hardware
if [ "${1:-}" = "--simulator" ]; then
    MODE=simulator
fi

echo "[run] Starting full stack (sensor.mode=$MODE) ..."
SENSOR_MODE=$MODE bash scripts/start-all.sh
echo
echo "DONE. Open http://localhost:5173  (admin / admin123)"