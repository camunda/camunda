#!/usr/bin/env bash
# owner: @camunda/engineering-operations
# Downloads a file over HTTPS, retrying on transient network errors.
# Usage: .github/scripts/download-with-retry.sh <url> <output-path>
#
# --retry-all-errors also retries connection resets and DNS failures, which the
# curl default skips, see https://github.com/camunda/camunda/actions/runs/32242321929/job/96035501009
set -euo pipefail

if [[ "$#" -ne 2 ]]; then
  echo "Usage: $0 <url> <output-path>" >&2
  exit 1
fi

url="$1"
output="$2"

echo "Downloading ${url}"
curl --fail --silent --show-error --location \
     --proto '=https' --proto-redir '=https' \
     --connect-timeout 10 --max-time 60 \
     --retry 5 --retry-delay 2 --retry-max-time 60 --retry-all-errors \
     --output "${output}" "${url}"
