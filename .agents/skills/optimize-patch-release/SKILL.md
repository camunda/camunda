---
name: optimize-patch-release
description: Use when releasing a patch version of Optimize 8.6 or 8.7 (e.g. 8.6.29, 8.7.31) from stable/optimize-8.6 or stable/optimize-8.7. Covers the upgrade-plan check, release branch, Zeebe/Identity bump, release workflow, GitHub release, next upgrade plan and merge-back PR.
---

# Optimize patch release (8.6 / 8.7)

Optimize 8.6 and 8.7 are released from their own branches, `stable/optimize-8.6` and
`stable/optimize-8.7`, not from `main` or `stable/8.x`. The release workflows are deprecated in
favour of the monorepo release process but are still the way to release these two lines.

## Inputs and derived values

Ask for the minor (`8.6` or `8.7`) and the release version. Derive everything else and show it to
the user before acting.

| Value                | Example for 8.6.29                         |
| -------------------- | ------------------------------------------ |
| Release version `R`  | `8.6.29`                                   |
| Previous version `P` | `8.6.28`                                   |
| Next version `N`     | `8.6.30`                                   |
| Base branch          | `stable/optimize-8.6`                      |
| Release branch       | `release/optimize-8.6.29`                  |
| Tag                  | `8.6.29-optimize`                          |
| Development version  | `8.6.30-SNAPSHOT`                          |
| Upgrade plan class   | `Upgrade8628To8629PlanFactory` (P to R)    |
| Next upgrade plan    | `Upgrade8629To8630PlanFactory` (R to N)    |

Plan files live in `optimize/upgrade/src/main/java/io/camunda/optimize/upgrade/plan/factories/`.

## Ground rules

- Never switch the user's checkout. Read other branches with `git fetch origin <branch>` and
  `git show origin/<branch>:<path>`. Make edits in a temporary `git worktree` and remove it
  afterwards.
- Never force-push. Ask for explicit confirmation before every push to a shared branch, workflow
  trigger, release creation and label change.
- Follow the repo commit rules: Conventional Commits, no scope, a body for every commit, and the
  `Co-authored-by` trailer.
- PR bodies must use `.github/pull_request_template.md`. Tick "This PR does not need a linked
  issue" for release PRs.
- Do not create backport PRs by hand. Use the `backport` skill and its label.
- Squash merges are disabled in this repo. Merge bump PRs and backports with a rebase merge, and
  merge-back PRs with a merge commit. You cannot approve a PR you authored, so a PR you opened
  needs one of the other reviewers.
- Release one line at a time (8.6, then 8.7) and wait until the first is fully released, so the two
  runs cannot interfere.

## Steps

Each step ends with a check. Do not continue until it holds.

### 1. Preflight (read-only)

- The plan `P to R` exists on the base branch. If not, stop and add it first.
- `optimize/pom.xml` is at `R-SNAPSHOT`, and neither the tag `R-optimize` nor the release branch
  exists yet.
- The Optimize integration tests (Elasticsearch and OpenSearch) and the E2E tests are green on the
  base branch head. They run in three separate workflows, so check the latest completed run of each
  for the head commit: `optimize-ci.yml`, `optimize-e2e-tests-sm.yml` and
  `optimize-e2e-test-cloud.yml`. If a job failed with "The runner has received a shutdown signal",
  that is infrastructure: re-run only the failed job and judge the new result. Otherwise, if they
  are red, stop and tell the user. Do not release over red Optimize ITs or E2E tests.
- Pom-only PRs run the Optimize backend ITs on the stable branch (`maven-change` in
  `optimize-backend-changes`). If a bump PR shows the ITs as skipped, find out why.
- Every CVE or fix PR the user wants in this release has merged into the base branch.

### 2. Create the release branch

```bash
gh workflow run optimize-create-release-branch.yml --repo camunda/camunda --ref main \
  -f release_version=<R> -f release_base_branch=<base branch>
```

Watch the run, then verify `release/optimize-<R>` exists and points at the base branch head.

### 3. Bump Zeebe and Identity

Optimize patch releases use the latest patch of the same minor for both.

1. Find the latest published `8.X.*` of `camunda/zeebe` and `camunda/identity` on Docker Hub
   (paginate through all tags) and cross-check Zeebe with the git tags and Maven Central. A version
   can be on Docker Hub and in the git tags hours before `zeebe-protocol` reaches Maven Central.
   Optimize compiles against it, so the bump PR's build is the real check that it resolves.
2. Compare with `zeebe.version` and `identity.version` in `optimize/pom.xml` on the release branch.
3. If a bump is needed, change both properties in a worktree off the release branch, push a branch
   and open a PR **against the release branch**, titled
   `deps: bump Zeebe and Identity for Optimize <R> release`.
4. Request reviews (see "Reviewers").
5. **Wait until the checks on the bump PR are green** before merging. That includes the Optimize
   backend integration tests (Elasticsearch and OpenSearch), the E2E tests and the build. If a
   check is skipped, find out why instead of treating it as passed. A pom-only change must still
   run the integration tests.

If a newer Zeebe or Identity version is published before the release starts, bump again.

### 4. Run the release workflow

Only after the bump PR is merged and the user confirms. Do not merge anything into the release
branch while it runs, because the push of the release commits is rejected otherwise.

```bash
gh workflow run optimize-release-optimize-c8-only.yml --repo camunda/camunda --ref <base branch> \
  -f RELEASE_VERSION=<R> -f DEVELOPMENT_VERSION=<N>-SNAPSHOT \
  -f BRANCH=release/optimize-<R> -f DOCKER_LATEST=false -f IS_DRY_RUN=false
```

Offer a dry run (`IS_DRY_RUN=true`) first if the user wants one, but remember it does not build or
push the multi-arch image. Watch the run and report the failed step, if any. If a run ends as
`action_required` with 0 jobs, it is waiting for a manual approval (this happens when dispatching
from a non-release branch). Only the user can approve it in the UI. When it ends, check that the
tag `<R>-optimize` exists, that the release branch is at `<N>-SNAPSHOT`, and that
`camunda/optimize:<R>` on Docker Hub has both `linux/amd64` and `linux/arm64`.

If the run fails before everything is published, see "Recovering from a failed release".

### 5. GitHub release

The final workflow step "Create a GitHub release" has failed on past releases. If no release
exists after the run, create it by hand:

```bash
gh api repos/camunda/camunda/releases/generate-notes -f tag_name=<R>-optimize \
  -f previous_tag_name=<P>-optimize --jq .body > /tmp/notes.md
gh release create <R>-optimize --repo camunda/camunda --verify-tag \
  --title "<R>-optimize" --notes-file /tmp/notes.md --latest=false
```

A patch release for an old line must never be marked **Latest**. Verify with
`gh api repos/camunda/camunda/releases/latest --jq .tag_name`. If it points at the Optimize release,
set the newest mainline release as Latest with `gh release edit <tag> --latest`, because unmarking
alone makes GitHub pick the newest release by date.

### 6. Next upgrade plan

Add the plan `R to N` after the release, not before. A plan merged earlier races with the release
workflow's push. Copy the previous plan class from the release branch, change the class name and
the two version strings, and push it to `release/optimize-<R>`. Keep the branch's style: the 8.6
plan classes have a `logErrorMessage` override and the 8.7 ones do not. Push it directly only if the
user agreed to skip a PR.

### 7. Merge-back PR

Open a PR from `release/optimize-<R>` into the base branch, titled
`build: merge back Optimize <R> release into stable/optimize-<X.Y>`. Check
`git rev-list --left-right --count origin/<base>...origin/release/optimize-<R>` first: the left
side should be 0. Tell the user to merge it with a merge commit, not a squash.

### Reviewers

Request reviews from two of `konstantinos-mylosis`, `tsedekey` and `OmranAbazid`, excluding the PR
author (`gh api user --jq .login`). Apply this to the bump PR and the merge-back PR.

## Recovering from a failed release

Find the failed step first (`gh run view <id> --json jobs`). What is already published depends on
where it stopped:

| Failed step                                   | Already published                              | Recovery |
| --------------------------------------------- | ---------------------------------------------- | -------- |
| "Push Git changes" (push rejected)            | nothing (the tag is removed by the workflow)   | Re-run with the same inputs once the release branch is quiet |
| "Execute health check and push docker image"  | tag, release commits and the Maven artifacts   | Fix the cause, then undo the release commits and the tag and re-run (below) |
| "Create a GitHub release"                     | everything                                     | Create the release by hand (step 5). Do not re-run |

To re-run after the tag and release commits were pushed, without a force-push:

1. **Confirm first that the artifact repository accepts a redeploy of `<R>`**, because the Maven
   artifacts were already deployed. This worked for 8.7.31 (the second run passed "Perform
   release"), but it is not guaranteed, so ask the CI owners if unsure. Do not change any shared ref
   before this is confirmed. If redeploy is not allowed, keep the tag and the release commits as
   they are and release the next patch instead.
2. Revert the release and next-iteration commits in a worktree off the release branch with
   `git revert --no-commit <next-iteration-sha> <release-sha>`, commit, and push normally.
3. Check that the tree equals the commit before the release: `git diff <pre-release-sha> HEAD` must
   be empty and the pom must be back at `<R>-SNAPSHOT`.
4. Delete the tag: `git push origin :refs/tags/<R>-optimize`.
5. Run the release workflow again with the same inputs.

Never force-push a release branch. Use a revert.

## Pitfalls

- Merging into the release branch while the release workflow runs makes its push fail with
  "rejected (fetch first)". The tag may be pushed and then removed. Nothing is published when the
  failure is before "Perform release", so you can re-run with the same inputs.
- The release branch has no branch protection, so nothing stops an early merge. Do not rely on
  GitHub to enforce the order.
- `main`'s CI gates the Optimize ITs on any `**/pom.xml` change. Check the stable branches do too,
  so that dependency bumps get tested.
- When only the last step "Create a GitHub release" fails, the tag and the artifacts are already
  published. Do not re-run the workflow. Create the release by hand and verify the tag, the release
  and the Latest flag at the end.
