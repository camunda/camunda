#!/bin/sh -eux

echo "Pulling base images for testcontainers"
docker pull alpine:3.5
docker pull quay.io/testcontainers/ryuk:0.2.3

echo "Pulling images required for upgrade tests"
docker pull camunda/zeebe:0.23.0
