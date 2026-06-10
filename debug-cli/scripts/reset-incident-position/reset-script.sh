#!/bin/bash
#
# Camunda Exporter Incident-Position Reset Script
#
# Resets an exporter's lastIncidentUpdatePosition on the partition replicas hosted
# by ONE broker by:
#   1. Backing up the existing snapshots of each partition
#   2. Running cdbg state reset-incident-position on each partition's snapshot
#   3. Deleting the original snapshot (cdbg creates a new one)
#
# Unlike the key-recovery procedure, the patched snapshot is NOT copied between
# brokers: the EXPORTER column family is replica-local state, so every broker
# patches its own snapshots. Run one Job per broker (the generator does this).
#
# The same PARTITION_POSITIONS is applied to every broker's Job. A partition that
# is not hosted on this broker (replicationFactor < clusterSize) is skipped with a
# log line, not treated as an error.
#
# This script is designed to run inside a Kubernetes Job with the broker's PVC
# mounted at /mnt/broker-${BROKER_ID}.
#
# Required Environment Variables:
#   BROKER_ID           - Broker whose PVC is mounted (0, 1, 2, ...)
#   PARTITION_POSITIONS - Space-separated partitionId:position tuples giving the new
#                         lastIncidentUpdatePosition per partition, e.g. "1:13417758 3:-1".
#                         Position -1 reprocesses all incidents for that partition.
#
# Optional Environment Variables:
#   EXPORTER_ID    - Exporter id to patch (default: camundaexporter)
#   SNAPSHOT_ID    - Specific snapshot to use; only valid when PARTITION_POSITIONS has a
#                    single tuple (default: auto-detect, fails if multiple snapshots)
#   NAMESPACE      - Namespace (only used to print an accurate cleanup hint; injected
#                    by the generator)
#   PVC_NAME       - This broker's PVC claim name (only used to print an accurate cleanup
#                    hint; injected by the generator)
#

set -euo pipefail

: "${BROKER_ID:?BROKER_ID must be set}"
: "${PARTITION_POSITIONS:?PARTITION_POSITIONS must be set (e.g. \"1:13417758 3:-1\")}"
EXPORTER_ID="${EXPORTER_ID:-camundaexporter}"
SNAPSHOT_ID="${SNAPSHOT_ID:-}" # Optional override

# Only used to print an accurate cleanup hint if a previous run left a backup.
# Injected by the generator; fall back to placeholders for standalone runs.
CLEANUP_NAMESPACE="${NAMESPACE:-<namespace>}"
CLEANUP_PVC="${PVC_NAME:-<pvc-name-for-broker-${BROKER_ID}>}"

BROKER_MOUNT="/mnt/broker-${BROKER_ID}"
PARTITIONS_PATH="${BROKER_MOUNT}/raft-partition/partitions"

# Validate the PARTITION_POSITIONS tuples up front, before touching any data.
for tuple in $PARTITION_POSITIONS; do
        if [[ ! "$tuple" =~ ^[^:]+:[^:]+$ ]]; then
                echo "ERROR: Invalid PARTITION_POSITIONS entry '${tuple}'"
                echo "Expected space-separated partitionId:position tuples, e.g. \"1:13417758 3:-1\""
                exit 1
        fi
done

echo "============================================="
echo "=== Incident-Position Reset Job Starting ==="
echo "============================================="
echo "Broker ID:            ${BROKER_ID}"
echo "Exporter ID:          ${EXPORTER_ID}"
echo "Partition positions:  ${PARTITION_POSITIONS}"
echo "Snapshot:             ${SNAPSHOT_ID:-<auto-detect latest>}"
echo "============================================="
echo ""

if [ ! -d "${PARTITIONS_PATH}" ]; then
        echo "ERROR: Partitions path does not exist: ${PARTITIONS_PATH}"
        exit 1
fi

if [ -n "${SNAPSHOT_ID}" ] && [ "$(echo "${PARTITION_POSITIONS}" | wc -w)" -gt 1 ]; then
        echo "ERROR: SNAPSHOT_ID can only be used when PARTITION_POSITIONS has a single tuple"
        exit 1
fi

# Check for leftovers of a previous failed run before touching anything.
for tuple in $PARTITION_POSITIONS; do
        partition_id="${tuple%%:*}"
        BACKUP_PATH="${PARTITIONS_PATH}/${partition_id}/snapshots-backup"
        if [ -d "${BACKUP_PATH}" ]; then
                PVC_RELATIVE_BACKUP="raft-partition/partitions/${partition_id}/snapshots-backup"
                echo "ERROR: Existing backup found at ${BACKUP_PATH}"
                echo ""
                echo "A previous reset attempt for partition ${partition_id} appears to have failed."
                echo "The cluster is scaled down, so there is NO broker pod to exec into. Inspect and"
                echo "rename (do not delete) the leftover from a throwaway pod that mounts this broker's"
                echo "PVC, then re-run this Job. For example, with the PVC mounted at /mnt:"
                echo ""
                echo "  PVC claim:  ${CLEANUP_PVC}"
                echo "  Namespace:  ${CLEANUP_NAMESPACE}"
                echo "  Command:    mv /mnt/${PVC_RELATIVE_BACKUP} /mnt/${PVC_RELATIVE_BACKUP}-old-<timestamp>"
                echo ""
                echo "Only proceed once you have confirmed the leftover is from a failed attempt."
                exit 1
        fi
done

PATCHED_SUMMARY=()
SKIPPED_SUMMARY=()

for tuple in $PARTITION_POSITIONS; do
        partition_id="${tuple%%:*}"
        new_position="${tuple##*:}"
        PARTITION_PATH="${PARTITIONS_PATH}/${partition_id}"
        SNAPSHOTS_PATH="${PARTITION_PATH}/snapshots"
        RUNTIME_PATH="/tmp/reset-runtime-${partition_id}"

        echo "============================================="
        echo "=== Partition ${partition_id} (-> ${new_position}) ==="
        echo "============================================="

        # Skip partitions this broker does not host (replicationFactor < clusterSize).
        if [ ! -d "${PARTITION_PATH}" ]; then
                echo "Partition ${partition_id} is not hosted on broker ${BROKER_ID}; skipping."
                echo ""
                SKIPPED_SUMMARY+=("${partition_id} (not hosted on this broker)")
                continue
        fi

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
                        echo "Re-run for this single partition with PARTITION_POSITIONS=\"${partition_id}:${new_position}\" and SNAPSHOT_ID=<snapshot-name>"
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
        CDBG_CMD="${CDBG_CMD} --position ${new_position}"
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

        PATCHED_SUMMARY+=("${partition_id} (-> ${new_position})")
done

echo "============================================="
echo "=== Reset Summary — broker ${BROKER_ID} ==="
echo "============================================="
if [ ${#PATCHED_SUMMARY[@]} -gt 0 ]; then
        echo "Patched exporter '${EXPORTER_ID}' on:"
        for entry in "${PATCHED_SUMMARY[@]}"; do
                echo "  - partition ${entry}"
        done
else
        echo "Patched: none"
fi
if [ ${#SKIPPED_SUMMARY[@]} -gt 0 ]; then
        echo "Skipped:"
        for entry in "${SKIPPED_SUMMARY[@]}"; do
                echo "  - partition ${entry}"
        done
fi
echo "============================================="

if [ ${#PATCHED_SUMMARY[@]} -eq 0 ]; then
        echo ""
        echo "WARNING: broker ${BROKER_ID} hosts none of the requested partitions"
        echo "(${PARTITION_POSITIONS}). Patched 0 partitions on this broker."
        echo "This is expected when replicationFactor < clusterSize and this broker is"
        echo "simply not a replica of those partitions. If you DID expect partitions here,"
        echo "re-check PARTITION_POSITIONS and the broker -> partition placement."
        echo ""
fi

echo ""
echo "IMPORTANT: this fix is per-replica. Make sure the Jobs for ALL brokers complete"
echo "before restarting the cluster, and confirm every targeted partition was patched"
echo "on at least one broker (each Job's summary above shows what it patched)."
echo ""
if [ ${#PATCHED_SUMMARY[@]} -gt 0 ]; then
        echo "Backups are preserved in each patched partition's 'snapshots-backup' directory."
        echo "After verifying the recovery (cluster restarted), clean them up manually:"
        for entry in "${PATCHED_SUMMARY[@]}"; do
                pid="${entry%% *}"
                echo "  kubectl exec \${POD_PREFIX}-${BROKER_ID} -n \${NAMESPACE} -- rm -rf /usr/local/camunda/data/raft-partition/partitions/${pid}/snapshots-backup"
        done
fi
echo "============================================="
