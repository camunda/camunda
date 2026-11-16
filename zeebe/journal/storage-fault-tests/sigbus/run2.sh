#!/usr/bin/env bash
set -u
CP="/usr/local/camunda/lib/*"
JOPTS="--add-opens=java.base/java.io=ALL-UNNAMED --add-opens=java.base/jdk.internal.misc=ALL-UNNAMED --enable-native-access=ALL-UNNAMED -Dlog4j2.level=WARN"
mkdir -p /tmp/sb && javac -nowarn -cp "$CP" -d /tmp/sb /work/sigbus/SigbusTest.java || exit 1
run() {
  local label=$1 extra=$2 mode=$3
  echo "=================== $label, end: $mode"
  umount /mnt/small 2>/dev/null; mkdir -p /mnt/small; mount -t tmpfs -o size=16m tmpfs /mnt/small
  rm -rf /tmp/warm-*
  java $JOPTS $extra -cp "/tmp/sb:$CP" SigbusTest write /mnt/small/j "$mode" > /tmp/w.out 2>&1; echo "writer exit code: $?"
  grep -E '^WARMUP|^STATE before|^VOLUME' /tmp/w.out
  grep -E '^ACK' /tmp/w.out | tail -1
  grep -E '^THROWN|^STATE after|^CLOSE|^CLOSED|^HALT' /tmp/w.out | sed -E 's/at append of [0-9]+/at append of N/' | cut -c1-300 | uniq -c
  grep -E '^THROWN' /tmp/w.out | head -1 | cut -c1-200
  ls /tmp/hs_err* 2>/dev/null && grep -m3 -E 'SIGBUS|Problematic frame' /tmp/hs_err*
  rm -f /mnt/small/ballast
  java $JOPTS -cp "/tmp/sb:$CP" SigbusTest verify /mnt/small/j 2>&1 | grep -E 'REOPEN'
}
run "warmed up (300k appends)" "-Dwarmup=300000" close
run "warmed up (300k appends)" "-Dwarmup=300000" halt
run "-Xcomp" "-Xcomp -XX:-TieredCompilation" close
