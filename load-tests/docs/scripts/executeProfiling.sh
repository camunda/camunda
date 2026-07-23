#!/bin/bash -xeu
# Usage:
#  ./executeProfiling.sh <POD-NAME> [EVENTS] [ADDITIONAL-OPTIONS] [TEST-TYPE]
#  PROFILING_DURATION=200 ./executeProfiling.sh <POD-NAME> [EVENTS] [ADDITIONAL-OPTIONS] [TEST-TYPE]
#
# EVENTS: comma-separated list of async-profiler events to capture (default: "cpu,wall,alloc"):
#   cpu   - CPU profiling
#   wall  - Wall clock time profiling (its flamegraph is converted with -t to split by thread)
#   alloc - Memory allocation profiling
# All requested events are captured together in a SINGLE async-profiler attach/session,
# written to one combined JFR recording -- async-profiler only supports one active
# profiling session per JVM, but a single session CAN sample multiple event types at once.
# See https://github.com/async-profiler/async-profiler/blob/master/docs/ProfilingModes.md#multiple-events
# The combined recording is then split into one HTML flamegraph per requested event using
# `jfrconv`, which ships in the same `bin/` directory as `asprof` in the async-profiler
# release and requires a JRE to run. The profiled pod already runs a JVM, so `jfrconv` is
# executed there too, right after the capture and before cleanup.
# See https://github.com/async-profiler/async-profiler/blob/master/docs/ConverterUsage.md
#
# ADDITIONAL-OPTIONS: Optional additional flags to pass to async-profiler's attach (applies
# to the whole capture session, since all events now share one attach). See
# https://github.com/async-profiler/async-profiler/blob/master/docs/ProfilerOptions.md for
# potential options.
# TEST-TYPE: Short label identifying the calling test/database variant (e.g. "grpc", "rest",
#            "elasticsearch", "opensearch"), included in the output filename to disambiguate
#            runs. Leave empty to omit it.
#
# Environment variables:
#   PROFILING_DURATION - profiling duration in seconds (default: 100)
#   WALL_INTERVAL       - wall-clock sampling interval, only used when "wall" is one of
#                         several requested EVENTS (default: 100ms, see --wall in
#                         ProfilerOptions.md)
#   ALLOC_INTERVAL       - allocation sampling interval, only used when "alloc" is one of
#                         several requested EVENTS (default: 2m, see the "Multiple events"
#                         example in ProfilingModes.md)
set -oxe pipefail

if [ -z "$1" ]; then
  echo "Error: Missing required argument <POD-NAME>."
  echo "Usage: ./executeProfiling.sh <POD-NAME> [EVENTS] [ADDITIONAL-OPTIONS] [TEST-TYPE]"
  exit 1
fi
node=$1

events_csv="${2:-cpu,wall,alloc}"
additional_options="${3:-}"
test_type="${4:-}"

IFS=',' read -ra events <<< "$events_csv"

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
  # Copy async profiler + jfr converter to pod (both ship in the release's bin/ directory)
  kubectl cp async-profiler-4.0-linux-x64/bin/asprof "$node":"$containerPath/asprof"
  kubectl cp async-profiler-4.0-linux-x64/bin/jfrconv "$node":"$containerPath/jfrconv"
  kubectl cp async-profiler-4.0-linux-x64/lib/libasyncProfiler.so "$node":"$containerPath/libasyncProfiler.so"
  kubectl exec "$node" -- chmod +x "$containerPath/asprof" "$containerPath/jfrconv"
fi

# Build the filenames incrementally so an empty test_type doesn't leave behind
# stray/doubled separators, e.g. flamegraph--cpu-20260710.html.
run_date=$(date +%Y%m%d)

combined_filename="combined"
if [ -n "$test_type" ]; then
  combined_filename="$combined_filename-$test_type"
fi
combined_filename="$combined_filename-$run_date.jfr"

html_filenames=()
for event in "${events[@]}"; do
  html_filename="flamegraph"
  if [ -n "$test_type" ]; then
    html_filename="$html_filename-$test_type"
  fi
  html_filename="$html_filename-$event-$run_date.html"
  html_filenames+=("$html_filename")
done

# Build the asprof event flags for a single combined attach. The first non-wall/alloc
# event (normally "cpu") is passed via -e; wall and alloc need their own dedicated flags to
# be combined with another event in the same JFR recording (asprof's "-e a,b,c" comma-list
# form doesn't support wall combined with another event -- --wall INTERVAL is required
# instead, see ProfilerOptions.md).
primary_event="cpu"
asprof_event_args=()
for event in "${events[@]}"; do
  case "$event" in
    wall)
      asprof_event_args+=(--wall "${WALL_INTERVAL:-100ms}")
      ;;
    alloc)
      asprof_event_args+=(--alloc "${ALLOC_INTERVAL:-2m}")
      ;;
    *)
      primary_event="$event"
      ;;
  esac
done

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

# Run profiling: a single attach captures all requested events into one combined JFR
# recording.
kubectl exec "$node" -- ./data/asprof -e "$primary_event" "${asprof_event_args[@]}" -d "${PROFILING_DURATION:-100}" -f "$containerPath/$combined_filename" --libpath "$containerPath/libasyncProfiler.so" $additional_options "$PID"

# Convert the combined recording into one HTML flamegraph per requested event, still
# inside the pod (jfrconv requires a JRE, which the profiled pod already has).
for i in "${!events[@]}"; do
  event="${events[$i]}"
  html_filename="${html_filenames[$i]}"
  convert_options=()
  if [[ "$event" == "wall" ]]; then
    # Split by thread for wall-clock profiling (recommended for wall-clock analysis)
    convert_options+=("-t")
  fi
  kubectl exec "$node" -- ./data/jfrconv "--$event" "${convert_options[@]}" "$containerPath/$combined_filename" "$containerPath/$html_filename"
done

# Copy results
for html_filename in "${html_filenames[@]}"; do
  kubectl cp "$node:$containerPath/$html_filename" "$node-$html_filename"
done

# Clean up
# Comment out the following lines to make exeuction faster next time
remote_html_paths=()
for html_filename in "${html_filenames[@]}"; do
  remote_html_paths+=("$containerPath/$html_filename")
done
kubectl exec "$node" -- rm "$containerPath/asprof" "$containerPath/jfrconv" "$containerPath/libasyncProfiler.so" "$containerPath/$combined_filename" "${remote_html_paths[@]}"
rm profiler.tar.gz
rm -r async-profiler-4.0-linux-x64/
