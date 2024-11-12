#!/bin/bash

# fail fast and print each statement
set -ex

# Validate inputs
if [[ -z "$ISSUE_URL" ]]; then
  echo "ISSUE_URL is required"
  exit 1
fi
if [[ -z "$ENGINEERING_DRI" ]]; then
  echo "ENGINEERING_DRI is required"
  exit 1
fi

# Extract the issue description
ISSUE_DESCRIPTION=$(gh issue view $ISSUE_URL --json body --jq '.body')

# Remove the Engineering DRI from the description (either with an assignee or without)
ISSUE_DESCRIPTION=$(echo "$ISSUE_DESCRIPTION" | sed "/Engineering DRI: @\w\+[[:space:]]*$/d")
ISSUE_DESCRIPTION=$(echo "$ISSUE_DESCRIPTION" | sed "/Engineering DRI:[[:space:]]*$/d")

# Add new assignee at the beginning of the description (if there were exactly the same assignee already it will be replaced anyway)
ISSUE_DESCRIPTION=$(echo -e "Engineering DRI: @$ENGINEERING_DRI\\n\\n${ISSUE_DESCRIPTION}")

# Update the issue description
gh issue edit $ISSUE_URL --body "${ISSUE_DESCRIPTION}"
