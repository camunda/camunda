#!/usr/bin/env bash
# Resolve the currently-active release branches and their merge-back targets.
#
# A branch `release-X.Y.Z[-alphaN]` is ACTIVE when its version is strictly greater than the highest
# release tag on its X.Y line — i.e. not yet released. A stale branch is stale precisely because its
# version is already tagged, so this one comparison filters them out with no allowlist.
#
# Target mapping:
#   - stable/X.Y exists -> target = stable/X.Y  (maintenance release merges back to its stable line)
#   - else              -> target = main        (pre-branch alpha of the next minor merges to main)
#
# Read-only: git ls-remote only. Prints one "release_ref<TAB>target_ref" per line; no output means
# no active release (quiet no-op).
set -euo pipefail

# Echo the greater of two versions, treating an -alpha suffix as LESS than the same core release
# (X.Y.Z-alphaN < X.Y.Z). `sort -V` gets that ordering wrong, so equal cores are handled here.
# Defined before the entrypoint guard so the test suite can source and unit-test it offline.
version_max() {
  local a="$1" b="$2"
  local a_core="${a%%-*}" b_core="${b%%-*}"
  if [ "$a_core" = "$b_core" ] && [ "$a" != "$b" ]; then
    # Same core: a plain release (no suffix) outranks any -alpha of that core.
    case "$a" in *-alpha*) [ "$a_core" = "$b" ] && { echo "$b"; return; } ;; esac
    case "$b" in *-alpha*) [ "$b_core" = "$a" ] && { echo "$a"; return; } ;; esac
  fi
  printf '%s\n%s\n' "$a" "$b" | sort -V | tail -1
}

# Entrypoint guard: run the resolver only when executed directly, not when sourced by the test
# suite (everything below does network I/O).
if [ "${BASH_SOURCE[0]}" != "${0}" ]; then
  # shellcheck disable=SC2317  # reached only when the script is executed, not sourced
  return 0 2>/dev/null || exit 0
fi

remote="${1:-origin}"

# Strict: X.Y.Z or X.Y.Z-alphaN only. Deliberately excludes the junk namespace
# (release-*-benchmark, release-*-workflow, backport/release-*, docs/release-*, etc.).
release_regex='^release-[0-9]+\.[0-9]+\.[0-9]+(-alpha[0-9]+)?$'

# Fail CLOSED: a failed ls-remote is "unknown", not "none" — collapsing it to empty would hide
# every active release. The trailing `grep || true` is fine: once ls-remote succeeds, zero matching
# branches is a legitimate answer.
if ! raw_branches="$(git ls-remote --heads "$remote" 'release-*')"; then
  echo "resolve-active-release-pairs: 'git ls-remote --heads $remote release-*' failed" >&2
  exit 1
fi
branches="$(printf '%s\n' "$raw_branches" \
  | awk '{print $2}' | sed 's#refs/heads/##' \
  | grep -E "$release_regex" || true)"

[ -z "$branches" ] && exit 0

# minor "X.Y" -> highest active (untagged, ahead-of-latest-tag) release version on that line.
declare -A active_for_minor

while IFS= read -r branch; do
  [ -z "$branch" ] && continue
  version="${branch#release-}"
  # Reduce X.Y.Z or X.Y.Z-alphaN to its X.Y minor.
  minor="${version%.*}"    # strip the trailing .Z
  minor="${minor%%-*}"     # strip any -alphaN suffix

  # Highest tag on this X.Y line (plain X.Y.Z and X.Y.Z-alphaN tags), semver-ordered.
  # Fail CLOSED: a failed tag listing would leave highest_tag empty, which the check below
  # reads as "brand-new minor -> active" and could RESURRECT an already-released branch.
  if ! raw_tags="$(git ls-remote --tags "$remote" "${minor}.*")"; then
    echo "resolve-active-release-pairs: 'git ls-remote --tags $remote ${minor}.*' failed" >&2
    exit 1
  fi
  highest_tag=""
  while IFS= read -r tag; do
    [ -z "$tag" ] && continue
    if [ -z "$highest_tag" ]; then
      highest_tag="$tag"
    else
      highest_tag="$(version_max "$highest_tag" "$tag")"
    fi
  done <<< "$(printf '%s\n' "$raw_tags" \
              | awk '{print $2}' | sed 's#refs/tags/##; s/\^{}//' \
              | grep -E "^${minor}\.[0-9]+(-alpha[0-9]+)?$" || true)"

  # Active iff branch version strictly greater than the highest existing tag
  # (no tag on the line -> brand-new minor -> active).
  if [ -n "$highest_tag" ]; then
    greatest="$(version_max "$version" "$highest_tag")"
    { [ "$greatest" = "$highest_tag" ] && [ "$version" != "$highest_tag" ]; } && continue
    [ "$version" = "$highest_tag" ] && continue   # already released
  fi

  # Keep only the HIGHEST active branch per X.Y line. Multiple untagged release branches can coexist
  # (e.g. an abandoned release-8.9.11 after release-8.9.12 branched); the greatest is the real
  # in-flight release, the rest are presumed abandoned and would otherwise alert forever.
  existing="${active_for_minor[$minor]:-}"
  if [ -z "$existing" ]; then
    active_for_minor[$minor]="$version"
  else
    active_for_minor[$minor]="$(version_max "$existing" "$version")"
  fi
done <<< "$branches"

# Emit one pair per active line: map the winning release branch to its merge-back target.
for minor in "${!active_for_minor[@]}"; do
  version="${active_for_minor[$minor]}"
  branch="release-${version}"

  # Map to target: stable/X.Y if it exists, else main. Fail CLOSED: a failed check must abort, not
  # fall through to main and merge the release in the WRONG direction.
  if ! stable_ls="$(git ls-remote --heads "$remote" "stable/${minor}")"; then
    echo "resolve-active-release-pairs: 'git ls-remote --heads $remote stable/${minor}' failed" >&2
    exit 1
  fi
  if [ -n "$stable_ls" ]; then
    target="stable/${minor}"
  else
    target="main"
  fi

  printf '%s\t%s\n' "$branch" "$target"
done
