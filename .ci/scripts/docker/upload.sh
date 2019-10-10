#!/bin/sh -eux

echo "Authenticating with DockerHub and pushing image."
docker login --username ${DOCKER_HUB_USR} --password ${DOCKER_HUB_PSW}

docker tag ${IMAGE}:current-test ${IMAGE}:${TAG}
docker push ${IMAGE}:${TAG}

if ${IS_LATEST}; then
    docker tag ${IMAGE}:${TAG} ${IMAGE}:latest
    docker push ${IMAGE}:latest
fi
