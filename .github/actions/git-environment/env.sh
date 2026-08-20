#!/bin/bash

# isStableBranch
if [[ $branch =~ $stable_branch_regex ]]; then
    stable_version=${BASH_REMATCH[1]}
    echo "stable_version=${stable_version}" | tee -a "$GITHUB_ENV" "$GITHUB_OUTPUT"
    is_stable_branch=true
else
    is_stable_branch=false
fi
echo "is_stable_branch=${is_stable_branch}" | tee -a "$GITHUB_ENV" "$GITHUB_OUTPUT"

# isMainBranch
if [[ $branch =~ $main_branch_regex ]]; then
    is_main_branch=true
else
    is_main_branch=false
fi
echo "is_main_branch=${is_main_branch}" | tee -a "$GITHUB_ENV" "$GITHUB_OUTPUT"

# isMainOrStableBranch
if $is_main_branch || $is_stable_branch; then
    is_main_or_stable_branch=true
else
    is_main_or_stable_branch=false
fi
echo "is_main_or_stable_branch=${is_main_or_stable_branch}" | tee -a "$GITHUB_ENV" "$GITHUB_OUTPUT"

# getBranchSlug
branch_slug=$(echo "${branch,,}" | sed "s/[^a-z0-9-]/-/g")
echo "branch_slug=${branch_slug}" | tee -a "$GITHUB_ENV" "$GITHUB_OUTPUT"

# getGitCommitHash
git_commit_hash=$(git rev-parse --verify HEAD)
echo "git_commit_hash=${git_commit_hash}" | tee -a "$GITHUB_ENV" "$GITHUB_OUTPUT"

# getImageTag
if $is_main_or_stable_branch; then
    echo "image_tag=${git_commit_hash}" | tee -a "$GITHUB_ENV" "$GITHUB_OUTPUT"
else
    echo "image_tag=branch-${branch_slug}" | tee -a "$GITHUB_ENV" "$GITHUB_OUTPUT"
fi

# getLatestTag
if $is_stable_branch; then
    latest_tag="${stable_version}-8-latest"
else
    latest_tag="8-latest"
fi
echo "latest_tag=${latest_tag}" | tee -a "$GITHUB_ENV" "$GITHUB_OUTPUT"
