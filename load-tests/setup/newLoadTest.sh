#!/bin/bash

# Contains OS specific sed function
# shellcheck source=utils.sh
. "$(dirname "$0")/utils.sh"

set -exo pipefail

if [ -z "$1" ]
then
  echo "Please provide a namespace name!"
  exit 1
fi

### Load test helper script
### First parameter is used as namespace name
### For a new namespace a new folder will be created

namespace=$1
author=${2:-$(whoami)}
ttl=${3:-7}

# Create K8 namespace if it doesn't exist
if ! kubectl get namespace "$namespace" >/dev/null 2>&1; then
  kubectl create namespace "$namespace"
else
  echo "Namespace '$namespace' already exists"
fi

# Label namespace with creator
kubectl label namespace "$namespace" created-by="$author" --overwrite

# Label namespace with deadline (TTL)
# Calculate deadline date with the TTL days from now
# Use basic ISO date format (YYYY-MM-DD), as it is compatible with kubectl label values
deadlineDate=$(date -d "+${ttl} days" +%Y-%m-%d 2>/dev/null || date -v "+${ttl}d" +%Y-%m-%d)
kubectl label namespace "$namespace" deadline-date="$deadlineDate" --overwrite

# Copy default folder to new namespace-named folder
cp -rv default/ "$namespace"

# Copy camunda-platform-values.yaml to the new folder
cp -v ../camunda-platform-values.yaml "$namespace/"

cd "$namespace"

# calls OS specific sed inplace function to update namespace in Makefile
sed_inplace "s/namespace ?= default/namespace ?= $namespace/g" Makefile

# Add Camunda helm repos
helm repo add camunda https://helm.camunda.io/ || true
helm repo add camunda-load-tests https://camunda.github.io/camunda-load-tests-helm/ || true
helm repo update

echo ""
echo "Load test namespace '$namespace' created successfully!"
echo "  - Namespace labeled with: created-by=$author, deadline-date=$deadlineDate"
echo "  - Configuration folder: $namespace/"
echo "  - To deploy: cd $namespace && make deploy"
echo ""
