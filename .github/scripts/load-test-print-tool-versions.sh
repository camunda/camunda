#!/usr/bin/env bash
# owner: @camunda/reliability-testing
#
# Prints the versions of the CLI tools used to deploy a load test cluster (helm, kubectl,
# tsh, git, openssl, awk, jq), both as a step summary table and as a grouped log block.
# Useful when a deploy behaves differently across runners due to a tool version drift.
#
# Writes to: $GITHUB_STEP_SUMMARY

set -euo pipefail

GITHUB_STEP_SUMMARY="${GITHUB_STEP_SUMMARY:-/dev/stdout}"

helm_v=$(helm version --short 2>&1 || true)
kubectl_v=$(kubectl version --client -o json 2>/dev/null | jq -r '.clientVersion.gitVersion' || true)
tsh_v=$(tsh version --client --format=json 2>/dev/null | jq -r '.version' || tsh version 2>&1 | head -1)
git_v=$(git --version)
openssl_v=$(openssl version)
awk_v=$(awk --version 2>/dev/null | head -1 || awk -W version 2>&1 | head -1)
jq_v=$(jq --version)

{
  echo "## Tool versions"
  echo ""
  echo "| Tool | Version |"
  echo "|------|---------|"
  echo "| helm | \`${helm_v}\` |"
  echo "| kubectl | \`${kubectl_v}\` |"
  echo "| tsh | \`${tsh_v}\` |"
  echo "| git | \`${git_v}\` |"
  echo "| openssl | \`${openssl_v}\` |"
  echo "| awk | \`${awk_v}\` |"
  echo "| jq | \`${jq_v}\` |"
} >> "$GITHUB_STEP_SUMMARY"

echo "::group::Tool versions"
printf '%-10s %s\n' helm "${helm_v}" kubectl "${kubectl_v}" tsh "${tsh_v}" \
  git "${git_v}" openssl "${openssl_v}" awk "${awk_v}" jq "${jq_v}"
echo "::endgroup::"
