#!/usr/bin/env bash
# 8.8.41 journal on an NFSv3 mount with an async export; the server writes back every 5s (no fsync);
# a server crash loses whatever was not written back yet. usage: run-journal-nfs.sh <scenario> <n> <seed0>
source /work/nfs/nfs-lib.sh
SCENARIO=$1; N=$2; SEED0=${3:-1}
CP="/usr/local/camunda/lib/*"
JOPTS="--add-opens=java.base/java.io=ALL-UNNAMED --add-opens=java.base/jdk.internal.misc=ALL-UNNAMED --enable-native-access=ALL-UNNAMED -Dlog4j2.level=WARN"
mkdir -p /tmp/h && javac -nowarn -cp "$CP" -d /tmp/h /work/harness/JournalCrash.java || exit 1
pass=0; fail=0
for i in $(seq 0 $((N-1))); do
  seed=$((SEED0+i))
  rm -rf "$ROOT"; nfs_start async >/dev/null 2>&1 || { echo "setup failed"; exit 1; }
  ( while true; do sleep 5; echo "lazyfs::cache-checkpoint" > "$FIFO" 2>/dev/null; done ) & WB=$!
  java $JOPTS -cp "/tmp/h:$CP" JournalCrash run "$NFSMNT/j" "$SCENARIO" "$seed" lazy > /tmp/run.out 2>/tmp/run.err &
  pid=$!
  sleep "$(awk -v s=$seed 'BEGIN{srand(s); printf "%.2f", 8+rand()*10}')"
  kill -9 $pid 2>/dev/null; wait $pid 2>/dev/null; kill $WB 2>/dev/null
  promise=$(awk '/^ACK /{d=($2>d?$2:d)} /^TRUNC /{if(d>$2)d=$2} /^RESET /{d=0} END{print d+0}' /tmp/run.out)
  nfs_server_crash
  lfs_mount
  res=$(java $JOPTS -cp "/tmp/h:$CP" JournalCrash verify "$MNT/j" lazy 2>/dev/null | grep -E 'OPEN_|READ_|INDEX_' | tr '\n' ' ')
  lfs_umount
  last=$(sed -nE 's/.*OPEN_OK first=-?[0-9]+ last=(-?[0-9]+).*/\1/p' <<<"$res")
  verdict=PASS
  if [[ "$res" != *OPEN_OK* ]]; then verdict="FAIL(open)"; elif [ "${last:--1}" -lt "$promise" ]; then verdict="FAIL(silent loss: $((promise - last)) acknowledged entries missing)"; fi
  [ "$verdict" = PASS ] && pass=$((pass+1)) || fail=$((fail+1))
  echo "$verdict seed=$seed promise=$promise :: $(cut -c1-230 <<<"$res")"
done
echo "SUMMARY nfs-async scenario=$SCENARIO pass=$pass fail=$fail"
