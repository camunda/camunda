#!/usr/bin/env bash
# dev-integration.sh — Rebuild the local integration branch from main + topic branches
#
# This is the "linux-next" pattern: a throwaway branch that merges all your
# in-flight work so you can build, test, and develop against a known-good state.
#
# Usage:
#   ./dev-integration.sh          # rebuild integration from scratch
#   ./dev-integration.sh --dry    # show what would be merged, don't do it
#
# Rules:
#   - integration is disposable. Rebuild it whenever anything changes.
#   - Never push integration as a PR. Never develop directly on it.
#   - New topic branches fork from integration for building/testing,
#     but PR back to main (or their real dependency branch).
#
# To add/remove branches, edit the TOPICS array below.
# ORDER MATTERS: branches are merged in this order. Put dependencies first.

set -euo pipefail

# ── Configure these ──────────────────────────────────────────────────────────
BASE="main"
INTEGRATION="integration"

# Branches to merge, in order. Dependencies before dependents.
# Format: "branch-name  # optional comment"
TOPICS=(
  "dev-scripts                               # chore: dev workflow scripts"
  "46402-deterministic-ordering-in-test      # test: statistics ordering fix"
  "46406-fix-e2e-document-test               # test: document processDefinitionId fix"
  "gateway-response-violation-enhanced-output # refactor: validator shows violating value"
  "copilot/validate-process-definition-id    # fix: validate processDefinitionId on upload"
  "46391-make-arrays-required                # spec: arrays required"
  "46224-enforce-response-arrays-required-not-nullable # ci: spectral enforcement rule"
)
# ─────────────────────────────────────────────────────────────────────────────

DRY=false
if [[ "${1:-}" == "--dry" ]]; then
  DRY=true
fi

strip_comment() { echo "$1" | sed 's/#.*//; s/[[:space:]]*$//; s/^[[:space:]]*//' ; }

echo "=== Integration branch builder ==="
echo "Base: $BASE"
echo ""

# Verify all topic branches exist locally
for entry in "${TOPICS[@]}"; do
  branch=$(strip_comment "$entry")
  if ! git rev-parse --verify "$branch" &>/dev/null; then
    echo "ERROR: Branch '$branch' does not exist locally."
    echo "       Fetch it first: git fetch origin $branch && git checkout -b $branch origin/$branch"
    exit 1
  fi
done

echo "Merge plan:"
for entry in "${TOPICS[@]}"; do
  branch=$(strip_comment "$entry")
  comment=$(echo "$entry" | grep -o '#.*' 2>/dev/null || echo "")
  ahead=$(git rev-list --count "$BASE".."$branch")
  echo "  $branch  ($ahead commits) $comment"
done
echo ""

if $DRY; then
  echo "(dry run — no changes made)"
  exit 0
fi

# Save current branch to return to later
ORIGINAL_BRANCH=$(git branch --show-current 2>/dev/null || git rev-parse --short HEAD)

# Check for uncommitted changes
if ! git diff-index --quiet HEAD -- 2>/dev/null; then
  echo "WARNING: You have uncommitted changes. Stashing them."
  git stash push -m "dev-integration: auto-stash before rebuild"
  STASHED=true
else
  STASHED=false
fi

# Delete old integration branch if it exists
if git rev-parse --verify "$INTEGRATION" &>/dev/null; then
  echo "Deleting old $INTEGRATION branch..."
  git branch -D "$INTEGRATION"
fi

# Create fresh integration from base
echo "Creating $INTEGRATION from $BASE..."
git checkout -b "$INTEGRATION" "$BASE"

# Merge each topic branch
FAILED=()
for entry in "${TOPICS[@]}"; do
  branch=$(strip_comment "$entry")
  echo ""
  echo "── Merging: $branch ──"
  if git merge --no-edit "$branch"; then
    echo "   ✓ OK"
  else
    echo "   ✗ CONFLICT merging $branch"
    echo "   Aborting this merge. Remaining branches will be skipped."
    git merge --abort
    FAILED+=("$branch")
    break
  fi
done

echo ""
echo "=== Integration branch ready ==="
echo "Tip: $(git --no-pager log --oneline -1)"
echo ""

if [[ ${#FAILED[@]} -gt 0 ]]; then
  echo "WARNING: Failed to merge: ${FAILED[*]}"
  echo "Fix the conflict manually, or reorder TOPICS in this script."
fi

echo ""
echo "You are now on '$INTEGRATION'. Next steps:"
echo "  • ./dev-start.sh              # build + start server"
echo "  • ./dev-test.sh               # run e2e tests"
echo "  • git checkout -b <topic>     # start new work from this state"
echo ""
echo "To return to your previous branch:"
echo "  git checkout $ORIGINAL_BRANCH"

if $STASHED; then
  echo ""
  echo "NOTE: Your uncommitted changes were stashed. Restore with: git stash pop"
fi
