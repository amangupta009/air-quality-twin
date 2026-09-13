#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# Starts the FULL stack without Docker / root:
#   PostgreSQL (:5433) -> Mosquitto MQTT (:1884) -> Spring Boot (:18080)
#   -> React dashboard (:5173)
#
# Runtime data lives in ~/aq-runtime (survives reboot, never committed).
# Safe to re-run: anything already running is left alone.
# ---------------------------------------------------------------------------
set -euo pipefail

RUNTIME="${HOME}/aq-runtime"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PGBIN="/usr/lib/postgresql/18/bin"
export LD_LIBRARY_PATH="${RUNTIME}/mosquitto/usr/lib/x86_64-linux-gnu"

log() { echo "[start-all] $*"; }
is_up() { ss -tln 2>/dev/null | grep -q ":$1 "; }

mkdir -p "$RUNTIME"

# --- 1. PostgreSQL ----------------------------------------------------------
if is_up 5433; then
    log "PostgreSQL already running on :5433"
else
    if [ ! -d "$RUNTIME/pgdata" ]; then
        log "Initializing fresh PostgreSQL cluster (first run only)..."
        "$PGBIN/initdb" -D "$RUNTIME/pgdata" \
            --auth-local=trust --auth-host=scram-sha-256 -U "$(whoami)" >/dev/null
    fi
    log "Starting PostgreSQL on :5433..."
    "$PGBIN/pg_ctl" -D "$RUNTIME/pgdata" -l "$RUNTIME/pg.log" \
        -o "-p 5433 -k $RUNTIME" start >/dev/null
fi

# App role + database (idempotent)
if ! "$PGBIN/psql" -h "$RUNTIME" -p 5433 -U "$(whoami)" -d postgres -tAc \
        "SELECT 1 FROM pg_roles WHERE rolname='aq_user'" | grep -q 1; then
    log "Creating app role + database..."
    "$PGBIN/psql" -h "$RUNTIME" -p 5433 -U "$(whoami)" -d postgres <<'SQL' >/dev/null
CREATE ROLE aq_user LOGIN PASSWORD 'aq_pass';
CREATE DATABASE airquality OWNER aq_user;
SQL
fi

# --- 2. Mosquitto MQTT ------------------------------------------------------
if is_up 1884; then
    log "Mosquitto already running on :1884"
else
    if [ ! -x "$RUNTIME/mosquitto/usr/sbin/mosquitto" ]; then
        log "Downloading + extracting Mosquitto (first run only)..."
        DL="$(mktemp -d)"
        (
            cd "$DL"
            apt-get download mosquitto libwebsockets19t64 libcjson1 libdlt3 >/dev/null 2>&1
            mkdir -p "$RUNTIME/mosquitto"
            for f in *.deb; do dpkg -x "$f" "$RUNTIME/mosquitto/"; done
        )
        rm -rf "$DL"
    fi
    log "Starting Mosquitto on :1884..."
    cat > "$RUNTIME/mosquitto-dev.conf" <<'EOF'
listener 1884 0.0.0.0
allow_anonymous true
persistence false
log_dest file /home/DUMMY/aq-runtime/mosquitto.log
EOF
    sed -i "s|/home/DUMMY|${HOME}|" "$RUNTIME/mosquitto-dev.conf"
    "$RUNTIME/mosquitto/usr/sbin/mosquitto" -c "$RUNTIME/mosquitto-dev.conf" -d
fi

# --- 3. Backend -------------------------------------------------------------
if is_up 18080; then
    log "Backend already running on :18080"
else
    JAR="$ROOT/backend/target/air-quality-twin-0.0.1-SNAPSHOT.jar"
    if [ ! -f "$JAR" ] || find "$ROOT/backend/src" -newer "$JAR" -print -quit | grep -q .; then
        log "Building backend jar (source changed / not built yet, ~20s)..."
        (cd "$ROOT/backend" && ./mvnw -q -DskipTests package >/dev/null)
    fi
    log "Starting backend jar (single JVM, capped heap)..."
    (
        setsid env \
            DB_HOST=localhost DB_PORT=5433 DB_NAME=airquality \
            DB_USER=aq_user DB_PASSWORD=aq_pass \
            MQTT_BROKER=tcp://localhost:1884 SENSOR_MODE=${SENSOR_MODE:-simulator} \
            SERVER_PORT=18080 \
            nohup java -Xms64m -Xmx400m -jar "$JAR" > "$RUNTIME/backend.log" 2>&1 < /dev/null &
    )
fi

# --- 4. Frontend ------------------------------------------------------------
if is_up 5173; then
    log "Frontend already running on :5173"
else
    [ -d "$ROOT/frontend/node_modules" ] || {
        log "Installing frontend dependencies (first run only)..."
        (cd "$ROOT/frontend" && npm install --silent)
    }
    log "Starting frontend..."
    (cd "$ROOT/frontend" && setsid nohup npm run dev > "$RUNTIME/frontend.log" 2>&1 < /dev/null &)
fi

# --- Wait until healthy -----------------------------------------------------
log "Waiting for services to become ready..."
for i in $(seq 1 60); do
    ok=1
    curl -sf http://localhost:18080/actuator/health >/dev/null 2>&1 || ok=0
    curl -sf http://localhost:5173 >/dev/null 2>&1 || ok=0
    [ "$ok" = "1" ] && break
    sleep 2
done

if [ "$ok" = "1" ]; then
    log "ALL SERVICES UP:"
    echo "    Dashboard : http://localhost:5173"
    echo "    API       : http://localhost:18080/api/rooms"
    echo "    Logs      : ~/aq-runtime/backend.log, ~/aq-runtime/frontend.log"
else
    log "Something did not come up. Check logs in ~/aq-runtime/"
    exit 1
fi
