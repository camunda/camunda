#!/usr/bin/env bash
set -euo pipefail

# Stands up a local environment for the nightly fix agent's live-verify loop
# and writes a matching .env into qa/c8-orchestration-cluster-e2e-test-suite/.
# Run from anywhere inside the repo; must be run from the branch/checkout the
# fix is targeting (main / stable/8.7 / stable/8.8 / stable/8.9) -- the script
# self-detects the docker-compose service shape from that checkout rather
# than taking a version argument, so it never drifts from the code it's
# actually verifying against.
#
# Usage:
#   scripts/start-verify-env.sh es [v1|v2]   # default -- Elasticsearch via docker compose
#   scripts/start-verify-env.sh h2 [v1|v2]   # RDBMS-flagged failures: same H2 setup the
#                                            # on-demand workflow's RDBMS job uses --
#                                            # no container, builds camunda from source.
#
# The second argument MUST match the dispatched test's `tasklist_mode` field
# when the failure is an e2e entry -- running the wrong Tasklist generation
# can silently fail to reproduce a v1-specific bug (or falsely "verify" a fix
# that was never exercised in the mode that actually failed). Defaults to v2
# (matching docker-compose.yml's own default) when omitted, which is correct
# for API-only dispatches (tasklist_mode is absent from api test_specs
# entries) and for `main` (whose e2e matrix is v2-only anyway).
#
# Idempotent-ish: safe to call once per verify session. Call stop-verify-env.sh
# (same directory) to tear down when the verify loop is done.

DATABASE_MODE="${1:-es}"
TASKLIST_MODE="${2:-v2}"
case "${TASKLIST_MODE}" in
  v1) TASKLIST_V2_ENABLED=false ;;
  v2) TASKLIST_V2_ENABLED=true ;;
  *)
    echo "::error::Unknown tasklist mode '${TASKLIST_MODE}' -- expected v1 or v2." >&2
    exit 1
    ;;
esac

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SUITE_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
REPO_ROOT="$(cd "${SUITE_DIR}/../.." && pwd)"
CONFIG_DIR="${SUITE_DIR}/config"

write_env() {
  # $1 = extra lines to append after the common ones
  cat > "${SUITE_DIR}/.env" <<EOF
LOCAL_TEST=false
CAMUNDA_AUTH_STRATEGY=BASIC
CAMUNDA_BASIC_AUTH_USERNAME=demo
CAMUNDA_BASIC_AUTH_PASSWORD=demo
$1
EOF
  echo "Wrote ${SUITE_DIR}/.env:"
  cat "${SUITE_DIR}/.env"
}

wait_for() {
  # $1 = URL to poll, $2 = label for logging
  local url="$1" label="$2"
  echo "Waiting for ${label} (${url})..."
  for i in $(seq 1 60); do
    if curl -sf -u demo:demo "${url}" >/dev/null 2>&1; then
      echo "${label} is ready."
      return 0
    fi
    echo "Waiting... (${i}/60)"
    sleep 5
  done
  echo "::error::${label} did not become ready in time." >&2
  return 1
}

if [ "${DATABASE_MODE}" = "h2" ]; then
  # Mirrors c8-orchestration-cluster-e2e-tests-on-demand.yml's
  # "Start Camunda with H2/RDBMS" step exactly -- no container, one
  # in-memory H2 instance, built from the currently-checked-out source.
  echo "=== Building Camunda distribution (this mirrors the on-demand workflow's RDBMS job and takes several minutes) ==="
  (cd "${REPO_ROOT}" && ./mvnw install -Dquickly -T1C)

  mkdir -p "${REPO_ROOT}/dev-dist"
  exploded="${REPO_ROOT}/dist/target/camunda-zeebe"
  if [ -d "${exploded}/bin" ]; then
    cp -a "${exploded}/." "${REPO_ROOT}/dev-dist/"
  else
    tarball=$(find "${REPO_ROOT}/dist/target" -maxdepth 1 -name 'camunda-zeebe-*.tar.gz' | head -1)
    if [ -z "${tarball}" ]; then
      echo "::error::No dist build output found under ${REPO_ROOT}/dist/target" >&2
      exit 1
    fi
    tar xzf "${tarball}" --strip-components=1 -C "${REPO_ROOT}/dev-dist"
  fi
  chmod +x "${REPO_ROOT}/dev-dist/bin/camunda"

  # This block is a deliberate full mirror of on-demand's "Start Camunda with
  # H2/RDBMS" step -- every var it exports, exported here too, so the verify
  # environment can't diverge from production and produce a false
  # verified/unverified outcome. If that step changes, update this to match.
  export SPRING_PROFILES_ACTIVE="broker,consolidated-auth,admin,tasklist,operate"
  export ZEEBE_CLOCK_CONTROLLED="true"
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
  export CAMUNDA_DATA_SECONDARY_STORAGE_TYPE="rdbms"
  export CAMUNDA_DATA_SECONDARY_STORAGE_RDBMS_URL="jdbc:h2:mem:cpt;DB_CLOSE_DELAY=-1;MODE=PostgreSQL"
  export CAMUNDA_DATA_SECONDARY_STORAGE_RDBMS_USERNAME="sa"
  export CAMUNDA_DATA_SECONDARY_STORAGE_RDBMS_PASSWORD=""
  export CAMUNDA_DATA_SECONDARY_STORAGE_RDBMS_AUTO_DDL="true"
  export CAMUNDA_DATA_SECONDARY_STORAGE_RDBMS_FLUSH_INTERVAL="PT0S"
  export CAMUNDA_DATA_SECONDARY_STORAGE_RDBMS_HISTORY_DEFAULT_HISTORY_TTL="PT60S"
  export CAMUNDA_DATA_SECONDARY_STORAGE_RDBMS_HISTORY_MIN_HISTORY_CLEANUP_INTERVAL="PT10S"
  export CAMUNDA_DATA_SECONDARY_STORAGE_RDBMS_HISTORY_MAX_HISTORY_CLEANUP_INTERVAL="PT60S"
  export CAMUNDA_DATA_AUDITLOG_ENABLED="true"
  export CAMUNDA_DATA_AUDITLOG_USER_CATEGORIES_0="ADMIN"
  export CAMUNDA_DATA_AUDITLOG_USER_CATEGORIES_1="DEPLOYED_RESOURCES"
  export CAMUNDA_DATA_AUDITLOG_USER_CATEGORIES_2="USER_TASKS"
  export CAMUNDA_DATA_AUDITLOG_USER_EXCLUDES_0="VARIABLE"
  export CAMUNDA_DATA_AUDITLOG_USER_EXCLUDES_1="BATCH"
  export CAMUNDA_DATA_AUDITLOG_CLIENT_CATEGORIES_0="ADMIN"
  export CAMUNDA_DATA_AUDITLOG_CLIENT_EXCLUDES_0="PROCESS_INSTANCE"
  export CAMUNDA_PROCESSINSTANCECREATION_BUSINESSIDUNIQUENESSENABLED="true"
  # on-demand's own H2/RDBMS job never varies this server-side (there is no
  # tasklist_mode dimension on its RDBMS matrix, only camunda_mode) -- match
  # it exactly rather than introducing a toggle production doesn't have.
  if [ "${2:-}" ] && [ "${2}" != "v2" ]; then
    echo "::warning::RDBMS/H2 verify never varies Tasklist mode (on-demand's own H2/RDBMS job doesn't either) -- ignoring the requested '${2}'." >&2
  fi

  "${REPO_ROOT}/dev-dist/bin/camunda" > "${REPO_ROOT}/dev-dist/camunda.log" 2>&1 &
  echo $! > "${REPO_ROOT}/dev-dist/camunda.pid"
  echo "Camunda starting (PID $(cat "${REPO_ROOT}/dev-dist/camunda.pid"))..."

  wait_for "http://localhost:8080/v2/topology" "Camunda (H2/RDBMS)" || {
    tail -100 "${REPO_ROOT}/dev-dist/camunda.log" >&2 || true
    exit 1
  }

  # Matches on-demand's own H2/RDBMS "Run API tests" client env exactly.
  write_env "CORE_APPLICATION_URL=http://localhost:8080
ZEEBE_REST_ADDRESS=http://localhost:8080
DATABASE=RDBMS
CAMUNDA_TASKLIST_V2_MODE_ENABLED=false"

  exit 0
fi

# --- Elasticsearch path (default) ---
# Self-detect the docker-compose shape: main/stable-8.8/stable-8.9 have one
# consolidated `camunda` service; stable/8.7 has separate zeebe/tasklist/operate
# services with no consolidated image. Never assume -- always read the
# checked-out branch's own compose file.
if grep -qE '^\s{2}camunda:' "${CONFIG_DIR}/docker-compose.yml"; then
  echo "=== Detected consolidated 'camunda' service shape (Tasklist ${TASKLIST_MODE}) ==="
  # Match on-demand's own invocation exactly: CAMUNDA_TASKLIST_V2_MODE_ENABLED
  # must be set at `docker compose up` time -- the compose file only reads it
  # via ${CAMUNDA_TASKLIST_V2_MODE_ENABLED:-true} at container-creation time,
  # so exporting it after the container exists has no effect.
  ( cd "${CONFIG_DIR}" && CAMUNDA_TASKLIST_V2_MODE_ENABLED="${TASKLIST_V2_ENABLED}" \
      DATABASE=elasticsearch docker compose up -d camunda )
  wait_for "http://localhost:8080/v2/topology" "Camunda"
  write_env "CORE_APPLICATION_URL=http://localhost:8080
ZEEBE_REST_ADDRESS=http://localhost:8080
DATABASE_CONTAINER=elasticsearch
CAMUNDA_TASKLIST_V2_MODE_ENABLED=${TASKLIST_V2_ENABLED}"
else
  echo "=== Detected pre-consolidation shape (zeebe/tasklist/operate) -- e.g. stable/8.7 ==="
  if [ "${2:-}" = "v2" ]; then
    echo "::warning::stable/8.7's Tasklist predates the v1/v2 toggle -- there is nothing to switch, ignoring the requested v2 mode." >&2
  fi
  ( cd "${CONFIG_DIR}" && DATABASE=elasticsearch docker compose up -d tasklist operate )
  wait_for "http://localhost:8080" "Tasklist"
  wait_for "http://localhost:8081" "Operate"
  write_env "CORE_APPLICATION_TASKLIST_URL=http://localhost:8080
CORE_APPLICATION_OPERATE_URL=http://localhost:8081
ZEEBE_REST_ADDRESS=http://localhost:8089
DATABASE_CONTAINER=elasticsearch"
fi
