#!/bin/bash -xeu
# Usage:
#   ./executeProfiling.sh <POD-NAME> [EVENT-TYPE] [ADDITIONAL-OPTIONS]
#
# EVENT-TYPE can be:
#   cpu   - CPU profiling (default)
#   wall  - Wall clock time profiling
#   alloc - Memory allocation profiling
#
# ADDITIONAL-OPTIONS: Optional additional flags to pass to async-profiler (e.g., "-t" to profile threads separately)
# See https://github.com/async-profiler/async-profiler/blob/master/docs/ProfilerOptions.md for potential options
set -oxe pipefail

if [ -z "$1" ]; then
  echo "Error: Missing required argument <POD-NAME>."
  echo "Usage: ./executeProfiling.sh <POD-NAME> [EVENT-TYPE] [ADDITIONAL-OPTIONS]"
  exit 1
fi
node=$1
profiler_event=${2:-cpu}
additional_options=${3:-}

if [[ $profiler_event == "wall" ]]; then
  # Add -t flag for wall profiling to split threads (recommended for wall-clock profiling)
  additional_options="-t $additional_options"
fi

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
filename=flamegraph-$profiler_event-$(date +%Y-%m-%d_%H-%M-%S).html
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

# Run profiling
kubectl exec "$node" -- ./data/asprof -e "$profiler_event" -d 100 -f "$containerPath/$filename" --libpath "$containerPath/libasyncProfiler.so" $additional_options "$PID"

# Copy result
kubectl cp "$node:$containerPath/$filename" "$node-$filename"

# Clean up
# Comment out the following lines to make exeuction faster next time
kubectl exec "$node" -- rm "$containerPath/asprof" "$containerPath/libasyncProfiler.so" "$containerPath/$filename"
rm profiler.tar.gz
rm -r async-profiler-4.0-linux-x64/
