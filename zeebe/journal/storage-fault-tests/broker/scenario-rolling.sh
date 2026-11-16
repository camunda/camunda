#!/usr/bin/env bash
# Rolling single-node power loss under load. usage: scenario-rolling.sh <cycles> <minLoadSecs> <maxLoadSecs> [all]
# With "all" as 4th argument, every cycle cuts power to all three nodes at once (shared storage event).
LOGDIR=/work/broker/logs-${4:-rolling}
source /work/broker/cluster.sh
CYCLES=${1:-6}; LMIN=${2:-30}; LMAX=${3:-60}; MODE=${4:-rolling}

for i in 0 1 2; do rm -rf "$(root $i)"; lfs_mount_i $i || exit 1; done
for i in 0 1 2; do start_node $i "node$i-start0.log"; done
for i in 0 1 2; do await_node $i "node$i-start0.log" 240; echo "node $i up rc=$? $(node_partitions $i)"; done

for c in $(seq 1 "$CYCLES"); do
  if [ "$MODE" = all ]; then victims="0 1 2"; via=0; else v=$(( (c - 1) % 3 )); victims=$v; via=$(( (v + 1) % 3 )); fi
  start_load "$via" 4 1000 "load-$c.log"
  secs=$((LMIN + RANDOM % (LMAX - LMIN + 1)))
  sleep "$secs"
  roles=""; for i in 0 1 2; do roles+=" n$i[$(node_partitions $i)]"; done
  kill -9 $LOAD 2>/dev/null
  echo "cycle $c: load ${secs}s via node $via: $(tail -1 "$LOGDIR/load-$c.log") | before:$roles"
  for v in $victims; do
    unsynced=$(lfs_power_loss_i "$v" "${PID[$v]}")
    echo "   power loss node $v (files with unsynced data dropped: $unsynced)"
  done
  for v in $victims; do lfs_mount_i "$v" && start_node "$v" "node$v-cycle$c.log"; done
  ok=1
  for v in $victims; do
    await_node "$v" "node$v-cycle$c.log" 240; rc=$?
    echo "   node $v restart rc=$rc $(node_partitions $v)"
    if [ $rc -ne 0 ]; then ok=0; corruption_lines "node$v-cycle$c.log" | sed 's/^/      /'; fi
  done
  [ $ok = 1 ] || { echo "STOP after cycle $c"; break; }
done
for i in 0 1 2; do kill -9 "${PID[$i]}" 2>/dev/null; lfs_umount_i $i; done
echo DONE
