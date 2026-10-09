#!/usr/bin/env bats

# Tests for resolve-failing-test-owner.sh. The owner it prints becomes the owner of a CI incident, so
# each test pins one routing decision: a single owner is attributed, anything ambiguous falls back to
# the job's TEST_OWNER (empty stdout).
#
# No network: each test builds a throwaway git repository under BATS_TEST_TMPDIR with copies of the
# resolver and its helpers, and puts a stub codeowners-cli on PATH that owns paths by prefix.

bats_require_minimum_version 1.5.0

RESOLVER_DIR="${BATS_TEST_DIRNAME}"
MEDIC_LOOKUP="${BATS_TEST_DIRNAME}/../setup-medic-lookup.sh"
SUITE="qa/c8-orchestration-cluster-e2e-test-suite"

setup() {
  cd "${BATS_TEST_TMPDIR}"
  rm -rf repo bin && mkdir -p repo bin && cd repo
  git init -q -b main
  git config user.email t@t.dev
  git config user.name tester

  mkdir -p .ci/scripts/ci/observe-build-status
  cp "${RESOLVER_DIR}/resolve-failing-test-owner.sh" "${RESOLVER_DIR}/junit-test-results-to-jsonl.py" \
    .ci/scripts/ci/observe-build-status/
  cp "${MEDIC_LOOKUP}" .ci/scripts/ci/

  mkdir -p "${SUITE}/tests/operate" "${SUITE}/tests/tasklist" "${SUITE}/test-results" \
    zeebe/engine/src/test/java/io/camunda/engine
  touch "${SUITE}/tests/operate/processes.spec.ts" "${SUITE}/tests/tasklist/task-panel.spec.ts" \
    zeebe/engine/src/test/java/io/camunda/engine/EngineTest.java
  git add -A && git commit -qm fixtures

  cat > "${BATS_TEST_TMPDIR}/bin/codeowners-cli" <<'STUB'
#!/usr/bin/env bash
# codeowners-cli owner --format json <file>
file="${!#}"
case "${file}" in
  */tests/operate/*) owner="@camunda/operate-admin-pod" ;;
  */tests/tasklist/*) owner="@camunda/employee-engagement-tasklist" ;;
  zeebe/*) owner="@camunda/core-features" ;;
  *) owner="" ;;
esac
jq -n --arg f "${file}" --arg o "${owner}" '{($f): {required: (if $o == "" then [] else [$o] end)}}'
STUB
  chmod +x "${BATS_TEST_TMPDIR}/bin/codeowners-cli"
  PATH="${BATS_TEST_TMPDIR}/bin:${PATH}"
}

# playwright_report <spec>=<passed|failed>... — writes the suite's JUnit report the way Playwright
# does: one testsuite per spec, classname is the spec path relative to the suite root, and a test
# that failed every attempt carries a <failure>.
playwright_report() {
  {
    echo '<testsuites>'
    local pair spec outcome
    for pair in "$@"; do
      spec="${pair%%=*}" outcome="${pair#*=}"
      echo "<testsuite name=\"${spec}\" time=\"1\"><testcase name=\"a test\" classname=\"${spec}\" time=\"1\">"
      [[ "${outcome}" == failed ]] && echo '<failure message="failed" type="FAILURE">boom</failure>'
      echo '</testcase></testsuite>'
    done
    echo '</testsuites>'
  } > "${SUITE}/test-results/junit-report.xml"
}

resolve() {
  bash .ci/scripts/ci/observe-build-status/resolve-failing-test-owner.sh
}

@test "attributes Playwright failures of one area to that area's owner" {
  playwright_report "tests/operate/processes.spec.ts=failed" "tests/tasklist/task-panel.spec.ts=passed"

  run --separate-stderr resolve

  [ "$status" -eq 0 ]
  [ "$output" = "@camunda/operate-admin-pod" ]
}

@test "falls back and names the candidates when Playwright failures span two owners" {
  playwright_report "tests/operate/processes.spec.ts=failed" "tests/tasklist/task-panel.spec.ts=failed"

  run --separate-stderr resolve

  [ "$status" -eq 0 ]
  [ -z "$output" ]
  [[ "$stderr" == *"Candidates: @camunda/employee-engagement-tasklist @camunda/operate-admin-pod."* ]]
}

@test "falls back when a failing spec cannot be found" {
  playwright_report "tests/operate/processes.spec.ts=failed" "tests/operate/deleted.spec.ts=failed"

  run --separate-stderr resolve

  [ "$status" -eq 0 ]
  [ -z "$output" ]
  [[ "$stderr" == *"Failing tests without an owner: tests/operate/deleted.spec.ts"* ]]
}

@test "still attributes Java failures from TEST-*.xml reports" {
  mkdir -p zeebe/engine/target/surefire-reports
  cat > zeebe/engine/target/surefire-reports/TEST-io.camunda.engine.EngineTest.xml <<'XML'
<testsuite name="io.camunda.engine.EngineTest" time="1">
  <testcase name="shouldWork" classname="io.camunda.engine.EngineTest" time="1">
    <failure message="failed">boom</failure>
  </testcase>
</testsuite>
XML

  run --separate-stderr resolve

  [ "$status" -eq 0 ]
  [ "$output" = "@camunda/core-features" ]
}
