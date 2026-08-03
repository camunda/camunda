#!/usr/bin/env bash
# Logging, timings and the evidence a run leaves behind.
#
# Sourced by run.sh; not runnable on its own.

# --- Logging ---------------------------------------------------------------

# All of these go to stderr, so that a helper can both narrate what it is doing
# and return a value on stdout for its caller to capture.
log()  { printf '%s %s\n' "$(date -u +%H:%M:%S)" "$*" | tee -a "$RUN_DIR/run.log" >&2; }
warn() { printf '%s WARN  %s\n' "$(date -u +%H:%M:%S)" "$*" | tee -a "$RUN_DIR/run.log" >&2; }
die()  { printf '%s FATAL %s\n' "$(date -u +%H:%M:%S)" "$*" | tee -a "$RUN_DIR/run.log" >&2; exit 1; }

section() {
  printf '\n%s ── %s ──\n' "$(date -u +%H:%M:%S)" "$*" | tee -a "$RUN_DIR/run.log" >&2
}

# --- Timings ---------------------------------------------------------------

# Every wait in a scenario goes through this, so that --time-scale can shrink a
# whole run to something that finishes in minutes while it is being debugged.
# A scaled duration never drops below a second, or waits meant to let metrics
# scrape would vanish entirely.
scaled() {
  local seconds=$1
  awk -v s="$seconds" -v f="$TIME_SCALE" 'BEGIN { v = s * f; print (v < 1 ? 1 : int(v)) }'
}

# settle <seconds> <what for>
settle() {
  local seconds; seconds=$(scaled "$1")
  log "  waiting ${seconds}s: $2"
  sleep "$seconds"
}

# Waits for what the last scenario did to have been scraped, so that the next one
# does not attribute the previous scenario's counters to itself. Deliberately not
# scaled: how long a scrape takes is a property of the cluster, not of how long
# the scenarios are, and a shrunk run must not become a differently-behaving one.
settle_scrape() {
  log "  waiting ${SCRAPE_SETTLE}s: $1"
  sleep "$SCRAPE_SETTLE"
}

now_ms() { python3 -c 'import time; print(int(time.time() * 1000))'; }

# Formatted through python3 rather than date, whose epoch-input flag differs
# between the BSD date on macOS and GNU date on Linux.
iso_from_ms() {
  python3 -c 'import sys, time; print(time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime(int(sys.argv[1]) / 1000)))' "$1"
}

clock_from_ms() {
  python3 -c 'import sys, time; print(time.strftime("%H:%M:%S", time.gmtime(int(sys.argv[1]) / 1000)))' "$1"
}

# --- Evidence --------------------------------------------------------------

# Each scenario owns a directory, so that a body captured mid-run cannot be
# overwritten by the next scenario and every artefact says which scenario it
# belongs to.
scenario_begin() {
  SCENARIO=$1
  SCENARIO_DIR="$RUN_DIR/$(printf '%02d' "$SCENARIO_INDEX")-$SCENARIO"
  SCENARIO_START_MS=$(now_ms)
  mkdir -p "$SCENARIO_DIR"
  : > "$SCENARIO_DIR/assertions.tsv"
  section "scenario $SCENARIO_INDEX/$SCENARIO_TOTAL: $SCENARIO"
}

scenario_end() {
  local end_ms; end_ms=$(now_ms)
  local passed failed
  # grep -c exits non-zero on no matches, which set -e would take as fatal
  passed=$(grep -c '^PASS' "$SCENARIO_DIR/assertions.tsv" || true)
  failed=$(grep -c '^FAIL' "$SCENARIO_DIR/assertions.tsv" || true)
  RUN_PASSED=$((RUN_PASSED + passed))
  RUN_FAILED=$((RUN_FAILED + failed))

  jq -nc \
    --arg scenario "$SCENARIO" \
    --argjson index "$SCENARIO_INDEX" \
    --argjson from "$SCENARIO_START_MS" \
    --argjson to "$end_ms" \
    --argjson passed "$passed" \
    --argjson failed "$failed" \
    '{scenario: $scenario, index: $index, from: $from, to: $to, passed: $passed, failed: $failed}' \
    >> "$RUN_DIR/timeline.jsonl"

  log "scenario $SCENARIO finished: $passed passed, $failed failed, $(( (end_ms - SCENARIO_START_MS) / 1000 ))s"
  if [[ "$failed" -gt 0 && "$STRICT" == "true" ]]; then
    die "stopping because $SCENARIO failed $failed assertions and --strict is set"
  fi
}

# note <text> — a free-text observation kept next to the scenario's artefacts,
# for the things that are read rather than asserted.
note() {
  printf '%s %s\n' "$(date -u +%H:%M:%S)" "$*" >> "$SCENARIO_DIR/notes.log"
  log "  note: $*"
}

# --- Reporting -------------------------------------------------------------

render_summary() {
  local summary="$RUN_DIR/SUMMARY.md"
  local run_end_ms; run_end_ms=$(now_ms)
  local grafana="$GRAFANA_URL"
  local dashboard="$grafana/d/$DASHBOARD_UID/$DASHBOARD_SLUG"

  {
    echo "# Coordinated rebalance run — $NS"
    echo
    echo "| | |"
    echo "|---|---|"
    echo "| Namespace | \`$NS\` |"
    echo "| Started | $(iso_from_ms "$RUN_START_MS") |"
    echo "| Finished | $(iso_from_ms "$run_end_ms") |"
    echo "| Duration | $(( (run_end_ms - RUN_START_MS) / 60000 )) min |"
    echo "| Assertions | $RUN_PASSED passed, $RUN_FAILED failed |"
    echo "| Time scale | $TIME_SCALE |"
    echo
    echo "Whole run in Grafana:"
    echo
    echo "- Rebalancing and Zeebe internals: <$dashboard?var-namespace=$NS&from=$RUN_START_MS&to=$run_end_ms>"
    echo "- Performance: <$grafana/d/camunda-performance/camunda-performance?var-namespace=$NS&from=$RUN_START_MS&to=$run_end_ms>"
    echo
    echo "## Scenarios"
    echo
    echo "| # | Scenario | Window (UTC) | Assertions | Dashboard |"
    echo "|---|---|---|---|---|"
    while read -r line; do
      local scenario index from to passed failed verdict
      scenario=$(jq -r '.scenario' <<<"$line")
      index=$(jq -r '.index' <<<"$line")
      from=$(jq -r '.from' <<<"$line")
      to=$(jq -r '.to' <<<"$line")
      passed=$(jq -r '.passed' <<<"$line")
      failed=$(jq -r '.failed' <<<"$line")
      verdict="$passed passed"
      [[ "$failed" -gt 0 ]] && verdict="$verdict, **$failed failed**"
      printf '| %s | %s | %s → %s | %s | [zeebe](%s) |\n' \
        "$index" "$scenario" \
        "$(clock_from_ms "$from")" "$(clock_from_ms "$to")" \
        "$verdict" \
        "$dashboard?var-namespace=$NS&from=$from&to=$to"
    done < "$RUN_DIR/timeline.jsonl"
    echo
    echo "## Failed assertions"
    echo
    if grep -h '^FAIL' "$RUN_DIR"/*/assertions.tsv >/dev/null 2>&1; then
      echo '```'
      grep -H '^FAIL' "$RUN_DIR"/*/assertions.tsv | sed "s#$RUN_DIR/##"
      echo '```'
    else
      echo "None."
    fi
    echo
    echo "## Dashboard panels over this window"
    echo
    if [[ -s "$RUN_DIR/dashboard-panels.txt" ]]; then
      echo '```'
      cat "$RUN_DIR/dashboard-panels.txt"
      echo '```'
    else
      echo "Not checked."
    fi
    echo
    echo "## Artefacts"
    echo
    echo "Per scenario: \`assertions.tsv\` (every metric assertion with its actual value),"
    echo "\`*.json\` (rebalance status bodies, the record of what each partition ended as),"
    echo "\`notes.log\` (observations that were read rather than asserted)."
  } > "$summary"

  log "summary written to $summary"
}
