#!/usr/bin/env bash
# Usage: cluster.sh start <scenario> | stop | kill <node> | restart <node> <scenario> | wipe | leader <partition>
source "$(dirname "$0")/env.sh"
start_broker() {
  local i=$1 scenario=$2
  mkdir -p "$WORK/broker-$i"
  echo "$scenario" > "$WORK/scenario"
  ( while IFS= read -r kv; do export "$kv"; done < <(broker_env "$i" "$scenario")
    nohup "$DIST/bin/broker" > "$WORK/broker-$i.log" 2>&1 & echo $! > "$WORK/broker-$i.pid" )
}
case "$1" in
  start)
    for i in $(seq 0 $((BROKERS - 1))); do start_broker "$i" "$2"; done
    echo "waiting for the cluster to be healthy..."
    for _ in $(seq 120); do
      n=$(curl -sf localhost:$REST_BASE/v2/topology | jq '[.brokers[].partitions[] | select(.health=="healthy")] | length' 2>/dev/null)
      [ "${n:-0}" -ge $((PARTITIONS * 3)) ] && { echo "cluster healthy"; exit 0; }
      sleep 2
    done
    echo "cluster did not become healthy, see $WORK/broker-*.log"; exit 1 ;;
  stop)
    for f in "$WORK"/broker-*.pid; do [ -f "$f" ] && kill "$(cat "$f")" 2>/dev/null; done
    sleep 8; echo "cluster stopped" ;;
  kill) kill -9 "$(cat "$WORK/broker-$2.pid")"; echo "broker $2 killed (SIGKILL)" ;;
  restart) start_broker "$2" "$3"; echo "broker $2 started" ;;
  wipe) rm -rf "$WORK"/broker-*/data; echo "broker data wiped (store kept)" ;;
  store-off) chmod -R a-w "$STORE"; echo "store made read-only" ;;
  store-on) chmod -R u+w "$STORE"; echo "store writable again" ;;
  leader)
    curl -sf localhost:$REST_BASE/v2/topology | jq -r --argjson p "$2" \
      '.brokers[] | select(any(.partitions[]; .partitionId==$p and .role=="leader")) | .nodeId' ;;
esac
