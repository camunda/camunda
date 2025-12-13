#!/bin/bash
set -euo pipefail

# ====================================================
# Camunda Recovery Job YAML Generator
# ====================================================
#
# Simple standalone script to generate recovery Job YAML
# All configuration via environment variables
#
# Required variables:
#   NAMESPACE              - Kubernetes namespace
#   PARTITION_ID           - Partition ID to recover
#   LAST_LEADER_BROKER     - Broker ID that was the leader
#   NEW_KEY                - New key value to set
#   PARTITION_BROKER_IDS   - Space-separated broker IDs (e.g., "0 1 2")
#   CONTAINER_IMAGE        - Container image (e.g., "camunda/camunda:8.6.1")
#
# Optional variables:
#   NEW_MAX_KEY            - New max key value (optional)
#   STATEFULSET_NAME       - StatefulSet name (default: "camunda")
#   PVC_PREFIX             - PVC name prefix (default: "data-${STATEFULSET_NAME}")
#   CONTAINER_USER_ID      - User ID (default: 1000)
#   CONTAINER_GROUP_ID     - Group ID (default: 1001)
#   CONTAINER_FS_GROUP     - fsGroup ID (default: 1001)
#   GENERATED_DIR          - Output directory (default: "generated")
#   OUTPUT_FILE            - Output file path (auto-generated if not set)
#
# Usage:
#   export NAMESPACE="camunda"
#   export PARTITION_ID="1"
#   export LAST_LEADER_BROKER="0"
#   export NEW_KEY="2251800000000000"
#   export PARTITION_BROKER_IDS="0 1 2"
#   export CONTAINER_IMAGE="camunda/camunda:8.6.1"
#   ./generate-recovery-job.sh
#
# ====================================================

# Validate required variables
: "${NAMESPACE:?ERROR: NAMESPACE is required}"
: "${PARTITION_ID:?ERROR: PARTITION_ID is required}"
: "${LAST_LEADER_BROKER:?ERROR: LAST_LEADER_BROKER is required}"
: "${NEW_KEY:?ERROR: NEW_KEY is required}"
: "${PARTITION_BROKER_IDS:?ERROR: PARTITION_BROKER_IDS is required (space-separated, e.g., '0 1 2')}"
: "${CONTAINER_IMAGE:?ERROR: CONTAINER_IMAGE is required (e.g., 'camunda/camunda:8.6.1')}"

# Optional variables with defaults
STATEFULSET_NAME="${STATEFULSET_NAME:-camunda}"
PVC_PREFIX="${PVC_PREFIX:-data-${STATEFULSET_NAME}}"
CONTAINER_USER_ID="${CONTAINER_USER_ID:-1000}"
CONTAINER_GROUP_ID="${CONTAINER_GROUP_ID:-1001}"
CONTAINER_FS_GROUP="${CONTAINER_FS_GROUP:-1001}"
NEW_MAX_KEY="${NEW_MAX_KEY:-}"

# Output file configuration
GENERATED_DIR="${GENERATED_DIR:-generated}"
TIMESTAMP=$(date +%Y%m%d-%H%M%S)
OUTPUT_FILE="${OUTPUT_FILE:-${GENERATED_DIR}/recovery-job-partition-${PARTITION_ID}-${TIMESTAMP}.yaml}"

# Validate recovery-script.sh exists
if [ ! -f "recovery-script.sh" ]; then
        echo "ERROR: recovery-script.sh not found in current directory!" >&2
        echo "This script must be run from the same directory as recovery-script.sh" >&2
        exit 1
fi

# Create output directory
mkdir -p "$GENERATED_DIR"

# ====================================================
# Generate YAML
# ====================================================

# Generate volumeMounts section
volume_mounts=""
volume_mounts+="        # Broker data PVCs (only for partition replicas)\n"

for broker_id in $PARTITION_BROKER_IDS; do
	volume_mounts+="        - name: broker-${broker_id}-data\n"
	volume_mounts+="          mountPath: /mnt/broker-${broker_id}\n"
	volume_mounts+="        \n"
done

volume_mounts+="        # Temporary storage for recovery operations\n"
volume_mounts+="        - name: temp-storage\n"
volume_mounts+="          mountPath: /tmp/recovery"

# Generate volumes section
volumes=""
volumes+="      # Broker PVCs (only for partition replicas)\n"

for broker_id in $PARTITION_BROKER_IDS; do
	volumes+="      - name: broker-${broker_id}-data\n"
	volumes+="        persistentVolumeClaim:\n"
        volumes+="          claimName: ${PVC_PREFIX}-${broker_id}\n"
        volumes+="      \n"
done

volumes+="      # Temporary storage\n"
volumes+="      - name: temp-storage\n"
volumes+="        emptyDir:\n"
volumes+="          sizeLimit: 10Gi"

# Build environment variables section
env_vars=""
env_vars+="        # Required parameters\n"
env_vars+="        - name: PARTITION_ID\n"
env_vars+="          value: \"${PARTITION_ID}\"\n"
env_vars+="        \n"
env_vars+="        - name: LAST_LEADER_BROKER\n"
env_vars+="          value: \"${LAST_LEADER_BROKER}\"\n"
env_vars+="        \n"
env_vars+="        - name: NEW_KEY\n"
env_vars+="          value: \"${NEW_KEY}\"\n"
env_vars+="        \n"
env_vars+="        - name: CAMUNDA_LOG_FILE_APPENDER_ENABLED\n"
env_vars+="          value: \"false\"\n"
env_vars+="        \n"

# Only add NEW_MAX_KEY if it's set
if [ -n "${NEW_MAX_KEY}" ]; then
        env_vars+="        - name: NEW_MAX_KEY\n"
        env_vars+="          value: \"${NEW_MAX_KEY}\"\n"
        env_vars+="        \n"
fi

env_vars+="        \n"
env_vars+="        - name: PARTITION_BROKER_IDS\n"
env_vars+="          value: \"${PARTITION_BROKER_IDS}\"\n"
env_vars+="        \n"
env_vars+="        # Optional: override snapshot selection\n"
env_vars+="        # Uncomment and set to use a specific snapshot ID\n"
env_vars+="        # - name: SNAPSHOT_ID\n"
env_vars+="        #   value: \"79292-1-963202-962181-0-c52549fd\""

# Generate complete Job YAML
{
        cat <<'EOF'
apiVersion: batch/v1
kind: Job
metadata:
EOF

        cat <<EOF
  name: key-recovery-job
  namespace: ${NAMESPACE}
  labels:
    app: camunda-recovery
    component: key-recovery
spec:
  # 10 minute timeout
  activeDeadlineSeconds: 600

  # Don't retry on failure - we want to investigate
  backoffLimit: 0

  template:
    metadata:
      labels:
        app: camunda-recovery
        component: key-recovery
    spec:
      restartPolicy: Never

      # Run as the same user as Camunda containers
      # User 1000 (camunda), Group 1000, fsGroup 1001
      securityContext:
        runAsUser: ${CONTAINER_USER_ID}
        runAsGroup: ${CONTAINER_GROUP_ID}
        fsGroup: ${CONTAINER_FS_GROUP}
        runAsNonRoot: true

      containers:
      - name: recovery
        image: ${CONTAINER_IMAGE}

        # Execute inline recovery script
        command: ["/bin/bash", "-c"]
        args:
          - |
$(cat recovery-script.sh | sed 's/^/            /')

        env:
$(echo -e "$env_vars")

        resources:
          requests:
            memory: "1Gi"
            cpu: "1000m"
          limits:
            memory: "1Gi"
            cpu: "1000m"

        volumeMounts:
$(echo -e "$volume_mounts")

      volumes:
$(echo -e "$volumes")
EOF
} >"$OUTPUT_FILE"

# Output the file path to stdout (for calling scripts to capture)
echo "$OUTPUT_FILE"
