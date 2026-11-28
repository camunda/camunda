#!/usr/bin/env bash
# owner: @camunda/reliability-testing
#
# Compatibility wrapper, to remove once stable/8.10 calls load-test-deploy.sh:
# the stable/8.10 copy of camunda-load-test.yml checks out main and still calls
# this script by its former name.
set -euo pipefail

exec "$(dirname "${BASH_SOURCE[0]}")/load-test-deploy.sh" "$@"
