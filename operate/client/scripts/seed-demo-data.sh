#!/usr/bin/env bash
#
# Copyright Camunda Services GmbH
#
# Licensed under the Camunda License 1.0. You may not use this file
# except in compliance with the Camunda License 1.0.
#
# Seeds a Camunda 8 cluster with demo BPMN deployments and varied process
# instances/incidents for the Operate Notebook hackday demo.
#
# Usage: bash seed-demo-data.sh [BASE_URL]
# Default BASE_URL: http://host.docker.internal:8080

set -euo pipefail

BASE_URL="${1:-http://host.docker.internal:8080}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BPMN_DIR="${SCRIPT_DIR}/sample-bpmn"

log()  { echo "[seed] $*" >&2; }
warn() { echo "[seed][WARN] $*" >&2; }
err()  { echo "[seed][ERR ] $*" >&2; }

require_jq() {
  if ! command -v jq >/dev/null 2>&1; then
    err "jq is required but not installed."
    exit 1
  fi
}

api() {
  # api METHOD PATH [JSON_BODY]
  local method="$1" path="$2" body="${3:-}"
  if [[ -n "$body" ]]; then
    curl -sS -X "$method" "${BASE_URL}${path}" \
      -H 'content-type: application/json' -d "$body"
  else
    curl -sS -X "$method" "${BASE_URL}${path}" \
      -H 'content-type: application/json'
  fi
}

check_topology() {
  local code
  code="$(curl -sS -o /dev/null -w '%{http_code}' "${BASE_URL}/v2/topology")"
  if [[ "$code" != "200" ]]; then
    err "Cluster ${BASE_URL} not reachable (HTTP ${code})."
    exit 1
  fi
  log "Cluster reachable at ${BASE_URL}"
}

# Returns existing processDefinitionKey for a processDefinitionId, or empty.
find_definition_key() {
  local pdid="$1"
  api POST /v2/process-definitions/search \
    "{\"filter\":{\"processDefinitionId\":\"${pdid}\"},\"sort\":[{\"field\":\"version\",\"order\":\"DESC\"}],\"page\":{\"limit\":1}}" \
    | jq -r '.items[0].processDefinitionKey // empty'
}

deploy_if_missing() {
  # deploy_if_missing PROCESS_DEFINITION_ID FILE
  local pdid="$1" file="$2"
  local existing
  existing="$(find_definition_key "$pdid")"
  if [[ -n "$existing" ]]; then
    log "Already deployed: ${pdid} (key=${existing}) — skipping deploy"
    echo "$existing"
    return
  fi
  log "Deploying ${pdid} from ${file}..."
  local resp
  resp="$(curl -sS -X POST "${BASE_URL}/v2/deployments" -F "resources=@${file}")"
  local key
  key="$(echo "$resp" | jq -r '.deployments[0].processDefinition.processDefinitionKey // empty')"
  if [[ -z "$key" ]]; then
    err "Deployment of ${pdid} failed: ${resp}"
    exit 1
  fi
  local ver
  ver="$(echo "$resp" | jq -r '.deployments[0].processDefinition.processDefinitionVersion')"
  log "Deployed ${pdid} v${ver} (key=${key})"
  echo "$key"
}

create_instance() {
  # create_instance PROCESS_DEFINITION_ID JSON_VARIABLES
  local pdid="$1" vars="$2"
  local resp
  resp="$(api POST /v2/process-instances \
    "{\"processDefinitionId\":\"${pdid}\",\"variables\":${vars}}")"
  local key
  key="$(echo "$resp" | jq -r '.processInstanceKey // empty')"
  if [[ -z "$key" ]]; then
    err "Failed to create instance for ${pdid}: ${resp}"
    return 1
  fi
  echo "$key"
}

# Activates up to N jobs of given type and fails them all with retries=0.
# Echoes the count of jobs failed.
fail_jobs() {
  # fail_jobs JOB_TYPE MAX_JOBS ERROR_MESSAGE
  local job_type="$1" max="$2" msg="$3"
  local resp
  resp="$(api POST /v2/jobs/activation \
    "{\"type\":\"${job_type}\",\"timeout\":120000,\"maxJobsToActivate\":${max},\"worker\":\"seed-worker\"}")"
  local keys
  keys="$(echo "$resp" | jq -r '.jobs[].jobKey')"
  local count=0
  for k in $keys; do
    local code
    code="$(curl -sS -o /dev/null -w '%{http_code}' \
      -X POST "${BASE_URL}/v2/jobs/${k}/failure" \
      -H 'content-type: application/json' \
      -d "{\"errorMessage\":$(jq -Rsa . <<<"$msg"),\"retries\":0}")"
    if [[ "$code" == "204" ]]; then
      count=$((count + 1))
    else
      warn "Failing job ${k} returned HTTP ${code}"
    fi
  done
  echo "$count"
}

# Activates up to MAX jobs of TYPE, then completes COMPLETE_PCT% and fails the
# rest with retries=0 (creating incidents). Output: "<completed> <failed>".
funnel_jobs() {
  # funnel_jobs JOB_TYPE MAX_JOBS COMPLETE_PCT ERROR_MESSAGE
  local job_type="$1" max="$2" complete_pct="$3" msg="$4"
  local resp
  resp="$(api POST /v2/jobs/activation \
    "{\"type\":\"${job_type}\",\"timeout\":120000,\"maxJobsToActivate\":${max},\"worker\":\"seed-worker\"}")"
  local keys
  keys="$(echo "$resp" | jq -r '.jobs[].jobKey')"
  local total=0 completed=0 failed=0
  for k in $keys; do total=$((total + 1)); done
  if (( total == 0 )); then
    echo "0 0"
    return
  fi
  local complete_target=$(( total * complete_pct / 100 ))
  local i=0
  for k in $keys; do
    i=$((i + 1))
    if (( i <= complete_target )); then
      local code
      code="$(curl -sS -o /dev/null -w '%{http_code}' \
        -X POST "${BASE_URL}/v2/jobs/${k}/completion" \
        -H 'content-type: application/json' \
        -d '{"variables":{}}')"
      if [[ "$code" == "204" ]]; then
        completed=$((completed + 1))
      else
        warn "Completing job ${k} returned HTTP ${code}"
      fi
    else
      local code
      code="$(curl -sS -o /dev/null -w '%{http_code}' \
        -X POST "${BASE_URL}/v2/jobs/${k}/failure" \
        -H 'content-type: application/json' \
        -d "{\"errorMessage\":$(jq -Rsa . <<<"$msg"),\"retries\":0}")"
      if [[ "$code" == "204" ]]; then
        failed=$((failed + 1))
      else
        warn "Failing job ${k} returned HTTP ${code}"
      fi
    fi
  done
  echo "${completed} ${failed}"
}

rand() {
  # rand MIN MAX (inclusive)
  local min="$1" max="$2"
  echo $(( RANDOM % (max - min + 1) + min ))
}

pick() {
  # pick from $@ randomly
  local -a arr=("$@")
  echo "${arr[$(( RANDOM % ${#arr[@]} ))]}"
}

main() {
  require_jq
  check_topology

  log "=== Step 1: Deploy BPMNs ==="
  local order_key payment_key shipping_key
  order_key="$(deploy_if_missing order-process    "${BPMN_DIR}/order-process.bpmn")"
  payment_key="$(deploy_if_missing payment-process  "${BPMN_DIR}/payment-process.bpmn")"
  shipping_key="$(deploy_if_missing shipping-process "${BPMN_DIR}/shipping-process.bpmn")"

  log "=== Step 2: Create process instances ==="

  local customers=("alice" "bob" "carol" "dave" "eve" "frank" "grace" "heidi")
  local regions=("EU" "US" "APAC" "LATAM")

  # Order process: 60 instances (the headliner) — most variety, drives funnel
  local order_count=60
  log "Creating ${order_count} order-process instances..."
  for i in $(seq 1 $order_count); do
    local cust amt region
    cust="$(pick "${customers[@]}")"
    amt="$(rand 10 9999)"
    region="$(pick "${regions[@]}")"
    local vars
    vars="$(jq -nc --arg id "ORD-$(date +%s)-$i" --arg c "$cust" --argjson a "$amt" --arg r "$region" \
      '{orderId:$id, customerId:$c, amount:$a, region:$r}')"
    local pikey
    if pikey="$(create_instance order-process "$vars")"; then
      log "  order-process instance ${i}/${order_count} created (key=${pikey})"
    fi
  done

  # Payment process: 10 instances
  local payment_count=10
  log "Creating ${payment_count} payment-process instances..."
  for i in $(seq 1 $payment_count); do
    local amt
    amt="$(rand 5 500)"
    local vars
    vars="$(jq -nc --arg id "PAY-$(date +%s)-$i" --argjson a "$amt" \
      '{paymentId:$id, amount:$a, currency:"EUR"}')"
    local pikey
    if pikey="$(create_instance payment-process "$vars")"; then
      log "  payment-process instance ${i}/${payment_count} created (key=${pikey})"
    fi
  done

  # Shipping process: 8 instances
  local shipping_count=8
  log "Creating ${shipping_count} shipping-process instances..."
  for i in $(seq 1 $shipping_count); do
    local vars
    vars="$(jq -nc --arg id "SHP-$(date +%s)-$i" --arg w "$(rand 1 50)kg" \
      '{shipmentId:$id, weight:$w}')"
    local pikey
    if pikey="$(create_instance shipping-process "$vars")"; then
      log "  shipping-process instance ${i}/${shipping_count} created (key=${pikey})"
    fi
  done

  log "Waiting 3s for jobs to materialize..."
  sleep 3

  log "=== Step 3: Drive the order-process funnel ==="
  # Walk each order-process stage in sequence: complete most jobs (so instances
  # progress to the next stage) and fail a slice (creating incidents). This
  # produces a decreasing pyramid Validate -> Charge -> Reserve -> Ship while
  # keeping a dramatic incident heatmap on Task_Validate.

  # Stage 1: Validate. Heaviest fail rate (heatmap). ~30 instances move on.
  log "Funnel stage 1: validate-order (complete ~60%, fail ~40% — heatmap)..."
  read -r vc vf < <(funnel_jobs validate-order 80 60 \
    'Validation service unreachable: connection timeout to validator-svc.internal:8443')
  log "  -> validate-order: completed=${vc}, failed=${vf}"

  log "Waiting 2s for next stage's jobs to materialize..."
  sleep 2

  # Stage 2: Charge. ~20 instances move on, ~10 incidents.
  log "Funnel stage 2: charge-payment (complete ~70%, fail ~30%)..."
  read -r cc cf < <(funnel_jobs charge-payment 50 70 \
    'Insufficient funds: card declined by issuer')
  log "  -> charge-payment: completed=${cc}, failed=${cf}"

  sleep 2

  # Stage 3: Reserve. ~15 move on, ~5 incidents.
  log "Funnel stage 3: reserve-inventory (complete ~80%, fail ~20%)..."
  read -r rc rf < <(funnel_jobs reserve-inventory 40 80 \
    'Inventory service: SKU not available in warehouse-eu-1')
  log "  -> reserve-inventory: completed=${rc}, failed=${rf}"

  sleep 2

  # Stage 4: Ship. ~10 complete to EndEvent, ~3 incidents.
  log "Funnel stage 4: ship-order (complete ~85%, fail ~15%)..."
  read -r sc sf < <(funnel_jobs ship-order 30 85 \
    'Carrier API timeout: dhl-gateway.internal returned 504')
  log "  -> ship-order: completed=${sc}, failed=${sf}"

  # Extra incident drama on Task_Validate to keep the heatmap dramatic across
  # re-runs (these are leftover validate jobs from the new instances).
  log "Bonus heatmap: failing more validate-order jobs..."
  local extra_v
  extra_v="$(fail_jobs validate-order 10 \
    'Validation service unreachable: connection timeout to validator-svc.internal:8443')"
  log "  -> failed ${extra_v} extra validate-order jobs"

  # Side processes: payment-process and shipping-process incidents
  log "Failing authorize-payment jobs (payment-process)..."
  local n
  n="$(fail_jobs authorize-payment 2 'Payment gateway returned HTTP 502')"
  log "  -> failed ${n} authorize-payment jobs"

  log "Failing pack-package jobs (shipping-process)..."
  n="$(fail_jobs pack-package 1 'Warehouse out of packing materials')"
  log "  -> failed ${n} pack-package jobs"

  log "Waiting 3s for incidents to be indexed..."
  sleep 3

  log "=== Step 4: Verification ==="
  local def_count inst_total inst_active inc_count
  def_count="$(api POST /v2/process-definitions/search '{"page":{"limit":1}}' | jq '.page.totalItems')"
  inst_total="$(api POST /v2/process-instances/search '{"page":{"limit":1}}' | jq '.page.totalItems')"
  inst_active="$(api POST /v2/process-instances/search '{"filter":{"state":"ACTIVE"},"page":{"limit":1}}' | jq '.page.totalItems')"
  inc_count="$(api POST /v2/incidents/search '{"page":{"limit":1}}' | jq '.page.totalItems')"

  echo
  log "Cluster summary:"
  log "  Process definitions:        ${def_count}"
  log "  Process instances total:    ${inst_total}"
  log "  Process instances ACTIVE:   ${inst_active}"
  log "  Incidents:                  ${inc_count}"
  log "  Headliner processDefinitionKey (order-process v1): ${order_key}"
  log "  payment-process key:        ${payment_key}"
  log "  shipping-process key:       ${shipping_key}"

  # Per-stage funnel counts for order-process
  echo
  log "order-process funnel (element-instance statistics):"
  local stats
  stats="$(api POST "/v2/process-definitions/${order_key}/statistics/element-instances" '{}')"
  if [[ -n "$stats" ]] && echo "$stats" | jq -e '.items' >/dev/null 2>&1; then
    # Print one line per element of interest with active/incidents/completed.
    local elements=("StartEvent_1" "Task_Validate" "Task_Charge" "Task_Reserve" "Task_Ship" "EndEvent_1")
    printf '[seed]   %-16s %8s %10s %10s\n' "element" "active" "incidents" "completed" >&2
    for el in "${elements[@]}"; do
      local active inc completed
      active="$(echo "$stats"     | jq -r --arg e "$el" '(.items[] | select(.elementId==$e) | .active)     // 0')"
      inc="$(echo "$stats"        | jq -r --arg e "$el" '(.items[] | select(.elementId==$e) | .incidents)  // 0')"
      completed="$(echo "$stats"  | jq -r --arg e "$el" '(.items[] | select(.elementId==$e) | .completed)  // 0')"
      printf '[seed]   %-16s %8s %10s %10s\n' "$el" "$active" "$inc" "$completed" >&2
    done
  else
    warn "Could not fetch element-instance statistics for order-process: ${stats}"
  fi
  echo

  if (( inc_count < 5 )); then
    warn "Fewer than 5 incidents present. Heatmap will look sparse."
  fi
  if (( inst_active < 20 )); then
    warn "Fewer than 20 active instances. Demo widgets may look thin."
  fi

  log "Done."
}

main "$@"
