#!/bin/bash

# Contains OS specific sed function
. utils.sh

set -exo pipefail

if [ -z $1 ]
then
  echo "Please provide a namespace name!"
  exit 1
fi

### Load test helper script
### First parameter is used as namespace name
### For a new namespace a new folder will be created


namespace=$1

# Create namespace if it doesn't exist
if ! kubectl get namespace $namespace >/dev/null 2>&1; then
  kubectl create namespace $namespace
else
  echo "Namespace '$namespace' already exists"
fi

# Label namespace with author (based on git author)
git_author=$(git config user.name || echo "unknown")
kubectl label namespace $namespace created-by="${git_author}" --overwrite

# Label namespace with TTL deadline (default: 7 days from now)
ttl_days=${2:-7}
deadline_date=$(date -d "+${ttl_days} days" +%Y-%m-%d 2>/dev/null || date -v +${ttl_days}d +%Y-%m-%d)
kubectl label namespace $namespace deadline-date="${deadline_date}" --overwrite

# Copy default folder to new namespace folder
cp -rv default/ $namespace

# Copy camunda-platform-values.yaml to the new folder
cp -v ../camunda-platform-values.yaml $namespace/

cd $namespace

# Update Makefile to use the namespace
sed_inplace "s/default/$namespace/g" Makefile

# Add/update helm repositories
helm repo add camunda https://helm.camunda.io/
helm repo add camunda-load-tests https://camunda.github.io/camunda-load-tests-helm/
helm repo update
