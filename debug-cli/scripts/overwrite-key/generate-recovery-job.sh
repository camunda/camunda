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
# Flags:
#   --use-yq               - Use yq for YAML generation (requires yq to be installed)
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
#   # Or use yq method:
#   ./generate-recovery-job.sh --use-yq
#
# ====================================================

# Parse command-line arguments
USE_YQ=false
while [[ $# -gt 0 ]]; do
        case $1 in
        --use-yq)
                USE_YQ=true
                shift
                ;;
        *)
                echo "Unknown option: $1" >&2
                echo "Usage: $0 [--use-yq]" >&2
                exit 1
                ;;
        esac
done

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

# ====================================================
# Generate YAML - YQ Method
# ====================================================

generate_yaml_yq() {
        # Check if yq is available
        if ! command -v yq &>/dev/null; then
                echo "ERROR: yq is not installed. Please install yq or run without --use-yq flag" >&2
                echo "Install yq: https://github.com/mikefarah/yq" >&2
                exit 1
        fi

        # Export all variables for yq's env() function
        # yq requires all env() variables to be set, even optional ones
        export NAMESPACE
        export PARTITION_ID
        export LAST_LEADER_BROKER
        export NEW_KEY
        export PARTITION_BROKER_IDS
        export CONTAINER_IMAGE
        export STATEFULSET_NAME
        export PVC_PREFIX
        export CONTAINER_USER_ID
        export CONTAINER_GROUP_ID
        export CONTAINER_FS_GROUP
        export NEW_MAX_KEY

        # Read recovery script content
        RECOVERY_SCRIPT=$(cat recovery-script.sh)
        export RECOVERY_SCRIPT

        # Create base structure
        yq eval -n '
		.apiVersion = "batch/v1" |
		.kind = "Job" |
		.metadata.name = "key-recovery-job" |
		.metadata.namespace = env(NAMESPACE) |
		.metadata.labels.app = "camunda-recovery" |
		.metadata.labels.component = "key-recovery" |
		.spec.activeDeadlineSeconds = 600 |
		.spec.backoffLimit = 0 |
		.spec.template.metadata.labels.app = "camunda-recovery" |
		.spec.template.metadata.labels.component = "key-recovery" |
		.spec.template.spec.restartPolicy = "Never" |
		.spec.template.spec.securityContext.runAsUser = (env(CONTAINER_USER_ID) | tonumber) |
		.spec.template.spec.securityContext.runAsGroup = (env(CONTAINER_GROUP_ID) | tonumber) |
		.spec.template.spec.securityContext.fsGroup = (env(CONTAINER_FS_GROUP) | tonumber) |
		.spec.template.spec.securityContext.runAsNonRoot = true
	' >"$OUTPUT_FILE"

        # Add container
        yq eval -i '
		.spec.template.spec.containers[0].name = "recovery" |
		.spec.template.spec.containers[0].image = env(CONTAINER_IMAGE) |
		.spec.template.spec.containers[0].command[0] = "/bin/bash" |
		.spec.template.spec.containers[0].command[1] = "-c"
	' "$OUTPUT_FILE"

        # Add recovery script as arg
        yq eval -i --from-file <(echo '.spec.template.spec.containers[0].args[0] = strenv(RECOVERY_SCRIPT)') "$OUTPUT_FILE"

        # Add environment variables (using strenv to ensure values are strings, not numbers)
        yq eval -i '
		.spec.template.spec.containers[0].env[0].name = "PARTITION_ID" |
		.spec.template.spec.containers[0].env[0].value = strenv(PARTITION_ID) |
		.spec.template.spec.containers[0].env[1].name = "LAST_LEADER_BROKER" |
		.spec.template.spec.containers[0].env[1].value = strenv(LAST_LEADER_BROKER) |
		.spec.template.spec.containers[0].env[2].name = "NEW_KEY" |
		.spec.template.spec.containers[0].env[2].value = strenv(NEW_KEY) |
		.spec.template.spec.containers[0].env[3].name = "CAMUNDA_LOG_FILE_APPENDER_ENABLED" |
		.spec.template.spec.containers[0].env[3].value = "false" |
		.spec.template.spec.containers[0].env[4].name = "PARTITION_BROKER_IDS" |
		.spec.template.spec.containers[0].env[4].value = (strenv(PARTITION_BROKER_IDS) | . style="double")
	' "$OUTPUT_FILE"

        # Add NEW_MAX_KEY if set
        if [ -n "${NEW_MAX_KEY}" ]; then
                yq eval -i '
			.spec.template.spec.containers[0].env[5].name = "NEW_MAX_KEY" |
			.spec.template.spec.containers[0].env[5].value = strenv(NEW_MAX_KEY)
		' "$OUTPUT_FILE"
        fi

        # Add resource requests and limits
        yq eval -i '
		.spec.template.spec.containers[0].resources.requests.memory = "1Gi" |
		.spec.template.spec.containers[0].resources.requests.cpu = "1000m" |
		.spec.template.spec.containers[0].resources.limits.memory = "1Gi" |
		.spec.template.spec.containers[0].resources.limits.cpu = "1000m"
	' "$OUTPUT_FILE"

        # Add volumeMounts for each broker
        local mount_index=0
        for broker_id in $PARTITION_BROKER_IDS; do
                yq eval -i "
			.spec.template.spec.containers[0].volumeMounts[$mount_index].name = \"broker-${broker_id}-data\" |
			.spec.template.spec.containers[0].volumeMounts[$mount_index].mountPath = \"/mnt/broker-${broker_id}\"
		" "$OUTPUT_FILE"
                mount_index=$((mount_index + 1))
        done

        # Add temp storage volumeMount
        yq eval -i "
		.spec.template.spec.containers[0].volumeMounts[$mount_index].name = \"temp-storage\" |
		.spec.template.spec.containers[0].volumeMounts[$mount_index].mountPath = \"/tmp/recovery\"
	" "$OUTPUT_FILE"

        # Add volumes for each broker
        local volume_index=0
        for broker_id in $PARTITION_BROKER_IDS; do
                yq eval -i "
			.spec.template.spec.volumes[$volume_index].name = \"broker-${broker_id}-data\" |
			.spec.template.spec.volumes[$volume_index].persistentVolumeClaim.claimName = \"${PVC_PREFIX}-${broker_id}\"
		" "$OUTPUT_FILE"
                volume_index=$((volume_index + 1))
        done

        # Add temp storage volume
        yq eval -i "
		.spec.template.spec.volumes[$volume_index].name = \"temp-storage\" |
		.spec.template.spec.volumes[$volume_index].emptyDir.sizeLimit = \"10Gi\"
	" "$OUTPUT_FILE"
}

# ====================================================
# Main execution
# ====================================================

# Generate YAML using selected method
if [ "$USE_YQ" = true ]; then
        echo "Using yq method for YAML generation..." >&2
        generate_yaml_yq
else
        echo "Using traditional method for YAML generation..." >&2
        generate_yaml_traditional
fi

# Output the file path to stdout (for calling scripts to capture)
echo "$OUTPUT_FILE"
