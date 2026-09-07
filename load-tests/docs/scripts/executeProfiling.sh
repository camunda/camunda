#!/bin/bash -xeu
# Usage:
#  ./executeProfiling.sh [-p|--prefix PREFIX] <POD-NAME> [EVENT-TYPE] [ADDITIONAL-OPTIONS]
#  PROFILING_DURATION=200 ./executeProfiling.sh <POD-NAME> [EVENT-TYPE] [ADDITIONAL-OPTIONS]
#
# EVENT-TYPE can be:
#   cpu   - CPU profiling (default)
#   wall  - Wall clock time profiling
#   alloc - Memory allocation profiling
# ADDITIONAL-OPTIONS: Optional additional flags to pass to async-profiler (e.g., "-t" to profile threads separately)
# See https://github.com/async-profiler/async-profiler/blob/master/docs/ProfilerOptions.md for potential options
# -p, --prefix: Filename prefix for the generated report (default: flamegraph-)
#
# Environment variables:
#   PROFILING_DURATION - profiling duration in seconds (default: 100)
set -oxe pipefail

prefix="flamegraph-"
if [ "${1:-}" = "-p" ] || [ "${1:-}" = "--prefix" ]; then
  if [ -z "${2:-}" ]; then
    echo "Error: ${1} requires a value."
    echo "Usage: ./executeProfiling.sh [-p|--prefix PREFIX] <POD-NAME> [EVENT-TYPE] [ADDITIONAL-OPTIONS]"
    exit 1
  fi
  prefix="$2"
  shift 2
fi

if [ -z "${1:-}" ]; then
  echo "Error: Missing required argument <POD-NAME>."
  echo "Usage: ./executeProfiling.sh [-p|--prefix PREFIX] <POD-NAME> [EVENT-TYPE] [ADDITIONAL-OPTIONS]"
  exit 1
fi
pod_name=$1

profiler_event="${2:-cpu}"
additional_options="${3:-}"

OUTPUT_DIR="${OUTPUT_DIR:-"profiling"}"
echo "Profiling reports will be saved to $OUTPUT_DIR"

if [[ $profiler_event == "wall" ]]; then
  # Add -t flag for wall profiling to split threads (recommended for wall-clock profiling)
  additional_options="-t $additional_options"
fi

# Determine right container path
containerPath=/usr/local/camunda/data
if ! kubectl exec "$pod_name" -- ls -la "$containerPath";
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

if ! kubectl exec "$pod_name" -- test -f data/libasyncProfiler.so;
then
  # Copy async profiler to pod
  kubectl cp async-profiler-4.0-linux-x64/bin/asprof "$pod_name":"$containerPath/asprof"
  kubectl cp async-profiler-4.0-linux-x64/lib/libasyncProfiler.so "$pod_name":"$containerPath/libasyncProfiler.so"
  kubectl exec "$pod_name" -- chmod +x "$containerPath/asprof"
fi

filename="${prefix}${profiler_event}.html"
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
PID=$(kubectl exec "$pod_name" -- ps -ax | awk '$5 ~ /java/ {print $1}')

# Run profiling
kubectl exec "$pod_name" -- ./data/asprof -e "$profiler_event" -d "${PROFILING_DURATION:-100}" -f "$containerPath/$filename" --libpath "$containerPath/libasyncProfiler.so" $additional_options "$PID"

# Copy result into specified output directory.
mkdir -p "$OUTPUT_DIR"
kubectl cp "$pod_name:$containerPath/$filename" "$OUTPUT_DIR/$filename"

# Clean up
# Comment out the following lines to make exeuction faster next time
# These are best-effort: a cleanup failure (e.g. a file already removed,
# a permission hiccup, or a transient exec error) must not fail the whole
# profiling run, so failures here are swallowed rather than propagated.
kubectl exec "$pod_name" -- rm -f "$containerPath/asprof" "$containerPath/libasyncProfiler.so" "$containerPath/$filename" || true
rm -f profiler.tar.gz
rm -rf async-profiler-4.0-linux-x64/
