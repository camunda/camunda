#!/bin/bash
set -euo pipefail

# ====================================================
# Camunda Incident-Position Reset Job YAML Generator
# ====================================================
#
# Generates one Kubernetes Job PER BROKER that resets an exporter's
# lastIncidentUpdatePosition on every partition replica hosted by that broker.
#
# The EXPORTER column family is replica-local state, so - unlike the
# key-recovery procedure - every broker must patch its own snapshots and
# nothing is copied between brokers. One Job per broker also avoids mounting
# several ReadWriteOnce (and possibly zonal) PVCs into a single pod.
#
# Required variables:
#   NAMESPACE              - Kubernetes namespace
#   BROKER_IDS             - Space-separated broker IDs to patch (e.g., "0 1 2")
#   NEW_POSITION           - New lastIncidentUpdatePosition (-1 reprocesses all incidents)
#   CONTAINER_IMAGE        - Container image (e.g., "camunda/camunda:8.8.1")
#
# Optional variables:
#   EXPORTER_ID            - Exporter id to patch (default: "camundaexporter")
#   PARTITION_IDS          - Space-separated partition ids (default: auto-detect all
#                            partitions on each broker's PVC)
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
#   export BROKER_IDS="0 1 2"
#   export NEW_POSITION="-1"
#   export CONTAINER_IMAGE="camunda/camunda:8.8.1"
#   ./generate-reset-job.sh
#
# ====================================================

# Validate required variables
: "${NAMESPACE:?ERROR: NAMESPACE is required}"
: "${BROKER_IDS:?ERROR: BROKER_IDS is required (space-separated, e.g., '0 1 2')}"
: "${NEW_POSITION:?ERROR: NEW_POSITION is required (-1 reprocesses all incidents)}"
: "${CONTAINER_IMAGE:?ERROR: CONTAINER_IMAGE is required (e.g., 'camunda/camunda:8.8.1')}"

# Optional variables with defaults
EXPORTER_ID="${EXPORTER_ID:-camundaexporter}"
PARTITION_IDS="${PARTITION_IDS:-}"
STATEFULSET_NAME="${STATEFULSET_NAME:-camunda}"
PVC_PREFIX="${PVC_PREFIX:-data-${STATEFULSET_NAME}}"
CONTAINER_USER_ID="${CONTAINER_USER_ID:-1000}"
CONTAINER_GROUP_ID="${CONTAINER_GROUP_ID:-1001}"
CONTAINER_FS_GROUP="${CONTAINER_FS_GROUP:-1001}"

# Output file configuration
GENERATED_DIR="${GENERATED_DIR:-generated}"
TIMESTAMP=$(date +%Y%m%d-%H%M%S)
OUTPUT_FILE="${OUTPUT_FILE:-${GENERATED_DIR}/reset-incident-position-jobs-${TIMESTAMP}.yaml}"

# Validate reset-script.sh exists
if [ ! -f "reset-script.sh" ]; then
        echo "ERROR: reset-script.sh not found in current directory!" >&2
        echo "This script must be run from the same directory as reset-script.sh" >&2
        exit 1
fi

# Create output directory
mkdir -p "$GENERATED_DIR"

# Read reset script content and escape for YAML
RESET_SCRIPT=$(cat reset-script.sh | sed 's/\\/\\\\/g' | sed 's/"/\\"/g' | awk '{printf "%s\\n", $0}' | sed 's/\\n$//')

generate_job_for_broker() {
        local broker_id="$1"

        cat <<-EOF
	apiVersion: batch/v1
	kind: Job
	metadata:
	  name: incident-position-reset-broker-${broker_id}
	  namespace: ${NAMESPACE}
	  labels:
	    app: camunda-recovery
	    component: incident-position-reset
	spec:
	  activeDeadlineSeconds: 600
	  backoffLimit: 0
	  template:
	    metadata:
	      labels:
	        app: camunda-recovery
	        component: incident-position-reset
	    spec:
	      restartPolicy: Never
	      securityContext:
	        runAsUser: ${CONTAINER_USER_ID}
	        runAsGroup: ${CONTAINER_GROUP_ID}
	        fsGroup: ${CONTAINER_FS_GROUP}
	        runAsNonRoot: true
	      containers:
	        - name: reset
	          image: ${CONTAINER_IMAGE}
	          command:
	            - /bin/bash
	            - -c
	          args:
	            - "${RESET_SCRIPT}"
	          env:
	            - name: BROKER_ID
	              value: "${broker_id}"
	            - name: EXPORTER_ID
	              value: "${EXPORTER_ID}"
	            - name: NEW_POSITION
	              value: "${NEW_POSITION}"
	            - name: CAMUNDA_LOG_FILE_APPENDER_ENABLED
	              value: "false"
	EOF

        if [ -n "${PARTITION_IDS}" ]; then
                cat <<-EOF
		            - name: PARTITION_IDS
		              value: "${PARTITION_IDS}"
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
	            - name: broker-${broker_id}-data
	              mountPath: /mnt/broker-${broker_id}
	            - name: temp-storage
	              mountPath: /tmp
	      volumes:
	        - name: broker-${broker_id}-data
	          persistentVolumeClaim:
	            claimName: ${PVC_PREFIX}-${broker_id}
	        - name: temp-storage
	          emptyDir:
	            sizeLimit: 10Gi
	EOF
}

{
        first=true
        for broker_id in $BROKER_IDS; do
                if [ "$first" = true ]; then
                        first=false
                else
                        echo "---"
                fi
                generate_job_for_broker "$broker_id"
        done
} >"$OUTPUT_FILE"

echo "Generated: ${OUTPUT_FILE}"
echo ""
echo "One Job per broker (${BROKER_IDS}). Apply with:"
echo "  kubectl apply -f ${OUTPUT_FILE}"
