# Camunda Key Recovery Procedure

Recovery procedure for fixing corrupted key values in Camunda partitions.

## Prerequisites

- Kubectl access to the cluster
- `jq` installed for JSON parsing
- **Cold backup of all broker data** (PVC snapshots or volume backups)
- Cluster must be shut down during recovery

## Required Information

Before starting, you need:
- **NAMESPACE**: Kubernetes namespace where Camunda is deployed
- **PARTITION_ID**: The partition ID to recover
- **NEW_KEY**: New key value to set
- **NEW_MAX_KEY**: New maximum key value (optional, but recommended)
- **LAST_LEADER_BROKER**: The broker ID that was leader for this partition
- **PARTITION_BROKER_IDS**: Space-separated list of broker IDs hosting this partition (e.g., "0 1 2")
- **CONTAINER_IMAGE**: The Camunda container image (e.g., "camunda/camunda:8.6.1")
- **SNAPSHOT_ID**: Specific snapshot to use (optional - required only if multiple snapshots exist)

**Tip:** Use `../detect-config.sh <namespace>` to auto-detect most of these values (namespace, broker count, pod prefix, PVC prefix, container image, etc.)

## Recovery Steps

### 1. Identify Partition Leader and Replicas

Use the [helper script](../identify-partition-brokers.sh) to identify which brokers host the partition  before shutting down the broker.
In this example partition we run the script for partition 1:

```bash
export NAMESPACE="camunda"
export PARTITION_ID="1"
export BROKER_COUNT="3"
export STATEFULSET_NAME="camunda"  # optional, default: "camunda"
export POD_PREFIX="camunda"         # optional, default: "camunda"
export ACTUATOR_PORT="9600"         # optional, default: 9600

PARTITION_ID=1 ../identify-partition-brokers.sh
```

**Example output:**
```bash
LAST_LEADER_BROKER=0
PARTITION_BROKER_IDS="0 1 2"
=================================================

You can export these variables with the following, so it can be used by other scripts

export LAST_LEADER_BROKER=0
export PARTITION_BROKER_IDS="0 1 2"
```

(Your output will vary based on which brokers actually host the partition)


### 2. Shut Down the Cluster

Scale the StatefulSet to 0 replicas:

```bash
kubectl scale statefulset $STATEFULSET_NAME --replicas=0 -n $NAMESPACE

# Wait for all pods to terminate (adjust pod names based on your POD_PREFIX and BROKER_COUNT)
kubectl wait --for=delete pod/${POD_PREFIX}-0 pod/${POD_PREFIX}-1 pod/${POD_PREFIX}-2 -n $NAMESPACE --timeout=300s
```

Verify all pods are stopped:

```bash
kubectl get pods -n $NAMESPACE | grep $POD_PREFIX
# Should show no running pods
```

### 3. Take a Cold Backup

**CRITICAL**: Backup your data before proceeding!

- PVC snapshots
- Volume snapshots
- File-level backup of data directories

This is a **manual prerequisite** step. Follow your organization's backup procedures.

### 4. Generate Recovery Job YAML

Use the generator script to create the recovery Job YAML:

```bash
# Set required variables
export NAMESPACE="camunda"
export PARTITION_ID="1"
export LAST_LEADER_BROKER="$LEADER"              # From step 1
export PARTITION_BROKER_IDS="$REPLICAS"          # From step 1 (space-separated)
export NEW_KEY="2251800000000000"
export NEW_MAX_KEY="2251900000000000"            # Optional
export CONTAINER_IMAGE="camunda/camunda:8.6.1"

# Optional variables (have defaults)
export STATEFULSET_NAME="camunda"                # default: "camunda"
export PVC_PREFIX="data-camunda"                 # default: "data-${STATEFULSET_NAME}"
export CONTAINER_USER_ID="1000"                  # default: 1000
export CONTAINER_GROUP_ID="1001"                 # default: 1001
export CONTAINER_FS_GROUP="1001"                 # default: 1001

# Generate the YAML
./generate-recovery-job.sh
# Output: generated/recovery-job-partition-1-20251215-143022.yaml
```

#### Calculating Key Values

Camunda keys are: `((long) partitionId << 51) + key`

For partition 1:
- Base: `1 << 51 = 2251799813685248`
- Range: `2251799813685248` to `4503599627370495`

**Example values:**
- `NEW_KEY`: `2251800000000000` (high safe value)
- `NEW_MAX_KEY`: `2251900000000000` (higher than NEW_KEY with growth room)

### 5. Review the Generated YAML

```bash
cat generated/recovery-job-partition-1-*.yaml
```

The Job will:
1. Backup existing snapshots to `${BROKER_PATH}/snapshots-backup` on each broker's PVC
2. Run `cdbg state update-key` on the leader's snapshot
3. Copy updated snapshot to all replica brokers
4. Delete the original snapshot (cdbg creates a new one)
5. Preserve backups for manual cleanup after verification

**Important:**
- Backups are stored on each broker's PVC at `snapshots-backup/`, not in ephemeral `/tmp`
- Backups are NOT automatically deleted - you must clean them up manually after verifying recovery (see Step 11)

### 6. Apply the Recovery Job
**NOTE**: if you recreate the job more than once, you need to first delete the previous versions (see [here](#8-clean-up-recovery-job))


```bash
kubectl apply -f generated/recovery-job-partition-1-*.yaml
```

Monitor the job:

```bash
kubectl logs -f job/key-recovery-job -n $NAMESPACE
```

Wait for completion:

```bash
kubectl wait --for=condition=complete --timeout=600s job/key-recovery-job -n $NAMESPACE
```

### 7. Verify Job Success

Check job status:

```bash
kubectl get job key-recovery-job -n $NAMESPACE
# Should show: COMPLETIONS 1/1
```

If failed, check logs:

```bash
kubectl logs job/key-recovery-job -n $NAMESPACE
```

### 8. Clean Up Recovery Job

```bash
kubectl delete job key-recovery-job -n $NAMESPACE
```

### 9. Restart the Cluster

Scale back to original replica count:

```bash
# Set BROKER_COUNT to your cluster's broker count (e.g., 3, 5, etc.)
kubectl scale statefulset $STATEFULSET_NAME --replicas=$BROKER_COUNT -n $NAMESPACE

# Wait for all pods to be ready (adjust based on your BROKER_COUNT)
for i in $(seq 0 $((BROKER_COUNT - 1))); do
  kubectl wait --for=condition=ready pod/${POD_PREFIX}-${i} -n $NAMESPACE --timeout=300s
done
```

### 10. Verify Recovery

Monitor partition health using Grafana or check broker logs:

**Option A: Use Grafana (Recommended)**
- Navigate to your Camunda monitoring dashboard
- Check partition health metrics for partition `$PARTITION_ID`
- Verify all brokers show the partition as HEALTHY
- Verify one broker is LEADER and others are FOLLOWER

**Option B: Check Actuator Endpoint**
```bash
# Check partition health on a specific broker
kubectl port-forward -n $NAMESPACE ${POD_PREFIX}-0 9600:9600 &
sleep 2
curl -s http://localhost:9600/actuator/partitions | jq ".\"$PARTITION_ID\""
# Kill the port-forward when done: kill %1
```

**Option C: Check Broker Logs**
```bash
# Check for errors in broker logs
kubectl logs ${POD_PREFIX}-0 -n $NAMESPACE --tail=100 | grep -i "partition.*$PARTITION_ID"
```

Expected outcome:
- Partition shows HEALTHY status on all brokers
- One broker is LEADER, others are FOLLOWER
- No errors in logs related to the partition

### 11. Clean Up Backups (Optional)

After verifying successful recovery, you can remove the backups from each broker's PVC:

```bash
# For each broker that hosted the partition, exec into the pod and remove backup
for broker_id in $PARTITION_BROKER_IDS; do
  echo "Removing backup from broker $broker_id..."
  kubectl exec -n $NAMESPACE ${POD_PREFIX}-${broker_id} -- \
    rm -rf /usr/local/zeebe/data/raft-partition/partitions/${PARTITION_ID}/snapshots-backup
done
```

**Note:** Adjust the path `/usr/local/zeebe/data` if your Camunda installation uses a different data directory.

## What the Recovery Script Does

The `recovery-script.sh` (embedded in the generated Job YAML) performs:

1. **Validates** partition data exists
2. **Checks for existing backups** - fails if `snapshots-backup` directories already exist (prevents accidental overwrites)
3. **Creates backups** of snapshots to `${BROKER_PATH}/snapshots-backup/` on each broker's PVC
4. **Updates keys** using `cdbg state update-key` on leader's snapshot
5. **Deletes original snapshot** and its `.checksum` file (cdbg creates a new snapshot)
6. **Copies updated snapshot** to all replica brokers
7. **Preserves backups** for manual cleanup after verification (NOT automatically deleted)

## Backup Safety

The recovery script will **fail** if `snapshots-backup` directories already exist from a previous run. This prevents accidental data loss.

**Important:** Backups are **never** automatically deleted by the recovery script. After successful recovery:
1. Verify the cluster is healthy (Step 10)
2. Manually clean up backups (Step 11)

To retry recovery after a failure:
1. Review logs of the failed job
2. Manually inspect/remove old backups if safe to do so
3. Re-run the recovery job

Backups are stored on **PVCs** (not ephemeral `/tmp`), so they survive pod restarts and remain available for investigation or restoration.

## Troubleshooting

### Job Fails: "Multiple snapshots found, but SNAPSHOT_ID not specified"

**Cause:** The partition has multiple snapshots and the script cannot automatically choose which one to use.

**Solution:** Specify which snapshot to use by setting the `SNAPSHOT_ID` environment variable:

1. Check the job logs to see available snapshots with their sizes and timestamps
2. Regenerate the Job YAML with the `SNAPSHOT_ID` variable:

```bash
export SNAPSHOT_ID="<snapshot-name>"  # e.g., 79292-1-963202-962181-0-c52549fd
./generate-recovery-job.sh
```

3. Or manually edit the generated Job YAML and add the environment variable:

```yaml
env:
- name: SNAPSHOT_ID
  value: "79292-1-963202-962181-0-c52549fd"  # Use the snapshot name from error message
```

**Tip:** Choose the most recent snapshot unless you have a specific reason to use an older one.

### Job Fails: "Backup directories already exist"

**Cause:** Previous recovery attempt left backups on PVCs.

**Solution:** Review the backups and manually remove if appropriate:
```bash
# Check what's in the backup (replace broker_id with actual broker, e.g., 0, 1, 2)
kubectl exec -n $NAMESPACE ${POD_PREFIX}-<broker_id> -- \
  ls -la /usr/local/zeebe/data/raft-partition/partitions/${PARTITION_ID}/snapshots-backup

# Remove if safe
kubectl exec -n $NAMESPACE ${POD_PREFIX}-<broker_id> -- \
  rm -rf /usr/local/zeebe/data/raft-partition/partitions/${PARTITION_ID}/snapshots-backup
```

### Job Fails: "Snapshot directory does not exist"

**Cause:** No snapshots found for the partition.

**Solution:** Verify partition data exists and partition ID is correct.

### Brokers Don't Start After Recovery

**Cause:** Snapshot data may be corrupted.

**Solution:**
1. Check broker logs: `kubectl logs ${POD_PREFIX}-0 -n $NAMESPACE`
2. Restore from cold backup (Step 3)
3. Retry recovery with different key values

### Partition Shows UNHEALTHY

**Cause:** Cluster needs time to stabilize.

**Solution:**
1. Wait 1-2 minutes
2. Check logs for specific errors
3. Verify leader election occurred (one LEADER, others FOLLOWER)

## Files

- **`generate-recovery-job.sh`** - Generates recovery Job YAML with inline recovery script
- **`recovery-script.sh`** - Recovery operations script (embedded in generated Job YAML)


## Development
The file `test-yaml-equivalence.sh` can be used to test the generation of yaml files comparing the two methods (`bash` and `yq`). This is to ensure that the bash method produces the same result as `yq` to be used when `yq` is not installed in the system.

