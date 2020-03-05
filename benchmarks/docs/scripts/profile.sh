#!/bin/bash -xeu
# Usage:
#   kubectl exec -it zeebe-0 bash -- < profile.sh
#   kubectl cp zeebe-0:/tmp/profiler/flamegraph-2019-03-27_12-42-33.svg .


unset JAVA_TOOL_OPTIONS

if hash apk 2> /dev/null; then
    apk add --no-cache openjdk11 openjdk11-dbg
else
    # add stretch backports to get java 11
    echo 'deb http://ftp.debian.org/debian stretch-backports main' | tee /etc/apt/sources.list.d/stretch-backports.list

    # update - to get backports as well
    echo "update"
    apt-get update

    # install jdk 11 from backport
    echo "install jdk"
    apt-get -t stretch-backports install -y openjdk-11-jdk openjdk-11-dbg
fi

PID=$(jps | grep StandaloneBroker | cut -d " " -f 1)

mkdir -p /tmp/profiler
cd /tmp/profiler

wget -O - https://github.com/jvm-profiling-tools/async-profiler/releases/download/v1.5/async-profiler-1.5-linux-x64.tar.gz | tar xzvf -

./profiler.sh -d 60 -f $PWD/flamegraph-$(date +%Y-%m-%d_%H-%M-%S).svg $PID
