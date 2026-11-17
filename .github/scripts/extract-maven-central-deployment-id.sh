#!/bin/bash
#
# Extracts the Maven Central deployment ID of the "Maven Release" job of the current workflow run
# and writes `deploymentId=<id>` to $GITHUB_OUTPUT.
#
# The central-publishing-maven-plugin only emits the deployment ID via a log line:
#   "Uploaded bundle successfully, deployment name: ..., deploymentId: <uuid>..."
# It is not persisted to disk by the plugin, so we fetch the release job's log from the GHA API
# (the release job has completed by this point) and grep it.
#
# Required env: GH_TOKEN, GITHUB_REPOSITORY, GITHUB_RUN_ID, GITHUB_RUN_ATTEMPT, GITHUB_OUTPUT,
#               RELEASE_VERSION, SKIP_CENTRAL_RELEASE ("true" for dry runs)
set -euo pipefail

api="https://api.github.com/repos/${GITHUB_REPOSITORY}"

# When this workflow is invoked as a reusable workflow (e.g., by the dry-run
# wrapper with a matrix), the run-level jobs API surfaces the inner job as
# "<caller-job-display-name> / Maven Release" rather than just "Maven Release".
# Match both forms and disambiguate matrix invocations via the release version,
# which callers conventionally embed in their job display name.
job_id=$(curl -fsSL \
    -H "Authorization: Bearer ${GH_TOKEN}" \
    -H "Accept: application/vnd.github+json" \
    "${api}/actions/runs/${GITHUB_RUN_ID}/attempts/${GITHUB_RUN_ATTEMPT}/jobs?per_page=100" \
  | jq -r --arg rv "$RELEASE_VERSION" '
      .jobs[]
      | select(.name == "Maven Release"
               or ((.name | endswith(" / Maven Release"))
                   and (.name | contains($rv))))
      | .id
    ' \
  | tail -1 || true)

# The job /logs endpoint returns plain text (only the run-level /runs/{id}/logs endpoint returns a
# ZIP). Still guard against an archive: detect it by its "PK" magic bytes and unpack it, otherwise
# read the log as-is.
deployment_id=""
if [[ -z "${job_id}" ]]; then
  echo "::warning::Could not locate the Maven Release job via the API"
else
  logs_file=$(mktemp)
  if curl -fsSL \
      -H "Authorization: Bearer ${GH_TOKEN}" \
      -H "Accept: application/vnd.github+json" \
      -o "${logs_file}" \
      "${api}/actions/jobs/${job_id}/logs"; then
    deployment_id=$(
      if [[ "$(head -c 2 "${logs_file}")" == "PK" ]]; then
        unzip -p "${logs_file}"
      else
        cat "${logs_file}"
      fi \
      | grep -oE 'deploymentId: [0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}' \
      | tail -1 \
      | awk '{print $2}' || true)
  fi
  rm -f "${logs_file}"
fi

if [[ -n "${deployment_id}" ]]; then
  echo "deploymentId=${deployment_id}" >> "$GITHUB_OUTPUT"
  echo "::notice::Maven Central deployment ID: ${deployment_id}"
elif [[ "${SKIP_CENTRAL_RELEASE}" == "true" ]]; then
  echo "deploymentId=dry-run-${RELEASE_VERSION}" >> "$GITHUB_OUTPUT"
  echo "ℹ️ Dry run — emitting placeholder deployment ID"
else
  echo "deploymentId=not-found" >> "$GITHUB_OUTPUT"
  echo "ℹ️ No Maven Central deployment ID found in build output" >&2
fi
