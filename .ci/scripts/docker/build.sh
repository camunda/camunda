#!/bin/sh -eux

echo "Building Zeebe Docker image ${IMAGE}:${TAG}"
docker build --no-cache --build-arg DISTBALL=zeebe-distribution.tar.gz -t ${IMAGE}:${TAG} .
