#!/bin/bash -xeu
# Usage:
#   kubectl exec -it zeebe-0 bash -- < profile.sh
#   kubectl cp zeebe-0:/tmp/profiler/flamegraph-2019-03-27_12-42-33.svg .
set -oxe pipefail

filename=$1

PID=$(jps | grep Standalone | cut -d " " -f 1)

mkdir -p /tmp/profiler
cd /tmp/profiler

# Running Profiler on k8:
#
#  * -e cpu will not work since we need more permissions in the docker image
#  * echo 1 > /proc/sys/kernel/perf_event_paranoid will not work since read-only file system
#  * adding sys_admin capalities seem to have no effect - at least with the helm charts
#
# Workaround for now is -e itimer, which then doesn't show the kernel calls
#
# Resources:
#
# https://blog.alicegoldfuss.com/enabling-perf-in-kubernetes/
# https://wenfeng-gao.github.io/post/how-to-use-async-profiler-to-profile-java-in-contianer/
./profiler.sh -e itimer -d 60 -t -f "$PWD/$filename" $PID
