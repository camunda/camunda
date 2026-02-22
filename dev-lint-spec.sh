#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"

echo "=== Linting all YAML files ==="
npx @stoplight/spectral lint zeebe/gateway-protocol/src/main/proto/v2/*.yaml \
  --ruleset zeebe/gateway-protocol/.spectral.yaml \
  --fail-severity error

echo "=== Linting rest-api.yaml (resolved refs) ==="
npx @stoplight/spectral lint zeebe/gateway-protocol/src/main/proto/v2/rest-api.yaml \
  --ruleset zeebe/gateway-protocol/.spectral.yaml \
  --fail-severity error

echo "=== All checks passed ==="
