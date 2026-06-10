# Camunda Exporter Incident-Position Reset Procedure

Recovery procedure for resetting an exporter's `lastIncidentUpdatePosition` in the `EXPORTER`
column family while preserving its `exporterPosition`.

You want to use this procedure when the incident-update cursor ends up ahead of the exported log
position (e.g. after a faulty backup/restore), which makes `IncidentUpdateTask` silently skip
pending incident updates so incidents stay outdated in Elasticsearch/OpenSearch.

Note that this operation requires the cluster to be shut down while the reset is performed.

> [!IMPORTANT]
> Cold backups are required from all the brokers

## How this differs from the key-recovery procedure

The cursor lives in **every replica's own snapshot** and is **not** part of the raft journal, so:

- The leader/follower role is irrelevant — **all replicas of every affected partition** must be
  patched, each on its **own** latest snapshot.
- The patched snapshot is **never copied between brokers**: the partition folder also contains the
  per-replica raft journal and metadata.
- The generator therefore creates **one Job per broker**, each mounting only that broker's PVC and
  patching all partition replicas found on it. This also avoids mounting several `ReadWriteOnce`
  (and possibly zonal) PVCs into a single pod.

## Prerequisites

- `bash` and common `unix` commands
- Kubectl access to the cluster
- **Cold backup of all broker data** (PVC snapshots or volume backups)
- Cluster must be shut down during the reset

## Required Information

Before starting, you need:

- **NAMESPACE**: Kubernetes namespace where Camunda is deployed
- **BROKER_IDS**: Space-separated list of broker IDs to patch (usually all brokers, e.g., "0 1 2")
- **PARTITION_POSITIONS**: Space-separated `partitionId:position` tuples giving the new
  `lastIncidentUpdatePosition` **per partition**, e.g. `"1:13417758 3:-1"`. Each partition has its
  own log positions, so the position is set per partition. Use `-1` for a partition to reprocess all
  its incidents from the start (idempotent, safest); use an explicit position only when a full
  reprocess is too costly. The same value is applied to every broker's Job; a partition not hosted
  on a given broker is skipped with a log line, not treated as an error.
- **CONTAINER_IMAGE**: The Camunda container image (e.g., "camunda/camunda:8.8.1")
- **EXPORTER_ID**: Id the exporter is configured under in `zeebe.broker.exporters`
  (optional, default: `camundaexporter`)
- **IMAGE_PULL_SECRET**: `imagePullSecrets` name for pulling the image from a private registry
  (optional, default: none)

> [!NOTE]
> You can use [../detect-config.sh](../detect-config.sh) to auto-detect `NAMESPACE`,
> `STATEFULSET_NAME`, `PVC_PREFIX`, `BROKER_COUNT` and `CONTAINER_IMAGE`.

### Worked example — which replicas get patched

With 3 partitions, 3 nodes and replication factor 2, replicas are spread round-robin, so each node
holds two partition replicas and there are 6 replicas to patch:

| Partition |   Replicas   |   | Node  | Hosts  |
|-----------|--------------|---|-------|--------|
| P1        | node0, node1 |   | node0 | P1, P3 |
| P2        | node1, node2 |   | node1 | P1, P2 |
| P3        | node2, node0 |   | node2 | P2, P3 |

Running the generated Jobs with `BROKER_IDS="0 1 2"` and `PARTITION_POSITIONS="1:<p1> 2:<p2> 3:<p3>"`
covers all 6 replicas: the Job for node0 patches P1+P3, node1 patches P1+P2, node2 patches P2+P3.
Every Job gets the full tuple list but patches only the partitions present on its own PVC and
**skips the rest with a log line** (e.g. node0 skips P2). If a broker happens to host none of the
requested partitions, its Job completes with a warning, having patched nothing — so confirm each
targeted partition shows as patched in at least one Job's summary before restarting the cluster.

## Reset Steps

### 1. Detect cluster configuration

Run `../detect-config.sh <NAMESPACE>` and export the variables it prints.

### 2. Shut Down the Cluster

```bash
kubectl scale statefulset $STATEFULSET_NAME --replicas=0 -n $NAMESPACE

# Wait for all pods to terminate (adjust pod names based on your POD_PREFIX and BROKER_COUNT)
kubectl wait --for=delete pod/${POD_PREFIX}-0 pod/${POD_PREFIX}-1 pod/${POD_PREFIX}-2 -n $NAMESPACE --timeout=300s
```

### 3. Take a Cold Backup

**CRITICAL**: Backup your data before proceeding (PVC snapshots, volume snapshots, or file-level
backups). This is a **manual prerequisite** step - follow your organization's backup procedures.

### 4. Generate the Reset Job YAML

```bash
export NAMESPACE="camunda"
export BROKER_IDS="0 1 2"
export PARTITION_POSITIONS="1:13417758 3:-1"
export CONTAINER_IMAGE="camunda/camunda:8.8.1"

# Optional (have defaults)
export EXPORTER_ID="camundaexporter"            # default: "camundaexporter"
export IMAGE_PULL_SECRET="harbor-registry"      # default: none (set for private registries)
export STATEFULSET_NAME="camunda"               # default: "camunda"
export PVC_PREFIX="data-camunda"                # default: "data-${STATEFULSET_NAME}"

./generate-reset-job.sh
# Output: generated/reset-incident-position-jobs-20260610-143022.yaml
```

The file contains **one Job per broker**. Each Job will, for every requested partition present on
that broker's PVC (skipping the ones it does not host):

1. Backup the existing snapshots to `partitions/<id>/snapshots-backup` on the PVC
2. Run `cdbg state reset-incident-position` on the partition's snapshot
3. Delete the original snapshot (cdbg creates a new checksum-valid one)
4. Preserve the backups for manual cleanup after verification

### 5. Apply and Monitor the Jobs

```bash
kubectl apply -f generated/reset-incident-position-jobs-*.yaml

# Monitor (one job per broker)
for broker_id in $BROKER_IDS; do
  kubectl logs -f job/incident-position-reset-broker-${broker_id} -n $NAMESPACE
done

# Wait for completion
for broker_id in $BROKER_IDS; do
  kubectl wait --for=condition=complete --timeout=600s job/incident-position-reset-broker-${broker_id} -n $NAMESPACE
done
```

> [!IMPORTANT]
> All Jobs must complete successfully before restarting the cluster. If a Job fails, check its
> logs, fix the cause and re-run it (delete the failed Job first; the script refuses to run if a
> `snapshots-backup` directory from a previous attempt still exists). Because the cluster is scaled
> down during the reset, there is **no broker pod to `kubectl exec` into** at that point — removing
> or renaming a leftover backup requires a throwaway pod that mounts the broker's PVC. The failing
> Job's log prints the exact PVC claim, namespace and `mv` command to use.

If a partition has multiple snapshots the Job fails and lists them; re-run that broker's Job with
`PARTITION_POSITIONS="<partition>:<position>"` and `SNAPSHOT_ID=<snapshot-name>` set.

### 6. Clean Up the Jobs

```bash
for broker_id in $BROKER_IDS; do
  kubectl delete job incident-position-reset-broker-${broker_id} -n $NAMESPACE
done
```

### 7. Restart the Cluster

```bash
kubectl scale statefulset $STATEFULSET_NAME --replicas=$BROKER_COUNT -n $NAMESPACE

for i in $(seq 0 $((BROKER_COUNT - 1))); do
  kubectl wait --for=condition=ready pod/${POD_PREFIX}-${i} -n $NAMESPACE --timeout=300s
done
```

### 8. Verify Recovery

- Confirm each partition elects a healthy leader (Grafana, `/actuator/partitions`, or broker logs).
- Verify the incident updates are draining into Elasticsearch/OpenSearch: previously stuck
  incidents should transition to their correct state.

### 9. Clean Up Snapshot Backups (After Verification)

```bash
# For each broker, remove the per-partition backups (data is mounted at /usr/local/camunda for 8.8+)
for broker_id in $BROKER_IDS; do
  kubectl exec -n $NAMESPACE ${POD_PREFIX}-${broker_id} -- \
    sh -c 'rm -rf /usr/local/camunda/data/raft-partition/partitions/*/snapshots-backup'
done
```

## Running the command manually

Outside Kubernetes (or for a single replica), run the CLI directly on the stopped broker's data
directory:

```bash
debug-cli state reset-incident-position \
  -r <data-dir>/raft-partition/partitions/<partitionId> \
  --snapshot <snapshotId> \
  --position -1 \
  --runtime /tmp/reset-runtime
```

The snapshot id is the directory name under `.../partitions/<partitionId>/snapshots/`. Use a fresh
empty `--runtime` directory per run, delete the source snapshot (and its `.checksum` file)
afterwards, and repeat on every replica of every affected partition before restarting the brokers.

## Files

- **`generate-reset-job.sh`** - Generates one reset Job YAML per broker with the inline reset script
- **`reset-script.sh`** - Reset operations script (embedded in the generated Job YAML)

