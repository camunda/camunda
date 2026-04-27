#!/bin/bash

# Contains OS specific sed function
. utils.sh

usage() {
  cat <<'EOF'
Usage: newCloudLoadTest.sh <namespace>

Arguments:
  namespace    Base namespace name. Will be prefixed with "c8-" if missing.

Options:
  -h           Show this help message.

Examples:
  ./newCloudLoadTest.sh demo
  ./newCloudLoadTest.sh c8-demo
EOF
}

while getopts ":h" opt; do
  case "$opt" in
    h) usage; exit 0 ;;
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

### Cloud load test helper script
### Namespace name is the only required positional argument
### For a new namespace a new folder will be created

namespace="$(namespace_name "$1")"

kubectl create namespace $namespace

# Label namespace with registry (required to inject image pull secrets)
kubectl label namespace "$namespace" registry=harbor --overwrite

cp -rv cloud-default/ $namespace
cd $namespace

# Update Makefile to use the namespace
sed_inplace "s/__NAMESPACE__/$namespace/" Makefile
