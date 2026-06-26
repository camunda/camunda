#!/usr/bin/env bash

# Validates generated .flattened-pom.xml files contain required Maven Central metadata
# (name, description, url, licenses, developers, scm), and packaging when the source POM is non-jar.
#
# Additionally validates Javadoc deployability for jar-packaged modules: Maven Central
# requires every non-pom artifact to ship a *-javadoc.jar. We detect, statically across
# the local parent chain, modules that set maven.javadoc.skip=true (which disables the
# real maven-javadoc-plugin) but do not configure the empty-javadoc-jar placeholder
# execution on maven-jar-plugin. See https://github.com/camunda/camunda/issues/55717.

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

# Reads the value of a <properties><name/></properties> entry from a POM, or empty
# string if absent.
read_pom_property() {
  local file_path="$1"
  local property_name="$2"
  xmllint --xpath "normalize-space(string(/*[local-name()='project']/*[local-name()='properties']/*[local-name()='${property_name}']))" "${file_path}" 2>/dev/null || true
}

# Resolves the source POM of the parent declared in the given POM via its
# <relativePath>. Echoes the absolute path if a parent POM file exists locally,
# otherwise echoes empty. <relativePath> defaults to ../pom.xml when omitted; an
# explicit empty <relativePath/> disables local resolution.
resolve_parent_pom() {
  local file_path="$1"
  local relative_path has_parent has_relative_path
  has_parent="$(xmllint --xpath "count(/*[local-name()='project']/*[local-name()='parent'])" "${file_path}" 2>/dev/null || echo 0)"
  if [[ "${has_parent}" != "1" ]]; then
    return 0
  fi
  # <relativePath> defaults to ../pom.xml when omitted; an explicit empty
  # <relativePath/> disables local resolution. We rely on count() to distinguish
  # "tag absent" from "tag present but empty", since string() returns "" in
  # both cases.
  has_relative_path="$(xmllint --xpath "count(/*[local-name()='project']/*[local-name()='parent']/*[local-name()='relativePath'])" "${file_path}" 2>/dev/null || echo 0)"
  if [[ "${has_relative_path}" == "1" ]]; then
    relative_path="$(xmllint --xpath "string(/*[local-name()='project']/*[local-name()='parent']/*[local-name()='relativePath'])" "${file_path}" 2>/dev/null || echo "")"
    if [[ -z "${relative_path}" ]]; then
      # Explicit empty <relativePath/> means the parent is not local (e.g. resolved from a remote repository).
      return 0
    fi
  else
    relative_path="../pom.xml"
  fi
  local module_dir parent_candidate
  module_dir="$(dirname "${file_path}")"
  parent_candidate="${module_dir}/${relative_path}"
  if [[ -d "${parent_candidate}" ]]; then
    parent_candidate="${parent_candidate%/}/pom.xml"
  fi
  if [[ -f "${parent_candidate}" ]]; then
    # Normalize path so caller comparisons / cycle detection work consistently.
    ( cd "$(dirname "${parent_candidate}")" && printf '%s/%s\n' "$(pwd -P)" "$(basename "${parent_candidate}")" )
  fi
}

# Returns 0 (true) if `maven.javadoc.skip` is set to `true` anywhere in the local
# POM chain (self -> parent -> ...). Stops at the first non-local parent.
javadoc_skip_set_in_chain() {
  local file_path="$1"
  local visited=()
  while [[ -n "${file_path}" && -f "${file_path}" ]]; do
    # Cycle guard.
    local already
    already=0
    for v in "${visited[@]+"${visited[@]}"}"; do
      if [[ "${v}" == "${file_path}" ]]; then
        already=1
        break
      fi
    done
    if [[ "${already}" -eq 1 ]]; then
      break
    fi
    visited+=("${file_path}")
    local skip
    skip="$(read_pom_property "${file_path}" "maven.javadoc.skip")"
    if [[ "${skip}" == "true" ]]; then
      return 0
    fi
    file_path="$(resolve_parent_pom "${file_path}")"
  done
  return 1
}

# Returns 0 (true) if `empty-javadoc-jar` execution is declared on `maven-jar-plugin`
# anywhere in the local POM chain (self -> parent -> ...), in either <build><plugins>
# or <build><pluginManagement><plugins>. Stops at the first non-local parent.
empty_javadoc_jar_declared_in_chain() {
  local file_path="$1"
  local visited=()
  while [[ -n "${file_path}" && -f "${file_path}" ]]; do
    local already
    already=0
    for v in "${visited[@]+"${visited[@]}"}"; do
      if [[ "${v}" == "${file_path}" ]]; then
        already=1
        break
      fi
    done
    if [[ "${already}" -eq 1 ]]; then
      break
    fi
    visited+=("${file_path}")
    local count
    # Match `empty-javadoc-jar` execution on maven-jar-plugin in either
    # <build><plugins> or <build><pluginManagement><plugins>.
    count="$(xmllint --xpath "count(/*[local-name()='project']/*[local-name()='build']//*[local-name()='plugin'][*[local-name()='artifactId']='maven-jar-plugin']//*[local-name()='execution'][*[local-name()='id']='empty-javadoc-jar'])" "${file_path}" 2>/dev/null || echo 0)"
    if [[ "${count}" -gt 0 ]]; then
      return 0
    fi
    file_path="$(resolve_parent_pom "${file_path}")"
  done
  return 1
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

  # Javadoc deployability check: only applies to non-pom modules that will produce
  # a primary jar artifact. Modules that set maven.javadoc.skip=true (in self or any
  # local ancestor POM) skip the real attach-javadocs execution, so they must
  # configure the empty-javadoc-jar placeholder to keep the deployment Maven Central
  # compatible (https://central.sonatype.org/publish/requirements/#supply-javadoc-and-sources).
  if [[ "${source_packaging}" == "jar" ]]; then
    if javadoc_skip_set_in_chain "${source_pom}"; then
      if ! empty_javadoc_jar_declared_in_chain "${source_pom}"; then
        failures=$((failures + 1))
        group_id="$(read_xml_value "${flattened_pom}" "groupId")"
        artifact_id="$(read_xml_value "${flattened_pom}" "artifactId")"
        version="$(read_xml_value "${flattened_pom}" "version")"
        echo "ERROR: pkg:maven/${group_id}/${artifact_id}@${version}: Javadocs must be provided but not found in entries" >&2
        echo "  Module: ${source_pom}" >&2
        echo "  Cause: maven.javadoc.skip=true disables the real javadoc execution but no empty-javadoc-jar placeholder is configured for maven-jar-plugin in the module or any local ancestor POM." >&2
        echo "  Fix: add an empty-javadoc-jar execution to maven-jar-plugin in ${source_pom} (or in a parent POM)." >&2
      fi
    fi
  fi
done < <(find "${search_roots[@]}" -name '.flattened-pom.xml' -type f | sort)

if [[ "${flattened_poms_found}" -eq 0 ]]; then
  echo "Validation failed: no .flattened-pom.xml files were found under: ${search_roots[*]}" >&2
  echo "  Fix: ensure flattening runs before validation (e.g. process-resources with flatten plugin enabled)." >&2
  exit 1
fi

if [[ "${failures}" -gt 0 ]]; then
  echo "Validation failed: ${failures} flattened POM file(s) are missing required metadata or Javadoc configuration." >&2
  exit 1
fi

echo "Validation passed: all flattened POM files contain required Maven Central metadata and Javadoc configuration."
