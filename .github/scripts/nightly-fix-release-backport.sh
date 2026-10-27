#!/usr/bin/env bash
# nightly-fix-release-backport.sh — decide whether a nightly fix PR should also be
# backported to the release branch that is currently frozen for a release.
#
# Why this exists:
# Between a code freeze and the release, the RC is built from `release-X.Y.Z`, not
# from the branch the nightly fix agent works on. A test fix merged to main/stable in
# that window never reaches the RC, so the release branch stays red and somebody
# arranges the backport by hand in the nightly Slack thread.
#
# The whole job here is to separate two kinds of fix:
#   * portable  — the failing test exists on the release branch the same way, so the
#                 fix belongs there too. Label it; backport-action does the rest.
#   * main-only — the test (or the file holding it) was added after the cut, or the
#                 patch no longer applies. Backporting drags in a test for code the
#                 release does not contain. Leave it alone and say why.
#
# Design rules:
#   * Read-only. This script never labels, comments or pushes; it prints a verdict
#     and the caller acts on it. That keeps the decision testable offline.
#   * Fail CLOSED. Any check that errors — a ref that will not fetch, an unreadable
#     patch — is "do not backport", with the reason carried through to the verdict.
#     Mislabelling a main-only fix costs a conflicted draft PR somebody has to own.
#   * Quiet outside a freeze. No active release branch is the common case and must
#     produce no verdict line at all, so the nightly thread is unchanged.
#
# Usage (run from inside the git repo holding the fix branch):
#   nightly-fix-release-backport.sh <target_ref> <pr_number> <patch_file> <specs_file>
#
# Prints one JSON object on stdout:
#   {"pr":N,"release_ref":"release-X.Y.Z","decision":"eligible|skipped|none","reason":"..."}
#
# Environment:
#   REMOTE      git remote to resolve against (default: origin)
#   PAIRS_FILE  pre-resolved "release_ref<TAB>target_ref" lines; when unset the
#               resolver in ../mergeback is run. Tests set this to stay offline.
set -euo pipefail

REMOTE="${REMOTE:-origin}"

# Echo the active release branch whose merge-back target is $1, or nothing.
#
# The pairs come from the merge-back resolver, which already defines "active" as
# "version greater than the highest tag on its X.Y line" and fails closed on a
# lookup error. Matching on the TARGET is what scopes a dispatch to its own line: a
# main dispatch may only feed a release branch that merges back to main.
resolve_release_ref() {
  local target_ref="$1" pairs_file="$2"
  [ -s "$pairs_file" ] || return 0
  awk -F'\t' -v t="$target_ref" '$2 == t { print $1; exit }' "$pairs_file"
}

# Echo every path the patch MODIFIES or DELETES that is absent from $1.
#
# Added files are deliberately not checked: a fix that introduces a new helper is
# still portable, and its path is absent from the release branch by definition. What
# matters is that everything the patch expects to already be there, is.
missing_paths() {
  local release_ref="$1" patch_file="$2" pre post
  while IFS= read -r pre && IFS= read -r post; do
    pre="${pre#--- }"
    post="${post#+++ }"
    # /dev/null as the preimage means the patch creates the file.
    [ "$pre" = "/dev/null" ] && continue
    local path="${post#b/}"
    [ "$post" = "/dev/null" ] && path="${pre#a/}"
    git cat-file -e "${release_ref}:${path}" 2>/dev/null || printf '%s\n' "$path"
  done < <(grep -E '^(---|\+\+\+) ' "$patch_file")
}

# Echo "<file> › <test name>" for every dispatched failing test whose title is NOT
# present in that file on $1.
#
# This is the check the file-existence one cannot make: a spec file can survive the
# cut while the test inside it is post-freeze work. Fixed-string match, because test
# titles carry regex metacharacters. A spec whose file is missing entirely is already
# reported by missing_paths, so it is skipped here rather than reported twice.
missing_tests() {
  local release_ref="$1" specs_file="$2"
  [ -s "$specs_file" ] || return 0
  while IFS=$'\t' read -r file test_name; do
    [ -z "$file" ] && continue
    git cat-file -e "${release_ref}:${file}" 2>/dev/null || continue
    git show "${release_ref}:${file}" 2>/dev/null | grep -qF "$test_name" \
      || printf '%s › %s\n' "$file" "$test_name"
  done < <(jq -r '.[]? | select(.file and .test_name) | "\(.file)\t\(.test_name)"' "$specs_file")
}

# True when the patch applies to $1.
#
# Three-way so the answer is "does this change still make sense here", not "is the
# context byte-identical" — unrelated drift on the release branch must not veto a
# portable fix. It runs a REAL apply in a throwaway worktree, not `--check`:
# `git apply --check --3way` reports success on a patch that applies only with
# conflict markers, which is exactly the case this gate exists to catch. The
# worktree is discarded either way, so nothing is mutated.
patch_applies() {
  local release_ref="$1" patch_file="$2" tmp rc=0
  # The apply runs with -C "$tmp", where a relative patch path would resolve
  # against the worktree instead of the caller's directory.
  case "$patch_file" in /*) ;; *) patch_file="${PWD}/${patch_file}" ;; esac
  tmp="$(mktemp -d)"
  git worktree add --detach --quiet "$tmp" "$release_ref" 2>/dev/null || {
    rm -rf "$tmp"
    return 1
  }
  git -C "$tmp" apply --3way "$patch_file" >/dev/null 2>&1 || rc=1
  git worktree remove --force "$tmp" >/dev/null 2>&1 || rm -rf "$tmp"
  return "$rc"
}

verdict() {
  jq -cn --argjson pr "$1" --arg ref "$2" --arg decision "$3" --arg reason "$4" \
    '{pr: $pr, release_ref: $ref, decision: $decision, reason: $reason}'
}

# Entrypoint guard: everything below does git I/O, so the test suite can source the
# functions above without it running.
if [ "${BASH_SOURCE[0]}" != "${0}" ]; then
  # shellcheck disable=SC2317  # reached only when executed, not sourced
  return 0 2>/dev/null || exit 0
fi

target_ref="${1:?target ref required}"
pr_number="${2:?pr number required}"
patch_file="${3:?patch file required}"
specs_file="${4:-}"

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
pairs_file="${PAIRS_FILE:-}"
if [ -z "$pairs_file" ]; then
  pairs_file="$(mktemp)"
  # A resolver failure is "unknown", not "no freeze": stay quiet rather than claim
  # there is nothing to back port to.
  if ! "${script_dir}/mergeback/resolve-active-release-pairs.sh" "$REMOTE" > "$pairs_file" 2>/dev/null; then
    verdict "$pr_number" "" skipped "could not resolve active release branches"
    exit 0
  fi
fi

release_ref="$(resolve_release_ref "$target_ref" "$pairs_file")"
if [ -z "$release_ref" ]; then
  verdict "$pr_number" "" none "no active release branch for ${target_ref}"
  exit 0
fi

if ! git cat-file -e "${release_ref}^{commit}" 2>/dev/null; then
  if ! git fetch --no-tags --depth 1 "$REMOTE" "${release_ref}:${release_ref}" >/dev/null 2>&1; then
    verdict "$pr_number" "$release_ref" skipped "could not fetch ${release_ref}"
    exit 0
  fi
fi

if [ ! -s "$patch_file" ]; then
  verdict "$pr_number" "$release_ref" skipped "empty or unreadable patch"
  exit 0
fi

absent="$(missing_paths "$release_ref" "$patch_file" | paste -sd', ' -)"
if [ -n "$absent" ]; then
  verdict "$pr_number" "$release_ref" skipped "not on the release branch: ${absent}"
  exit 0
fi

if [ -n "$specs_file" ] && [ -f "$specs_file" ]; then
  absent_tests="$(missing_tests "$release_ref" "$specs_file" | paste -sd'; ' -)"
  if [ -n "$absent_tests" ]; then
    verdict "$pr_number" "$release_ref" skipped "test not on the release branch: ${absent_tests}"
    exit 0
  fi
fi

if ! patch_applies "$release_ref" "$patch_file"; then
  verdict "$pr_number" "$release_ref" skipped "patch does not apply to ${release_ref}"
  exit 0
fi

verdict "$pr_number" "$release_ref" eligible "same area present on ${release_ref}"
