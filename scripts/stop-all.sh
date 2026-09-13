#!/usr/bin/env bash
# Stops everything started by start-all.sh (backend jar, frontend, MQTT, DB).
set -u

RUNTIME="${HOME}/aq-runtime"
PGBIN="/usr/lib/postgresql/18/bin"

echo "[stop-all] Stopping backend jar..."
pkill -f "air-quality-twin-0.0.1-SNAPSHOT.jar" 2>/dev/null
pkill -f "spring-boot:run" 2>/dev/null

echo "[stop-all] Stopping frontend..."
pkill -f "[v]ite" 2>/dev/null

echo "[stop-all] Stopping Mosquitto..."
pkill -f "mosquitto-dev.conf" 2>/dev/null

if [ -d "$RUNTIME/pgdata" ]; then
    echo "[stop-all] Stopping PostgreSQL..."
    "$PGBIN/pg_ctl" -D "$RUNTIME/pgdata" stop -m fast >/dev/null 2>&1
fi

sleep 2
echo "[stop-all] Done. Data is preserved in ~/aq-runtime — run scripts/start-all.sh to restart."