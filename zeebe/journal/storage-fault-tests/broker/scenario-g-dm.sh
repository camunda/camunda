#!/usr/bin/env bash
# Scenario G with a failing flush: three 8.8.41 brokers, six partitions, RF 3, each broker on its
# own ext4 on a device-mapper device. During a "quota exceeded" window, every existing segment
# file of every broker is mapped to the dm "error" target (writes fail, flushes report EIO); the
# rest of the filesystem keeps working. Then writes work again, load continues, and the brokers
# are restarted one by one (graceful stop + remount = page cache gone).
# usage: scenario-g-dm.sh [windowSeconds]
WINDOW=${1:-10}
export PARTITIONS=6 SEGMENT_SIZE=16MB PROC_MB=200 REPL_MB=100
LOGDIR=/work/broker/logs-g-dm
source /work/broker/cluster.sh
rm -rf "$LOGDIR"; mkdir -p "$LOGDIR"
command -v dmsetup >/dev/null || { apt-get update -qq >/dev/null 2>&1; apt-get install -y -qq dmsetup e2fsprogs >/dev/null 2>&1; }

declare -A LOOPDEV SECT
for i in 0 1 2; do
  dmsetup remove fg$i 2>/dev/null
  for l in $(losetup -j /var/fg$i.img 2>/dev/null | cut -d: -f1); do losetup -d $l; done
  rm -f /var/fg$i.img; truncate -s 3G /var/fg$i.img
  LOOPDEV[$i]=$(losetup -f --show /var/fg$i.img); SECT[$i]=$(blockdev --getsz ${LOOPDEV[$i]})
  echo "0 ${SECT[$i]} linear ${LOOPDEV[$i]} 0" | dmsetup create fg$i && dmsetup mknodes fg$i
  mkfs.ext4 -q -F /dev/mapper/fg$i
  mkdir -p /mnt/fg$i && mount -o errors=continue /dev/mapper/fg$i /mnt/fg$i || exit 1
  export DATA_DIR_$i=/mnt/fg$i/data
done

healthy() { local p; p=$(node_partitions "$1"); echo "$(grep -o '/HEALTHY' <<<"$p" | wc -l)"; }
wait_healthy() { # <node> <log> <seconds>: 0 ok, 2 corruption, 3 died, 1 timeout
  for _ in $(seq 1 "$3"); do
    grep -qE "CorruptedJournalException|Failed to install partition" "$LOGDIR/$2" && return 2
    [ "$(healthy "$1")" -ge $PARTITIONS ] && return 0
    kill -0 "${PID[$1]}" 2>/dev/null || return 3
    sleep 1
  done; return 1
}
# dm table for node i: all extents of its segment files -> error, the rest linear
error_table() {
  local i=$1 bs k p=0 t=""
  bs=$(stat -f -c %S /mnt/fg$i); k=$((bs/512))
  while read -r ps pe; do
    local st=$((ps*k)) en=$(((pe+1)*k))
    [ $st -lt $p ] && continue
    [ $st -gt $p ] && t+="$p $((st-p)) linear ${LOOPDEV[$i]} $p"$'\n'
    t+="$st $((en-st)) error"$'\n'; p=$en
  done < <(find /mnt/fg$i/data -name '*.log' -print0 | xargs -0 -r -n1 filefrag -e -b$bs \
           | sed -nE 's/^ *[0-9]+: +[0-9]+\.\. *[0-9]+: +([0-9]+)\.\. *([0-9]+):.*/\1 \2/p' | sort -n)
  [ $p -lt ${SECT[$i]} ] && t+="$p $((${SECT[$i]}-p)) linear ${LOOPDEV[$i]} $p"
  printf "%s" "$t"
}
set_table() { printf "%s\n" "$2" | dmsetup load fg$1 && dmsetup suspend fg$1 && dmsetup resume fg$1; }
flush_warns() { grep -hcE "cannot guarantee durability|Failed to flush (commit|when append)" "$LOGDIR/$1" 2>/dev/null; }

for i in 0 1 2; do start_node $i "node$i-start.log"; done
for i in 0 1 2; do wait_healthy $i "node$i-start.log" 240; echo "node $i up rc=$? $(node_partitions $i)"; done

start_load 0 6 1000 load.log
sleep 40
echo "after 40s load: $(tail -1 $LOGDIR/load.log)"
date -u +"%H:%M:%S window opens: every existing segment file fails for ${WINDOW}s"
for i in 0 1 2; do
  T=$(error_table $i); echo "   node $i: $(grep -c error <<<"$T") segment extents mapped to error"
  set_table $i "$T"
done
sleep "$WINDOW"
for i in 0 1 2; do set_table $i "0 ${SECT[$i]} linear ${LOOPDEV[$i]} 0"; done
date -u +"%H:%M:%S window closed"
echo "   kernel: $(dmesg | grep -c 'Buffer I/O error on device dm-') buffer I/O errors"
sleep 30
kill -9 $LOAD 2>/dev/null
echo "load stopped: $(tail -1 $LOGDIR/load.log)"
for i in 0 1 2; do
  echo "   node $i: $(node_partitions $i) | flush warnings: $(flush_warns node$i-start.log) | alive: $(kill -0 ${PID[$i]} 2>/dev/null && echo yes || echo NO)"
done
grep -hE "cannot guarantee durability|Failed to flush (commit|when append)" $LOGDIR/node*-start.log | sed -E 's/^\s+//' | cut -c1-200 | sort | uniq -c | sort -rn | head -4
echo "idle 30s"; sleep 30

for v in 1 0 2; do
  echo "RESTART node $v (graceful stop, remount)"
  kill -TERM "${PID[$v]}"
  for _ in $(seq 1 90); do kill -0 "${PID[$v]}" 2>/dev/null || break; sleep 1; done
  kill -0 "${PID[$v]}" 2>/dev/null && { echo "   did not stop in 90s, killing"; kill -9 "${PID[$v]}"; }
  wait "${PID[$v]}" 2>/dev/null
  umount /mnt/fg$v && mount -o errors=continue /dev/mapper/fg$v /mnt/fg$v
  start_node $v "node$v-restart.log"
  wait_healthy $v "node$v-restart.log" 150; rc=$?
  echo "   node $v restart rc=$rc $(node_partitions $v)"
  grep -hE "CorruptedJournalException|Expected to find records|recognize version|not aligned|Failed to install partition" "$LOGDIR/node$v-restart.log" \
    | grep -vE "^\s+at " | sed -E 's/^\s+//' | cut -c1-200 | sort | uniq -c | head -6
done
for i in 0 1 2; do kill -9 "${PID[$i]}" 2>/dev/null; done; sleep 2
for i in 0 1 2; do umount /mnt/fg$i 2>/dev/null; dmsetup remove fg$i 2>/dev/null; losetup -d ${LOOPDEV[$i]} 2>/dev/null; done
echo DONE
