#!/bin/bash
# Scaffolds a per-namespace folder for a C7 load test, like load-tests/setup/newLoadTest.sh
# (C8). The cluster is unchanged until `make install`.
#
#   ./newLoadTest.sh <name> [ttl_days]
#   cd c7-<name> && make install
set -eo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

if [[ -z "${1:-}" || "$1" == "-h" || "$1" == "--help" ]]; then
  echo "Usage: $0 <namespace> [ttl_days]"
  echo "  namespace  Prefixed with 'c7-' if missing."
  echo "  ttl_days   Namespace TTL in days. Default: 1."
  exit 1
fi

namespace="$1"
[[ "$namespace" == c7-* ]] || namespace="c7-$namespace"
ttl_days="${2:-1}"
[[ "$ttl_days" =~ ^[1-9][0-9]*$ ]] || { echo "ttl_days must be a positive integer" >&2; exit 1; }

if date -v+1d >/dev/null 2>&1; then
  deadline_date="$(date -v+"${ttl_days}"d +%Y-%m-%d)"
else
  deadline_date="$(date -d "+${ttl_days} days" +%Y-%m-%d)"
fi
author="${GIT_AUTHOR:-$(git config user.email 2>/dev/null | cut -d@ -f1 | tr '.' '-')}"

target="$SCRIPT_DIR/$namespace"
if [[ -e "$target" ]]; then
  echo "Folder $target already exists" >&2
  exit 1
fi
mkdir -p "$target"

sed -e "s/__NAMESPACE__/$namespace/" "$SCRIPT_DIR/Makefile.template" > "$target/Makefile"
cat > "$target/values.yaml" <<VALUES
# Overrides for this load test. Defaults: ../charts/c7-load-test/values.yaml
author: "$author"
deadlineDate: "$deadline_date"
topologyZone: ""

global:
  image:
    tag: "${IMAGE_TAG:-7.24.0-SNAPSHOT}"
  # Do not use N2 nodes for C7 (agreed in the workshop). Set the node pool here.
  nodeSelector: {}
  tolerations: []

starter:
  rate: 300

worker:
  replicas: 3
VALUES

echo "Created $target (deadline $deadline_date)."
echo "Next: cd $namespace && make install"
