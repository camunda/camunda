#!/usr/bin/env bash
# Use case 5 - a job worker activates a job by long polling and receives resolved secret values.
#
# requestTimeout > 0 is long polling: the gateway holds the request open until a job is activated
# or the timeout expires. A job whose secret is not cached yet is parked, the resolution runs in
# the background, and this one request picks the job up as soon as it is reactivated. No second
# activation call is needed.
#
# What makes that work is the gateway's long-polling probe: nothing notifies workers when a parked
# job becomes activatable again, so the held request re-checks the partitions every
# camunda.api.long-polling.probe-timeout (set to 2s in demo-config.yaml, default 10s).
#
# Job push (job streaming) does not resolve secrets yet, which is why the demo activates over this
# endpoint.
#
# Usage: 05-activate-job.sh [jobType] [requestTimeoutMillis]
#   jobType              defaults to send-order
#   requestTimeoutMillis defaults to 30000; pass a smaller value where the point is that the job
#                        stays parked (use case 7) rather than that it arrives

source "$(dirname "${BASH_SOURCE[0]}")/env.sh"

JOB_TYPE="${1:-send-order}"
REQUEST_TIMEOUT="${2:-30000}"

api_post "$ADMIN_USER" "$ADMIN_PASSWORD" /v2/jobs/activation "$(cat <<JSON
{
  "type": "$JOB_TYPE",
  "worker": "demo-worker",
  "timeout": 60000,
  "maxJobsToActivate": 1,
  "requestTimeout": $REQUEST_TIMEOUT
}
JSON
)"
