#!/usr/bin/env bash
# Usage: bucket.sh <scenario> [timeline] — summary of the log archive per partition
source "$(dirname "$0")/env.sh"
S=${1:?scenario}; T=${2:-tl-0}
for p in $(seq 1 $PARTITIONS); do
  pre="$STORE/$S/log-archive/$T/p$p"
  [ -d "$pre" ] || { echo "p$p: no archive yet ($pre)"; continue; }
  c() { ls "$pre/$1" 2>/dev/null | wc -l | tr -d ' '; }
  echo "p$p: commits=$(c commits) data=$(c data) index=$(c index) snapshots=$(c state)"
  last=$(ls "$pre/commits" 2>/dev/null | tail -1)
  [ -n "$last" ] && jq -c '{seq, type, archivedIndex, archivedPosition, checkpoints: [.checkpoints[]? | .checkpointId]}' "$pre/commits/$last"
done
