#!/usr/bin/env bash
# Usage: restore.sh <scenario> <restore args...>   e.g. restore.sh a-classic --backupId=123
#        restore.sh c-archive --logArchive.timeline=tl-0 --logArchive.checkpointId=123
source "$(dirname "$0")/env.sh"
S=$1; shift
for i in $(seq 0 $((BROKERS - 1))); do
  ( while IFS= read -r kv; do export "$kv"; done < <(broker_env "$i" "$S")
    "$DIST/bin/restore" "$@" > "$WORK/restore-$i.log" 2>&1 ) && echo "broker $i restored" || { echo "broker $i restore FAILED, see $WORK/restore-$i.log"; tail -5 "$WORK/restore-$i.log"; }
done
