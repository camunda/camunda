---
name: backport
description: "Use when asked to backport, add a `backport stable/X.Y` label, comment `/backport`, or fix backport conflicts."
---

# Backport

Backports are automated by a GitHub Action (`korthout/backport-action`, see
`.github/workflows/backport.yml`; human docs in `CONTRIBUTING.md`, section "Backporting changes").

**Never create backport PRs, branches, or cherry-picks yourself, and never offer to.** Trigger a
backport only through the automation: a label or a `/backport` comment.

## Start a backport

Add one `backport stable/X.Y` label per target branch (e.g. `backport stable/8.7`). Multiple labels
create multiple backport PRs.

| PR state     | How it runs                                       |
| ------------ | ------------------------------------------------- |
| Not merged   | The action runs on merge. Add the labels before.  |
| Already merged | Add the labels, then comment `/backport` on the PR. |

## Release branches (during a code freeze)

While a release is frozen, the RC is built from `release-X.Y.Z` (e.g.
`release-8.11.0-alpha1`), not from `main` or `stable/X.Y`, so a change merged to the line's
normal branch does not reach it. Those targets get a `backport release-X.Y.Z` label —
same action, same rules as above.

Only approved changes go onto a frozen release branch: ask in the freeze thread before
adding the label.

The orchestration-cluster nightly fix agent adds this label to its own fix PRs when the
release branch still holds the test area the fix touches
(`.github/scripts/nightly-fix-release-backport.sh`), so a nightly fix PR may already carry
one.

## Success

The action opens the backport PR. A bot approves it and enables auto-merge once CI passes, but only
when the backport touches `zeebe/`, `clients/`, a `pom.xml`, shared build/webapps config, or
CI-relevant `.github/` files. The approval job lives in the Zeebe CI workflow and is gated on those
paths. A backport touching none of them needs a human review and merge.

## Conflicts

On a conflict the action opens a **draft** PR with the conflict markers committed as-is, plus a
comment with resolution steps.

When asked to backport, first check whether such a draft PR already exists for the original PR.

You may resolve conflicts, but only on a draft PR the action already opened:

1. Check out the draft PR's branch.
2. Fix the conflicts and remove all conflict markers.
3. Explain the resolution in the commit body.
4. Push to the draft PR's branch and follow the action's comment for what to do next.

Never resolve by opening a separate PR or cherry-picking onto a new branch.
