#!/bin/bash -xeu
# Usage:
#   kubectl exec -it zeebe-0 bash -- < profile.sh
#   kubectl cp zeebe-0:/tmp/profiler/flamegraph-2019-03-27_12-42-33.svg .
set -oxe pipefail

unset JAVA_TOOL_OPTIONS

if hash apk 2> /dev/null; then
    apk add --no-cache openjdk11 openjdk11-dbg
else
    mkdir -p /usr/share/man/man1
    apt-get update
    apt-get install -y openjdk-11-jdk openjdk-11-dbg
    apt-get install -y wget
fi

PID=$(jps | grep StandaloneBroker | cut -d " " -f 1)

mkdir -p /tmp/profiler
cd /tmp/profiler

wget -O - https://github.com/jvm-profiling-tools/async-profiler/releases/download/v1.5/async-profiler-1.5-linux-x64.tar.gz | tar xzv

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
./profiler.sh -e itimer -d 60 -f $PWD/flamegraph-$(date +%Y-%m-%d_%H-%M-%S).svg $PID
