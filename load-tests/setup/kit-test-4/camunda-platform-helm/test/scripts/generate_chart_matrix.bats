#!/usr/bin/env bats

setup() {
  if ROOT="$(git -C "$here" rev-parse --show-toplevel 2>/dev/null)"; then
    :
  else
    ROOT="$(cd "$here/../.." && pwd)"
  fi
  export ROOT

  # Ensure bats helper libraries are available, otherwise guide the contributor
  support_load="$ROOT/test/test_helper/bats-support/load.bash"
  assert_load="$ROOT/test/test_helper/bats-assert/load.bash"
  if [ ! -f "$support_load" ] || [ ! -f "$assert_load" ]; then
    echo "Missing Bats helper libraries (bats-support / bats-assert)."
    echo "  git clone https://github.com/bats-core/bats-support test/test_helper/bats-support"
    echo "  git clone https://github.com/bats-core/bats-assert  test/test_helper/bats-assert"
    skip "Test helpers missing; see instructions above. $support_load"
  fi
  load "$ROOT/test/test_helper/bats-support/load"
  load "$ROOT/test/test_helper/bats-assert/load"

  export GITHUB_OUTPUT=/dev/null
  export AV="$(yq '.camundaVersions.supportStandard | join(" ")' "$ROOT/charts/chart-versions.yaml")"
  export TMPDIR_TEST="$(mktemp -d)"
  cd "$TMPDIR_TEST"
}

teardown() {
  rm -rf "$TMPDIR_TEST"
}

get_first_version() {
  echo "$AV" | awk '{print $1}'
}

@test "manual all builds a non-empty matrix" {
  run bash "$ROOT/scripts/generate-chart-matrix.sh" \
    --manual-trigger all \
    --active-versions "$AV"
  [ "$status" -eq 0 ]
  run yq '.matrix | length' matrix_versions.txt
  [ "$status" -eq 0 ]
  [ "$output" -ge 1 ]
}

@test "manual single version limits matrix to that version" {
  v="$(get_first_version)"
  run bash "$ROOT/scripts/generate-chart-matrix.sh" \
    --manual-trigger "$v" \
    --active-versions "$AV"
  [ "$status" -eq 0 ]
  run bash -c 'yq -o=json ".matrix | [.[] | .version] | unique | sort" matrix_versions.txt | jq -c'
  assert_success
  assert_output "[$v]"
}

@test "changed-files for a single chart only includes that version" {
  v="$(get_first_version)"
  chart_dir="$ROOT/charts/camunda-platform-$v"
  changed_file="$chart_dir/templates/any.yaml"
  run bash "$ROOT/scripts/generate-chart-matrix.sh" \
    --manual-trigger none \
    --active-versions "$AV" \
    --all-modified-files "$changed_file"
  [ "$status" -eq 0 ]
  run bash -c 'yq -o=json ".matrix | [.[] | .version] | unique | sort" matrix_versions.txt | jq -c'
  assert_success
  assert_output "[$v]"
}


@test "non-chart changes produce an empty matrix and succeed" {
  export GITHUB_OUTPUT="$TMPDIR_TEST/github_output"
  : > "$GITHUB_OUTPUT"

  # Simulate a PR that only changes tooling/scripts paths, not charts.
  run bash "$ROOT/scripts/generate-chart-matrix.sh" \
    --manual-trigger none \
    --active-versions "$AV" \
    --all-modified-files "scripts/camunda-core"
  assert_success

  # The script should emit an empty JSON matrix for downstream jobs to skip.
  run bash -c 'grep -E "^matrix=\{\\\"include\\\":\[\]\}$" "$GITHUB_OUTPUT"'
  assert_success
}


@test "manual flow single value applies to all entries" {
  v="$(get_first_version)"
  run bash "$ROOT/scripts/generate-chart-matrix.sh" \
    --manual-trigger "$v" \
    --active-versions "$AV" \
    --manual-flow "upgrade-minor"
  [ "$status" -eq 0 ]
  run bash -c 'yq -o=json ".matrix | [.[] | .flow] | unique | sort" matrix_versions.txt | jq -c'
  assert_success
  assert_output "[\"upgrade-minor\"]"
}

@test "manual flow multiple values produce entries for each flow" {
  v="$(get_first_version)"
  run bash "$ROOT/scripts/generate-chart-matrix.sh" \
    --manual-trigger "$v" \
    --active-versions "$AV" \
    --manual-flow "install,upgrade-patch"
  [ "$status" -eq 0 ]
  run bash -c 'yq -o=json ".matrix | [.[] | .flow] | unique | sort" matrix_versions.txt | jq -c'
  assert_success
  assert_output '["install","upgrade-patch"]'
}

@test "invalid manual flow causes failure" {
  v="$(get_first_version)"
  run bash "$ROOT/scripts/generate-chart-matrix.sh" \
    --manual-trigger "$v" \
    --active-versions "$AV" \
    --manual-flow "bogus-flow"
  [ "$status" -ne 0 ]
  [[ "$output" == *"Invalid flow"* ]]
}

@test "upgrade-minor is filtered for versions <= 8.7" {
  # Find a version <= 8.7 in active versions; skip if none
  v_old=""
  for v in $AV; do
    maj="$(echo "$v" | awk -F. '{print $1}')"
    min="$(echo "$v" | awk -F. '{print $2}')"
    if [ "$maj" -lt 8 ] || { [ "$maj" -eq 8 ] && [ "$min" -le 7 ]; }; then
      v_old="$v"
      break
    fi
  done
  if [ -z "$v_old" ]; then
    skip "No active version <= 8.7 available"
  fi
  run bash "$ROOT/scripts/generate-chart-matrix.sh" \
    --manual-trigger "$v_old" \
    --active-versions "8.7" \
    --manual-flow "install,upgrade-patch,upgrade-minor"
  [ "$status" -eq 0 ]
  run bash -c 'yq -o=json ".matrix | [.[] | .flow] | unique | sort" matrix_versions.txt | jq -c'
  assert_success
  # Only install and upgrade-patch should remain
  assert_output '["install","upgrade-patch"]'

}

@test "upgrade-patch is filtered for version == 8.9 via YAML config" {
  # Ensure 8.9 is among active versions; skip if not present
  if ! printf "%s\n" $AV | grep -q '^8\.9$'; then
    skip "8.9 not available in active versions"
  fi
  run bash "$ROOT/scripts/generate-chart-matrix.sh" \
    --manual-trigger "8.9" \
    --active-versions "$AV" \
    --manual-flow "install,upgrade-patch,upgrade-minor"
  assert_success
  run bash -c 'yq -o=json ".matrix | [.[] | .flow] | unique | sort" matrix_versions.txt | jq -c'
  assert_success
  # Only install and upgrade-minor should remain
  assert_output '["install","upgrade-minor"]'
}


@test "upgrade-patch is skipped for keycloak-original scenario (8.7)" {
  # Ensure 8.7 is among active versions; skip if not present
  if ! printf "%s\n" $AV | grep -q '^8\.7$'; then
    skip "8.7 not available in active versions"
  fi
  run bash "$ROOT/scripts/generate-chart-matrix.sh" \
    --manual-trigger "8.7" \
    --active-versions "$AV"
  assert_success
  run bash -c 'yq -o=json ".matrix[] | select(.scenario==\"keycloak-original\") | .flow" matrix_versions.txt | jq -s -c'
  assert_success
  # Only install should remain for keycloak-original
  assert_output '["install"]'
}

@test "upgrade-patch is skipped for keycloak-mt even with manual flow" {
  # Ensure 8.8 is among active versions; skip if not present
  if ! printf "%s\n" $AV | grep -q '^8\.8$'; then
    skip "8.8 not available in active versions"
  fi
  run bash "$ROOT/scripts/generate-chart-matrix.sh" \
    --manual-trigger "8.8" \
    --active-versions "$AV" \
    --manual-flow "install,upgrade-patch"
  assert_success
  run bash -c 'yq -o=json ".matrix[] | select(.scenario==\"keycloak-mt\") | .flow" matrix_versions.txt | jq -s -c'
  assert_success
  # Only install should remain for keycloak-mt when manual flow includes upgrade-patch
  assert_output '["install"]'
}

