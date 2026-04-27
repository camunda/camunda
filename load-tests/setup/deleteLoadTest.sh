#!/bin/bash

. utils.sh

usage() {
  cat <<'EOF'
Usage: deleteLoadTest.sh <namespace>

Arguments:
  namespace    Name of the load test namespace to delete.

Options:
  -h           Show this help message.

Examples:
  ./deleteLoadTest.sh c8-demo
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

### Load test helper script
### Namespace name is the only required positional argument
### Given namespace will be completely deleted.

namespace=${1//\//} # remove trailing slashes

kubens default
kubectl delete namespace "$namespace" --wait=false
rm -rf "$namespace"
