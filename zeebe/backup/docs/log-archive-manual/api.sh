#!/usr/bin/env bash
# Usage: api.sh deploy | load <n> [tag] [payloadBytes] | jobs | backup [id] | status <id> | backups
source "$(dirname "$0")/env.sh"
N=${NODE:-0}; R=localhost:$((REST_BASE + N)); M=localhost:$((MGMT_BASE + N))
case "$1" in
  deploy) curl -sf -X POST $R/v2/deployments -F "resources=@$(dirname "$0")/process.bpmn" | jq -c '.deployments[0].processDefinition | {processDefinitionId, processDefinitionKey}' ;;
  load)
    # optional 4th argument: payload size in bytes, stored as a string variable
    payload=$(head -c "${4:-0}" /dev/zero | tr '\0' 'x')
    ok=0; for k in $(seq 1 "$2"); do
      curl -sf -X POST $R/v2/process-instances -H 'Content-Type: application/json' \
        -d "{\"processDefinitionId\":\"la-manual\",\"variables\":{\"tag\":\"${3:-x}\",\"n\":$k,\"payload\":\"$payload\"}}" >/dev/null && ok=$((ok+1))
    done; echo "created $ok instances (tag ${3:-x})" ;;
  jobs)
    all="[]"
    while :; do
      out=$(curl -sf -X POST $R/v2/jobs/activation -H 'Content-Type: application/json' \
        -d '{"type":"la-job","maxJobsToActivate":100,"timeout":600000,"worker":"manual"}')
      n=$(echo "$out" | jq '.jobs | length'); [ "${n:-0}" -eq 0 ] && break
      all=$(jq -c --argjson a "$all" '$a + [.jobs[].variables.tag]' <<<"$out")
    done
    echo "activatable jobs: $(jq length <<<"$all")"
    jq -r 'group_by(.) | .[] | "  tag \(.[0]): \(length)"' <<<"$all" ;;
  backup)
    if [ -n "$2" ]; then curl -s -X POST $M/actuator/backups -H 'Content-Type: application/json' -d "{\"backupId\":$2}";
    else curl -s -X POST $M/actuator/backups -H 'Content-Type: application/json' -d '{}'; fi; echo ;;
  status) curl -s $M/actuator/backups/"$2" | jq -c '{backupId, state, failureReason}' ;;
  backups) curl -s $M/actuator/backups | jq -c '.[] | {backupId, state}' ;;
esac
