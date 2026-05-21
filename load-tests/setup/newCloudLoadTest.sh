#!/bin/bash
set -exo pipefail

# Contains OS specific sed function
. utils.sh

usage() {
  cat <<'EOF'
Usage: newCloudLoadTest.sh <namespace>

Arguments:
  namespace          Base namespace name. Will be prefixed with "c8-" if missing.

Options:
  -h, --help         Show this help message.

Examples:
  ./newCloudLoadTest.sh demo
  ./newCloudLoadTest.sh c8-demo
EOF
}

if [ "$1" = "-h" ] || [ "$1" = "--help" ]; then
  usage
  exit 0
fi

if [ -z "$1" ]; then
  echo "Error: Missing namespace name."
  usage
  exit 1
fi

### Cloud load test helper script
### First parameter is used as namespace name
### For a new namespace a new folder will be created


namespace=$1

# Add c8- prefix if not present
if [[ ! "$namespace" =~ ^c8- ]]; then
  namespace="c8-$namespace"
  echo "Namespace prefix added: $namespace"
fi

kubectl create namespace $namespace

# Label namespace with registry (required to inject image pull secrets)
kubectl label namespace "$namespace" registry=harbor --overwrite

# Label namespace with author and purpose
git_author=$(compute_git_author)
kubectl label namespace "$namespace" camunda.io/purpose=load-test --overwrite
kubectl label namespace "$namespace" camunda.io/created-by="$git_author" --overwrite

cp -rv cloud-default/ $namespace
cd $namespace


# Update Makefile to use the namespace
sed_inplace "s/__NAMESPACE__/$namespace/" Makefile
sed_inplace "s/__AUTHOR__/$git_author/" *.yaml
