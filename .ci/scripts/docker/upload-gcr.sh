#!/bin/sh -eux

# this command is a little convoluted to avoid leaking of the secret;
# it is unclear why the built-in Jenkins mechanism doesn't work out of the box
echo "Authenticating with gcr.io and pushing image."
set +x ; echo ${DOCKER_GCR} | docker login -u _json_key --password-stdin https://gcr.io ; set -x

docker push ${IMAGE}:${TAG}


