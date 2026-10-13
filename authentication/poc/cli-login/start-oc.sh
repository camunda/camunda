#!/usr/bin/env bash
# Starts the Orchestration Cluster from the locally built distribution with the PoC config.
# Build it first: ./mvnw install -pl dist -am -Dquickly -T1C
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "$HERE/../../.." && pwd)"
DIST="${CAMUNDA_DIST:-$REPO_ROOT/dist/target/camunda-zeebe}"
DATA_DIR="${CAMUNDA_DATA_DIR:-$HERE/.oc-data}"

if [ ! -x "$DIST/bin/camunda" ]; then
  echo "No distribution at $DIST - build it with: ./mvnw install -pl dist -am -Dquickly -T1C" >&2
  exit 1
fi

mkdir -p "$DATA_DIR"
cd "$DATA_DIR"
exec "$DIST/bin/camunda" \
  --spring.config.additional-location="file:$HERE/oc-application.yaml" \
  --camunda.data.primary-storage.directory="$DATA_DIR/data"
