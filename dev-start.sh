#!/usr/bin/env bash
set -euo pipefail

# ------------------------------------------------------------------
# dev-start.sh – Build & run Camunda locally for E2E testing.
#
# Usage:
#   ./dev-start.sh              Full quick build + start (H2/RDBMS)
#   ./dev-start.sh --es         Full quick build + start (Elasticsearch)
#   ./dev-start.sh --restart    Rebuild dist only + restart
#   ./dev-start.sh --gateway    Rebuild gateway + dist + restart
#   ./dev-start.sh --skip-build Start from previous build (no build)
#   ./dev-start.sh --stop       Stop a running instance
#   ./dev-start.sh --status     Check if the server is running
#   ./dev-start.sh --log        Tail the server log
#
# The --es flag can be combined with other modes:
#   ./dev-start.sh --es --skip-build
#   ./dev-start.sh --es --restart
# ------------------------------------------------------------------

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
DIST_DIR="$SCRIPT_DIR/dev-dist"
PID_FILE="$DIST_DIR/camunda.pid"
LOG_FILE="$DIST_DIR/camunda.log"
DOCKER_COMPOSE_DIR="$SCRIPT_DIR/qa/c8-orchestration-cluster-e2e-test-suite/config"
LINT_SPEC="$SCRIPT_DIR/dev-lint-spec.sh"

MODE="full"
USE_ES=false
for arg in "$@"; do
  case "$arg" in
    --es)         USE_ES=true ;;
    --restart)    MODE="restart" ;;
    --gateway)    MODE="gateway" ;;
    --skip-build) MODE="skip-build" ;;
    --stop)       MODE="stop" ;;
    --status)     MODE="status" ;;
    --log)        MODE="log" ;;
    -h|--help)    MODE="help" ;;
    *)            echo "Unknown option: $arg"; exit 1 ;;
  esac
done

kill_pid() {
  local pid=$1
  if kill -0 "$pid" 2>/dev/null; then
    echo "Stopping process (PID $pid)..."
    kill "$pid"
    for i in $(seq 1 30); do
      kill -0 "$pid" 2>/dev/null || return 0
      sleep 0.5
    done
    if kill -0 "$pid" 2>/dev/null; then
      echo "Force-killing (PID $pid)..."
      kill -9 "$pid" 2>/dev/null || true
    fi
  fi
}

stop_camunda() {
  # Stop via PID file if available
  if [ -f "$PID_FILE" ]; then
    kill_pid "$(cat "$PID_FILE")"
    rm -f "$PID_FILE"
  fi

  # Also kill anything holding Camunda ports (catches orphaned processes)
  for port in 8080 26500 26501 26502 9600; do
    local pid
    pid=$(lsof -ti :"$port" 2>/dev/null | head -1) || true
    if [ -n "$pid" ]; then
      echo "Port $port still in use by PID $pid"
      kill_pid "$pid"
    fi
  done
}

stop_elasticsearch() {
  if [ -f "$DOCKER_COMPOSE_DIR/docker-compose.yml" ]; then
    echo "Stopping Elasticsearch container..."
    DATABASE=elasticsearch docker compose -f "$DOCKER_COMPOSE_DIR/docker-compose.yml" down elasticsearch 2>/dev/null || true
  fi
}

start_elasticsearch() {
  echo "=== Starting Elasticsearch via Docker ==="
  DATABASE=elasticsearch docker compose -f "$DOCKER_COMPOSE_DIR/docker-compose.yml" up -d elasticsearch

  echo "Waiting for Elasticsearch to be ready..."
  for i in $(seq 1 60); do
    if curl -sf http://localhost:9200/_cluster/health >/dev/null 2>&1; then
      echo "Elasticsearch is ready."
      return 0
    fi
    sleep 2
  done
  echo "ERROR: Elasticsearch failed to start in time."
  exit 1
}

build_full() {
  echo "=== Full clean build (skipping tests & checks) ==="
  ./mvnw clean install -Dquickly -T1C
}

build_dist_only() {
  echo "=== Rebuilding dist module only ==="
  ./mvnw install -Dquickly -T1C -pl dist -am
}

build_gateway_and_dist() {
  echo "=== Rebuilding gateway + dist ==="
  ./mvnw install -Dquickly -T1C -pl zeebe/gateway,dist -am
}

extract_dist() {
  echo "=== Extracting dist ==="
  rm -rf "$DIST_DIR"
  mkdir -p "$DIST_DIR"

  # Prefer the exploded directory (always present), fall back to tarball
  local exploded="$SCRIPT_DIR/dist/target/camunda-zeebe"
  if [ -d "$exploded/bin" ]; then
    cp -a "$exploded"/. "$DIST_DIR"/
  else
    local tarball
    tarball=$(find "$SCRIPT_DIR/dist/target" -maxdepth 1 -name 'camunda-zeebe-*.tar.gz' 2>/dev/null | head -1)
    if [ -z "$tarball" ]; then
      echo "ERROR: No dist build output found. Run ./dev-start.sh (full build) first."
      exit 1
    fi
    tar xzf "$tarball" --strip-components=1 -C "$DIST_DIR"
  fi
}

start_camunda() {
  local backend="H2/RDBMS"
  if [ "$USE_ES" = true ]; then
    backend="Elasticsearch"
  fi
  echo "=== Starting Camunda with $backend ==="
  local camunda_bin="$DIST_DIR/bin/camunda"
  chmod +x "$camunda_bin"

  # Clean data and logs from previous runs to avoid corruption
  rm -rf "$DIST_DIR/data" "$DIST_DIR/logs"

  export ZEEBE_CLOCK_CONTROLLED="true"

  # Auth: match CI (protected API, auth enabled, basic auth with demo+lisa users)
  export CAMUNDA_SECURITY_AUTHENTICATION_UNPROTECTEDAPI="false"
  export CAMUNDA_SECURITY_AUTHORIZATIONS_ENABLED="true"
  export CAMUNDA_SECURITY_AUTHENTICATION_METHOD="BASIC"
  export CAMUNDA_SECURITY_MULTITENANCY_CHECKSENABLED="false"
  export CAMUNDA_SECURITY_INITIALIZATION_USERS_0_USERNAME="demo"
  export CAMUNDA_SECURITY_INITIALIZATION_USERS_0_PASSWORD="demo"
  export CAMUNDA_SECURITY_INITIALIZATION_USERS_0_NAME="Demo"
  export CAMUNDA_SECURITY_INITIALIZATION_USERS_0_EMAIL="demo@example.com"
  export CAMUNDA_SECURITY_INITIALIZATION_DEFAULTROLES_ADMIN_USERS_0="demo"
  export CAMUNDA_SECURITY_INITIALIZATION_USERS_1_USERNAME="lisa"
  export CAMUNDA_SECURITY_INITIALIZATION_USERS_1_PASSWORD="lisa"
  export CAMUNDA_SECURITY_INITIALIZATION_USERS_1_NAME="lisa"
  export CAMUNDA_SECURITY_INITIALIZATION_USERS_1_EMAIL="lisa@example.com"
  export CAMUNDA_SECURITY_INITIALIZATION_DEFAULTROLES_ADMIN_USERS_1="lisa"

  if [ "$USE_ES" = true ]; then
    # Elasticsearch: full CI profile set (tasklist/operate/e2e-test require ES)
    export SPRING_PROFILES_ACTIVE="e2e-test,consolidated-auth,tasklist,broker,operate,admin"

    # Database: Elasticsearch
    export CAMUNDA_DATABASE_URL="http://localhost:9200"
    export CAMUNDA_DATA_SECONDARY_STORAGE_TYPE="elasticsearch"
    export CAMUNDA_DATA_SECONDARY_STORAGE_ELASTICSEARCH_URL="http://localhost:9200"
    export CAMUNDA_DATA_SECONDARYSTORAGE_ELASTICSEARCH_WAITFORIMPORTERS="false"
    export ZEEBE_BROKER_EXPORTERS_CAMUNDAEXPORTER_CLASSNAME="io.camunda.exporter.CamundaExporter"
    export ZEEBE_BROKER_EXPORTERS_CAMUNDAEXPORTER_ARGS_CONNECT_URL="http://localhost:9200"
    export ZEEBE_BROKER_EXPORTERS_CAMUNDAEXPORTER_ARGS_BULK_SIZE="100"
    export ZEEBE_BROKER_EXPORTERS_CAMUNDAEXPORTER_ARGS_INDEX_SHOULDWAITFORIMPORTERS="false"
  else
    # H2 in-memory environment, matching CI docker-compose config
    # Profiles: broker + consolidated-auth + admin. The CI also uses tasklist, operate,
    # and e2e-test profiles, but those require Elasticsearch (ProcessCache bean).
    # With RDBMS, the REST API endpoints still work — only the legacy webapp UIs are missing.
    export SPRING_PROFILES_ACTIVE="broker,consolidated-auth,admin"

    # Database: H2 in-memory with RDBMS secondary storage
    export CAMUNDA_DATABASE_URL="jdbc:h2:mem:cpt;DB_CLOSE_DELAY=-1;MODE=PostgreSQL"
    export CAMUNDA_DATABASE_TYPE="rdbms"
    export CAMUNDA_DATABASE_USERNAME="sa"
    export CAMUNDA_DATABASE_PASSWORD=""
    export CAMUNDA_DATA_SECONDARY_STORAGE_TYPE="rdbms"
    export ZEEBE_BROKER_EXPORTERS_RDBMS_CLASSNAME="io.camunda.exporter.rdbms.RdbmsExporter"
    export ZEEBE_BROKER_EXPORTERS_RDBMS_ARGS_FLUSH_INTERVAL="PT0S"
    export ZEEBE_BROKER_EXPORTERS_RDBMS_ARGS_DEFAULT_HISTORY_TTL="PT2S"
    export ZEEBE_BROKER_EXPORTERS_RDBMS_ARGS_MIN_HISTORY_CLEANUP_INTERVAL="PT2S"
    export ZEEBE_BROKER_EXPORTERS_RDBMS_ARGS_MAX_HISTORY_CLEANUP_INTERVAL="PT5S"
  fi

  # Audit log: match CI configuration
  export CAMUNDA_DATA_AUDITLOG_ENABLED="true"
  export CAMUNDA_DATA_AUDITLOG_USER_CATEGORIES_0="ADMIN"
  export CAMUNDA_DATA_AUDITLOG_USER_CATEGORIES_1="DEPLOYED_RESOURCES"
  export CAMUNDA_DATA_AUDITLOG_USER_CATEGORIES_2="USER_TASKS"
  export CAMUNDA_DATA_AUDITLOG_USER_EXCLUDES_0="VARIABLE"
  export CAMUNDA_DATA_AUDITLOG_USER_EXCLUDES_1="BATCH"
  export CAMUNDA_DATA_AUDITLOG_CLIENT_CATEGORIES_0="ADMIN"
  export CAMUNDA_DATA_AUDITLOG_CLIENT_EXCLUDES_0="PROCESS_INSTANCE"

  export CAMUNDA_REST_RESPONSE_VALIDATION_ENABLED="true"

  # Start in foreground, tee to log file
  echo "Log: $LOG_FILE"
  echo "REST API:       http://localhost:8080"
  echo "gRPC API:       http://localhost:26500"
  echo "Management API: http://localhost:9600/actuator"
  echo ""
  echo "Press Ctrl+C to stop."
  echo "---"

  # Trap to clean up on exit (Camunda + ES container if applicable)
  trap 'stop_camunda; if [ "$USE_ES" = true ]; then stop_elasticsearch; fi; exit 0' INT TERM

  "$camunda_bin" "$@" 2>&1 | tee "$LOG_FILE" &
  local bg_pid=$!
  echo "$bg_pid" > "$PID_FILE"
  wait "$bg_pid" || true
  rm -f "$PID_FILE"
}

lint_spec() {
  if [ -x "$LINT_SPEC" ]; then
    echo "=== Validating OpenAPI spec ==="
    "$LINT_SPEC"
  fi
}

# --- Main ---
cd "$SCRIPT_DIR"

case "$MODE" in
  help)
    cat <<'EOF'
dev-start.sh – Build & run Camunda locally for E2E testing.

Usage: ./dev-start.sh [options]

Options:
  (none)        Full quick build (mvnw install -Dquickly) + start (H2/RDBMS)
  --es          Use Elasticsearch backend (starts ES via Docker)
  --restart     Rebuild dist module only + restart
  --gateway     Rebuild gateway + dist + restart
  --skip-build  Start from the last build without recompiling
  --stop        Stop a running instance (and ES container if running)
  --status      Check if the server is running
  --log         Tail the server log (Ctrl+C to stop tailing)
  -h, --help    Show this help

The --es flag can be combined with other modes:
  ./dev-start.sh --es              Full build + ES
  ./dev-start.sh --es --skip-build Start with ES, no build
  ./dev-start.sh --es --restart    Rebuild dist + ES

Server endpoints (when running):
  REST API        http://localhost:8080
  gRPC API        http://localhost:26500
  Management API  http://localhost:9600/actuator

Backends:
  Default (H2/RDBMS):  H2 in-memory, profiles: broker,consolidated-auth,admin
  --es (Elasticsearch): Docker ES on :9200, profiles: e2e-test,consolidated-auth,tasklist,broker,operate,admin

Auth: basic auth (demo/demo, lisa/lisa), response validation enabled.
Log file: dev-dist/camunda.log
EOF
    ;;
  full)
    lint_spec
    stop_camunda
    if [ "$USE_ES" = true ]; then start_elasticsearch; fi
    build_full
    extract_dist
    start_camunda
    ;;
  restart)
    lint_spec
    stop_camunda
    if [ "$USE_ES" = true ]; then start_elasticsearch; fi
    build_dist_only
    extract_dist
    start_camunda
    ;;
  gateway)
    lint_spec
    stop_camunda
    if [ "$USE_ES" = true ]; then start_elasticsearch; fi
    build_gateway_and_dist
    extract_dist
    start_camunda
    ;;
  skip-build)
    lint_spec
    stop_camunda
    if [ "$USE_ES" = true ]; then start_elasticsearch; fi
    if [ ! -d "$DIST_DIR/bin" ]; then
      extract_dist
    fi
    start_camunda
    ;;
  stop)
    stop_camunda
    stop_elasticsearch
    echo "Camunda stopped."
    ;;
  status)
    if pid=$(lsof -ti :26500 2>/dev/null | head -1) && [ -n "$pid" ]; then
      echo "Camunda is running (PID $pid)"
      health=$(curl -sf http://localhost:9600/actuator/health/status 2>/dev/null) || health="unreachable"
      echo "Health: $health"
    else
      echo "Camunda is not running."
      exit 1
    fi
    ;;
  log)
    if [ ! -f "$LOG_FILE" ]; then
      echo "No log file found at $LOG_FILE"
      exit 1
    fi
    tail -f "$LOG_FILE"
    ;;
esac
