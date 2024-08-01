#!/bin/bash

# fail fast and print each statement
set -ex

issue_url=$ISSUE_URL

# Validate inputs
if [[ -z "$issue_url" ]]; then
  echo "ISSUE_URL is required"
  exit 1
fi

issue_description=$(gh issue view $issue_url --json body --jq '.body')
engineering_dri=$(grep -oP 'Engineering DRI: @\K\w+' <<<"$issue_description" || true)

echo "engineering_dri=${engineering_dri}" >>"$GITHUB_OUTPUT"
