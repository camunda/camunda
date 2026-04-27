#!/bin/bash

# Contains OS specific sed function
. utils.sh

usage() {
  cat <<'EOF'
Usage: newLoadTest.sh [options] <namespace>

Arguments:
  namespace    Base namespace name. Will be prefixed with "c8-" if missing.

Options:
  -s <type>          Secondary storage type. One of: elasticsearch, opensearch, postgresql, mysql, mariadb, mssql, oracle, none. Default: elasticsearch.
  -t <days>          Namespace TTL in days (positive integer). Default: 1.
  -O                 Disable Optimize. Default: enabled.
  -z                 Deploy across multiple zones. Default: single zone.
  -h                 Show this help message.

Examples:
  ./newLoadTest.sh demo
  ./newLoadTest.sh perf -s opensearch -t 3 -O
EOF
}

secondaryStorage="elasticsearch"
ttl_days=1
enable_optimize="true"
enable_single_zone="true"

while getopts ":hs:t:Oz" opt; do
  case "$opt" in
    h) usage; exit 0 ;;
    s) validate_secondary_storage "$OPTARG" || { usage; exit 1; }; secondaryStorage="$OPTARG" ;;
    t) validate_ttl "$OPTARG" || { usage; exit 1; }; ttl_days="$OPTARG" ;;
    O) enable_optimize="false" ;;
    z) enable_single_zone="false" ;;
    :) echo "Error: Option -$OPTARG requires an argument." >&2; usage; exit 1 ;;
    \?) echo "Error: Unknown option -$OPTARG." >&2; usage; exit 1 ;;
  esac
done
shift $((OPTIND - 1))

if [ -z "$1" ]; then
  echo "Error: Missing namespace name." >&2
  usage
  exit 1
fi

set -exo pipefail

### Load test helper script
### Namespace name is the only required positional argument
### For a new namespace a new folder will be created

helm_chart="camunda-platform-8.10"
namespace="$(namespace_name "$1")"
single_zone_annotation_name="topology.kubernetes.io/zone"
availability_zone="~"

# Create namespace if it doesn't exist
if ! kubectl get namespace $namespace >/dev/null 2>&1; then
  kubectl create namespace "$namespace"
  if [[ "$enable_single_zone" == "true" ]]; then
    availability_zone="$(hashmod_zone "$namespace")"
    kubectl annotate namespace "$namespace" "${single_zone_annotation_name}=${availability_zone}"
    echo "Will configure pods to deploy into the $availability_zone AZ only."
  else
    availability_zone="~"
    echo "Will NOT configure pods to deploy into a single zone."
  fi
else
  echo "Namespace '$namespace' already exists"

  existing_zone="$(kubectl get ns "$namespace" -o json | jq --raw-output ".metadata.annotations[\"$single_zone_annotation_name\"]")"
  availability_zone="$existing_zone"
  echo "Namespace ${namespace} has previously been configured to run on the single availability zone: $availability_zone"
fi


# Label to easily find related namespaces
kubectl label namespace "$namespace" "camunda.io/purpose=load-test" --overwrite

# Label namespace with author (based on git author)
raw_git_author=$(git config user.name || echo "unknown")
git_author=$(sanitize_k8s_label "$raw_git_author")
kubectl label namespace "$namespace" created-by="$git_author" --overwrite

# Label namespace with TTL deadline
deadline_date="$(new_deadline_date "$ttl_days")"
kubectl label namespace $namespace deadline-date="${deadline_date}" --overwrite

# Label namespace with registry (required to inject image pull secrets)
kubectl label namespace "$namespace" registry=harbor --overwrite

# Copy default folder to new namespace folder
cp -rv default/ $namespace

# Copy all *.yaml files to the new folder
cp -v ../*.yaml $namespace/

cd $namespace

# Update Makefile to use the namespace and secondary storage
sed_inplace "s/__NAMESPACE__/$namespace/" Makefile
sed_inplace "s/__NAMESPACE__/$namespace/" load-test-values.yaml
sed_inplace "s/__STORAGE_TYPE__/$secondaryStorage/" Makefile
sed_inplace "s/__ENABLE_OPTIMIZE__/$enable_optimize/" Makefile
sed_inplace "s/__AVAILABILITY_ZONE__/$availability_zone/" *.yaml databases/*.yaml

# Add/update helm repositories
helm repo add camunda https://helm.camunda.io/ --force-update
helm repo add camunda-load-tests https://camunda.github.io/camunda-load-tests-helm/ --force-update
helm repo add opensearch https://opensearch-project.github.io/helm-charts/ --force-update
helm repo update

# Clone Platform Helm so we can run the latest chart

git clone --depth 1 --branch main --single-branch https://github.com/camunda/camunda-platform-helm.git

# Make deps

helm dependency build "camunda-platform-helm/charts/$helm_chart"

