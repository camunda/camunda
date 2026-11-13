#!/usr/bin/env bats

# Tests for build.sh. No network: stubs on PATH answer bq from fixture files, gh from a table
# of issue searches, and git and codeowners-cli for the medic lookup.

BUILD="${BATS_TEST_DIRNAME}/build.sh"

setup() {
  cd "${BATS_TEST_TMPDIR}"
  mkdir -p bin fixtures
  export FIXTURES="${BATS_TEST_TMPDIR}/fixtures"
  export GH_SEARCH_LOG="${BATS_TEST_TMPDIR}/gh-searches.txt"
  export PATH="${BATS_TEST_TMPDIR}/bin:${PATH}"

  # bq: picks the fixture by the column the query selects
  cat > bin/bq <<'EOF'
#!/usr/bin/env bash
query="${*: -1}"
case "$query" in
  *user_reason*) cat "${FIXTURES}/flaky-jobs.json" ;;
  *number_of_all_jobs*) cat "${FIXTURES}/all-jobs.json" ;;
  *number_of_flakes*) cat "${FIXTURES}/top-tests.json" ;;
  *number_of_fails*) cat "${FIXTURES}/top-tests.json" ;;
  *) echo "unexpected query: $query" >&2; exit 1 ;;
esac
EOF

  # gh issue list --search <s>: prints the issue number listed for <s> in fixtures/issues.tsv
  cat > bin/gh <<'EOF'
#!/usr/bin/env bash
[ -f "${FIXTURES}/gh.fail" ] && { echo "HTTP 502" >&2; exit 1; }
while [ $# -gt 0 ]; do
  case "$1" in
    --search) search="$2"; shift 2 ;;
    *) shift ;;
  esac
done
echo "$search" >> "$GH_SEARCH_LOG"
[ -f "${FIXTURES}/issues.tsv" ] && awk -F'\t' -v s="$search" '$1 == s { print $2 }' "${FIXTURES}/issues.tsv"
exit 0
EOF

  # git ls-files <glob>: the glob without its leading **/, as the test source sits in the cwd
  cat > bin/git <<'EOF'
#!/usr/bin/env bash
echo "${2#\*\*/}"
EOF

  cat > bin/codeowners-cli <<'EOF'
#!/usr/bin/env bash
file="${*: -1}"
jq -n --arg f "$file" '{($f): {required: ["@camunda/data-layer"]}}'
EOF
  chmod +x bin/*

  echo '[{"number_of_all_jobs":"2000"}]' > fixtures/all-jobs.json
  echo '[{"number_of_all_jobs":"6"}]' > fixtures/flaky-jobs.json
}

# top_tests <count field> <class>|<test>|<count>...
top_tests() {
  local field="$1"; shift
  printf '%s\n' "$@" | jq -R -s -c --arg f "$field" '
    split("\n") | map(select(. != "") | split("|"))
    | map({test_class_name: .[0], test_name: .[1], ($f): .[2]})' > fixtures/top-tests.json
}

# owned_test <fully qualified class name>: makes the medic lookup find the test source file
owned_test() {
  local path="src/test/java/${1//.//}.java"
  mkdir -p "$(dirname "$path")"
  touch "$path"
}

issue() {
  printf '%s\t%s\n' "$1" "$2" >> fixtures/issues.tsv
}

@test "should summarize flaky tests with GH issue links and due medic pings" {
  # given
  top_tests number_of_flakes \
    "io.camunda.it.FooIT|shouldFoo|12" \
    "io.camunda.it.BarIT|shouldBar|11" \
    "io.camunda.it.BazIT|shouldBaz|4"
  owned_test io.camunda.it.FooIT
  owned_test io.camunda.it.BarIT
  owned_test io.camunda.it.BazIT
  issue "in:title BarIT AND (label:kind/flake OR in:title flaky)" 4711

  # when
  run bash "$BUILD" flaky

  # then
  [ "$status" -eq 0 ] || { echo "$output"; return 1; }
  [ "${lines[0]}" = "📈 ~0.3% of jobs had flaky tests yesterday (6 from total of 2000 jobs)." ]
  [ "${lines[1]}" = "🥇 *Top 5 flakiest tests yesterday:*" ]
  [ "${lines[2]}" = '• 12x `FooIT.shouldFoo`' ]
  [ "${lines[3]}" = ":arrow_right:  <!subteam^S08P2CSC06S|data-layer-medic>" ]
  [ "${lines[4]}" = '• 11x `BarIT.shouldBar` (<https://github.com/camunda/camunda/issues/4711|GH issue>)' ]
  [ "${lines[5]}" = '• 4x `BazIT.shouldBaz`' ]
  [[ "${lines[6]}" == "📊 Check out <https://dashboard.int.camunda.com/"*"var-branch=main|Grafana (\`main\`) for links"* ]]
  [ "${#lines[@]}" -eq 7 ]
}

@test "should search flaky GH issues by test name first, then by class name" {
  # given
  top_tests number_of_flakes "io.camunda.it.FooIT|shouldFoo|1"

  # when
  run bash "$BUILD" flaky

  # then
  [ "$status" -eq 0 ] || { echo "$output"; return 1; }
  [ "$(sed -n 1p "$GH_SEARCH_LOG")" = "in:title FooIT AND in:title shouldFoo AND (label:kind/flake OR in:title flaky)" ]
  [ "$(sed -n 2p "$GH_SEARCH_LOG")" = "in:title FooIT AND (label:kind/flake OR in:title flaky)" ]
}

@test "should summarize failed tests on main and name frontend specs by file" {
  # given
  top_tests number_of_fails \
    "io.camunda.it.FooIT|shouldFoo|3" \
    "src/operate/Processes.test.tsx|renders the list|5"
  owned_test io.camunda.it.FooIT
  issue 'in:title flaky "Processes"' 815

  # when
  run bash "$BUILD" failure

  # then
  [ "$status" -eq 0 ] || { echo "$output"; return 1; }
  [ "${lines[0]}" = ':fire: *Top 5 failing tests on `main` last week:*' ]
  [ "${lines[1]}" = '• 3x `FooIT.shouldFoo`' ]
  [ "${lines[2]}" = ":arrow_right:  <!subteam^S08P2CSC06S|data-layer-medic>" ]
  [ "${lines[3]}" = '• 5x `Processes.renders the list` (<https://github.com/camunda/camunda/issues/815|GH issue>)' ]
  [[ "${lines[4]}" == *"from=now-1w%2Fw&to=now-1w%2Fw"*"var-branch=main|Grafana for links and more details>." ]]
  grep -qx "in:title FooIT AND in:title shouldFoo" "$GH_SEARCH_LOG"
}

@test "should name frontend specs by file in the flaky summary" {
  # given
  top_tests number_of_flakes "src/operate/Processes.test.tsx|renders the list|1"
  issue 'in:title "Processes" AND (label:kind/flake OR in:title flaky)' 815

  # when
  run bash "$BUILD" flaky

  # then
  [ "$status" -eq 0 ] || { echo "$output"; return 1; }
  [ "${lines[2]}" = '• 1x `Processes.renders the list` (<https://github.com/camunda/camunda/issues/815|GH issue>)' ]
}

@test "should list tests without GH issue links when the GH lookup fails" {
  # given
  top_tests number_of_fails "io.camunda.it.FooIT|shouldFoo|1"
  touch fixtures/gh.fail

  # when
  run bash "$BUILD" failure

  # then
  [ "$status" -eq 0 ] || { echo "$output"; return 1; }
  [[ "$output" == *"::warning::gh issue lookup failed for FooIT"* ]]
  [[ "$output" == *'• 1x `FooIT.shouldFoo`'* ]]
}

@test "should reject an unknown mode" {
  run bash "$BUILD" weekly
  [ "$status" -eq 2 ]
}
