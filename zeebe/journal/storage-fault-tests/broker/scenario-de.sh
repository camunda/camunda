#!/usr/bin/env bash
# Scenarios D and E on 8.8.41: a follower's snapshot install fails because its (real, small) disk is
# full. D: the follower is then stopped (graceful|kill), space freed, restarted.
# E: while it loops, the leader is stopped; the other follower (no snapshot) leads and sends entries.
# usage: scenario-de.sh <tmpfs|ext4> <D-graceful|D-kill|E>
FS=$1; VARIANT=$2
export PARTITIONS=1 SEGMENT_SIZE=16MB SNAPSHOT_PERIOD=1h MSG_TTL=0 PROC_MB=2 REPL_MB=1
LOGDIR=/work/broker/logs-de-$FS-$VARIANT
source /work/broker/cluster.sh
rm -rf "$LOGDIR"; mkdir -p "$LOGDIR"
SMALL=/small; mkdir -p $SMALL /data0 /data1 /data2; rm -rf /data0/* /data1/* /data2/*
if [ "$FS" = tmpfs ]; then
  mount -t tmpfs -o size=512m tmpfs $SMALL
else
  rm -f /var/ext4.img; dd if=/dev/zero of=/var/ext4.img bs=1M count=512 status=none
  mkfs.ext4 -q -F /var/ext4.img && mount -o loop /var/ext4.img $SMALL || { echo "ext4 loop mount failed"; exit 1; }
fi
df -h $SMALL | tail -1

for i in 0 1 2; do export DATA_DIR_$i=/data$i; done
for i in 0 1 2; do start_node $i "node$i-start.log"; done
for i in 0 1 2; do await_node $i "node$i-start.log" 240; echo "node $i up rc=$? $(node_partitions $i)"; done
L=""; for i in 0 1 2; do [[ "$(node_partitions $i)" == *LEADER* ]] && L=$i; done
FOLLOWERS=(); for i in 0 1 2; do [ "$i" != "$L" ] && FOLLOWERS+=($i); done
F=${FOLLOWERS[0]}; G=${FOLLOWERS[1]}
echo "leader $L, follower under test F=$F (data on $FS), other follower G=$G"

# F's data moves to the small filesystem: stop it, move its directory, restart it there
kill -TERM "${PID[$F]}"; wait "${PID[$F]}" 2>/dev/null
cp -a /data$F/. $SMALL/ && export DATA_DIR_$F=$SMALL
start_node $F "node$F-small.log"; await_node $F "node$F-small.log" 120; echo "F on $FS: rc=$? $(node_partitions $F)"
start_load "$L" 4 1000 load-warmup.log; sleep 20; kill -9 $LOAD

# F falls behind: stop it, keep loading through the leader, snapshot only on the leader
kill -TERM "${PID[$F]}"; wait "${PID[$F]}" 2>/dev/null
start_load "$L" 6 1000 load.log; sleep 45; kill -9 $LOAD
echo "load while F down: $(tail -1 $LOGDIR/load.log)"
curl -s -X POST "localhost:$(port $L 9600)/actuator/partitions/takeSnapshot" >/dev/null; sleep 5
start_load "$L" 6 1000 load2.log; sleep 20; kill -9 $LOAD   # roll over so compaction can remove old segments
curl -s -X POST "localhost:$(port $L 9600)/actuator/partitions/takeSnapshot" >/dev/null; sleep 5
echo "leader snapshot: $(ls /data$L/raft-partition/partitions/1/snapshots 2>/dev/null | tr '\n' ' ')"
echo "G snapshots: $(ls /data$G/raft-partition/partitions/1/snapshots 2>/dev/null | grep -v pending | tr '\n' ' ')"

# F's old segments stay allocated after deletion (hard links = a reader still holding them)
mkdir -p $SMALL/hold && ln $SMALL/raft-partition/partitions/1/*.log $SMALL/hold/
echo "held segments: $(ls $SMALL/hold | tr '\n' ' ')"
# F's disk fills up: leave ~10 MB (enough for the snapshot, not for a 16 MB segment)
avail=$(( $(df --output=avail -B1 $SMALL | tail -1) / 1048576 ))
dd if=/dev/zero of=$SMALL/ballast bs=1M count=$(( avail - 10 )) status=none conv=fsync 2>/dev/null
echo "F disk: $(df -h $SMALL | tail -1 | awk '{print $4" free of "$2}')"

start_node $F "node$F-install.log"
for _ in $(seq 1 60); do grep -q "Failed to commit pending snapshot" "$LOGDIR/node$F-install.log" && break; sleep 1; done
sleep 10
echo "--- F after start (install attempts):"
grep -hE "Failed to commit pending snapshot|OutOfDiskSpace:|Failed to install partition|journal has no first|currentSegment|Committed snapshot" "$LOGDIR/node$F-install.log" | sed -E 's/^\s+//' | cut -c1-150 | sort | uniq -c | head -8

if [ "$VARIANT" = E ]; then
  echo "--- E: stopping leader $L; G=$G (no snapshot) takes over"
  kill -TERM "${PID[$L]}"
  for _ in $(seq 1 60); do kill -0 "${PID[$L]}" 2>/dev/null || break; sleep 1; done
  kill -9 "${PID[$L]}" 2>/dev/null; wait "${PID[$L]}" 2>/dev/null
  sleep 40
  echo "   F process alive: $(kill -0 "${PID[$F]}" 2>/dev/null && echo yes || echo NO)"
  grep -hE "SIGSEGV|SIGBUS|Problematic frame|^# [JjVv] " "$LOGDIR/node$F-install.log" | head -5
  echo "   new leader's view of F:"
  grep -B1 -E "AppendRequest\{.*\} to $F failed|InstallRequest.* to member $F" "$LOGDIR/node$G-start.log" | grep -oE 'prevLogIndex=[0-9]+, prevLogTerm=[0-9]+, entries=[0-9]+, commitIndex=[0-9]+|to member [0-9]+, with RaftError\{[^}]*\}' | sort | uniq -c | head -5
  kill -9 "${PID[$F]}" 2>/dev/null; wait "${PID[$F]}" 2>/dev/null
  start_node $L "node$L-restart.log"
else
  if [ "$VARIANT" = D-graceful ]; then
    kill -TERM "${PID[$F]}"
    for _ in $(seq 1 90); do kill -0 "${PID[$F]}" 2>/dev/null || break; sleep 1; done
    if kill -0 "${PID[$F]}" 2>/dev/null; then
      echo "   graceful stop hung for 90s (install attempts so far: $(grep -c 'Failed to commit pending' $LOGDIR/node$F-install.log)); thread dump + SIGKILL"
      jcmd "${PID[$F]}" Thread.print > "$LOGDIR/node$F-threaddump.txt" 2>&1
      kill -9 "${PID[$F]}"
    fi
  else kill -9 "${PID[$F]}"; fi
  wait "${PID[$F]}" 2>/dev/null
fi
echo "--- F's partition files before restart: $(ls $SMALL/raft-partition/partitions/1/ | grep -vE 'snapshots|runtime|pending' | tr '\n' ' ')"
rm -rf $SMALL/ballast $SMALL/hold
start_node $F "node$F-restart.log"
await_node $F "node$F-restart.log" 120; rc=$?
echo "F restart rc=$rc $(node_partitions $F)"
grep -hE "CorruptedJournalException|UnknownVersionException|expected to find a snapshot|IllegalStateException" "$LOGDIR/node$F-restart.log" | sed -E 's/^\s+//' | cut -c1-200 | sort | uniq -c | head -5
for i in 0 1 2; do kill -9 "${PID[$i]}" 2>/dev/null; done
umount $SMALL 2>/dev/null
echo DONE
