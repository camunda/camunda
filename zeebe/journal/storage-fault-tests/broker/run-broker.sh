#!/usr/bin/env bash
# Single 8.8.41 broker with its data on LazyFS; repeated power-loss cycles.
# usage: run-broker.sh <cycles> <loadSecondsMin> <loadSecondsMax>
source /work/lazyfs-lib.sh
set +e
CYCLES=${1:-5}; LMIN=${2:-20}; LMAX=${3:-60}
DATA="$MNT/broker-0"
LOGDIR=/work/broker/logs-single; mkdir -p "$LOGDIR"
CP="/usr/local/camunda/lib/*"
JOPTS="--add-opens=java.base/java.io=ALL-UNNAMED --add-opens=java.base/jdk.internal.misc=ALL-UNNAMED --enable-native-access=ALL-UNNAMED"
mkdir -p /tmp/lg && javac -nowarn -cp "$CP" -d /tmp/lg /work/broker/LoadGen.java || exit 1

export ZEEBE_BROKER_CLUSTER_NODEID=0 ZEEBE_BROKER_CLUSTER_CLUSTERSIZE=1 \
  ZEEBE_BROKER_CLUSTER_PARTITIONSCOUNT=3 ZEEBE_BROKER_CLUSTER_REPLICATIONFACTOR=1 \
  ZEEBE_BROKER_DATA_DIRECTORY="$DATA" ZEEBE_BROKER_DATA_LOGSEGMENTSIZE=4MB \
  ZEEBE_BROKER_NETWORK_MAXMESSAGESIZE=1MB ZEEBE_BROKER_DATA_SNAPSHOTPERIOD=1m \
  ZEEBE_BROKER_GATEWAY_ENABLE=true \
  CAMUNDA_DATA_SECONDARYSTORAGE_TYPE=none \
  CAMUNDA_SECURITY_AUTHENTICATION_UNPROTECTEDAPI=true \
  CAMUNDA_SECURITY_AUTHORIZATIONS_ENABLED=false \
  JAVA_OPTS="-Xmx768m -XX:+ExitOnOutOfMemoryError"

start_broker() {
  /usr/local/camunda/bin/broker > "$1" 2>&1 &
  BROKER=$!
  for _ in $(seq 1 240); do
    if curl -sf localhost:9600/actuator/health/readiness >/dev/null 2>&1; then return 0; fi
    if grep -qE "CorruptedJournalException|Failed to install partition|Aborting startup" "$1"; then return 2; fi
    kill -0 $BROKER 2>/dev/null || return 3
    sleep 1
  done
  return 1
}

partitions() { curl -s localhost:9600/actuator/partitions | jq -c 'to_entries|map({p:.key,role:.value.role,health:.value.health})' 2>/dev/null; }

rm -rf "$ROOT"; lfs_mount
for c in $(seq 1 "$CYCLES"); do
  log="$LOGDIR/broker-cycle-$c.log"
  start_broker "$log"; rc=$?
  corrupt=$(grep -cE "CorruptedJournalException" "$log")
  echo "cycle $c: start rc=$rc corruptedJournal=$corrupt partitions=$(partitions)"
  if [ $rc -ne 0 ] || [ "$corrupt" != 0 ]; then
    grep -m3 -E "CorruptedJournalException|Expected to find|recognize version|not aligned" "$log" | cut -c1-240 | sed 's/^/   /'
    echo "STOP: broker did not come back cleanly in cycle $c"; kill -9 $BROKER 2>/dev/null; break
  fi
  java $JOPTS -cp "/tmp/lg:$CP" LoadGen localhost:26500 8 2000 > "$LOGDIR/load-$c.log" 2>&1 &
  LOAD=$!
  secs=$((LMIN + RANDOM % (LMAX - LMIN + 1)))
  sleep "$secs"
  echo "   load for ${secs}s: $(tail -1 "$LOGDIR/load-$c.log")"
  kill -9 $LOAD 2>/dev/null
  lfs_power_loss "$BROKER"
  lfs_mount
done
lfs_umount
echo "DONE"
