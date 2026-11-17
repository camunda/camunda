#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE'
Usage:
  ./postgres-replica-lag.sh status [all|postgres-replica-1|postgres-replica-2|pg-replica1|pg-replica2]
  ./postgres-replica-lag.sh set <delay> [all|postgres-replica-1|postgres-replica-2|pg-replica1|pg-replica2]
  ./postgres-replica-lag.sh reset [all|postgres-replica-1|postgres-replica-2|pg-replica1|pg-replica2]

Examples:
  ./postgres-replica-lag.sh set 5s
  ./postgres-replica-lag.sh set 30s postgres-replica-1
  ./postgres-replica-lag.sh status
  ./postgres-replica-lag.sh reset all

The delay is PostgreSQL's recovery_min_apply_delay on the standby. It delays replaying
commits on replicas, which makes replica reads intentionally stale while primary writes continue.
USAGE
}

command=${1:-status}
shift || true

postgres_user=${POSTGRES_SUPERUSER:-postgres}
postgres_password=${POSTGRES_SUPERUSER_PASSWORD:-camunda}
postgres_database=${POSTGRES_DATABASE:-postgres}

resolve_targets() {
  local target=${1:-all}
  case "${target}" in
    all)
      printf '%s\n' pg-replica1 pg-replica2
      ;;
    postgres-replica-1|pg-replica1)
      printf '%s\n' pg-replica1
      ;;
    postgres-replica-2|pg-replica2)
      printf '%s\n' pg-replica2
      ;;
    *)
      echo "Unknown replica target: ${target}" >&2
      usage >&2
      exit 2
      ;;
  esac
}

run_psql() {
  local container=$1
  local sql=$2
  docker exec \
    -e "PGPASSWORD=${postgres_password}" \
    "${container}" \
    psql \
      -U "${postgres_user}" \
      -d "${postgres_database}" \
      -v ON_ERROR_STOP=1 \
      -X \
      -q \
      -c "${sql}"
}

status() {
  local container=$1
  echo "== ${container} =="
  run_psql "${container}" "SHOW recovery_min_apply_delay;"
  run_psql "${container}" "SELECT COALESCE((now() - pg_last_xact_replay_timestamp())::text, 'no replay timestamp yet') AS observed_replay_lag;"
}

set_delay() {
  local delay=$1
  local container=$2
  if [[ ! ${delay} =~ ^(0|[0-9]+(ms|s|min|h|d))$ ]]; then
    echo "Invalid delay '${delay}'. Use values like 0, 500ms, 5s, 1min, 1h, or 1d." >&2
    exit 2
  fi

  echo "Setting recovery_min_apply_delay=${delay} on ${container}"
  run_psql "${container}" "ALTER SYSTEM SET recovery_min_apply_delay = '${delay}';"
  run_psql "${container}" "SELECT pg_reload_conf();"
  status "${container}"
}

reset_delay() {
  local container=$1
  echo "Resetting recovery_min_apply_delay on ${container}"
  run_psql "${container}" "ALTER SYSTEM RESET recovery_min_apply_delay;"
  run_psql "${container}" "SELECT pg_reload_conf();"
  status "${container}"
}

case "${command}" in
  status)
    target=${1:-all}
    while IFS= read -r container; do
      status "${container}"
    done < <(resolve_targets "${target}")
    ;;
  set)
    delay=${1:-}
    target=${2:-all}
    if [[ -z ${delay} ]]; then
      usage >&2
      exit 2
    fi
    while IFS= read -r container; do
      set_delay "${delay}" "${container}"
    done < <(resolve_targets "${target}")
    ;;
  reset)
    target=${1:-all}
    while IFS= read -r container; do
      reset_delay "${container}"
    done < <(resolve_targets "${target}")
    ;;
  -h|--help|help)
    usage
    ;;
  *)
    echo "Unknown command: ${command}" >&2
    usage >&2
    exit 2
    ;;
esac

