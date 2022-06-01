#!/bin/sh -eux

dockerImageName="localhost:5000/${IMAGE}:${TAG}"

# Build, push image, and start TC Cloud agent
echo "Starting local Docker registry"
docker run --rm -d -p 5000:5000 --name registry registry:2

echo "Building Zeebe Docker image ${dockerImageName}"
docker build --no-cache --build-arg DISTBALL=camunda-zeebe.tar.gz -t "${dockerImageName}" --target app .
docker push "${dockerImageName}"
