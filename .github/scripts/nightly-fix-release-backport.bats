#!/usr/bin/env bats

# Tests for nightly-fix-release-backport.sh. No network: each test builds a real
# little git repo whose branches stand in for main and a frozen release branch, and
# PAIRS_FILE supplies what the merge-back resolver would have returned.
#
# This gate decides whether a test fix gets carried into an RC, so the cases that
# matter are the refusals: a spec added after the cut, a test added after the cut,
# and a patch that no longer applies must each refuse on their own.

SCRIPT="${BATS_TEST_DIRNAME}/nightly-fix-release-backport.sh"

# Triage dispatches `.file` relative to the suite root, while git and the PR patch
# use repository-relative paths. The fixtures keep both forms so a path-form mix-up
# fails here instead of silently disabling the test-title check in a real run.
SUITE="qa/c8-orchestration-cluster-e2e-test-suite"

setup() {
  cd "${BATS_TEST_TMPDIR}"
  rm -rf repo && mkdir repo && cd repo
  git init --quiet --initial-branch=main .
  git config user.email ci@example.com
  git config user.name CI

  mkdir -p "$SUITE/tests"
  cat > "$SUITE/tests/smoke.spec.ts" <<'EOF'
test('logs in', async () => {
  await page.click('#login');
});
EOF
  git add -A && git commit --quiet -m "base"

  # The freeze point. Both branches carry the spec above; what each test changes
  # afterwards is what the gate is being asked about.
  git branch release-8.11.0-alpha1

  printf '%s\t%s\n' release-8.11.0-alpha1 main > "${BATS_TEST_TMPDIR}/pairs.tsv"
  export PAIRS_FILE="${BATS_TEST_TMPDIR}/pairs.tsv"

  cat > "${BATS_TEST_TMPDIR}/specs.json" <<'EOF'
[{"file": "tests/smoke.spec.ts", "test_name": "logs in"}]
EOF
  export SPECS="${BATS_TEST_TMPDIR}/specs.json"
}

# Commit a change on main and write the resulting PR patch to $BATS_TEST_TMPDIR/pr.patch.
commit_fix_on_main() {
  git add -A
  git commit --quiet -m "fix"
  git diff HEAD~1 HEAD > "${BATS_TEST_TMPDIR}/pr.patch"
}

# The aggregate base..HEAD diff, which is what the workflow feeds the gate. Used
# where a PR spans several commits and the per-commit series would read differently.
write_aggregate_patch() {
  git diff "$1" HEAD > "${BATS_TEST_TMPDIR}/pr.patch"
}

@test "labels a fix whose spec and test both exist on the release branch" {
  sed -i.bak "s/#login/#login-button/" "$SUITE/tests/smoke.spec.ts" && rm -f "$SUITE/tests/smoke.spec.ts.bak"
  commit_fix_on_main

  run "$SCRIPT" main 42 "${BATS_TEST_TMPDIR}/pr.patch" "$SPECS"
  [ "$status" -eq 0 ]
  [ "$(jq -r .decision <<<"$output")" = "eligible" ]
  [ "$(jq -r .release_ref <<<"$output")" = "release-8.11.0-alpha1" ]
  [ "$(jq -r .pr <<<"$output")" = "42" ]
}

@test "stays quiet when no active release branch targets this branch" {
  # Only a stable/8.10 pair is active, so a main dispatch has nothing to feed.
  printf '%s\t%s\n' release-8.10.5 stable/8.10 > "$PAIRS_FILE"
  sed -i.bak "s/#login/#login-button/" "$SUITE/tests/smoke.spec.ts" && rm -f "$SUITE/tests/smoke.spec.ts.bak"
  commit_fix_on_main

  run "$SCRIPT" main 42 "${BATS_TEST_TMPDIR}/pr.patch" "$SPECS"
  [ "$status" -eq 0 ]
  [ "$(jq -r .decision <<<"$output")" = "none" ]
}

@test "refuses a fix to a spec added after the cut" {
  cat > "$SUITE/tests/mcp.spec.ts" <<'EOF'
test('mcp server responds', async () => {});
EOF
  git add -A && git commit --quiet -m "post-cut spec"
  sed -i.bak "s/responds/responds fast/" "$SUITE/tests/mcp.spec.ts" && rm -f "$SUITE/tests/mcp.spec.ts.bak"
  commit_fix_on_main

  run "$SCRIPT" main 42 "${BATS_TEST_TMPDIR}/pr.patch" "$SPECS"
  [ "$status" -eq 0 ]
  [ "$(jq -r .decision <<<"$output")" = "skipped" ]
  [[ "$(jq -r .reason <<<"$output")" == *"mcp.spec.ts"* ]]
}

@test "refuses a fix to a test added to an existing spec after the cut" {
  cat >> "$SUITE/tests/smoke.spec.ts" <<'EOF'
test('uses the new redaction field', async () => {});
EOF
  git add -A && git commit --quiet -m "post-cut test"
  sed -i.bak "s/new redaction field/new redaction field v2/" "$SUITE/tests/smoke.spec.ts"
  rm -f "$SUITE/tests/smoke.spec.ts.bak"
  commit_fix_on_main

  cat > "$SPECS" <<'EOF'
[{"file": "tests/smoke.spec.ts", "test_name": "uses the new redaction field v2"}]
EOF

  run "$SCRIPT" main 42 "${BATS_TEST_TMPDIR}/pr.patch" "$SPECS"
  [ "$status" -eq 0 ]
  [ "$(jq -r .decision <<<"$output")" = "skipped" ]
  [[ "$(jq -r .reason <<<"$output")" == *"test not on the release branch"* ]]
}

@test "refuses a fix whose patch no longer applies to the release branch" {
  # The release branch rewrote the same region, so the fix cannot be carried over
  # mechanically even though spec and test are both present.
  git checkout --quiet release-8.11.0-alpha1
  cat > "$SUITE/tests/smoke.spec.ts" <<'EOF'
test('logs in', async () => {
  await loginHelper(page);
});
EOF
  git add -A && git commit --quiet -m "release-only rework"
  git checkout --quiet main

  sed -i.bak "s/#login/#login-button/" "$SUITE/tests/smoke.spec.ts" && rm -f "$SUITE/tests/smoke.spec.ts.bak"
  commit_fix_on_main

  run "$SCRIPT" main 42 "${BATS_TEST_TMPDIR}/pr.patch" "$SPECS"
  [ "$status" -eq 0 ]
  [ "$(jq -r .decision <<<"$output")" = "skipped" ]
  [[ "$(jq -r .reason <<<"$output")" == *"does not apply"* ]]
}

@test "allows a fix that adds a new file" {
  cat > "$SUITE/tests/helper.ts" <<'EOF'
export const waitForReady = async () => {};
EOF
  commit_fix_on_main

  run "$SCRIPT" main 42 "${BATS_TEST_TMPDIR}/pr.patch" "$SPECS"
  [ "$status" -eq 0 ]
  [ "$(jq -r .decision <<<"$output")" = "eligible" ]
}

@test "refuses an empty patch rather than labelling on no evidence" {
  : > "${BATS_TEST_TMPDIR}/pr.patch"

  run "$SCRIPT" main 42 "${BATS_TEST_TMPDIR}/pr.patch" "$SPECS"
  [ "$status" -eq 0 ]
  [ "$(jq -r .decision <<<"$output")" = "skipped" ]
}

@test "refuses a shared-helper fix when the dispatched spec is not on the release branch" {
  # The patch touches only a helper that exists on both branches, so the file check
  # passes; only the dispatched spec, absent on the release branch, shows this is a
  # fix for post-freeze work.
  git checkout --quiet main
  cat > "$SUITE/tests/helper.ts" <<'EOF'
export const waitForReady = async () => {};
EOF
  git add -A && git commit --quiet -m "helper"
  git branch -f release-8.11.0-alpha1 HEAD
  cat > "$SUITE/tests/mcp.spec.ts" <<'EOF'
test('mcp server responds', async () => {});
EOF
  git add -A && git commit --quiet -m "post-cut spec"
  sed -i.bak "s/Ready/Ready2/" "$SUITE/tests/helper.ts" && rm -f "$SUITE/tests/helper.ts.bak"
  commit_fix_on_main

  cat > "$SPECS" <<'EOF'
[{"file": "tests/mcp.spec.ts", "test_name": "mcp server responds"}]
EOF

  run "$SCRIPT" main 42 "${BATS_TEST_TMPDIR}/pr.patch" "$SPECS"
  [ "$status" -eq 0 ]
  [ "$(jq -r .decision <<<"$output")" = "skipped" ]
  [[ "$(jq -r .reason <<<"$output")" == *"(spec absent)"* ]]
}

@test "accepts a dispatched file that already carries the suite root" {
  sed -i.bak "s/#login/#login-button/" "$SUITE/tests/smoke.spec.ts" && rm -f "$SUITE/tests/smoke.spec.ts.bak"
  commit_fix_on_main
  cat > "$SPECS" <<EOF
[{"file": "$SUITE/tests/smoke.spec.ts", "test_name": "logs in"}]
EOF

  run "$SCRIPT" main 42 "${BATS_TEST_TMPDIR}/pr.patch" "$SPECS"
  [ "$status" -eq 0 ]
  [ "$(jq -r .decision <<<"$output")" = "eligible" ]
}

@test "refuses a dispatched title that only appears as a prefix of another test" {
  # The release branch has a longer test whose title starts with the dispatched
  # one. A raw substring search would call the dispatched test present.
  git checkout --quiet release-8.11.0-alpha1
  cat > "$SUITE/tests/smoke.spec.ts" <<'EOF'
test('logs in with SSO', async () => {
  await page.click('#login');
});
EOF
  git add -A && git commit --quiet -m "release-only test"
  git checkout --quiet main

  sed -i.bak "s/#login/#login-button/" "$SUITE/tests/smoke.spec.ts" && rm -f "$SUITE/tests/smoke.spec.ts.bak"
  commit_fix_on_main

  run "$SCRIPT" main 42 "${BATS_TEST_TMPDIR}/pr.patch" "$SPECS"
  [ "$status" -eq 0 ]
  [ "$(jq -r .decision <<<"$output")" = "skipped" ]
  [[ "$(jq -r .reason <<<"$output")" == *"test not on the release branch"* ]]
}

@test "allows a helper added in one commit and edited in a later one" {
  # Across the whole PR the helper is simply added, and added files are exempt.
  # Only a per-commit patch series would show the second commit as a change to a
  # file the release branch lacks.
  base=$(git rev-parse HEAD)
  cat > "$SUITE/tests/helper.ts" <<'EOF'
export const waitForReady = async () => {};
EOF
  git add -A && git commit --quiet -m "add helper"
  cat > "$SUITE/tests/helper.ts" <<'EOF'
export const waitForReady = async () => { await Promise.resolve(); };
EOF
  git add -A && git commit --quiet -m "edit helper"
  write_aggregate_patch "$base"

  run "$SCRIPT" main 42 "${BATS_TEST_TMPDIR}/pr.patch" "$SPECS"
  [ "$status" -eq 0 ]
  [ "$(jq -r .decision <<<"$output")" = "eligible" ]
}

@test "finds a title near the top of a spec larger than the pipe buffer" {
  # Regression for grep -q under pipefail. The title must come EARLY: grep exits
  # on the match, git show then takes SIGPIPE on the ~200k still to write, and the
  # pipeline reports failure -- so a title that IS there reads as absent. With the
  # match at the end grep drains the file anyway and the bug stays hidden.
  awk 'BEGIN { for (i = 0; i < 4000; i++) print "// pad pad pad pad pad pad pad pad pad pad pad pad" }' \
    >> "$SUITE/tests/smoke.spec.ts"
  git add -A && git commit --quiet -m "big spec"
  git branch -f release-8.11.0-alpha1 HEAD
  [ "$(wc -c < "$SUITE/tests/smoke.spec.ts")" -gt 65536 ]

  sed -i.bak "s/#login/#login-button/" "$SUITE/tests/smoke.spec.ts" && rm -f "$SUITE/tests/smoke.spec.ts.bak"
  commit_fix_on_main

  run "$SCRIPT" main 42 "${BATS_TEST_TMPDIR}/pr.patch" "$SPECS"
  [ "$status" -eq 0 ]
  [ "$(jq -r .decision <<<"$output")" = "eligible" ]
}

@test "refuses when the dispatched specs are unusable as evidence" {
  sed -i.bak "s/#login/#login-button/" "$SUITE/tests/smoke.spec.ts" && rm -f "$SUITE/tests/smoke.spec.ts.bak"
  commit_fix_on_main

  # Malformed JSON.
  echo '{not json' > "$SPECS"
  run "$SCRIPT" main 42 "${BATS_TEST_TMPDIR}/pr.patch" "$SPECS"
  [ "$(jq -r .decision <<<"$output")" = "skipped" ]
  [[ "$(jq -r .reason <<<"$output")" == *"not a JSON array"* ]]

  # Entry without a test_name.
  echo '[{"file": "tests/smoke.spec.ts"}]' > "$SPECS"
  run "$SCRIPT" main 42 "${BATS_TEST_TMPDIR}/pr.patch" "$SPECS"
  [ "$(jq -r .decision <<<"$output")" = "skipped" ]
  [[ "$(jq -r .reason <<<"$output")" == *"missing file/test_name"* ]]

  # No specs file at all.
  run "$SCRIPT" main 42 "${BATS_TEST_TMPDIR}/pr.patch" "${BATS_TEST_TMPDIR}/nope.json"
  [ "$(jq -r .decision <<<"$output")" = "skipped" ]
  [[ "$(jq -r .reason <<<"$output")" == *"missing"* ]]
}

@test "allows an empty spec list, as a workflow-level dispatch carries none" {
  sed -i.bak "s/#login/#login-button/" "$SUITE/tests/smoke.spec.ts" && rm -f "$SUITE/tests/smoke.spec.ts.bak"
  commit_fix_on_main
  echo '[]' > "$SPECS"

  run "$SCRIPT" main 42 "${BATS_TEST_TMPDIR}/pr.patch" "$SPECS"
  [ "$(jq -r .decision <<<"$output")" = "eligible" ]
}

@test "resolve_release_ref picks the pair matching the dispatch target" {
  source "$SCRIPT"
  printf '%s\t%s\n%s\t%s\n' \
    release-8.10.5 stable/8.10 release-8.11.0-alpha1 main > "$PAIRS_FILE"

  run resolve_release_ref stable/8.10 "$PAIRS_FILE"
  [ "$output" = "release-8.10.5" ]

  run resolve_release_ref main "$PAIRS_FILE"
  [ "$output" = "release-8.11.0-alpha1" ]

  run resolve_release_ref stable/8.9 "$PAIRS_FILE"
  [ -z "$output" ]
}
