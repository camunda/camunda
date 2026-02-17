#!/bin/bash

set -euo pipefail

# Script to deploy Optimize load tester
# Usage: ./deploy-optimize-load-tester.sh <namespace> <image-tag>
# Example: ./deploy-optimize-load-tester.sh pg-optimize-enabled-bh pg-optimize-enabled-bh-v2-1771317505

if [ -z "${1:-}" ]; then
  echo "Error: Please provide a namespace name!"
  echo "Usage: $0 <namespace> <image-tag>"
  echo "Example: $0 pg-optimize-enabled-bh pg-optimize-enabled-bh-v2-1771317505"
  exit 1
fi

if [ -z "${2:-}" ]; then
  echo "Error: Please provide an image tag!"
  echo "Usage: $0 <namespace> <image-tag>"
  echo "Example: $0 pg-optimize-enabled-bh pg-optimize-enabled-bh-v2-1771317505"
  exit 1
fi

namespace="$1"
image_tag="$2"
script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
template_file="$script_dir/default/values-optimize-starter.yaml"
output_file="$script_dir/optimize-deployment-${namespace}.yaml"

echo "============================================"
echo "Deploying Optimize Load Tester"
echo "============================================"
echo "Namespace: $namespace"
echo "Image Tag: $image_tag"
echo "Template: $template_file"
echo "Output: $output_file"
echo "============================================"

# Check if namespace exists
if ! kubectl get namespace "$namespace" >/dev/null 2>&1; then
  echo "Error: Namespace '$namespace' does not exist!"
  echo "Please create the namespace first or use an existing one."
  exit 1
fi

# Check if template file exists
if [ ! -f "$template_file" ]; then
  echo "Error: Template file not found at $template_file"
  exit 1
fi

# Create deployment YAML by replacing placeholders
echo "Creating deployment configuration..."
sed -e "s|namespace: pg-optimize-enabled-bh|namespace: $namespace|g" \
    -e "s|gcr.io/zeebe-io/optimize-load-tester:.*|gcr.io/zeebe-io/optimize-load-tester:$image_tag|g" \
    -e "s|value: \"http://pg-optimize-enabled-bh\"|value: \"http://$namespace\"|g" \
    -e "s|value: \"http://pg-optimize-enabled-bh-keycloak\"|value: \"http://$namespace-keycloak\"|g" \
    "$template_file" > "$output_file"

echo "✓ Generated deployment file: $output_file"

# Update liveness probe settings (optimized for 10-minute intervals)
echo "Updating liveness probe settings..."
sed -i.bak \
    -e 's/initialDelaySeconds: 60/initialDelaySeconds: 120/g' \
    -e 's/periodSeconds: 30/periodSeconds: 60/g' \
    -e 's/failureThreshold: 3/failureThreshold: 5/g' \
    "$output_file"
rm -f "${output_file}.bak"

echo "✓ Updated liveness probe for 10-minute evaluation intervals"

# Show the configuration
echo ""
echo "============================================"
echo "Deployment Configuration:"
echo "============================================"
grep -A 2 "namespace:" "$output_file" | head -3
grep "image:" "$output_file"
grep "OPTIMIZE_BASE_URL" -A 1 "$output_file" | grep "value:"
grep "OPTIMIZE_KEYCLOAK_URL" -A 1 "$output_file" | grep "value:"
echo "============================================"
echo ""

# Ask for confirmation
read -p "Deploy to namespace '$namespace'? (y/n): " -n 1 -r
echo
if [[ ! $REPLY =~ ^[Yy]$ ]]; then
  echo "Deployment cancelled."
  exit 0
fi

# Delete existing deployment if it exists
if kubectl get deployment optimize-load-tester -n "$namespace" >/dev/null 2>&1; then
  echo "Deleting existing deployment..."
  kubectl delete deployment optimize-load-tester -n "$namespace"
  echo "✓ Deleted existing deployment"
fi

# Apply the deployment
echo "Applying deployment..."
kubectl apply -f "$output_file"

# Wait for deployment to be ready
echo "Waiting for deployment to be ready..."
kubectl wait --for=condition=available --timeout=120s deployment/optimize-load-tester -n "$namespace" || true

# Show pod status
echo ""
echo "============================================"
echo "Pod Status:"
echo "============================================"
kubectl get pods -n "$namespace" -l app=optimize-load-tester

# Show recent logs
echo ""
echo "============================================"
echo "Recent Logs (last 20 lines):"
echo "============================================"
kubectl logs -n "$namespace" -l app=optimize-load-tester --tail=20 2>/dev/null || echo "Logs not available yet, pod may still be starting..."

echo ""
echo "============================================"
echo "Deployment Complete!"
echo "============================================"
echo "Namespace: $namespace"
echo "Image: gcr.io/zeebe-io/optimize-load-tester:$image_tag"
echo ""
echo "Useful commands:"
echo "  View logs:   kubectl logs -n $namespace -l app=optimize-load-tester -f"
echo "  Get pods:    kubectl get pods -n $namespace -l app=optimize-load-tester"
echo "  Delete:      kubectl delete deployment optimize-load-tester -n $namespace"
echo "  Metrics:     kubectl port-forward -n $namespace deployment/optimize-load-tester 9600:9600"
echo "============================================"
