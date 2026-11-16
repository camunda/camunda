#!/usr/bin/env bash
# Helpers to mount LazyFS and simulate a power loss inside the container.
set -euo pipefail

MNT=/mnt/lazy
ROOT=/var/lazy-root
FIFO=/tmp/faults.fifo
CFG=/tmp/lazyfs.toml
LOG=/tmp/lazyfs.log

lfs_config() {
  cat > "$CFG" <<EOF
[faults]
fifo_path="$FIFO"
[cache]
apply_eviction=false
[cache.simple]
custom_size="2gb"
blocks_per_page=1
[filesystem]
log_all_operations=false
logfile="$LOG"
EOF
}

lfs_mount() {
  mkdir -p "$MNT" "$ROOT"
  lfs_config
  (cd /opt/lazyfs/lazyfs && ./scripts/mount-lazyfs.sh -c "$CFG" -m "$MNT" -r "$ROOT" >/dev/null 2>&1)
  for _ in $(seq 1 100); do
    if mountpoint -q "$MNT" && [ -p "$FIFO" ]; then return 0; fi
    sleep 0.1
  done
  echo "LazyFS did not mount" >&2
  cat "$LOG" >&2 || true
  return 1
}

lfs_umount() {
  fusermount3 -uz "$MNT" 2>/dev/null || fusermount -uz "$MNT" 2>/dev/null || umount -l "$MNT" || true
  pkill -9 -f /opt/lazyfs/lazyfs/build/lazyfs 2>/dev/null || true
  for _ in $(seq 1 50); do mountpoint -q "$MNT" || return 0; sleep 0.1; done
}

# Power loss: the process dies, the kernel's dirty pages reach LazyFS as plain writes (no fsync),
# then LazyFS drops everything that was not fsynced.
lfs_power_loss() {
  local pid="$1"
  kill -9 "$pid" 2>/dev/null || true
  wait "$pid" 2>/dev/null || true
  sync
  echo "lazyfs::clear-cache" > "$FIFO"
  sleep 0.5
  lfs_umount
}
