#!/usr/bin/env bats

# Tests for load-test-update-versions-file.sh. They run against a temporary copy of the setup
# folders, so no network is needed.

UPDATE="${BATS_TEST_DIRNAME}/load-test-update-versions-file.sh"

setup() {
  SETUP_DIR="${BATS_TEST_TMPDIR}/setup"
  mkdir -p "${SETUP_DIR}/main" "${SETUP_DIR}/stable-87" "${SETUP_DIR}/stable-88" "${SETUP_DIR}/stable-810"
  for folder in main stable-88 stable-810; do
    cat > "${SETUP_DIR}/${folder}/versions.yaml" <<'YAML'
orchestration:
  image:
    # renovate: datasource=docker depName=docker.io/camunda/camunda
    tag: "1.0.0"
optimize:
  image:
    # renovate: datasource=docker depName=docker.io/camunda/optimize
    tag: "1.0.1"
connectors:
  image:
    # renovate: datasource=docker depName=docker.io/camunda/connectors-bundle
    tag: "1.0.2"
YAML
  done
  cat > "${SETUP_DIR}/stable-87/versions.yaml" <<'YAML'
zeebe:
  image:
    tag: "1.0.0"
zeebeGateway:
  image:
    tag: "1.0.0"
operate:
  image:
    tag: "1.0.0"
tasklist:
  image:
    tag: "1.0.0"
optimize:
  image:
    tag: "1.0.1"
YAML
}

@test "should update the orchestration tag and keep the comment and the quotes" {
  run "${UPDATE}" stable-88 8.8.47 "" "" "" "${SETUP_DIR}"
  [ "$status" -eq 0 ]
  grep -q '^    tag: "8.8.47"$' "${SETUP_DIR}/stable-88/versions.yaml"
  grep -q '# renovate: datasource=docker depName=docker.io/camunda/camunda$' "${SETUP_DIR}/stable-88/versions.yaml"
}

@test "should leave a component unchanged when its input is empty" {
  run "${UPDATE}" stable-88 8.8.47 "" "" 2.0.0 "${SETUP_DIR}"
  [ "$status" -eq 0 ]
  [ "$(yq '.optimize.image.tag' "${SETUP_DIR}/stable-88/versions.yaml")" = "1.0.1" ]
  [ "$(yq '.connectors.image.tag' "${SETUP_DIR}/stable-88/versions.yaml")" = "2.0.0" ]
}

@test "should set the four Camunda keys of stable-87" {
  run "${UPDATE}" stable-87 8.7.46 "" "" "" "${SETUP_DIR}"
  [ "$status" -eq 0 ]
  for key in zeebe zeebeGateway operate tasklist; do
    [ "$(yq ".${key}.image.tag" "${SETUP_DIR}/stable-87/versions.yaml")" = "8.7.46" ]
  done
}

@test "should produce no change on a second run with the same inputs" {
  "${UPDATE}" main 8.11.0-alpha2 1.0.5 "" "" "${SETUP_DIR}"
  cp "${SETUP_DIR}/main/versions.yaml" "${BATS_TEST_TMPDIR}/first.yaml"
  "${UPDATE}" main 8.11.0-alpha2 1.0.5 "" "" "${SETUP_DIR}"
  cmp "${SETUP_DIR}/main/versions.yaml" "${BATS_TEST_TMPDIR}/first.yaml"
}
