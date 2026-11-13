#!/usr/bin/env bash
# Creates or updates the single PR comment that reports the OC REST API breaking-change check.
# Reads the report check.py wrote to ${RUNNER_TEMP}/oas-check.
#
# Required env: GH_TOKEN, HEAD_SHA, PR_NUMBER, REPO, RUNNER_TEMP
set -eu

out="${RUNNER_TEMP}/oas-check"
ids="$(gh api --paginate "repos/${REPO}/issues/${PR_NUMBER}/comments" \
  --jq '.[] | select(.user.login == "github-actions[bot]" and (.body | contains("<!-- oas-breaking-change-check -->"))) | .id')"
existing="$(printf '%s\n' "${ids}" | head -n1)"
if [ -f "${out}/report.md" ]; then
  if [ -n "${existing}" ]; then
    gh api --method PATCH "repos/${REPO}/issues/comments/${existing}" -F "body=@${out}/report.md" > /dev/null
  elif [ "$(cat "${out}/status")" != none ]; then
    gh api --method POST "repos/${REPO}/issues/${PR_NUMBER}/comments" -F "body=@${out}/report.md" > /dev/null
  fi
else
  echo "::warning::The breaking-change check produced no report; see the earlier steps."
  # A comment from an earlier push would keep showing an outdated result. Rewrite it, but
  # never create one: there is nothing to report.
  if [ -n "${existing}" ]; then
    body="$(printf '%s\n' \
      '<!-- oas-breaking-change-check -->' \
      '### OC REST API breaking-change check' \
      '' \
      "The breaking-change check could not run for \`${HEAD_SHA}\`; see the job logs." \
      '' \
      '_This check is in reporting-only mode and does not block merging._')"
    gh api --method PATCH "repos/${REPO}/issues/comments/${existing}" -f "body=${body}" > /dev/null
  fi
fi
