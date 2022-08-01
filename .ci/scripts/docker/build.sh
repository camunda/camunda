#!/bin/sh -eux

echo "Building Zeebe Docker image ${IMAGE}:${TAG}"
docker build --no-cache --build-arg DISTBALL=camunda-zeebe.tar.gz -t ${IMAGE}:${TAG} --target app .
