#!/bin/bash -eux

# Login to the registries happens separately from the script as a GHA step

tags=("${DOCKER_IMAGE_TEAM}:${DOCKER_TAG}" "${DOCKER_IMAGE_TEAM}:${DOCKER_BRANCH_TAG}")

if [ "${PUSH_LATEST_TAG}" = "true" ]; then
    tags+=("${DOCKER_IMAGE_TEAM}:${DOCKER_LATEST_TAG}")
fi

if [ "${IS_MAIN}" = "true" ]; then
    tags+=("${DOCKER_IMAGE_DOCKER_HUB}:8-SNAPSHOT")
fi

# temporarily needed for https://github.com/camunda/product-hub/issues/2618
if [ "${IS_STABLE_87}" = "true" ]; then
    tags+=("${DOCKER_IMAGE_DOCKER_HUB}:8.7.0-SNAPSHOT")
fi

printf -v tag_arguments -- "-t %s " "${tags[@]}"
docker buildx create --use

export VERSION="${VERSION}"
export DATE="$(date +%FT%TZ)"
export REVISION="${REVISION}"
export BASE_IMAGE=docker.io/library/alpine:3.21.3

# if CI (GHA) export the variables for pushing in a later step
if [ "${CI}" = "true" ]; then
    echo "DATE=$DATE" >>"$GITHUB_ENV"
    echo "tag_arguments=$tag_arguments" >>"$GITHUB_ENV"
fi

docker buildx build \
    ${tag_arguments} \
    --build-arg VERSION="${VERSION}" \
    --build-arg DATE="${DATE}" \
    --build-arg REVISION="${REVISION}" \
    --provenance false \
    --load \
    -f optimize.Dockerfile \
    .

./optimize/docker/test/verify.sh "${tags[@]}"
