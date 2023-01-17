#!/bin/bash -eu
# Verifies certain properties of the Docker image, e.g. actualLabels are correct. This script relies
# on `jq` as an external dependency.
#
# Example usage:
#   $ ./verify.sh camunda/optimize:3.9.0 registry.camunda.cloud/team-optimize/optimize:latest
#
# Globals:
#   VERSION - required; the semantic version, e.g. 3.9.0 or 3.9.0-alpha1
#   REVISION - required; the sha1 of the commit used to build the artifact
#   DATE - required; the ISO 8601 date at which the image was built
#   ARCHITECTURE - required; the architecture (e.g. amd64, arm64) for which the image was built
#   BASE_IMAGE - required; Docker base image name (e.g. docker.io/library/alpine:3)
# Arguments:
#   1 - Docker image names to be checked (no limit on number of images, each is checked separately)
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
  echo >&2 "No VERSION was given; make sure to pass a semantic version, e.g. VERSION=3.9.0"
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

if [ -z "${ARCHITECTURE}" ]; then
  echo >&2 "No ARCHITECTURE was given; make sure to pass a valid platform, it must be one of arm64 or amd64"
  exit 1
fi

if [ -z "${BASE_IMAGE}" ]; then
  echo >&2 "No BASE_IMAGE was given; make sure to pass a valid base image name, e.g. docker.io/library/alpine:3"
  exit 1
fi

# Check that the base image exists
if ! baseImageInfo="$(docker manifest inspect "${BASE_IMAGE}")"; then
  echo >&2 "No known Docker base image ${BASE_IMAGE} exists; did you pass the right name?"
  exit 1
fi

echo "Checking for architecture ${ARCHITECTURE}"
digestForArchitecture=$(echo "${baseImageInfo}" | jq '.manifests[] | select(.platform.architecture == "'"${ARCHITECTURE}"'") |
.digest' )

# Removing leading and trailing quotes
digestForArchitecture="${digestForArchitecture%\"}"
digestForArchitecture="${digestForArchitecture#\"}"
echo "Expected base image digest from ${BASE_IMAGE} for ${ARCHITECTURE} is: ${digestForArchitecture}"

imageName="${1}"
# Iterate through all the images passed as parameter
for imageName in "$@"
do
    echo "Comparing image label values for ${imageName}..."
    # Check that the image exists
    if ! imageInfo="$(docker inspect "${imageName}")"; then
      echo >&2 "No known Docker image ${imageName} exists; did you pass the right name?"
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
        --arg BASEDIGEST "${digestForArchitecture}" \
        "$(cat "${labelsGoldenFile}")"
    )

    # Compare and output
    if ! diff <(echo "${expectedLabels}") <(echo "${actualLabels}"); then
      echo >&2 "Expected label values (marked by '<') do not match actual label values (marked by '>'); if you think this is wrong, update the golden file at ${labelsGoldenFile}"
      exit 1
    fi
    echo "Check successful for ${imageName}"
done

