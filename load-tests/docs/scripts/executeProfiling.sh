#!/bin/bash -xeu
# Usage:
#  ./executeProfiling.sh <POD-NAME> [EVENT-TYPE] [ADDITIONAL-OPTIONS] [TEST-TYPE]
#  PROFILING_DURATION=200 ./executeProfiling.sh <POD-NAME> [EVENT-TYPE] [ADDITIONAL-OPTIONS] [TEST-TYPE]
#
# EVENT-TYPE can be:
#   cpu   - CPU profiling (default)
#   wall  - Wall clock time profiling
#   alloc - Memory allocation profiling
# ADDITIONAL-OPTIONS: Optional additional flags to pass to async-profiler (e.g., "-t" to profile threads separately)
# See https://github.com/async-profiler/async-profiler/blob/master/docs/ProfilerOptions.md for potential options
# TEST-TYPE: Short label identifying the calling test/database variant (e.g. "grpc", "rest",
#            "elasticsearch", "opensearch"), included in the output filename to disambiguate
#            runs. Leave empty to omit it.
#
# Environment variables:
#   PROFILING_DURATION - profiling duration in seconds (default: 100)
set -oxe pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "${SCRIPT_DIR}/../../setup/utils.sh"

if [ -z "$1" ]; then
  echo "Error: Missing required argument <POD-NAME>."
  echo "Usage: ./executeProfiling.sh <POD-NAME> [EVENT-TYPE] [ADDITIONAL-OPTIONS] [TEST-TYPE]"
  exit 1
fi
pod_name=$1

profiler_event="${2:-cpu}"
additional_options="${3:-}"
test_type="${4:-}"

OUTPUT_DIR="${OUTPUT_DIR:-"profiling"}"
echo "Profiling reports will be saved to $OUTPUT_DIR"

if [[ $profiler_event == "wall" ]]; then
  # Add -t flag for wall profiling to split threads (recommended for wall-clock profiling)
  additional_options="-t $additional_options"
fi

# Determine right container path
# Retried: a transient exec/connection flake here would otherwise be
# misread as "new path doesn't exist", silently picking the wrong
# containerPath for the rest of the script.
containerPath=/usr/local/camunda/data
if ! retry_with_backoff kubectl exec "$pod_name" -- ls -la "$containerPath";
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

if ! retry_with_backoff kubectl exec "$pod_name" -- test -f data/libasyncProfiler.so;
then
  # Copy async profiler to pod. Safely retryable: re-copying/re-chmod-ing the
  # same files after a transient exec/cp failure is idempotent.
  retry_with_backoff kubectl cp async-profiler-4.0-linux-x64/bin/asprof "$pod_name":"$containerPath/asprof"
  retry_with_backoff kubectl cp async-profiler-4.0-linux-x64/lib/libasyncProfiler.so "$pod_name":"$containerPath/libasyncProfiler.so"
  retry_with_backoff kubectl exec "$pod_name" -- chmod +x "$containerPath/asprof"
fi

# Run profiling
# Build the filename incrementally so an empty test_type doesn't leave behind
# stray/doubled separators, e.g. flamegraph--cpu-20260710.html.
filename="flamegraph"
if [ -n "$test_type" ]; then
  filename="$filename-$test_type"
fi
filename="$filename-$profiler_event-$(date +%Y%m%d).html"
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
get_java_pid() {
  kubectl exec "$pod_name" -- ps -ax | awk '$5 ~ /java/ {print $1}'
}
PID=$(retry_with_backoff get_java_pid)

# Run profiling
# Intentionally NOT wrapped in retry_with_backoff: PROFILING_DURATION can run
# up to 30 minutes, and a failure here (e.g. exit 137/SIGKILL) is far more
# likely to be the profiled JVM or asprof itself getting OOM-killed under
# profiling overhead than a transient exec/connection flake. Retrying an
# OOM-killed run just burns another full profiling window to get OOM-killed
# again, with no realistic chance of succeeding.
kubectl exec "$pod_name" -- ./data/asprof -e "$profiler_event" -d "${PROFILING_DURATION:-100}" -f "$containerPath/$filename" --libpath "$containerPath/libasyncProfiler.so" $additional_options "$PID"

# Copy result into specified output directory. Safely retryable:
# re-running kubectl cp for the same already-written flamegraph file is
# idempotent.
mkdir -p "$OUTPUT_DIR"
retry_with_backoff kubectl cp "$pod_name:$containerPath/$filename" "$OUTPUT_DIR/$filename"

# Clean up
# Comment out the following lines to make exeuction faster next time
# These are best-effort: a cleanup failure (e.g. a file already removed,
# a permission hiccup, or a transient exec error) must not fail the whole
# profiling run, so failures here are swallowed rather than propagated.
kubectl exec "$pod_name" -- rm -f "$containerPath/asprof" "$containerPath/libasyncProfiler.so" "$containerPath/$filename" || true
rm -f profiler.tar.gz
rm -rf async-profiler-4.0-linux-x64/
