#!/usr/bin/env bash
# owner: @camunda/reliability-testing
#
# Writes release image tags into load-tests/setup/<folder>/versions.yaml. An empty
# optimize, identity or connectors tag leaves the current value unchanged.
#
# Usage: load-test-update-versions-file.sh <folder> <orchestration-tag> <optimize-tag> <identity-tag> <connectors-tag> [setup-dir]
#   folder             stable-87, stable-88, stable-89, stable-810 or main
#   orchestration-tag  Camunda tag. For stable-87 it applies to zeebe, zeebeGateway, operate and tasklist.

set -euo pipefail

folder="$1"
orchestration_tag="$2"
optimize_tag="${3:-}"
identity_tag="${4:-}"
connectors_tag="${5:-}"
setup_dir="${6:-load-tests/setup}"

file="${setup_dir}/${folder}/versions.yaml"
if [[ ! -f "${file}" ]]; then
  echo "::error::${file} does not exist" >&2
  exit 1
fi

set_tag() {
  local key="$1" value="$2"
  [[ -z "${value}" ]] && return 0
  VALUE="${value}" yq -i "${key}.image.tag = strenv(VALUE)" "${file}"
  # yq drops the quotes of a string it considers plain; Renovate expects quoted values.
  VALUE="${value}" yq -i "(${key}.image.tag) style = \"double\"" "${file}"
}

if [[ "${folder}" == "stable-87" ]]; then
  for key in .zeebe .zeebeGateway .operate .tasklist; do
    set_tag "${key}" "${orchestration_tag}"
  done
else
  set_tag .orchestration "${orchestration_tag}"
fi
set_tag .optimize "${optimize_tag}"
set_tag .identity "${identity_tag}"
set_tag .connectors "${connectors_tag}"
