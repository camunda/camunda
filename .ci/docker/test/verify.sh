#!/bin/bash -eu
# Verifies certain properties of the Docker image, e.g. actualLabels are correct. This script relies
# on `jq` as an external dependency.
#
# Example usage:
#   $ ./verify.sh camunda/zeebe:8.1.0
#   $ ./verify.sh camunda/zeebe:8.1.0 arm64
#
# Globals:
#   VERSION - required; the semantic version, e.g. 8.1.0 or 1.2.0-alpha1
#   REVISION - required; the sha1 of the commit used to build the artifact
#   DATE - required; the ISO 8601 date at which the image was built
# Arguments:
#   1 - Docker image name
#   2 - (optional) image architecture/platform e.g. amd64 (default) or arm64
# Outputs:
#   STDERR Error message if any of the properties are invalid
# Returns:
#   0 on success
#   1 if one of the properties is invalid

set -o pipefail

VERSION="${VERSION:-}"
REVISION="${REVISION:-}"
DATE="${DATE:-}"
DOCKERFILENAME="${DOCKERFILENAME:-}"
GOLDENFILE="${GOLDENFILE:-}"

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

if [ -z "${DOCKERFILENAME}" ]; then
  echo >&2 "No DOCKERFILENAME was given; make sure to pass an name for the corresponding Dockerfile, like 'Dockerfile' or 'operate.Dockerfile'"
  exit 1
fi

if [ -z "${GOLDENFILE}" ]; then
  echo >&2 "No GOLDENFILE was given; make sure to pass an name for the corresponding golden file, like 'zeebe-docker-labels.golden.json'."
  exit 1
fi

imageName="${1}"
arch="${2:-amd64}"

# Get the platform-specific digest from the manifest index
# This works with Docker CLI v29+ and avoids the need to pull platform-specific images separately
platformDigest=$(docker buildx imagetools inspect "${imageName}" --raw | jq -r --arg arch "${arch}" '.manifests[] | select(.platform.architecture == $arch and .platform.os == "linux") | .digest')

if [ -z "${platformDigest}" ] || [ "${platformDigest}" == "null" ]; then
  echo >&2 "Could not find platform linux/${arch} in manifest for ${imageName}"
  exit 1
fi

imageRef="${imageName%:*}@${platformDigest}"
echo "Verifying platform linux/${arch} using ${imageRef}"

# Pull the platform-specific image by digest and inspect it
if ! docker pull "${imageRef}" > /dev/null; then
  echo >&2 "Failed to pull ${imageRef}"
  exit 1
fi

if ! imageInfo="$(docker inspect "${imageRef}")"; then
  echo >&2 "No known Docker image ${imageRef} exists; did you pass the right name?"
  exit 1
fi

actualArchitecture=$(echo "${imageInfo}" | jq -r '.[0].Architecture')
if [ "$actualArchitecture" != "$arch" ]; then
  echo >&2 "The local Docker image ${imageName} has the wrong architecture ${actualArchitecture}, expected ${arch}."
  exit 1
fi

imageManifestMediaType="$(docker buildx imagetools inspect "${imageName}" --raw | jq -r '.mediaType')"
# newer manifest types application/vnd.oci.image.index.v1+json (used when provenance is enabled when building
# a docker image) are not always compatible with older customer Docker registries:
imageManifestMediaTypeExpected="application/vnd.docker.distribution.manifest.list.v2+json"

if [ "$imageManifestMediaType" != "$imageManifestMediaTypeExpected" ]; then
  echo >&2 "The local Docker image ${imageName} has the wrong manifest media type ${imageManifestMediaType}, expected ${imageManifestMediaTypeExpected}."
  echo "Full manifest:"
  docker buildx imagetools inspect "${imageName}"
  exit 1
fi

DIGEST_REGEX="BASE_DIGEST=\"(sha256\:[a-f0-9\:]+)\""
DOCKERFILE=$(<"${BASH_SOURCE%/*}/../../../$DOCKERFILENAME")
if [[ $DOCKERFILE =~ $DIGEST_REGEX ]]; then
    DIGEST="${BASH_REMATCH[1]}"
    echo "Digest found: $DIGEST"
else
    echo >&2 "Docker image digest can not be found in the Dockerfile (with name $DOCKERFILENAME)"
    exit 1
fi

BASE_IMAGE_REGEX='ARG BASE_IMAGE="([^"]+)"'
if [[ $DOCKERFILE =~ $BASE_IMAGE_REGEX ]]; then
    BASE_IMAGE="${BASH_REMATCH[1]}"
    echo "Base image found: $BASE_IMAGE"
else
    echo >&2 "Base image cannot be found in the Dockerfile (with name $DOCKERFILENAME)"
    exit 1
fi

# "Dumb" way to match if the base image already has a fully qualified name or not. If the prefix
# before the slash contains a dot (indicating possibly a domain) or a colon (indicating a port),
# followed by a slash, then we can assume it's fully qualified already.
if [[ ! $BASE_IMAGE =~ ^(.+[\.|:].+)?/.*$ ]]; then
  # No registry prefix, so we add the default one
  BASE_IMAGE="docker.io/library/${BASE_IMAGE}"
fi

# Extract the actual labels from the info - make sure to sort keys so we always have the same
# ordering for maps to compare things properly
# Exclude the minimus.images.version label, since it changes every time the image is patched, and
# we can't really know in advance reliably what it will be.
actualLabels=$(echo "${imageInfo}" | jq --sort-keys '.[0].Config.Labels | del(."io.minimus.images.version")')

if [[ -z "${actualLabels}" || "${actualLabels}" == "null" || "${actualLabels}" == "[]" ]]; then
  echo >&2 "No labels found in the given image ${imageName}; raw inspect output to follow"
  printf >&2 "\n====\n%s\n====\n" "${imageInfo}"
  echo >&2 "Are there any labels in the above? If so, the JSON query may need to be changed."
  exit 1
fi

# Generate the expected labels files with the dynamic properties substituted
labelsGoldenFile="${BASH_SOURCE%/*}/$GOLDENFILE"
expectedLabels=$(
  jq --sort-keys -n \
    --arg VERSION "${VERSION}" \
    --arg REVISION "${REVISION}" \
    --arg DATE "${DATE}" \
    --arg DIGEST "${DIGEST}" \
    --arg BASE_IMAGE "${BASE_IMAGE}" \
    "$(cat "${labelsGoldenFile}")"
)

# Compare and output
echo "Comparing image label values..."
if ! diff <(echo "${expectedLabels}") <(echo "${actualLabels}"); then
  echo >&2 "Expected label values (marked by '<') do not match actual label values (marked by '>'); if you think this is wrong, update the golden file at ${labelsGoldenFile}"
  exit 1
fi
