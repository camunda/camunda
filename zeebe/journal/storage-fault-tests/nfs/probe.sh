#!/usr/bin/env bash
source /work/nfs/nfs-lib.sh
mkdir -p /tmp/probe-classes && javac -d /tmp/probe-classes /work/probe/MmapProbe.java
for mode in sync async; do
  echo "=== export: $mode"
  rm -rf "$ROOT"; nfs_start $mode || exit 1
  mount | grep " $NFSMNT " | cut -c1-140
  java -cp /tmp/probe-classes MmapProbe write "$NFSMNT/probe.bin" > /tmp/probe.out 2>&1 &
  pid=$!; for _ in $(seq 1 100); do grep -q written /tmp/probe.out && break; sleep 0.1; done
  cat /tmp/probe.out | head -3
  kill -9 $pid; wait $pid 2>/dev/null
  nfs_server_crash
  lfs_mount
  java -cp /tmp/probe-classes MmapProbe read "$MNT/probe.bin"
  lfs_umount
done
