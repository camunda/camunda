#!/usr/bin/env bash
#
# Sets up an existing load-test deployment in three namespaces as one zone-aware, tri-region cluster:
# two data regions (region-a, region-b) and a control region (control) that only runs the system
# partition, with the SWIM failure detector on the system partition: every system partition broker
# reports its SWIM membership view to the others, and the system partition leader commits a region
# failover when every system partition broker of a data region has been out of sight of the
# observers for the detection window, and the region did not come back within the recovery window.
#
#   usage: tri-region-swim-setup.sh [--yes] [--dry-run] <region-a-namespace> <region-b-namespace> <control-namespace>
#
# What it does, in order:
#   1. writes the tri-region settings into each namespace's `camunda-configuration` ConfigMap, in
#      `application-override.yml` (loaded after the chart's application.yaml, so it wins)
#   2. scales all three StatefulSets to 0
#   3. deletes the brokers' data volumes: a broker restarts from the cluster topology saved on its
#      volume, so volumes created before the zone-aware config would keep the old 3-broker topology
#   4. scales the StatefulSets back up, region-a and region-b with 3 brokers and control with 1
#
# The failure detector windows are whole seconds (or minutes), unlike the milliseconds SWIM itself
# probes with. Watch it work with the `zeebe_system_partition_swim_detector_*` metrics, or in the
# brokers' logs: "was detected as gone" when the recovery window starts, and "Region failover ...
# committed" when it is over.
#
# Override defaults through the environment, e.g. `DETECTION_WINDOW=60s ./tri-region-swim-setup.sh ...`.

set -euo pipefail

# --- cluster layout
REGION_A="${REGION_A:-region-a}"
REGION_B="${REGION_B:-region-b}"
CONTROL="${CONTROL:-control}"
REGION_A_BROKERS="${REGION_A_BROKERS:-3}"
REGION_B_BROKERS="${REGION_B_BROKERS:-3}"
CONTROL_BROKERS="${CONTROL_BROKERS:-1}"
REGION_A_PRIORITY="${REGION_A_PRIORITY:-1000}"
REGION_B_PRIORITY="${REGION_B_PRIORITY:-500}"
PARTITION_COUNT="${PARTITION_COUNT:-3}"
# replicas of each data partition, spread over the two data regions
REPLICATION_FACTOR="${REPLICATION_FACTOR:-4}"
# brokers replicating the system partition, spread over all three regions
SYSTEM_PARTITION_REPLICATION_FACTOR="${SYSTEM_PARTITION_REPLICATION_FACTOR:-5}"

# --- SWIM failure detector, in whole seconds or minutes, e.g. 30s or 1m
# how often each system partition broker reports its SWIM view to the others
REPORT_INTERVAL="${REPORT_INTERVAL:-1s}"
# how long after its last report a broker's view is ignored; at least twice the report interval
REPORT_TIMEOUT="${REPORT_TIMEOUT:-5s}"
# how long every observer must have not seen a broker before it is gone; at least the report timeout
# and SWIM's failure timeout
DETECTION_WINDOW="${DETECTION_WINDOW:-30s}"
# how long a region detected as gone has to come back before the leader fails over from it
RECOVERY_WINDOW="${RECOVERY_WINDOW:-30s}"
# how many distinct brokers must have a broker out of sight before it is gone
MIN_OBSERVERS="${MIN_OBSERVERS:-2}"
# SWIM's own failure timeout; only written to the configuration when set, otherwise the broker's
# default of 10s applies, which the detection window is validated against
MEMBERSHIP_FAILURE_TIMEOUT="${MEMBERSHIP_FAILURE_TIMEOUT:-}"

# --- deployment
# must be the same in all namespaces: brokers only join a cluster of their own name
CLUSTER_NAME="${CLUSTER_NAME:-tri-region-zeebe}"
STATEFULSET="${STATEFULSET:-camunda}"
# the headless service the StatefulSet pods hang off
SERVICE="${SERVICE:-camunda}"
CONFIGMAP="${CONFIGMAP:-camunda-configuration}"
BROKER_LABEL="${BROKER_LABEL:-app.kubernetes.io/component=zeebe-broker}"
INTERNAL_API_PORT="${INTERNAL_API_PORT:-26502}"
TIMEOUT="${TIMEOUT:-600s}"

ASSUME_YES=false
DRY_RUN=false
POSITIONAL=()
for arg in "$@"; do
  case "$arg" in
    --yes | -y) ASSUME_YES=true ;;
    --dry-run) DRY_RUN=true ;;
    -h | --help)
      sed -n '2,/^set -euo/p' "$0" | sed '$d' | sed 's/^# \{0,1\}//'
      exit 0
      ;;
    *) POSITIONAL+=("$arg") ;;
  esac
done
if [ "${#POSITIONAL[@]}" -ne 3 ]; then
  echo "usage: $0 [--yes] [--dry-run] <region-a-namespace> <region-b-namespace> <control-namespace>" >&2
  exit 2
fi
NS_A="${POSITIONAL[0]}"
NS_B="${POSITIONAL[1]}"
NS_C="${POSITIONAL[2]}"

NAMESPACES=("$NS_A" "$NS_B" "$NS_C")
ZONES=("$REGION_A" "$REGION_B" "$CONTROL")
BROKERS=("$REGION_A_BROKERS" "$REGION_B_BROKERS" "$CONTROL_BROKERS")
CLUSTER_SIZE=$((REGION_A_BROKERS + REGION_B_BROKERS + CONTROL_BROKERS))

log() { printf '\033[1m==> %s\033[0m\n' "$*"; }
run() {
  if $DRY_RUN; then
    printf '[dry-run] %s\n' "$*"
  else
    "$@"
  fi
}

# --- validation
# converts 30s or 2m to seconds, and fails for anything else, e.g. 500ms: the detector's windows are
# in seconds, set apart from the milliseconds SWIM probes with
to_seconds() {
  local name="$1" value="$2"
  if [[ "$value" =~ ^([0-9]+)s$ ]]; then
    echo "${BASH_REMATCH[1]}"
  elif [[ "$value" =~ ^([0-9]+)m$ ]]; then
    echo $((BASH_REMATCH[1] * 60))
  else
    echo "$name must be a whole number of seconds or minutes, e.g. 30s or 1m, but was '$value'" >&2
    exit 1
  fi
}
REPORT_INTERVAL_S="$(to_seconds REPORT_INTERVAL "$REPORT_INTERVAL")"
REPORT_TIMEOUT_S="$(to_seconds REPORT_TIMEOUT "$REPORT_TIMEOUT")"
DETECTION_WINDOW_S="$(to_seconds DETECTION_WINDOW "$DETECTION_WINDOW")"
RECOVERY_WINDOW_S="$(to_seconds RECOVERY_WINDOW "$RECOVERY_WINDOW")"
FAILURE_TIMEOUT_S=10
if [ -n "$MEMBERSHIP_FAILURE_TIMEOUT" ]; then
  FAILURE_TIMEOUT_S="$(to_seconds MEMBERSHIP_FAILURE_TIMEOUT "$MEMBERSHIP_FAILURE_TIMEOUT")"
fi
if [ "$REPORT_INTERVAL_S" -lt 1 ]; then
  echo "REPORT_INTERVAL must be at least 1s" >&2
  exit 1
fi
if [ "$REPORT_TIMEOUT_S" -lt $((2 * REPORT_INTERVAL_S)) ]; then
  echo "REPORT_TIMEOUT ($REPORT_TIMEOUT) must be at least twice the REPORT_INTERVAL ($REPORT_INTERVAL)" >&2
  exit 1
fi
if [ "$DETECTION_WINDOW_S" -lt "$REPORT_TIMEOUT_S" ]; then
  echo "DETECTION_WINDOW ($DETECTION_WINDOW) must be at least the REPORT_TIMEOUT ($REPORT_TIMEOUT)" >&2
  exit 1
fi
if [ "$DETECTION_WINDOW_S" -lt "$FAILURE_TIMEOUT_S" ]; then
  echo "DETECTION_WINDOW ($DETECTION_WINDOW) must be at least SWIM's failure timeout (${FAILURE_TIMEOUT_S}s)" >&2
  exit 1
fi
if ! [[ "$MIN_OBSERVERS" =~ ^[0-9]+$ ]] || [ "$MIN_OBSERVERS" -lt 1 ]; then
  echo "MIN_OBSERVERS must be at least 1, but was '$MIN_OBSERVERS'" >&2
  exit 1
fi
if [ "$SYSTEM_PARTITION_REPLICATION_FACTOR" -gt "$CLUSTER_SIZE" ]; then
  echo "system partition replication factor $SYSTEM_PARTITION_REPLICATION_FACTOR exceeds the $CLUSTER_SIZE brokers" >&2
  exit 1
fi
if [ "$NS_A" = "$NS_B" ] || [ "$NS_A" = "$NS_C" ] || [ "$NS_B" = "$NS_C" ]; then
  echo "the three namespaces must be different" >&2
  exit 1
fi
for i in 0 1 2; do
  ns="${NAMESPACES[$i]}"
  kubectl -n "$ns" get sts "$STATEFULSET" >/dev/null ||
    { echo "namespace $ns has no StatefulSet $STATEFULSET" >&2; exit 1; }
  kubectl -n "$ns" get cm "$CONFIGMAP" >/dev/null ||
    { echo "namespace $ns has no ConfigMap $CONFIGMAP" >&2; exit 1; }
done
echo "context: $(kubectl config current-context)"

# --- configuration
contact_points() {
  local i ns n
  for i in 0 1 2; do
    ns="${NAMESPACES[$i]}"
    for ((n = 0; n < BROKERS[i]; n++)); do
      printf '  - %s-%d.%s.%s.svc.cluster.local:%s\n' \
        "$STATEFULSET" "$n" "$SERVICE" "$ns" "$INTERNAL_API_PORT"
    done
  done
}

# SWIM's own failure timeout, only when set; empty otherwise
membership_override() {
  if [ -n "$MEMBERSHIP_FAILURE_TIMEOUT" ]; then
    printf '  membership:\n    failure-timeout: %s\n' "$MEMBERSHIP_FAILURE_TIMEOUT"
  fi
}

override_for() {
  local zone="$1"
  cat <<EOF
# tri-region SWIM setup (tri-region-swim-setup.sh): the same in all namespaces except the zone.
camunda.cluster:
  # the zone of the brokers in this namespace; their node ids are the pod ordinals
  zone: ${zone}
  # all brokers of all regions
  size: ${CLUSTER_SIZE}
  partition-count: ${PARTITION_COUNT}
  replication-factor: ${REPLICATION_FACTOR}
  partitioning:
    scheme: ZONE_AWARE
    zone-aware:
      zones:
        - {name: ${REGION_A}, number-of-brokers: ${REGION_A_BROKERS}, number-of-replicas: $((REPLICATION_FACTOR / 2)), priority: ${REGION_A_PRIORITY}}
        - {name: ${REGION_B}, number-of-brokers: ${REGION_B_BROKERS}, number-of-replicas: $((REPLICATION_FACTOR / 2)), priority: ${REGION_B_PRIORITY}}
        - {name: ${CONTROL}, number-of-brokers: ${CONTROL_BROKERS}, number-of-replicas: 0, priority: 1, role: control}
  system-partition:
    enabled: true
    replication-factor: ${SYSTEM_PARTITION_REPLICATION_FACTOR}
    snapshot-period: 1m
    failure-detector:
      type: SWIM
      swim:
        report-interval: ${REPORT_INTERVAL}
        report-timeout: ${REPORT_TIMEOUT}
        detection-window: ${DETECTION_WINDOW}
        recovery-window: ${RECOVERY_WINDOW}
        min-observers: ${MIN_OBSERVERS}
$(membership_override)
  initial-contact-points:
$(contact_points)
# the chart derives the cluster name from the release name, and brokers only join a cluster of
# their own name; the advertised host must resolve from the other namespaces
zeebe.broker.cluster.clusterName: ${CLUSTER_NAME}
zeebe.broker.network.advertisedHost: "\${K8S_NAME}.\${K8S_SERVICE_NAME}.\${K8S_NAMESPACE}.svc.cluster.local"
EOF
}

log "Writing the tri-region configuration (cluster size $CLUSTER_SIZE, SWIM detection ${DETECTION_WINDOW}, recovery ${RECOVERY_WINDOW})"
for i in 0 1 2; do
  ns="${NAMESPACES[$i]}"
  zone="${ZONES[$i]}"
  override="$(override_for "$zone")"
  if $DRY_RUN; then
    printf '[dry-run] namespace %s, zone %s, application-override.yml:\n%s\n' "$ns" "$zone" "$override"
  else
    kubectl -n "$ns" patch cm "$CONFIGMAP" --type merge \
      -p "$(jq -n --arg o "$override" '{data: {"application-override.yml": $o}}')"
  fi
done

# --- confirm the destructive part
log "Data volumes with label '$BROKER_LABEL' to delete"
for ns in "${NAMESPACES[@]}"; do
  kubectl -n "$ns" get pvc -l "$BROKER_LABEL" --no-headers -o custom-columns=NAMESPACE:.metadata.namespace,NAME:.metadata.name
done
if ! $ASSUME_YES && ! $DRY_RUN; then
  read -r -p "Delete these volumes and restart all brokers? [y/N] " answer
  [ "$answer" = "y" ] || { echo "aborted; the configuration is written but the brokers still run with the old topology"; exit 1; }
fi

# --- scale down everywhere before deleting anything, so no broker rejoins with old state
log "Scaling down"
for ns in "${NAMESPACES[@]}"; do
  run kubectl -n "$ns" scale sts/"$STATEFULSET" --replicas=0
done
for ns in "${NAMESPACES[@]}"; do
  run kubectl -n "$ns" wait --for=delete pod -l "$BROKER_LABEL" --timeout="$TIMEOUT" || true
done

log "Deleting the data volumes"
for ns in "${NAMESPACES[@]}"; do
  run kubectl -n "$ns" delete pvc -l "$BROKER_LABEL" --wait=false
done
for ns in "${NAMESPACES[@]}"; do
  run kubectl -n "$ns" wait --for=delete pvc -l "$BROKER_LABEL" --timeout="$TIMEOUT" || true
done

# --- scale up together
log "Scaling up"
for i in 0 1 2; do
  run kubectl -n "${NAMESPACES[$i]}" scale sts/"$STATEFULSET" --replicas="${BROKERS[$i]}"
done
for i in 0 1 2; do
  run kubectl -n "${NAMESPACES[$i]}" rollout status sts/"$STATEFULSET" --timeout="$TIMEOUT"
done

# --- check
if ! $DRY_RUN; then
  log "Brokers"
  for ns in "${NAMESPACES[@]}"; do
    kubectl -n "$ns" get pods -l "$BROKER_LABEL"
  done
  log "System partition leader, as seen by ${CONTROL}'s broker (expected: ${CONTROL}_0)"
  kubectl -n "$NS_C" logs "$STATEFULSET-0" 2>/dev/null |
    jq -R -r 'fromjson? | select(.message | startswith("Found leader")) | .message' | tail -1 || true
  log "Watch the detector: the brokers log the detection and the failover"
  echo "  kubectl -n <namespace> logs <broker-pod> | grep -E 'was detected as gone|Region failover'"
  echo "  metrics: zeebe_system_partition_swim_detector_{broker_state,broker_gone_duration,region_state,reports_fresh,region_failover}"
fi
log "Done"
