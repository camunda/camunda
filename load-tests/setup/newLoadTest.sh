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
### First parameter is used as namespace name
### For a new namespace a new folder will be created


namespace=$1

# Create K8 namespace
if ! kubectl get namespace "$namespace" >/dev/null 2>&1; then
  kubectl create namespace "$namespace"
else
  echo "Namespace '$namespace' already exists"
fi

# Label namespace with author
kubectl label namespace "$namespace" created-by="${USER:-unknown}" --overwrite

# Label namespace with TTL (default 7 days)
TTL=${TTL:-7}
deadlineDate=$(date -d "+${TTL} days" +%Y-%m-%d 2>/dev/null || date -v +"${TTL}"d +%Y-%m-%d)
kubectl label namespace "$namespace" deadline-date="$deadlineDate" --overwrite

# Copy default folder to new namespace folder
cp -rv default/ "$namespace"

# Copy camunda-platform-values.yaml to new folder
cp -v ../camunda-platform-values.yaml "$namespace"/

cd "$namespace"

# calls OS specific sed inplace function
sed_inplace "s/default/$namespace/g" Makefile

# get latest updates from helm repos
helm repo add camunda https://helm.camunda.io/ # skips if already exists
helm repo add camunda-load-tests https://camunda.github.io/camunda-load-tests-helm/ # skips if already exists
helm repo update
