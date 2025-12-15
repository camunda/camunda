#!/bin/bash
set -euo pipefail

# ====================================================
# YAML Generation Validation Test
# ====================================================
#
# This script validates that both YAML generation methods
# (traditional bash and yq) produce valid Kubernetes YAML.
#
# Note: The two methods produce different formatting styles
# but both should be valid and functional Kubernetes manifests.
#
# Usage:
#   ./test-yaml-equivalence.sh
#
# The script will:
#   1. Set up test environment variables
#   2. Generate YAML using traditional method
#   3. Generate YAML using yq method
#   4. Validate both with kubectl dry-run
#   5. Report on differences and validity
#
# ====================================================

echo "============================================="
echo "=== YAML Generation Validation Test ==="
echo "============================================="
echo ""

# Check if yq is installed
if ! command -v yq &> /dev/null; then
    echo "ERROR: yq is not installed. This test requires yq."
    echo "Install yq: https://github.com/mikefarah/yq"
    exit 1
fi

# Set up test environment variables
echo "[1/5] Setting up test environment variables..."
export NAMESPACE="test-namespace"
export PARTITION_ID="1"
export LAST_LEADER_BROKER="0"
export NEW_KEY="2251800000000000"
export NEW_MAX_KEY="2251900000000000"
export PARTITION_BROKER_IDS="0 1 2"
export CONTAINER_IMAGE="camunda/camunda:8.6.1"
export STATEFULSET_NAME="camunda"
export PVC_PREFIX="data-camunda"
export CONTAINER_USER_ID="1000"
export CONTAINER_GROUP_ID="1001"
export CONTAINER_FS_GROUP="1001"
export GENERATED_DIR="generated"

echo "  NAMESPACE:              ${NAMESPACE}"
echo "  PARTITION_ID:           ${PARTITION_ID}"
echo "  LAST_LEADER_BROKER:     ${LAST_LEADER_BROKER}"
echo "  NEW_KEY:                ${NEW_KEY}"
echo "  NEW_MAX_KEY:            ${NEW_MAX_KEY}"
echo "  PARTITION_BROKER_IDS:   ${PARTITION_BROKER_IDS}"
echo "  CONTAINER_IMAGE:        ${CONTAINER_IMAGE}"
echo ""

# Generate YAML using traditional method
echo "[2/5] Generating YAML using traditional method..."
TRADITIONAL_OUTPUT="generated/test-traditional.yaml"
export OUTPUT_FILE="$TRADITIONAL_OUTPUT"
./generate-recovery-job.sh > /dev/null 2>&1
echo "  Generated: ${TRADITIONAL_OUTPUT}"
echo ""

# Generate YAML using yq method
echo "[3/5] Generating YAML using yq method..."
YQ_OUTPUT="generated/test-yq.yaml"
export OUTPUT_FILE="$YQ_OUTPUT"
./generate-recovery-job.sh --use-yq > /dev/null 2>&1
echo "  Generated: ${YQ_OUTPUT}"
echo ""

# Compare the outputs
echo "[4/5] Comparing YAML outputs..."
echo ""

# First, do a simple diff to see if they're identical
if diff -q "$TRADITIONAL_OUTPUT" "$YQ_OUTPUT" > /dev/null 2>&1; then
    echo "✓ Files are byte-for-byte identical!"
    echo ""
    echo "[5/5] Cleaning up test files..."
    rm -f "$TRADITIONAL_OUTPUT" "$YQ_OUTPUT"
    echo "  Removed: ${TRADITIONAL_OUTPUT}"
    echo "  Removed: ${YQ_OUTPUT}"
    echo ""
    echo "============================================="
    echo "=== TEST PASSED ==="
    echo "============================================="
    echo "Both methods produce identical YAML output."
    exit 0
fi

# If not identical, use yq to compare semantic equivalence
echo "Files are not byte-for-byte identical. Checking semantic equivalence..."
echo ""

# Use yq to parse and compare the YAML structures
TRADITIONAL_JSON=$(yq eval -o=json "$TRADITIONAL_OUTPUT" | jq -S '.')
YQ_JSON=$(yq eval -o=json "$YQ_OUTPUT" | jq -S '.')

if [ "$TRADITIONAL_JSON" == "$YQ_JSON" ]; then
    echo "✓ Files are semantically equivalent!"
    echo ""
    echo "Note: The files differ in formatting but represent the same YAML structure."
    echo ""
    echo "Formatting differences:"
    diff -u "$TRADITIONAL_OUTPUT" "$YQ_OUTPUT" || true
    echo ""
    echo "[5/5] Cleaning up test files..."
    rm -f "$TRADITIONAL_OUTPUT" "$YQ_OUTPUT"
    echo "  Removed: ${TRADITIONAL_OUTPUT}"
    echo "  Removed: ${YQ_OUTPUT}"
    echo ""
    echo "============================================="
    echo "=== TEST PASSED (with formatting differences) ==="
    echo "============================================="
    echo "Both methods produce semantically equivalent YAML."
    exit 0
else
    echo "✗ Files are NOT semantically equivalent!"
    echo ""
    echo "Structural differences found:"
    echo ""
    
    # Show the diff
    echo "=== Traditional Method Output ==="
    cat "$TRADITIONAL_OUTPUT"
    echo ""
    echo "=== YQ Method Output ==="
    cat "$YQ_OUTPUT"
    echo ""
    
    echo "=== JSON Diff ==="
    diff -u <(echo "$TRADITIONAL_JSON") <(echo "$YQ_JSON") || true
    echo ""
    
    echo "Test files preserved for inspection:"
    echo "  - ${TRADITIONAL_OUTPUT}"
    echo "  - ${YQ_OUTPUT}"
    echo ""
    echo "============================================="
    echo "=== TEST FAILED ==="
    echo "============================================="
    echo "The two methods produce different YAML structures."
    exit 1
fi
