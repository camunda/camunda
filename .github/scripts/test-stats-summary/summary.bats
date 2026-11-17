#!/usr/bin/env bats

# Tests for build.sh and post.sh. No network: stubs on PATH answer bq from fixture files, gh from
# a table of issue searches, git and codeowners-cli for the medic lookup, and curl for Slack.

BUILD="${BATS_TEST_DIRNAME}/build.sh"
POST="${BATS_TEST_DIRNAME}/post.sh"

GRAFANA="https://dashboard.int.camunda.com/d/ae2j69npxh3b4f/flaky-tests-camunda-camunda-monorepo"
RUNS="https://github.com/camunda/camunda/actions/runs"
ISSUES="https://github.com/camunda/camunda/issues"
DATA_LAYER_MEDIC="<!subteam^S08P2CSC06S|data-layer-medic>"

setup() {
  cd "${BATS_TEST_TMPDIR}"
  mkdir -p bin fixtures
  export FIXTURES="${BATS_TEST_TMPDIR}/fixtures"
  export PATH="${BATS_TEST_TMPDIR}/bin:${PATH}"
  MESSAGES="${BATS_TEST_TMPDIR}/messages.json"

  # bq: picks the fixture by the column the query selects, logs the query parameters
  cat > bin/bq <<'EOF'
#!/usr/bin/env bash
printf '%s\n' "$@" | grep '^--parameter=' >> "${FIXTURES}/bq-parameters.txt"
query="${*: -1}"
case "$query" in
  *user_reason*) echo '[{"number_of_all_jobs":"6"}]' ;;
  *number_of_all_jobs*) echo '[{"number_of_all_jobs":"2000"}]' ;;
  *tests_on_branch*) cat "${FIXTURES}/top-tests.json" ;;
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
echo "$search" >> "${FIXTURES}/gh-searches.txt"
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
jq -n --arg f "${*: -1}" '{($f): {required: ["@camunda/data-layer"]}}'
EOF

  # curl to chat.postMessage: logs the request body, answers ok with a ts, or the error in
  # fixtures/slack-top.fail or fixtures/slack-reply.fail
  cat > bin/curl <<'EOF'
#!/usr/bin/env bash
body="$(cat)"
echo "$body" >> "${FIXTURES}/slack-posts.jsonl"
if jq -e 'has("thread_ts")' <<< "$body" > /dev/null; then fail="${FIXTURES}/slack-reply.fail"; else fail="${FIXTURES}/slack-top.fail"; fi
if [ -f "$fail" ]; then
  jq -cn --arg e "$(cat "$fail")" '{ok: false, error: $e}'
else
  echo '{"ok": true, "ts": "1700000000.000100"}'
fi
EOF
  chmod +x bin/*
  echo '[]' > fixtures/top-tests.json
}

# top_tests <branch>|<class>|<test>|<count>|<tests on branch>|<run>:<attempt>:<job>,...
top_tests() {
  printf '%s\n' "$@" | jq -R -s -c '
    split("\n") | map(select(. != "") | split("|")) | map({
      branch: .[0], test_class_name: .[1], test_name: .[2], occurrences: .[3], tests_on_branch: .[4],
      links: (.[5] | split(",") | map(split(":") | {run_id: .[0], run_attempt: .[1], job_name: .[2]}))
    })' > fixtures/top-tests.json
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

build() {
  run bash "$BUILD" "$@" "$MESSAGES"
  [ "$status" -eq 0 ] || { echo "$output"; return 1; }
}

# section <message index> <block index>: the mrkdwn of a section or context block
block() {
  jq -r --argjson m "$1" --argjson b "$2" '.[$m].blocks[$b] | .text.text // .elements[0].text' "$MESSAGES"
}

@test "should post main in the top-level message and stable branches with tests in its thread" {
  # given
  top_tests \
    "main|io.camunda.it.FooIT|shouldFoo|12|2|101:1:integration-tests/es,102:2:integration-tests/os,103:1:integration-tests/es" \
    "main|io.camunda.it.BarIT|shouldBar|4|2|104:1:unit-tests" \
    "stable/8.9|io.camunda.it.FooIT|shouldFoo|1|1|105:1:integration-tests/es"
  owned_test io.camunda.it.FooIT
  owned_test io.camunda.it.BarIT
  issue "in:title BarIT AND (label:kind/flake OR in:title flaky)" 4711

  # when
  build flaky

  # then
  [ "$(jq 'length' "$MESSAGES")" -eq 2 ]
  [ "$(jq -r '.[0].text' "$MESSAGES")" = "Flaky tests yesterday" ]
  [ "$(block 0 0)" = "📈 ~0.3% of jobs had flaky tests yesterday (6 from total of 2000 jobs)." ]
  [ "$(jq -r '.[0].blocks[1].type' "$MESSAGES")" = "divider" ]
  [ "$(block 0 2)" = "🥇 *\`main\`*: top 3 flaky tests yesterday (<${GRAFANA}?orgId=1&from=now-1d%2Fd&to=now-1d%2Fd&timezone=browser&var-branch=main|Grafana>)
• 12x \`FooIT.shouldFoo\`
      ↳ \`integration-tests/es\` <${RUNS}/101/attempts/1|#1> <${RUNS}/103/attempts/1|#3> · \`integration-tests/os\` <${RUNS}/102/attempts/2|#2>
:arrow_right:  ${DATA_LAYER_MEDIC}
• 4x \`BarIT.shouldBar\` (<${ISSUES}/4711|GH issue>)
      ↳ \`unit-tests\` <${RUNS}/104/attempts/1|#1>" ]
  [ "$(block 0 3)" = ":thread: Stable branches in the thread: \`stable/8.10\` (none) · \`stable/8.9\` (1 flaky test) · \`stable/8.8\` (none)" ]

  [ "$(jq -r '.[1].text' "$MESSAGES")" = "Flaky tests yesterday on stable/8.9" ]
  [ "$(block 1 0)" = "🥇 *\`stable/8.9\`*: top 3 flaky tests yesterday (<${GRAFANA}?orgId=1&from=now-1d%2Fd&to=now-1d%2Fd&timezone=browser&var-branch=stable%2F8.9|Grafana>)
• 1x \`FooIT.shouldFoo\`
      ↳ \`integration-tests/es\` <${RUNS}/105/attempts/1|#1>" ]
}

@test "should count flaky tests of push, merge queue and PR runs of the previous day" {
  # when
  build flaky

  # then
  grep -qx -- '--parameter=days:INT64:1' fixtures/bq-parameters.txt
  grep -qx -- '--parameter=test_status:STRING:flaky' fixtures/bq-parameters.txt
  grep -qx -- '--parameter=triggers:ARRAY<STRING>:\["push","merge_group","pull_request"\]' fixtures/bq-parameters.txt
  grep -qx -- '--parameter=branches:ARRAY<STRING>:\["main","stable/8.10","stable/8.9","stable/8.8"\]' fixtures/bq-parameters.txt
  grep -qx -- '--parameter=top:INT64:3' fixtures/bq-parameters.txt
}

@test "should count failed tests of push and merge queue runs of the previous 7 days" {
  # given
  top_tests "main|io.camunda.it.FooIT|shouldFoo|3|1|101:1:integration-tests/es"
  owned_test io.camunda.it.FooIT

  # when
  build failure

  # then
  grep -qx -- '--parameter=days:INT64:7' fixtures/bq-parameters.txt
  grep -qx -- '--parameter=test_status:STRING:failure' fixtures/bq-parameters.txt
  grep -qx -- '--parameter=triggers:ARRAY<STRING>:\["push","merge_group"\]' fixtures/bq-parameters.txt
  [ "$(jq -r '.[0].text' "$MESSAGES")" = "Failing tests last week" ]
  [ "$(jq '.[0].blocks | length' "$MESSAGES")" -eq 2 ]
  [ "$(block 0 0)" = ":fire: *\`main\`*: top 3 failing tests last week (<${GRAFANA}?orgId=1&from=now-1w%2Fw&to=now-1w%2Fw&timezone=browser&var-branch=main|Grafana>)
• 3x \`FooIT.shouldFoo\`
      ↳ \`integration-tests/es\` <${RUNS}/101/attempts/1|#1>
:arrow_right:  ${DATA_LAYER_MEDIC}" ]
  [ "$(block 0 1)" = ":thread: Stable branches in the thread: \`stable/8.10\` (none) · \`stable/8.9\` (none) · \`stable/8.8\` (none)" ]
  ! grep -q "flake" fixtures/gh-searches.txt
}

@test "should show main without tests and post no thread reply" {
  # when
  build flaky

  # then
  [ "$(jq 'length' "$MESSAGES")" -eq 1 ]
  [ "$(block 0 2)" = "🥇 *\`main\`*: no flaky tests yesterday (<${GRAFANA}?orgId=1&from=now-1d%2Fd&to=now-1d%2Fd&timezone=browser&var-branch=main|Grafana>)" ]
}

@test "should look up the GH issue of a test on several branches once" {
  # given
  top_tests \
    "main|io.camunda.it.FooIT|shouldFoo|2|1|101:1:job" \
    "stable/8.10|io.camunda.it.FooIT|shouldFoo|1|1|102:1:job"

  # when
  build flaky

  # then
  [ "$(cat fixtures/gh-searches.txt)" = "in:title FooIT AND in:title shouldFoo AND (label:kind/flake OR in:title flaky)
in:title FooIT AND (label:kind/flake OR in:title flaky)" ]
}

@test "should not ping the medic below the threshold or when a GH issue exists" {
  # given
  top_tests \
    "main|io.camunda.it.FooIT|shouldFoo|9|2|101:1:job" \
    "main|io.camunda.it.BarIT|shouldBar|10|2|102:1:job"
  owned_test io.camunda.it.FooIT
  owned_test io.camunda.it.BarIT
  issue "in:title BarIT AND in:title shouldBar AND (label:kind/flake OR in:title flaky)" 4711

  # when
  build flaky

  # then
  [[ "$(block 0 2)" != *":arrow_right:"* ]]
}

@test "should label a test without a name as class setup and search its GH issue by class" {
  # given
  top_tests "main|io.camunda.it.GatewayIT||11|1|101:1:job"
  issue "in:title GatewayIT AND (label:kind/flake OR in:title flaky)" 69026

  # when
  build flaky

  # then
  [[ "$(block 0 2)" == *"• 11x \`GatewayIT (class setup)\` (<${ISSUES}/69026|GH issue>)"* ]]
  [ "$(wc -l < fixtures/gh-searches.txt)" -eq 1 ]
}

@test "should name frontend specs by file" {
  # given
  top_tests \
    "main|src/operate/Processes.test.tsx|renders the list|1|1|101:1:job"
  issue 'in:title "Processes" AND (label:kind/flake OR in:title flaky)' 815

  # when
  build flaky

  # then
  [[ "$(block 0 2)" == *"• 1x \`Processes.renders the list\` (<${ISSUES}/815|GH issue>)"* ]]
}

@test "should search failed frontend specs by the quoted file name and flaky" {
  # given
  top_tests "main|src/operate/Processes.test.tsx|renders the list|1|1|101:1:job"
  issue 'in:title flaky "Processes"' 815

  # when
  build failure

  # then
  [[ "$(block 0 0)" == *"(<${ISSUES}/815|GH issue>)"* ]]
}

@test "should escape Slack control characters in test names" {
  # given
  top_tests "main|io.camunda.it.FooIT|should<T> & more|1|1|101:1:job"

  # when
  build flaky

  # then
  [[ "$(block 0 2)" == *'`FooIT.should&lt;T&gt; &amp; more`'* ]]
}

@test "should drop links, then shorten test names, when a section is over the block limit" {
  # given
  long="$(printf 'x%.0s' {1..900})"
  links="101:1:$(printf 'j%.0s' {1..100}),102:1:job-b,103:1:job-c"
  top_tests \
    "main|io.camunda.it.FooIT|${long}a|3|3|${links}" \
    "main|io.camunda.it.FooIT|${long}b|2|3|${links}" \
    "main|io.camunda.it.FooIT|${long}c|1|3|${links}"

  # when
  build flaky

  # then
  section="$(block 0 2)"
  [ "${#section}" -le 3000 ]
  [[ "$section" == *"• 3x \`FooIT.xxxx"*"…\`"* ]]
  [[ "$section" == *"|#1>"* ]]
  [[ "$section" != *"|#2>"* ]]
  [[ "$output" == *'::warning::Shortened the Slack message "Flaky tests yesterday" to fit the block size limit'* ]]
}

@test "should list tests without GH issue links when the GH lookup fails" {
  # given
  top_tests "main|io.camunda.it.FooIT|shouldFoo|1|1|101:1:job"
  touch fixtures/gh.fail

  # when
  build failure

  # then
  [[ "$output" == *"::warning::gh issue lookup failed for FooIT"* ]]
  [[ "$(block 0 0)" == *"• 1x \`FooIT.shouldFoo\`"$'\n'* ]]
}

@test "should reject an unknown mode or a missing output file" {
  run bash "$BUILD" weekly "$MESSAGES"
  [ "$status" -eq 2 ]
  run bash "$BUILD" flaky
  [ "$status" -eq 2 ]
}

@test "should post the first message to the channel and the others in its thread" {
  # given
  echo '[{"text": "top", "blocks": []}, {"text": "a", "blocks": []}, {"text": "b", "blocks": []}]' > "$MESSAGES"

  # when
  SLACK_BOT_TOKEN=token SLACK_CHANNEL=C123 run bash "$POST" "$MESSAGES"

  # then
  [ "$status" -eq 0 ] || { echo "$output"; return 1; }
  [ "$(jq -c '.' fixtures/slack-posts.jsonl)" = '{"text":"top","blocks":[],"channel":"C123","unfurl_links":false,"unfurl_media":false}
{"text":"a","blocks":[],"channel":"C123","unfurl_links":false,"unfurl_media":false,"thread_ts":"1700000000.000100"}
{"text":"b","blocks":[],"channel":"C123","unfurl_links":false,"unfurl_media":false,"thread_ts":"1700000000.000100"}' ]
}

@test "should post all thread replies and warn when one fails" {
  # given
  echo '[{"text": "top", "blocks": []}, {"text": "a", "blocks": []}, {"text": "b", "blocks": []}]' > "$MESSAGES"
  echo "ratelimited" > fixtures/slack-reply.fail

  # when
  SLACK_BOT_TOKEN=token SLACK_CHANNEL=C123 run bash "$POST" "$MESSAGES"

  # then
  [ "$status" -eq 0 ] || { echo "$output"; return 1; }
  [ "$(wc -l < fixtures/slack-posts.jsonl)" -eq 3 ]
  [[ "$output" == *'::warning::Slack thread reply "a" failed: ratelimited'* ]]
  [[ "$output" == *'::warning::Slack thread reply "b" failed: ratelimited'* ]]
}

@test "should fail without thread replies when the top-level message fails" {
  # given
  echo '[{"text": "top", "blocks": []}, {"text": "a", "blocks": []}]' > "$MESSAGES"
  echo "not_in_channel" > fixtures/slack-top.fail

  # when
  SLACK_BOT_TOKEN=token SLACK_CHANNEL=C123 run bash "$POST" "$MESSAGES"

  # then
  [ "$status" -eq 1 ]
  [[ "$output" == *"::error::Slack chat.postMessage failed: not_in_channel"* ]]
  [ "$(wc -l < fixtures/slack-posts.jsonl)" -eq 1 ]
}
