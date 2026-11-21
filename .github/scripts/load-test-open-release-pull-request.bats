#!/usr/bin/env bats

# Tests for load-test-open-release-pull-request.sh. A local bare repository replaces origin, and a
# stub `gh` on PATH records its arguments and the pull request body, so no network is needed.

SCRIPT="${BATS_TEST_DIRNAME}/load-test-open-release-pull-request.sh"

setup() {
  REPO="${BATS_TEST_TMPDIR}/repo"
  REMOTE="${BATS_TEST_TMPDIR}/remote.git"
  mkdir -p "${REPO}/setup/stable-88" "${BATS_TEST_TMPDIR}/bin"
  printf 'orchestration:\n  image:\n    tag: "1.0.0"\n' > "${REPO}/setup/stable-88/versions.yaml"
  git init -q -b main "${REPO}"
  git -C "${REPO}" -c user.name=seed -c user.email=seed@example.com add setup
  git -C "${REPO}" -c user.name=seed -c user.email=seed@example.com commit -q -m seed
  git init -q --bare "${REMOTE}"
  git -C "${REPO}" remote add origin "${REMOTE}"

  cat > "${BATS_TEST_TMPDIR}/bin/gh" <<'STUB'
#!/usr/bin/env bash
echo "$*" >> "${GH_CALLS}"
if [[ "$1 $2" == "pr list" ]]; then
  echo "${GH_PR_LIST_OUTPUT:-}"
  exit 0
fi
while [[ $# -gt 0 ]]; do
  if [[ "$1" == "--body-file" ]]; then cp "$2" "${GH_BODY}"; fi
  shift
done
STUB
  chmod +x "${BATS_TEST_TMPDIR}/bin/gh"
  export PATH="${BATS_TEST_TMPDIR}/bin:${PATH}"
  export GH_CALLS="${BATS_TEST_TMPDIR}/gh-calls.txt" GH_BODY="${BATS_TEST_TMPDIR}/gh-body.md"
  export GITHUB_ACTOR=octocat GITHUB_ACTOR_ID=1234 GITHUB_WORKFLOW=update GITHUB_RUN_ID=42
  export GITHUB_REPOSITORY=camunda/camunda SETUP_DIR=setup
  cd "${REPO}"
}

change_versions_file() {
  sed -i 's/1.0.0/8.8.47/' setup/stable-88/versions.yaml
}

@test "should commit as the actor, push the branch and create the pull request" {
  change_versions_file
  run "${SCRIPT}" stable-88 8.8.47 "" 1.2.3 ""
  [ "$status" -eq 0 ]
  [ "$(git -C "${REMOTE}" log -1 --format='%an <%ae>' release-load-test/stable-88)" = "octocat <1234+octocat@users.noreply.github.com>" ]
  [ "$(git -C "${REMOTE}" log -1 --format=%s release-load-test/stable-88)" = "ci: run 8.8.47 in the stable-88 release load test" ]
  grep -q '^pr create --repo camunda/camunda --base main --head release-load-test/stable-88 .*--label generate-golden$' "${GH_CALLS}"
  grep -qF '| Camunda | `8.8.47` |' "${GH_BODY}"
  grep -qF '| Optimize | unchanged |' "${GH_BODY}"
  grep -qF '| Identity | `1.2.3` |' "${GH_BODY}"
}

@test "should update the open pull request of the branch" {
  change_versions_file
  GH_PR_LIST_OUTPUT=17 run "${SCRIPT}" stable-88 8.8.47 "" "" ""
  [ "$status" -eq 0 ]
  grep -q '^pr edit 17 --repo camunda/camunda --title ci: run 8.8.47 in the stable-88 release load test --body-file ' "${GH_CALLS}"
  ! grep -q '^pr create' "${GH_CALLS}"
}

@test "should do nothing when the file has no change" {
  run "${SCRIPT}" stable-88 1.0.0 "" "" ""
  [ "$status" -eq 0 ]
  [ ! -e "${GH_CALLS}" ]
  ! git -C "${REMOTE}" rev-parse --verify -q release-load-test/stable-88
}

@test "should fail when GITHUB_ACTOR is not set" {
  change_versions_file
  run env -u GITHUB_ACTOR "${SCRIPT}" stable-88 8.8.47 "" "" ""
  [ "$status" -ne 0 ]
}
