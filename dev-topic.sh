#!/usr/bin/env bash
# dev-topic.sh — Start a new topic branch from integration for development
#
# Creates a topic branch at the current integration tip. You develop and test
# here against the full integrated state. When ready for a PR, use
# dev-extract-pr.sh to rebase onto the real target branch.
#
# Usage:
#   ./dev-topic.sh <branch-name>
#
# Example:
#   ./dev-topic.sh 46398-deprecation-vendor-keys

set -euo pipefail

if [[ $# -lt 1 ]]; then
  echo "Usage: $0 <branch-name>"
  echo "Creates a new topic branch from the integration branch."
  exit 1
fi

BRANCH="$1"
INTEGRATION="integration"

if ! git rev-parse --verify "$INTEGRATION" &>/dev/null; then
  echo "ERROR: No '$INTEGRATION' branch found."
  echo "       Run ./dev-integration.sh first to create it."
  exit 1
fi

if git rev-parse --verify "$BRANCH" &>/dev/null; then
  echo "ERROR: Branch '$BRANCH' already exists."
  exit 1
fi

# Record the integration tip so we know where the topic's own commits start
INTEGRATION_TIP=$(git rev-parse "$INTEGRATION")

git checkout -b "$BRANCH" "$INTEGRATION"

echo ""
echo "Topic branch '$BRANCH' created at integration tip."
echo "Integration base: $INTEGRATION_TIP"
echo ""
echo "Develop and test here. When ready for PR:"
echo "  ./dev-extract-pr.sh $BRANCH main"
echo "  (or target another branch instead of main if this topic has a dependency)"
