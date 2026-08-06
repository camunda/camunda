#!/usr/bin/env bash
# Triage merge-back conflicts into actionable guidance.
#
# Merge-back = merging a RELEASE branch forward into a TARGET (main or stable/8.x). Consumes the
# detector contract from detect-mergeback-conflict.sh:
#   {"status":"clean|conflict", "paths":[<conflicted path>, ...]}
# classifies each conflicted path into one bucket, and emits a machine-readable JSON document (the
# single source of truth). A downstream renderer turns JSON into a message; this script emits no
# prose and nothing here is regex-scraped.
#
#   version_build      - pom.xml / lockfiles. Mechanical ("theirs for versions"). No alarm.
#   source_missing     - THE ALARM: a substantive commit may be missing from one side. Two directions,
#                        tried FORWARD-first (cheap) then REVERSE only if forward is empty:
#                          forward  (missing_on:"target")  - a commit touching this path is on RELEASE
#                            but NOT on TARGET (git cherry patch-id '+'). The classic gap.
#                          reverse  (missing_on:"release") - a commit on TARGET but NOT on RELEASE that
#                            authored the path's CONFLICTING lines (INC-6953: a main/stable re-fix
#                            never backported). Tied to the clash itself, never a whole-file or blanket
#                            target-ahead sweep — see reverse_conflict_hits for the flood guard.
#   version_build_gap  - a version/build file carrying a REAL missing dep change (also actionable).
#   source_drift       - neither direction fires: changes already on both sides by patch-id. Text drift.
#
# action_count = |source_missing| + |version_build_gap|. Gate downstream on action_count > 0.
#
# Read-only (git reads only). Requires jq.
#
# Usage:
#   triage-mergeback.sh <target_ref> <release_ref> [detect_json_file]
#   detect-mergeback-conflict.sh <target> <release> | triage-mergeback.sh <target> <release>
# Detector JSON is read from detect_json_file if given, else stdin. The COMPLETE contract is
# validated up front (fail loud on any malformed shape); status/paths arrive as structured JSON,
# so there is no path-shape filtering and no sentinel: a conflict with no named path arrives as
# status=conflict + empty paths and is surfaced as actionable directly.
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

# Ownership lookup via the repo's CANONICAL source: the codeowners-plus CLI against `.codeowners`
# (last-match-wins, full glob). We don't hand-roll matching — a wrong owner in an alert is worse
# than none, so this is best-effort: empty when the CLI is absent or the path is unowned. The
# workflow installs codeowners-cli (.github/actions/codeowners-setup-cli) before this runs.
#
# KNOWN LIMITATION (accepted): the CLI resolves against the checked-out tree, so a modify/delete
# conflict for a path absent from that revision yields empty ownership. Fine under "empty beats
# wrong"; resolving against a revision where the path exists is a tracked follow-up.
owner_for() {
  local path="$1"
  command -v codeowners-cli >/dev/null 2>&1 || return 0
  # Prints "@team1 @team2" (or an unowned marker). Keep only @-tokens; `|| true` absorbs grep's
  # no-match exit under pipefail.
  codeowners-cli owner "$path" 2>/dev/null \
    | tr ' ' '\n' | grep -E '^@' | tr '\n' ' ' | sed 's/ *$//' || true
}

# Read the detector's structured contract {status, paths}.
detect_json="$(if [ -n "$detect_json_file" ]; then cat "$detect_json_file"; else cat; fi)"

# Validate the COMPLETE contract up front — a malformed document must fail loud, never collapse
# to a silent "clean" with an empty path list. jq parses the doc (invalid JSON aborts here) and
# error() exits non-zero with a message on the FIRST violation: root must be an object; .status
# must be exactly clean|conflict; .paths must be an array of strings; and the two must agree
# (status=clean forbids any path). The elif chain short-circuits, so each check runs only once
# its precondition holds (e.g. .paths[] is reached only after .paths is confirmed an array).
# Only after this do we trust .status and .paths below — no `.paths[]?` suppression needed.
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
    source_missing: [], version_build_gap: [], source_drift: [], version_build: []
  }'
  exit 0
fi

# Conflict with no named paths (e.g. directory-rename conflicts): status=conflict + empty paths.
# MUST surface as actionable — treating it as clean is the exact false all-clear we guard against.
if [ "${#paths[@]}" -eq 0 ]; then
  jq -n --arg release_ref "$release_ref" --arg target_ref "$target_ref" '{
    release_ref: $release_ref, target_ref: $target_ref,
    conflicting_paths: 1, action_count: 1,
    verdict: "merge-back conflicts but merge-tree reported no named paths — needs manual review.",
    source_missing: [{
      path: "(unclassified conflict — merge-tree reported a conflict with no named paths; inspect the run and merge manually)",
      owner: "", commits: []
    }],
    version_build_gap: [], source_drift: [], version_build: []
  }'
  exit 0
fi

# RELEASE commits whose patch is NOT on TARGET (git cherry '+') — genuinely missing on target.
# One git call, reused per path.
missing_shas="$(git cherry "$target_ref" "$release_ref" 2>/dev/null \
  | awk '/^\+/ {print $2}')"

# TARGET commits whose patch is NOT on RELEASE (reverse git cherry '+'). Computed ONCE and used ONLY
# as a MEMBERSHIP SET — never iterated as a driver. The reverse look (below) blames a path's few
# conflict lines to a handful of candidate commits, then asks "is this one missing on release?" here.
# Patch-id equivalence (like forward) means a commit cherry-picked to release under a new SHA is
# correctly seen as present, not missing.
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

# Emit {sha,author,subject,missing_on} for <sha>. $2 is the side the commit is MISSING on
# ("target" = on release, absent from target; "release" = on target, absent from release). NUL-
# delimited git fields split on NUL, so a '|' (or any char) in author/subject can't mis-split; the
# trailing %x00 absorbs git's end-of-entry newline.
commit_detail_json() {
  git log -1 --format='%h%x00%an%x00%s%x00' "$1" 2>/dev/null \
    | jq -Rsc --arg missing_on "$2" 'split("\u0000") | {sha: .[0], author: .[1], subject: .[2], missing_on: $missing_on}'
}

# Conflict-tied REVERSE attribution for a SOURCE path. Emits newline-separated commit_detail_json
# objects (missing_on:release) for TARGET commits that (a) authored the path's CONFLICTING lines and
# (b) are missing on release. The conflict lines are found by three-way overlap: a target hunk
# (base->target) whose BASE range overlaps a release hunk's (base->release) base range means both
# sides edited the same original region — the definition of a clash. Only those target line ranges
# are blamed. A target branch that has merely moved ahead elsewhere never overlaps, so it can't
# flood; that is the whole reason the reverse direction is otherwise treated as normal drift.
reverse_conflict_hits() {
  local p="$1"
  [ -n "$merge_base_ref" ] || return 0
  # Release-side base ranges (old-side of base->release), one "start end" per hunk in base coords.
  local rel_ranges
  rel_ranges="$(git diff -U0 "$merge_base_ref" "$release_ref" -- "$p" 2>/dev/null \
    | awk '/^@@ / {
        split($2, m, ","); a = substr(m[1], 2) + 0; b = (m[2] == "" ? 1 : m[2]) + 0
        if (b == 0) next                 # pure release insertion: no base span to overlap (add/add gap)
        print a, a + b - 1
      }')"
  [ -n "$rel_ranges" ] || return 0
  # Target hunks whose base range overlaps a release base range -> emit their TARGET (new-side) ranges.
  local target_ranges
  target_ranges="$(git diff -U0 "$merge_base_ref" "$target_ref" -- "$p" 2>/dev/null \
    | awk -v rr="$rel_ranges" '
        BEGIN { nr = split(rr, L, "\n"); for (i = 1; i <= nr; i++) { split(L[i], f, " "); rs[i] = f[1]; re[i] = f[2] } }
        /^@@ / {
          split($2, mo, ","); ba = substr(mo[1], 2) + 0; bb = (mo[2] == "" ? 1 : mo[2]) + 0
          split($3, mn, ","); tc = substr(mn[1], 2) + 0; td = (mn[2] == "" ? 1 : mn[2]) + 0
          if (td == 0) next                       # pure target deletion: no target line to blame
          bend = (bb == 0 ? ba : ba + bb - 1)
          for (i = 1; i <= nr; i++) if (ba <= re[i] && rs[i] <= bend) { print tc "," (tc + td - 1); break }
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

version_build=()
version_build_gap=()   # version/build files carrying a REAL missing dep change
source_missing=()   # "path" entries; commit detail JSON accumulated per path below
source_drift=()
declare -A missing_detail   # path -> newline-separated JSON objects {sha,author,subject}

for p in "${paths[@]}"; do
  [ -z "$p" ] && continue
  if is_version_build "$p"; then
    # Usually mechanical bumps, but a real dep change (renovate, backported bump) can hide here.
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
    # release branch as a matter of course, so a reverse look here would flag essentially every
    # mechanical version conflict — the exact flood we avoid. These stay forward-only mechanical.
    if [ -n "$vb_hits" ]; then
      version_build_gap+=("$p")
      missing_detail[$p]="$vb_hits"
    else
      version_build+=("$p")
    fi
    continue
  fi
  # Source path. FORWARD first (cheap — reuses missing_shas): release commits missing on target that
  # touch it. A forward hit already makes the path actionable, so we short-circuit and skip the
  # expensive REVERSE look. Only when forward is empty do we spend the conflict-tied reverse pass
  # (INC-6953: a main/stable re-fix on the clashing lines that never reached release).
  hits=""
  while IFS= read -r sha; do
    [ -z "$sha" ] && continue
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
action_count=$(( ${#source_missing[@]} + ${#version_build_gap[@]} ))
if [ "$action_count" -eq 0 ]; then
  verdict="no mid-release action required — all conflicts are mechanical or text drift."
else
  verdict="$action_count path(s) need a human to confirm nothing is lost on merge-back."
fi

# Guard empty-array expansion under set -u; pass nothing when a bucket is empty.
sm_json="$(build_actionable ${source_missing[@]+"${source_missing[@]}"})"
vbg_json="$(build_actionable ${version_build_gap[@]+"${version_build_gap[@]}"})"
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
    source_drift: $source_drift,
    version_build: $version_build
  }'
