#!/bin/bash -eux

# Login to the registries happens separately from the script as a GHA step
tags=""

echo "Adding tags to release docker image..."

# Tagging the optimize release Docker image with the specified version
echo "Tagging optimize release docker image with version ${VERSION}"
tags=("${DOCKER_IMAGE_TEAM}:${VERSION}")
tags+=("camunda/optimize:${VERSION}")

# Major and minor versions are always tagged as the latest
if [ "${MAJOR_OR_MINOR}" = true ] || [ "${DOCKER_LATEST}" = true ]; then
   echo "Tagging optimize release docker image with ${DOCKER_LATEST_TAG}"
   tags+=("${DOCKER_IMAGE_TEAM}:${DOCKER_LATEST_TAG}")
   tags+=("${DOCKER_IMAGE_DOCKER_HUB}:${DOCKER_LATEST_TAG}")
fi

# Check if an additional Docker tag is provided and add it to the tags
if [ ! -z "${ADDITIONAL_DOCKER_TAG}" ]; then
  echo "Tagging optimize release docker image with ${ADDITIONAL_DOCKER_TAG}"
  tags+=("${DOCKER_IMAGE_TEAM}:${ADDITIONAL_DOCKER_TAG}")
  tags+=("${DOCKER_IMAGE_DOCKER_HUB}:${ADDITIONAL_DOCKER_TAG}")
fi

printf -v tag_arguments -- "-t %s " "${tags[@]}"
docker buildx create --use

export VERSION="${VERSION}"
export DATE="$(date +%FT%TZ)"
export REVISION="${REVISION}"
export BASE_IMAGE=docker.io/library/alpine:3.19.0

# if CI (GHA) export the variables for pushing in a later step
if [ "${CI}" = "true"  ]; then
    echo "DATE=$DATE" >> "$GITHUB_ENV"
    echo "tag_arguments=$tag_arguments" >> "$GITHUB_ENV"
fi

# Since docker buildx doesn't allow to use --load for a multi-platform build, we do it one at a time to be
# able to perform the checks before pushing
# First arm64
docker buildx build \
    ${tag_arguments} \
    --build-arg VERSION="${VERSION}" \
    --build-arg DATE="${DATE}" \
    --build-arg REVISION="${REVISION}" \
    --platform linux/arm64 \
    --provenance false \
    --load \
    .
export ARCHITECTURE=arm64
./docker/test/verify.sh "${tags[@]}"

# Now amd64
docker buildx build \
    ${tag_arguments} \
    --build-arg VERSION="${VERSION}" \
    --build-arg DATE="${DATE}" \
    --build-arg REVISION="${REVISION}" \
    --platform linux/amd64 \
    --provenance false \
    --load \
    .
export ARCHITECTURE=amd64
./docker/test/verify.sh "${tags[@]}"

