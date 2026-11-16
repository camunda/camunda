#!/usr/bin/env bash
set -euo pipefail
source /work/lazyfs-lib.sh

mkdir -p /tmp/probe-classes
javac -d /tmp/probe-classes /work/probe/MmapProbe.java

for variant in powerloss processcrash; do
  echo "=== $variant"
  rm -rf "$ROOT"; lfs_mount
  java -cp /tmp/probe-classes MmapProbe write "$MNT/probe.bin" > /tmp/probe.out 2>&1 &
  pid=$!
  for _ in $(seq 1 100); do grep -q written /tmp/probe.out && break; sleep 0.1; done
  if [ "$variant" = powerloss ]; then
    lfs_power_loss "$pid"
  else
    # process crash only: page cache survives, LazyFS keeps its cache until it is persisted
    kill -9 "$pid"; wait "$pid" 2>/dev/null || true
    sync
    echo "lazyfs::cache-checkpoint" > "$FIFO"; sleep 0.5
    lfs_umount
  fi
  lfs_mount
  java -cp /tmp/probe-classes MmapProbe read "$MNT/probe.bin"
  lfs_umount
done
