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

helm_chart="camunda-platform-8.9"
namespace="$1"

# Validate secondaryStorage value
secondaryStorage="${2:-elasticsearch}"
if [[ "$secondaryStorage" != "elasticsearch" && "$secondaryStorage" != "opensearch" && "$secondaryStorage" != "postgresql" ]]; then
  echo "Error: Invalid secondary storage type '$secondaryStorage'"
  echo "Allowed values are: elasticsearch, opensearch, postgresql"
  exit 1
fi

# Validate TTL value
ttl_days="${3:-7}"
numberRegex='^[0-9]+$'
if ! [[ $ttl_days =~ $numberRegex ]] ; then
   echo "Error: TTL '$ttl_days' is not a number"
   exit 1
fi

# Validate enable_optimize value
enable_optimize="${4:-false}"
enable_optimize=$(echo "$enable_optimize" | tr '[:upper:]' '[:lower:]')
if [[ "$enable_optimize" != "true" && "$enable_optimize" != "false" ]]; then
  echo "Error: Invalid enable_optimize value '$enable_optimize'"
  echo "Allowed values are: true or false"
  exit 1
fi

# Create namespace if it doesn't exist
if ! kubectl get namespace $namespace >/dev/null 2>&1; then
  kubectl create namespace $namespace
else
  echo "Namespace '$namespace' already exists"
fi

# Sanitize a string to be a valid Kubernetes label value
sanitize_k8s_label() {
  local value="$1"
  # Replace invalid characters with hyphens
  value=$(echo "$value" | sed 's/[^A-Za-z0-9_.-]/-/g')
  # Remove leading non-alphanumeric characters
  value=$(echo "$value" | sed 's/^[^A-Za-z0-9]\+//')
  # Remove trailing non-alphanumeric characters
  value=$(echo "$value" | sed 's/[^A-Za-z0-9]\+$//')
  # Truncate to 63 characters as required by Kubernetes label values
  value=${value:0:63}
  # Fallback if the result is empty
  if [ -z "$value" ]; then
    value="unknown"
  fi
  echo "$value"
}

# Label namespace with author (based on git author)
raw_git_author=$(git config user.name || echo "unknown")
git_author=$(sanitize_k8s_label "$raw_git_author")
kubectl label namespace "$namespace" created-by="$git_author" --overwrite

# Label namespace with TTL deadline (default: 7 days from now)
# Try GNU date format first (Linux), then BSD/macOS format
if deadline_date=$(date -d "+${ttl_days} days" +%Y-%m-%d 2>/dev/null); then
  : # GNU date succeeded
elif deadline_date=$(date -v +${ttl_days}d +%Y-%m-%d 2>/dev/null); then
  : # BSD/macOS date succeeded
else
  echo "Warning: Could not calculate deadline date. Supported on Linux and macOS only."
  deadline_date="unknown"
fi
kubectl label namespace $namespace deadline-date="${deadline_date}" --overwrite

# Copy default folder to new namespace folder
cp -rv default/ $namespace

# Copy camunda-platform-values*.yaml files to the new folder
cp -v ../camunda-platform-values*.yaml $namespace/
cp -v ../secondary-storage-values*.yaml $namespace/

# Copy Prometheus ElasticSearch Exporter values.yaml to the new folder
cp -v ../prometheus-elasticsearch-exporter-values.yaml $namespace/

cd $namespace

# Update Makefile to use the namespace and secondary storage
sed_inplace "s/__NAMESPACE__/$namespace/" Makefile
sed_inplace "s/__STORAGE_TYPE__/$secondaryStorage/" Makefile
sed_inplace "s/__ENABLE_OPTIMIZE__/$enable_optimize/" Makefile

# Add/update helm repositories
helm repo add camunda https://helm.camunda.io/ --force-update
helm repo add camunda-load-tests https://camunda.github.io/camunda-load-tests-helm/ --force-update
helm repo add opensearch https://opensearch-project.github.io/helm-charts/ --force-update
helm repo update

# Clone Platform Helm so we can run the latest chart

git clone --depth 1 --branch main --single-branch https://github.com/camunda/camunda-platform-helm.git

# Make deps

helm dependency build "camunda-platform-helm/charts/$helm_chart"

