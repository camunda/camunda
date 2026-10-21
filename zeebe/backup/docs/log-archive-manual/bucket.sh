#!/usr/bin/env bash
# Usage: bucket.sh <scenario> [timeline]                      summary of the log archive per partition
#        bucket.sh old-backups <scenario>                     old-style backups and metadata.json per partition
#        bucket.sh handover <scenario>                        handover files metadata/<p>/handover/<H>.json
#        bucket.sh handover-commits <scenario> [timeline]     HANDOVER commit entries of the archive
#        bucket.sh merge <scenario> [timeline]                LOG_MERGED/LOG_RELEASED entries, merged ranges (contiguous?),
#                                                             replaced objects still in data/, level-1 rollups
#        bucket.sh wait-handover <scenario> [afterId]         waits until every partition has a handover
#                                                             above afterId, prints its checkpoint id
source "$(dirname "$0")/env.sh"

case "${1:-}" in
  old-backups)
    D=$(store_of "${2:?scenario}")
    for p in $(seq 1 $PARTITIONS); do
      echo "p$p: old-style backups (manifests/$p): $(ls "$D/manifests/$p" 2>/dev/null | tr '\n' ' ')"
      jq -c . "$D/metadata/$p/metadata.json" 2>/dev/null || echo "  no metadata/$p/metadata.json"
    done
    exit 0 ;;
  handover)
    D=$(store_of "${2:?scenario}")
    for p in $(seq 1 $PARTITIONS); do
      echo "p$p: handover files"
      found=0
      for f in "$D/metadata/$p/handover/"*.json; do
        [ -f "$f" ] || continue
        found=1; echo "  $(basename "$f")"; jq -c . "$f"
      done
      [ $found -eq 1 ] || echo "  none"
    done
    exit 0 ;;
  handover-commits)
    D=$(store_of "${2:?scenario}"); T=${3:-tl-0}
    for p in $(seq 1 $PARTITIONS); do
      found=0
      for f in "$D/log-archive/$T/p$p/commits/"*.json; do
        [ -f "$f" ] || continue
        out=$(jq -c 'select(.type=="HANDOVER") | {seq, type, handover}' "$f")
        [ -n "$out" ] && { found=1; echo "p$p $(basename "$f"): $out"; }
      done
      [ $found -eq 1 ] || echo "p$p: no HANDOVER commit entry"
    done
    exit 0 ;;
  merge)
    D=$(store_of "${2:?scenario}"); T=${3:-tl-0}
    for p in $(seq 1 $PARTITIONS); do
      pre="$D/log-archive/$T/p$p"
      [ -d "$pre/commits" ] || { echo "p$p: no archive yet"; continue; }
      # merged ranges in log order, from the LOG_MERGED entries
      jq -r 'select(.type=="LOG_MERGED") | .objects[0] | "\(.firstIndex) \(.lastIndex) \(.key)"' "$pre"/commits/*.json > "$WORK/merged-p$p.txt"
      n=$(wc -l < "$WORK/merged-p$p.txt" | tr -d ' ')
      gaps=$(awk 'NR>1 && $1 != prev+1 {g++} {prev=$2} END {print g+0}' "$WORK/merged-p$p.txt")
      rel=$(jq -c 'select(.type=="LOG_RELEASED") | {seq, releasedIndex, releasedPosition}' "$pre"/commits/*.json | tail -1)
      last=$(tail -1 "$WORK/merged-p$p.txt" | cut -d' ' -f2)
      total=$(ls "$pre/data" | wc -l | tr -d ' '); l1=$(ls "$pre/data" | grep -c -- '-l1.seg$')
      # objects below the merged index whose key is not a merged range: replaced small objects not deleted yet
      replaced=0; for f in "$pre/data/"*.seg; do
        k=${f##*/}; case "$k" in *-l1.seg) continue ;; esac
        li=$((10#$(echo "$k" | cut -d- -f2)))
        [ "$li" -le "${last:-0}" ] && ! grep -q " data/$k$" "$WORK/merged-p$p.txt" && replaced=$((replaced+1))
      done
      echo "p$p: LOG_MERGED entries=$n (merged up to ${last:-none}, gaps between merged ranges: $gaps) LOG_RELEASED newest: ${rel:-none}"
      echo "    data/ objects=$total level-1 rollups=$l1 replaced-but-still-there=$replaced"
    done
    exit 0 ;;
  wait-handover)
    D=$(store_of "${2:?scenario}"); after=${3:-0}
    for _ in $(seq 180); do
      ids=""; complete=1
      for p in $(seq 1 $PARTITIONS); do
        id=$(for f in "$D/metadata/$p/handover/"*.json; do [ -f "$f" ] && jq -r .checkpointId "$f"; done | sort -n | tail -1)
        if [ -z "$id" ] || [ "$id" -le "$after" ]; then complete=0; break; fi
        ids="$ids $id"
      done
      if [ $complete -eq 1 ]; then
        set -- $ids; first=$1
        for id in "$@"; do
          [ "$id" = "$first" ] || { echo "partitions recorded different handovers:$ids" >&2; exit 1; }
        done
        echo "$first"; exit 0
      fi
      sleep 5
    done
    echo "no handover above $after on every partition, see $D/metadata" >&2; exit 1 ;;
esac

S=${1:?scenario}; T=${2:-tl-0}
for p in $(seq 1 $PARTITIONS); do
  pre="$(store_of "$S")/log-archive/$T/p$p"
  [ -d "$pre" ] || { echo "p$p: no archive yet ($pre)"; continue; }
  c() { ls "$pre/$1" 2>/dev/null | wc -l | tr -d ' '; }
  echo "p$p: commits=$(c commits) data=$(c data) index=$(c index) snapshots=$(c state)"
  last=$(ls "$pre/commits" 2>/dev/null | tail -1)
  [ -n "$last" ] && jq -c '{seq, type, archivedIndex, archivedPosition, checkpoints: [.checkpoints[]? | .checkpointId]}' "$pre/commits/$last"
done
