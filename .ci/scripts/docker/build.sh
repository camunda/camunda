#!/bin/sh -eux

echo "Building Zeebe Docker image ${IMAGE}:${TAG}"
docker build --no-cache \
  --build-arg DISTBALL=camunda-zeebe.tar.gz \
  --build-arg DATE="${DATE}" \
  --build-arg REVISION="${REVISION}" \
  --build-arg VERSION="${VERSION}" \
  -t "${IMAGE}:${TAG}" --target app .
