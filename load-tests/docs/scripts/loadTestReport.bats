#!/usr/bin/env bats

bats_require_minimum_version 1.5.0

# Tests for loadTestReport.sh.
#
# Unit tests source the script (past its `if [[ "${BASH_SOURCE[0]}" == "${0}" ]]`
# guard, so `main` never runs) and call extract_metric_value/parse_epoch directly —
# fast, and pin the exact reason string each failure logs to stderr.
#
# End-to-end tests run the real script through `run bash "$SCRIPT" ...` against a
# stub `curl` on PATH that returns canned Prometheus JSON, so the full argument
# parsing -> yq/jq query-file pipeline -> rendering path is exercised without a
# live cluster. This is also the regression pin for a real bug found while
# building this script: a query that returned no data corrupted the JSON output
# instead of leaving that column null (see the "no data" test below).

SCRIPT="${BATS_TEST_DIRNAME}/loadTestReport.sh"

run_fn()        { run bash -c 'source "$1"; shift; "$@" 2>/dev/null' _ "$@"; }
run_fn_stderr() { run bash -c 'source "$1"; shift; "$@" 2>&1 >/dev/null' _ "$@"; }

setup() {
  mkdir -p "${BATS_TEST_TMPDIR}/bin"
  PATH="${BATS_TEST_TMPDIR}/bin:${PATH}"

  # Fake curl: never touches the network. Recognizes the two endpoints
  # loadTestReport.sh calls and picks a canned response for /api/v1/query from
  # the query text's key substring, matching the fixture in query_file().
  cat > "${BATS_TEST_TMPDIR}/bin/curl" <<'EOF'
#!/usr/bin/env bash
args="$*"
if [[ "$args" == *"/api/v1/status/runtimeinfo"* ]]; then
  echo '{"status":"success","data":{}}'
  exit 0
fi
if [[ "$args" == *"/api/v1/query"* ]]; then
  query=""
  for arg in "$@"; do
    case "$arg" in
      query=*) query="${arg#query=}" ;;
    esac
  done
  case "$query" in
    *no_data_metric*)
      echo '{"status":"success","data":{"result":[]}}'
      ;;
    *label_metric*)
      echo '{"status":"success","data":{"result":[{"metric":{"image":"b"}},{"metric":{"image":"a"}},{"metric":{"image":"a"}}]}}'
      ;;
    *numeric_metric*)
      echo '{"status":"success","data":{"result":[{"metric":{},"value":[1700000000,"42.5"]}]}}'
      ;;
    *)
      echo "unexpected query: $query" >&2
      exit 1
      ;;
  esac
  exit 0
fi
echo "unexpected curl invocation: $*" >&2
exit 1
EOF
  chmod +x "${BATS_TEST_TMPDIR}/bin/curl"

  cat > "${BATS_TEST_TMPDIR}/queries.yaml" <<'EOF'
queries:
  - key: namespace
    header: Namespace
    value: $NAMESPACE
  - key: numeric_metric
    header: Numeric
    query: numeric_metric{namespace="$NAMESPACE"}
  - key: label_metric
    header: Label
    query: label_metric{namespace="$NAMESPACE"}
    valueLabel: image
  - key: no_data_metric
    header: NoData
    query: no_data_metric{namespace="$NAMESPACE"}
EOF
}

# ── extract_metric_value ───────────────────────────────────────────────────

@test "should return the numeric sample value" {
  run_fn "$SCRIPT" extract_metric_value '{"status":"success","data":{"result":[{"value":[1700000000,"42.5"]}]}}' "" mykey
  [ "$status" -eq 0 ]
  [ "$output" = "42.5" ]
}

@test "should return deduplicated, sorted, comma-joined label values as a JSON string" {
  run_fn "$SCRIPT" extract_metric_value '{"status":"success","data":{"result":[{"metric":{"image":"b"}},{"metric":{"image":"a"}},{"metric":{"image":"a"}}]}}' image mykey
  [ "$status" -eq 0 ]
  [ "$output" = '"a, b"' ]
}

@test "should fail without printing a value when a query has no data" {
  run_fn "$SCRIPT" extract_metric_value '{"status":"success","data":{"result":[]}}' "" mykey
  [ "$status" -ne 0 ]
  [ -z "$output" ]
}

@test "should log the specific reason for each failure to stderr, including the key" {
  run_fn_stderr "$SCRIPT" extract_metric_value '{"status":"error"}' "" mykey
  [[ "$output" == *"mykey: Prometheus returned non-success status"* ]]

  run_fn_stderr "$SCRIPT" extract_metric_value '{"status":"success","data":{"result":[]}}' somelabel mykey
  [[ "$output" == *"mykey: no label sample"* ]]

  run_fn_stderr "$SCRIPT" extract_metric_value '{"status":"success","data":{"result":[]}}' "" mykey
  [[ "$output" == *"mykey: no numeric sample"* ]]
}

# ── parse_epoch ─────────────────────────────────────────────────────────────

@test "should pass a plain integer through unchanged" {
  run_fn "$SCRIPT" parse_epoch 1700000000
  [ "$status" -eq 0 ]
  [ "$output" = "1700000000" ]
}

@test "should parse an RFC3339 timestamp to its Unix epoch" {
  run_fn "$SCRIPT" parse_epoch 2026-01-01T00:00:00Z
  [ "$status" -eq 0 ]
  [ "$output" = "1767225600" ]
}

@test "should fail on an unparseable time value" {
  run_fn "$SCRIPT" parse_epoch not-a-date
  [ "$status" -ne 0 ]
}

# ── End-to-end CLI ────────────────────────────────────────────────────────

@test "should build a JSON report, leaving a no-data metric null instead of crashing" {
  run --separate-stderr bash "$SCRIPT" c8-test-ns --queries-file "${BATS_TEST_TMPDIR}/queries.yaml" --format json
  [ "$status" -eq 0 ]
  echo "$output" | jq -e '
    .namespace == "c8-test-ns"
    and .metrics.numeric_metric == 42.5
    and .metrics.label_metric == "a, b"
    and .metrics.no_data_metric == null
  '
  [[ "$stderr" == *"no_data_metric: no numeric sample"* ]]
}

@test "should render CSV with the requested missing-value placeholder" {
  run --separate-stderr bash "$SCRIPT" c8-test-ns --queries-file "${BATS_TEST_TMPDIR}/queries.yaml" \
    --format csv --no-header --missing-value "N/A"
  [ "$status" -eq 0 ]
  [ "$output" = '"c8-test-ns",42.5,"a, b","N/A"' ]
}

@test "should reject a missing namespace" {
  run bash "$SCRIPT"
  [ "$status" -ne 0 ]
  [[ "$output" == *"Missing <namespace>"* ]]
}

@test "should reject an invalid namespace" {
  run bash "$SCRIPT" "Not_A_Valid_Namespace"
  [ "$status" -ne 0 ]
  [[ "$output" == *"must be a valid Kubernetes DNS label"* ]]
}

@test "should reject an unsupported --format" {
  run bash "$SCRIPT" c8-test-ns --queries-file "${BATS_TEST_TMPDIR}/queries.yaml" --format yaml
  [ "$status" -ne 0 ]
  [[ "$output" == *"Unsupported --format"* ]]
}

@test "should print usage on --help without requiring a namespace" {
  run bash "$SCRIPT" --help
  [ "$status" -eq 0 ]
  [[ "$output" == *"Usage: loadTestReport.sh"* ]]
}

@test "should reject the legacy loadTestMetrics query schema" {
  cat > "${BATS_TEST_TMPDIR}/legacy-queries.yaml" <<'EOF'
queries:
  - name: throughput-per-second
    description: Client Process Instance Started Rate (avg)
    query: numeric_metric{namespace="$NAMESPACE"}
EOF

  run bash "$SCRIPT" c8-test-ns --queries-file "${BATS_TEST_TMPDIR}/legacy-queries.yaml"
  [ "$status" -ne 0 ]
  [[ "$output" == *"invalid queries file"* ]]
  [[ "$output" == *"non-empty string key"* ]]
}

@test "should reject duplicate query keys" {
  cat > "${BATS_TEST_TMPDIR}/duplicate-queries.yaml" <<'EOF'
queries:
  - key: duplicate_metric
    header: Duplicate 1
    query: numeric_metric{namespace="$NAMESPACE"}
  - key: duplicate_metric
    header: Duplicate 2
    value: static
EOF

  run bash "$SCRIPT" c8-test-ns --queries-file "${BATS_TEST_TMPDIR}/duplicate-queries.yaml"
  [ "$status" -ne 0 ]
  [[ "$output" == *"duplicate query key: duplicate_metric"* ]]
}

@test "should reject entries without exactly one value source" {
  cat > "${BATS_TEST_TMPDIR}/invalid-source-queries.yaml" <<'EOF'
queries:
  - key: missing_source
    header: Missing source
EOF

  run bash "$SCRIPT" c8-test-ns --queries-file "${BATS_TEST_TMPDIR}/invalid-source-queries.yaml"
  [ "$status" -ne 0 ]
  [[ "$output" == *"must set exactly one of query or value"* ]]
}

@test "should reject valueLabel without a query" {
  cat > "${BATS_TEST_TMPDIR}/invalid-label-queries.yaml" <<'EOF'
queries:
  - key: invalid_label
    header: Invalid label
    value: static
    valueLabel: image
EOF

  run bash "$SCRIPT" c8-test-ns --queries-file "${BATS_TEST_TMPDIR}/invalid-label-queries.yaml"
  [ "$status" -ne 0 ]
  [[ "$output" == *"sets valueLabel without query"* ]]
}

@test "should render the default camunda template end-to-end" {
  run --separate-stderr bash "$SCRIPT" c8-test-ns
  [ "$status" -eq 0 ]
  echo "$output" | jq -e '.columns | length == 45'
}

@test "should render the stable-87 template end-to-end" {
  run --separate-stderr bash "$SCRIPT" c8-test-ns --template stable-87
  [ "$status" -eq 0 ]
  echo "$output" | jq -e '.columns | length == 53'
}
