#!/bin/bash

# fail fast and print each statement
set -ex

issue_url=$ISSUE_URL
project_id=$PROJECT_ID
project_owner=$PROJECT_OWNER

# Validate inputs
if [[ -z "$issue_url" ]]; then
  echo "ISSUE_URL is required"
  exit 1
fi
if [[ -z "$project_id" ]]; then
  echo "PROJECT_ID is required"
  exit 1
fi
if [[ -z "$project_owner" ]]; then
  echo "PROJECT_OWNER is required"
  exit 1
fi

project_title=$(gh project view $project_id --owner $project_owner --format json --jq '.title')
issue_projects=$(gh issue view $issue_url --json "projectItems" --jq '.projectItems')
issue_project=$(echo $issue_projects | jq --arg title "$project_title" '.[] | select(.title == $title)')
project_status=$(echo "$issue_project" | jq -r '.status.name')

is_in_project="false"
if [[ -n "$issue_project" ]]; then
  is_in_project="true"
fi

echo "is_in_project=${is_in_project}" >>"$GITHUB_OUTPUT"
echo "project_title=${project_title}" >>"$GITHUB_OUTPUT"
echo "project_status=${project_status}" >>"$GITHUB_OUTPUT"
