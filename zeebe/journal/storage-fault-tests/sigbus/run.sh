#!/usr/bin/env bash
set -u
CP="/usr/local/camunda/lib/*"
JOPTS="--add-opens=java.base/java.io=ALL-UNNAMED --add-opens=java.base/jdk.internal.misc=ALL-UNNAMED --enable-native-access=ALL-UNNAMED -Dlog4j2.level=WARN"
mkdir -p /tmp/sb && javac -nowarn -cp "$CP" -d /tmp/sb /work/sigbus/SigbusTest.java || exit 1
for mode in close halt; do
  echo "=================== end of old process: $mode"
  umount /mnt/small 2>/dev/null; mkdir -p /mnt/small
  mount -t tmpfs -o size=16m tmpfs /mnt/small
  java $JOPTS -cp "/tmp/sb:$CP" SigbusTest write /mnt/small/j "$mode" > /tmp/w.out 2>&1; echo "writer exit code: $?"
  grep -E '^ACK' /tmp/w.out | tail -2
  grep -vE '^ACK|SLF4J|^\s+at |DEBUG' /tmp/w.out | cut -c1-330 | head -25
  ls /tmp/hs_err* /hs_err* 2>/dev/null && grep -m3 -E 'SIGBUS|Problematic frame|^# ' /tmp/hs_err* /hs_err* 2>/dev/null
  rm -f /mnt/small/ballast
  java $JOPTS -cp "/tmp/sb:$CP" SigbusTest verify /mnt/small/j 2>&1 | grep -E 'REOPEN'
done
