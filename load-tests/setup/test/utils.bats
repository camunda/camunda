#!/usr/bin/env bats

setup() {
  source "$BATS_TEST_DIRNAME/../utils.sh"
}

@test "builds a namespace from uppercase load-test names" {
  run new_namespace_name "INC-LoadTest"

  [ "$status" -eq 0 ]
  [ "$output" = "c8-inc-loadtest" ]
}

@test "builds a namespace from already-prefixed uppercase load-test names" {
  run new_namespace_name "C8-INC-LoadTest"

  [ "$status" -eq 0 ]
  [ "$output" = "c8-inc-loadtest" ]
}

@test "rejects invalid non-uppercase namespace characters" {
  run new_namespace_name "load_test"

  [ "$status" -eq 1 ]
  [[ "$output" == *"not a valid Kubernetes DNS-1123 label"* ]]
}

@test "rejects namespace names longer than Kubernetes label limit" {
  local name
  name="$(printf 'a%.0s' {1..61})"

  run new_namespace_name "$name"

  [ "$status" -eq 1 ]
  [[ "$output" == *"Kubernetes labels are capped at 63"* ]]
}
