#!/usr/bin/env bash
# Three 8.8.41 brokers in one container, each on its own LazyFS mount.
source /work/broker/lazyfs-multi.sh
set +e

LOGDIR=${LOGDIR:-/work/broker/logs-cluster}; mkdir -p "$LOGDIR"
CP="/usr/local/camunda/lib/*"
JOPTS="--add-opens=java.base/java.io=ALL-UNNAMED --add-opens=java.base/jdk.internal.misc=ALL-UNNAMED --enable-native-access=ALL-UNNAMED"
declare -A PID

port() { echo $(( $2 + 10 * $1 )); }   # port <node> <base>

start_node() {
  local i=$1 log="$LOGDIR/$2"
  local dvar="DATA_DIR_$i"; local datadir="${!dvar:-$(mnt "$i")/data}"
  (
    export ZEEBE_BROKER_CLUSTER_NODEID=$i ZEEBE_BROKER_CLUSTER_CLUSTERSIZE=3 \
      ZEEBE_BROKER_CLUSTER_PARTITIONSCOUNT=${PARTITIONS:-3} ZEEBE_BROKER_CLUSTER_REPLICATIONFACTOR=3 \
      ZEEBE_BROKER_CLUSTER_INITIALCONTACTPOINTS="localhost:26502,localhost:26512,localhost:26522" \
      ZEEBE_BROKER_NETWORK_HOST=localhost \
      ZEEBE_BROKER_NETWORK_COMMANDAPI_PORT=$(port $i 26501) \
      ZEEBE_BROKER_NETWORK_INTERNALAPI_PORT=$(port $i 26502) \
      ZEEBE_BROKER_GATEWAY_ENABLE=true ZEEBE_BROKER_GATEWAY_NETWORK_PORT=$(port $i 26500) \
      SERVER_PORT=$(port $i 8080) MANAGEMENT_SERVER_PORT=$(port $i 9600) \
      ZEEBE_BROKER_DATA_DIRECTORY="$datadir" ZEEBE_BROKER_DATA_LOGSEGMENTSIZE=${SEGMENT_SIZE:-4MB} \
      ZEEBE_BROKER_NETWORK_MAXMESSAGESIZE=1MB ZEEBE_BROKER_DATA_SNAPSHOTPERIOD=${SNAPSHOT_PERIOD:-1m} \
      ZEEBE_BROKER_EXPERIMENTAL_ROCKSDB_MEMORYALLOCATIONSTRATEGY=PARTITION \
      ZEEBE_BROKER_EXPERIMENTAL_ROCKSDB_MEMORYLIMIT=64MB \
      CAMUNDA_DATA_SECONDARYSTORAGE_TYPE=none \
      CAMUNDA_SECURITY_AUTHENTICATION_UNPROTECTEDAPI=true \
      CAMUNDA_SECURITY_AUTHORIZATIONS_ENABLED=false \
      JAVA_OPTS="-Xmx512m -XX:+ExitOnOutOfMemoryError"
    if [ -n "${PROC_MB:-}" ]; then
      export ZEEBE_BROKER_DATA_DISK_FREESPACE_PROCESSING="${PROC_MB}MB" ZEEBE_BROKER_DATA_DISK_FREESPACE_REPLICATION="${REPL_MB}MB" \
        ZEEBE_BROKER_DATA_DISK_MONITORINGINTERVAL=1s
    fi
    exec /usr/local/camunda/bin/broker
  ) > "$log" 2>&1 &
  PID[$i]=$!
}

# Partition roles/health of node i as "p1:LEADER/HEALTHY p2:..." (empty if not reachable).
node_partitions() {
  curl -s --max-time 3 "localhost:$(port $1 9600)/actuator/partitions" \
    | jq -r 'to_entries|map("p\(.key):\(.value.role // "-")/\(.value.health.status // "-")")|join(" ")' 2>/dev/null
}

# Waits until node i reports 3 healthy partitions; returns 2 on corruption, 1 on timeout.
await_node() {
  local i=$1 log="$LOGDIR/$2" timeout=${3:-180}
  for _ in $(seq 1 "$timeout"); do
    if grep -qE "CorruptedJournalException|UnknownVersionException|Failed to install partition" "$log"; then return 2; fi
    local p; p=$(node_partitions "$i")
    if [ "$(grep -o HEALTHY <<<"$p" | wc -l)" -ge "${PARTITIONS:-3}" ]; then return 0; fi
    kill -0 "${PID[$i]}" 2>/dev/null || return 3
    sleep 1
  done
  return 1
}

corruption_lines() { grep -hE "CorruptedJournalException|Expected to find records|recognize version|not aligned|journal has no first|Failed to install partition" "$LOGDIR/$1" | cut -c1-220 | sort | uniq -c | head -5; }

start_load() {
  mkdir -p /tmp/lg && [ -f /tmp/lg/LoadGen.class ] || javac -nowarn -cp "$CP" -d /tmp/lg /work/broker/LoadGen.java
  java $JOPTS -Dttl="${MSG_TTL:-600}" -cp "/tmp/lg:$CP" LoadGen "localhost:$(port "$1" 26500)" "${2:-6}" "${3:-2000}" > "$LOGDIR/$4" 2>&1 &
  LOAD=$!
}
