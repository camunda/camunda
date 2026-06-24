#!/bin/bash
# Checks that every Maven module with both junit:junit and junit-jupiter-engine
# also has junit-vintage-engine. Without it, JUnit 4 tests are silently skipped
# because the JUnit Platform provider doesn't discover them.
set -euo pipefail

ROOT_DIR="${1:-.}"
FAILED=0

while IFS= read -r -d '' pom; do
  has_junit4=$(grep -c '<artifactId>junit</artifactId>' "$pom" || true)
  has_jupiter_engine=$(grep -c '<artifactId>junit-jupiter-engine</artifactId>' "$pom" || true)
  has_vintage_engine=$(grep -c '<artifactId>junit-vintage-engine</artifactId>' "$pom" || true)

  if [[ "$has_junit4" -gt 0 && "$has_jupiter_engine" -gt 0 && "$has_vintage_engine" -eq 0 ]]; then
    echo "ERROR: $pom has junit:junit and junit-jupiter-engine but is missing junit-vintage-engine." >&2
    echo "  JUnit 4 tests will be silently skipped. Add junit-vintage-engine as a test dependency." >&2
    FAILED=1
  fi
done < <(find "$ROOT_DIR" -name pom.xml -not -path '*/target/*' -print0)

if [[ "$FAILED" -eq 1 ]]; then
  exit 1
fi
