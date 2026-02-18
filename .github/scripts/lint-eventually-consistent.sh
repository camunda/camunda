#!/usr/bin/env bash
# owner: Camunda-Ex #team-camunda-ex on Slack
# DRI: Josh Wulf <josh.wulf@camunda.com>
# Lint: x-eventually-consistent must not be true on command (mutating) operations.
#
# Query operations (search, statistics, get-style reads) correctly use
# x-eventually-consistent: true because they read from eventually-consistent
# projections.  But command operations (create, update, delete, cancel, etc.)
# are synchronous writes — marking them eventually-consistent is incorrect and
# causes SDK generators to emit unnecessary polling wrappers.
#
# This script parses the multi-file OpenAPI spec YAML and fails if any command
# operation has x-eventually-consistent: true.
#
# Usage:  lint-eventually-consistent.sh <spec-directory>
# Example: lint-eventually-consistent.sh zeebe/gateway-protocol/src/main/proto/v2

set -euo pipefail

SPEC_DIR="${1:?Usage: $0 <spec-directory>}"

if ! command -v python3 &>/dev/null; then
  echo "ERROR: python3 is required" >&2
  exit 1
fi

# PyYAML is available in ubuntu-latest runners and most Python installs.
# Install it automatically if missing (e.g. in act containers for local testing ).
python3 -c "import yaml" 2>/dev/null || {
  echo "PyYAML not found, installing..."
  python3 -m pip install --user pyyaml==6.0.3 -q 2>/dev/null \
    || sudo apt-get update -qq && sudo apt-get install -y -qq python3-yaml 2>/dev/null \
    || {
      echo "ERROR: Could not install PyYAML" >&2
      exit 1
    }
}

python3 - "$SPEC_DIR" <<'PYTHON'
import yaml, os, sys

spec_dir = sys.argv[1]
http_methods = ['get', 'post', 'put', 'patch', 'delete', 'head', 'options']

# Methods that are always mutations
mutation_methods = {'put', 'patch', 'delete'}

# POST endpoints matching these patterns are queries, not mutations
query_path_segments = {'/search', '/statistics'}

violations = []

for filename in sorted(os.listdir(spec_dir)):
    if not filename.endswith('.yaml'):
        continue
    filepath = os.path.join(spec_dir, filename)
    with open(filepath) as fh:
        try:
            doc = yaml.safe_load(fh)
        except Exception:
            print(f"ERROR: Failed to parse YAML file '{filepath}': {exc}", file=sys.stderr)  
            print("Should this file be in the specification?")
            print("Contact the Camunda Ex team on Slack and tag Josh Wulf if this task is failing unexpectedly.")
            sys.exit(1)  
    if not doc or not isinstance(doc, dict):
        continue

    # Spec files may have paths at top level or under 'paths'
    paths = doc.get('paths', doc)
    for path_key, path_val in paths.items():
        if not path_key.startswith('/'):
            continue
        if not isinstance(path_val, dict):
            continue
        for method, op in path_val.items():
            if method not in http_methods:
                continue
            if not isinstance(op, dict):
                continue

            ec = op.get('x-eventually-consistent')
            if ec is not True:
                continue

            op_id = op.get('operationId', '?')

            # GET is always a query — x-eventually-consistent: true is fine
            if method == 'get':
                continue

            # PUT, PATCH, DELETE are always mutations
            is_mutation = method in mutation_methods

            # POST: check if it's a query (search/statistics) or a mutation
            if method == 'post' and not is_mutation:
                is_query = any(seg in path_key for seg in query_path_segments)
                if not is_query:
                    is_query = op_id.lower().startswith(('search', 'get'))
                is_mutation = not is_query

            if is_mutation:
                violations.append(f"  {filename}: {method.upper()} {path_key} ({op_id})")

if violations:
    print("ERROR: Command operations must not have x-eventually-consistent: true.")
    print("These operations are detected heuristically as synchronous writes, not eventually-consistent reads:\n")
    for v in violations:
        print(v)
    print(f"\n{len(violations)} violation(s) found.")
    print("To fix: set x-eventually-consistent: false on these operations. \nRefer to https://github.com/camunda/camunda/issues/45968 for more details. \nContact #team-camunda-ex and tag Josh Wulf if this is a false positive.")
    sys.exit(1)
PYTHON

rc=$?
if [ $rc -eq 0 ]; then
  echo "✓ x-eventually-consistent lint passed: no command operations incorrectly annotated."
fi
exit $rc
