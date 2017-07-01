#!/bin/bash

PID=$(pgrep -f zeebe-broker)

# warmup
echo "Warmup"
java -Dsample.numberOfRequests=5000000 -cp target/zeebe-samples-0.1.0-SNAPSHOT-jar-with-dependencies.jar io.zeebe.taskqueue.NonBlockingTaskCreator

# start perf
echo "Recording"
sudo perf record -F 99 -a -g -- sleep 12340000000 &

java -Dsample.numberOfRequests=1000000 -cp target/zeebe-samples-0.1.0-SNAPSHOT-jar-with-dependencies.jar io.zeebe.taskqueue.NonBlockingTaskCreator

JAVA_HOME=/usr/lib/jvm/default /home/menski/github/jrudolph/perf-map-agent/bin/create-java-perf-map.sh $PID

# stop perf
sudo pkill -f "sleep 12340"

sleep 2

sudo perf script --fields comm,pid,tid,time,event,ip,sym,dso,trace | \
    sed 's/+0x[a-fA-F0-9]\+//g' | \
    stackcollapse-perf.pl --pid | \
    grep java-$PID | \
    flamegraph.pl --color=java --hash > flamegraph.svg
