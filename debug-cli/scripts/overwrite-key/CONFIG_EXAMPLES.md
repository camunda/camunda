# Configuration Examples for Different Camunda Versions

This document provides configuration examples for running the recovery procedure on different Camunda versions and setups.

## Environment Variables

Both the recovery procedure script and recovery job support environment variables for configuration:

### Recovery Procedure Script (`recovery-procedure.sh`)

```bash
# Customize the recovery procedure script with environment variables
export NAMESPACE="my-namespace"
export PARTITION_ID="2"
export BROKER_COUNT="5"
export STATEFULSET_NAME="zeebe"
export POD_PREFIX="zeebe"
export PVC_PREFIX="data-zeebe"
export ACTUATOR_PORT="9600"
export NEW_KEY="2251800000000000"
export NEW_MAX_KEY="2251900000000000"

./recovery-procedure.sh
```

### Recovery Job (`recovery-job.yaml`)

Edit the environment variables section:

```yaml
env:
- name: PARTITION_ID
  value: "1"
- name: LAST_LEADER_BROKER
  value: "0"
- name: NEW_KEY
  value: "2251800000000000"
- name: NEW_MAX_KEY
  value: "2251900000000000"
- name: BROKER_COUNT
  value: "3"
```

## Common Version Configurations

### Camunda 8.x (Newest - Default)

**PVC Pattern**: `data-camunda-{0,1,2,...}`  
**StatefulSet**: `camunda`  
**Pod Pattern**: `camunda-{0,1,2,...}`

```bash
# Test script
export NAMESPACE="camunda"
export STATEFULSET_NAME="camunda"
export POD_PREFIX="camunda"
export PVC_PREFIX="data-camunda"
export BROKER_COUNT="3"

./test-recovery-procedure.sh
```

**Recovery Job PVCs**:
```yaml
volumes:
- name: broker-0-data
  persistentVolumeClaim:
    claimName: data-camunda-0
- name: broker-1-data
  persistentVolumeClaim:
    claimName: data-camunda-1
- name: broker-2-data
  persistentVolumeClaim:
    claimName: data-camunda-2
```

---


### Older Helm Chart Versions

**PVC Pattern**: `zeebe-data-zeebe-{0,1,2,...}`  
**StatefulSet**: `zeebe`  
**Pod Pattern**: `zeebe-{0,1,2,...}`

```bash
# Test script
export NAMESPACE="zeebe"
export STATEFULSET_NAME="zeebe"
export POD_PREFIX="zeebe"
export PVC_PREFIX="zeebe-data-zeebe"
export BROKER_COUNT="3"

./test-recovery-procedure.sh
```

**Recovery Job PVCs**:
```yaml
volumes:
- name: broker-0-data
  persistentVolumeClaim:
    claimName: zeebe-data-zeebe-0
- name: broker-1-data
  persistentVolumeClaim:
    claimName: zeebe-data-zeebe-1
- name: broker-2-data
  persistentVolumeClaim:
    claimName: zeebe-data-zeebe-2
```

---

## Custom Configurations

### 5-Broker Cluster

For clusters with more than 3 brokers, you need to:

1. **Update test script**:
```bash
export BROKER_COUNT="5"
./test-recovery-procedure.sh
```

2. **Update recovery job** - add volume mounts and volumes:

```yaml
env:
- name: BROKER_COUNT
  value: "5"

volumeMounts:
- name: broker-0-data
  mountPath: /mnt/broker-0
- name: broker-1-data
  mountPath: /mnt/broker-1
- name: broker-2-data
  mountPath: /mnt/broker-2
- name: broker-3-data
  mountPath: /mnt/broker-3
- name: broker-4-data
  mountPath: /mnt/broker-4
- name: temp-storage
  mountPath: /tmp/recovery

volumes:
- name: broker-0-data
  persistentVolumeClaim:
    claimName: data-camunda-0
- name: broker-1-data
  persistentVolumeClaim:
    claimName: data-camunda-1
- name: broker-2-data
  persistentVolumeClaim:
    claimName: data-camunda-2
- name: broker-3-data
  persistentVolumeClaim:
    claimName: data-camunda-3
- name: broker-4-data
  persistentVolumeClaim:
    claimName: data-camunda-4
```

---

### Custom Namespace and Names

```bash
# Test script
export NAMESPACE="prod-camunda"
export STATEFULSET_NAME="my-zeebe-cluster"
export POD_PREFIX="my-zeebe-cluster"
export PVC_PREFIX="zeebe-storage"
export BROKER_COUNT="3"

./test-recovery-procedure.sh
```

---

## Finding Your Configuration

If you're unsure about your configuration, use these commands:

### 1. Find StatefulSet Name
```bash
kubectl get statefulsets -n <namespace>
```

### 2. Find Pod Prefix
```bash
kubectl get pods -n <namespace>
# Look at the pod names (e.g., "camunda-0" has prefix "camunda")
```

### 3. Find PVC Names
```bash
kubectl get pvc -n <namespace>
# Look at the PVC names (e.g., "data-camunda-0" has prefix "data-camunda")
```

### 4. Count Brokers
```bash
kubectl get statefulset <statefulset-name> -n <namespace> -o jsonpath='{.spec.replicas}'
```

### 5. Verify Actuator Port
```bash
kubectl get statefulset <statefulset-name> -n <namespace> -o yaml | grep -A5 "ports:"
# Look for the port with name "http" or "management" (usually 9600)
```

---

## Example: Auto-Detect Configuration

Run this script to auto-detect your configuration:

```bash
#!/bin/bash

NAMESPACE="${1:-default}"

echo "Detecting configuration for namespace: $NAMESPACE"
echo ""

# Find StatefulSet
STS=$(kubectl get statefulsets -n $NAMESPACE -o name | head -1 | cut -d'/' -f2)
echo "StatefulSet: $STS"

# Get replica count
REPLICAS=$(kubectl get statefulset $STS -n $NAMESPACE -o jsonpath='{.spec.replicas}')
echo "Broker Count: $REPLICAS"

# Get pod prefix (same as StatefulSet name)
echo "Pod Prefix: $STS"

# Find PVC prefix
PVC=$(kubectl get pvc -n $NAMESPACE -o name | head -1 | cut -d'/' -f2)
PVC_PREFIX=$(echo $PVC | sed 's/-[0-9]*$//')
echo "PVC Prefix: $PVC_PREFIX"

echo ""
echo "Use these environment variables:"
echo "export NAMESPACE=\"$NAMESPACE\""
echo "export STATEFULSET_NAME=\"$STS\""
echo "export POD_PREFIX=\"$STS\""
echo "export PVC_PREFIX=\"$PVC_PREFIX\""
echo "export BROKER_COUNT=\"$REPLICAS\""
```

Save this to `detect-config.sh` and run:
```bash
chmod +x detect-config.sh
./detect-config.sh <namespace>
```

---

## Testing Your Configuration

Before running the full recovery procedure, test your configuration:

```bash
# 1. Set environment variables
export NAMESPACE="cs-key-recovery"
export STATEFULSET_NAME="camunda"
export POD_PREFIX="camunda"
export PVC_PREFIX="data-camunda"
export BROKER_COUNT="3"

# 2. Test connectivity to brokers
for i in $(seq 0 $((BROKER_COUNT - 1))); do
  echo "Testing ${POD_PREFIX}-${i}..."
  kubectl get pod ${POD_PREFIX}-${i} -n $NAMESPACE
done

# 3. Test PVC existence
for i in $(seq 0 $((BROKER_COUNT - 1))); do
  echo "Testing ${PVC_PREFIX}-${i}..."
  kubectl get pvc ${PVC_PREFIX}-${i} -n $NAMESPACE
done

# 4. Test StatefulSet
kubectl get statefulset $STATEFULSET_NAME -n $NAMESPACE

# 5. If all tests pass, run the recovery script
./test-recovery-procedure.sh
```

---

## Common Issues

### Issue: PVC Names Don't Match

**Error**: Job fails with "PVC not found"

**Solution**: Update the `claimName` in the recovery-job.yaml volumes section to match your actual PVC names.

### Issue: Wrong Broker Count

**Error**: Script waits forever for pods that don't exist

**Solution**: Set `BROKER_COUNT` environment variable correctly.

### Issue: StatefulSet Not Found

**Error**: `kubectl scale statefulset camunda --replicas=0` fails

**Solution**: Set `STATEFULSET_NAME` environment variable to your actual StatefulSet name.

---

## Support Matrix

| Camunda Version | StatefulSet | Pod Prefix | PVC Prefix | Tested |
|----------------|-------------|------------|------------|--------|
| 8.8.x | `camunda` | `camunda` | `data-camunda` | ✅ Yes |
| 8.7.x | `camunda` | `camunda` | `data-camunda` | ✅ Yes |
| 8.6.x | `camunda` | `camunda` | `data-camunda` | ✅ Yes |
| 8.5.x | `zeebe` | `zeebe` | `data-zeebe` | ⚠️ Untested |
| 8.4.x | `zeebe` | `zeebe` | `data-zeebe` | ⚠️ Untested |
| 7.x (Zeebe) | `zeebe` | `zeebe` | `zeebe-data-zeebe` | ⚠️ Untested |

If you've tested with a version not listed, please contribute your configuration!
