#!/usr/bin/env bash
#
# Centralized Secret Resolution - guided demo driver.
#
# Prepares a clean Camunda 8 Run cluster, walks eleven use cases one at a time, and tears the
# environment down again so the next run starts from the same state. Every use case explains what
# it is about, waits for you to press Enter, runs it, and then says what happened and where to look
# for the result.
#
# This terminal is your prompter, not the recording: the things worth filming are the Operate
# screens it points you at, the BPMN models, and the JSON it writes under output/.
#
#   ./run-demo.sh                 full run, tears down at the end
#   ./run-demo.sh --keep-running  leave the cluster up afterwards
#   ./run-demo.sh --yes           no prompts, used to verify the script itself
#   ./run-demo.sh --no-reset      run against an already-running cluster
#
set -euo pipefail

DEMO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SCRIPTS="$DEMO_ROOT/scripts"
C8RUN_DIR="$(cd "$DEMO_ROOT/.." && pwd)/c8run"
OUTPUT_DIR="$DEMO_ROOT/output"

CAMUNDA_VERSION="${CAMUNDA_VERSION:-8.10.0-SNAPSHOT}"
BASE_URL="${BASE_URL:-http://localhost:8080}"
HEALTH_URL="${HEALTH_URL:-http://localhost:9600/actuator/health}"
OPERATE_URL="$BASE_URL/operate"
# The file-based secret store. Kept inside this directory so everything the demo owns lives in
# one place, and git-ignored so no secret file is ever committed.
export SECRET_DEMO_DIR="${SECRET_DEMO_DIR:-$DEMO_ROOT/secrets}"

# Written by 01-create-secrets.sh. Teardown will not delete a secrets directory without it.
SECRET_MARKER=".secret-demo-marker"

TOTAL_USE_CASES=11

KEEP_RUNNING=false
ASSUME_YES=false
DO_RESET=true

CURRENT_USE_CASE="startup"
CLUSTER_STARTED=false
CANCELLED=false

# ---------------------------------------------------------------------------- output helpers ---

if [[ -t 1 && -z "${NO_COLOR:-}" ]]; then
  BOLD=$'\033[1m'; DIM=$'\033[2m'; GREEN=$'\033[32m'; YELLOW=$'\033[33m'; RED=$'\033[31m'
  CYAN=$'\033[36m'; RESET=$'\033[0m'
else
  BOLD=''; DIM=''; GREEN=''; YELLOW=''; RED=''; CYAN=''; RESET=''
fi

rule() { printf '%s%s%s\n' "$DIM" "$(printf '=%.0s' {1..78})" "$RESET"; }
info() { printf '%s\n' "$*"; }
note() { printf '%s%s%s\n' "$DIM" "$*" "$RESET"; }
ok() { printf '%s%s%s\n' "$GREEN" "$*" "$RESET"; }
warn() { printf '%s%s%s\n' "$YELLOW" "$*" "$RESET"; }
fail() { printf '%s%s%s\n' "$RED" "$*" "$RESET" >&2; }

section() {
  printf '\n'
  rule
  printf '%s%s%s\n' "$BOLD" " $*" "$RESET"
  rule
}

# Prints the header of a use case and its narration.
# Usage: use_case <number> <title>
use_case() {
  CURRENT_USE_CASE="$1 - $2"
  printf '\n'
  rule
  printf '%s USE CASE %s of %s - %s%s\n' "$BOLD" "$1" "$TOTAL_USE_CASES" "$2" "$RESET"
  rule
}

# A labelled block of narration lines: block ABOUT "line" "line" ...
block() {
  local label="$1"; shift
  printf '%s%s%s\n' "$CYAN" "$label" "$RESET"
  local line
  for line in "$@"; do
    printf '  %s\n' "$line"
  done
}

# Waits for the operator. Reads from the terminal directly, so prompts still work when this
# script's output is piped or teed somewhere.
pause() {
  local prompt="${1:-Press Enter to continue}"
  if $ASSUME_YES; then
    note "[--yes] $prompt"
    return 0
  fi
  printf '\n%s%s ... %s' "$YELLOW" "$prompt" "$RESET"
  read -r </dev/tty || true
  printf '\n'
}

# Runs a demo script, showing its output and saving stdout for later reference.
# stderr is left alone so response headers and HTTP status lines stay visible.
# Usage: run_step <slug> <command> [args...]
LAST_JSON=''
run_step() {
  local slug="$1"; shift
  local out="$OUTPUT_DIR/$slug.json"
  printf '%s$ %s%s\n' "$DIM" "$*" "$RESET"
  "$@" >"$out"
  cat "$out"
  LAST_JSON="$(cat "$out")"
}

# Polls until a command succeeds. Usage: wait_until <timeout_s> <description> <command...>
wait_until() {
  local timeout="$1" description="$2"; shift 2
  local deadline=$((SECONDS + timeout))
  printf '%swaiting for %s%s' "$DIM" "$description" "$RESET"
  while ! "$@" >/dev/null 2>&1; do
    if ((SECONDS >= deadline)); then
      printf '\n'
      fail "timed out after ${timeout}s waiting for $description"
      return 1
    fi
    printf '.'
    sleep 2
  done
  printf ' done\n'
}

# --------------------------------------------------------------------------------- lifecycle ---

# Removes the state a run leaves behind: the H2 database, the Zeebe partition data, and the log
# files. The log *directory* itself stays: c8run opens camunda.log without creating it, so
# removing the directory makes the next start fail. It also holds a tracked .gitkeep.
clear_state() {
  rm -rf "$C8RUN_DIR/camunda-data" \
         "$C8RUN_DIR/camunda-zeebe-$CAMUNDA_VERSION/data"
  rm -f "$C8RUN_DIR"/log/*.log
}

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || {
    fail "missing prerequisite: $1${2:+ ($2)}"
    return 1
  }
}

preflight() {
  section "Preflight"
  local failed=false

  for cmd in curl jq tar; do
    require_cmd "$cmd" || failed=true
  done

  if [[ -z "${JAVA_HOME:-}" ]] && command -v /usr/libexec/java_home >/dev/null 2>&1; then
    JAVA_HOME="$(/usr/libexec/java_home -v 21 2>/dev/null || true)"
    export JAVA_HOME
  fi
  if [[ -z "${JAVA_HOME:-}" ]]; then
    fail "JAVA_HOME is not set and no JDK 21 was found"
    failed=true
  else
    note "JAVA_HOME=$JAVA_HOME"
  fi

  if [[ ! -x "$C8RUN_DIR/c8run" ]]; then
    fail "c8run CLI not built. From $C8RUN_DIR run: go build -o c8run ./cmd/c8run"
    failed=true
  fi
  if [[ ! -d "$C8RUN_DIR/camunda-zeebe-$CAMUNDA_VERSION" ]]; then
    fail "distribution $CAMUNDA_VERSION is not extracted in $C8RUN_DIR."
    fail "Build it from the repository root, then extract it:"
    fail "  ./mvnw -B -T1C -DskipTests -DskipChecks -Dflatten.skip=true \\"
    fail "         -Dskip.fe.build=false -DskipOptimize package"
    fail "  cp dist/target/camunda-zeebe-$CAMUNDA_VERSION.tar.gz c8run/"
    fail "  (cd c8run && tar -xzf camunda-zeebe-$CAMUNDA_VERSION.tar.gz)"
    failed=true
  fi
  if ! grep -q "^CAMUNDA_VERSION=$CAMUNDA_VERSION\$" "$C8RUN_DIR/.env" 2>/dev/null; then
    fail "c8run/.env must contain CAMUNDA_VERSION=$CAMUNDA_VERSION"
    failed=true
  fi

  if $failed; then
    return 1
  fi
  ok "prerequisites present"
}

# Stops any cluster still running and removes everything a previous run created, so a re-run
# starts from the same state. Safe to call when nothing exists.
reset_environment() {
  section "Reset"

  if [[ -f "$C8RUN_DIR/camunda.process" ]] || curl -sf "$HEALTH_URL" >/dev/null 2>&1; then
    note "stopping a cluster left over from an earlier run"
    (cd "$C8RUN_DIR" && ./c8run stop >/dev/null 2>&1) || true
  fi

  clear_state

  if [[ -f "$SECRET_DEMO_DIR/$SECRET_MARKER" ]]; then
    rm -rf "$SECRET_DEMO_DIR"
  elif [[ -e "$SECRET_DEMO_DIR" ]]; then
    # Never delete a directory this demo did not create.
    fail "$SECRET_DEMO_DIR exists but has no $SECRET_MARKER file, refusing to delete it."
    fail "Point SECRET_DEMO_DIR somewhere else or remove that directory yourself."
    return 1
  fi

  rm -rf "$OUTPUT_DIR"
  mkdir -p "$OUTPUT_DIR"

  # A cluster that was just stopped can hold its listeners for a moment, so give the ports a
  # little time before treating one as taken by something else.
  if command -v lsof >/dev/null 2>&1; then
    local port deadline
    for port in 8080 9600 26500; do
      deadline=$((SECONDS + 30))
      while lsof -ti "tcp:$port" >/dev/null 2>&1; do
        if ((SECONDS >= deadline)); then
          fail "port $port is still in use by another process:"
          lsof -i "tcp:$port" >&2 || true
          return 1
        fi
        sleep 2
      done
    done
  fi

  ok "clean state"
}

start_cluster() {
  section "Start Camunda 8 Run"
  note "secret store directory: $SECRET_DEMO_DIR"
  "$SCRIPTS/01-create-secrets.sh" >/dev/null
  note "config: $DEMO_ROOT/demo-config.yaml (connectors disabled)"
  note "this takes about a minute"

  # c8run resolves --config against its own base directory, so an absolute path ends up
  # concatenated onto it and fails to open. It has to be relative to c8run/.
  local config_rel="../$(basename "$DEMO_ROOT")/demo-config.yaml"

  (
    cd "$C8RUN_DIR"
    ./c8run start \
      --config "$config_rel" \
      --disable-connectors \
      --startup-url "$OPERATE_URL" >"$OUTPUT_DIR/c8run-start.log" 2>&1
  ) || {
    fail "c8run failed to start, see $OUTPUT_DIR/c8run-start.log"
    return 1
  }
  CLUSTER_STARTED=true

  if ! wait_until 180 "the cluster to report healthy" curl -sf "$HEALTH_URL"; then
    fail "last lines of $OUTPUT_DIR/c8run-start.log:"
    tail -15 "$OUTPUT_DIR/c8run-start.log" >&2 || true
    return 1
  fi
  ok "cluster is up at $BASE_URL (Operate: $OPERATE_URL, demo / demo)"
}

on_signal() {
  CANCELLED=true
  exit 130
}

teardown() {
  local exit_code=$?
  trap - EXIT INT TERM

  if $CANCELLED; then
    printf '\n'
    warn "cancelled during: $CURRENT_USE_CASE"
  elif ((exit_code != 0)) && [[ "$CURRENT_USE_CASE" != "done" ]]; then
    printf '\n'
    fail "stopped during: $CURRENT_USE_CASE (exit $exit_code)"
    [[ -f "$C8RUN_DIR/log/camunda.log" ]] && fail "cluster log: $C8RUN_DIR/log/camunda.log"
  fi

  if $KEEP_RUNNING; then
    section "Teardown skipped (--keep-running)"
    info "The cluster is still running at $BASE_URL."
    info "Clean up before the next run by starting this script again, or by hand:"
    info "or by hand:"
    info "  (cd $C8RUN_DIR && ./c8run stop)"
    info "  rm -rf $C8RUN_DIR/camunda-data $C8RUN_DIR/camunda-zeebe-$CAMUNDA_VERSION/data"
    info "  rm -rf $SECRET_DEMO_DIR"
    exit "$exit_code"
  fi

  section "Teardown"
  if [[ -f "$C8RUN_DIR/camunda.process" ]] || curl -sf "$HEALTH_URL" >/dev/null 2>&1; then
    note "stopping the cluster"
    (cd "$C8RUN_DIR" && ./c8run stop >/dev/null 2>&1) || true
  fi
  clear_state
  if [[ -f "$SECRET_DEMO_DIR/$SECRET_MARKER" ]]; then
    rm -rf "$SECRET_DEMO_DIR"
  fi
  ok "environment cleaned, this script can be run again as is"
  note "the output of each use case was kept in $OUTPUT_DIR"
  exit "$exit_code"
}

# ---------------------------------------------------------------------------------- use cases ---

uc01_secret_store() {
  use_case 1 "The file-based secret store"
  block ABOUT \
    "Secrets live in one directory, one file per secret: the file name is the secret name" \
    "and the file contents are the value. That is exactly how Kubernetes projects a mounted" \
    "Secret volume, so the same directory works unchanged with k8s, External Secrets" \
    "Operator or the Secrets Store CSI driver. Files are read on every call, so a rotated" \
    "value needs no restart."
  block ACTIONS "List the store the cluster was started against."
  pause "Press Enter to list the secret store"

  ls -l "$SECRET_DEMO_DIR"
  printf '\n'
  for f in apiToken dbPassword; do
    printf '  %s = %s\n' "$f" "$(cat "$SECRET_DEMO_DIR/$f")"
  done

  block RESULT \
    "Two usable secrets, apiToken and dbPassword." \
    "tls.crt is there on purpose: the store accepts that file name, but a reference is" \
    "  camunda.secrets.<name> with name limited to letters, digits and underscores, so the" \
    "  dot rules it out. Use case 10 shows the listing endpoint leaving it out." \
    "missingToken is deliberately absent until use case 8."
  block "LOOK AT" "This terminal, and the directory $SECRET_DEMO_DIR"
}

uc02_configuration() {
  use_case 2 "Configuring the store"
  block ABOUT \
    "One block of unified configuration points the cluster at a store. The store id must be" \
    "'default': that is the store a camunda.secrets.<name> reference addresses, and any other" \
    "id is rejected at startup. The same keys are overridable per physical tenant." \
    "Both the broker and the gateway read this one configuration."
  block ACTIONS \
    "Show the secrets section of demo-config.yaml, then the line the cluster logged at" \
    "startup when it registered the store."
  pause "Press Enter to show the configuration"

  sed -n '/^camunda:/,/^  security:/p' "$DEMO_ROOT/demo-config.yaml" | sed '$d'
  printf '\n'
  grep -i "secret store" "$C8RUN_DIR/log/camunda.log" || true

  block RESULT \
    "camunda.secrets.stores.file.default.path is the store." \
    "camunda.secrets.cache.ttl is 1m here (default 20m) so a rotated file is picked up during" \
    "  the demo. Both broker and gateway read through that cache." \
    "The commented physical-tenants block is the per-tenant override." \
    "The startup log confirms: Registered file secret store 'default' for physical tenant" \
    "  'default'."
  block "LOOK AT" \
    "$DEMO_ROOT/demo-config.yaml" \
    "$C8RUN_DIR/log/camunda.log"
}

uc03_deploy() {
  use_case 3 "Referencing secrets in a BPMN model"
  block ABOUT \
    "A secret is referenced from a job worker task's input mappings, as a FEEL expression." \
    "Input mappings set variables in the local scope of the element being activated, which is" \
    "what keeps a resolved value isolated to that one job. This is also where outbound" \
    "connector tasks already put their credentials, so connectors inherit it for free."
  block ACTIONS \
    "Deploy three models: the order process (two references), the missing-secret process" \
    "and the cluster-variable process."
  block MODEL \
    '<zeebe:input source="=&quot;Bearer &quot; + camunda.secrets.apiToken" target="authorization" />' \
    '<zeebe:input source="=camunda.secrets.dbPassword" target="dbPassword" />'
  pause "Press Enter to deploy"

  run_step "03-deploy" "$SCRIPTS/02-deploy.sh"

  block RESULT "Three process definitions deployed, each with a version and a key."
  block "LOOK AT" \
    "The model itself: $DEMO_ROOT/models/order-process.bpmn (open it in Desktop Modeler)" \
    "Operate > Processes: $BASE_URL/operate/processes"
}

uc04_literal_rejected() {
  use_case 4 "A reference must be an expression, not a string"
  block ABOUT \
    "The same reference written as a static value, without the leading '=', is rejected at" \
    "deployment. Detection runs on the parsed FEEL syntax tree, not on raw text, so a" \
    "reference inside a string literal stays a literal and a runtime value that merely looks" \
    "like a reference is never resolved."
  block ACTIONS "Deploy a model whose input mapping source is the plain string." \
    "Expected: HTTP 400."
  pause "Press Enter to attempt the deployment"

  local out="$OUTPUT_DIR/04-bad-literal.txt"
  "$SCRIPTS/03-deploy-bad-literal.sh" >"$out" 2>&1 || true
  cat "$out"

  block RESULT \
    "Rejected with HTTP 400. The error names the reference and the fix:" \
    "  must be used as an expression (e.g. '=camunda.secrets.<name>'), not as a string literal."
  block "LOOK AT" "This terminal, or $out"
}

uc05_worker_receives_resolved() {
  use_case 5 "A job worker receives already-resolved values"
  block ABOUT \
    "The engine resolves references at job activation and injects the values into the" \
    "activated job only. The worker needs no secret-store configuration and no resolution" \
    "logic of its own. Resolution never blocks command processing: a job whose secret is not" \
    "cached yet is parked in a dedicated waiting state, resolution is requested in the" \
    "background, and the job is reactivated when the value arrives." \
    "One long-polling request covers all of that. It is held open, and picks the job up as" \
    "soon as it is reactivated, so the worker issues no second call." \
    "Long polling only. Job push does not resolve secrets yet."
  block ACTIONS \
    "Start an instance, then activate over POST /v2/jobs/activation with requestTimeout 30s."
  pause "Press Enter to start an instance"

  run_step "05-instance" "$SCRIPTS/04-start-instance.sh" secret-demo-order
  ORDER_INSTANCE_KEY="$(jq -r '.processInstanceKey' <<<"$LAST_JSON")"
  ok "process instance $ORDER_INSTANCE_KEY"

  pause "Press Enter to activate (one request, held open until the secret resolves)"
  run_step "05-activation" "$SCRIPTS/05-activate-job.sh" send-order
  if [[ "$(jq '.jobs | length' <<<"$LAST_JSON")" == "0" ]]; then
    fail "the long-polling request returned no job before its timeout"
    return 1
  fi

  block RESULT \
    "The activated job carries the resolved values:" \
    "  authorization = Bearer sk-live-DEMO-9f3ad41c" \
    "  dbPassword    = p4ssw0rd-from-the-store" \
    "One request, no polling loop: the job was parked when the request arrived and came back" \
    "on the same request once the background resolution finished." \
    "The worker never learns which store the values came from."
  block "LOOK AT" "$OUTPUT_DIR/05-activation.json"
}

uc06_no_leak() {
  use_case 6 "The resolved value goes nowhere else"
  block ABOUT \
    "The value is substituted into the activation response and nowhere else. What the input" \
    "mapping writes into process state is the reference text, so the value is never" \
    "persisted, never exported, and never displayed in Operate or Tasklist."
  block ACTIONS \
    "Read the variables of the instance from use case 5, then grep the cluster log for both" \
    "secret values."
  pause "Press Enter to read the stored variables"

  wait_until 60 "the variables to be exported" \
    bash -c "curl -sS -u demo:demo -H 'Content-Type: application/json' \
      -X POST '$BASE_URL/v2/variables/search' \
      -d '{\"filter\":{\"processInstanceKey\":\"$ORDER_INSTANCE_KEY\"}}' \
      | jq -e '.page.totalItems > 0' >/dev/null"

  local out="$OUTPUT_DIR/06-variables.txt"
  "$SCRIPTS/06-show-stored-variables.sh" "$ORDER_INSTANCE_KEY" >"$out" 2>&1
  cat "$out"

  block RESULT \
    'The stored variables read "Bearer camunda.secrets.apiToken" and "camunda.secrets.dbPassword".' \
    "The grep over the cluster log finds neither secret value."
  block "LOOK AT" \
    "Operate, the instance's Variables tab" \
    "$BASE_URL/operate/processes/$ORDER_INSTANCE_KEY"
  pause "Press Enter once you have shown the placeholder in Operate"
}

uc07_incident() {
  use_case 7 "A secret that cannot be resolved raises an incident"
  block ABOUT \
    "When a referenced secret is not in the store, the job stays parked while background" \
    "resolution retries with backoff. No worker ever sees a half-resolved job. On exhaustion" \
    "the engine raises a SECRET_RESOLUTION_ERROR incident on the element instance."
  block ACTIONS \
    "Start an instance of the process that references camunda.secrets.missingToken, attempt" \
    "one activation to trigger resolution, then wait for the incident. This activation uses a" \
    "short 5s timeout on purpose: the job is never coming, so there is nothing to wait for."
  pause "Press Enter to start the instance and trigger resolution"

  run_step "07-instance" "$SCRIPTS/04-start-instance.sh" secret-demo-missing
  MISSING_INSTANCE_KEY="$(jq -r '.processInstanceKey' <<<"$LAST_JSON")"
  run_step "07-activation" "$SCRIPTS/05-activate-job.sh" charge-card 5000
  note "empty as expected: the job is parked, and this secret will never resolve"

  wait_until 90 "the incident to be raised" \
    bash -c "curl -sS -u demo:demo -H 'Content-Type: application/json' \
      -X POST '$BASE_URL/v2/incidents/search' \
      -d '{\"filter\":{\"errorType\":\"SECRET_RESOLUTION_ERROR\"}}' \
      | jq -e '.page.totalItems > 0' >/dev/null"

  run_step "07-incident" "$SCRIPTS/07-show-incidents.sh"
  INCIDENT_KEY="$(jq -r '.items[0].incidentKey' <<<"$LAST_JSON")"
  ok "incident $INCIDENT_KEY on process instance $MISSING_INSTANCE_KEY"

  block RESULT \
    "errorType SECRET_RESOLUTION_ERROR, and the message names the recovery path:" \
    "  Failed to resolve secret 'missingToken' from the configured secret store. Ensure the" \
    "  secret exists and the store is available, then resolve the incident to retry." \
    "In Operate this reads as 'Secret resolution error'."
  block "LOOK AT" \
    "Operate, the instance with the incident" \
    "$BASE_URL/operate/processes/$MISSING_INSTANCE_KEY"
  pause "Press Enter once you have shown the incident in Operate"
}

uc08_incident_recovery() {
  use_case 8 "Recovering the incident without a redeploy"
  block ABOUT \
    "Supplying the secret and resolving the incident makes the parked job activatable again." \
    "The next activation resolves against the store, which now holds the file, and the job is" \
    "handed out with the value. No redeploy, no restart, no new process instance."
  block ACTIONS \
    "Write the missing secret file, resolve the incident, then activate the job again."
  pause "Press Enter to add the missing secret to the store"

  "$SCRIPTS/08-add-missing-secret.sh"
  ok "camunda.secrets.missingToken now exists in the store"

  block "MANUAL OPTION" \
    "You can resolve the incident in Operate with the Retry button instead of the next step." \
    "If you do, skip the script call by answering the prompt after doing it in the UI."
  pause "Press Enter to resolve incident $INCIDENT_KEY over the API (or do it in Operate first)"

  if curl -sS -u demo:demo -H 'Content-Type: application/json' \
      -X POST "$BASE_URL/v2/incidents/$INCIDENT_KEY/resolution" \
      -o /dev/null -w '%{http_code}\n' | grep -q '^20'; then
    ok "incident resolved (HTTP 204)"
  else
    note "the incident was already resolved, presumably from Operate"
  fi

  pause "Press Enter to activate the job again (one long-polling request)"
  run_step "08-activation" "$SCRIPTS/05-activate-job.sh" charge-card
  if [[ "$(jq '.jobs | length' <<<"$LAST_JSON")" == "0" ]]; then
    fail "the job did not come back after the incident was resolved"
    return 1
  fi

  block RESULT \
    "The job is handed out with paymentToken = pay-live-DEMO-77c1e8." \
    "The incident is gone and the instance carries on."
  block "LOOK AT" \
    "$OUTPUT_DIR/08-activation.json" \
    "Operate, the same instance now without an incident:" \
    "$BASE_URL/operate/processes/$MISSING_INSTANCE_KEY"
}

uc09_resolve_endpoint() {
  use_case 9 "The resolve endpoint, and who may call it"
  block ABOUT \
    "Inbound connectors cannot receive a job, so they fetch secrets over" \
    "POST /v2/secrets/resolve. The gateway answers from its own store and cache, with no" \
    "round-trip to the broker. Access is guarded by the new SECRET resource with the REVEAL" \
    "permission, checked per reference. Batch-only by design: one round-trip resolves up to" \
    "20 deduplicated references, and each one succeeds or fails on its own."
  block ACTIONS \
    "Resolve as the admin, then as a user with no grant, then grant one single reference and" \
    "send a batch of three. Finish with an unauthenticated call."
  pause "Press Enter to resolve as the admin"

  run_step "09-admin" "$SCRIPTS/12-resolve-secrets.sh" demo camunda.secrets.apiToken
  note "the admin needs no grant: the default admin role carries SECRET:READ and SECRET:REVEAL on *"

  pause "Press Enter to create three users with no grants at all"
  run_step "09-users" "$SCRIPTS/10-create-users.sh"

  pause "Press Enter to resolve as noGrantUser"
  run_step "09-denied" "$SCRIPTS/12-resolve-secrets.sh" noGrantUser camunda.secrets.apiToken
  ok "ACCESS_DENIED, and no value and no hint about existence leaked"

  pause "Press Enter to grant revealUser SECRET:REVEAL on camunda.secrets.apiToken only"
  run_step "09-grant" "$SCRIPTS/11-grant.sh" revealUser REVEAL camunda.secrets.apiToken
  wait_until 60 "the grant to become effective" \
    bash -c "'$SCRIPTS/12-resolve-secrets.sh' revealUser camunda.secrets.apiToken 2>/dev/null \
      | jq -e '.resolved | length > 0' >/dev/null"

  pause "Press Enter to resolve a batch of three as revealUser"
  run_step "09-batch" "$SCRIPTS/12-resolve-secrets.sh" revealUser \
    camunda.secrets.apiToken camunda.secrets.dbPassword camunda.secrets.doesNotExist

  block RESULT \
    "One HTTP 200 carrying three independent outcomes: the granted reference resolved, the" \
    "un-granted one ACCESS_DENIED, and the non-existent one ACCESS_DENIED as well." \
    "That last one is the point: authorization runs before any store lookup, so an" \
    "unauthorized caller is never told whether a secret exists."

  pause "Press Enter to see NOT_FOUND, which only an authorized caller gets"
  run_step "09-not-found" "$SCRIPTS/12-resolve-secrets.sh" demo \
    camunda.secrets.apiToken camunda.secrets.doesNotExist

  pause "Press Enter to call the endpoint with no credentials"
  local out="$OUTPUT_DIR/09-anonymous.txt"
  "$SCRIPTS/12-resolve-secrets.sh" --anonymous camunda.secrets.apiToken >"$out" 2>&1 || true
  cat "$out"

  block RESULT \
    "HTTP 401 without credentials." \
    "Every authenticated response above carried Cache-Control: no-store, so a browser or an" \
    "intermediary proxy cannot retain a response holding secret values."
  block "LOOK AT" "$OUTPUT_DIR/09-*.json and $OUTPUT_DIR/09-anonymous.txt"
}

uc10_list_endpoint() {
  use_case 10 "The reference listing endpoint"
  block ABOUT \
    "POST /v2/secrets/list is what Modeler autocompletion needs: the names a caller may see," \
    "never the values. It is guarded by SECRET:READ, filtered per caller, and REVEAL does not" \
    "imply READ."
  block ACTIONS \
    "Grant listUser SECRET:READ on '*', then list as listUser, as noGrantUser, and as" \
    "revealUser, who holds REVEAL but not READ."
  pause "Press Enter to grant listUser SECRET:READ on *"

  run_step "10-grant" "$SCRIPTS/11-grant.sh" listUser READ '*'
  wait_until 60 "the grant to become effective" \
    bash -c "'$SCRIPTS/13-list-secrets.sh' listUser 2>/dev/null \
      | jq -e '.references | length > 0' >/dev/null"

  pause "Press Enter to list as listUser"
  run_step "10-list-all" "$SCRIPTS/13-list-secrets.sh" listUser

  pause "Press Enter to list as noGrantUser and as revealUser"
  run_step "10-list-none" "$SCRIPTS/13-list-secrets.sh" noGrantUser
  run_step "10-list-reveal-only" "$SCRIPTS/13-list-secrets.sh" revealUser

  block RESULT \
    "listUser sees apiToken, dbPassword and missingToken. Names only, no values." \
    "tls.crt is absent: it cannot form a valid reference, so listing it would only suggest" \
    "  something every expression would reject." \
    "noGrantUser sees nothing, and revealUser sees nothing either: REVEAL does not imply READ."
  block "LOOK AT" "$OUTPUT_DIR/10-list-all.json against $OUTPUT_DIR/10-list-reveal-only.json"
}

uc11_cluster_variable() {
  use_case 11 "A secret reference carried by a cluster variable"
  block ABOUT \
    "A cluster variable of kind SECRET_REFERENCE may hold camunda.secrets.* references inside" \
    "its value. A model then references the cluster variable and names no secret at all. The" \
    "engine folds the reference the cluster variable carries onto the job at creation and" \
    "resolves it exactly like a direct reference. This is the shape the Credentials Manager" \
    "epic stores a credential in."
  block ACTIONS \
    "Create the cluster variable, start an instance of a model that reads" \
    "camunda.vars.env.slackConfig.token, and activate the job."
  pause "Press Enter to create the cluster variable"

  run_step "11-cluster-variable" "$SCRIPTS/14-create-cluster-variable.sh"
  note 'the stored value holds "token": "camunda.secrets.apiToken"'

  pause "Press Enter to start an instance and activate the job"
  run_step "11-instance" "$SCRIPTS/04-start-instance.sh" secret-demo-cluster-var
  run_step "11-activation" "$SCRIPTS/05-activate-job.sh" notify-slack
  if [[ "$(jq '.jobs | length' <<<"$LAST_JSON")" == "0" ]]; then
    fail "the job never came back with a resolved value"
    return 1
  fi

  block RESULT \
    "The worker receives slackToken = sk-live-DEMO-9f3ad41c, resolved out of the cluster" \
    "variable, plus the plain slackUrl next to it." \
    "It comes back immediately here rather than after a short park, because apiToken is" \
    "already in the cache from the earlier use cases. That is the same cache the resolve" \
    "endpoint reads." \
    "The model itself contains no secret name at all."
  block "LOOK AT" \
    "$DEMO_ROOT/models/cluster-variable-process.bpmn" \
    "$OUTPUT_DIR/11-activation.json"
}

# -------------------------------------------------------------------------------------- main ---

usage() {
  sed -n '3,20p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
}

main() {
  while (($# > 0)); do
    case "$1" in
      --keep-running) KEEP_RUNNING=true ;;
      --keep-running=false) KEEP_RUNNING=false ;;
      --yes | -y) ASSUME_YES=true ;;
      --no-reset) DO_RESET=false ;;
      -h | --help)
        usage
        exit 0
        ;;
      *)
        fail "unknown option: $1"
        usage
        exit 2
        ;;
    esac
    shift
  done

  trap teardown EXIT
  trap on_signal INT TERM

  section "Centralized Secret Resolution - guided demo"
  info "Eleven use cases. Each one explains itself, waits for Enter, runs, and tells you what to"
  info "look at. The cluster is torn down at the end so this script can be run again as is."
  info ""
  info "Operate: $OPERATE_URL  (demo / demo)"
  info "Outputs: $OUTPUT_DIR"

  preflight
  if $DO_RESET; then
    reset_environment
    start_cluster
  else
    mkdir -p "$OUTPUT_DIR"
    note "--no-reset: using the cluster that is already running"
    wait_until 60 "the cluster to report healthy" curl -sf "$HEALTH_URL"
  fi

  pause "Environment is ready. Press Enter to start use case 1"

  uc01_secret_store
  uc02_configuration
  uc03_deploy
  uc04_literal_rejected
  uc05_worker_receives_resolved
  uc06_no_leak
  uc07_incident
  uc08_incident_recovery
  uc09_resolve_endpoint
  uc10_list_endpoint
  uc11_cluster_variable

  CURRENT_USE_CASE="done"
  section "All $TOTAL_USE_CASES use cases done"
  pause "Press Enter to tear the environment down"
}

main "$@"
