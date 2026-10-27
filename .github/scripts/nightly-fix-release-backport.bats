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
