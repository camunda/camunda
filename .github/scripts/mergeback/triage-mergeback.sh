#!/usr/bin/env bash
# Triage merge-back conflicts into actionable guidance.
#
# Merge-back = merging a RELEASE branch forward into a TARGET (main or stable/8.x). Consumes the
# detector contract from detect-mergeback-conflict.sh ({"status":..,"paths":[..]}), classifies each
# conflicted path into one bucket, and emits a JSON document (the single source of truth for the
# renderer). No prose output, nothing here is regex-scraped.
#
# Buckets:
#   version_build      - pom.xml / lockfiles with only mechanical version bumps. Quiet.
#   version_build_gap  - a version/build file touched by a non-noise commit missing on target.
#   source_missing     - a substantive commit may be missing from one side. Two directions, forward
#                        first (cheap), reverse only if forward is empty:
#                          forward (missing_on:target)  - a commit touching this path is on RELEASE
#                            but not on TARGET (git cherry patch-id). The classic gap.
#                          reverse (missing_on:release) - a commit on TARGET but not on RELEASE that
#                            authored the path's conflicting lines (a re-fix never backported). Tied
#                            to the clash itself, never a blanket "target is ahead" sweep.
#   needs_review       - merge-tree flagged a conflict but no commit can be blamed (a deletion or an
#                        insertion on the clashing lines). No missing-commit claim; a human just
#                        resolves it. Keeps modify/delete and add/add out of source_drift.
#   source_drift       - no actionable missing commit identified after noise filtering. Quiet.
#
# action_count = |source_missing| + |version_build_gap| + |needs_review|; gate downstream on > 0.
#
# Read-only (git reads only). Requires jq.
#
# Usage:
#   triage-mergeback.sh <target_ref> <release_ref> [detect_json_file]   # JSON from file or stdin
# The detector contract is validated up front (fail loud on any malformed shape). A conflict with
# no named path arrives as status=conflict + empty paths and is surfaced as actionable directly.
set -euo pipefail

target_ref="${1:?usage: triage-mergeback.sh <target_ref> <release_ref> [detect_json_file]}"
release_ref="${2:?usage: triage-mergeback.sh <target_ref> <release_ref> [detect_json_file]}"
detect_json_file="${3:-}"

# Version/build basenames -> mechanical bucket. Everything else defaults to source.
is_version_build() {
  case "${1##*/}" in
    pom.xml|package.json|package-lock.json|yarn.lock|pnpm-lock.yaml|go.sum) return 0 ;;
    *) return 1 ;;
  esac
}

# Ownership lookup via the codeowners-plus CLI against `.codeowners` (last-match-wins). Best-effort:
# empty when the CLI is absent or the path is unowned — a wrong owner in an alert is worse than none.
# The workflow installs the CLI (.github/actions/codeowners-setup-cli) beforehand.
# Note: the CLI resolves against the checked-out tree, so a path absent from that revision (e.g. a
# modify/delete conflict) yields empty ownership.
owner_for() {
  local path="$1"
  command -v codeowners-cli >/dev/null 2>&1 || return 0
  # Query the JSON interface (stable shape) and anchor resolution at the repo root. jq extracts the
  # required owners as space-separated @-tokens. `|| true` absorbs a non-zero exit under pipefail —
  # empty ownership beats a wrong one.
  codeowners-cli owner --root ./ --format json "$path" 2>/dev/null \
    | jq -r --arg p "$path" '(.[$p].required // []) | join(" ")' 2>/dev/null || true
}

# Read the detector's structured contract {status, paths}.
detect_json="$(if [ -n "$detect_json_file" ]; then cat "$detect_json_file"; else cat; fi)"

# Validate the whole contract up front — a malformed document must fail loud, never collapse to a
# silent "clean". error() aborts on the first violation: root an object, .status exactly
# clean|conflict, .paths an array of strings, and the two consistent (clean forbids any path).
if ! contract_err="$(jq '
    if type != "object" then error("root is not a JSON object")
    elif (.status | type) != "string" then error(".status is missing or not a string")
    elif (.status != "clean" and .status != "conflict") then error(".status is not clean|conflict")
    elif (.paths | type) != "array" then error(".paths is missing or not an array")
    elif any(.paths[]; type != "string") then error(".paths contains a non-string element")
    elif (.status == "clean" and (.paths | length) > 0) then error(".status is clean but .paths is non-empty")
    else empty end' <<< "$detect_json" 2>&1)"; then
  echo "triage-mergeback: malformed detector contract: ${contract_err}" >&2
  exit 1
fi
status="$(jq -r '.status' <<< "$detect_json")"

# Read paths NUL-delimited so any path (spaces/newlines) survives intact.
paths=()
while IFS= read -r -d '' p; do
  [ -z "$p" ] && continue
  paths+=("$p")
done < <(jq -j '.paths[] + "\u0000"' <<< "$detect_json")

# CLEAN -> emit the clean JSON shape so the gate reads uniformly via jq (action_count == 0).
if [ "$status" = clean ]; then
  jq -n --arg release_ref "$release_ref" --arg target_ref "$target_ref" '{
    release_ref: $release_ref, target_ref: $target_ref,
    conflicting_paths: 0, action_count: 0,
    verdict: "merge-back is clean — no conflicting paths.",
    source_missing: [], version_build_gap: [], needs_review: [], source_drift: [], version_build: []
  }'
  exit 0
fi

# Conflict with no named paths (e.g. directory-rename): must still surface as actionable, never as
# a false all-clear.
if [ "${#paths[@]}" -eq 0 ]; then
  jq -n --arg release_ref "$release_ref" --arg target_ref "$target_ref" '{
    release_ref: $release_ref, target_ref: $target_ref,
    conflicting_paths: 1, action_count: 1,
    verdict: "merge-back conflicts but merge-tree reported no named paths — needs manual review.",
    source_missing: [{
      path: "(unclassified conflict — merge-tree reported a conflict with no named paths; inspect the run and merge manually)",
      owner: "", commits: []
    }],
    version_build_gap: [], needs_review: [], source_drift: [], version_build: []
  }'
  exit 0
fi

# RELEASE commits whose patch is NOT on TARGET (git cherry '+') — genuinely missing on target.
# One git call, reused per path.
missing_shas="$(git cherry "$target_ref" "$release_ref" 2>/dev/null \
  | awk '/^\+/ {print $2}')"

# TARGET commits whose patch is NOT on RELEASE. Used only as a membership set: the reverse pass
# blames a path's conflicting lines to candidate commits, then asks "is this one missing on
# release?" here. Patch-id equivalence means a commit cherry-picked under a new SHA counts present.
reverse_missing_shas="$(git cherry "$release_ref" "$target_ref" 2>/dev/null \
  | awk '/^\+/ {print $2}')"

# Common ancestor for the three-way overlap that localises conflict lines (empty if none/unrelated).
merge_base_ref="$(git merge-base "$target_ref" "$release_ref" 2>/dev/null || true)"

# Does commit <sha> touch <path>?
commit_touches() {
  [ -n "$(git diff-tree --no-commit-id --name-only -r "$1" -- "$2" 2>/dev/null)" ]
}

# Is <sha> automation noise, not a substantive change? True when:
#   (a) the release plugin authored it (version bumps),
#   (b) its author is github-actions (identity bumps, java-compat, etc.), or
#   (c) it reverts a github-actions commit (churn that nets to zero).
commit_is_automation() { git log -1 --format='%an %ae' "$1" 2>/dev/null | grep -qi 'github-actions'; }
commit_is_release_noise() {
  local subject body reverted
  subject="$(git log -1 --format='%s' "$1" 2>/dev/null)"
  case "$subject" in *'[maven-release-plugin]'*) return 0 ;; esac
  commit_is_automation "$1" && return 0
  # Revert of an automation commit -> noise. Body has "This reverts commit <sha>."
  body="$(git log -1 --format='%B' "$1" 2>/dev/null)"
  reverted="$(printf '%s\n' "$body" | sed -n 's/^This reverts commit \([0-9a-f]\{7,40\}\).*/\1/p' | head -1)"
  if [ -n "$reverted" ] && commit_is_automation "$reverted"; then
    return 0
  fi
  return 1
}

# Emit {sha,author,subject,missing_on} for <sha>. $2 = the side it is missing on ("target" = on
# release but not target; "release" = on target but not release). NUL-delimited fields so a '|' in
# author/subject can't mis-split; the trailing %x00 absorbs git's end-of-entry newline.
commit_detail_json() {
  git log -1 --format='%h%x00%an%x00%s%x00' "$1" 2>/dev/null \
    | jq -Rsc --arg missing_on "$2" 'split("\u0000") | {sha: .[0], author: .[1], subject: .[2], missing_on: $missing_on}'
}

# Reverse attribution for a source path. Emits commit_detail_json objects (missing_on:release) for
# TARGET commits that authored the path's conflicting lines and are missing on release. Conflicting
# lines are found by three-way overlap: a target hunk whose base range overlaps a release hunk's
# base range means both sides edited the same original region. Only those target lines are blamed,
# so a target that merely moved ahead elsewhere never matches and can't flood.
reverse_conflict_hits() {
  local p="$1"
  [ -n "$merge_base_ref" ] || return 0
  # Release-side base ranges (old-side of base->release), one "start end" per hunk in base coords.
  local rel_ranges
  rel_ranges="$(git diff -U0 "$merge_base_ref" "$release_ref" -- "$p" 2>/dev/null \
    | awk '/^@@ / {
        split($2, base_fields, ","); base_start = substr(base_fields[1], 2) + 0; base_count = (base_fields[2] == "" ? 1 : base_fields[2]) + 0
        if (base_count == 0) next        # pure release insertion: no base span to overlap (add/add gap)
        print base_start, base_start + base_count - 1
      }')"
  [ -n "$rel_ranges" ] || return 0
  # Target hunks whose base range overlaps a release base range -> emit their TARGET (new-side) ranges.
  local target_ranges
  target_ranges="$(git diff -U0 "$merge_base_ref" "$target_ref" -- "$p" 2>/dev/null \
    | awk -v rr="$rel_ranges" '
        BEGIN { nr = split(rr, L, "\n"); for (i = 1; i <= nr; i++) { split(L[i], f, " "); release_start[i] = f[1]; release_end[i] = f[2] } }
        /^@@ / {
          split($2, base_fields, ","); base_start = substr(base_fields[1], 2) + 0; base_count = (base_fields[2] == "" ? 1 : base_fields[2]) + 0
          split($3, target_fields, ","); target_start = substr(target_fields[1], 2) + 0; target_count = (target_fields[2] == "" ? 1 : target_fields[2]) + 0
          if (target_count == 0) next             # pure target deletion: no target line to blame
          base_end = (base_count == 0 ? base_start : base_start + base_count - 1)
          for (i = 1; i <= nr; i++) if (base_start <= release_end[i] && release_start[i] <= base_end) { print target_start "," (target_start + target_count - 1); break }
        }')"
  [ -n "$target_ranges" ] || return 0
  local range sha seen=" " out=""
  while IFS= read -r range; do
    [ -z "$range" ] && continue
    while IFS= read -r sha; do
      [ -z "$sha" ] && continue
      case "$seen" in *" $sha "*) continue ;; esac                 # de-dupe across ranges
      grep -qxF "$sha" <<< "$reverse_missing_shas" || continue     # missing on release (patch-id)
      commit_is_release_noise "$sha" && continue
      seen+="$sha "
      out+="$(commit_detail_json "$sha" release)"$'\n'
    done < <(git blame --porcelain -L "$range" "$target_ref" -- "$p" 2>/dev/null | grep -oE '^[0-9a-f]{40}')
  done <<< "$target_ranges"
  printf '%s' "$out"
}

# Blind-spot detector for the reverse direction. Two conflict shapes leave no line for
# reverse_conflict_hits to blame, so it returns empty and the path would wrongly fall through to
# source_drift (benign): (1) modify/delete — target deleted lines release still edits; (2) insertion
# — release and/or target inserted at the same spot. merge-tree already flagged the conflict, so once
# no commit is blamable this decides benign drift vs blind-spot conflict (a human must resolve).
# Returns 0 (blind spot found) / 1 (none).
reverse_conflict_blindspot() {
  local p="$1"
  [ -n "$merge_base_ref" ] || return 1
  # Release-side base intervals; insertions kept as zero-width points (ins=1) so an insertion edge is
  # recognisable rather than dropped.
  local rel
  rel="$(git diff -U0 "$merge_base_ref" "$release_ref" -- "$p" 2>/dev/null \
    | awk '/^@@ / {
        split($2, base_fields, ","); base_start = substr(base_fields[1], 2) + 0; base_count = (base_fields[2] == "" ? 1 : base_fields[2]) + 0
        if (base_count == 0) print base_start, base_start, 1            # release insertion point
        else                 print base_start, base_start + base_count - 1, 0    # release modification/deletion span
      }')"
  [ -n "$rel" ] || return 1
  # A target hunk whose base range overlaps a release base range is a clash. It is a BLIND SPOT (no
  # blamable line) when the clashing edit is a target deletion, a target insertion, or a release
  # insertion — the three zero-width shapes the blame pass skips. Pure modification-vs-modification
  # overlaps are NOT blind spots (they are blamable), so genuine text drift stays in source_drift.
  git diff -U0 "$merge_base_ref" "$target_ref" -- "$p" 2>/dev/null \
    | awk -v rel="$rel" '
        BEGIN {
          n = split(rel, L, "\n")
          for (i = 1; i <= n; i++) { if (L[i] == "") continue; split(L[i], f, " "); release_start[i]=f[1]; release_end[i]=f[2]; release_ins[i]=f[3] }
        }
        /^@@ / {
          split($2, base_fields, ","); base_start = substr(base_fields[1], 2) + 0; base_count = (base_fields[2] == "" ? 1 : base_fields[2]) + 0
          split($3, target_fields, ","); target_count = (target_fields[2] == "" ? 1 : target_fields[2]) + 0
          target_ins = (base_count == 0) ? 1 : 0    # target insertion (no base span)
          target_del = (target_count == 0) ? 1 : 0  # target deletion (no target line to blame)
          base_end = (base_count == 0) ? base_start : base_start + base_count - 1
          for (i = 1; i <= n; i++) {
            if (L[i] == "") continue
            if (base_start <= release_end[i] && release_start[i] <= base_end && (target_del || target_ins || release_ins[i])) { found = 1; exit }
          }
        }
        END { exit (found ? 0 : 1) }'
}

version_build=()
version_build_gap=()   # version/build files touched by a non-noise commit missing on target
source_missing=()   # "path" entries; commit detail JSON accumulated per path below
needs_review=()   # merge-tree conflicts with no blamable commit (blind spot) — a human must resolve
source_drift=()
declare -A missing_detail   # path -> newline-separated JSON objects {sha,author,subject}

for p in "${paths[@]}"; do
  [ -z "$p" ] && continue
  if is_version_build "$p"; then
    # Usually mechanical bumps, but a human-authored build change can hide here.
    # Same patch-id test, ignoring release-plugin version-bump noise.
    vb_hits=""
    while IFS= read -r sha; do
      [ -z "$sha" ] && continue
      commit_is_release_noise "$sha" && continue
      if commit_touches "$sha" "$p"; then
        vb_hits+="$(commit_detail_json "$sha" target)"$'\n'
      fi
    done <<< "$missing_shas"
    # NB: no REVERSE check for version/build. main's pom.xml (and lockfiles) diverge from every
    # release branch as a matter of course, so a reverse look would flag essentially every
    # mechanical version conflict — the exact flood we avoid. These stay forward-only mechanical.
    if [ -n "$vb_hits" ]; then
      version_build_gap+=("$p")
      missing_detail[$p]="$vb_hits"
    else
      version_build+=("$p")
    fi
    continue
  fi
  # Source path. Forward first (cheap, reuses missing_shas): release commits missing on target that
  # touch it. A forward hit is already actionable, so skip the expensive reverse pass; only when
  # forward is empty do we run the conflict-tied reverse look (a target re-fix never backported).
  hits=""
  while IFS= read -r sha; do
    [ -z "$sha" ] && continue
    commit_is_release_noise "$sha" && continue
    if commit_touches "$sha" "$p"; then
      hits+="$(commit_detail_json "$sha" target)"$'\n'
    fi
  done <<< "$missing_shas"
  if [ -z "$hits" ]; then
    hits="$(reverse_conflict_hits "$p")"
  fi
  if [ -n "$hits" ]; then
    source_missing+=("$p")
    missing_detail[$p]="$hits"
  elif reverse_conflict_blindspot "$p"; then
    # merge-tree conflicted but no commit is blamable (modify/delete or insertion): not a missing-commit
    # alarm, but not proven-benign drift either — surface it so a human resolves the conflict.
    needs_review+=("$p")
  else
    source_drift+=("$p")
  fi
done

# ---------------------------------------------------------------------------
# Emit the JSON contract (single source of truth). Raw strings go through jq --arg (safely
# escaped); the renderer handles display (e.g. backticking owners/authors so they never ping).
# ---------------------------------------------------------------------------

# JSON array of {path, owner, commits:[{sha,author,subject}]} from a path list; commit detail
# (already JSON objects) comes from missing_detail.
build_actionable() {
  local p owner commits_json
  for p in "$@"; do
    owner="$(owner_for "$p")"
    # missing_detail[$p] is newline-separated JSON objects; slurp them into an array.
    commits_json="$(printf '%s' "${missing_detail[$p]:-}" | jq -sc '.')"
    jq -nc --arg path "$p" --arg owner "$owner" --argjson commits "${commits_json:-[]}" \
      '{path: $path, owner: $owner, commits: $commits}'
  done | jq -sc '.'
}

# Build a JSON array of plain path strings.
build_string_array() {
  local x
  for x in "$@"; do jq -nc --arg s "$x" '$s'; done | jq -sc '.'
}

conflicting_paths="${#paths[@]}"
action_count=$(( ${#source_missing[@]} + ${#version_build_gap[@]} + ${#needs_review[@]} ))
if [ "$action_count" -eq 0 ]; then
  verdict="no mid-release review identified — conflicts are known noise or text drift."
else
  verdict="$action_count path(s) need human review before merge-back."
fi

# Guard empty-array expansion under set -u; pass nothing when a bucket is empty.
sm_json="$(build_actionable ${source_missing[@]+"${source_missing[@]}"})"
vbg_json="$(build_actionable ${version_build_gap[@]+"${version_build_gap[@]}"})"
nr_json="$(build_actionable ${needs_review[@]+"${needs_review[@]}"})"
sd_json="$(build_string_array ${source_drift[@]+"${source_drift[@]}"})"
vb_json="$(build_string_array ${version_build[@]+"${version_build[@]}"})"

jq -n \
  --arg release_ref "$release_ref" \
  --arg target_ref "$target_ref" \
  --argjson conflicting_paths "$conflicting_paths" \
  --argjson action_count "$action_count" \
  --arg verdict "$verdict" \
  --argjson source_missing "${sm_json:-[]}" \
  --argjson version_build_gap "${vbg_json:-[]}" \
  --argjson needs_review "${nr_json:-[]}" \
  --argjson source_drift "${sd_json:-[]}" \
  --argjson version_build "${vb_json:-[]}" \
  '{
    release_ref: $release_ref,
    target_ref: $target_ref,
    conflicting_paths: $conflicting_paths,
    action_count: $action_count,
    verdict: $verdict,
    source_missing: $source_missing,
    version_build_gap: $version_build_gap,
    needs_review: $needs_review,
    source_drift: $source_drift,
    version_build: $version_build
  }'
