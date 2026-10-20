#!/usr/bin/env bats

# Tests for suite-health.sh. No network: a stub `gh` on PATH answers `gh run list` from fixture
# files, one JSON array per workflow, newest run first, as `gh run list --json` returns it.

SCRIPT="${BATS_TEST_DIRNAME}/suite-health.sh"

# 2026-10-13T04:00:00Z, shortly after triage.
NOW=1791864000
DAY=86400

setup() {
  cd "${BATS_TEST_TMPDIR}"
  mkdir -p bin fixtures
  cat > bin/gh <<'EOF'
#!/usr/bin/env bash
wf=""
while [ $# -gt 0 ]; do
  case "$1" in
    --workflow) wf="$2"; shift 2 ;;
    *) shift ;;
  esac
done
[ -f "${FIXTURES}/${wf}.fail" ] && { echo "HTTP 502" >&2; exit 1; }
cat "${FIXTURES}/${wf}.json"
EOF
  chmod +x bin/gh
  export PATH="${BATS_TEST_TMPDIR}/bin:${PATH}"
  export FIXTURES="${BATS_TEST_TMPDIR}/fixtures"
  export STATE_FILE="${BATS_TEST_TMPDIR}/health.json"
  export ALERTS_FILE="${BATS_TEST_TMPDIR}/alerts.txt"
  export RETRY_DELAY_SECONDS=0
  export NOW_EPOCH="$NOW"
  export NIGHTLY_WORKFLOWS="
    # comment lines and blank lines are ignored
    e2e.yml|8.10|e2e
  "
}

# runs <workflow> <conclusion>@<createdAt>... — newest first. "in_progress" makes a running run.
runs() {
  local wf="$1"; shift
  local spec i=100
  for spec in "$@"; do
    printf '%s %s %s\n' "$((i--))" "${spec%@*}" "${spec#*@}"
  done | jq -R -s -c '
    split("\n") | map(select(. != "") | split(" ")) | map({
      databaseId: (.[0] | tonumber),
      status: (if .[1] == "in_progress" then "in_progress" else "completed" end),
      conclusion: (if .[1] == "in_progress" then "" else .[1] end),
      createdAt: .[2],
      url: ("https://github.com/camunda/camunda/actions/runs/" + .[0])
    })' > "${FIXTURES}/${wf}.json"
}

check() {
  run bash "$SCRIPT"
  [ "$status" -eq 0 ] || { echo "$output"; return 1; }
  alerts=$(cat "$ALERTS_FILE")
}

three_red() {
  runs e2e.yml failure@2026-10-13T02:15:00Z failure@2026-10-12T02:15:00Z \
    failure@2026-10-11T02:15:00Z success@2026-10-10T02:15:00Z
}

@test "should announce a red streak once, and again after 7 days" {
  # given
  three_red

  # when / then
  check
  [ "$alerts" = ':rotating_light: *Nightly E2E -- 8.10* has been red on 3 consecutive scheduled runs, since 2026-10-11. <https://github.com/camunda/camunda/actions/runs/100|Latest run>' ]

  NOW_EPOCH=$((NOW + DAY)) check
  [ -z "$alerts" ]

  NOW_EPOCH=$((NOW + 7 * DAY)) check
  [[ "$alerts" == ':rotating_light: *Nightly E2E -- 8.10* is still red: 3 consecutive scheduled runs, since 2026-10-11.'* ]]
}

@test "should announce the end of a streak and forget it" {
  # given
  three_red
  check

  # when
  runs e2e.yml success@2026-10-14T02:15:00Z failure@2026-10-13T02:15:00Z
  NOW_EPOCH=$((NOW + DAY)) check

  # then
  [ "$alerts" = ':white_check_mark: *Nightly E2E -- 8.10*: the red streak announced earlier has ended.' ]
  [ "$(jq -c '.' "$STATE_FILE")" = '{}' ]
}

@test "should neither count nor break a streak on cancelled runs" {
  # given
  runs e2e.yml failure@2026-10-13T02:15:00Z cancelled@2026-10-12T02:15:00Z \
    failure@2026-10-11T02:15:00Z skipped@2026-10-10T02:15:00Z success@2026-10-09T02:15:00Z

  # when / then: 2 red runs, below the threshold
  check
  [ -z "$alerts" ]

  # when / then: a third red run behind the cancelled one completes the streak
  runs e2e.yml failure@2026-10-13T02:15:00Z cancelled@2026-10-12T02:15:00Z \
    failure@2026-10-11T02:15:00Z failure@2026-10-10T02:15:00Z success@2026-10-09T02:15:00Z
  check
  [[ "$alerts" == *'has been red on 3 consecutive scheduled runs, since 2026-10-10.'* ]]
}

@test "should not announce again a streak longer than the listed history" {
  # given: every listed run is red
  runs e2e.yml failure@2026-10-13T02:15:00Z failure@2026-10-12T02:15:00Z failure@2026-10-11T02:15:00Z
  check
  [[ "$alerts" == *'has been red on at least 3 consecutive scheduled runs, since 2026-10-11.'* ]]

  # when: the oldest run drops out of the list the next day
  runs e2e.yml failure@2026-10-14T02:15:00Z failure@2026-10-13T02:15:00Z failure@2026-10-12T02:15:00Z
  NOW_EPOCH=$((NOW + DAY)) check

  # then
  [ -z "$alerts" ]
}

@test "should announce a nightly with no scheduled run for 26 hours, once, until it runs again" {
  # given: the newest run started 26 hours and 15 minutes ago
  runs e2e.yml success@2026-10-12T01:45:00Z

  # when / then
  check
  [ "$alerts" = ':warning: *Nightly E2E -- 8.10* has had no scheduled run for more than 26 hours (last: 2026-10-12). Check that `e2e.yml` is enabled.' ]

  NOW_EPOCH=$((NOW + DAY)) check
  [ -z "$alerts" ]

  # when / then: a running run counts as a run
  runs e2e.yml in_progress@2026-10-14T02:15:00Z success@2026-10-12T01:45:00Z
  NOW_EPOCH=$((NOW + DAY)) check
  [ "$alerts" = ':white_check_mark: *Nightly E2E -- 8.10* has scheduled runs again.' ]
}

@test "should announce a nightly that never ran" {
  # given
  echo '[]' > "${FIXTURES}/e2e.yml.json"

  # when
  check

  # then
  [[ "$alerts" == *'(last: never)'* ]]
}

@test "should fail and keep the state when the history cannot be read" {
  # given
  three_red
  check
  before=$(cat "$STATE_FILE")
  touch "${FIXTURES}/e2e.yml.fail"

  # when
  run bash "$SCRIPT"

  # then
  [ "$status" -ne 0 ]
  [[ "$output" == *'gh run list failed for e2e.yml'* ]]
  [ "$(cat "$STATE_FILE")" = "$before" ]
}

@test "should drop the state of a nightly that left the scope" {
  # given
  three_red
  echo '{"old.yml":{"missing":{"alerted_at":1}}}' > "$STATE_FILE"

  # when
  check

  # then
  [ "$(jq -c 'keys' "$STATE_FILE")" = '["e2e.yml"]' ]
}

@test "should start from an empty state when the state is not valid" {
  # given
  three_red
  echo 'not json' > "$STATE_FILE"

  # when
  check

  # then
  [[ "$alerts" == *'has been red on 3 consecutive scheduled runs'* ]]
  [ "$(jq -r '.["e2e.yml"].streak.start_day' "$STATE_FILE")" = '2026-10-11' ]
}

@test "should fail when no nightly is in scope" {
  # given
  export NIGHTLY_WORKFLOWS=$'\n  # nothing\n'

  # when
  run bash "$SCRIPT"

  # then
  [ "$status" -ne 0 ]
  [[ "$output" == *'NIGHTLY_WORKFLOWS is empty'* ]]
}
