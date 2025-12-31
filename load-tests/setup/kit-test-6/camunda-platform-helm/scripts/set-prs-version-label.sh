# A script to label PRs based on the app and chart version.
# It meant to run in the post-release workflow and before merging the release PR.

#!/bin/bash
set -euo pipefail

#
# Vars.
REPO_OWNER='camunda'
REPO_NAME='camunda-platform-helm'

# Init.
# Check dependencies.
dep_names="ct gh git git-cliff grep jq yq"
for dep_name in ${dep_names}; do
    test -n "$(which ${dep_name})" || (
      echo "Missing dependency: ${dep_name}";
      echo "Dependencies list: ${dep_names}";
      exit 1
    )
done
# Ensure that the main branch is there.
test "$(git branch --show-current)" != "main" &&
    git fetch origin main:main --no-tags

# Get PRs from commits in a certain chart dir.
get_prs_per_chart_dir () {
    chart_dir="${1}"
    # Get the latest version from main, not from the releas PR as it could be updated in the PR.
    latest_chart_version="$(git show main:${chart_dir}/Chart.yaml | yq '.version')"
    latest_chart_name="${chart_dir##*/}"
    latest_chart_tag_hash="$(git show-ref --hash ${latest_chart_name}-${latest_chart_version})"
    cliff_config_file=".github/config/cliff.toml"
    git-cliff ${latest_chart_tag_hash}.. \
        --context \
        --config "${cliff_config_file}" \
        --include-path "${chart_dir}/**" |
            jq '.[].commits[].message' | (grep -Po '(?<=#)\d+' || true)
}

# Get the issues fixed by a PR.
# Note: GH CLI doesn't support this so we use the API call directly.
# https://github.com/cli/cli/discussions/7097#discussioncomment-5229031
get_issues_per_pr () {
    pr_nubmer="${1}"
    gh api graphql -F owner="${REPO_OWNER}" -F repo="${REPO_NAME}" -F pr="${pr_nubmer}" -f query='
        query ($owner: String!, $repo: String!, $pr: Int!) {
            repository(owner: $owner, name: $repo) {
                pullRequest(number: $pr) {
                    closingIssuesReferences(first: 100) {
                        nodes {
                            number
                        }
                    }
                }
            }
        }' --jq '.data.repository.pullRequest.closingIssuesReferences.nodes[].number'
}

# Run.
# Label PRs with the app and chart version only if there is a change in the chart.
ct list-changed | while read chart_dir; do
    app_version="$(yq '.appVersion | sub("\..$", "")' ${chart_dir}/Chart.yaml)"
    chart_version="$(yq '.version' ${chart_dir}/Chart.yaml)"
    app_version_label="version/${app_version}"
    # The "version:x.y.z" label format is used by the support team,
    # it must not changed without checking with the support team.
    chart_version_label="version:${app_version}-${chart_version}"

    echo -e "\nChart dir: ${chart_dir}"
    echo "Apps version: ${app_version}"
    echo "Chart version: ${chart_version}"

    # Create the chart version label if it doesn't exist.
    # We need to use grep because GH CLI doesn't support exact match.
    gh label list --search "${chart_version_label}" | grep "${chart_version_label}" ||
        gh label create "${chart_version_label}" --color "0052CC" \
            --description "Issues and PRs related to chart version ${chart_version}"

    # Update GH PRs and Issues with the chart version.
    get_prs_per_chart_dir "${chart_dir}" | while read pr_nubmer; do
        # Label PR.
        printf "[PR ${pr_nubmer}] Labeling the PR with chart version label ${chart_version_label}"
        printf -- " - %s\n" $(gh pr edit "${pr_nubmer}" --add-label "${app_version_label},${chart_version_label}")
        # Label the PR's corresponding issue.
        issue_nubmers="$(get_issues_per_pr ${pr_nubmer})"
        test -z "${issue_nubmers}" && {
            printf -- "- PR no. ${pr_nubmer} has no corresponding issues.\n"
            continue
        }
        echo -e "${issue_nubmers}" | while read issue_nubmer; do
            printf -- "- [Issue ${issue_nubmer}] Labeling the issue with chart version label ${chart_version_label}"
            printf -- " - %s\n" $(gh issue edit "${issue_nubmer}" --add-label "${app_version_label},${chart_version_label}")
        done
    done
done
