#!/usr/bin/env bash
# Detect whether merging <release_ref> back into <target_ref> would conflict.
# Read-only: uses git merge-tree, never mutates any branch.
#
# Output contract (consumed by triage-mergeback.sh): ONE JSON object on stdout, human context on
# stderr only. A pathless conflict is expressed explicitly, not via a sentinel:
#   {"status":"clean","paths":[]}
#   {"status":"conflict","paths":["Makefile","zeebe/engine/Foo.java"]}
#   {"status":"conflict","paths":[]}
#
# Exit codes let the caller tell a real result from an operational failure (never a false clean):
#   0 = clean, 2 = conflict, 3 = operational error (bad ref / git failure) — caller must fail loud.
#
# merge-tree flags (-z --no-messages --name-only): output is the tree OID followed by a NUL-
# delimited list of conflicted paths. NUL framing keeps paths with spaces/newlines intact. A real
# result always emits the tree OID, so exit 1 with empty stdout means "could not merge" (exit 3),
# not a conflict.
set -euo pipefail
target_ref="${1:?usage: detect-mergeback-conflict.sh <target_ref> <release_ref>}"
release_ref="${2:?usage: detect-mergeback-conflict.sh <target_ref> <release_ref>}"

# Both refs must resolve to real commits; an unresolvable ref is operational, not "clean".
for ref in "$target_ref" "$release_ref"; do
  if ! git rev-parse --verify --quiet "${ref}^{commit}" >/dev/null; then
    echo "ERROR: ref '$ref' does not resolve to a commit" >&2
    exit 3
  fi
done

mt_out="$(mktemp)"
mt_err="$(mktemp)"
trap 'rm -f "$mt_out" "$mt_err"' EXIT

# git merge-tree exit status: 0 = clean, 1 = conflict, anything else = error.
mt_rc=0
git merge-tree --write-tree --name-only --no-messages -z "$target_ref" "$release_ref" \
  >"$mt_out" 2>"$mt_err" || mt_rc=$?

# No stdout means git produced no merge result (e.g. an unmergeable ref that still exits 1).
# Operational, never an empty "conflict".
if [ ! -s "$mt_out" ]; then
  echo "ERROR: git merge-tree produced no output (exit ${mt_rc}) for $release_ref -> $target_ref" >&2
  cat "$mt_err" >&2 || true
  exit 3
fi

# Drop field 1 (tree OID) and the trailing empty field after the last NUL; the rest are the
# conflicted paths, verbatim.
paths_json="$(jq -Rsc 'split("\u0000") | .[1:] | map(select(length > 0))' <"$mt_out")"

if [ "$mt_rc" -eq 0 ]; then
  echo "CLEAN: $release_ref merges into $target_ref with no conflicts" >&2
  jq -nc --argjson paths "$paths_json" '{status: "clean", paths: $paths}'
  exit 0
elif [ "$mt_rc" -eq 1 ]; then
  echo "CONFLICT: $release_ref does NOT merge cleanly into $target_ref" >&2
  cat "$mt_err" >&2 || true
  jq -nc --argjson paths "$paths_json" '{status: "conflict", paths: $paths}'
  exit 2
else
  # git itself failed — surface stderr and fail loud (operational, not an empty "conflict").
  echo "ERROR: git merge-tree failed (exit ${mt_rc}) for $release_ref -> $target_ref" >&2
  cat "$mt_err" >&2 || true
  exit 3
fi
