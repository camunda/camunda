# Camunda Key Recovery Procedure

This directory contains the recovery procedure for fixing corrupted key values in Camunda partitions.

## Overview

When a partition's key generator becomes corrupted (e.g., key overflow), this recovery procedure allows you to reset the key to a safe value and set a maximum key threshold to prevent future corruption.

## Files

- **`recovery-script.sh`**: Bash script that performs the actual key recovery operations (inlined in generated Job YAML)
- **`recovery-procedure.sh`**: Automated recovery procedure script (generates and applies Job YAML)
- **`recovery-values.example.sh`**: Example configuration file for environment variables
- **`detect-config.sh`**: Auto-detection script for cluster configuration
- **`README.md`**: This file - complete documentation
- **`CONFIG_EXAMPLES.md`**: Configuration examples for different Camunda versions
- **`TESTING_GUIDE.md`**: Quick testing guide
- **`generated/`**: Directory containing generated Job YAML files

## Quick Start

### Using the Automated Script (Recommended)

For a guided, interactive recovery process:

**Required Environment Variables:**
```bash
export NAMESPACE=<your-namespace>      # e.g., cs-key-recovery
export PARTITION_ID=<partition-id>     # e.g., 1 (REQUIRED)
export NEW_KEY=<new-key-value>         # e.g., 2251800000000000
export NEW_MAX_KEY=<new-max-key>       # e.g., 2251900000000000 (OPTIONAL)
```

**Or use a values file:**
```bash
cp recovery-values.example.sh recovery-values.sh
# Edit recovery-values.sh with your configuration
source recovery-values.sh
```

**Run the script:**
```bash
# Interactive mode (executes the recovery)
./recovery-procedure.sh

# Dry-run mode (preview without making changes, generates Job YAML file)
./recovery-procedure.sh --dry-run
```

#### Dry-Run Mode

Dry-run mode allows you to preview the entire recovery procedure without making any changes to the cluster:

```bash
./recovery-procedure.sh --dry-run
```

**What dry-run does:**
- **Executes read-only queries**: Runs `kubectl get` commands to show current cluster state
- **Checks pod status**: Queries whether pods exist and their current state
- **Generates the recovery Job YAML file**: Creates the actual YAML that would be applied
- **Saves YAML to**: `generated/recovery-job-partition-<ID>-<timestamp>.yaml`
- **Shows exactly what would be executed**: Displays all write operations that would run
- **Skips all modifications**: Does NOT scale, apply, delete, or modify any resources

**What dry-run queries (read-only):**
- `kubectl get pods` - Shows current pods
- `kubectl get pvc` - Shows current PVCs
- `kubectl get statefulset` - Shows StatefulSet status
- `kubectl get pod <name>` - Checks if pods exist

**What dry-run skips (write operations):**
- `kubectl scale` - Does NOT scale down/up the cluster
- `kubectl apply` - Does NOT apply ConfigMaps or Jobs
- `kubectl delete` - Does NOT delete resources
- Port-forwarding and partition queries - Does NOT connect to pods

**Workflow with dry-run:**
1. Run `./recovery-procedure.sh --dry-run` to generate the Job YAML
2. Review the generated file: `generated/recovery-job-partition-1-20251212-143022.yaml`
3. Edit the file if needed (adjust resources, add annotations, etc.)
4. Apply manually: `kubectl apply -f generated/recovery-job-partition-1-20251212-143022.yaml`
5. Or run the full script: `./recovery-procedure.sh` (without --dry-run)

#### Resuming from a Specific Step

If the recovery procedure fails or is interrupted, you can resume from a specific step instead of starting over:

```bash
./recovery-procedure.sh --from-step <step-name>
```

**Available steps:**
- `identify-leader` - Step 1: Identify partition leader and replicas
- `check-cluster` - Step 2: Check cluster state
- `shutdown` - Step 3: Shutdown cluster
- `backup-reminder` - Step 4: Backup reminder
- `run-job` - Step 5: Run recovery job
- `cleanup-job` - Step 6: Cleanup recovery job
- `restart` - Step 7: Restart cluster
- `verify` - Step 8: Verify recovery

**Examples:**

If the recovery job failed and you need to restart the cluster:
```bash
./recovery-procedure.sh --from-step restart
```

If the cluster restarted but you want to re-verify:
```bash
./recovery-procedure.sh --from-step verify
```

**How it works:**
- The script saves state after each completed step to `generated/.recovery-state-partition-<ID>.env`
- When resuming, it loads the previous state (leader broker, partition brokers, etc.)
- It skips all steps before the specified `--from-step`
- It runs the specified step and all subsequent steps
- The state file is automatically deleted on successful completion

**Important:**
- You must have run the procedure at least once to create the state file
- The state file contains: leader broker ID, partition broker IDs, container image, and last completed step
- If you need to change environment variables (like NEW_KEY), you must start from the beginning

#### Interactive Mode

The script will:
1. Auto-detect your cluster configuration (StatefulSet name, pod names, PVC names, broker count, container image)
2. **Identify the partition leader and all replicas (LEADER/FOLLOWER brokers for this partition)**
3. Guide you through shutting down the cluster
4. **Dynamically generate recovery Job YAML with:**
   - **Inline recovery script** (no ConfigMap needed)
   - **PVCs mounted ONLY for brokers hosting the partition**
   - **Correct container image** from running StatefulSet
5. **Save the generated Job YAML to `generated/` directory with timestamp**
6. **Prompt for confirmation before applying the Job**
7. Apply the recovery job
8. Restart the cluster
9. Verify recovery success

**Important Notes:**
- The recovery job only mounts PVCs for brokers that are LEADER or FOLLOWER for the specified partition
- This reduces resource usage and makes the recovery more targeted
- Broker IDs are determined at runtime based on actual partition ownership
- Generated Job YAML is saved to `generated/` directory with timestamps for review, editing, and reuse
- You can exit at the confirmation prompt to manually edit the YAML before applying

**Template Variables:**

The YAML files support the following template placeholders that are automatically substituted by `recovery-procedure.sh`:
- `{{NAMESPACE}}` - Kubernetes namespace
- `{{PARTITION_ID}}` - Partition to recover
- `{{LAST_LEADER_BROKER}}` - Leader broker ID
- `{{NEW_KEY}}` - New key value
- `{{NEW_MAX_KEY}}` - New max key value
- `{{BROKER_COUNT}}` - Number of brokers
- `{{PVC_PREFIX}}` - PVC name prefix (e.g., data-camunda)

**Optional Environment Variables** (auto-detected if not set):
- `BROKER_COUNT` (default: 3) - Total number of brokers in cluster
- `STATEFULSET_NAME` (default: camunda)
- `POD_PREFIX` (default: camunda)
- `PVC_PREFIX` (default: data-camunda)
- `ACTUATOR_PORT` (default: 9600)
- `CONTAINER_IMAGE` (default: auto-detected from StatefulSet)

**Note:** PARTITION_ID is required. The script will automatically identify which brokers host the partition and only mount those PVCs in the recovery job.

See `CONFIG_EXAMPLES.md` for examples with different Camunda versions.

### Manual Recovery

For manual control over each step, follow the procedure below.

**Note on Templating:**
If applying YAML files manually, you need to substitute the template variables yourself:

```bash
# Example: Substitute templates and apply
cat recovery-configmap.yaml | \
  sed 's/{{NAMESPACE}}/cs-key-recovery/g' | \
  kubectl apply -f -

cat recovery-job.yaml | \
  sed 's/{{NAMESPACE}}/cs-key-recovery/g' | \
  sed 's/{{PARTITION_ID}}/1/g' | \
  sed 's/{{LAST_LEADER_BROKER}}/0/g' | \
  sed 's/{{NEW_KEY}}/2251800000000000/g' | \
  sed 's/{{NEW_MAX_KEY}}/2251900000000000/g' | \
  sed 's/{{BROKER_COUNT}}/3/g' | \
  sed 's/{{PVC_PREFIX}}/data-camunda/g' | \
  kubectl apply -f -
```

Or edit the YAML files directly to replace template placeholders with actual values.

## Recovery Procedure (Manual Steps)

### Prerequisites

- Kubectl configured with access to the cluster
- Cluster namespace: `cs-key-recovery`
- Required information:
  - Partition ID to recover
  - Broker ID that was last leader for the partition
  - New key value to set
  - New max key value to set

### Step 1: Identify the Last Leader for the Partition

For each broker in the cluster, check which one is the leader for the affected partition:

```bash
# For each broker (0, 1, 2, etc.)
kubectl port-forward -n cs-key-recovery camunda-0 9600:9600 &
curl http://localhost:9600/actuator/partitions | jq '.["1"].role'
pkill -f "port-forward.*9600"

# Repeat for camunda-1, camunda-2, etc.
```

The broker with `"role": "LEADER"` for your partition is the last leader.

**Example output:**
```json
{
  "1": {
    "role": "LEADER",
    "processedPosition": 1081388,
    ...
  }
}
```

### Step 2: Shut Down the Cluster

Scale the StatefulSet to 0 replicas to ensure no brokers are running:

```bash
kubectl scale statefulset camunda --replicas=0 -n cs-key-recovery
kubectl wait --for=delete pod/camunda-0 pod/camunda-1 pod/camunda-2 -n cs-key-recovery --timeout=300s
```

Verify all brokers are stopped:

```bash
kubectl get pods -n cs-key-recovery | grep camunda
# Should show no running pods
```

### Step 3: Take a Cold Backup

**IMPORTANT**: Before running the recovery job, take a backup of your data!

This could be:
- PVC snapshots
- Volume snapshots
- File-level backup of the data directories

This step is NOT automated by the recovery job and must be done manually according to your backup procedures.

### Step 4: Configure the Recovery Job

The recovery script is stored in a ConfigMap (`recovery-configmap.yaml`) and executed by the Job (`recovery-job.yaml`).

Edit the environment variables in `recovery-job.yaml`:

```yaml
env:
- name: PARTITION_ID
  value: "1"  # The partition to recover

- name: LAST_LEADER_BROKER
  value: "0"  # The broker ID that was last leader (from Step 1)

- name: NEW_KEY
  value: "2251800000000000"  # New key value to set

- name: NEW_MAX_KEY
  value: "2251900000000000"  # New max key value (must be > NEW_KEY)

# Optional: specify a specific snapshot ID
# - name: SNAPSHOT_ID
#   value: "79292-1-963202-962181-0-c52549fd"
```

#### Calculating Key Values

Camunda keys are calculated as: `((long) partitionId << 51) + key`

For partition 1:
- Base value: `1 << 51 = 2251799813685248`
- Keys range from: `2251799813685248` to `4503599627370495`

**Example values:**
- `NEW_KEY`: Set to a high safe value (e.g., `2251800000000000`)
- `NEW_MAX_KEY`: Set higher than NEW_KEY with room for growth (e.g., `2251900000000000`)

### Step 5: Apply the ConfigMap and Run the Recovery Job

First, apply the ConfigMap containing the recovery script:

```bash
kubectl apply -f recovery-configmap.yaml -n cs-key-recovery
```

Then apply the recovery job:

```bash
kubectl apply -f recovery-job.yaml -n cs-key-recovery
```

Monitor the job execution:

```bash
kubectl logs -f job/key-recovery-job -n cs-key-recovery
```

The job will:
1. Load the recovery script from the ConfigMap
2. Validate partition data exists
3. Determine which snapshot to use (latest or specified)
4. Backup all broker snapshots (rename to `.backup`)
5. Run `cdbg state update-key` on the leader's snapshot
6. Copy the updated snapshot to all replicas
7. Clean up backup files

Wait for the job to complete:

```bash
kubectl wait --for=condition=complete --timeout=600s job/key-recovery-job -n cs-key-recovery
```

Expected output from the job:

```
=============================================
=== Key Recovery Job Starting ===
=============================================
Partition ID:       1
Leader Broker:      0
New Key:            2251800000000000
New Max Key:        2251900000000000
Snapshot Override:  <auto-detect latest>
=============================================

[1/5] Validating partition data...
Leader partition path: /mnt/broker-0/raft-partition/partitions/1
...

[2/5] Determining snapshot to use...
Auto-detected latest snapshot: 79292-1-963202-962181-0-c52549fd
...

[3/5] Creating backups of partition data on all brokers...
  - Backing up Broker 0 snapshots...
    Renaming: 79292-1-963202-962181-0-c52549fd -> 79292-1-963202-962181-0-c52549fd.backup
...

[4/5] Updating key values using cdbg...
Command: cdbg state update-key
  --root /mnt/broker-0/raft-partition/partitions/1
  --runtime /tmp/recovery-runtime-1
  --snapshot 79292-1-963202-962181-0-c52549fd.backup
  --partition-id 1
  --key 2251800000000000
  --max-key 2251900000000000

Key update completed successfully!
...

[5/5] Copying updated partition data to replicas...
  - Syncing to Broker 1...
  - Syncing to Broker 2...
...

[6/6] Cleaning up backup files...
...

=============================================
=== Recovery Complete ===
=============================================
Partition 1 has been recovered with:
  - Key:     2251800000000000
  - Max Key: 2251900000000000
  - Snapshot: 79292-1-963202-962181-0-c52549fd

All brokers have been updated with the new snapshot.
You can now restart the cluster.
=============================================
```

### Step 6: Verify Job Success

Check the job status:

```bash
kubectl get job key-recovery-job -n cs-key-recovery
```

Should show:
```
NAME               COMPLETIONS   DURATION   AGE
key-recovery-job   1/1           2m34s      3m
```

If the job failed, check the logs:

```bash
kubectl logs job/key-recovery-job -n cs-key-recovery
```

### Step 7: Clean Up the Recovery Job

Delete the job (but keep the YAML for documentation):

```bash
kubectl delete job key-recovery-job -n cs-key-recovery
```

### Step 8: Restart the Cluster

Scale the StatefulSet back to the original number of replicas:

```bash
kubectl scale statefulset camunda --replicas=3 -n cs-key-recovery
```

Wait for all pods to be ready:

```bash
kubectl wait --for=condition=ready pod/camunda-0 pod/camunda-1 pod/camunda-2 -n cs-key-recovery --timeout=300s
```

Verify all pods are running:

```bash
kubectl get pods -n cs-key-recovery | grep camunda
```

### Step 9: Verify Recovery Success

#### Check Partition Health

```bash
kubectl port-forward -n cs-key-recovery camunda-0 9600:9600 &
curl http://localhost:9600/actuator/partitions | jq '.["1"]'
pkill -f "port-forward"
```

Verify:
- `"role": "LEADER"` (or "FOLLOWER" depending on the broker)
- `"health.status": "HEALTHY"`

#### Check All Brokers

For each broker, verify the partition is healthy:

```bash
for i in 0 1 2; do
  echo "=== Checking camunda-$i ==="
  kubectl port-forward -n cs-key-recovery camunda-$i 9600:9600 > /dev/null 2>&1 &
  PF_PID=$!
  sleep 2
  
  curl -s http://localhost:9600/actuator/partitions | jq ".\"1\" | {role, health: .health.status}"
  
  kill $PF_PID 2>/dev/null || true
  sleep 1
done
```

#### Check Logs for Errors

```bash
kubectl logs camunda-0 -n cs-key-recovery --tail=100 | grep -i error
kubectl logs camunda-1 -n cs-key-recovery --tail=100 | grep -i error
kubectl logs camunda-2 -n cs-key-recovery --tail=100 | grep -i error
```

#### Verify Cluster Topology

```bash
kubectl port-forward -n cs-key-recovery camunda-0 9600:9600 &
curl http://localhost:9600/actuator/cluster | jq '.brokers[].partitions[] | select(.id==1)'
pkill -f "port-forward"
```

All brokers should show the partition as `"state": "ACTIVE"`.

## Environment Variables Reference

The recovery job accepts the following environment variables (configured in `recovery-job.yaml`):

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `PARTITION_ID` | Yes | `1` | The partition ID to recover |
| `LAST_LEADER_BROKER` | Yes | `0` | The broker ID (0, 1, 2...) that was last leader for this partition |
| `NEW_KEY` | Yes | `2251800000000000` | The new key value to set (must be valid for the partition) |
| `NEW_MAX_KEY` | Yes | `2251900000000000` | The new maximum key value (must be > NEW_KEY) |
| `SNAPSHOT_ID` | No | (auto-detect) | Optional: specific snapshot ID to use instead of latest |

## How the Recovery Job Works

The recovery process uses two Kubernetes resources:

1. **ConfigMap** (`recovery-configmap.yaml`): Contains the recovery bash script
2. **Job** (`recovery-job.yaml`): Mounts the ConfigMap and executes the script

The recovery script performs these operations:

1. **Validation**: Checks that the partition data exists on the specified leader broker

2. **Snapshot Selection**: 
   - If `SNAPSHOT_ID` is set, uses that snapshot
   - Otherwise, auto-detects the latest snapshot

3. **Backup**:
   - Renames all snapshot directories to `<snapshot-id>.backup`
   - Renames checksum files to `<checksum>.backup`
   - Does this for ALL brokers (0, 1, 2)

4. **Key Update**:
   - Runs `cdbg state update-key` on the leader's backed-up snapshot
   - Updates the key and max_key values
   - Creates a new snapshot with the updated values

5. **Replication**:
   - Copies the updated snapshot from the leader to all replica brokers
   - Ensures all brokers have the same snapshot data

6. **Cleanup**:
   - Removes all `.backup` files after successful completion
   - Leaves only the new updated snapshots

## Troubleshooting

### ConfigMap Not Found

**Cause**: The ConfigMap was not applied before the Job.

**Solution**:
```bash
kubectl apply -f recovery-configmap.yaml -n cs-key-recovery
```

### Job Fails with "Snapshot directory does not exist"

**Cause**: The specified snapshot ID doesn't exist, or auto-detection failed.

**Solution**:
1. Exec into one of the broker pods (even though scaled to 0, you can create a debug pod)
2. Check available snapshots:
   ```bash
   ls -la /mnt/broker-0/raft-partition/partitions/1/snapshots/
   ```
3. Set the `SNAPSHOT_ID` env var to a valid snapshot ID

### Job Fails with "Leader partition path does not exist"

**Cause**: The `LAST_LEADER_BROKER` value is incorrect, or the partition ID is wrong.

**Solution**:
1. Verify the partition ID is correct
2. Double-check which broker was the leader using the actuator endpoint
3. Verify the PVCs are correctly mounted

### Brokers Don't Start After Recovery

**Cause**: The snapshot data may be corrupted or incompatible.

**Solution**:
1. Check broker logs: `kubectl logs camunda-0 -n cs-key-recovery`
2. If needed, restore from the cold backup taken in Step 3
3. Re-run the recovery job with different key values

### Partition Shows UNHEALTHY After Recovery

**Cause**: The cluster may need more time to stabilize, or there's a genuine issue.

**Solution**:
1. Wait 1-2 minutes for the cluster to stabilize
2. Check leader election: One broker should be LEADER, others FOLLOWER
3. Check logs for specific errors
4. Verify all brokers have the same snapshot data

## Recovery Job Timeout

The job has a 10-minute timeout (`activeDeadlineSeconds: 600`). If it hasn't completed by then:

1. Check the job logs for where it got stuck
2. The job will be terminated automatically
3. Data will be left in its current state (with `.backup` files present)
4. You can manually investigate and retry

## Testing

To test the recovery procedure in the `cs-key-recovery` environment:

```bash
export NAMESPACE=cs-key-recovery
export NEW_KEY=2251800000000000
export NEW_MAX_KEY=2251900000000000
./recovery-procedure.sh
```

This script automates all the steps above and includes verification checks.

## Notes

- The recovery job requires the cluster to be shut down (StatefulSet scaled to 0)
- All broker PVCs must be accessible from a single pod
- The job uses verbose logging for troubleshooting
- Backup files (.backup) are automatically cleaned up on success
- On failure, backup files remain in place for manual recovery

## Support

For issues or questions about this recovery procedure:

1. Check the job logs: `kubectl logs job/key-recovery-job -n cs-key-recovery`
2. Review broker logs after recovery
3. Verify the key values are appropriate for your partition
4. Ensure you have a valid cold backup before attempting recovery
