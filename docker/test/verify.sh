#!/bin/bash -eu
# Verifies certain properties of the Docker image, e.g. actualLabels are correct. This script relies
# on `jq` as an external dependency.
#
# Example usage:
#   $ ./verify.sh camunda/zeebe:8.1.0
#
# Globals:
#   VERSION - required; the semantic version, e.g. 8.1.0 or 1.2.0-alpha1
#   REVISION - required; the sha1 of the commit used to build the artifact
#   DATE - required; the ISO 8601 date at which the image was built
# Arguments:
#   1 - Docker image name
# Outputs:
#   STDERR Error message if any of the properties are invalid
# Returns:
#   0 on success
#   1 if one of the properties is invalid

set -o pipefail

VERSION="${VERSION:-}"
REVISION="${REVISION:-}"
DATE="${DATE:-}"

# Make sure environment variables are set
if [ -z "${VERSION}" ]; then
  echo >&2 "No VERSION was given; make sure to pass a semantic version, e.g. VERSION=8.1.0"
  exit 1
fi

if [ -z "${REVISION}" ]; then
  echo >&2 "No REVISION was given; make sure to pass the Git commit sha, e.g. REVISION=2941d620f08d9729632b2b1222123edcbe3532c8"
  exit 1
fi

if [ -z "${DATE}" ]; then
  echo >&2 "No DATE was given; make sure to pass an ISO8601 date, e.g. DATE='2001-01-01T00:00:00Z'"
  exit 1
fi

imageName="${1}"

# Check that the image exists
if ! imageInfo="$(docker inspect "${imageName}")"; then
  echo >&2 "No known Docker image ${imageName} exists; did you pass the right name?"
  exit 1
fi

DIGEST_REGEX="BASE_DIGEST=\"(sha256\:[a-f0-9\:]+)\""
DOCKERFILE=$(<"${BASH_SOURCE%/*}/../../Dockerfile")
if [[ $DOCKERFILE =~ $DIGEST_REGEX ]]; then
    DIGEST="${BASH_REMATCH[1]}"
    echo "Digest found: $DIGEST"
else
    echo >&2 "Docker image digest can not be found in the Dockerfile"
    exit 1
fi

# Extract the actual labels from the info - make sure to sort keys so we always have the same
# ordering for maps to compare things properly
actualLabels=$(echo "${imageInfo}" | jq --sort-keys '.[0].Config.Labels')

if [[ -z "${actualLabels}" || "${actualLabels}" == "null" || "${actualLabels}" == "[]" ]]; then
  echo >&2 "No labels found in the given image ${imageName}; raw inspect output to follow"
  printf >&2 "\n====\n%s\n====\n" "${imageInfo}"
  echo >&2 "Are there any labels in the above? If so, the JSON query may need to be changed."
  exit 1
fi

# Generate the expected labels files with the dynamic properties substituted
labelsGoldenFile="${BASH_SOURCE%/*}/docker-labels.golden.json"
expectedLabels=$(
  jq --sort-keys -n \
    --arg VERSION "${VERSION}" \
    --arg REVISION "${REVISION}" \
    --arg DATE "${DATE}" \
    --arg DIGEST "${DIGEST}" \
    "$(cat "${labelsGoldenFile}")"
)

# Compare and output
echo "Comparing image label values..."
if ! diff <(echo "${expectedLabels}") <(echo "${actualLabels}"); then
  echo >&2 "Expected label values (marked by '<') do not match actual label values (marked by '>'); if you think this is wrong, update the golden file at ${labelsGoldenFile}"
  exit 1
fi
