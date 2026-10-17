#!/usr/bin/env bash
# nightly-fix-reopen.sh — "reopen a sweeper-closed fix PR instead of recreating
# it" helpers for the C8 Orchestration Cluster nightly triage dispatcher.
#
# Why this exists (camunda/team-test-automation#189):
# c8-orchestration-cluster-e2e-nightly-close-stale-fix-prs closes an unmerged
# nightly fix PR the next morning and — since the companion change to that
# workflow — no longer deletes its branch, so the branch and the in-progress fix
# survive. When the same version is still failing tonight, triage would
# otherwise open a brand-new PR re-deriving the same change from scratch.
# Reopening keeps its diff and its review comments.
#
# Ported from camunda/c8-cross-component-e2e-tests. A fix PR here is scoped to a
# VERSION, so the branch slot (fix/nightly-<version>-) identifies it, and the
# failing specs of that version are required to overlap before an older PR is
# adopted.
#
# Design rules, all of which the gates below enforce:
#   * Every gate is a reason NOT to reopen. The default is the pre-existing
#     behaviour: the fix agent opens a fresh PR. This feature must never be able
#     to turn a dispatch into a no-op.
#   * Fail CLOSED on unreadable evidence. "A human closed this" and "I cannot
#     tell who closed this" must produce the same answer, because reopening a PR
#     a reviewer deliberately rejected is the rudest thing this code could do.
#   * Read-only until the caller commits. Candidate selection makes no writes,
#     so a dispatcher can run the whole decision under DRY_RUN.
#
# Source it after REPO is set; every function tolerates `set -euo pipefail`.

# Master switch. Off by default: turn it on per dispatcher once a week of
# dry-run logs looks right.
REOPEN_CLOSED_PRS="${REOPEN_CLOSED_PRS:-false}"
# Observation mode, and the only way to GET those dry-run logs: candidate
# selection runs in full and prints its whole decision trace, but the caller must
# not commit. Selection is read-only, so this is safe by construction — it is the
# caller that owns `reopen_commit`, and the dispatcher checks this flag before
# calling it. Separate from REOPEN_CLOSED_PRS on purpose: with only the master
# switch, "off" evaluates nothing and "on" mutates PRs immediately, so there was
# no state in which the gates could be observed before they could act.
REOPEN_DRY_RUN="${REOPEN_DRY_RUN:-false}"
# "Recent" — wide enough to span a long weekend, short enough that a branch is
# not hopelessly behind main.
REOPEN_WINDOW_DAYS="${REOPEN_WINDOW_DAYS:-7}"
# Commits on main the branch is missing before it is written off as too stale.
REOPEN_MAX_BEHIND="${REOPEN_MAX_BEHIND:-150}"
# Ping-pong brake. The sweeper re-closes a reopened PR the next night (a bot
# reopen and a bot commit do not move its human-activity clock, by design), so
# the cycle is bounded here instead: a fix that has survived this many rounds
# without a human touching it needs review, not another reopen.
REOPEN_CAP="${REOPEN_CAP:-3}"
# Applied by the close-stale-fix-prs workflow when IT closes a PR.
STALE_CLOSED_LABEL="${STALE_CLOSED_LABEL:-auto-closed-stale}"
STALE_CLOSE_MARKER="${STALE_CLOSE_MARKER:-stale-sweeper-closed}"
REOPENED_LABEL="${REOPENED_LABEL:-reopened-by-triage}"
# A human asking for a PR to be left alone also means "do not resurrect it".
REOPEN_VETO_LABEL="${REOPEN_VETO_LABEL:-do-not-close}"
# Bot identities whose close may be reopened, space-separated and compared after
# normalisation (an `app/` prefix and a `[bot]` suffix are stripped). It is a SET,
# not one name, because the two bots here close PRs under different identities:
# the fix agent runs as the qa-processes app, while close-stale-fix-prs calls
# `gh pr close` with secrets.GITHUB_TOKEN and therefore closes as
# `github-actions[bot]`. Matching only one of them rejected every real sweeper
# close and made the whole reopen path dead code.
# This list is NOT what proves the sweeper closed the PR — the auto-closed-stale
# label or the stale-sweeper-closed marker does that, below. Its only job is to
# veto a close by anyone who is not a known bot, i.e. a human.
BOT_OWNERS="${BOT_OWNERS:-qa-processes github-actions}"

# Closed-but-NOT-merged bot fix PRs on this platform+version's branch slot,
# closed inside the recency window, newest close first. Emits "<number>\t<branch>".
#
# startswith, not contains: "saas-nightly-8.10-" CONTAINS "nightly-8.10-", so a
# contains test in the SM dispatcher would cheerfully adopt a SaaS PR.
# mergedAt == null matters twice: `gh pr list --state closed` includes merged
# PRs, `gh pr reopen` hard-fails on one, and a merged fix is already done.
reopen_candidates() {
  local prefix="$1" cutoff
  cutoff=$(date -u -d "${REOPEN_WINDOW_DAYS} days ago" +%FT%TZ 2>/dev/null \
    || date -u -v-"${REOPEN_WINDOW_DAYS}"d +%FT%TZ 2>/dev/null) || return 0
  gh pr list --repo "$REPO" \
    --label "failing-test-fix" --state closed --limit 50 \
    --json number,headRefName,headRefOid,closedAt,mergedAt,labels \
    --jq "[.[]
           | select(.mergedAt == null)
           | select(.closedAt >= \"${cutoff}\")
           | select(.headRefName | startswith(\"${prefix}\"))
           | select([.labels[].name] | index(\"${REOPEN_VETO_LABEL}\") | not)]
          | sort_by(.closedAt) | reverse
          | .[] | \"\(.number)\t\(.headRefName)\t\(.headRefOid)\"" 2>/dev/null || true
}

# Did OUR sweeper close this, or did a person close it deliberately?
# Two independent signals, either sufficient:
#   1. the auto-closed-stale label the sweeper applies at close time
#   2. the LAST `closed` timeline event's actor is a known bot AND a comment
#      carries the sweeper's marker
# The actor alone is not enough (the bot also OPENED the PR and could close its
# own); the marker alone is not enough (a human could quote the sweeper's text).
# Taking the LAST closed event means a human closing a triage-reopened PR wins
# permanently, which is exactly right.
closed_by_sweeper() {
  local n="$1" labels actor

  # The LAST close event decides, and it must be readable and bot-owned. This
  # check comes FIRST, before the label: the label can outlive the state it
  # described — triage reopens, the label removal fails, a reviewer later closes
  # the PR for real, and a label-first test would then reopen a PR a human
  # deliberately closed. That is the one outcome this function exists to prevent,
  # so an unreadable timeline is a refusal too.
  actor=$(gh api "repos/${REPO}/issues/${n}/timeline?per_page=100" --paginate --slurp \
            --jq '[.[][] | select(.event == "closed")] | last | .actor.login // ""' \
            2>/dev/null || echo "")
  actor="${actor#app/}"
  actor="${actor%\[bot\]}"
  [ -n "$actor" ] || return 1
  case " ${BOT_OWNERS} " in
    *" ${actor} "*) ;;
    *) return 1 ;;
  esac

  # Bot-closed. Now confirm it was the SWEEPER, not some other bot path: either
  # the label it applies, or the marker it writes into the close comment (which
  # also covers PRs closed before the label existed).
  labels=$(gh pr view "$n" --repo "$REPO" --json labels \
             --jq '[.labels[].name] | join(",")' 2>/dev/null || echo "")
  case ",${labels}," in *",${STALE_CLOSED_LABEL},"*) return 0 ;; esac

  gh pr view "$n" --repo "$REPO" --json comments \
    --jq '[.comments[].body] | join("\n")' 2>/dev/null \
    | grep -q "$STALE_CLOSE_MARKER"
}

# How many times this PR has already been reopened. Read off the timeline, not
# the ledger: it survives a ledger reset (the dispatchers DO reset a corrupt
# ledger to {}), survives the fingerprint changing, and is the same number a
# human sees on the PR. Fails to a huge value so unreadable history is never
# reopened past the cap.
reopen_count() {
  gh api "repos/${REPO}/issues/${1}/timeline?per_page=100" --paginate --slurp \
    --jq '[.[][] | select(.event == "reopened")] | length' 2>/dev/null || echo 999
}

branch_alive() {
  gh api "repos/${REPO}/git/ref/heads/${1}" >/dev/null 2>&1
}

# Recreate a deleted head branch at the PR's recorded head commit, so the PR can
# be reopened. `gh pr reopen` needs a LIVE head ref and fails without one —
# verified on a throwaway PR: close --delete-branch, reopen → "Could not open
# the pull request"; recreate the ref at the same SHA, reopen → succeeds, with
# the diff intact. The commits themselves are never lost: GitHub keeps them
# reachable as refs/pull/<n>/head for the life of the PR, which is where the
# recorded head SHA still resolves.
#
# A no-op when the branch still exists. Non-zero only if the ref could not be
# created, which the caller treats as "do not reopen".
restore_branch() {
  local branch="$1" sha="$2"
  branch_alive "$branch" && return 0
  [ -n "$sha" ] || return 1
  gh api -X POST "repos/${REPO}/git/refs" \
    -f ref="refs/heads/${branch}" -f sha="$sha" >/dev/null 2>&1
}

# Commits on main that this ref does not have. Takes a SHA as readily as a
# branch name, which matters because the branch is usually GONE by the time we
# look: the sweeper deletes it on close. `gh pr view --json mergeable` is no
# substitute — GitHub does not keep it fresh for a closed PR.
behind_by() {
  gh api "repos/${REPO}/compare/main...${1}" --jq '.behind_by // 99999' \
    2>/dev/null || echo 99999
}

# How many of THIS dispatch's failing spec files the closed PR already touched.
# The caller passes tests/** paths ONLY: overlap on pages/**, fixtures/** or
# utils/** is meaningless here, because nearly every fix PR touches a shared
# page object and matching on those would merge unrelated work.
#
# `$want[] as $w` is load-bearing: in `select($have | index(.))` the pipe
# rebinds `.` to $have, so index() searches the array for itself and returns 0
# — truthy — for every candidate. That silently reported full overlap.
spec_overlap() {
  local n="$1" want_json="$2"
  gh pr view "$n" --repo "$REPO" --json files --jq '[.files[].path]' 2>/dev/null \
    | jq -r --argjson want "$want_json" \
        '. as $have | [$want[] as $w | select($have | index($w))] | length' 2>/dev/null \
    || echo 0
}

# Pick the first acceptable candidate. Prints "<number>\t<branch>\t<prior_reopens>"
# on stdout and the decision trace on stderr, so a caller can log the trace and
# capture the answer. Prints nothing when no candidate passes.
#
# $1 branch prefix, $2 JSON array of this dispatch's tests/** paths ("[]" to
# skip the overlap gate — used where the PR carries fp= coverage markers that
# the caller matches instead).
reopen_pick() {
  local prefix="$1" specs_json="${2:-[]}" n branch sha rc bb ov
  # Dry run evaluates every gate and logs the trace; only the caller's
  # reopen_commit is withheld.
  [ "$REOPEN_CLOSED_PRS" = "true" ] || [ "$REOPEN_DRY_RUN" = "true" ] || return 0

  while IFS=$'\t' read -r n branch sha; do
    [ -z "${n:-}" ] && continue
    if ! closed_by_sweeper "$n"; then
      echo "    reopen: skip #${n} — closed by a human, or who closed it is unreadable" >&2
      continue
    fi
    # The branch being gone is NOT a disqualifier: the sweeper deletes it on
    # close, and reopen_commit recreates it from this SHA. Only a missing SHA
    # is fatal, because then there is nothing to restore it to.
    if [ -z "${sha:-}" ]; then
      echo "    reopen: skip #${n} — no recorded head commit to restore the branch to" >&2
      continue
    fi
    rc=$(reopen_count "$n")
    if [ "${rc:-999}" -ge "$REOPEN_CAP" ]; then
      echo "    reopen: skip #${n} — already reopened ${rc}x (cap ${REOPEN_CAP}); it needs a human, not another round" >&2
      continue
    fi
    # Measured on the SHA, not the branch name — the branch is normally deleted.
    bb=$(behind_by "$sha")
    if [ "${bb:-99999}" -gt "$REOPEN_MAX_BEHIND" ]; then
      echo "    reopen: skip #${n} — ${bb} commits behind main (max ${REOPEN_MAX_BEHIND})" >&2
      continue
    fi
    if [ "$specs_json" != "[]" ]; then
      ov=$(spec_overlap "$n" "$specs_json")
      if [ "${ov:-0}" -lt 1 ]; then
        echo "    reopen: skip #${n} — touches none of this dispatch's spec files" >&2
        continue
      fi
      echo "    reopen: #${n} (${branch}) matches on ${ov} spec file(s)" >&2
    else
      echo "    reopen: #${n} (${branch}) is a candidate" >&2
    fi
    printf '%s\t%s\t%s\t%s\n' "$n" "$branch" "$sha" "$rc"
    return 0
  done < <(reopen_candidates "$prefix")
  return 0
}

# Commit the reopen: restore the head branch, reopen, swap the labels, comment.
# Returns non-zero if the branch could not be restored or the reopen failed, so
# the caller falls back to a fresh PR. Label and comment failures are non-fatal
# — the PR is open either way.
# $1 number, $2 branch, $3 head sha, $4 body of the comment.
reopen_commit() {
  local n="$1" branch="$2" sha="$3" body="$4"
  if ! restore_branch "$branch" "$sha"; then
    echo "    reopen: could not restore ${branch} at ${sha:0:12} for #${n}" >&2
    return 1
  fi
  gh pr reopen "$n" --repo "$REPO" >/dev/null 2>&1 || return 1
  gh pr edit "$n" --repo "$REPO" \
    --remove-label "$STALE_CLOSED_LABEL" --add-label "$REOPENED_LABEL" >/dev/null 2>&1 || true
  gh pr comment "$n" --repo "$REPO" --body "$body" >/dev/null 2>&1 || true
  return 0
}

# Is there already an OPEN fix PR on this branch slot? When there is, the fix
# agent's own post-queue lookup will find it and decide (skip / stack / new), so
# resurrecting a closed PR on top would leave the version with two competing
# PRs — the opposite of the point. Treat an unreadable answer as "yes" and skip
# the reopen: a missed reopen costs a duplicate we already tolerate today, a
# wrong one costs a confusing second PR.
open_pr_exists() {
  local prefix="$1" out
  out=$(gh pr list --repo "$REPO" \
    --label "failing-test-fix" --state open --limit 50 \
    --json number,headRefName \
    --jq "[.[] | select(.headRefName | startswith(\"${prefix}\"))] | length" \
    2>/dev/null) || return 0
  [ -n "$out" ] || return 0
  [ "$out" -gt 0 ]
}
