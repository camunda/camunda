#!/usr/bin/env bats

setup() {
  source "$BATS_TEST_DIRNAME/utils.sh"
}

@test "normalizes uppercase load-test names" {
  run normalize_load_test_name "INC-LoadTest"

  [ "$status" -eq 0 ]
  [ "$output" = "inc-loadtest" ]
}

@test "normalizes already-prefixed uppercase load-test names" {
  run normalize_load_test_name "C8-INC-LoadTest"

  [ "$status" -eq 0 ]
  [ "$output" = "c8-inc-loadtest" ]
}

@test "rejects invalid non-uppercase namespace characters" {
  run validate_load_test_namespace "c8-load_test"

  [ "$status" -eq 1 ]
  [[ "$output" == *"not a valid Kubernetes DNS-1123 label"* ]]
}
