#!/usr/bin/env bash
# Exact-syscall crash sweep of the 8.8.41 journal on LazyFS.
# usage: run-sweep.sh <faults-file> [steps "a b c"]   (faults-file: one "name|lazyfs::crash::..." per line)
set -uo pipefail
source /work/lazyfs-lib.sh; set +e
FAULTS=$1
CP="${CAMUNDA_LIB:-/usr/local/camunda/lib}/*"
mkdir -p /tmp/h && javac -nowarn -cp "$CP" -d /tmp/h /work/harness/JournalCrash.java /work/harness/CrashSweep.java || exit 1
CP="/tmp/h:$CP"
JOPTS="-Dsweep.crossTruncate=${CROSS:-true} --add-opens=java.base/java.io=ALL-UNNAMED --add-opens=java.base/jdk.internal.misc=ALL-UNNAMED --add-exports=java.base/jdk.internal.misc=ALL-UNNAMED --enable-native-access=ALL-UNNAMED -Dlog4j2.level=WARN"
NSTEPS=$(java $JOPTS -cp "$CP" CrashSweep steps | wc -l)
STEPS=${2:-$(seq 0 $((NSTEPS-1)) | tr '\n' ' ')}
lazy_alive() { pgrep -f "build/lazyfs /mnt/lazy" >/dev/null; }
pass=0; fail=0
while IFS='|' read -r fname fspec; do
  [ -z "$fname" ] && continue
  meta=lazy; [[ "$fname" == *meta* ]] && meta=forced   # a torn .meta write needs an older value on disk
  for k in $STEPS; do
    rm -rf "$ROOT"; lfs_mount
    java $JOPTS -Dsweep.meta=$meta -XX:ErrorFile=/tmp/hs_err_%p.log -cp "$CP" CrashSweep run "$MNT/j" "$k" "$fspec" > /tmp/run.out 2>/tmp/run.err &
    pid=$!
    how=timeout
    for _ in $(seq 1 300); do
      if ! lazy_alive; then how=FIRED; break; fi
      if grep -q '^END' /tmp/run.out; then how=end; break; fi
      sleep 0.1
    done
    kill -9 "$pid" 2>/dev/null; wait "$pid" 2>/dev/null
    if [ $how = FIRED ]; then lfs_umount; else lfs_power_loss "$pid" >/dev/null; fi
    promise=$(awk '/^ACK /{d=($2>d?$2:d)} /^TRUNC /{if(d>$2)d=$2} /^RESET /{d=0} END{print d+0}' /tmp/run.out)
    at=$(grep '^STEP' /tmp/run.out | tail -1 | cut -d' ' -f2-)
    pre=$(cd "$ROOT/j/data" 2>/dev/null && for f in *; do printf "%s:%s " "$f" "$(stat -c %s "$f")"; done)
    lfs_mount
    res=$(java $JOPTS ${VERIFY_OPTS:-} -cp "$CP" JournalCrash verify "$MNT/j" lazy > /tmp/verify.out 2>/tmp/verify.err; grep -E "^(OPEN_|READ_FAIL|INDEX_GAP)" /tmp/verify.out | tr '\n' ' ')
    files=$(ls "$ROOT/j/data" 2>/dev/null | tr '\n' ',')
    lfs_umount
    last=$(sed -nE 's/.*OPEN_OK first=-?[0-9]+ last=(-?[0-9]+).*/\1/p' <<<"$res")
    bad=$(sed -nE 's/.*badContent=([0-9]+).*/\1/p' <<<"$res")
    verdict=PASS
    if [[ "$res" != *OPEN_OK* || "$res" == *READ_FAIL* || "$res" == *INDEX_GAP* || "${bad:-1}" != 0 || ${last:--1} -lt $promise ]]; then verdict=FAIL; fi
    if [ $verdict = FAIL ] && [[ "$at" == *reset-snapshot* && "$res" == *OPEN_OK* && "$res" != *READ_FAIL* && "$res" != *INDEX_GAP* && "${bad:-1}" == 0 ]]; then verdict=EXPECTED; fi
    [ $verdict = PASS ] && pass=$((pass+1)) || fail=$((fail+1))
    armed=$(grep -m1 '^ARMED' /tmp/run.out | grep -oE 'file=[^:]+' | sed 's#.*/##')
    echo "$verdict fault=$fname arm=$k file=$armed crash=$how in=[$at] promise=$promise :: $res files=$files pre=[$pre]"
    if [ "${KEEP:-}" = 1 ]; then mkdir -p /work/sweep-keep/${CROSS:-true}-$fname-$k; cp /tmp/run.out /tmp/verify.out /tmp/verify.err /work/sweep-keep/${CROSS:-true}-$fname-$k/; fi
  done
done < "$FAULTS"
echo "SUMMARY pass=$pass fail=$fail"
