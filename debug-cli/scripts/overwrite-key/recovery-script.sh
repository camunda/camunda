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
PARTITION_ID="${PARTITION_ID}"
LEADER_BROKER="$LAST_LEADER_BROKER"
NEW_KEY="${NEW_KEY}"
NEW_MAX_KEY="${NEW_MAX_KEY:-}"                   # Optional
SNAPSHOT_ID="${SNAPSHOT_ID:-}"                   # Optional override
PARTITION_BROKER_IDS="${PARTITION_BROKER_IDS:-}" # Space-separated broker IDs

LEADER_PATH="/mnt/broker-${LEADER_BROKER}/raft-partition/partitions/${PARTITION_ID}"
RUNTIME_PATH="/tmp/recovery-runtime-${PARTITION_ID}"
BACKUP_BASE_PATH="/tmp/recovery-backup-${PARTITION_ID}"

# Track whether backups have been created
BACKUPS_CREATED=false

# Cleanup function to restore backups on failure
cleanup_on_failure() {
        local exit_code=$?

        if [ $exit_code -ne 0 ] && [ "$BACKUPS_CREATED" = true ]; then
                echo ""
                echo "============================================="
                echo "=== ERROR DETECTED - RESTORING BACKUPS ==="
                echo "============================================="
                echo "An error occurred during recovery. Restoring original snapshots from backup..."
                echo "Backup location: ${BACKUP_BASE_PATH}"
                echo ""

                for broker_id in $PARTITION_BROKER_IDS; do
                        BROKER_PATH="/mnt/broker-${broker_id}/raft-partition/partitions/${PARTITION_ID}"
                        BROKER_BACKUP_PATH="${BACKUP_BASE_PATH}/broker-${broker_id}"

                        if [ -d "${BROKER_BACKUP_PATH}/snapshots" ]; then
                                echo "  - Restoring Broker ${broker_id} snapshots from backup..."

                                # Remove all current snapshots (potentially corrupted)
                                echo "    Removing corrupted snapshots..."
                                rm -rf "${BROKER_PATH}/snapshots/"*

                                # Restore from backup
                                echo "    Restoring from backup..."
                                cp -r "${BROKER_BACKUP_PATH}/snapshots/"* "${BROKER_PATH}/snapshots/"

                                # Verify restoration
                                RESTORED_COUNT=$(ls -1 "${BROKER_PATH}/snapshots/" 2>/dev/null | grep -v ".checksum" | wc -l)
                                echo "    Broker ${broker_id} restored (${RESTORED_COUNT} snapshots)"
                        else
                                echo "  - WARNING: No backup found for Broker ${broker_id} at ${BROKER_BACKUP_PATH}"
                        fi
                done

                echo ""
                echo "Original snapshots have been restored from backup."
                echo "Backup directory preserved at: ${BACKUP_BASE_PATH}"
                echo "============================================="
                echo ""
        fi
}

# Set trap to cleanup on any error
trap cleanup_on_failure EXIT

echo "============================================="
echo "=== Key Recovery Job Starting ==="
echo "============================================="
echo "Partition ID:       ${PARTITION_ID}"
echo "Leader Broker:      ${LEADER_BROKER}"
echo "Partition Brokers:  ${PARTITION_BROKER_IDS}"
echo "New Key:            ${NEW_KEY}"
if [ -n "${NEW_MAX_KEY}" ]; then
        echo "New Max Key:        ${NEW_MAX_KEY}"
else
        echo "New Max Key:        <not set>"
fi
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
        # Auto-detect latest snapshot (exclude .checksum files)
        SELECTED_SNAPSHOT=$(ls -t ${LEADER_PATH}/snapshots/ | grep -v ".checksum" | head -1)
        echo "Auto-detected latest snapshot: ${SELECTED_SNAPSHOT}"
fi

# Validate snapshot exists
if [ ! -d "${LEADER_PATH}/snapshots/${SELECTED_SNAPSHOT}" ]; then
        echo "ERROR: Snapshot directory does not exist: ${LEADER_PATH}/snapshots/${SELECTED_SNAPSHOT}"
        exit 1
fi

echo "Snapshot details:"
ls -la "${LEADER_PATH}/snapshots/${SELECTED_SNAPSHOT}"
echo ""

# Step 3: Create backups of all broker partition data to temporary location
echo "[3/5] Creating backups of partition data on all replicas..."
BACKUP_BASE_PATH="/tmp/recovery-backup-${PARTITION_ID}"
echo "Backup location: ${BACKUP_BASE_PATH}"
echo "Processing brokers: ${PARTITION_BROKER_IDS}"

rm -rf "${BACKUP_BASE_PATH}" 2>/dev/null || true
mkdir -p "${BACKUP_BASE_PATH}"

for broker_id in $PARTITION_BROKER_IDS; do
        BROKER_PATH="/mnt/broker-${broker_id}/raft-partition/partitions/${PARTITION_ID}"
        BROKER_BACKUP_PATH="${BACKUP_BASE_PATH}/broker-${broker_id}"

        if [ -d "${BROKER_PATH}/snapshots" ]; then
                echo "  - Backing up Broker ${broker_id} snapshots to ${BROKER_BACKUP_PATH}..."
                mkdir -p "${BROKER_BACKUP_PATH}"

                # Copy all snapshots to backup location
                cp -r "${BROKER_PATH}/snapshots" "${BROKER_BACKUP_PATH}/"

                # Verify backup was created
                if [ -d "${BROKER_BACKUP_PATH}/snapshots" ]; then
                        BACKUP_SIZE=$(du -sh "${BROKER_BACKUP_PATH}/snapshots" | cut -f1)
                        echo "    Backup created: ${BACKUP_SIZE}"
                else
                        echo "    ERROR: Backup failed for Broker ${broker_id}"
                        exit 1
                fi
        else
                echo "  - WARNING: No snapshots directory for Broker ${broker_id}"
        fi
done
echo "Backups created in: ${BACKUP_BASE_PATH}"
BACKUPS_CREATED=true
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

if [ -n "${NEW_MAX_KEY}" ]; then
        CDBG_CMD="${CDBG_CMD} --max-key ${NEW_MAX_KEY}"
fi

CDBG_CMD="${CDBG_CMD} --verbose"

echo "Command: cdbg state update-key"
echo "  --root ${LEADER_PATH}"
echo "  --runtime ${RUNTIME_PATH}"
echo "  --snapshot ${SELECTED_SNAPSHOT}"
echo "  --partition-id ${PARTITION_ID}"
echo "  --key ${NEW_KEY}"
if [ -n "${NEW_MAX_KEY}" ]; then
        echo "  --max-key ${NEW_MAX_KEY}"
fi
echo "  --verbose"
echo ""

eval "${CDBG_CMD}"

echo ""
echo "Key update completed successfully!"
echo "Updated snapshot location: ${LEADER_PATH}/snapshots/"
ls -la "${LEADER_PATH}/snapshots/"
echo ""

# Step 5: Copy updated partition data to replicas
echo "[5/5] Copying updated partition data to replicas..."

for broker_id in $PARTITION_BROKER_IDS; do
        if [ "$broker_id" != "$LEADER_BROKER" ]; then
                REPLICA_PATH="/mnt/broker-${broker_id}/raft-partition/partitions/${PARTITION_ID}"
                echo "  - Syncing to Broker ${broker_id}..."
                echo "    Source: ${LEADER_PATH}/snapshots/"
                echo "    Destination: ${REPLICA_PATH}/snapshots/"

                # Remove all existing snapshots
                echo "    Removing old snapshot data..."
                rm -rf "${REPLICA_PATH}/snapshots/"*

                # Copy new snapshots from leader
                echo "    Copying updated snapshots from leader..."
                cp -r "${LEADER_PATH}/snapshots/"* "${REPLICA_PATH}/snapshots/"

                # Verify copy
                REPLICA_SNAPSHOT_COUNT=$(ls -1 "${REPLICA_PATH}/snapshots/" 2>/dev/null | grep -v ".checksum" | wc -l)
                echo "    Broker ${broker_id} updated successfully (${REPLICA_SNAPSHOT_COUNT} snapshots)"
                echo ""
        fi
done

# Step 6: Clean up backup files
echo "[6/6] Cleaning up backup files..."
echo "Removing backup directory: ${BACKUP_BASE_PATH}"
rm -rf "${BACKUP_BASE_PATH}"
echo "Backups removed"
echo ""

# Mark backups as cleaned up (don't restore on exit)
BACKUPS_CREATED=false

echo "============================================="
echo "=== Recovery Complete ==="
echo "============================================="
echo "Partition ${PARTITION_ID} has been recovered with:"
echo "  - Key:     ${NEW_KEY}"
if [ -n "${NEW_MAX_KEY}" ]; then
        echo "  - Max Key: ${NEW_MAX_KEY}"
fi
echo "  - Snapshot: ${SELECTED_SNAPSHOT}"
echo ""
echo "All brokers have been updated with the new snapshot."
echo "You can now restart the cluster."
echo "============================================="
