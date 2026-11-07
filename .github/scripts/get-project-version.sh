#!/usr/bin/env bash

# Prints the Maven project version of the root POM, read straight from the file.
#
# Resolving this through `mvnw exec:exec` needs the Maven distribution and the
# exec plugin, so a registry blip fails the step -- and because callers capture
# stdout, Maven's own error text is swallowed and the run is undiagnosable.
# See https://github.com/camunda/camunda/actions/runs/41465209492.
#
# The root POM carries no <version> of its own; it inherits the one declared in
# its <parent> block. Both facts are asserted below, so a restructured POM fails
# loudly here instead of silently yielding an empty image tag.

set -euo pipefail

pom="${1:-pom.xml}"

if [[ ! -f "$pom" ]]; then
  echo "ERROR: ${pom} not found (run from the repository root)." >&2
  exit 1
fi

# Direct children of <project> are indented by two spaces; the <parent> block's
# own children by four.
if sed '/^  <parent>/,/^  <\/parent>/d' "$pom" | grep -q '^  <version>'; then
  echo "ERROR: ${pom} now declares its own <version>; update $(basename "$0")." >&2
  exit 1
fi

version=$(sed -n '/^  <parent>/,/^  <\/parent>/s|^ *<version>\(.*\)</version> *$|\1|p' "$pom")

if [[ ! "$version" =~ ^[0-9]+\.[0-9]+\.[0-9]+(-[0-9A-Za-z.]+)?$ ]]; then
  echo "ERROR: no usable version in the <parent> block of ${pom} (read: '${version}')." >&2
  exit 1
fi

echo "$version"
