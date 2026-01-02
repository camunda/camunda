#!/usr/bin/env bash

# Inputs passed in from the calling GitHub Action
MANUAL_TRIGGER="none"
MANUAL_SCENARIO="none"
MANUAL_FLOW="none"
ACTIVE_VERSIONS=""
ALL_MODIFIED_FILES="${ALL_MODIFIED_FILES}"

# Resolve repository root based on this script's location so paths work from anywhere
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
CHARTS_DIR="${REPO_ROOT}/charts"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --manual-trigger)
      MANUAL_TRIGGER="$2"
      shift 2
      ;;
    --manual-scenario)
      MANUAL_SCENARIO="$2"
      shift 2
      ;;
    --manual-flow)
      MANUAL_FLOW="$2"
      shift 2
      ;;
    --active-versions)
      ACTIVE_VERSIONS="$2"
      shift 2
      ;;
    --all-modified-files)
      ALL_MODIFIED_FILES="$2"
      shift 2
      ;;
    *)
      echo "Unknown argument: $1" >&2
      exit 2
      ;;
  esac
done

# Compare two versions (major.minor). Echo -1 if a<b, 0 if equal, 1 if a>b
version_compare() {
  local a="$1" b="$2"
  local a_major a_minor b_major b_minor
  a_major="$(echo "$a" | awk -F. '{print $1}')"
  a_minor="$(echo "$a" | awk -F. '{print $2}')"
  b_major="$(echo "$b" | awk -F. '{print $1}')"
  b_minor="$(echo "$b" | awk -F. '{print $2}')"
  if [ -z "$a_minor" ]; then a_minor=0; fi
  if [ -z "$b_minor" ]; then b_minor=0; fi
  if [ "$a_major" -lt "$b_major" ]; then echo -1; return; fi
  if [ "$a_major" -gt "$b_major" ]; then echo 1; return; fi
  if [ "$a_minor" -lt "$b_minor" ]; then echo -1; return; fi
  if [ "$a_minor" -gt "$b_minor" ]; then echo 1; return; fi
  echo 0
}

# Evaluate op (one of ==,>=,>,<=,<) between version and target. Returns 0 if true
version_op_eval() {
  local version="$1" op="$2" target="$3"
  local cmp
  cmp="$(version_compare "$version" "$target")"
  case "$op" in
    '==') [ "$cmp" -eq 0 ] ;;
    '>=') [ "$cmp" -ge 0 ] ;;
    '>')  [ "$cmp" -gt 0 ] ;;
    '<=') [ "$cmp" -le 0 ] ;;
    '<')  [ "$cmp" -lt 0 ] ;;
    *)    return 1 ;;
  esac
}

# Determine if a flow is permitted for a given Camunda version based on config file
is_flow_permitted() {
  local flow="$1"; shift
  local version="$1"; shift
  local config_file="$1"

  if [ -f "$config_file" ]; then
    local rules_json rule match_expr op target deny_csv rules_count i
    rules_json="$(yq -o=json '.rules // []' "$config_file")"
    rules_count="$(echo "$rules_json" | jq 'length')"
    if [ "$rules_count" -eq 0 ]; then
      return 0
    fi
    for i in $(seq 0 $((rules_count - 1))); do
      rule="$(echo "$rules_json" | jq -rc ".[$i]")"
      match_expr="$(echo "$rule" | jq -r '.match // ""')"
      deny_csv="$(echo "$rule" | jq -r '.deny // [] | join(",")')"
      # Extract operator and target version
      target="$(echo "$match_expr" | sed 's/[^0-9.]*\([0-9][0-9]*\.[0-9][0-9]*\).*/\1/')"
      op="$(echo "$match_expr" | sed "s/$target//" | tr -d ' ')"
      [ -z "$op" ] && op='=='
      if version_op_eval "$version" "$op" "$target"; then
        case ",$deny_csv," in
          *",$flow,"*) return 1 ;;
        esac
      fi
    done
    return 0
  else
    # Fallback to legacy hardcoded rule
    if [ "$flow" = "upgrade-minor" ]; then
      local v_major v_minor
      v_major="$(echo "$version" | awk -F. '{print $1}')"
      v_minor="$(echo "$version" | awk -F. '{print $2}')"
      if [ "$v_major" -lt 8 ] || { [ "$v_major" -eq 8 ] && [ "$v_minor" -le 7 ]; }; then
        return 1
      fi
    fi
    return 0
  fi
}

write_matrix_entry() {
  local camunda_version="$1"
  local chart_dir="$2"
  echo "⭐ Generating matrix for $camunda_version and chart $chart_dir"
  if [ -f "$chart_dir/test/ci-test-config.yaml" ]; then
    camunda_version_previous="$(echo "$camunda_version" | awk -F. '{printf "%d.%d", $1, $2-1}')"
    chart_version_previous="$(yq '.version' "${CHARTS_DIR}/camunda-platform-${camunda_version_previous}/Chart.yaml")"
    declare -A used_shortnames=()

    readarray prScenarios < <(yq e -o=j -I=0 '.integration.case.pr.scenario.[]' $chart_dir/test/ci-test-config.yaml)
    for prScenario in "${prScenarios[@]}"; do
      enabled=$(echo "$prScenario" | yq e '.enabled' -)
      if [ "$enabled" = "false" ]; then
        continue
      fi
      if [[ "${MANUAL_SCENARIO}" != "none" && "${MANUAL_SCENARIO}" != "all" ]]; then
        echo "$(echo "$prScenario" | yq e '.name' -)"
        if [[ "${MANUAL_SCENARIO}" != "$(echo "$prScenario" | yq e '.name' -)" ]]; then
          continue
        fi
      fi
      # If manual flow is set, use it. Otherwise, use the flow from the scenario.
      if [[ -n "${MANUAL_FLOW}" && "${MANUAL_FLOW}" != "none" ]]; then
        flows_raw="${MANUAL_FLOW}"
      else
        flows_raw=$(echo "$prScenario" | yq e -r '.flow' -)
        if [ -z "$flows_raw" ] || [ "$flows_raw" = "null" ]; then
          flows_raw="install"
        fi
      fi
      if [ -z "$flows_raw" ] || [ "$flows_raw" = "null" ]; then
        flows_raw="install"
      fi
      scenario_name=$(echo "$prScenario" | yq e -r '.name' -)
      IFS=',' read -r -a flow_items <<< "$flows_raw"
      for flow_item in "${flow_items[@]}"; do
        flow_trimmed=$(echo "$flow_item" | sed 's/^ *//;s/ *$//')
        if [ -z "$flow_trimmed" ] || [ "$flow_trimmed" = "null" ]; then
          flow_trimmed="install"
        fi
        # Filter out upgrade-patch for keycloak-original and keycloak-mt scenarios because the templates on the released chart don't support custom realm bootstrapping.
        if [ "$flow_trimmed" = "upgrade-patch" ] && { [ "$scenario_name" = "keycloak-original" ] || [ "$scenario_name" = "keycloak-mt" ]; }; then
          continue
        fi
        # This needs to be fixed by setting up the client in the ENTRA
        if [ "$flow_trimmed" = "upgrade-minor" ] && { [ "$scenario_name" = "oidc" ]; }; then
          continue
        fi
        # Filter flows according to YAML config rules (fallback to legacy rules if config absent)
        config_file="${REPO_ROOT}/.github/config/permitted-flows.yaml"
        if ! is_flow_permitted "$flow_trimmed" "$camunda_version" "$config_file"; then
          continue
        fi
        case "$flow_trimmed" in
          install | upgrade-patch | upgrade-minor) ;;
          *)
            echo "❌ Invalid flow '$flow_trimmed'. Valid flows: install, upgrade-patch, upgrade-minor. We do have a flow called modular-upgrade-minor.. however this can only be called directly on integration-test-template.yaml." >&2
            exit 1
            ;;
        esac
        base_shortname=$(echo "$prScenario" | yq e -r '.shortname' -)
        flow_slug=$(echo "$flow_trimmed" | tr '[:upper:]' '[:lower:]' | tr -cs 'a-z0-9' '-' | sed 's/^-\+//;s/-\+$//')
        shortname="${base_shortname}-${flow_slug}"
        if [[ -v used_shortnames[$shortname] ]]; then
          count=${used_shortnames[$shortname]}
          count=$((count + 1))
          used_shortnames[$shortname]=$count
          shortname="${shortname}-${count}"
        else
          used_shortnames[$shortname]=1
        fi
        echo "  - version: ${camunda_version}" >> matrix_versions.txt
        echo "    camundaVersionPrevious: $(echo "$camunda_version_previous")" >> matrix_versions.txt
        echo "    case: pr" >> matrix_versions.txt
        echo "    scenario: $(echo "$prScenario" | yq e '.name' -)" >> matrix_versions.txt
        echo "    shortname: $(echo "$prScenario" | yq e '.shortname' -)" >> matrix_versions.txt
        echo "    auth: $(echo "$prScenario" | yq e '.auth' -)" >> matrix_versions.txt
        echo "    flow: ${flow_trimmed}" >> matrix_versions.txt
        echo "    exclude: $(echo "$prScenario" | yq e '.exclude | join("|")' -)" >> matrix_versions.txt
      done
    done
    sed -i -e '$s/,$/]\n/' matrix_versions.txt
  fi
}

echo "Checking for manual-trigger"
touch matrix_versions.txt
echo "matrix:" > matrix_versions.txt
if [[ "${MANUAL_TRIGGER}" == "all" ]]; then
  echo "Requested to build all"
  for camunda_version in ${ACTIVE_VERSIONS}; do
    chart_dir="${CHARTS_DIR}/camunda-platform-${camunda_version}"
    write_matrix_entry "$camunda_version" "$chart_dir"
  done
elif [[ "${MANUAL_TRIGGER}" != "none" && "${MANUAL_TRIGGER}" != "" ]]; then
  echo "Manual trigger detected: ${MANUAL_TRIGGER}"
  chart_dir="${CHARTS_DIR}/camunda-platform-${MANUAL_TRIGGER}"
  if [ -d "$chart_dir" ]; then
    camunda_version="${MANUAL_TRIGGER}"
    write_matrix_entry "$camunda_version" "$chart_dir"
  else
    echo "Chart directory $chart_dir does not exist. Aborting."
    exit 1
  fi
else
  echo "Setting matrix based on changed files"
  echo "Changed files:"
  printf "%s\n" ${ALL_MODIFIED_FILES}

  # Directories/patterns that trigger building all chart versions when changed
  # Format: "pattern;;exclude_pattern;;description"
  # Use empty string for exclude_pattern if no exclusion is needed
  # Note: We use ';;' as delimiter to avoid conflicts with '|' in regex patterns
  BUILD_ALL_TRIGGERS=(
    '\.github/(workflows|actions);;.github/workflows or .github/actions'
    '\.github/config;;\.github/config/release-please;;.github/config (excluding release-please)'
    'scripts/deploy-camunda/;;scripts/deploy-camunda/'
  )

  build_all_triggered=false
  for trigger in "${BUILD_ALL_TRIGGERS[@]}"; do
    IFS=';;' read -r pattern exclude_pattern description <<< "$trigger"
    if echo "${ALL_MODIFIED_FILES}" | grep -qE "$pattern"; then
      # Check exclusion pattern if specified
      if [ -n "$exclude_pattern" ] && echo "${ALL_MODIFIED_FILES}" | grep -qE "$exclude_pattern"; then
        # All matches are in the excluded path, skip this trigger
        # Check if there are matches outside the exclusion
        if ! echo "${ALL_MODIFIED_FILES}" | grep -E "$pattern" | grep -qvE "$exclude_pattern"; then
          continue
        fi
      fi
      echo "Changes in ${description} detected — building all chart versions"
      for camunda_version in ${ACTIVE_VERSIONS}; do
        chart_dir="${CHARTS_DIR}/camunda-platform-${camunda_version}"
        write_matrix_entry "$camunda_version" "$chart_dir"
      done
      build_all_triggered=true
      break
    fi
  done

  # If no global trigger matched, only rebuild the affected charts
  if [ "$build_all_triggered" = false ]; then
    for camunda_version in ${ACTIVE_VERSIONS}; do
      if [[ $(echo ${ALL_MODIFIED_FILES} | grep "charts/camunda-platform-${camunda_version}") ]]; then
        chart_dir="${CHARTS_DIR}/camunda-platform-${camunda_version}"
        write_matrix_entry "$camunda_version" "$chart_dir"
      fi
    done
  fi
fi
cat matrix_versions.txt

if [ "$(cat matrix_versions.txt)" = "matrix:" ]; then
  echo "No matching chart changes detected; emitting empty matrix and skipping downstream jobs."
  echo 'matrix={"include":[]}' | tee -a "${GITHUB_OUTPUT:-/dev/null}"
  exit 0
fi

matrix="$(cat matrix_versions.txt | yq -o=json '.matrix' | jq -c '{ "include": . }' \
  | jq -c 'walk(if type == "number" then tostring else . end)')"
echo "matrix=${matrix}" | tee -a $GITHUB_OUTPUT
