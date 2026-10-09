#!/usr/bin/env bash
#
# Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
# one or more contributor license agreements. See the NOTICE file distributed
# with this work for additional information regarding copyright ownership.
# Licensed under the Camunda License 1.0. You may not use this file
# except in compliance with the Camunda License 1.0.
#
# Runs JobQueueWorkload once per variant, sequentially, against a fresh DB each time, and writes
# all windows into one CSV plus a summary. Variants are "label|arg arg ..." lines read from a file
# (default: variants.txt next to this script); every run also gets the COMMON_ARGS below.
#
# Usage: ./run-sweep.sh [variants-file] [extra args applied to every run...]
#   e.g. ./run-sweep.sh variants.txt duration=PT300S preloadKeys=8000000
#
# Env:
#   RESULTS_DIR  where CSV/logs go (default: ./results/<timestamp>)
#   DB_DIR       where the DB lives; put it on the disk type you care about (default: $RESULTS_DIR/db)
#   JAVA_OPTS    JVM flags (default: fixed heap, no JIT warmup tricks)
#   CPUS         optional taskset CPU list (e.g. "2,3") to pin the run away from noisy neighbours
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
JAR="$SCRIPT_DIR/../target/benchmarks.jar"
VARIANTS_FILE="${1:-$SCRIPT_DIR/variants.txt}"
shift || true
EXTRA_ARGS=("$@")

RESULTS_DIR="${RESULTS_DIR:-$SCRIPT_DIR/results/$(date +%Y%m%d-%H%M%S)}"
DB_DIR="${DB_DIR:-$RESULTS_DIR/db}"
JAVA_OPTS="${JAVA_OPTS:--Xms1g -Xmx1g -XX:+UseParallelGC}"
COMMON_ARGS=("dbDir=$DB_DIR" "out=$RESULTS_DIR/windows.csv")

if [[ ! -f "$JAR" ]]; then
  echo "Building $JAR ..."
  (cd "$SCRIPT_DIR/../.." && ./mvnw package -pl microbenchmarks -DskipTests -Dquickly=false -DskipChecks -q)
fi

mkdir -p "$RESULTS_DIR"
cp "$VARIANTS_FILE" "$RESULTS_DIR/variants.txt"
{
  echo "date: $(date -Iseconds)"
  echo "host: $(uname -a)"
  echo "cpus: $(nproc)"
  echo "mem: $(free -h | awk '/Mem:/ {print $2}')"
  echo "disk: $(df -h "$(dirname "$DB_DIR")" | tail -1)"
  echo "git: $(git -C "$SCRIPT_DIR" rev-parse HEAD 2>/dev/null || echo n/a)"
  echo "extra args: ${EXTRA_ARGS[*]:-}"
} > "$RESULTS_DIR/env.txt"

PIN=()
if [[ -n "${CPUS:-}" ]]; then
  PIN=(taskset -c "$CPUS")
fi

while IFS= read -r line || [[ -n "$line" ]]; do
  [[ -z "${line// }" || "$line" =~ ^[[:space:]]*# ]] && continue
  label="${line%%|*}"
  label="${label// /}"
  read -r -a args <<< "${line#*|}"
  echo "=== $label: ${args[*]} ${EXTRA_ARGS[*]:-}"
  sync
  # shellcheck disable=SC2086
  "${PIN[@]}" java $JAVA_OPTS -cp "$JAR" io.camunda.microbenchmarks.rocksdb.JobQueueWorkload \
    "${COMMON_ARGS[@]}" "label=$label" "${args[@]}" "${EXTRA_ARGS[@]}" \
    2> >(tee "$RESULTS_DIR/$label.log" >&2)
  # keep the RocksDB LOG/OPTIONS of each run for later inspection, drop the data
  mkdir -p "$RESULTS_DIR/rocksdb-$label"
  cp "$DB_DIR"/LOG "$DB_DIR"/OPTIONS-* "$RESULTS_DIR/rocksdb-$label/" 2>/dev/null || true
  rm -rf "$DB_DIR"
done < "$VARIANTS_FILE"

python3 "$SCRIPT_DIR/summarize.py" "$RESULTS_DIR/windows.csv" | tee "$RESULTS_DIR/summary.md"
