#!/bin/bash
#
# Camunda Key Recovery Procedure
#
# This script guides you through the recovery procedure for a corrupted key in a Camunda partition.
# It performs the following steps:
#   1. Identifies the partition leader
#   2. Shuts down the cluster
#   3. Generates Job YAML with inline recovery script
#   4. Runs the recovery job to fix the key values
#   5. Restarts the cluster
#   6. Verifies the recovery was successful
#
# Prerequisites:
#   - kubectl configured with access to the cluster
#   - jq installed for JSON parsing
#   - NEW_KEY environment variable set (the new key value)
#   - Cold backup of all broker data taken
#   - recovery-script.sh file present (will be inlined in Job YAML)
#
# Usage:
#   export NEW_KEY="2251800000000000"
#   export NEW_MAX_KEY="2251900000000000"  # Optional
#   ./recovery-procedure.sh
#
#   # Dry-run mode (preview without making changes):
#   ./recovery-procedure.sh --dry-run
#
# Optional environment variables:
#   NEW_MAX_KEY        - New max key value (if not set, only key is updated)
#   STATEFULSET_NAME   - StatefulSet name (default: camunda)
#   POD_PREFIX         - Pod name prefix (default: camunda)
#   PVC_PREFIX         - PVC name prefix (default: data-camunda)
#   ACTUATOR_PORT      - Actuator endpoint port (default: 9600)
#   CONTAINER_IMAGE    - Container image (default: auto-detected from StatefulSet)
#
# For auto-detection of cluster configuration, run:
#   ./detect-config.sh <namespace>
#
# This script guides you through the recovery procedure for a corrupted key in a Camunda partition.
# It performs the following steps:
#   1. Identifies the partition leader
#   2. Shuts down the cluster
#   3. Runs the recovery job to fix the key values
#   4. Restarts the cluster
#   5. Verifies the recovery was successful
#
# Prerequisites:
#   - kubectl configured with access to the cluster
#   - jq installed for JSON parsing
#   - NEW_KEY environment variable set (the new key value)
#   - Cold backup of all broker data taken
#   - recovery-configmap.yaml and recovery-job.yaml files present
#   - recovery-script.sh referenced by the ConfigMap
#
# Usage:
#   export NEW_KEY="2251800000000000"
#   export NAMESPACE="Kubernetes namespace"
#   export PARTITION_ID="Partition to recover"
#   export NEW_MAX_KEY="2251900000000000"  # Optional
#   ./recovery-procedure.sh
#
#   # Dry-run mode (preview without making changes):
#   ./recovery-procedure.sh --dry-run
#
# Optional environment variables:
#   NEW_MAX_KEY        - New max key value (if not set, only key is updated)
#   STATEFULSET_NAME   - StatefulSet name (default: camunda)
#   POD_PREFIX         - Pod name prefix (default: camunda)
#   PVC_PREFIX         - PVC name prefix (default: data-camunda)
#   ACTUATOR_PORT      - Actuator endpoint port (default: 9600)
#   CONTAINER_IMAGE    - Container image (default: auto-detected from StatefulSet)
#
# For auto-detection of cluster configuration, run:
#   ./detect-config.sh <namespace>
#

set -euo pipefail

# ====================================================
# Parse command-line arguments
# ====================================================

DRY_RUN=false
START_FROM_STEP=""

function show_usage() {
        echo "Camunda Key Recovery Procedure"
        echo ""
        echo "Usage: $0 [OPTIONS]"
        echo ""
        echo "Options:"
        echo "  --dry-run              Preview the recovery procedure without making changes"
        echo "                         Generates Job YAML file for review/editing"
        echo "  --from-step STEP       Resume from a specific step (use step name)"
        echo "  --help                 Show this help message"
        echo ""
        echo "Available steps for --from-step:"
        echo "  identify-leader        Step 1: Identify partition leader and replicas"
        echo "  check-cluster          Step 2: Check cluster state"
        echo "  shutdown               Step 3: Shutdown cluster"
        echo "  backup-reminder        Step 4: Backup reminder"
        echo "  run-job                Step 5: Run recovery job"
        echo "  cleanup-job            Step 6: Cleanup recovery job"
        echo "  restart                Step 7: Restart cluster"
        echo "  verify                 Step 8: Verify recovery"
        echo ""
        echo "Required Environment Variables:"
        echo "  NAMESPACE       Kubernetes namespace"
        echo "  PARTITION_ID    Partition to recover"
        echo "  NEW_KEY         New key value"
        echo ""
        echo "Optional Environment Variables:"
        echo "  NEW_MAX_KEY            New max key value (if not set, only key is updated)"
        echo "  CONTAINER_USER_ID      User ID for recovery job (default: 1000)"
        echo "  CONTAINER_GROUP_ID     Group ID for recovery job (default: 1000)"
        echo "  CONTAINER_FS_GROUP     fsGroup for recovery job (default: 1001)"
        echo ""
        echo "Example:"
        echo "  export NAMESPACE=cs-key-recovery"
        echo "  export PARTITION_ID=1"
        echo "  export NEW_KEY=2251800000000000"
        echo "  export NEW_MAX_KEY=2251900000000000  # Optional"
        echo "  $0"
        echo ""
        echo "  # Or in dry-run mode:"
        echo "  $0 --dry-run"
        echo ""
        echo "  # Resume from a specific step:"
        echo "  $0 --from-step restart"
        echo ""
        exit 0
}

while [[ $# -gt 0 ]]; do
        case $1 in
        --dry-run)
                DRY_RUN=true
                shift
                ;;
        --from-step)
                START_FROM_STEP="$2"
                shift 2
                ;;
        --help | -h)
                show_usage
                ;;
        *)
                echo "Unknown option: $1"
                echo "Usage: $0 [--dry-run] [--from-step STEP] [--help]"
                exit 1
                ;;
        esac
done

# ====================================================
# Configuration Variables
# ====================================================

# Namespace where the cluster is deployed (REQUIRED)
NAMESPACE="${NAMESPACE:-}"

# Partition to recover (REQUIRED)
PARTITION_ID="${PARTITION_ID:-}"

# Recovery files
RECOVERY_CONFIGMAP_FILE="${RECOVERY_CONFIGMAP_FILE:-recovery-configmap.yaml}"
RECOVERY_JOB_FILE="${RECOVERY_JOB_FILE:-recovery-job.yaml}"

# Generated Job YAML output directory and file
GENERATED_DIR="generated"
TIMESTAMP=$(date +%Y%m%d-%H%M%S)
GENERATED_JOB_FILE="${GENERATED_DIR}/recovery-job-partition-${PARTITION_ID:-UNKNOWN}-${TIMESTAMP}.yaml"

# Number of brokers in the cluster
BROKER_COUNT="${BROKER_COUNT:-3}"

# Container image (auto-detected from StatefulSet if not set)
CONTAINER_IMAGE="${CONTAINER_IMAGE:-}"

# StatefulSet name (older versions might use different names)
STATEFULSET_NAME="${STATEFULSET_NAME:-camunda}"

# Pod name prefix (older versions might use different prefixes)
POD_PREFIX="${POD_PREFIX:-${STATEFULSET_NAME}}"

# PVC name prefix (older versions use different patterns)
# Examples: "data-camunda", "data-zeebe", "zeebe-data-zeebe"
PVC_PREFIX="${PVC_PREFIX:-data-${STATEFULSET_NAME}}"

# Actuator port
ACTUATOR_PORT="${ACTUATOR_PORT:-9600}"

# Key recovery values (REQUIRED - must be set by user)
NEW_KEY="${NEW_KEY:-}"
# Max Key OPTIONAL
NEW_MAX_KEY="${NEW_MAX_KEY:-}"

# Container security context - use Camunda defaults
# User: 1000 (camunda), Group: 1000, fsGroup: 1001
CONTAINER_USER_ID="${CONTAINER_USER_ID:-1000}"
CONTAINER_GROUP_ID="${CONTAINER_GROUP_ID:-1001}"
CONTAINER_FS_GROUP="${CONTAINER_FS_GROUP:-1001}"

# State file for tracking recovery progress
STATE_FILE="${GENERATED_DIR}/.recovery-state-partition-${PARTITION_ID:-UNKNOWN}.env"

# ====================================================
# Colors for output
# ====================================================
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# ====================================================
# State Management Functions
# ====================================================

function save_state() {
        mkdir -p "$GENERATED_DIR"
        cat >"$STATE_FILE" <<EOF
# Recovery state for partition $PARTITION_ID
# Generated: $(date)
LAST_LEADER_BROKER="${LAST_LEADER_BROKER:-}"
PARTITION_BROKER_IDS="${PARTITION_BROKER_IDS:-}"
CONTAINER_IMAGE="${CONTAINER_IMAGE:-}"
GENERATED_JOB_FILE="${GENERATED_JOB_FILE:-}"
LAST_COMPLETED_STEP="${1:-}"
EOF
        print_info "State saved to: $STATE_FILE"
}

function load_state() {
        if [ -f "$STATE_FILE" ]; then
                print_info "Loading previous recovery state from: $STATE_FILE"
                source "$STATE_FILE"
                echo "  Last completed step: ${LAST_COMPLETED_STEP:-none}"
                echo "  Leader broker: ${LAST_LEADER_BROKER:-unknown}"
                echo "  Partition brokers: ${PARTITION_BROKER_IDS:-unknown}"
                echo ""
                return 0
        else
                return 1
        fi
}

function should_run_step() {
        local step_name="$1"

        # If --from-step is specified, check if we should start from this step
        if [ -n "$START_FROM_STEP" ]; then
                if [ "$step_name" = "$START_FROM_STEP" ]; then
                        # Found the starting step, run it and all subsequent steps
                        START_FROM_STEP="running"
                        return 0
                elif [ "$START_FROM_STEP" = "running" ]; then
                        # Already started, run this step
                        return 0
                else
                        # Haven't reached the starting step yet
                        return 1
                fi
        fi

        # No --from-step specified, run all steps
        return 0
}

function mark_step_complete() {
        local step_name="$1"
        save_state "$step_name"
        print_success "Step completed: $step_name"
        echo ""
}

# ====================================================
# Helper Functions
# ====================================================

# Generate pod name for broker ID
function get_pod_name() {
        local broker_id=$1
        echo "${POD_PREFIX}-${broker_id}"
}

# Generate PVC name for broker ID
function get_pvc_name() {
        local broker_id=$1
        echo "${PVC_PREFIX}-${broker_id}"
}

# Get list of all broker IDs
function get_broker_ids() {
        seq 0 $((BROKER_COUNT - 1))
}

# Get list of all pod names
function get_all_pod_names() {
        local pods=()
        for i in $(get_broker_ids); do
                pods+=("pod/$(get_pod_name $i)")
        done
        echo "${pods[@]}"
}

# ====================================================
# Output Functions
# ====================================================

function print_header() {
        echo ""
        echo -e "${BLUE}=============================================${NC}"
        echo -e "${BLUE}=== $1${NC}"
        echo -e "${BLUE}=============================================${NC}"
        echo ""
}

function print_step() {
        echo -e "${GREEN}[STEP $1]${NC} $2"
        echo ""
}

function print_info() {
        echo -e "${YELLOW}INFO:${NC} $1"
}

function print_error() {
        echo -e "${RED}ERROR:${NC} $1"
}

function print_success() {
        echo -e "${GREEN}SUCCESS:${NC} $1"
}

function pause_for_user() {
        echo ""
        read -p "Press ENTER to continue..."
        echo ""
}

function identify_partition_leader() {
	print_header "Identifying Partition Leader and Replicas"

	echo "Checking partition $PARTITION_ID across all $BROKER_COUNT brokers to find the leader and replicas..."
	echo ""

	if [ "$DRY_RUN" = true ]; then
		# In dry-run mode, simulate a typical setup for YAML generation
		print_info "[DRY-RUN] Simulating partition replica discovery for YAML generation..."
		LEADER_BROKER=0
		PARTITION_REPLICAS=(0 1 2)
		print_success "[DRY-RUN] Using simulated leader: $(get_pod_name $LEADER_BROKER)"
		print_info "[DRY-RUN] Using simulated replicas (${#PARTITION_REPLICAS[@]} total):"
		for replica_id in "${PARTITION_REPLICAS[@]}"; do
			echo "  - Broker $replica_id: $(get_pod_name $replica_id)"
		done
		export LAST_LEADER_BROKER=$LEADER_BROKER
		export PARTITION_BROKER_IDS="${PARTITION_REPLICAS[*]}"
		return
	fi

	# Export variables needed by identify-partition-brokers.sh
	export NAMESPACE
	export PARTITION_ID
	export BROKER_COUNT
	export STATEFULSET_NAME
	export POD_PREFIX
	export ACTUATOR_PORT
	export GENERATED_DIR

	# Call the standalone script to identify brokers
	if ! ./identify-partition-brokers.sh; then
		print_error "Failed to identify partition brokers"
		exit 1
	fi

	# Source the generated state file
	BROKER_STATE_FILE="${GENERATED_DIR}/.partition-brokers-${PARTITION_ID}.env"
	if [ ! -f "$BROKER_STATE_FILE" ]; then
		print_error "Broker state file not found: $BROKER_STATE_FILE"
		exit 1
	fi
	source "$BROKER_STATE_FILE"

	# Convert to expected variable names and format
	LEADER_BROKER=$LEADER
	PARTITION_REPLICAS=($REPLICAS)

	echo ""
	print_success "Partition $PARTITION_ID leader is: $(get_pod_name $LEADER_BROKER)"
	print_info "Partition $PARTITION_ID replicas (${#PARTITION_REPLICAS[@]} total):"
	for replica_id in "${PARTITION_REPLICAS[@]}"; do
		echo "  - Broker $replica_id: $(get_pod_name $replica_id)"
	done

	export LAST_LEADER_BROKER=$LEADER_BROKER
	export PARTITION_BROKER_IDS="${PARTITION_REPLICAS[*]}"
}

function check_cluster_state() {
        print_header "Checking Current Cluster State"

        echo "Pods:"
        kubectl get pods -n $NAMESPACE | grep "^${POD_PREFIX}" || echo "  (no pods found with prefix '${POD_PREFIX}')"
        echo ""

        echo "PVCs:"
        kubectl get pvc -n $NAMESPACE | grep "^${PVC_PREFIX}" || echo "  (no PVCs found with prefix '${PVC_PREFIX}')"
        echo ""

        echo "StatefulSet:"
        kubectl get statefulset $STATEFULSET_NAME -n $NAMESPACE || echo "  (StatefulSet '$STATEFULSET_NAME' not found)"
        echo ""

        # Auto-detect broker count from StatefulSet replicas
        print_info "Detecting broker count from StatefulSet..."
        DETECTED_BROKER_COUNT=$(kubectl get statefulset $STATEFULSET_NAME -n $NAMESPACE -o jsonpath='{.spec.replicas}' 2>/dev/null || echo "0")

        # Use detected count if found, otherwise use environment variable or default
        if [ "$DETECTED_BROKER_COUNT" -gt 0 ]; then
                BROKER_COUNT=$DETECTED_BROKER_COUNT
                print_success "Detected broker count from StatefulSet: $BROKER_COUNT"
        else
                print_info "Could not auto-detect broker count, using configured value: $BROKER_COUNT"
        fi

        echo "Detected Configuration:"
        echo "  Broker Count:        $BROKER_COUNT"
        echo "  StatefulSet Name:    $STATEFULSET_NAME"
        echo "  Pod Prefix:          $POD_PREFIX"
        echo "  PVC Prefix:          $PVC_PREFIX"
        echo ""

        # Detect the container image from StatefulSet (if not already set)
        if [ -z "$CONTAINER_IMAGE" ]; then
                print_info "Detecting container image from StatefulSet..."
                DETECTED_IMAGE=$(kubectl get statefulset $STATEFULSET_NAME -n $NAMESPACE -o jsonpath='{.spec.template.spec.containers[0].image}' 2>/dev/null || echo "")

                if [ -z "$DETECTED_IMAGE" ]; then
                        print_error "Could not detect container image from StatefulSet"
                        print_info "Falling back to default image: camunda/camunda:latest"
                        CONTAINER_IMAGE="camunda/camunda:latest"
                else
                        print_success "Detected container image: $DETECTED_IMAGE"
                        CONTAINER_IMAGE="$DETECTED_IMAGE"
                fi
        else
                print_info "Using container image from environment: $CONTAINER_IMAGE"
        fi

        export CONTAINER_IMAGE
        export BROKER_COUNT
        echo ""

        if [ "$DRY_RUN" = true ]; then
                print_info "[DRY-RUN] Cluster state retrieved successfully"
                print_info "[DRY-RUN] No changes will be made"
        fi
}

function shutdown_cluster() {
        print_header "Shutting Down Cluster"

        if [ "$DRY_RUN" = true ]; then
                print_info "[DRY-RUN] Would scale StatefulSet '$STATEFULSET_NAME' to 0 replicas"
                echo ""
                echo "[DRY-RUN] Would run:"
                echo "  kubectl scale statefulset $STATEFULSET_NAME --replicas=0 -n $NAMESPACE"
                echo "  kubectl wait --for=delete $(get_all_pod_names) -n $NAMESPACE --timeout=300s"
                echo ""
                print_success "[DRY-RUN] Would shut down cluster"
                return
        fi

        print_info "Scaling StatefulSet '$STATEFULSET_NAME' to 0 replicas..."

        kubectl scale statefulset $STATEFULSET_NAME --replicas=0 -n $NAMESPACE

        echo "Waiting for pods to terminate..."
        local all_pods=$(get_all_pod_names)
        kubectl wait --for=delete $all_pods -n $NAMESPACE --timeout=300s || true

        echo ""
        echo "Current pods:"
        kubectl get pods -n $NAMESPACE | grep "^${POD_PREFIX}" || echo "  (no ${POD_PREFIX} pods running)"
        echo ""

        print_success "Cluster shutdown complete"
}

function backup_reminder() {
        print_header "Backup Reminder"

        print_info "IMPORTANT: Please ensure you have already taken a COLD BACKUP of the broker data."
        print_info "This is NOT part of the recovery job - it's a manual prerequisite step."
        print_info ""
        print_info "The backup should include snapshots of all PVCs or data volumes."
        print_info ""

        pause_for_user
}

function run_recovery_job() {
        print_header "Running Recovery Job"

        # Update the job with the correct leader broker
        print_info "Updating recovery job with leader broker: $LAST_LEADER_BROKER"
        print_info "Mounting PVCs only for partition $PARTITION_ID replicas: $PARTITION_BROKER_IDS"

        if [ "$DRY_RUN" = false ]; then
                # Check if recovery job already exists and delete it
                if kubectl get job key-recovery-job -n $NAMESPACE &>/dev/null; then
                        print_info "Deleting existing recovery job..."
                        kubectl delete job key-recovery-job -n $NAMESPACE --wait=true
                fi
        fi

	print_info "Generating recovery job manifest with inline script and dynamic PVC mounts..."

	# Create generated directory if it doesn't exist
	mkdir -p "$GENERATED_DIR"

	# Export variables needed by generate-recovery-job.sh
	export NAMESPACE
	export PARTITION_ID
	export LAST_LEADER_BROKER
	export NEW_KEY
	export NEW_MAX_KEY
	export PARTITION_BROKER_IDS
	export STATEFULSET_NAME
	export PVC_PREFIX
	export CONTAINER_IMAGE
	export CONTAINER_USER_ID
	export CONTAINER_GROUP_ID
	export CONTAINER_FS_GROUP
	export GENERATED_DIR
	export OUTPUT_FILE="$GENERATED_JOB_FILE"

	# Generate the Job YAML using standalone script
	if ! GENERATED_JOB_FILE=$(./generate-recovery-job.sh); then
		print_error "Failed to generate recovery Job YAML"
		exit 1
	fi

	# Read the generated YAML for display
	GENERATED_JOB_YAML=$(cat "$GENERATED_JOB_FILE")
	print_success "Generated Job YAML saved to: $GENERATED_JOB_FILE"

	echo ""
	print_header "Generated Recovery Job YAML"
	echo ""
	echo "The following Job will be applied to namespace: $NAMESPACE"
	echo ""
	echo "PVCs to be mounted:"
	for broker_id in $PARTITION_BROKER_IDS; do
		echo "  - ${PVC_PREFIX}-${broker_id} (Broker ${broker_id})"
	done
	echo ""
	echo "----------------------------------------"
	echo "$GENERATED_JOB_YAML"
	echo "----------------------------------------"
	echo ""

        if [ "$DRY_RUN" = true ]; then
                print_info "[DRY-RUN] Job YAML has been saved to: $GENERATED_JOB_FILE"
                print_info "[DRY-RUN] You can review and edit this file, then apply it with:"
                echo "  kubectl apply -f $GENERATED_JOB_FILE"
                echo ""
                print_info "[DRY-RUN] Would wait for job to complete and follow logs"
                return
        fi

        print_info "Please review the generated Job YAML above."
        print_info "The YAML has been saved to: $GENERATED_JOB_FILE"
        echo ""
        echo "You can:"
        echo "  1. Apply the Job now (type 'yes')"
        echo "  2. Exit to review/edit the file first (type 'no')"
        echo ""
        read -p "Do you want to apply this Job now? (yes/no): " APPLY_CONFIRMATION
        echo ""

        if [ "$APPLY_CONFIRMATION" != "yes" ] && [ "$APPLY_CONFIRMATION" != "y" ]; then
                print_info "Job not applied. You can review/edit the file and apply it manually:"
                echo "  kubectl apply -f $GENERATED_JOB_FILE"
                echo ""
                print_info "After applying the job, you can monitor it with:"
                echo "  kubectl logs -f job/key-recovery-job -n $NAMESPACE"
                echo "  kubectl wait --for=condition=complete --timeout=600s job/key-recovery-job -n $NAMESPACE"
                exit 0
        fi

        print_info "Applying recovery job from file: $GENERATED_JOB_FILE"
        kubectl apply -f "$GENERATED_JOB_FILE"

        echo ""
        print_info "Recovery job started. Waiting for pod to be ready..."
        echo ""

        # Wait for the pod to be created
        sleep 3

        # Follow logs in background so we can also wait for completion
        print_info "Following job logs..."
        kubectl logs -f job/key-recovery-job -n $NAMESPACE 2>&1 &
        LOGS_PID=$!

        echo ""
        print_info "Waiting for job to complete..."

        # Poll job status until it completes or fails (max 10 minutes)
        TIMEOUT=600
        ELAPSED=0
        JOB_DONE=false

        while [ $ELAPSED -lt $TIMEOUT ]; do
                # Check job status
                JOB_STATUS=$(kubectl get job key-recovery-job -n $NAMESPACE -o jsonpath='{.status.conditions[?(@.status=="True")].type}' 2>/dev/null || echo "")

                if [[ "$JOB_STATUS" == *"Complete"* ]]; then
                        JOB_DONE=true
                        break
                elif [[ "$JOB_STATUS" == *"Failed"* ]]; then
                        JOB_DONE=true
                        break
                fi

                sleep 5
                ELAPSED=$((ELAPSED + 5))
        done

        # Kill the logs follow if still running
        kill $LOGS_PID 2>/dev/null || true
        wait $LOGS_PID 2>/dev/null || true
        echo ""

        if [ "$JOB_DONE" = false ]; then
                print_error "Recovery job did not complete within timeout (${TIMEOUT}s)"
                echo ""
                echo "Job status:"
                kubectl get job key-recovery-job -n $NAMESPACE
                echo ""
                echo "Recent logs:"
                kubectl logs job/key-recovery-job -n $NAMESPACE --tail=50 || true
                exit 1
        fi

        # Check final status
        if [[ "$JOB_STATUS" == *"Complete"* ]]; then
                print_success "Recovery job completed successfully!"
        elif [[ "$JOB_STATUS" == *"Failed"* ]]; then
                print_error "Recovery job failed!"
                echo ""
                echo "Job status:"
                kubectl get job key-recovery-job -n $NAMESPACE
                echo ""
                echo "Pod logs:"
                kubectl logs job/key-recovery-job -n $NAMESPACE --tail=100
                echo ""
                print_info "Full logs available with: kubectl logs job/key-recovery-job -n $NAMESPACE"
                exit 1
        else
                print_error "Recovery job finished with unknown status: $JOB_STATUS"
                kubectl get job key-recovery-job -n $NAMESPACE
                exit 1
        fi
}

function cleanup_recovery_job() {
        print_header "Cleaning Up Recovery Job"

        if [ "$DRY_RUN" = true ]; then
                print_info "[DRY-RUN] Would delete recovery job"
                echo ""
                echo "[DRY-RUN] Would run:"
                echo "  kubectl delete job key-recovery-job -n $NAMESPACE --wait=true"
                echo ""
                return
        fi

        print_info "Deleting recovery job..."
        kubectl delete job key-recovery-job -n $NAMESPACE --wait=true

        print_success "Recovery job deleted"
}

function print_summary() {
        print_header "Recovery Procedure Summary"

        if [ "$DRY_RUN" = true ]; then
                echo "[DRY-RUN] The following actions would be performed:"
                echo ""
                echo "1. Identify partition $PARTITION_ID leader and replicas"
                echo "2. Shut down the Camunda cluster (scale to 0)"
                echo "3. Generate and save recovery Job YAML to: $GENERATED_JOB_FILE"
                echo "4. Apply recovery job which would:"
                echo "   - Backup existing snapshots (.backup)"
                echo "   - Update key values using cdbg on leader's snapshot"
                echo "   - Copy updated snapshot to all replicas"
                echo "   - Clean up backup files"
                echo ""
                echo "Recovery job parameters:"
                echo "  - Partition ID:     $PARTITION_ID"
                echo "  - Leader Broker:    $LAST_LEADER_BROKER"
                if [ -n "$NEW_KEY" ]; then
                        echo "  - New Key:          $NEW_KEY"
                fi
                if [ -n "$NEW_MAX_KEY" ]; then
                        echo "  - New Max Key:      $NEW_MAX_KEY"
                fi
                echo ""
                echo "Cluster configuration:"
                echo "  - Namespace:        $NAMESPACE"
                echo "  - StatefulSet:      $STATEFULSET_NAME"
                echo "  - Pod Prefix:       $POD_PREFIX"
                echo "  - PVC Prefix:       $PVC_PREFIX"
                echo ""
                print_success "[DRY-RUN] Dry-run completed!"
                print_info "Generated Job YAML saved to: $GENERATED_JOB_FILE"
                echo ""
                echo "To execute the recovery procedure for real, run:"
                echo "  ./recovery-procedure.sh"
                echo ""
                echo "Or review/edit the generated Job and apply it manually:"
                echo "  kubectl apply -f $GENERATED_JOB_FILE"
                return
        fi

        echo "The recovery procedure has been completed. Here's what was done:"
        echo ""
        echo "1. Identified partition $PARTITION_ID leader: $(get_pod_name $LAST_LEADER_BROKER)"
        echo "2. Shut down the Camunda cluster (scaled to 0)"
        echo "3. Ran the recovery job which:"
        echo "   - Backed up existing snapshots (.backup)"
        echo "   - Updated key values using cdbg on leader's snapshot"
        echo "   - Copied updated snapshot to all replicas"
        echo "   - Cleaned up backup files"
        echo ""
        echo "Recovery job parameters:"
        echo "  - Partition ID:     $PARTITION_ID"
        echo "  - Leader Broker:    $LAST_LEADER_BROKER"
        if [ -n "$NEW_KEY" ]; then
                echo "  - New Key:          $NEW_KEY"
        fi
        if [ -n "$NEW_MAX_KEY" ]; then
                echo "  - New Max Key:      $NEW_MAX_KEY"
        fi
        echo ""
        echo "Cluster configuration:"
        echo "  - Namespace:        $NAMESPACE"
        echo "  - Broker Count:     $BROKER_COUNT"
        echo "  - StatefulSet:      $STATEFULSET_NAME"
        echo "  - Pod Prefix:       $POD_PREFIX"
        echo "  - PVC Prefix:       $PVC_PREFIX"
        echo ""
        echo "Generated Job YAML saved to: $GENERATED_JOB_FILE"
        echo ""
        print_success "Recovery procedure completed!"
}

#
# Main execution flow
#

print_header "Camunda Key Recovery Procedure"

if [ "$DRY_RUN" = true ]; then
        print_info "[DRY-RUN MODE] No changes will be made to the cluster"
        echo ""
fi

echo "This script guides you through the key recovery procedure for a Camunda partition."
echo "Follow the prompts to safely recover from a corrupted key generator."
echo ""
echo "Environment:"
echo "  Namespace:         $NAMESPACE"
echo "  Partition:         $PARTITION_ID"
echo "  Recovery ConfigMap: $RECOVERY_CONFIGMAP_FILE"
echo "  Recovery Job:      $RECOVERY_JOB_FILE"
if [ "$DRY_RUN" = true ]; then
        echo "  Output File:       $GENERATED_JOB_FILE"
fi
echo ""
echo "Cluster Configuration:"
echo "  Broker Count:      $BROKER_COUNT"
echo "  StatefulSet Name:  $STATEFULSET_NAME"
echo "  Pod Prefix:        $POD_PREFIX"
echo "  PVC Prefix:        $PVC_PREFIX"
echo "  Actuator Port:     $ACTUATOR_PORT"
echo ""
echo "Detected Brokers:"
for i in $(get_broker_ids); do
        echo "  - Broker $i: $(get_pod_name $i) (PVC: $(get_pvc_name $i))"
done
echo ""

# Validate required environment variables
if [ -z "$NAMESPACE" ]; then
        print_error "NAMESPACE environment variable is not set"
        echo ""
        echo "Please set the NAMESPACE environment variable before running this script:"
        echo "  export NAMESPACE=\"<your-namespace>\""
        echo ""
        echo "Example:"
        echo "  export NAMESPACE=\"camunda\""
        echo ""
        echo "To auto-detect your configuration, run:"
        echo "  ./detect-config.sh <namespace>"
        echo ""
        exit 1
fi

# Validate namespace exists
if ! kubectl get namespace "$NAMESPACE" >/dev/null 2>&1; then
        print_error "Namespace '$NAMESPACE' does not exist"
        echo ""
        echo "Available namespaces:"
        kubectl get namespaces -o custom-columns=NAME:.metadata.name --no-headers | sed 's/^/  - /'
        echo ""
        echo "Please set NAMESPACE to a valid namespace:"
        echo "  export NAMESPACE=\"<your-namespace>\""
        echo ""
        exit 1
fi

if [ -z "$PARTITION_ID" ]; then
        print_error "PARTITION_ID environment variable is not set"
        echo ""
        echo "Please set the PARTITION_ID environment variable before running this script:"
        echo "  export PARTITION_ID=\"<partition-id>\""
        echo ""
        echo "Example:"
        echo "  export PARTITION_ID=\"1\""
        echo ""
        exit 1
fi

# Validate required environment variables for recovery job
if [ -z "$NEW_KEY" ]; then
        print_error "NEW_KEY environment variable is not set"
        echo ""
        echo "Please set the NEW_KEY environment variable before running this script:"
        echo "  export NEW_KEY=\"<new-key-value>\""
        echo ""
        echo "Example for partition $PARTITION_ID:"
        echo "  export NEW_KEY=\"2251800000000000\""
        echo ""
        exit 1
fi

echo "Recovery Parameters:"
echo "  New Key:           $NEW_KEY"
if [ -n "$NEW_MAX_KEY" ]; then
        echo "  New Max Key:       $NEW_MAX_KEY"
else
        echo "  New Max Key:       <not set - will not update max key>"
fi
echo ""

# Try to load previous state if resuming
if [ -n "$START_FROM_STEP" ]; then
        if load_state; then
                print_info "Resuming from step: $START_FROM_STEP"
                echo ""
        else
                print_error "No previous state found. Cannot resume."
                echo "State file not found: $STATE_FILE"
                echo ""
                echo "Please run the full recovery procedure first, or start without --from-step"
                exit 1
        fi
fi

if [ "$DRY_RUN" = false ] && [ -z "$START_FROM_STEP" ]; then
        pause_for_user
fi

# Step 1: Identify leader
if should_run_step "identify-leader"; then
        identify_partition_leader
        mark_step_complete "identify-leader"
        if [ "$DRY_RUN" = false ]; then
                pause_for_user
        fi
else
        print_info "Skipping step: identify-leader (resuming from later step)"
fi

# Step 2: Check cluster state
if should_run_step "check-cluster"; then
        check_cluster_state
        mark_step_complete "check-cluster"
        if [ "$DRY_RUN" = false ]; then
                pause_for_user
        fi
else
        print_info "Skipping step: check-cluster (resuming from later step)"
fi

# Step 3: Shutdown cluster
if should_run_step "shutdown"; then
        shutdown_cluster
        mark_step_complete "shutdown"
        if [ "$DRY_RUN" = false ]; then
                pause_for_user
        fi
else
        print_info "Skipping step: shutdown (resuming from later step)"
fi

# Step 4: Backup reminder
if should_run_step "backup-reminder"; then
        if [ "$DRY_RUN" = false ]; then
                backup_reminder
                mark_step_complete "backup-reminder"
        fi
else
        print_info "Skipping step: backup-reminder (resuming from later step)"
fi

# Step 5: Run recovery job (or generate YAML in dry-run mode)
if should_run_step "run-job"; then
        run_recovery_job
        mark_step_complete "run-job"

        # In dry-run mode, stop here and show summary
        if [ "$DRY_RUN" = true ]; then
                print_summary
                exit 0
        fi
        pause_for_user
else
        print_info "Skipping step: run-job (resuming from later step)"
fi

# Step 6: Cleanup job
if should_run_step "cleanup-job"; then
        cleanup_recovery_job
        mark_step_complete "cleanup-job"
        pause_for_user
else
        print_info "Skipping step: cleanup-job (resuming from later step)"
fi

# Summary
print_summary

# Clean up state file on successful completion
if [ -f "$STATE_FILE" ]; then
        rm -f "$STATE_FILE"
        print_info "Recovery state file removed (recovery completed successfully)"
fi
