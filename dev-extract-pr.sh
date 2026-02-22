#!/usr/bin/env bash
# dev-extract-pr.sh — Extract a topic branch's own commits for PR submission
#
# When you develop on a topic branch forked from 'integration', your branch
# contains all the merged-in dependencies. This script rebases just YOUR
# commits onto the real target branch (main or a dependency branch).
#
# Usage:
#   ./dev-extract-pr.sh <topic-branch> <target-branch>
#
# Example:
#   ./dev-extract-pr.sh 46398-deprecation-vendor-keys main
#   ./dev-extract-pr.sh 46398-deprecation-vendor-keys 46224-enforce-response-arrays-required-not-nullable
#
# What it does:
#   1. Finds where 'integration' was when the topic branched off
#   2. Cherry-picks only the topic's own commits onto a PR branch based on <target>
#   3. Creates: <topic-branch>-pr (the branch you push for the PR)
#
# The original topic branch is left untouched so you can keep developing on it.

set -euo pipefail

if [[ $# -lt 2 ]]; then
  echo "Usage: $0 <topic-branch> <target-branch>"
  echo ""
  echo "Extracts commits unique to <topic-branch> (not in integration) and"
  echo "replays them onto <target-branch> as a clean PR branch."
  exit 1
fi

TOPIC="$1"
TARGET="$2"
INTEGRATION="integration"
PR_BRANCH="${TOPIC}-pr"

# Validate branches exist
for b in "$TOPIC" "$TARGET" "$INTEGRATION"; do
  if ! git rev-parse --verify "$b" &>/dev/null; then
    echo "ERROR: Branch '$b' does not exist."
    exit 1
  fi
done

if git rev-parse --verify "$PR_BRANCH" &>/dev/null; then
  echo "ERROR: Branch '$PR_BRANCH' already exists."
  echo "       Delete it first if you want to recreate: git branch -D $PR_BRANCH"
  exit 1
fi

# Find the fork point: where did the topic diverge from integration?
FORK_POINT=$(git merge-base "$INTEGRATION" "$TOPIC")

# Count commits
COMMIT_COUNT=$(git rev-list --count "$FORK_POINT".."$TOPIC")
if [[ "$COMMIT_COUNT" -eq 0 ]]; then
  echo "No commits found on '$TOPIC' beyond integration. Nothing to extract."
  exit 0
fi

echo "Found $COMMIT_COUNT commit(s) on '$TOPIC' to extract:"
git --no-pager log --oneline "$FORK_POINT".."$TOPIC"
echo ""

# Create PR branch from target
git checkout -b "$PR_BRANCH" "$TARGET"

# Cherry-pick the topic's own commits
echo "Cherry-picking onto '$PR_BRANCH' (based on '$TARGET')..."
if git cherry-pick "$FORK_POINT".."$TOPIC"; then
  echo ""
  echo "✓ PR branch '$PR_BRANCH' ready."
  echo ""
  echo "Next steps:"
  echo "  git push -u origin $PR_BRANCH"
  echo "  gh pr create --base $TARGET --head $PR_BRANCH"
  echo ""
  echo "To continue developing, switch back:"
  echo "  git checkout $TOPIC"
else
  echo ""
  echo "✗ Cherry-pick had conflicts. Resolve them, then:"
  echo "  git cherry-pick --continue"
  echo ""
  echo "Or abort:"
  echo "  git cherry-pick --abort && git checkout $TOPIC && git branch -D $PR_BRANCH"
fi
