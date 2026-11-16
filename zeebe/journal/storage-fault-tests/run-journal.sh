#!/usr/bin/env bash
# usage: run-journal.sh <scenario> <metaMode> <iterations> <firstSeed>
set -uo pipefail
source /work/lazyfs-lib.sh; set +e
SCENARIO=$1; META=$2; N=$3; SEED0=${4:-1}
CP="${CAMUNDA_LIB:-/usr/local/camunda/lib}/*"
mkdir -p /tmp/h && javac -nowarn -cp "$CP" -d /tmp/h /work/harness/JournalCrash.java || exit 1
CP="/tmp/h:$CP"
JOPTS="--add-opens=java.base/java.io=ALL-UNNAMED --add-opens=java.base/jdk.internal.misc=ALL-UNNAMED --add-exports=java.base/jdk.internal.misc=ALL-UNNAMED --enable-native-access=ALL-UNNAMED -Dlog4j2.level=WARN"
pass=0; fail=0
for i in $(seq 0 $((N-1))); do
  seed=$((SEED0+i))
  rm -rf "$ROOT"; lfs_mount
  java $JOPTS -cp "$CP" JournalCrash run "$MNT/j" "$SCENARIO" "$seed" "$META" > /tmp/run.out 2>/tmp/run.err &
  pid=$!
  if [ "$SCENARIO" = leader-truncate ]; then
    for _ in $(seq 1 300); do grep -q READY /tmp/run.out && break; sleep 0.1; done
  else
    sleep "$(awk -v s=$seed 'BEGIN{srand(s); printf "%.2f", 1.5+rand()*3}')"
  fi
  lfs_power_loss "$pid"
  # durability promise: last ACK, lowered by later truncations, cleared by resets
  promise=$(awk '/^ACK /{d=($2>d?$2:d)} /^TRUNC /{if(d>$2)d=$2} /^RESET /{d=0} END{print d+0}' /tmp/run.out)
  ops=$(grep -cE '^(ACK|TRUNC|RESET) ' /tmp/run.out)
  lfs_mount
  res=$(java $JOPTS -cp "$CP" JournalCrash verify "$MNT/j" "$META" 2>/tmp/verify.err | tr '\n' ' ')
  lfs_umount
  last=$(sed -nE 's/.*OPEN_OK first=-?[0-9]+ last=(-?[0-9]+).*/\1/p' <<<"$res")
  bad=$(sed -nE 's/.*badContent=([0-9]+).*/\1/p' <<<"$res")
  verdict=PASS
  if [[ "$res" != *OPEN_OK* || "$res" == *READ_FAIL* || "$res" == *INDEX_GAP* || "${bad:-1}" != 0 || ${last:--1} -lt $promise ]]; then verdict=FAIL; fi
  [ $verdict = PASS ] && pass=$((pass+1)) || fail=$((fail+1))
  info=$(grep -m1 '^INFO' /tmp/run.out || true)
  echo "$verdict seed=$seed ops=$ops promise=$promise :: $res ${info}"
  [ -s /tmp/run.err ] && grep -m2 -E 'Exception|Error' /tmp/run.err | sed 's/^/   run.err: /'
done
echo "SUMMARY scenario=$SCENARIO meta=$META pass=$pass fail=$fail"
