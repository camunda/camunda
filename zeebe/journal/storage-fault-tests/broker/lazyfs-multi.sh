#!/usr/bin/env bash
# LazyFS helpers for several independent mounts (one per broker). Instance i uses
# /mnt/lazy<i>, /var/lazy-root<i>, /tmp/faults<i>.fifo, /tmp/lazyfs<i>.log.

mnt()  { echo "/mnt/lazy$1"; }
root() { echo "/var/lazy-root$1"; }
fifo() { echo "/tmp/faults$1.fifo"; }
llog() { echo "/tmp/lazyfs$1.log"; }

lfs_mount_i() {
  local i=$1 cfg="/tmp/lazyfs$1.toml"
  mkdir -p "$(mnt "$i")" "$(root "$i")"
  cat > "$cfg" <<EOF
[faults]
fifo_path="$(fifo "$i")"
[cache]
apply_eviction=true
[cache.simple]
custom_size="700mb"
blocks_per_page=1
[filesystem]
log_all_operations=false
logfile="$(llog "$i")"
EOF
  (export LAZYFS_QUOTA_FILE="/tmp/quota-full$i"; cd /opt/lazyfs/lazyfs && ./scripts/mount-lazyfs.sh -c "$cfg" -m "$(mnt "$i")" -r "$(root "$i")" >/dev/null 2>&1)
  for _ in $(seq 1 100); do
    if mountpoint -q "$(mnt "$i")" && [ -p "$(fifo "$i")" ]; then return 0; fi
    sleep 0.1
  done
  echo "LazyFS $i did not mount" >&2; return 1
}

lfs_umount_i() {
  local i=$1
  fusermount3 -uz "$(mnt "$i")" 2>/dev/null || umount -l "$(mnt "$i")" 2>/dev/null
  pkill -9 -f "lazyfs.*$(mnt "$i")( |$)" 2>/dev/null
  for _ in $(seq 1 50); do mountpoint -q "$(mnt "$i")" || return 0; sleep 0.1; done
}

# Number of files with unsynced data in LazyFS's cache, from its report.
lfs_unsynced_i() {
  local i=$1 before
  before=$(wc -l < "$(llog "$i")" 2>/dev/null || echo 0)
  echo "lazyfs::unsynced-data-report" > "$(fifo "$i")"; sleep 0.5
  tail -n +"$((before + 1))" "$(llog "$i")" | grep -ciE "inode|file" || true
}

# Power loss of node i: kill its process, push dirty pages to LazyFS without fsync, drop all
# unsynced data, unmount. Prints how many cached files had unsynced data.
lfs_power_loss_i() {
  local i=$1 pid=$2
  kill -9 "$pid" 2>/dev/null; wait "$pid" 2>/dev/null
  sync
  local unsynced; unsynced=$(lfs_unsynced_i "$i")
  echo "lazyfs::clear-cache" > "$(fifo "$i")"; sleep 0.5
  lfs_umount_i "$i"
  echo "$unsynced"
}
