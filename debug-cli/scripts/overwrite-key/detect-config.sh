#!/bin/bash
#
# Auto-detect Camunda/Zeebe cluster configuration
#
# Usage: ./detect-config.sh <namespace>
#

set -euo pipefail

# Check if namespace argument is provided
if [ $# -eq 0 ]; then
  echo "ERROR: Namespace argument is required"
  echo ""
  echo "Usage: $0 <namespace>"
  echo ""
  echo "Examples:"
  echo "  $0 camunda"
  echo "  $0 camunda-namespace"
  echo ""
  exit 1
fi

NAMESPACE="$1"

echo "=========================================="
echo "Detecting configuration for namespace: $NAMESPACE"
echo "=========================================="
echo ""

# Check if namespace exists
if ! kubectl get namespace $NAMESPACE &>/dev/null; then
  echo "ERROR: Namespace '$NAMESPACE' does not exist"
  echo ""
  echo "Available namespaces:"
  kubectl get namespaces -o custom-columns=NAME:.metadata.name --no-headers | grep -E "camunda|zeebe" || echo "  (no camunda/zeebe namespaces found)"
  echo ""
  echo "All namespaces:"
  kubectl get namespaces -o custom-columns=NAME:.metadata.name --no-headers
  exit 1
fi

# Find StatefulSet (look for zeebe or camunda)
echo "[1/6] Finding StatefulSet..."
STS=$(kubectl get statefulsets -n $NAMESPACE -o name 2>/dev/null | grep -E "camunda|zeebe" | head -1 | cut -d'/' -f2 || echo "")

if [ -z "$STS" ]; then
  echo "  ERROR: No StatefulSet found with 'camunda' or 'zeebe' in the name"
  echo "  Available StatefulSets:"
  kubectl get statefulsets -n $NAMESPACE -o name 2>/dev/null || echo "    (none)"
  exit 1
fi

echo "  StatefulSet: $STS"

# Get replica count
echo ""
echo "[2/6] Getting broker count..."
REPLICAS=$(kubectl get statefulset $STS -n $NAMESPACE -o jsonpath='{.spec.replicas}' 2>/dev/null || echo "0")
echo "  Broker Count: $REPLICAS"

# Get pod prefix (same as StatefulSet name)
echo ""
echo "[3/6] Detecting pod prefix..."
POD_PREFIX="$STS"
echo "  Pod Prefix: $POD_PREFIX"

# Verify pods exist
echo ""
echo "[4/6] Verifying pods..."
POD_COUNT=$(kubectl get pods -n $NAMESPACE -l "app.kubernetes.io/name=${POD_PREFIX}" -o name 2>/dev/null | wc -l || echo "0")
if [ "$POD_COUNT" -eq "0" ]; then
  # Try without label selector
  POD_COUNT=$(kubectl get pods -n $NAMESPACE 2>/dev/null | grep "^${POD_PREFIX}-" | wc -l || echo "0")
fi
echo "  Found $POD_COUNT pods matching prefix '$POD_PREFIX'"

if [ "$POD_COUNT" -eq "0" ]; then
  echo "  WARNING: No pods found. Cluster might be scaled down."
fi

# Find PVC prefix
echo ""
echo "[5/6] Detecting PVC prefix..."
PVC=$(kubectl get pvc -n $NAMESPACE -o name 2>/dev/null | grep -E "camunda|zeebe" | head -1 | cut -d'/' -f2 || echo "")

if [ -z "$PVC" ]; then
  echo "  ERROR: No PVC found with 'camunda' or 'zeebe' in the name"
  echo "  Available PVCs:"
  kubectl get pvc -n $NAMESPACE -o name 2>/dev/null || echo "    (none)"
  exit 1
fi

# Remove the broker number suffix (e.g., "-0", "-1", "-2")
PVC_PREFIX=$(echo $PVC | sed 's/-[0-9]*$//')
echo "  PVC Prefix: $PVC_PREFIX"

# Verify PVCs
echo ""
echo "[6/6] Verifying PVCs..."
for i in $(seq 0 $((REPLICAS - 1))); do
  PVC_NAME="${PVC_PREFIX}-${i}"
  if kubectl get pvc $PVC_NAME -n $NAMESPACE &>/dev/null; then
    echo "  ✓ $PVC_NAME exists"
  else
    echo "  ✗ $PVC_NAME NOT FOUND"
  fi
done

# Try to detect actuator port
echo ""
echo "[7/8] Detecting actuator port..."
ACTUATOR_PORT=$(kubectl get statefulset $STS -n $NAMESPACE -o jsonpath='{.spec.template.spec.containers[0].ports[?(@.name=="http")].containerPort}' 2>/dev/null || echo "9600")
if [ -z "$ACTUATOR_PORT" ]; then
  ACTUATOR_PORT=$(kubectl get statefulset $STS -n $NAMESPACE -o jsonpath='{.spec.template.spec.containers[0].ports[?(@.name=="management")].containerPort}' 2>/dev/null || echo "9600")
fi
echo "  Actuator Port: $ACTUATOR_PORT (detected or default)"

# Detect container image
echo ""
echo "[8/8] Detecting container image..."
CONTAINER_IMAGE=$(kubectl get statefulset $STS -n $NAMESPACE -o jsonpath='{.spec.template.spec.containers[0].image}' 2>/dev/null || echo "")

if [ -z "$CONTAINER_IMAGE" ]; then
  echo "  WARNING: Could not detect container image from StatefulSet"
  echo "  You will need to set CONTAINER_IMAGE manually"
  CONTAINER_IMAGE="camunda/camunda:latest"
  echo "  Using default: $CONTAINER_IMAGE"
else
  echo "  Container Image: $CONTAINER_IMAGE"
fi

echo ""
echo "=========================================="
echo "Configuration Summary"
echo "=========================================="
echo ""
echo "Namespace:         $NAMESPACE"
echo "StatefulSet:       $STS"
echo "Pod Prefix:        $POD_PREFIX"
echo "PVC Prefix:        $PVC_PREFIX"
echo "Broker Count:      $REPLICAS"
echo "Actuator Port:     $ACTUATOR_PORT"
echo "Container Image:   $CONTAINER_IMAGE"
echo ""
echo "=========================================="
echo "Environment Variables"
echo "=========================================="
echo ""
echo "Copy and paste these to configure the recovery scripts:"
echo ""
echo "export NAMESPACE=\"$NAMESPACE\""
echo "export STATEFULSET_NAME=\"$STS\""
echo "export POD_PREFIX=\"$POD_PREFIX\""
echo "export PVC_PREFIX=\"$PVC_PREFIX\""
echo "export BROKER_COUNT=\"$REPLICAS\""
echo "export ACTUATOR_PORT=\"$ACTUATOR_PORT\""
echo "export CONTAINER_IMAGE=\"$CONTAINER_IMAGE\""
echo ""
echo "Then run:"
echo "  ./identify-partition-brokers.sh  # Identify partition leader and replicas"
echo "  ./generate-recovery-job.sh       # Generate recovery Job YAML"
echo ""
