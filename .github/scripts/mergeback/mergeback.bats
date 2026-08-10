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

# make_remote <dir> <branch...> [tag:<name>...] — build a throwaway repo that `resolve` can treat as a
# remote via `git ls-remote`. One empty base commit, then a branch/tag per arg (`tag:X.Y.Z` makes a
# tag, anything else a branch). Used to exercise the ACTIVE-detection body end-to-end without network.
make_remote() {
  local dir="$1"; shift
  git init -q -b main "$dir"
  git -C "$dir" config user.email t@t.dev
  git -C "$dir" config user.name tester
  git -C "$dir" commit -q --allow-empty -m base
  local ref
  for ref in "$@"; do
    case "$ref" in
      tag:*) git -C "$dir" tag "${ref#tag:}" ;;
      *)     git -C "$dir" branch "$ref" ;;
    esac
  done
}

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
  [ "$(jq -r '.source_missing[0].commits[0].missing_on' <<<"$output")" = target ]
  [ "$(jq -r '.source_missing[0].commits[0].subject' <<<"$output")" = "feat: fix a|b in app.js" ]
}

@test "triage: a target-only re-fix on a conflicting line (missing on release) is flagged, missing_on=release" {
  # INC-6953 shape, and the reason the FORWARD check alone is blind. base has line Y. release re-applies
  # the SAME edit main already made (Y->Y2) as a patch-identical commit, so git cherry sees it as present
  # and the forward direction finds NOTHING. main then makes a FURTHER edit to that same line (Y2->Y3)
  # that never reached release. The three-way merge conflicts (base Y, release Y2, main Y3 — both sides
  # diverge from base), and only the conflict-tied REVERSE check surfaces the main-only commit. Without
  # it the path lands silently in source_drift with action_count 0.
  commit base $'app.js=X\nY\nZ'
  git branch release
  commit "shared: bump Y on main" $'app.js=X\nY2\nZ'                        # main P
  git checkout -q release && commit "shared: bump Y on release" $'app.js=X\nY2\nZ'   # release P' (patch-identical to P, distinct SHA)
  git checkout -q main && commit "fix: Y2 to Y3 (main only)" $'app.js=X\nY3\nZ'   # main Q, missing on release
  echo '{"status":"conflict","paths":["app.js"]}' > c.json
  run_json "$TRIAGE" main release c.json
  [ "$status" -eq 0 ]
  [ "$(jq -r '.action_count' <<<"$output")" -eq 1 ]
  [ "$(jq -r '.source_missing[0].path' <<<"$output")" = app.js ]
  [ "$(jq -r '.source_missing[0].commits[0].missing_on' <<<"$output")" = release ]
  [ "$(jq -r '.source_missing[0].commits[0].subject' <<<"$output")" = "fix: Y2 to Y3 (main only)" ]
  [ "$(jq -r '.source_drift | length' <<<"$output")" -eq 0 ]
}

@test "triage: reverse attribution is tied to the conflict lines, not the whole file (decoy excluded)" {
  # Flood guard. Same forward-blind conflict as above on line Y, PLUS a decoy: another main-only commit
  # (also missing on release, also touching app.js) on a far, unrelated line. The reverse look blames
  # only the conflicting line's range, so it surfaces the conflict-line commit and NOT the decoy — the
  # whole-file/blanket-target-ahead sweep that caused the flood would have pulled the decoy in too.
  commit base $'app.js=X\nY\nZ\nW\nV'
  git branch release
  commit "shared: bump Y on main" $'app.js=X\nY2\nZ\nW\nV'                        # main P
  git checkout -q release && commit "shared: bump Y on release" $'app.js=X\nY2\nZ\nW\nV'   # release P' (patch-identical)
  git checkout -q main && commit "fix: Y2 to Y3 (main only)" $'app.js=X\nY3\nZ\nW\nV'       # main Q, on the conflict line
  commit "chore: V to V2 (decoy, off the conflict)" $'app.js=X\nY3\nZ\nW\nV2'                # main D, missing on release, far line
  echo '{"status":"conflict","paths":["app.js"]}' > c.json
  run_json "$TRIAGE" main release c.json
  [ "$status" -eq 0 ]
  [ "$(jq -r '.action_count' <<<"$output")" -eq 1 ]
  [ "$(jq -r '.source_missing[0].path' <<<"$output")" = app.js ]
  [ "$(jq -r '.source_missing[0].commits | length' <<<"$output")" -eq 1 ]
  [ "$(jq -r '.source_missing[0].commits[0].subject' <<<"$output")" = "fix: Y2 to Y3 (main only)" ]
  [ "$(jq -r '.source_missing[0].commits[0].missing_on' <<<"$output")" = release ]
}

@test "triage: modify/delete blind spot (target deletes a line release edits) is flagged needs_review" {
  # Fixture: a patch-equivalent shared edit on the clashing line, then a TARGET-only deletion. The
  # reverse blame has no surviving target line to blame, so the path must land in needs_review — a
  # conflict a human resolves, not a source_missing alarm (nothing was forgotten) and not source_drift.
  commit base $'app.js=X\nY\nZ'
  git branch release
  commit "shared: bump Y on main" $'app.js=X\nY2\nZ'        # main P
  commit "chore: drop the line on main" $'app.js=X\nZ'      # main deletion, missing on release
  git checkout -q release && commit "shared: bump Y on release" $'app.js=X\nY2\nZ'  # release P' (patch-identical)
  echo '{"status":"conflict","paths":["app.js"]}' > c.json
  run_json "$TRIAGE" main release c.json
  [ "$status" -eq 0 ]
  [ "$(jq -r '.action_count' <<<"$output")" -eq 1 ]
  [ "$(jq -r '.needs_review[0].path' <<<"$output")" = app.js ]
  [ "$(jq -r '.source_missing | length' <<<"$output")" -eq 0 ]
  [ "$(jq -r '.source_drift | length' <<<"$output")" -eq 0 ]
}

@test "triage: add/add blind spot (same insertion both sides, target then modifies it) is flagged needs_review" {
  # Fixture: both branches independently apply the SAME insertion (patch-identical, so the forward
  # check sees it present), then the target modifies the inserted line. The release-side pure insertion
  # carries no base span to overlap, so it must land in needs_review rather than source_drift.
  commit base $'app.js=X\nZ'
  git branch release
  commit "shared: insert Y on main" $'app.js=X\nY\nZ'       # main P (insertion)
  commit "fix: Y to Y2 on main" $'app.js=X\nY2\nZ'          # main modify, missing on release
  git checkout -q release && commit "shared: insert Y on release" $'app.js=X\nY\nZ'  # release P' (patch-identical insertion)
  echo '{"status":"conflict","paths":["app.js"]}' > c.json
  run_json "$TRIAGE" main release c.json
  [ "$status" -eq 0 ]
  [ "$(jq -r '.action_count' <<<"$output")" -eq 1 ]
  [ "$(jq -r '.needs_review[0].path' <<<"$output")" = app.js ]
  [ "$(jq -r '.source_missing | length' <<<"$output")" -eq 0 ]
  [ "$(jq -r '.source_drift | length' <<<"$output")" -eq 0 ]
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

# ── resolve: RC vs final tag active-detection (the release-window blind spot boundary) ────────

@test "resolve: an RC tag does NOT retire the release branch (stays active mid-RC)" {
  # A cut RC tags the line (8.9.0-rc1) but the final X.Y.Z is not published yet. The tag regex
  # counts only X.Y.Z / X.Y.Z-alphaN, so the -rc tag is ignored and the branch stays monitored.
  make_remote "${BATS_TEST_TMPDIR}/remote" release-8.9.0 stable/8.9 tag:8.9.0-rc1
  run bash "$RESOLVE" "${BATS_TEST_TMPDIR}/remote"
  [ "$status" -eq 0 ]
  [ "$output" = $'release-8.9.0\tstable/8.9' ]
}

@test "resolve: the final release tag retires the branch (drops out of active set)" {
  # Once 8.9.0 itself is tagged the version equals the highest tag -> inactive, so nothing is emitted.
  make_remote "${BATS_TEST_TMPDIR}/remote" release-8.9.0 stable/8.9 tag:8.9.0
  run bash "$RESOLVE" "${BATS_TEST_TMPDIR}/remote"
  [ "$status" -eq 0 ]
  [ -z "$output" ]
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

@test "triage: a github-actions-authored source conflict is filtered as automation noise" {
  commit base "app.js=v0"
  git checkout -q -b release
  commit_as "github-actions[bot] <github-actions[bot]@users.noreply.github.com>" "update generated source" "app.js=release"
  git checkout -q main
  commit_as "github-actions[bot] <github-actions[bot]@users.noreply.github.com>" "update generated source differently" "app.js=main"
  echo '{"status":"conflict","paths":["app.js"]}' > c.json
  run_json "$TRIAGE" main release c.json
  [ "$status" -eq 0 ]
  [ "$(jq -r '.action_count' <<<"$output")" -eq 0 ]
  [ "$(jq -r '.source_missing | length' <<<"$output")" -eq 0 ]
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
  [[ "$output" == *"act now on a missing required change"* ]]
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
  [[ "$output" == *"GitHub Actions run"* ]]
}

@test "render: all three buckets full stays within Slack's 50-block limit and flags truncation" {
  # Each bucket alone (1 header + max_paths sections) fits, but three together would blow the
  # 50-block cap — the exact mixed-bucket case that dropped the only alert. Fill every bucket past
  # max_paths so the global budget MUST clip, then prove the total never exceeds 50.
  paths=$(jq -nc '[range(30) | {path:("p"+(.|tostring)+".java"), owner:"", commits:[{sha:"c",author:"d",subject:"s"}]}]')
  jq -nc --argjson paths "$paths" '{
    release_ref:"r", target_ref:"t", conflicting_paths:90, action_count:90, verdict:"v",
    source_missing:$paths, version_build_gap:$paths, needs_review:$paths,
    source_drift:[], version_build:[]
  }' > t.json
  run_json "$RENDER" t.json
  [ "$status" -eq 0 ]
  [ "$(jq -r '.blocks | length <= 50' <<<"$output")" = true ]
  [[ "$output" == *"GitHub Actions run"* ]]
}

