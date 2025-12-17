#!/bin/bash

# Contains OS specific sed function
. utils.sh

set -exo pipefail

if [ -z "$1" ]
then
  echo "Please provide a namespace name!"
  exit 1
fi

### Load test helper script
### First parameter is used as namespace name (load test name)
### Creates:
### 1. A K8 namespace
### 2. A new folder with setup files copied from default
### 3. A values file for customization

namespace=$1

# Create the K8 namespace if it doesn't exist
if ! kubectl get namespace "$namespace" >/dev/null 2>&1; then
  kubectl create namespace "$namespace"
else
  echo "Namespace '$namespace' already exists"
fi

# Label the namespace with author (current user)
author=${USER:-unknown}
kubectl label namespace "$namespace" created-by="$author" --overwrite

# Label the namespace with TTL (default 7 days)
ttl_days=${TTL_DAYS:-7}
# Calculate deadline date with the TTL days from now
# Use basic ISO date format (YYYY-MM-DD), as it is compatible with kubectl label values
if [[ "$OSTYPE" == "darwin"* ]]; then
  # macOS date command
  deadlineDate=$(date -v +"${ttl_days}"d +%Y-%m-%d)
else
  # Linux date command
  deadlineDate=$(date -d "+${ttl_days} days" +%Y-%m-%d)
fi
kubectl label namespace "$namespace" deadline-date="$deadlineDate" --overwrite

# Copy default setup to new folder
cp -rv default/ "$namespace"
cd "$namespace"

# Update Makefile with the namespace name
sed_inplace "s/default/$namespace/g" Makefile

# Copy the camunda-platform-values.yaml to the new folder
cp ../camunda-platform-values.yaml .

echo "Load test setup created successfully in folder: $namespace"
echo "Namespace: $namespace"
echo "Author: $author"
echo "Deadline: $deadlineDate"
echo ""
echo "Next steps:"
echo "1. cd $namespace"
echo "2. Adjust camunda-platform-values.yaml and Makefile if needed"
echo "3. Run 'make install' to deploy the load test"
