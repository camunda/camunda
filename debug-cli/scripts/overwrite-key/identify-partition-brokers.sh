#!/bin/bash
set -euo pipefail

# ====================================================
# Camunda Partition Broker Identifier
# ====================================================
#
# Identifies which brokers are part of the replication group
# for a given partition by querying the actuator endpoint.
#
# Required variables:
#   NAMESPACE              - Kubernetes namespace
#   PARTITION_ID           - Partition ID to identify
#   BROKER_COUNT           - Number of brokers in the cluster
#
# Optional variables:
#   STATEFULSET_NAME       - StatefulSet name (default: "camunda")
#   POD_PREFIX             - Pod name prefix (default: ${STATEFULSET_NAME})
#   ACTUATOR_PORT          - Actuator port (default: 9600)
#   GENERATED_DIR          - Output directory for state file (default: "generated")
#
# Output:
#   1. Writes state file: ${GENERATED_DIR}/.partition-brokers-${PARTITION_ID}.env
#   2. Prints to stdout (for piping/eval):
#      LEADER=<broker-id>
#      REPLICAS="<space-separated-broker-ids>"
#   3. Prints info message to stderr
#
# Usage:
#   export NAMESPACE="camunda"
#   export PARTITION_ID="1"
#   export BROKER_COUNT="3"
#   ./identify-partition-brokers.sh
#
#   # Approach 1: Use state file
#   ./identify-partition-brokers.sh
#   source generated/.partition-brokers-1.env
#   echo "Leader: $LEADER, Replicas: $REPLICAS"
#
#   # Approach 2: Capture output (suppress stderr)
#   eval $(./identify-partition-brokers.sh 2>/dev/null)
#   echo "Leader: $LEADER"
#   echo "Replicas: $REPLICAS"
#
# ====================================================

# Validate required variables
: "${NAMESPACE:?ERROR: NAMESPACE is required}"
: "${PARTITION_ID:?ERROR: PARTITION_ID is required}"
: "${BROKER_COUNT:?ERROR: BROKER_COUNT is required}"

# Optional variables with defaults
STATEFULSET_NAME="${STATEFULSET_NAME:-camunda}"
POD_PREFIX="${POD_PREFIX:-${STATEFULSET_NAME}}"
ACTUATOR_PORT="${ACTUATOR_PORT:-9600}"
GENERATED_DIR="${GENERATED_DIR:-generated}"

# State file for sharing broker information
STATE_FILE="${GENERATED_DIR}/.partition-brokers-${PARTITION_ID}.env"

# ====================================================
# Functions
# ====================================================

function get_pod_name() {
    local broker_id=$1
    echo "${POD_PREFIX}-${broker_id}"
}

function get_broker_ids() {
    seq 0 $((BROKER_COUNT - 1))
}

# ====================================================
# Main Logic
# ====================================================

LEADER_BROKER=""
PARTITION_REPLICAS=()

for i in $(get_broker_ids); do
    pod_name=$(get_pod_name $i)
    
    # Port forward to the broker
    kubectl port-forward -n $NAMESPACE $pod_name $ACTUATOR_PORT:$ACTUATOR_PORT >/dev/null 2>&1 &
    PF_PID=$!
    sleep 2
    
    # Get partition role (use || true to prevent script exit on curl failure)
    HTTP_RESPONSE=$(curl -s http://localhost:$ACTUATOR_PORT/actuator/partitions/$PARTITION_ID || true)
    ROLE=$(echo "$HTTP_RESPONSE" | jq -r ".role" 2>/dev/null || true)
    if [ -z "$ROLE" ] || [ "$ROLE" = "null" ]; then
        ROLE="UNKNOWN"
    fi
    
    # Track leader and replicas
    if [ "$ROLE" = "LEADER" ]; then
        LEADER_BROKER=$i
        PARTITION_REPLICAS+=($i)
    elif [ "$ROLE" = "FOLLOWER" ]; then
        PARTITION_REPLICAS+=($i)
    fi
    
    # Kill port-forward
    kill $PF_PID 2>/dev/null || true
    sleep 1
done

# Validate we found a leader
if [ -z "$LEADER_BROKER" ]; then
    echo "ERROR: Could not find leader for partition $PARTITION_ID" >&2
    exit 1
fi

# Validate we found replicas
if [ ${#PARTITION_REPLICAS[@]} -eq 0 ]; then
    echo "ERROR: Could not find any replicas for partition $PARTITION_ID" >&2
    exit 1
fi

# Save to state file
mkdir -p "$GENERATED_DIR"
cat > "$STATE_FILE" << EOF
# Partition broker information for partition $PARTITION_ID
# Generated: $(date)
LEADER=$LEADER_BROKER
REPLICAS="${PARTITION_REPLICAS[*]}"
EOF

# Output results (for backward compatibility and piping)
echo "LEADER=$LEADER_BROKER"
echo "REPLICAS=\"${PARTITION_REPLICAS[*]}\""

# Print info to stderr so it doesn't interfere with stdout parsing
echo "INFO: Broker information saved to: $STATE_FILE" >&2
