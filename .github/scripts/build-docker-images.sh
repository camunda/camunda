#!/bin/bash -eux

# Login to the registries happens separately from the script as a GHA step

tags=("${DOCKER_IMAGE_TEAM}:${DOCKER_TAG}" "${DOCKER_IMAGE_TEAM}:${DOCKER_BRANCH_TAG}")

if [ "${PUSH_LATEST_TAG}" = "true" ]; then
    tags+=("${DOCKER_IMAGE_TEAM}:${DOCKER_LATEST_TAG}")
fi

if [ "${IS_MAIN}" = "true" ]; then
    tags+=("${DOCKER_IMAGE_DOCKER_HUB}:SNAPSHOT")
fi

printf -v tag_arguments -- "-t %s " "${tags[@]}"
docker buildx create --use

export VERSION="${VERSION}"
export DATE="$(date +%FT%TZ)"
export REVISION="${REVISION}"
export BASE_IMAGE=docker.io/library/alpine:3.18.5

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
    --load \
    .
export ARCHITECTURE=amd64
./docker/test/verify.sh "${tags[@]}"
