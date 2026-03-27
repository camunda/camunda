#!/usr/bin/env bash

# Validates generated .flattened-pom.xml files contain required Maven Central metadata
# (name, description, url, licenses, developers, scm), and packaging when the source POM is non-jar.

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

validate_xml_well_formed() {
  local file_path="$1"
  local error_output
  if ! error_output="$(xmllint --noout "${file_path}" 2>&1)"; then
    echo "ERROR: ${file_path} is not a well-formed XML file." >&2
    echo "  xmllint output:" >&2
    while IFS= read -r line; do
      echo "    ${line}" >&2
    done <<< "${error_output}"
    return 1
  fi
}

read_xml_value() {
  local file_path="$1"
  local tag_name="$2"
  xmllint --xpath "normalize-space(string(/*[local-name()='project']/*[local-name()='${tag_name}']))" "${file_path}"
}

failures=0
flattened_poms_found=0

while IFS= read -r flattened_pom; do
  flattened_poms_found=$((flattened_poms_found + 1))
  module_dir="$(dirname "${flattened_pom}")"
  source_pom="${module_dir}/pom.xml"

  if [[ ! -f "${source_pom}" ]]; then
    source_pom="pom.xml"
  fi

  if ! validate_xml_well_formed "${flattened_pom}"; then
    failures=$((failures + 1))
    continue
  fi

  if ! validate_xml_well_formed "${source_pom}"; then
    failures=$((failures + 1))
    continue
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

if [[ "${flattened_poms_found}" -eq 0 ]]; then
  echo "Validation failed: no .flattened-pom.xml files were found under: ${search_roots[*]}" >&2
  echo "  Fix: ensure flattening runs before validation (e.g. process-resources with flatten plugin enabled)." >&2
  exit 1
fi

if [[ "${failures}" -gt 0 ]]; then
  echo "Validation failed: ${failures} flattened POM file(s) are missing required metadata." >&2
  exit 1
fi

echo "Validation passed: all flattened POM files contain required Maven Central metadata."
