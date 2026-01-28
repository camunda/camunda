#!/bin/bash

if [ -z "$1" ]; then
    >&2 echo "Usage: $0 PR_NUMBER"
    exit 1
fi

set -e

OWNER_NAME="camunda"
REPO_NAME="camunda"
PR_NUMBER="$1"
PR_HEAD_SHA=$(gh api -X GET "repos/${OWNER_NAME}/${REPO_NAME}/pulls/${PR_NUMBER}" --jq '.head.sha')

# stdout of the script is Markdown available in "result" output (via `echo`) for end users
# stderr shows the progress of the script while working (via `>&2 echo`), for debugging purposes

# Limitations:
# * no pagination in GH API calls, over 100 workflows per commit or 100 jobs per workflow wont be recognized

echo "## 🔎 GHA problems for PR #${PR_NUMBER}"
echo ""
echo "For most recent commit ${PR_HEAD_SHA}:"
echo ""

gh api -X GET "repos/${OWNER_NAME}/${REPO_NAME}/actions/runs?sha=${PR_HEAD_SHA}&per_page=100" --jq '.workflow_runs[]' | while read -r workflow_run; do
  workflow_run_id=$(echo "${workflow_run}" | jq -r '.id')
  workflow_run_attempt=$(echo "${workflow_run}" | jq -r '.run_attempt')
  >&2 echo "Checking workflow run ${workflow_run_id} attempt ${workflow_run_attempt} for problems..."

  gh api -X GET "repos/${OWNER_NAME}/${REPO_NAME}/actions/runs/${workflow_run_id}/attempts/${workflow_run_attempt}/jobs?per_page=100" --jq '.jobs[] | select(.conclusion=="failure" and .name!="check-results" and .name!="zeebe-ci / Test summary") .id' | while read -r job_id; do
    >&2 echo "Checking failed job ${job_id} for well known failure reasons..."

    workflow_name=$(gh api -X GET "repos/${OWNER_NAME}/${REPO_NAME}/actions/jobs/${job_id}" --jq '.workflow_name')
    job_name=$(gh api -X GET "repos/${OWNER_NAME}/${REPO_NAME}/actions/jobs/${job_id}" --jq '.name')
    job_url="https://github.com/${OWNER_NAME}/${REPO_NAME}/actions/runs/${workflow_run_id}/job/${job_id}?pr=${PR_NUMBER}"
    job_annotations=$(gh api -X GET "/repos/${OWNER_NAME}/${REPO_NAME}/check-runs/${job_id}/annotations")

    ############################################################################
    runner_problem_annotations=$(echo "${job_annotations}" | jq '.[] | select(.message | contains("lost communication with the server") or contains("runner has received a shutdown signal"))')
    if echo "${runner_problem_annotations}" | grep -q message; then
      # get runner name from job properties (usually empty on aborts) or from annotations via regex
      runner_name=$(gh api "repos/${OWNER_NAME}/${REPO_NAME}/actions/jobs/${job_id}" --jq '.runner_name' | tr '[:upper:]' '[:lower:]')
      if [ "${runner_name}" == "" ]; then
        runner_name=$(echo "$runner_problem_annotations" | grep -oP '(?<=The self-hosted runner: )\S+')
      fi

      >&2 echo "Job ${job_id} got aborted due to problem with runner: ${runner_name}"

      pods_dashboard_url="https://dashboard.int.camunda.com/d/000000019/pods?orgId=1&var-namespace=camunda&var-pod=${runner_name}&var-container=All&from=now-7d&to=now"
      gke_logs_url="https://console.cloud.google.com/logs/query;query=resource.type%3D%22k8s_cluster%22%0Aresource.labels.project_id%3D%22ci-30-162810%22%0Aresource.labels.location%3D%22europe-west1%22%0Aresource.labels.cluster_name%3D%22camunda-ci%22%20${runner_name};duration=P7D?project=ci-30-162810"

      echo "* 👻 [Job ${workflow_name} / ${job_name}](${job_url}) got aborted due to problems with runner \`${runner_name}\`:"
      echo "  * GitHub prematurely lost connection to the runner which can happen due to high job CPU load (try reducing load), network issues or hardware failures (try rerunning)."
      echo "  * Check [resource usage for runner \`${runner_name}\`](${pods_dashboard_url}) and [Kubernetes logs for runner \`${runner_name}\`](${gke_logs_url})."
    fi

    ############################################################################
    timeout_annotations=$(echo "${job_annotations}" | jq '.[] | select(.message | contains("has exceeded the maximum execution time"))')
    if echo "${timeout_annotations}" | grep -q message; then
      runner_name=$(gh api "repos/${OWNER_NAME}/${REPO_NAME}/actions/jobs/${job_id}" --jq '.runner_name' | tr '[:upper:]' '[:lower:]')

      >&2 echo "Job ${job_id} got cancelled due to timeout."

      echo "* ⏱️ [Job ${workflow_name} / ${job_name}](${job_url}) got cancelled due to timeout:"
      echo "  * Try rerunning if that is the first time, otherwise try parallelizing/speeding up the job."
      if [ "${workflow_name}" == "CI" ]; then
        echo "  * Avoid increasing the timeout to [keep fast workflows in Unified CI](https://camunda.github.io/camunda/ci/#workflow-inclusion-criteria)."
      fi
      if [[ $runner_name == camunda-* ]]; then
        echo "  * Check [resource usage for runner \`${runner_name}\`](https://dashboard.int.camunda.com/d/000000019/pods?orgId=1&var-namespace=camunda&var-pod=${runner_name}&var-container=All&from=now-7d&to=now)."
      fi
    fi

    ############################################################################
    dockerhub_problem_annotations=$(echo "${job_annotations}" | jq '.[] | select(.message | contains("docker.io") and (contains("Bad Gateway") or contains("Gateway Timeout")))')
    if echo "${dockerhub_problem_annotations}" | grep -q message; then
      >&2 echo "Job ${job_id} failed due to DockerHub problem."

      echo "* 🐋 [Job ${workflow_name} / ${job_name}](${job_url}) failed due to a network error at DockerHub:"
      echo "  * Check [DockerHub status](https://www.dockerstatus.com/) for outages and try rerunning."
    fi

    ############################################################################
    exitcode_annotations=$(echo "${job_annotations}" | jq '.[] | select(.message | contains("Process completed with exit code"))')
    if echo "${exitcode_annotations}" | grep -q message; then
      >&2 echo "Job ${job_id} failed with an error."

      echo "* ❌ [Job ${workflow_name} / ${job_name}](${job_url}) failed with an error:"
      echo "  * Check the error message in [the job logs](${job_url})."
    fi
  done
done
