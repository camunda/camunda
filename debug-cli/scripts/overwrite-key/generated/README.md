# Generated Recovery Job YAML Files

This directory contains automatically generated Kubernetes Job YAML files created by the `recovery-procedure.sh` script.

## File Naming Convention

```
recovery-job-partition-<PARTITION_ID>-<TIMESTAMP>.yaml
```

**Examples:**
- `recovery-job-partition-1-20251212-134811.yaml`
- `recovery-job-partition-3-20251212-150230.yaml`

## Contents

Each generated file contains:
- Complete Kubernetes Job manifest
- Dynamically configured environment variables (partition ID, keys, broker IDs)
- PVC mounts for only the brokers hosting the specified partition
- All recovery parameters from your environment variables

## Usage

### Review Generated YAML

```bash
cat generated/recovery-job-partition-1-20251212-134811.yaml
```

### Edit Before Applying

```bash
vim generated/recovery-job-partition-1-20251212-134811.yaml
# Make changes (adjust resources, add labels, etc.)
```

### Apply Manually

```bash
kubectl apply -f generated/recovery-job-partition-1-20251212-134811.yaml
```

### Monitor Job

```bash
kubectl logs -f job/key-recovery-job -n <namespace>
kubectl wait --for=condition=complete --timeout=600s job/key-recovery-job -n <namespace>
```

## Cleanup

To remove all generated files:

```bash
rm -rf generated/*.yaml
```

Or remove the entire directory:

```bash
rm -rf generated/
```

## Git Ignore

This directory is included in `.gitignore` to prevent accidentally committing operator-specific YAML files to version control.

## Generation

Files are created by:
- `./recovery-procedure.sh` (interactive mode)
- `./recovery-procedure.sh --dry-run` (preview mode)

Both modes generate and save the Job YAML to this directory.
