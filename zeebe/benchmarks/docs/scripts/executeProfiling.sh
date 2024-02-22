#!/bin/bash -xeu
# Usage:
#   kubectl exec -it zeebe-0 bash -- < profile.sh
#   kubectl cp zeebe-0:/tmp/profiler/flamegraph-2019-03-27_12-42-33.svg .
set -oxe pipefail

node=$1

kubectl cp installProfiler.sh "$node":/usr/local/zeebe/installProfiler.sh
kubectl cp runProfiler.sh "$node":/usr/local/zeebe/runProfiler.sh

kubectl exec "$node" -- ./installProfiler.sh

filename=flamegraph-$(date +%Y-%m-%d_%H-%M-%S).svg

kubectl exec "$node" -- ./runProfiler.sh "$filename"

kubectl cp "$node:/tmp/profiler/$filename" "$filename"
