# Camunda Key Recovery Procedure

Recovery procedure for fixing invalid key values in a partition.

You want to use this procedure if for some reason the key of a partition jumped to an extremely high value which might eventually overflow into another partition key space.

Note that this operation requires the cluster to be shut down while the recovery is performed.

> [!IMPORTANT]
> Cold backups are required from all the brokers

## Prerequisites

- `bash` and common `unix` commands
- Kubectl access to the cluster
- `jq` installed for JSON parsing
- **Cold backup of all broker data** (PVC snapshots or volume backups)
- Cluster must be shut down during recovery

## Steps summary

The recovery procedure consists in running a K8S job that performs the actual change in the partition state.
The recovery job yaml should be generated using [generate-recovery-job](./generate-recovery-job.sh) script.

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

> [!NOTE]
> You can use [../detect-config.sh](../detect-config.sh) && [identify-partition-brokers](../identify-partition-brokers.sh) to auto-detect all required parameters except for `NEW_KEY` and `PARTITION_ID`

## Recovery Steps

### 1. Detect cluster configuration

Run `../detect-config.sh <NAMESPACE>` which will output some environment variable to export:

```bash
/detect-config.sh cs-key-recovery
==========================================
Detecting configuration for namespace: cs-key-recovery
==========================================

[1/6] Finding StatefulSet...
  StatefulSet: camunda

[2/6] Getting broker count...
  Broker Count: 3

[3/6] Detecting pod prefix...
  Pod Prefix: camunda

[4/6] Verifying pods...
  Found 3 pods matching prefix 'camunda'

[5/6] Detecting PVC prefix...
  PVC Prefix: data-camunda

[6/6] Verifying PVCs...
  ✓ data-camunda-0 exists
  ✓ data-camunda-1 exists
  ✓ data-camunda-2 exists

[7/8] Detecting actuator port...
  Actuator Port: 9600 (detected or default)

[8/8] Detecting container image...
  Container Image: gcr.io/zeebe-io/zeebe:cs-key-recovery-fa5e126a

==========================================
Configuration Summary
==========================================

Namespace:         cs-key-recovery
StatefulSet:       camunda
Pod Prefix:        camunda
PVC Prefix:        data-camunda
Broker Count:      3
Actuator Port:     9600
Container Image:   gcr.io/zeebe-io/zeebe:cs-key-recovery-fa5e126a

==========================================
Environment Variables
==========================================

Copy and paste these to configure the recovery scripts:

export NAMESPACE="cs-key-recovery"
export STATEFULSET_NAME="camunda"
export POD_PREFIX="camunda"
export PVC_PREFIX="data-camunda"
export BROKER_COUNT="3"
export ACTUATOR_PORT="9600"
export CONTAINER_IMAGE="gcr.io/zeebe-io/zeebe:cs-key-recovery-fa5e126a"

Then run:
  ./identify-partition-brokers.sh  # Identify partition leader and replicas
  ./generate-recovery-job.sh       # Generate recovery Job YAML

```

### 2. Identify Partition Leader and Replicas

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

### 3. Shut Down the Cluster

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

### 4. Take a Cold Backup

**CRITICAL**: Backup your data before proceeding!

- PVC snapshots
- Volume snapshots
- File-level backup of data directories

This is a **manual prerequisite** step. Follow your organization's backup procedures.

### 5. Generate Recovery Job YAML

Use the generator script to create the recovery Job YAML:

If you run the scripts in steps [1](#1-detect-cluster-configuration) and [2](#2-identify-partition-leader-and-replicas) then you only need to set the following variables.:

```bash
export PARTITION_ID="1"
export NEW_KEY="2251800000000000"
export NEW_MAX_KEY="2251900000000000"            # Optional
```

If you didn't run the scripts in the previous steps, you need to export all the required environment variables and verify that the optional environment variables match your cluster:

```bash

# Set required variables
export NAMESPACE="camunda"
export PARTITION_ID="1"
export LAST_LEADER_BROKER="0"                    # From step 2
export PARTITION_BROKER_IDS="0 1 2"              # From step 2 (space-separated)
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

Choosing the right `NEW_KEY` and `NEW_MAX_KEY` is not straightforward as it's important to select a new key range `[NEW_KEY, NEW_MAX_KEY)` that does not overlap with existing records.

> [!IMPORTANT]
> If there's overlap with existing records there is risk of data corruption!

In camunda, the key for a partition `PARTITION_ID` must be in the range:

```bash
echo "[$(($PARTITION_ID << 51)), $((($PARTITION_ID + 1) << 51)))"
# returns for PARTITION_ID=2
# [4503599627370496, 6755399441055744)
```

If for some reason the key for partition 2 jumped from 4503599627370496 to 6755399441000000, then you should set the variables to:
- `NEW_KEY`: higher than the key before the jump, such as `4503599900000000`
- `NEW_MAX_KEY`: lower than the key it jumped to, such as `6755399000000000`

It's ok to leave a lot of room between the keys for extra safety, the key space is quite big.

### 6. Review the Generated YAML

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
- Backups are NOT automatically deleted - you must clean them up manually after verifying recovery (see Step 12)

### 7. Apply the Recovery Job

**NOTE**: if you recreate the job more than once, you need to first delete the previous versions (see [here](#9-clean-up-recovery-job))

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

### 8. Verify Job Success

Check job status:

```bash
kubectl get job key-recovery-job -n $NAMESPACE
# Should show: COMPLETIONS 1/1
```

If failed, check logs:

```bash
kubectl logs job/key-recovery-job -n $NAMESPACE
```

### 9. Clean Up Recovery Job

```bash
kubectl delete job key-recovery-job -n $NAMESPACE
```

### 10. Restart the Cluster

Scale back to original replica count:

```bash
# Set BROKER_COUNT to your cluster's broker count (e.g., 3, 5, etc.)
kubectl scale statefulset $STATEFULSET_NAME --replicas=$BROKER_COUNT -n $NAMESPACE

# Wait for all pods to be ready (adjust based on your BROKER_COUNT)
for i in $(seq 0 $((BROKER_COUNT - 1))); do
  kubectl wait --for=condition=ready pod/${POD_PREFIX}-${i} -n $NAMESPACE --timeout=300s
done
```

### 11. Verify Recovery

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
- One broker is LEADER, others are FOLLOWER for each partition
- No errors in logs related to the partition modified

### 12. Clean Up Snapshot Backups (After Verification)

After verifying successful recovery, you can remove the backups from each broker's PVC.
Depending on the camunda version it's mounted at:
- 8.8+: `/usr/local/camunda`
- < 8.8: `/usr/local/zeebe`

Set `MOUNT_POINT` variable accordingly:

```bash
# For Camunda 8.8+
export MOUNT_POINT="/usr/local/camunda"

# For Camunda < 8.8
export MOUNT_POINT="/usr/local/zeebe"
```

Then remove the backups:

```bash
# For each broker that hosted the partition, exec into the pod and remove backup
for broker_id in $PARTITION_BROKER_IDS; do
  echo "Removing backup from broker $broker_id..."
  kubectl exec -n $NAMESPACE ${POD_PREFIX}-${broker_id} -- \
    rm -rf $MOUNT_POINT/data/raft-partition/partitions/${PARTITION_ID}/snapshots-backup
done
```

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
1. Verify the cluster is healthy (Step 11)
2. Manually clean up backups (Step 12)

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
# Set MOUNT_POINT based on your Camunda version
# For Camunda 8.8+:
export MOUNT_POINT="/usr/local/camunda"
# For Camunda < 8.8:
# export MOUNT_POINT="/usr/local/zeebe"

# Check what's in the backup (replace broker_id with actual broker, e.g., 0, 1, 2)
kubectl exec -n $NAMESPACE ${POD_PREFIX}-<broker_id> -- \
  ls -la $MOUNT_POINT/data/raft-partition/partitions/${PARTITION_ID}/snapshots-backup

# Remove if safe
kubectl exec -n $NAMESPACE ${POD_PREFIX}-<broker_id> -- \
  rm -rf $MOUNT_POINT/data/raft-partition/partitions/${PARTITION_ID}/snapshots-backup
```

### Job Fails: "Snapshot directory does not exist"

**Cause:** No snapshots found for the partition.

**Solution:** Verify partition data exists and partition ID is correct.

### Brokers Don't Start After Recovery

**Cause:** Snapshot data may be corrupted.

**Solution:**
1. Check broker logs: `kubectl logs ${POD_PREFIX}-0 -n $NAMESPACE`
2. Restore from cold backup (Step 4)
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

