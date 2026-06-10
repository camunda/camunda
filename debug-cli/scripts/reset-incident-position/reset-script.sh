#!/bin/bash
#
# Camunda Exporter Incident-Position Reset Script
#
# Resets an exporter's lastIncidentUpdatePosition on all partition replicas hosted
# by ONE broker by:
#   1. Backing up the existing snapshots of each partition
#   2. Running cdbg state reset-incident-position on each partition's snapshot
#   3. Deleting the original snapshot (cdbg creates a new one)
#
# Unlike the key-recovery procedure, the patched snapshot is NOT copied between
# brokers: the EXPORTER column family is replica-local state, so every broker
# patches its own snapshots. Run one Job per broker (the generator does this).
#
# This script is designed to run inside a Kubernetes Job with the broker's PVC
# mounted at /mnt/broker-${BROKER_ID}.
#
# Required Environment Variables:
#   BROKER_ID      - Broker whose PVC is mounted (0, 1, 2, ...)
#   NEW_POSITION   - New lastIncidentUpdatePosition (-1 reprocesses all incidents)
#
# Optional Environment Variables:
#   EXPORTER_ID    - Exporter id to patch (default: camundaexporter)
#   PARTITION_IDS  - Space-separated partition ids to patch (default: all partitions
#                    found on this broker's PVC)
#   SNAPSHOT_ID    - Specific snapshot to use; only valid when patching a single
#                    partition (default: auto-detect, fails if multiple snapshots)
#

set -euo pipefail

: "${BROKER_ID:?BROKER_ID must be set}"
: "${NEW_POSITION:?NEW_POSITION must be set}"
EXPORTER_ID="${EXPORTER_ID:-camundaexporter}"
PARTITION_IDS="${PARTITION_IDS:-}" # Optional, auto-detected when empty
SNAPSHOT_ID="${SNAPSHOT_ID:-}"     # Optional override

BROKER_MOUNT="/mnt/broker-${BROKER_ID}"
PARTITIONS_PATH="${BROKER_MOUNT}/raft-partition/partitions"

echo "============================================="
echo "=== Incident-Position Reset Job Starting ==="
echo "============================================="
echo "Broker ID:      ${BROKER_ID}"
echo "Exporter ID:    ${EXPORTER_ID}"
echo "New Position:   ${NEW_POSITION}"
echo "Partition IDs:  ${PARTITION_IDS:-<auto-detect all on this broker>}"
echo "Snapshot:       ${SNAPSHOT_ID:-<auto-detect latest>}"
echo "============================================="
echo ""

if [ ! -d "${PARTITIONS_PATH}" ]; then
        echo "ERROR: Partitions path does not exist: ${PARTITIONS_PATH}"
        exit 1
fi

# Auto-detect partitions hosted by this broker if not provided
if [ -z "${PARTITION_IDS}" ]; then
        PARTITION_IDS=$(find "${PARTITIONS_PATH}" -mindepth 1 -maxdepth 1 -type d -exec basename {} \; | sort -n | tr '\n' ' ')
        PARTITION_IDS="${PARTITION_IDS% }"
        if [ -z "${PARTITION_IDS}" ]; then
                echo "ERROR: No partitions found in ${PARTITIONS_PATH}"
                exit 1
        fi
        echo "Auto-detected partitions on broker ${BROKER_ID}: ${PARTITION_IDS}"
        echo ""
fi

if [ -n "${SNAPSHOT_ID}" ] && [ "$(echo "${PARTITION_IDS}" | wc -w)" -gt 1 ]; then
        echo "ERROR: SNAPSHOT_ID can only be used when patching a single partition"
        exit 1
fi

# Check for leftovers of a previous failed run before touching anything
for partition_id in $PARTITION_IDS; do
        BACKUP_PATH="${PARTITIONS_PATH}/${partition_id}/snapshots-backup"
        if [ -d "${BACKUP_PATH}" ]; then
                echo "ERROR: Existing backup found at ${BACKUP_PATH}"
                echo ""
                echo "This indicates a previous reset attempt may have failed."
                echo "Manually review and remove existing backups before retrying:"
                echo "  kubectl exec <pod> -- rm -rf ${BACKUP_PATH}"
                echo ""
                echo "Only remove these backups if you're certain they're no longer needed!"
                exit 1
        fi
done

for partition_id in $PARTITION_IDS; do
        PARTITION_PATH="${PARTITIONS_PATH}/${partition_id}"
        SNAPSHOTS_PATH="${PARTITION_PATH}/snapshots"
        RUNTIME_PATH="/tmp/reset-runtime-${partition_id}"

        echo "============================================="
        echo "=== Partition ${partition_id} ==="
        echo "============================================="

        # Step 1: Determine snapshot to use
        if [ -n "${SNAPSHOT_ID}" ]; then
                echo "Using snapshot from SNAPSHOT_ID env var: ${SNAPSHOT_ID}"
                SELECTED_SNAPSHOT="${SNAPSHOT_ID}"
        else
                snapshots=()
                for snap in "${SNAPSHOTS_PATH}/"*/; do
                        [ -d "$snap" ] || continue
                        [[ "$(basename "$snap")" == *.checksum ]] && continue
                        snapshots+=("$snap")
                done
                SNAPSHOT_COUNT=${#snapshots[@]}

                if [ "$SNAPSHOT_COUNT" -eq 0 ]; then
                        echo "ERROR: No snapshots found in ${SNAPSHOTS_PATH}"
                        exit 1
                elif [ "$SNAPSHOT_COUNT" -gt 1 ]; then
                        echo "ERROR: Multiple snapshots found for partition ${partition_id}, but SNAPSHOT_ID not specified"
                        echo ""
                        echo "Available snapshots:"
                        for snapshot in "${snapshots[@]}"; do
                                snapshot_name=$(basename "$snapshot")
                                SIZE=$(du -sh "$snapshot" 2>/dev/null | cut -f1)
                                MODIFIED=$(stat -c %y "$snapshot" 2>/dev/null | cut -d'.' -f1)
                                echo "  - ${snapshot_name} (${SIZE}, modified: ${MODIFIED})"
                        done
                        echo ""
                        echo "Re-run for this single partition with PARTITION_IDS=${partition_id} and SNAPSHOT_ID=<snapshot-name>"
                        exit 1
                else
                        SELECTED_SNAPSHOT=$(basename "${snapshots[0]}")
                        echo "Auto-detected snapshot: ${SELECTED_SNAPSHOT}"
                fi
        fi

        # Step 2: Backup the snapshots of this partition (on the PVC, not in /tmp)
        echo "Backing up snapshots to ${PARTITION_PATH}/snapshots-backup ..."
        mkdir -p "${PARTITION_PATH}/snapshots-backup"
        cp -r "${SNAPSHOTS_PATH}/"* "${PARTITION_PATH}/snapshots-backup/"
        sync
        echo "Backup created"

        # Step 3: Run cdbg to reset the incident position
        CDBG_CMD="/usr/local/camunda/bin/cdbg state reset-incident-position"
        CDBG_CMD="${CDBG_CMD} --root ${PARTITION_PATH}"
        CDBG_CMD="${CDBG_CMD} --runtime ${RUNTIME_PATH}"
        CDBG_CMD="${CDBG_CMD} --snapshot ${SELECTED_SNAPSHOT}"
        CDBG_CMD="${CDBG_CMD} --exporter-id ${EXPORTER_ID}"
        CDBG_CMD="${CDBG_CMD} --position ${NEW_POSITION}"
        CDBG_CMD="${CDBG_CMD} --verbose"

        echo "Running: ${CDBG_CMD}"
        echo ""
        eval "${CDBG_CMD}"
        echo ""

        # Step 4: Delete the original snapshot since cdbg created a new one
        echo "Removing original snapshot: ${SELECTED_SNAPSHOT}"
        rm -rf "${SNAPSHOTS_PATH:?}/${SELECTED_SNAPSHOT}"
        [ -f "${SNAPSHOTS_PATH}/${SELECTED_SNAPSHOT}.checksum" ] && rm -f "${SNAPSHOTS_PATH}/${SELECTED_SNAPSHOT}.checksum"
        sync
        echo "Original snapshot removed; remaining snapshots:"
        ls -la "${SNAPSHOTS_PATH}/"
        echo ""
done

echo "============================================="
echo "=== Reset Complete on broker ${BROKER_ID} ==="
echo "============================================="
echo "Patched exporter '${EXPORTER_ID}' to lastIncidentUpdatePosition=${NEW_POSITION}"
echo "on partitions: ${PARTITION_IDS}"
echo ""
echo "IMPORTANT: this fix is per-replica. Make sure the Jobs for ALL brokers"
echo "complete before restarting the cluster."
echo ""
echo "Backups are preserved in each partition's 'snapshots-backup' directory."
echo "After verifying the recovery, clean them up manually:"
for partition_id in $PARTITION_IDS; do
        echo "  kubectl exec \${POD_PREFIX}-${BROKER_ID} -n \${NAMESPACE} -- rm -rf /usr/local/camunda/data/raft-partition/partitions/${partition_id}/snapshots-backup"
done
echo "============================================="
