#!/bin/bash -xeu
# Usage:
#   ./executeProfiling.sh <POD-NAME>
set -oxe pipefail

if [ -z "$1" ]; then
  echo "Error: Missing required argument <POD-NAME>."
  echo "Usage: ./executeProfiling.sh <POD-NAME>"
  exit 1
fi
node=$1

# Download and extract latest async profiler
curl -L https://github.com/jvm-profiling-tools/async-profiler/releases/download/v4.0/async-profiler-4.0-linux-x64.tar.gz -o profiler.tar.gz
tar -xzvf profiler.tar.gz

# Copy async profiler to pod
kubectl cp async-profiler-4.0-linux-x64/bin/asprof "$node":/usr/local/camunda/data/asprof
kubectl exec "$node" -- mkdir -p /usr/local/camunda/data/lib
kubectl cp async-profiler-4.0-linux-x64/lib/libasyncProfiler.so "$node":/usr/local/camunda/data/libasyncProfiler.so
kubectl exec "$node" -- chmod +x /usr/local/camunda/data/asprof

# Run profiling
filename=flamegraph-$(date +%Y-%m-%d_%H-%M-%S).html
PID=$(kubectl exec "$node" -- jps | grep Standalone | cut -d " " -f 1)
kubectl exec "$node" -- ./data/asprof -e itimer -d 100 -t -f "/usr/local/camunda/data/$filename" --libpath /usr/local/camunda/data/libasyncProfiler.so "$PID"

# Copy result
kubectl cp "$node:/usr/local/camunda/data/$filename" "$node-$filename"
