# Renders the Slack messages of the flaky or failed test summary.
#
# Args: $mode ("flaky" or "failure"), $header (mrkdwn, may be empty), $top (tests per branch),
#       $branches (first one goes in the top-level message, the others in its thread),
#       $tests (rows from build.sh: branch, count, tests_on_branch, name, issue, medic, links).
#
# Output: array of {text, blocks, shortened}. The first is the top-level message, the others are
# thread replies, one per branch with tests. "shortened" is true when the section did not fit
# into one Slack section block and lost links or name characters.

def config:
  if $mode == "flaky" then
    {emoji: "🥇", title: "top \($top) flaky tests yesterday", none: "no flaky tests yesterday",
     noun: "flaky test", window: "now-1d%2Fd", text: "Flaky tests yesterday"}
  else
    {emoji: ":fire:", title: "top \($top) failing tests last week", none: "no failing tests last week",
     noun: "failing test", window: "now-1w%2Fw", text: "Failing tests last week"}
  end;

# Slack's limit for the text of one section block
def max_section_length: 3000;

def escape: gsub("&"; "&amp;") | gsub("<"; "&lt;") | gsub(">"; "&gt;");

def truncate($n): if $n > 0 and length > $n then .[:$n - 1] + "…" else . end;

def grafana_url($branch):
  "https://dashboard.int.camunda.com/d/ae2j69npxh3b4f/flaky-tests-camunda-camunda-monorepo"
  + "?orgId=1&from=\(config.window)&to=\(config.window)&timezone=browser&var-branch=\($branch | @uri)";

def link_url:
  "https://github.com/camunda/camunda/actions/runs/\(.run_id)/attempts/\(.run_attempt)";

# "↳ `job-a` <url|#1> <url|#2> · `job-b` <url|#3>", jobs in the order of their first link
def links_line($max_links):
  [.links[:$max_links] | to_entries[] | .value + {n: (.key + 1)}]
  | reduce .[] as $link ([];
      (map(.job) | index($link.job_name)) as $i
      | if $i == null then . + [{job: $link.job_name, links: [$link]}] else .[$i].links += [$link] end)
  | if length == 0 then empty
    else "      ↳ " + (map("`\(.job | escape)` " + (.links | map("<\(link_url)|#\(.n)>") | join(" "))) | join(" · "))
    end;

def test_lines($max_links; $max_name):
  "• \(.count)x `\(.name | truncate($max_name) | escape)`"
  + (if .issue then " (<https://github.com/camunda/camunda/issues/\(.issue)|GH issue>)" else "" end),
  links_line($max_links),
  (if .medic then ":arrow_right:  \(.medic)" else empty end);

def section($branch; $max_links; $max_name):
  [$tests[] | select(.branch == $branch)] as $rows
  | "\(config.emoji) *`\($branch)`*: "
    + (if $rows == [] then config.none else config.title end)
    + " (<\(grafana_url($branch))|Grafana>)"
    + ([$rows[] | test_lines($max_links; $max_name)] | map("\n" + .) | join(""));

# The section with as many links and as much of each test name as fit
def fitted_section($branch):
  [section($branch; 3; 0), section($branch; 1; 0), section($branch; 1; 80), section($branch; 0; 80)]
  | (map(select(length <= max_section_length)) | first) as $fit
  | if $fit == null then {text: (last | .[:max_section_length - 1] + "…"), shortened: true}
    else {text: $fit, shortened: ($fit != first)}
    end;

def tests_on($branch):
  [$tests[] | select(.branch == $branch) | .tests_on_branch] | first // 0;

def plural($n; $word): if $n == 1 then "1 \($word)" else "\($n) \($word)s" end;

def section_block: {type: "section", text: {type: "mrkdwn", text: .}};

($branches[0]) as $main
| ($branches[1:]) as $others
| fitted_section($main) as $main_section
| [
    {
      text: config.text,
      blocks: (
        (if $header != "" then [($header | section_block), {type: "divider"}] else [] end)
        + [$main_section.text | section_block]
        + (if $others == [] then []
           else [{
             type: "context",
             elements: [{
               type: "mrkdwn",
               text: (":thread: Stable branches in the thread: "
                 + ($others | map(tests_on(.) as $n
                     | "`\(.)` " + (if $n == 0 then "(none)" else "(\(plural($n; config.noun)))" end))
                   | join(" · ")))
             }]
           }]
           end)
      ),
      shortened: $main_section.shortened
    }
  ]
  + [
      $others[]
      | select(tests_on(.) > 0)
      | . as $branch
      | fitted_section($branch) as $section
      | {
          text: "\(config.text) on \($branch)",
          blocks: [$section.text | section_block],
          shortened: $section.shortened
        }
    ]
