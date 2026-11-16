#!/usr/bin/env bash
# Scenario G (a follower that fails to restart after full disks): three 8.8.41 brokers, six partitions, disks fill up cluster-wide,
# partitions go idle, broker 1 is replaced (graceful stop + node cache lost), restarts on a full disk.
# usage: scenario-g.sh <quota|plain> <graceful|kill>
MODE=${1:-quota}; END=${2:-graceful}; VICTIM=1
export PARTITIONS=6
LOGDIR=/work/broker/logs-g-$MODE-$END${RUN_TAG:+-$RUN_TAG}
source /work/broker/cluster.sh
rm -rf "$LOGDIR"; mkdir -p "$LOGDIR"
SEG_RE='raft-partition-partition-[0-9]+-[0-9]+\.log$'

for i in 0 1 2; do rm -f /tmp/quota-full$i; rm -rf "$(root $i)"; lfs_mount_i $i || exit 1; done
U0_MB=$(( $(df --output=avail -B1 "$(mnt 0)" | tail -1) / 1048576 ))
export PROC_MB=$(( U0_MB - 1500 )) REPL_MB=$(( U0_MB - 3000 ))
echo "free at start ${U0_MB} MB; processing limit ${PROC_MB} MB, replication limit ${REPL_MB} MB"

for i in 0 1 2; do start_node $i "node$i-start.log"; done
for i in 0 1 2; do await_node $i "node$i-start.log" 240; echo "node $i up rc=$? $(node_partitions $i)"; done

start_load 0 4 1000 load.log
sleep 40
echo "after 40s load: $(tail -1 $LOGDIR/load.log)"
if [ "$MODE" = quota ]; then
  for i in 0 1 2; do echo "$SEG_RE" > /tmp/quota-full$i; done
  echo "QUOTA exhausted on all brokers (segment flushes acknowledged without committing)"
fi
sleep ${QUOTA_WINDOW:-10}
echo "last writes before the stall: $(tail -1 $LOGDIR/load.log)"
# the disks fill up: free space between the replication and the processing limit
avail_mb=$(( $(df --output=avail -B1 "$(mnt 2)" | tail -1) / 1048576 ))
ballast_mb=$(( avail_mb - PROC_MB + 200 ))
dd if=/dev/zero of="$(root 2)/ballast" bs=1M count="$ballast_mb" conv=fsync status=none   # same backing filesystem, bypasses the LazyFS cache
echo "DISK filled with ${ballast_mb} MB: free now $(( $(df --output=avail -B1 "$(mnt 0)" | tail -1) / 1048576 )) MB"
sleep 15
kill -9 $LOAD 2>/dev/null
echo "load stopped: $(tail -1 $LOGDIR/load.log)"
for i in 0 1 2; do echo "   node $i: $(node_partitions $i)"; done
echo "idle for 60s"; sleep 60

echo "REPLACING node $VICTIM ($END)"
if [ "$END" = graceful ]; then
  kill -TERM "${PID[$VICTIM]}"
  for _ in $(seq 1 90); do kill -0 "${PID[$VICTIM]}" 2>/dev/null || break; sleep 1; done
  kill -0 "${PID[$VICTIM]}" 2>/dev/null && { echo "   did not stop in 90s, killing"; kill -9 "${PID[$VICTIM]}"; }
  wait "${PID[$VICTIM]}" 2>/dev/null
  # node cache lost: anything the storage never committed is gone
  sync; echo "lazyfs::clear-cache" > "$(fifo $VICTIM)"; sleep 0.5; lfs_umount_i $VICTIM
else
  lfs_power_loss_i $VICTIM "${PID[$VICTIM]}" > /dev/null
fi
echo "   quota-skipped fsyncs on node $VICTIM: $(grep -c 'lazyfs.quota' "$(llog $VICTIM)" 2>/dev/null || echo 0)"
rm -f /tmp/quota-full$VICTIM
lfs_mount_i $VICTIM
start_node $VICTIM "node$VICTIM-restart.log"
await_node $VICTIM "node$VICTIM-restart.log" 120; rc=$?
echo "node $VICTIM restart rc=$rc partitions: $(node_partitions $VICTIM)"
echo "--- journal errors on node $VICTIM:"
grep -hE "CorruptedJournalException|UnknownVersionException" "$LOGDIR/node$VICTIM-restart.log" | sed -E 's/^\s+//' | cut -c1-200 | sort | uniq -c
echo "--- leaders' heartbeats to node $VICTIM (first per partition after the stop):"
for i in 0 2; do
  grep -B1 -E "AppendRequest\{.*\} to $VICTIM failed" "$LOGDIR/node$i-start.log" \
    | grep -oE 'partitionId=[0-9]+|prevLogIndex=[0-9]+, prevLogTerm=[0-9]+, entries=[0-9]+, commitIndex=[0-9]+' \
    | paste - - | sort -u -t$'\t' -k1,1 | sed "s/^/   leader node $i: /"
done
for i in 0 1 2; do kill -9 "${PID[$i]}" 2>/dev/null; lfs_umount_i $i; done
echo DONE
