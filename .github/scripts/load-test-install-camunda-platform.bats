#!/usr/bin/env bats

# Tests for load-test-install-camunda-platform.sh. A stub `make` on PATH records its arguments, so
# the tests assert on the Helm flags the script passes without a cluster.

SCRIPT="${BATS_TEST_DIRNAME}/load-test-install-camunda-platform.sh"

setup() {
  cd "${BATS_TEST_TMPDIR}"
  mkdir -p bin load-tests/setup/c8-test
  printf '#!/usr/bin/env bash\nprintf "%%s\\n" "$@" > "%s/make-args.txt"\n' "${BATS_TEST_TMPDIR}" > bin/make
  chmod +x bin/make
  export PATH="${BATS_TEST_TMPDIR}/bin:${PATH}"
}

# install <tag> <repository> <optimize-enabled> <metrics-exporter-built> [script options...]
install() {
  local tag="$1" repository="$2" optimize="$3" exporter="$4"
  shift 4
  "${SCRIPT}" c8-test install "${tag}" "${repository}" true "${optimize}" false "${exporter}" false 0 "$@"
}

make_arg() {
  grep "^$1=" "${BATS_TEST_TMPDIR}/make-args.txt" | sed "s/^$1=//" | tr -s ' ' | sed 's/^ //; s/ $//'
}

@test "source build sets orchestration and optimize from the built images" {
  run install abc123 registry.camunda.cloud/team-zeebe true true \
    --orchestration-registry registry.camunda.cloud --orchestration-repository team-zeebe/camunda \
    --orchestration-tag abc123 --optimize-registry registry.camunda.cloud \
    --optimize-repository team-zeebe/optimize --optimize-tag abc123
  [ "$status" -eq 0 ]
  [ "$(make_arg additional_platform_configuration)" = "--set orchestration.image.registry=registry.camunda.cloud --set orchestration.image.repository=team-zeebe/camunda --set orchestration.image.tag=abc123 --set optimize.image.registry=registry.camunda.cloud --set optimize.image.repository=team-zeebe/optimize --set optimize.image.tag=abc123" ]
  [ "$(make_arg additional_load_test_configuration)" = "--set global.image.tag=abc123 --set global.image.repository=registry.camunda.cloud/team-zeebe --set global.image.pullSecrets[0].name=harbor-registry" ]
  [ "$(make_arg additional_load_test_setup_configuration)" = "--set metricsExporter.image.tag=abc123" ]
}

@test "released orchestration tag sets only the orchestration image" {
  run install 8.9.0 registry.camunda.cloud/team-zeebe true false \
    --orchestration-registry docker.io --orchestration-repository camunda/camunda --orchestration-tag 8.9.0
  [ "$status" -eq 0 ]
  [ "$(make_arg additional_platform_configuration)" = "--set orchestration.image.registry=docker.io --set orchestration.image.repository=camunda/camunda --set orchestration.image.tag=8.9.0" ]
  [ "$(make_arg additional_load_test_setup_configuration)" = "" ]
}

@test "released tags for all platform images set one value per image" {
  run install 8.9.0 registry.camunda.cloud/team-zeebe true false \
    --orchestration-registry docker.io --orchestration-repository camunda/camunda --orchestration-tag 8.9.0 \
    --optimize-tag 8.9.1 --identity-tag 8.9.2 --connectors-tag 8.9.3
  [ "$status" -eq 0 ]
  [ "$(make_arg additional_platform_configuration)" = "--set orchestration.image.registry=docker.io --set orchestration.image.repository=camunda/camunda --set orchestration.image.tag=8.9.0 --set optimize.image.tag=8.9.1 --set identity.image.tag=8.9.2 --set connectors.image.tag=8.9.3" ]
}

@test "no platform image input sets no platform image value" {
  run install client1 registry.camunda.cloud/team-zeebe true true
  [ "$status" -eq 0 ]
  [ "$(make_arg additional_platform_configuration)" = "" ]
  [ "$(make_arg additional_load_test_configuration)" = "--set global.image.tag=client1 --set global.image.repository=registry.camunda.cloud/team-zeebe --set global.image.pullSecrets[0].name=harbor-registry" ]
  [ "$(make_arg additional_load_test_setup_configuration)" = "--set metricsExporter.image.tag=client1" ]
}

@test "optimize image inputs are ignored when Optimize is disabled" {
  run install 8.9.0 registry.camunda.cloud/team-zeebe false false \
    --orchestration-tag 8.9.0 --optimize-tag 8.9.1
  [ "$status" -eq 0 ]
  [ "$(make_arg additional_platform_configuration)" = "--set orchestration.image.tag=8.9.0" ]
}

@test "user Helm values come after the image values" {
  run install 8.9.0 registry.camunda.cloud/team-zeebe true false \
    --orchestration-tag 8.9.0 --platform-helm-values "--set orchestration.replicas=1"
  [ "$status" -eq 0 ]
  [ "$(make_arg additional_platform_configuration)" = "--set orchestration.image.tag=8.9.0 --set orchestration.replicas=1" ]
}

# The stable/8.10 copy of camunda-load-test.yml calls the script with the previous argument format.
legacy_install() {
  "${SCRIPT}" c8-test install "$1" registry.camunda.cloud/team-zeebe "$2" "$3" "$4" true true false "$5" false 0 "${@:6}"
}

@test "previous argument format: source build" {
  run legacy_install abc123 registry.camunda.cloud team-zeebe/camunda team-zeebe/optimize true \
    --optimize-tag "" --orchestration-tag ""
  [ "$status" -eq 0 ]
  [ "$(make_arg additional_platform_configuration)" = "--set orchestration.image.registry=registry.camunda.cloud --set orchestration.image.repository=team-zeebe/camunda --set orchestration.image.tag=abc123 --set optimize.image.registry=registry.camunda.cloud --set optimize.image.repository=team-zeebe/optimize --set optimize.image.tag=abc123" ]
  [ "$(make_arg additional_load_test_setup_configuration)" = "--set metricsExporter.image.tag=abc123" ]
}

@test "previous argument format: released orchestration tag with an Optimize tag" {
  run legacy_install 8.9.0 docker.io camunda/camunda camunda/optimize false \
    --optimize-tag 8.9.1 --orchestration-tag 8.9.0
  [ "$status" -eq 0 ]
  [ "$(make_arg additional_platform_configuration)" = "--set orchestration.image.registry=docker.io --set orchestration.image.repository=camunda/camunda --set orchestration.image.tag=8.9.0 --set optimize.image.tag=8.9.1" ]
}

@test "previous argument format: released orchestration tag without an Optimize tag" {
  run legacy_install 8.9.0 docker.io camunda/camunda camunda/optimize false --orchestration-tag 8.9.0
  [ "$status" -eq 0 ]
  [ "$(make_arg additional_platform_configuration)" = "--set orchestration.image.registry=docker.io --set orchestration.image.repository=camunda/camunda --set orchestration.image.tag=8.9.0" ]
}
