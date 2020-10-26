#!/bin/sh -eux

echo "Building Zeebe with Hazelast exporter Docker image zeebe-hazelcast-exporter:current-test"
docker build --no-cache -f .ci/docker/Dockerfile_zeebe-hazelcast-exporter . -t zeebe-hazelcast-exporter:current-test
