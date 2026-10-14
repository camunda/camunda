#!/usr/bin/env bats

# Tests for nightly-fix-reopen.sh. No network: a stub `gh` on PATH resolves each
# request to a fixture file and then applies the real `--jq` filter with jq, the
# way gh itself does. A request with no fixture exits non-zero, which is how the
# "unreadable evidence" cases are expressed.
#
# These gates decide whether to resurrect a closed PR, so the cases that matter
# are the refusals: a human close and an unreadable timeline must both refuse.

SCRIPT="${BATS_TEST_DIRNAME}/nightly-fix-reopen.sh"

setup() {
  cd "${BATS_TEST_TMPDIR}"
  mkdir -p bin fixtures
  write_gh_stub
  chmod +x bin/gh
  export PATH="${BATS_TEST_TMPDIR}/bin:${PATH}"
  export FIXTURES="${BATS_TEST_TMPDIR}/fixtures"
  export GH_CALL_LOG="${BATS_TEST_TMPDIR}/gh-calls.log"
  : > "$GH_CALL_LOG"
  export REPO="camunda/camunda"
  export REOPEN_CLOSED_PRS=true
}

write_gh_stub() {
  cat > bin/gh <<'EOF'
#!/usr/bin/env bash
# Resolve a gh request to ${FIXTURES}/<name>.json, then apply its --jq filter.
printf '%s\n' "$*" >> "$GH_CALL_LOG"

filter=""; want_json=""; state=""; number=""; path=""
args=("$@")
for ((i = 0; i < ${#args[@]}; i++)); do
  case "${args[i]}" in
    --jq)    filter="${args[i+1]}" ;;
    --json)  want_json="${args[i+1]}" ;;
    --state) state="${args[i+1]}" ;;
  esac
done

slug() { printf '%s' "$1" | tr '/.' '__'; }

case "$1 $2" in
  "pr list")    name="prlist_${state}" ;;
  "pr view")    number="$3"; name="pr_${number}_$(slug "$want_json")" ;;
  "pr reopen"|"pr edit"|"pr comment") exit 0 ;;
  "api "*|"api")
    path="$2"
    case "$path" in
      *"/timeline"*) name="timeline_$(printf '%s' "$path" | sed -E 's#.*/issues/([0-9]+)/.*#\1#')" ;;
      *"/git/ref/heads/"*) [ -f "${FIXTURES}/ref_$(slug "${path##*/heads/}")" ] && exit 0 || exit 1 ;;
      *"/compare/"*) name="compare_$(slug "${path##*...}")" ;;
      *) exit 1 ;;
    esac
    ;;
  *) exit 1 ;;
esac

[ -f "${FIXTURES}/${name}.json" ] || exit 1
if [ -n "$filter" ]; then jq -r "$filter" "${FIXTURES}/${name}.json"; else cat "${FIXTURES}/${name}.json"; fi
EOF
}

# A single closed, unmerged, labelled candidate on this version's branch slot.
candidate() {
  cat > "${FIXTURES}/prlist_closed.json" <<EOF
[{"number": 1, "headRefName": "fix/nightly-8.10-flaky",
  "closedAt": "$(date -u +%FT%TZ)", "mergedAt": null,
  "labels": [{"name": "failing-test-fix"}, {"name": "auto-closed-stale"}]}]
EOF
  echo '[]' > "${FIXTURES}/prlist_open.json"
  echo '[{"name": "failing-test-fix"}, {"name": "auto-closed-stale"}]' \
    | jq '{labels: .}' > "${FIXTURES}/pr_1_labels.json"
  echo '{"comments": []}' > "${FIXTURES}/pr_1_comments.json"
  echo '{"files": [{"path": "qa/c8-orchestration-cluster-e2e-test-suite/tests/a.spec.ts"}]}' \
    > "${FIXTURES}/pr_1_files.json"
  : > "${FIXTURES}/ref_fix_nightly-8_10-flaky"
  echo '{"behind_by": 3}' > "${FIXTURES}/compare_fix_nightly-8_10-flaky.json"
  closed_by "github-actions[bot]"
}

# Write the timeline for PR #1 with <actor> as the last close actor.
closed_by() {
  jq -n --arg a "$1" '[[{event: "closed", actor: {login: $a}}]]' \
    > "${FIXTURES}/timeline_1.json"
}

SPECS='["qa/c8-orchestration-cluster-e2e-test-suite/tests/a.spec.ts"]'

pick() {
  run bash -c ". '$SCRIPT'; reopen_pick 'fix/nightly-8.10-' '${1-$SPECS}'"
  [ "$status" -eq 0 ] || { echo "$output"; return 1; }
}

@test "should reopen a PR the sweeper closed as github-actions[bot]" {
  # given — the sweeper closes with secrets.GITHUB_TOKEN, not the app token
  candidate

  # when
  pick

  # then
  [[ "$output" == *$'1\tfix/nightly-8.10-flaky\t0'* ]]
}

@test "should reopen a PR closed by the qa-processes app" {
  # given
  candidate
  closed_by "app/qa-processes"

  # when
  pick

  # then
  [[ "$output" == *$'1\tfix/nightly-8.10-flaky'* ]]
}

@test "should refuse a PR a human closed, even with the stale label present" {
  # given — the label can outlive the state it described
  candidate
  closed_by "some-reviewer"

  # when
  pick

  # then
  [[ "$output" != *$'1\tfix'* ]]
  [[ "$output" == *"closed by a human"* ]]
}

@test "should refuse when the timeline cannot be read" {
  # given
  candidate
  rm "${FIXTURES}/timeline_1.json"

  # when
  pick

  # then
  [[ "$output" != *$'1\tfix'* ]]
  [[ "$output" == *"unreadable"* ]]
}

@test "should refuse a bot close with neither the stale label nor the marker" {
  # given — bot-closed, but not by the sweeper
  candidate
  echo '{"labels": [{"name": "failing-test-fix"}]}' > "${FIXTURES}/pr_1_labels.json"

  # when
  pick

  # then
  [[ "$output" != *$'1\tfix'* ]]
}

@test "should accept the sweeper's close comment marker without the label" {
  # given — PRs closed before the label shipped carry only the marker
  candidate
  echo '{"labels": [{"name": "failing-test-fix"}]}' > "${FIXTURES}/pr_1_labels.json"
  echo '{"comments": [{"body": "<!-- stale-sweeper-closed at=now v=1 -->"}]}' \
    > "${FIXTURES}/pr_1_comments.json"

  # when
  pick

  # then
  [[ "$output" == *$'1\tfix/nightly-8.10-flaky'* ]]
}

@test "should never offer a merged PR as a candidate" {
  # given — `gh pr list --state closed` includes merged PRs, and reopen fails on one
  candidate
  jq '.[0].mergedAt = "2026-10-13T00:00:00Z"' "${FIXTURES}/prlist_closed.json" > t \
    && mv t "${FIXTURES}/prlist_closed.json"

  # when
  pick

  # then
  [ -z "$output" ]
}

@test "should never offer a PR a reviewer held with do-not-close" {
  # given
  candidate
  jq '.[0].labels += [{"name": "do-not-close"}]' "${FIXTURES}/prlist_closed.json" > t \
    && mv t "${FIXTURES}/prlist_closed.json"

  # when
  pick

  # then
  [ -z "$output" ]
}

@test "should refuse once the reopen cap is reached" {
  # given
  candidate
  jq -n '[[{event: "closed", actor: {login: "github-actions[bot]"}},
           {event: "reopened"}, {event: "reopened"}, {event: "reopened"}]]' \
    > "${FIXTURES}/timeline_1.json"

  # when
  pick

  # then
  [[ "$output" == *"cap 3"* ]]
  [[ "$output" != *$'1\tfix'* ]]
}

@test "should refuse when the head branch is gone" {
  # given — gh pr reopen hard-fails without a head ref
  candidate
  rm "${FIXTURES}/ref_fix_nightly-8_10-flaky"

  # when
  pick

  # then
  [[ "$output" == *"head branch"* ]]
}

@test "should refuse a branch too far behind main" {
  # given
  candidate
  echo '{"behind_by": 500}' > "${FIXTURES}/compare_fix_nightly-8_10-flaky.json"

  # when
  pick

  # then
  [[ "$output" == *"commits behind main"* ]]
}

@test "should refuse when no dispatched spec file overlaps the PR" {
  # given
  candidate

  # when
  pick '["qa/c8-orchestration-cluster-e2e-test-suite/tests/other.spec.ts"]'

  # then
  [[ "$output" == *"touches none of this dispatch"* ]]
}

@test "should evaluate nothing while the master switch is off" {
  # given
  candidate
  export REOPEN_CLOSED_PRS=false

  # when
  pick

  # then
  [ -z "$output" ]
  [ ! -s "$GH_CALL_LOG" ]
}

@test "should still evaluate every gate in dry-run mode" {
  # given — this is how the rollout gets evidence before the switch is flipped
  candidate
  export REOPEN_CLOSED_PRS=false REOPEN_DRY_RUN=true

  # when
  pick

  # then
  [[ "$output" == *$'1\tfix/nightly-8.10-flaky'* ]]
}
