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
# Generate YAML - Traditional Method (String Concatenation)
# ====================================================

generate_yaml_traditional() {
        # Read recovery script content and escape for YAML
        RECOVERY_SCRIPT=$(cat recovery-script.sh | sed 's/\\/\\\\/g' | sed 's/"/\\"/g' | awk '{printf "%s\\n", $0}' | sed 's/\\n$//')

        # Generate complete Job YAML matching yq format
        {
                cat <<-EOF
		apiVersion: batch/v1
		kind: Job
		metadata:
		  name: key-recovery-job
		  namespace: ${NAMESPACE}
		  labels:
		    app: camunda-recovery
		    component: key-recovery
		spec:
		  activeDeadlineSeconds: 600
		  backoffLimit: 0
		  template:
		    metadata:
		      labels:
		        app: camunda-recovery
		        component: key-recovery
		    spec:
		      restartPolicy: Never
		      securityContext:
		        runAsUser: ${CONTAINER_USER_ID}
		        runAsGroup: ${CONTAINER_GROUP_ID}
		        fsGroup: ${CONTAINER_FS_GROUP}
		        runAsNonRoot: true
		      containers:
		        - name: recovery
		          image: ${CONTAINER_IMAGE}
		          command:
		            - /bin/bash
		            - -c
		          args:
		            - "${RECOVERY_SCRIPT}"
		          env:
		            - name: PARTITION_ID
		              value: "${PARTITION_ID}"
		            - name: LAST_LEADER_BROKER
		              value: "${LAST_LEADER_BROKER}"
		            - name: NEW_KEY
		              value: "${NEW_KEY}"
		            - name: CAMUNDA_LOG_FILE_APPENDER_ENABLED
		              value: "false"
		            - name: PARTITION_BROKER_IDS
		              value: "${PARTITION_BROKER_IDS}"
		EOF

                # Add NEW_MAX_KEY if set
                if [ -n "${NEW_MAX_KEY}" ]; then
                        cat <<-EOF
			            - name: NEW_MAX_KEY
			              value: "${NEW_MAX_KEY}"
			EOF
                fi

                cat <<-EOF
		          resources:
		            requests:
		              memory: 500Mi
		              cpu: 500m
		            limits:
		              memory: 500Mi
		              cpu: 500m
		          volumeMounts:
		EOF

                # Add volumeMounts
                for broker_id in $PARTITION_BROKER_IDS; do
                        cat <<-EOF
			            - name: broker-${broker_id}-data
			              mountPath: /mnt/broker-${broker_id}
			EOF
                done

                cat <<-EOF
		            - name: temp-storage
		              mountPath: /tmp/recovery
		      volumes:
		EOF

                # Add volumes
                for broker_id in $PARTITION_BROKER_IDS; do
                        cat <<-EOF
			        - name: broker-${broker_id}-data
			          persistentVolumeClaim:
			            claimName: ${PVC_PREFIX}-${broker_id}
			EOF
                done

                cat <<-EOF
		        - name: temp-storage
		          emptyDir:
		            sizeLimit: 10Gi
		EOF
        } >"$OUTPUT_FILE"
}

generate_yaml_traditional
