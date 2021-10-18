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

mkdir -p /tmp/profiler
cd /tmp/profiler

wget -O - https://github.com/jvm-profiling-tools/async-profiler/releases/download/v1.5/async-profiler-1.5-linux-x64.tar.gz | tar xzv
