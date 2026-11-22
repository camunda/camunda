#!/usr/bin/env bats
# owner: @camunda/reliability-testing

setup() {
  SCRIPT="${BATS_TEST_DIRNAME}/load-test-dispatch-workflow.sh"
  STUB_DIR="${BATS_TEST_TMPDIR}/bin"
  mkdir -p "${STUB_DIR}"
  export GH_CALLS="${BATS_TEST_TMPDIR}/gh-calls"
  export GH_LIST_COUNT="${BATS_TEST_TMPDIR}/gh-list-count"
  export GITHUB_OUTPUT="${BATS_TEST_TMPDIR}/github-output"
  export POLL_ATTEMPTS=3
  export POLL_INTERVAL=0
  : > "${GH_CALLS}"
  : > "${GITHUB_OUTPUT}"
  echo 0 > "${GH_LIST_COUNT}"
  # `gh run list` prints $GH_RUNS from the second call on, to exercise the polling.
  cat > "${STUB_DIR}/gh" <<'STUB'
#!/usr/bin/env bash
echo "$*" >> "${GH_CALLS}"
if [[ "$1 $2" == "run list" ]]; then
  count=$(( $(cat "${GH_LIST_COUNT}") + 1 ))
  echo "${count}" > "${GH_LIST_COUNT}"
  if (( count >= 2 )); then echo "${GH_RUNS}"; else echo "[]"; fi
fi
STUB
  chmod +x "${STUB_DIR}/gh"
  export PATH="${STUB_DIR}:${PATH}"
}

future_run() {
  local title="$1" id="$2" created
  created="$(date -u -d '1 minute' +%Y-%m-%dT%H:%M:%SZ)"
  echo "[{\"databaseId\": ${id}, \"displayTitle\": \"${title}\", \"createdAt\": \"${created}\"}]"
}

@test "should dispatch the workflow at the ref with each input as a field" {
  # given
  export GH_RUNS="$(future_run 'Load test: ns-1' 42)"

  # when
  run "${SCRIPT}" wf.yml base-branch 'Load test: ns-1' name=ns-1 ttl=1

  # then
  [ "${status}" -eq 0 ]
  grep -qx "workflow run wf.yml --ref base-branch -f name=ns-1 -f ttl=1" "${GH_CALLS}"
}

@test "should write the ID of the run found after polling to GITHUB_OUTPUT" {
  # given
  export GH_RUNS="$(future_run 'Load test: ns-1' 42)"

  # when
  run "${SCRIPT}" wf.yml base-branch 'Load test: ns-1' name=ns-1

  # then
  [ "${status}" -eq 0 ]
  [ "$(cat "${GITHUB_OUTPUT}")" = "run-id=42" ]
}

@test "should ignore runs with another title" {
  # given
  export GH_RUNS="$(future_run 'Load test: other' 42)"

  # when
  run "${SCRIPT}" wf.yml base-branch 'Load test: ns-1' name=ns-1

  # then
  [ "${status}" -eq 1 ]
  [ ! -s "${GITHUB_OUTPUT}" ]
}

@test "should ignore runs created before the dispatch" {
  # given
  export GH_RUNS='[{"databaseId": 42, "displayTitle": "Load test: ns-1", "createdAt": "2020-01-01T00:00:00Z"}]'

  # when
  run "${SCRIPT}" wf.yml base-branch 'Load test: ns-1' name=ns-1

  # then
  [ "${status}" -eq 1 ]
  [[ "${output}" == *"No run titled 'Load test: ns-1' appeared"* ]]
}
