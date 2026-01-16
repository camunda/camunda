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

# Determine right container path
containerPath=/usr/local/camunda/data
if ! kubectl exec "$node" -- ls -la "$containerPath";
then
  # Old container path
  containerPath=/usr/local/zeebe/data
fi


# Download and extract latest async profiler
if [ ! -d "async-profiler-4.0-linux-x64/" ];
then
  curl -L https://github.com/jvm-profiling-tools/async-profiler/releases/download/v4.0/async-profiler-4.0-linux-x64.tar.gz -o profiler.tar.gz
  tar -xzvf profiler.tar.gz
fi

if ! kubectl exec "$node" -- test -f data/libasyncProfiler.so;
then
  # Copy async profiler to pod
  kubectl cp async-profiler-4.0-linux-x64/bin/asprof "$node":"$containerPath/asprof"
  kubectl cp async-profiler-4.0-linux-x64/lib/libasyncProfiler.so "$node":"$containerPath/libasyncProfiler.so"
  kubectl exec "$node" -- chmod +x "$containerPath/asprof"
fi

# Run profiling
filename=flamegraph-$(date +%Y-%m-%d_%H-%M-%S).html
# Extracting the PID:
#
#  $ k exec camunda-0 -it -- ps -ax
#    PID TTY      STAT   TIME COMMAND
#      1 ?        Ssl  570:26 /usr/lib/jvm/default-jvm/bin/java -XX:+ExitOnOutOfM
#   5905 pts/0    Rs+    0:00 ps -ax
#
#   As we want to find the PID of the Java process we can use awk
#   to check the fifth input whether it contains "/java/"
#   If so we return the first input, which is the PID
PID=$(kubectl exec "$node" -- ps -ax | awk '$5 ~ /java/ {print $1}')
kubectl exec "$node" -- ./data/asprof -e itimer -d 100 -t -f "$containerPath/$filename" --libpath "$containerPath/libasyncProfiler.so" "$PID"

# Copy result
kubectl cp "$node:$containerPath/$filename" "$node-$filename"

# Clean up
# Comment out the following lines to make exeuction faster next time
kubectl exec "$node" -- rm "$containerPath/asprof" "$containerPath/libasyncProfiler.so" "$containerPath/$filename"
rm profiler.tar.gz
rm -r async-profiler-4.0-linux-x64/
