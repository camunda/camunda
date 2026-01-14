# Camunda Key Recovery Procedure

Recovery procedure for fixing invalid key values in a partition.

You want to use this procedure if for some reason the key of a partition jumped to an extremely high value which might eventually overflow into another partition key space.

Note that this operation requires the cluster to be shut down while the recovery is performed.

> [!IMPORTANT]
> Cold backups are required from all the brokers

## Prerequisites

- `bash` and common `unix` commands - Kubectl access to the cluster
- `curl`
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

#### Finding Key Jumps Using Elasticsearch

To identify where the key jump occurred within a partition, use the provided script to query Elasticsearch and analyze `processInstanceKey` values. The script automatically handles pagination for partitions with many process instances.

> [!NOTE]
> The provided script should work for most scenarios, but may need adjustments for specific Elasticsearch configurations or custom requirements. If needed, you can perform manual queries by examining the Elasticsearch queries used by the script.

**Run the Analysis Script:**

```bash
# Set required parameters and run the script
ES_URL="http://localhost:9200" PARTITION_ID=1 ./analyze-partition-keys.sh
```

The script will:
1. Query Elasticsearch for all unique processInstanceKeys in the partition (with automatic pagination)
2. Save results to `partition_${PARTITION_ID}_keys.txt`
3. Analyze the keys to detect jumps > 1 million
4. Display any jumps found with timestamps

**Optional Parameters:**

```bash
# Custom batch size (default: 1000, max: 10000)
ES_URL="http://localhost:9200" PARTITION_ID=1 BATCH_SIZE=5000 ./analyze-partition-keys.sh

# Resume from a specific key (useful if script was interrupted)
ES_URL="http://localhost:9200" PARTITION_ID=1 AFTER_KEY=2251799813999999 ./analyze-partition-keys.sh

# Skip download and only analyze existing file (useful if data already downloaded)
PARTITION_ID=1 SKIP_DOWNLOAD=true ./analyze-partition-keys.sh
```

**Use Cases:**

- **BATCH_SIZE**: Use a larger batch size (e.g., 5000-10000) for faster downloads when the partition has many process instances. Smaller batch sizes are useful for slow networks.

- **AFTER_KEY**: If the script is interrupted during download, you can resume by setting AFTER_KEY to the last processInstanceKey in the file. Check the last line with `tail -1 partition_${PARTITION_ID}_keys.txt | cut -f1`.

- **SKIP_DOWNLOAD**: Use this when you've already downloaded the data and just want to re-run the analysis. This is useful for:

  - Testing different jump thresholds without re-downloading
  - Sharing the data file with team members for offline analysis
  - Re-analyzing after manually modifying the data

**Important:** If you need to re-run the analysis from scratch, manually delete the output file first:

```bash
rm partition_${PARTITION_ID}_keys.txt
```

**Example Output:**

```
======================================
Partition Key Analysis
======================================
Elasticsearch URL: http://localhost:9200
Partition ID:      1
Batch Size:        1000
Output File:       partition_1_keys.txt

Testing Elasticsearch connectivity...
✓ Connected to Elasticsearch

Fetching processInstanceKeys...
Batch 1: Fetching up to 1000 keys... ✓ 1000 keys (Total: 1000)
Batch 2: Fetching up to 1000 keys... ✓ 1000 keys (Total: 2000)
Batch 3: Fetching up to 1000 keys... ✓ 456 keys (Total: 2456)
✓ All keys fetched

======================================
Analyzing for Key Jumps...
======================================
=== JUMP DETECTED ===
Previous key: 2251799813693770 (timestamp: 2026-01-14T14:20:15.123Z)
Current key:  4503599627373441 (timestamp: 2026-01-14T14:20:15.456Z)
Difference:   2251799813679671

======================================
Analysis Complete
======================================
Total keys analyzed: 2456
Results saved to:    partition_1_keys.txt
```

**Alternative Analysis Methods:**

Step 2 can be done using other tools if you prefer. The file `partition_${PARTITION_ID}_keys.txt` is a tab-separated text file with two columns:

1. **Column 1**: `processInstanceKey` (sorted numerically)
2. **Column 2**: `timestamp` (when the process instance started)

**What to look for:** Find rows where the difference between consecutive `processInstanceKey` values is greater than a threshold (e.g. one million ). This indicates an abnormal jump.

**Example file content:**

```
2251799813688023	2026-01-14T14:17:11.249Z
2251799813688159	2026-01-14T14:18:02.853Z
2251799813688165	2026-01-14T14:18:03.241Z
...
2251799813693770	2026-01-14T14:23:03.201Z
4503599627373441	2026-01-14T14:23:03.456Z  <- JUMP! Jumped into partition 2's range (~2.25 trillion difference)
4503599627373512	2026-01-14T14:23:03.789Z
```

**Using Python:**

```python
prev_key = None
with open('partition_1_keys.txt', 'r') as f:
    for line in f:
        key, timestamp = line.strip().split('\t')
        key = int(key)
        if prev_key is not None:
            diff = key - prev_key
            if diff > 1000000:
                print(f"JUMP: {prev_key} -> {key} (diff: {diff})")
        prev_key = key
```

**Using Excel/Spreadsheet:**
1. Import the file as tab-delimited data
2. Add a column to calculate the difference: `=A2-A1` (drag down)
3. Use conditional formatting or filter to highlight differences > 1,000,000

**How to Use the Results:**

1. The **Previous key** shows the last valid key before the jump (e.g., `2251799813693770`)
2. The **Current key** shows where it jumped to (e.g., `4503599627373441` - overflowed into partition 2's range)
3. Use these values in the "Calculating Key Values" section below to determine `NEW_KEY` and `NEW_MAX_KEY`
4. **Important:** Always verify the calculated range using the "Verifying the Key Range is Safe" section before proceeding with recovery

**Understanding Partition Key Ranges:**

- Partition 1: `[2251799813685248, 4503599627370496)`
- Partition 2: `[4503599627370496, 6755399441055744)`
- Partition 3: `[6755399441055744, 9007199254740992)`

If partition 1 has keys that jumped into partition 2's range, that indicates the overflow issue.

**Next Steps:** Once you've identified where the jump occurred, proceed to the sections below to calculate and verify safe key values before starting the recovery procedure.

#### Calculating Key Values

After identifying the jump location using the Elasticsearch analysis above, you need to calculate safe values for `NEW_KEY` and `NEW_MAX_KEY`.

Choosing the right values is critical - the new key range `[NEW_KEY, NEW_MAX_KEY)` must not overlap with existing records.

> [!IMPORTANT]
> If there's overlap with existing records there is risk of data corruption!

In camunda, the key for a partition `PARTITION_ID` must be in the range:

```bash
echo "[$(($PARTITION_ID << 51)), $((($PARTITION_ID + 1) << 51)))"
# For PARTITION_ID=1:
# [2251799813685248, 4503599627370496)
```

**Example:** If the Elasticsearch analysis (above) shows partition 1 keys jumped from `2251799813693770` to `4503599627373441` (which is in partition 2's range), then you should set:
- `NEW_KEY`: higher than the key before the jump → `2251800000000000`
- `NEW_MAX_KEY`: lower than the key it jumped to → `4503599000000000`

It's ok to leave a lot of room between the keys for extra safety, the key space is quite big.

#### Verifying the Key Range is Safe

**CRITICAL STEP:** Before proceeding with recovery, verify that no data exists in the proposed `[NEW_KEY, NEW_MAX_KEY)` range. Run both verification queries below to ensure the key range is safe.

```bash
# Set your proposed key values
NEW_KEY=2251800000000000
NEW_MAX_KEY=4503599000000000
PARTITION_ID=1
ES_URL="http://localhost:9200"
```

**1. Check Process Instances (operate-list-view):**

```bash
curl -s "$ES_URL/operate-list-view-*/_search" -H 'Content-Type: application/json' -d "$(cat <<EOF
{
  "size": 0,
  "query": {
    "bool": {
      "filter": [
        {"term": {"partitionId": $PARTITION_ID}},
        {"range": {"processInstanceKey": {"gte": $NEW_KEY, "lt": $NEW_MAX_KEY}}}
      ]
    }
  },
  "aggs": {
    "has_data": {
      "stats": {"field": "processInstanceKey"}
    }
  }
}
EOF
)" | jq '{
  index: "operate-list-view",
  total_hits: .hits.total.value,
  has_data: (.hits.total.value > 0),
  min_key: .aggregations.has_data.min,
  max_key: .aggregations.has_data.max
}'
```

**2. Check Flow Node Instances (operate-flownode-instance):**

```bash
curl -s "$ES_URL/operate-flownode-instance-*/_search" -H 'Content-Type: application/json' -d "$(cat <<EOF
{
  "size": 0,
  "query": {
    "bool": {
      "filter": [
        {"term": {"partitionId": $PARTITION_ID}},
        {"range": {"key": {"gte": $NEW_KEY, "lt": $NEW_MAX_KEY}}}
      ]
    }
  },
  "aggs": {
    "has_data": {
      "stats": {"field": "key"}
    }
  }
}
EOF
)" | jq '{
  index: "operate-flownode-instance",
  total_hits: .hits.total.value,
  has_data: (.hits.total.value > 0),
  min_key: .aggregations.has_data.min,
  max_key: .aggregations.has_data.max
}'
```

**Expected Safe Output (for both queries):**

```json
{
  "index": "operate-list-view",  // or "operate-flownode-instance"
  "total_hits": 0,
  "has_data": false,
  "min_key": null,
  "max_key": null
}
```

**BOTH queries must return `has_data: false` for the range to be safe.**

**If `has_data: true` (UNSAFE):**

> [!WARNING]
> Data exists in the proposed range! You must choose different values to avoid data corruption.

The query will show `min_key` and `max_key` of existing data. You have three options to find a safe range:

**Option A: Lower NEW_MAX_KEY**
Set `NEW_MAX_KEY` below the smallest `min_key` found across both queries:

```bash
NEW_MAX_KEY=<value less than min_key>
```

**Option B: Raise NEW_KEY**
Set `NEW_KEY` above the largest `max_key` found across both queries:

```bash
NEW_KEY=<value greater than max_key>
```

**Option C: Find a gap**
If there's a gap between the last key before the jump and first key after the jump:
1. Run the script again to get all keys: `./analyze-partition-keys.sh`
2. Find the gap in the output file `partition_${PARTITION_ID}_keys.txt`
3. Set `NEW_KEY` and `NEW_MAX_KEY` within that gap

After adjusting values, **re-run both verification queries** until both return `has_data: false`.

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

If the recovery job did not succeed, the best way to understand and fix the problem is to create another job YAML that
mounts all the PVCs as the recovery job, but without actually running the job.
If you change the container to run this command, it will stay up until it's killed manually.

```yaml
containers:
  - name: recovery
    image: gcr.io/zeebe-io/zeebe:cs-key-recovery-87-9e55dd4
    command:
      - /bin/bash
      - -c
    args:
      - "trap : TERM INT; sleep infinity & wait"
```

This will allow you to exec into the pod and access the shell (change `$pod_name` accordingly):

```bash
kubectl exec $pod_name -it -- bash
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

