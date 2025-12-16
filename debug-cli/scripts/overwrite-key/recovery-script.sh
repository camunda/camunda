#!/bin/bash
#
# Camunda Key Recovery Script
#
# This script performs key recovery on a Camunda partition by:
#   1. Backing up existing snapshots
#   2. Running cdbg to update key values
#   3. Replicating updated snapshot to all brokers
#   4. Cleaning up backups
#
# This script is designed to run inside a Kubernetes Job with access to all broker PVCs.
#
# Required Environment Variables:
#   PARTITION_ID        - Partition to recover
#   LAST_LEADER_BROKER  - Broker ID that was last leader (0, 1, 2, ...)
#   NEW_KEY             - New key value to set
#   PARTITION_BROKER_IDS - Space-separated list of broker IDs hosting this partition
#
# Optional Environment Variables:
#   NEW_MAX_KEY         - New max key value (if not set, --max-key won't be passed to cdbg)
#   SNAPSHOT_ID         - Specific snapshot to use (default: auto-detect latest)
#
#

set -euo pipefail

# Configuration from environment variables
: "${PARTITION_ID:?PARTITION_ID must be set}"
: "${LAST_LEADER_BROKER:?LAST_LEADER_BROKER must be set}"
: "${NEW_KEY:?NEW_KEY must be set}"
LEADER_BROKER="$LAST_LEADER_BROKER"
NEW_MAX_KEY="${NEW_MAX_KEY:-}"                   # Optional
SNAPSHOT_ID="${SNAPSHOT_ID:-}"                   # Optional override
PARTITION_BROKER_IDS="${PARTITION_BROKER_IDS:-}" # Space-separated broker IDs

LEADER_PATH="/mnt/broker-${LEADER_BROKER}/raft-partition/partitions/${PARTITION_ID}"
RUNTIME_PATH="/tmp/recovery-runtime-${PARTITION_ID}"

echo "============================================="
echo "=== Key Recovery Job Starting ==="
echo "============================================="
echo "Partition ID:       ${PARTITION_ID}"
echo "Leader Broker:      ${LEADER_BROKER}"
echo "Partition Brokers:  ${PARTITION_BROKER_IDS}"
echo "New Key:            ${NEW_KEY}"
echo "New Max Key:        ${NEW_MAX_KEY:-<not set>}"
echo "Snapshot Override:  ${SNAPSHOT_ID:-<auto-detect latest>}"
echo "============================================="
echo ""

# Validate leader partition path exists
if [ ! -d "${LEADER_PATH}" ]; then
        echo "ERROR: Leader partition path does not exist: ${LEADER_PATH}"
        exit 1
fi

echo "[1/5] Validating partition data..."
echo "Leader partition path: ${LEADER_PATH}"
ls -la "${LEADER_PATH}"
echo ""

# Step 2: Determine snapshot to use
echo "[2/5] Determining snapshot to use..."
if [ -n "${SNAPSHOT_ID}" ]; then
        echo "Using snapshot from SNAPSHOT_ID env var: ${SNAPSHOT_ID}"
        SELECTED_SNAPSHOT="${SNAPSHOT_ID}"
else
        # Auto-detect snapshot (exclude .checksum files)
        snapshots=()
        for snap in "${LEADER_PATH}/snapshots/"*/; do
                # skip it if it's not a directory
                [ -d "$snap" ] || continue
                # skip it if it's a checksum file
                [[ "$(basename "$snap")" == *.checksum ]] && continue
                snapshots+=("$snap")
        done
        SNAPSHOT_COUNT=${#snapshots[@]}

        if [ "$SNAPSHOT_COUNT" -eq 0 ]; then
                echo "ERROR: No snapshots found in ${LEADER_PATH}/snapshots/"
                exit 1
        elif [ "$SNAPSHOT_COUNT" -gt 1 ]; then
                echo "ERROR: Multiple snapshots found, but SNAPSHOT_ID not specified"
                echo ""
                echo "Available snapshots:"
                for snapshot in "${snapshots[@]}"; do
                        snapshot_name=$(basename "$snapshot")
                        SIZE=$(du -sh "$snapshot" 2>/dev/null | cut -f1)
                        MODIFIED=$(stat -c %y "$snapshot" 2>/dev/null | cut -d'.' -f1)
                        echo "  - ${snapshot_name} (${SIZE}, modified: ${MODIFIED})"
                done
                echo ""
                echo "Please set SNAPSHOT_ID environment variable to specify which snapshot to use:"
                echo "  SNAPSHOT_ID=<snapshot-name>"
                echo ""
                echo "Example:"
                EXAMPLE_SNAPSHOT=$(basename "${snapshots[0]}")
                echo "  SNAPSHOT_ID=${EXAMPLE_SNAPSHOT}"
                exit 1
        else
                # Exactly one snapshot found
                SELECTED_SNAPSHOT=$(basename "${snapshots[0]}")
                echo "Auto-detected snapshot: ${SELECTED_SNAPSHOT}"
        fi
fi

echo "Snapshot details:"
ls -la "${LEADER_PATH}/snapshots/${SELECTED_SNAPSHOT}"
echo "snapshot metadata:"
cat "${LEADER_PATH}/snapshots/${SELECTED_SNAPSHOT}/zeebe.metadata"
printf "\n"
echo ""

# Step 3: Create backups of all broker partition data to temporary location
echo "[3/5] Creating backups of partition data on all replicas..."
echo "Backups will be stored in each broker's volume under 'snapshots-backup'"
echo ""

# First, check if any backups already exist (from previous failed run)
for broker_id in $PARTITION_BROKER_IDS; do
        BROKER_BACKUP_PATH="/mnt/broker-${broker_id}/raft-partition/partitions/${PARTITION_ID}/snapshots-backup"

        if [ -d "${BROKER_BACKUP_PATH}" ]; then
                echo "ERROR: Existing backup found at ${BROKER_BACKUP_PATH}"
                echo ""
                echo "This indicates a previous recovery attempt may have failed."
                echo "To proceed, manually review and remove existing backups:"
                echo ""
                for bid in $PARTITION_BROKER_IDS; do
                        BBACKUP="/mnt/broker-${bid}/raft-partition/partitions/${PARTITION_ID}/snapshots-backup"
                        [ -d "${BBACKUP}" ] && echo "  kubectl exec <pod> -- rm -rf ${BBACKUP}"
                done
                echo ""
                echo "Only remove these backups if you're certain they're no longer needed!"
                exit 1
        fi
done

for broker_id in $PARTITION_BROKER_IDS; do
        BROKER_PATH="/mnt/broker-${broker_id}/raft-partition/partitions/${PARTITION_ID}"
        BROKER_BACKUP_PATH="${BROKER_PATH}/snapshots-backup"

        if [ -d "${BROKER_PATH}/snapshots" ]; then
                echo "  Backing up Broker ${broker_id}..."
                mkdir -p "${BROKER_BACKUP_PATH}"
                cp -r "${BROKER_PATH}/snapshots/"* "${BROKER_BACKUP_PATH}/"
                sync # Ensure backup is persisted to disk

                # Verify backup was created
                if [ -d "${BROKER_BACKUP_PATH}" ]; then
                        BACKUP_SIZE=$(du -sh "${BROKER_BACKUP_PATH}" | cut -f1)
                        BACKUP_COUNT=$(find "${BROKER_BACKUP_PATH}" -maxdepth 1 -type d ! -name "$(basename "${BROKER_BACKUP_PATH}")" ! -name "*.checksum" 2>/dev/null | wc -l)
                        echo "    ${BACKUP_SIZE} (${BACKUP_COUNT} snapshots)"
                else
                        echo "    ERROR: Backup failed for Broker ${broker_id}"
                        exit 1
                fi
        else
                echo "  WARNING: No snapshots directory for Broker ${broker_id}"
        fi
done
echo "Backups created successfully"
echo ""

# Step 4: Run cdbg to update key values on leader
echo "[4/5] Updating key values using cdbg on leader broker ${LEADER_BROKER}..."

# Build the cdbg command
CDBG_CMD="/usr/local/camunda/bin/cdbg state update-key"
CDBG_CMD="${CDBG_CMD} --root ${LEADER_PATH}"
CDBG_CMD="${CDBG_CMD} --runtime ${RUNTIME_PATH}"
CDBG_CMD="${CDBG_CMD} --snapshot ${SELECTED_SNAPSHOT}"
CDBG_CMD="${CDBG_CMD} --partition-id ${PARTITION_ID}"
CDBG_CMD="${CDBG_CMD} --key ${NEW_KEY}"
[ -n "${NEW_MAX_KEY}" ] && CDBG_CMD="${CDBG_CMD} --max-key ${NEW_MAX_KEY}"
CDBG_CMD="${CDBG_CMD} --verbose"

# Print the full command being executed
echo "Running: ${CDBG_CMD}"
echo ""

eval "${CDBG_CMD}"

echo ""
echo "Key update completed successfully!"
echo ""

# Delete the original snapshot since cdbg created a new one
echo "Removing original snapshot: ${SELECTED_SNAPSHOT}"
rm -rf "${LEADER_PATH}/snapshots/${SELECTED_SNAPSHOT}"
# Also remove the checksum file if it exists
[ -f "${LEADER_PATH}/snapshots/${SELECTED_SNAPSHOT}.checksum" ] && rm -f "${LEADER_PATH}/snapshots/${SELECTED_SNAPSHOT}.checksum"
echo "Original snapshot removed"
echo ""

echo "Updated snapshot location: ${LEADER_PATH}/snapshots/"
ls -la "${LEADER_PATH}/snapshots/"
echo ""

# Step 5: Copy updated partition data to replicas
echo "[5/5] Copying updated partition data to replicas..."

for broker_id in $PARTITION_BROKER_IDS; do
        if [ "$broker_id" != "$LEADER_BROKER" ]; then
                REPLICA_PATH="/mnt/broker-${broker_id}/raft-partition/partitions/${PARTITION_ID}"
                echo "  Syncing to Broker ${broker_id}..."

                # Remove all existing snapshots and copy new ones from leader
                rm -rf "${REPLICA_PATH}/snapshots/"*
                cp -r "${LEADER_PATH}/snapshots/"* "${REPLICA_PATH}/snapshots/"
                sync # Ensure replicated snapshots are persisted to disk

                # Verify copy
                REPLICA_SNAPSHOT_COUNT=$(find "${REPLICA_PATH}/snapshots/" -maxdepth 1 -type d ! -name "snapshots" ! -name "*.checksum" 2>/dev/null | wc -l)
                echo "    Updated (${REPLICA_SNAPSHOT_COUNT} snapshots)"
        fi
done

echo ""
echo "Ensuring all data is persisted to disk..."
sync
echo "All changes flushed to disk"
echo ""
echo "============================================="
echo "=== Recovery Complete ==="
echo "============================================="
echo "Partition ${PARTITION_ID} has been recovered with:"
echo "  - Key:     ${NEW_KEY}"
[ -n "${NEW_MAX_KEY}" ] && echo "  - Max Key: ${NEW_MAX_KEY}"
echo "  - Snapshot: ${SELECTED_SNAPSHOT}"
echo ""
echo "All brokers have been updated with the new snapshot."
echo ""
echo "IMPORTANT: Backups are preserved in 'snapshots-backup' directories."
echo "After verifying successful recovery, clean them up manually:"
echo ""
for broker_id in $PARTITION_BROKER_IDS; do
        echo "  kubectl exec \${POD_PREFIX}-${broker_id} -n \${NAMESPACE} -- rm -rf /usr/local/zeebe/data/raft-partition/partitions/${PARTITION_ID}/snapshots-backup"
done
echo ""
echo "You can now restart the cluster."
echo "============================================="
