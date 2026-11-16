#!/usr/bin/env bash
# Step 3b: one 8.8.41 broker (1 partition, RF 1) on LazyFS. Per fault: fresh data, load, arm one
# LazyFS crash fault (or a quota-model regex), take a snapshot (-> compaction), crash, restart, check.
# usage: broker-sweep.sh <faults-file>   lines: "name|lazyfs::crash::..." or "name|QUOTA:<regex>" or "name|"
source /work/broker/lazyfs-multi.sh; set +e
FAULTS=$1; I=0
OUT=/work/broker/logs-bsweep; mkdir -p "$OUT"
CP="/usr/local/camunda/lib/*"
JOPTS="--add-opens=java.base/java.io=ALL-UNNAMED --add-opens=java.base/jdk.internal.misc=ALL-UNNAMED --enable-native-access=ALL-UNNAMED"
mkdir -p /tmp/lg && javac -nowarn -cp "$CP" -d /tmp/lg /work/broker/LoadGen.java || exit 1
DATA="$(mnt $I)/data"; PDIR="$(root $I)/data/raft-partition/partitions/1"

export ZEEBE_BROKER_CLUSTER_NODEID=0 ZEEBE_BROKER_CLUSTER_CLUSTERSIZE=1 \
  ZEEBE_BROKER_CLUSTER_PARTITIONSCOUNT=1 ZEEBE_BROKER_CLUSTER_REPLICATIONFACTOR=1 \
  ZEEBE_BROKER_DATA_DIRECTORY="$DATA" ZEEBE_BROKER_DATA_LOGSEGMENTSIZE=2MB \
  ZEEBE_BROKER_NETWORK_MAXMESSAGESIZE=1MB ZEEBE_BROKER_DATA_SNAPSHOTPERIOD=1h \
  ZEEBE_BROKER_GATEWAY_ENABLE=true CAMUNDA_DATA_SECONDARYSTORAGE_TYPE=none \
  CAMUNDA_SECURITY_AUTHENTICATION_UNPROTECTEDAPI=true CAMUNDA_SECURITY_AUTHORIZATIONS_ENABLED=false \
  JAVA_OPTS="-Xmx768m -XX:+ExitOnOutOfMemoryError"

lazy_alive() { pgrep -f "build/lazyfs $(mnt $I)( |$)" >/dev/null; }
start_broker() { # <log> -> rc 0 ready, 2 corruption/abort, 3 died, 1 timeout
  /usr/local/camunda/bin/broker > "$1" 2>&1 &
  BROKER=$!
  for _ in $(seq 1 150); do
    if [ "$(curl -s --max-time 2 localhost:9600/actuator/partitions | jq -r '."1".health.status' 2>/dev/null)" = HEALTHY ]; then return 0; fi
    grep -qE "CorruptedJournalException|Failed to install partition|Aborting startup|Expected to find a snapshot|Failed to recover" "$1" && return 2
    kill -0 $BROKER 2>/dev/null || return 3
    sleep 1
  done
  return 1
}
state() { # snapshots and segments on the backing store (what survived)
  echo "snap=[$(ls "$PDIR/snapshots" 2>/dev/null | grep -v '^$' | tr '\n' ' ')] segs=[$(ls "$PDIR" 2>/dev/null | grep -E '\.log' | sed -E 's/raft-partition-partition-1-//' | tr '\n' ' ')]"
}

while IFS='|' read -r name spec; do
  [ -z "$name" ] && continue
  L="$OUT/$name"; rm -rf "$L"; mkdir -p "$L"
  rm -f /tmp/quota-full$I; rm -rf "$(root $I)"; lfs_mount_i $I || exit 1
  start_broker "$L/start.log"; rc=$?
  [ $rc -ne 0 ] && { echo "SKIP $name: first start rc=$rc"; kill -9 $BROKER; lfs_umount_i $I; continue; }
  java $JOPTS -cp "/tmp/lg:$CP" LoadGen localhost:26500 6 2000 > "$L/load.log" 2>&1 &
  LOAD=$!
  sleep 20
  # a first snapshot + compaction, so the next one replaces an existing snapshot
  curl -s -X POST localhost:9600/actuator/partitions/takeSnapshot >/dev/null; sleep 8
  case "$spec" in
    QUOTA:*) echo "${spec#QUOTA:}" > /tmp/quota-full$I ;;   # matching fsyncs acknowledged, not persisted
    lazyfs::*) echo "$spec" > "$(fifo $I)"; sleep 0.3 ;;
  esac
  sleep 10   # more log, so the second snapshot compacts segments
  curl -s -X POST localhost:9600/actuator/partitions/takeSnapshot >/dev/null
  how=timeout
  for _ in $(seq 1 200); do lazy_alive || { how=FIRED; break; }; sleep 0.1; done
  kill -9 $LOAD 2>/dev/null
  acked=$(tail -1 "$L/load.log")
  snaps_logged=$(grep -c "Committed new snapshot\|Persisted snapshot\|Taking snapshot" "$L/start.log")
  if [ $how = FIRED ]; then kill -9 $BROKER; wait $BROKER 2>/dev/null; lfs_umount_i $I
  else lfs_power_loss_i $I $BROKER >/dev/null; fi
  before=$(state)
  rm -f /tmp/quota-full$I
  lfs_mount_i $I
  start_broker "$L/restart.log"; rc=$?
  health=$(curl -s --max-time 2 localhost:9600/actuator/partitions | jq -c '."1" | {role, h: .health.status}' 2>/dev/null)
  errs=$(grep -hE "CorruptedJournalException|Expected to find|recognize version|not aligned|Failed to recover|snapshot.*corrupt|checksum|Failed to install partition" "$L/restart.log" | grep -vE "^\s+at " | sed -E 's/^\s+//' | cut -c1-170 | sort | uniq -c | head -3 | tr '\n' ';')
  verdict=PASS; [ $rc -ne 0 ] && verdict=FAIL
  echo "$verdict $name crash=$how restart_rc=$rc $health | before: $before | load: $acked | $errs"
  kill -9 $BROKER 2>/dev/null; wait $BROKER 2>/dev/null; lfs_umount_i $I
done < "$FAULTS"
echo DONE
