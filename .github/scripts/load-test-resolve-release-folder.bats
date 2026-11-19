#!/usr/bin/env bats

# Tests for load-test-resolve-release-folder.sh. They run against a temporary setup directory,
# so no network is needed.

RESOLVE="${BATS_TEST_DIRNAME}/load-test-resolve-release-folder.sh"

setup() {
  SETUP_DIR="${BATS_TEST_TMPDIR}/setup"
  mkdir -p "${SETUP_DIR}/main" "${SETUP_DIR}/stable-87" "${SETUP_DIR}/stable-88" "${SETUP_DIR}/stable-810"
}

@test "should map a patch tag to its stable folder" {
  run "${RESOLVE}" 8.8.47 "${SETUP_DIR}"
  [ "$status" -eq 0 ]
  [ "$output" = "stable-88" ]
}

@test "should map a minor-only tag to its stable folder" {
  run "${RESOLVE}" 8.10 "${SETUP_DIR}"
  [ "$status" -eq 0 ]
  [ "$output" = "stable-810" ]
}

@test "should map a tag of a minor version without folder to main" {
  run "${RESOLVE}" 8.11.0 "${SETUP_DIR}"
  [ "$status" -eq 0 ]
  [ "$output" = "main" ]
}

@test "should map alpha and rc tags to main" {
  run "${RESOLVE}" 8.11.0-alpha1 "${SETUP_DIR}"
  [ "$output" = "main" ]
  run "${RESOLVE}" 8.8.47-rc1 "${SETUP_DIR}"
  [ "$output" = "main" ]
}

@test "should reject a malformed tag" {
  for tag in "" foo 8 9.0.1 8.8. 8.8.x; do
    run "${RESOLVE}" "${tag}" "${SETUP_DIR}"
    [ "$status" -ne 0 ]
  done
}
