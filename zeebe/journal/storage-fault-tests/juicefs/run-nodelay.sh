#!/usr/bin/env bash
set +e
META=sqlite3:///var/jfs-meta/meta.db; MNT=/mnt/jfs; CACHE=/var/jfsCache
CP="/usr/local/camunda/lib/*"
JOPTS="--add-opens=java.base/java.io=ALL-UNNAMED --add-opens=java.base/jdk.internal.misc=ALL-UNNAMED --enable-native-access=ALL-UNNAMED -Dlog4j2.level=WARN"
mkdir -p /tmp/jt && javac -nowarn -cp "$CP" -d /tmp/jt /work/juicefs/JfsTest.java || exit 1
mount_jfs() { juicefs mount "$META" "$MNT" -d --writeback --upload-delay ${UPLOAD_DELAY:-0s} --cache-dir "$CACHE" --log /tmp/jfs.log >/dev/null 2>&1; for _ in $(seq 1 50); do mountpoint -q "$MNT" && return 0; sleep 0.2; done; echo "mount failed"; tail -5 /tmp/jfs.log; }
for ending in nodeloss; do for n in ${WHILE_FULL:-1 10}; do
  echo "=================== $n entries while quota full, ending: $ending"
  fusermount3 -uz $MNT 2>/dev/null; pkill -9 -f 'juicefs mount' 2>/dev/null; sleep 0.5
  rm -rf /var/jfs-meta /var/jfs-objects $CACHE; mkdir -p /var/jfs-meta $MNT
  juicefs format "$META" zeebe --storage file --bucket /var/jfs-objects/ >/dev/null 2>&1
  mount_jfs; mkdir -p $MNT/zeebe
  juicefs quota set "$META" --path /zeebe --capacity 1 >/dev/null 2>&1
  juicefs quota get "$META" --path /zeebe 2>/dev/null | tail -2 | head -1
  java $JOPTS -cp "/tmp/jt:$CP" JfsTest run $MNT/zeebe/j "$n" > /tmp/r.out 2>&1
  grep -E '^ACK' /tmp/r.out | tail -1
  grep -vE '^ACK|SLF4J|^\s+at |^\s*\.\.\.' /tmp/r.out | cut -c1-260 | uniq | head -14
  echo "   staged (not yet uploaded) blocks in local cache: $(find $CACHE -path '*rawstaging*' -type f 2>/dev/null | wc -l)"
  if [ "$ending" = nodeloss ]; then
    pkill -9 -f 'juicefs mount'; sleep 0.5; fusermount3 -uz $MNT 2>/dev/null
    rm -rf "$CACHE"            # the node's local cache is gone (pod moved to another node)
  else
    juicefs umount $MNT >/dev/null 2>&1 || fusermount3 -uz $MNT
  fi
  mount_jfs; rm -f $MNT/zeebe/ballast
  java $JOPTS -cp "/tmp/jt:$CP" JfsTest verify $MNT/zeebe/j 2>&1 > /tmp/v.out 2>&1; grep -vE "SLF4J|DEBUG|^\s+at " /tmp/v.out | head -12 | cut -c1-250
  juicefs umount $MNT >/dev/null 2>&1
done; done
