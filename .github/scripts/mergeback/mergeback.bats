#!/usr/bin/env bats

# Regression suite for the release merge-back scripts. Minimal by design: every test pins a bug
# actually hit while building these scripts, so a regression fails CI instead of needing manual replay.
#   detect-mergeback-conflict.sh    — operational failure must not masquerade as "clean"; extensionless
#                                     paths must survive (old path-shape filter dropped Makefile/mvnw).
#   triage-mergeback.sh             — a malformed contract must fail loud (reviewer reproduced a
#                                     non-array .paths passing as clean); pathless conflict must stay
#                                     actionable; a '|' in a commit subject must not mis-split.
#   resolve-active-release-pairs.sh — a failed remote lookup must fail closed, never collapse to "none".
#
# No network: fixtures are built at runtime under BATS_TEST_TMPDIR. The detector prints JSON to stdout
# and human context to stderr, so `run_json` captures ONLY stdout and `run_stderr` ONLY stderr.

DETECT="${BATS_TEST_DIRNAME}/detect-mergeback-conflict.sh"
TRIAGE="${BATS_TEST_DIRNAME}/triage-mergeback.sh"
RESOLVE="${BATS_TEST_DIRNAME}/resolve-active-release-pairs.sh"
RENDER="${BATS_TEST_DIRNAME}/render-slack-message.sh"

setup() {
  cd "${BATS_TEST_TMPDIR}"
  rm -rf repo && mkdir repo && cd repo
  git init -q -b main
  git config user.email t@t.dev
  git config user.name tester
}

# commit <msg> <file=content>...
commit() {
  local msg="$1"; shift
  local pair
  for pair in "$@"; do printf '%s\n' "${pair#*=}" > "${pair%%=*}"; done
  git add -A && git commit -qm "$msg"
}

# commit_as <author> <msg> <file=content>...  — like commit() but with an explicit author, so the
# triage automation-author noise filter (github-actions bumps) can be exercised deterministically.
commit_as() {
  local author="$1" msg="$2"; shift 2
  local pair
  for pair in "$@"; do printf '%s\n' "${pair#*=}" > "${pair%%=*}"; done
  git add -A && git commit -qm "$msg" --author="$author"
}

run_json()   { run bash -c 'bash "$@" 2>/dev/null' _ "$@"; }
run_stderr() { run bash -c 'bash "$@" 2>&1 >/dev/null' _ "$@"; }
# Source a script (past its exec guard) and run one of its functions; captures stdout only.
run_fn() { run bash -c 'source "$1"; shift; "$@" 2>/dev/null' _ "$@"; }

# ── detect ───────────────────────────────────────────────────────────────────

@test "detect: extensionless root file (Makefile) survives in paths" {
  commit base "Makefile=v0"
  git checkout -q -b release && commit rel "Makefile=release"
  git checkout -q main && commit main "Makefile=main"
  run_json "$DETECT" main release
  [ "$status" -eq 2 ]
  [ "$(jq -r '.paths[0]' <<<"$output")" = Makefile ]
}

@test "detect: an unresolvable ref is operational (exit 3), never clean" {
  commit base "a=1"
  run_json "$DETECT" main no-such-branch
  [ "$status" -eq 3 ]
  [ -z "$output" ]
}

# ── triage: contract validation (reviewer reproductions) ─────────────────────

@test "triage: rejects .paths that is not an array" {
  echo '{"status":"clean","paths":"not-an-array"}' > c.json
  run_stderr "$TRIAGE" main HEAD c.json
  [ "$status" -ne 0 ]
  [[ "$output" == *"malformed detector contract"* ]]
}

@test "triage: rejects status=clean contradicted by a non-empty paths list" {
  echo '{"status":"clean","paths":["x"]}' > c.json
  run_stderr "$TRIAGE" main HEAD c.json
  [ "$status" -ne 0 ]
  [[ "$output" == *"malformed detector contract"* ]]
}

# ── triage: classification ───────────────────────────────────────────────────

@test "triage: pathless conflict (status=conflict, empty paths) stays actionable" {
  echo '{"status":"conflict","paths":[]}' > c.json
  run_json "$TRIAGE" main HEAD c.json
  [ "$status" -eq 0 ]
  [ "$(jq -r '.action_count' <<<"$output")" -eq 1 ]
}

@test "triage: a source file missing on target is flagged, pipe in subject intact" {
  commit base "app.js=v0"
  git checkout -q -b release && commit "feat: fix a|b in app.js" "app.js=release"
  git checkout -q main && commit other "app.js=main"
  echo '{"status":"conflict","paths":["app.js"]}' > c.json
  run_json "$TRIAGE" main release c.json
  [ "$status" -eq 0 ]
  [ "$(jq -r '.action_count' <<<"$output")" -eq 1 ]
  [ "$(jq -r '.source_missing[0].commits[0].subject' <<<"$output")" = "feat: fix a|b in app.js" ]
}

# ── resolve: fail-closed remote lookup ───────────────────────────────────────

@test "resolve: fails closed when the remote is unreachable" {
  run bash "$RESOLVE" "${BATS_TEST_TMPDIR}/does-not-exist.git"
  [ "$status" -eq 1 ]
}

# ── resolve: version_max ordering (the -alpha-vs-release edge sort -V gets wrong) ─────────────

@test "resolve: version_max ranks a plain release above its own -alpha" {
  run_fn "$RESOLVE" version_max 8.9.0 8.9.0-alpha3
  [ "$status" -eq 0 ]
  [ "$output" = 8.9.0 ]
}

@test "resolve: version_max is order-independent for release-vs-alpha" {
  run_fn "$RESOLVE" version_max 8.9.0-alpha3 8.9.0
  [ "$status" -eq 0 ]
  [ "$output" = 8.9.0 ]
}

@test "resolve: version_max picks the higher patch on the same line" {
  run_fn "$RESOLVE" version_max 8.9.0 8.9.1
  [ "$status" -eq 0 ]
  [ "$output" = 8.9.1 ]
}

@test "resolve: version_max compares across minors numerically, not lexically" {
  run_fn "$RESOLVE" version_max 8.10.0 8.9.5
  [ "$status" -eq 0 ]
  [ "$output" = 8.10.0 ]
}

@test "resolve: version_max orders alpha numbers numerically (alpha10 > alpha2)" {
  run_fn "$RESOLVE" version_max 8.9.0-alpha2 8.9.0-alpha10
  [ "$status" -eq 0 ]
  [ "$output" = 8.9.0-alpha10 ]
}

# ── triage: automation-author / release-plugin noise filter (what kills the noise) ────────────

@test "triage: a maven-release-plugin pom bump is mechanical, not actionable" {
  commit base "pom.xml=v0"
  git checkout -q -b release && commit "[maven-release-plugin] prepare release 8.9.1" "pom.xml=release"
  git checkout -q main && commit other "pom.xml=main"
  echo '{"status":"conflict","paths":["pom.xml"]}' > c.json
  run_json "$TRIAGE" main release c.json
  [ "$status" -eq 0 ]
  [ "$(jq -r '.action_count' <<<"$output")" -eq 0 ]
  [ "$(jq -r '.version_build[0]' <<<"$output")" = pom.xml ]
}

@test "triage: a github-actions-authored pom change is filtered as automation noise" {
  commit base "pom.xml=v0"
  git checkout -q -b release
  commit_as "github-actions[bot] <github-actions[bot]@users.noreply.github.com>" "bump identity image" "pom.xml=release"
  git checkout -q main && commit other "pom.xml=main"
  echo '{"status":"conflict","paths":["pom.xml"]}' > c.json
  run_json "$TRIAGE" main release c.json
  [ "$status" -eq 0 ]
  [ "$(jq -r '.action_count' <<<"$output")" -eq 0 ]
  [ "$(jq -r '.version_build[0]' <<<"$output")" = pom.xml ]
}

@test "triage: a real human dependency change on a pom is an actionable gap" {
  commit base "pom.xml=v0"
  git checkout -q -b release && commit "build: bump elasticsearch to 8.15" "pom.xml=release"
  git checkout -q main && commit other "pom.xml=main"
  echo '{"status":"conflict","paths":["pom.xml"]}' > c.json
  run_json "$TRIAGE" main release c.json
  [ "$status" -eq 0 ]
  [ "$(jq -r '.action_count' <<<"$output")" -eq 1 ]
  [ "$(jq -r '.version_build_gap[0].path' <<<"$output")" = pom.xml ]
}

# ── render: Slack Block Kit presentation (extracted from the workflow) ────────────────────────

@test "render: emits valid Block Kit JSON and neutralises Slack mention injection" {
  echo '{"release_ref":"release-8.9.0","target_ref":"stable/8.9","conflicting_paths":1,"action_count":1,"verdict":"needs a human","source_missing":[{"path":"a/<!here>.java","owner":"@x/<@U1>","commits":[{"sha":"abc","author":"d","subject":"s <!channel>"}]}],"version_build_gap":[],"source_drift":[],"version_build":[]}' > t.json
  run_json "$RENDER" t.json
  [ "$status" -eq 0 ]
  [ "$(jq -r '.blocks | length > 0' <<<"$output")" = true ]
  # No raw mention/link syntax may survive into the rendered message.
  [[ "$output" != *"<!here>"* ]]
  [[ "$output" != *"<!channel>"* ]]
  [[ "$output" != *"<@U1>"* ]]
}

@test "render: flags truncation when a path carries more than max_commits" {
  commits=$(jq -nc '[range(6) | {sha:("c"+(.|tostring)),author:"d",subject:"s"}]')
  jq -nc --argjson commits "$commits" '{
    release_ref:"r", target_ref:"t", conflicting_paths:1, action_count:1, verdict:"v",
    source_missing:[{path:"a.java", owner:"", commits:$commits}],
    version_build_gap:[], source_drift:[], version_build:[]
  }' > t.json
  run_json "$RENDER" t.json
  [ "$status" -eq 0 ]
  [[ "$output" == *"truncated"* ]]
}

