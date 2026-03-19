#!/usr/bin/env bash

set -euo pipefail

if ! command -v xmllint >/dev/null 2>&1; then
  echo "ERROR: xmllint is required but not available in PATH." >&2
  exit 1
fi

if [[ "$#" -eq 0 ]]; then
  search_roots=(.)
else
  search_roots=("$@")
fi

required_tags=(name description url licenses developers scm)

read_xml_value() {
  local file_path="$1"
  local tag_name="$2"
  xmllint --xpath "string(/*[local-name()='project']/*[local-name()='${tag_name}'])" "${file_path}" 2>/dev/null || true
}

failures=0

while IFS= read -r flattened_pom; do
  module_dir="$(dirname "${flattened_pom}")"
  source_pom="${module_dir}/pom.xml"

  if [[ ! -f "${source_pom}" ]]; then
    source_pom="pom.xml"
  fi

  missing_tags=()
  for tag in "${required_tags[@]}"; do
    value="$(read_xml_value "${flattened_pom}" "${tag}")"
    if [[ -z "${value}" ]]; then
      missing_tags+=("${tag}")
    fi
  done

  source_packaging="$(read_xml_value "${source_pom}" "packaging")"
  if [[ -z "${source_packaging}" ]]; then
    source_packaging="jar"
  fi

  flattened_packaging="$(read_xml_value "${flattened_pom}" "packaging")"
  if [[ "${source_packaging}" != "jar" && -z "${flattened_packaging}" ]]; then
    missing_tags+=("packaging")
  fi

  if [[ "${#missing_tags[@]}" -gt 0 ]]; then
    failures=$((failures + 1))
    joined_missing="$(printf '%s, ' "${missing_tags[@]}")"
    joined_missing="${joined_missing%, }"
    echo "ERROR: ${flattened_pom} is missing required metadata: ${joined_missing}" >&2
    echo "  Fix: add/adjust metadata in ${source_pom} so flatten produces required values." >&2
  fi
done < <(find "${search_roots[@]}" -name '.flattened-pom.xml' -type f | sort)

if [[ "${failures}" -gt 0 ]]; then
  echo "Validation failed: ${failures} flattened POM file(s) are missing required metadata." >&2
  exit 1
fi

echo "Validation passed: all flattened POM files contain required Maven Central metadata."
