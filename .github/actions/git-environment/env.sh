#!/bin/bash

# isMaintenanceBranch
if [[ $branch =~ $maintenance_branch_regex ]];
then
    maintenance_version=${BASH_REMATCH[1]}
    echo "maintenance_version=${maintenance_version}" >> "$GITHUB_OUTPUT"
    is_maintenance_branch=true
else
    is_maintenance_branch=false
fi
echo "is_maintenance_branch=${is_maintenance_branch}" >> "$GITHUB_OUTPUT"

# isMainBranch
if  [[ $branch =~ $main_branch_regex ]];
then
    is_main_branch=true
else
    is_main_branch=false
fi
echo "is_main_branch=${is_main_branch}" >> "$GITHUB_OUTPUT"

# isMainOrMaintenanceBranch
if $is_main_branch || $is_maintenance_branch;
then
    is_main_or_maintenance_branch=true
else
    is_main_or_maintenance_branch=false
fi
echo "is_main_or_maintenance_branch=${is_main_or_maintenance_branch}" >> "$GITHUB_OUTPUT"

# getBranchSlug
branch_slug=$(echo "${branch,,}" | sed "s/[^a-z0-9-]/-/g")
echo "branch_slug=${branch_slug}" >> "$GITHUB_OUTPUT"

# getGitCommitHash
git_commit_hash=$(git rev-parse --verify HEAD)
echo "git_commit_hash=${git_commit_hash}" >> "$GITHUB_OUTPUT"

# getImageTag
if $is_main_or_maintenance_branch;
then
    echo "image_tag=${git_commit_hash}" >> "$GITHUB_OUTPUT"
else
    echo "image_tag=branch-${branch_slug}" >> "$GITHUB_OUTPUT"
fi

# getLatestTag
if $is_maintenance_branch;
then
    latest_tag="${maintenance_version}-latest"
else
    latest_tag="latest"
fi
echo "latest_tag=${latest_tag}" >> "$GITHUB_OUTPUT"
