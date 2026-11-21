#!/usr/bin/env bash
# owner: @camunda/reliability-testing
#
# Selects the load-tests/setup folders to deploy as release load tests.
#
# Usage: load-test-select-release-folders.sh
#
# Environment:
#   EVENT_NAME   push, or any other value (workflow_dispatch, workflow_call) to use VERSIONS
#   VERSIONS     Comma-separated folder names or minor versions, such as "8.8,8.10,main", or all
#   BEFORE       Push only: SHA of the head before the push
#   SHA          Push only: SHA of the head after the push
#   SETUP_DIR    Optional: path of the setup directory (default: load-tests/setup)
#
# Outputs (GITHUB_OUTPUT):
#   folders      JSON array of the selected folders, in canonical order

set -o nounset
set -o errexit
set -o pipefail

GITHUB_OUTPUT="${GITHUB_OUTPUT:-/dev/stdout}"
SETUP_DIR="${SETUP_DIR:-load-tests/setup}"
EVENT_NAME="${EVENT_NAME:-}"
VERSIONS="${VERSIONS:-}"
BEFORE="${BEFORE:-}"
SHA="${SHA:-}"

ALL_FOLDERS=(main stable-87 stable-88 stable-89 stable-810)
VERSION_FOLDER_REGEX="^${SETUP_DIR}/($(IFS='|'; echo "${ALL_FOLDERS[*]}"))/"
TEST_FOLDER_REGEX="^${SETUP_DIR}/test/"

selected=()

select_all() {
  selected=("${ALL_FOLDERS[@]}")
}

select_from_versions() {
  local token folder f
  local -A wanted=()
  IFS=',' read -ra tokens <<<"${VERSIONS}"
  for token in "${tokens[@]}"; do
    token="$(tr -d '[:space:]' <<<"${token}")"
    [[ -z "${token}" ]] && continue
    case "${token}" in
      all)
        selected=("${ALL_FOLDERS[@]}")
        return
        ;;
      main) folder=main ;;
      stable-87 | stable-88 | stable-89 | stable-810) folder="${token}" ;;
      8.7 | 8.8 | 8.9 | 8.10) folder="stable-${token/./}" ;;
      *)
        echo "::error::Unknown version '${token}'. Use one of: all, main, 8.7, 8.8, 8.9, 8.10, or a folder name (stable-87, stable-88, stable-89, stable-810)."
        exit 1
        ;;
    esac
    wanted[${folder}]=1
  done
  for f in "${ALL_FOLDERS[@]}"; do
    [[ -n "${wanted[${f}]:-}" ]] && selected+=("${f}")
  done
  if [[ ${#selected[@]} -eq 0 ]]; then
    echo "::error::The versions input is empty."
    exit 1
  fi
}

select_from_push() {
  local changed f
  if [[ -z "${BEFORE}" || "${BEFORE}" =~ ^0+$ ]]; then
    echo "No usable 'before' SHA, deploying all folders."
    select_all
    return
  fi
  if ! git cat-file -e "${BEFORE}^{commit}" 2>/dev/null; then
    git fetch --no-tags --depth=1 origin "${BEFORE}" || true
  fi
  if ! changed="$(git diff --name-only "${BEFORE}" "${SHA}" 2>/dev/null)"; then
    echo "Cannot diff ${BEFORE}..${SHA}, deploying all folders."
    select_all
    return
  fi
  echo "Changed files:"
  echo "${changed}"
  # The files of the test folder and the files outside the setup directory are ignored. A file that
  # is not in a version folder is shared by all of them.
  changed="$(grep -E "^${SETUP_DIR}/" <<<"${changed}" | grep -vE "${TEST_FOLDER_REGEX}" || true)"
  if [[ -n "${changed}" ]] && grep -qvE "${VERSION_FOLDER_REGEX}" <<<"${changed}"; then
    echo "A shared file changed, deploying all folders."
    select_all
    return
  fi
  for f in "${ALL_FOLDERS[@]}"; do
    if grep -qE "^${SETUP_DIR}/${f}/" <<<"${changed}"; then
      selected+=("${f}")
    fi
  done
}

if [[ "${EVENT_NAME}" == "push" ]]; then
  select_from_push
else
  select_from_versions
fi

if [[ ${#selected[@]} -eq 0 ]]; then
  folders='[]'
else
  folders="$(printf '%s\n' "${selected[@]}" | jq -R . | jq -sc .)"
fi

echo "folders=${folders}" >>"${GITHUB_OUTPUT}"
echo "Selected folders: ${folders}"
