#!/usr/bin/env bash
# Usage: event-plane.sh start | stop | kill | overview | check [waitSeconds] | objects <partition>
#   Runs the event plane server (demo profile, in-memory H2, so every start is an empty catalog) next to
#   the brokers of scenario i-eventplane and compares what it has announced with the store.
#   EP_DIR (checkout of camunda/event-plane, jar built with `./mvnw -o install -DskipTests`) and
#   EP_PORT (default 8090, broker 0 serves REST on 8080) are set in env.sh.
source "$(dirname "$0")/env.sh"
S=${SCENARIO:-i-eventplane}
EP=localhost:$EP_PORT
ep() { curl -sf -u demo:demo "$EP$1"; }
newest_seq() { ls "$1" 2>/dev/null | tail -1 | sed 's/\.json$//' | sed 's/^0*//'; }

case "$1" in
  start)
    mkdir -p "$(store_of "$S")"
    # j-merge: roll up 4-minute periods, checked every 10 seconds, and keep the released objects for 10 minutes
    extra=()
    if [ "$S" = j-merge ]; then
      extra=(--camunda.event-plane.log-archive.rollup.enabled=true
             --camunda.event-plane.log-archive.rollup.period=PT4M
             --camunda.event-plane.log-archive.rollup.delay=PT10S
             --camunda.event-plane.log-archive.rollup.interval=PT10S
             --camunda.event-plane.log-archive.retention=PT10M)
    fi
    nohup java -jar "$EP_DIR/server/target/event-plane-server-0-SNAPSHOT.jar" --spring.profiles.active=demo \
      --server.port="$EP_PORT" --camunda.event-plane.log-archive.default-store="$(store_of "$S")" \
      "${extra[@]}" > "$WORK/ep.log" 2>&1 &
    echo $! > "$WORK/ep.pid"
    for _ in $(seq 60); do ep /v1/log-archive >/dev/null && { echo "event plane up on :$EP_PORT"; exit 0; }; sleep 1; done
    echo "event plane did not start, see $WORK/ep.log"; exit 1 ;;
  stop) kill "$(cat "$WORK/ep.pid")" 2>/dev/null; sleep 2; echo "event plane stopped" ;;
  kill) kill -9 "$(cat "$WORK/ep.pid")"; echo "event plane killed (SIGKILL)" ;;
  overview)
    ep /v1/log-archive | jq -r '.partitions[] | . as $p | .timelines[] |
      "p\($p.partitionId) \(.timelineId) current=\(.current) parent=\(.parentTimelineId) takeover(index=\(.parentIndex),position=\(.parentPosition)) nextSeq=\(.nextSeq) objects=\(.objectCount) checkpoints=\(.checkpointCount) snapshots=\(.snapshotCount) lastPosition=\(.lastPosition)"' ;;
  check)
    # Passes when, for every timeline the event plane knows, nextSeq-1 equals the newest commit seq in the
    # store and the store has no timeline the event plane lacks. Retries for waitSeconds (default 20).
    wait=${2:-20}
    for t in $(seq 0 "$wait"); do
      out=""; ok=1
      for p in $(seq 1 "$PARTITIONS"); do
        for d in "$(store_of "$S")"/log-archive/*/p$p; do
          [ -d "$d/commits" ] || continue
          tl=$(basename "$(dirname "$d")")
          mine=$(newest_seq "$d/commits")
          next=$(ep /v1/log-archive | jq -r --argjson p "$p" --arg tl "$tl" \
            '[.partitions[] | select(.partitionId==$p) | .timelines[] | select(.timelineId==$tl) | .nextSeq][0] // 1')
          [ "$((next - 1))" = "$mine" ] || ok=0
          out="$out  p$p $tl: store newest seq $mine, event plane nextSeq-1 $((next - 1))\n"
        done
      done
      [ $ok -eq 1 ] && { printf "CAUGHT UP\n$out"; exit 0; }
      sleep 1
    done
    printf "NOT CAUGHT UP after ${wait}s\n$out"; exit 1 ;;
  objects)
    # The read API of the current path; checks that the objects follow each other without a gap in the
    # index (within a timeline) and in position order.
    p=${2:?partition}
    ep "/v1/log-archive/default/default/partitions/$p/objects?after=0" | jq -r '
      "objects=\(.objects|length) end=\(.end|tostring)",
      ( [ .objects as $o | range(1; $o|length) | select($o[.].timelineId == $o[.-1].timelineId and $o[.].firstIndex != $o[.-1].lastIndex + 1) ] |
        "index gaps within a timeline: \(length)" ),
      ( [ .objects as $o | range(1; $o|length) | select($o[.].firstPosition <= $o[.-1].lastPosition) ] |
        "position overlaps: \(length)" ),
      ( .objects | group_by(.timelineId) | .[] | "  timeline \(.[0].timelineId): \(length) objects, positions \(.[0].firstPosition)..\(.[-1].lastPosition)" )' ;;
esac
