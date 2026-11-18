# Renders the Slack messages of the flaky or failed test summary.
#
# Args: $mode ("flaky" or "failure"), $header (mrkdwn, may be empty),
#       $top_main and $top_stable (tests shown on the first branch and on the others),
#       $branches (first one goes in the top-level message, the others in its thread),
#       $tests (rows from build.sh, all tests of each branch: branch, count, tests_on_branch, rank,
#       shown, name, team, issue, medic, links).
#
# Output: array of {text, blocks, shortened}. The first is the top-level message, the others are
# thread replies, one per branch with tests. "shortened" is true when the section did not fit
# into one Slack section block and lost links or name characters.

# from and to are the Grafana time range of the query window: the last full days before today.
# The weekly range is the last 7 days, not now-1w/w, which is the previous calendar week.
def config:
  if $mode == "flaky" then
    {emoji: "🥇", title: "flaky tests yesterday", none: "no flaky tests yesterday",
     noun: "flaky test", from: "now-1d%2Fd", to: "now-1d%2Fd", text: "Flaky tests yesterday"}
  else
    {emoji: ":fire:", title: "failing tests last week", none: "no failing tests last week",
     noun: "failing test", from: "now-7d%2Fd", to: "now-1d%2Fd", text: "Failing tests last week"}
  end;

def top($branch): if $branch == $branches[0] then $top_main else $top_stable end;

def plural($n; $word): if $n == 1 then "1 \($word)" else "\($n) \($word)s" end;

# Slack's limit for the text of one section block
def max_section_length: 3000;

def escape: gsub("&"; "&amp;") | gsub("<"; "&lt;") | gsub(">"; "&gt;");

def truncate($n): if $n > 0 and length > $n then .[:$n - 1] + "…" else . end;

def grafana_url($branch):
  "https://dashboard.int.camunda.com/d/ae2j69npxh3b4f/flaky-tests-camunda-camunda-monorepo"
  + "?orgId=1&from=\(config.from)&to=\(config.to)&timezone=browser&var-branch=\($branch | @uri)";

# The job when its ID is known, else the run attempt, marked "(run)" so readers know to look for
# the job in the run
def link:
  "https://github.com/camunda/camunda/actions/runs/\(.run_id)" as $run
  | if .job_id then "<\($run)/job/\(.job_id)|#\(.n)>"
    else "<\($run)/attempts/\(.run_attempt)|#\(.n) (run)>"
    end;

# "↳ `job-a` <url|#1> <url|#2> · `job-b` <url|#3>", jobs in the order of their first link
def links_line($max_links):
  [.links[:$max_links] | to_entries[] | .value + {n: (.key + 1)}]
  | reduce .[] as $link ([];
      (map(.job) | index($link.job_name)) as $i
      | if $i == null then . + [{job: $link.job_name, links: [$link]}] else .[$i].links += [$link] end)
  | if length == 0 then empty
    else "      ↳ " + (map("`\(.job | escape)` " + (.links | map(link) | join(" "))) | join(" · "))
    end;

def test_lines($max_links; $max_name):
  "• \(.count)x `\(.name | truncate($max_name) | escape)`"
  + (if .issue then " (<https://github.com/camunda/camunda/issues/\(.issue)|GH issue>)" else "" end),
  links_line($max_links);

def team_key: .team // "";

def team_label: if . == "" then "No owner" else sub("^@camunda/"; "") end;

# One group per team with a shown test or a due medic ping: teams with shown tests first, by their
# highest count, "No owner" last among them. A due test that is not shown is named in the header,
# so its team still gets the ping.
def team_groups($rows):
  [$rows[] | select(.shown or .medic)]
  | group_by(team_key)
  | map((.[0] | team_key) as $key | {
      key: $key,
      shown: (map(select(.shown)) | sort_by(.rank)),
      also_due: map(select(.medic and (.shown | not))),
      medic: (map(.medic // empty) | first),
      total: ([$rows[] | select(team_key == $key)] | length)
    })
  | sort_by((.shown == []), (.key == ""), -(.shown | map(.count) | max // 0), .key);

# Due tests named in a team header. More would use the section space of the shown tests and of
# the medic pings of other teams.
def max_also_due: 3;

def team_header($max_name):
  "*\(.key | team_label | escape)* (\(.shown | length) of \(plural(.total; "test")))"
  + (if .medic then
       " :arrow_right:  \(.medic)"
       + (if .also_due == [] then ""
          else " (also due: "
            + (.also_due[:max_also_due] | map("`\(.name | truncate($max_name) | escape)` \(.count)x") | join(", "))
            + (if (.also_due | length) > max_also_due then ", +\((.also_due | length) - max_also_due) more" else "" end)
            + ")"
          end)
     else "" end);

def section($branch; $max_links; $max_name):
  [$tests[] | select(.branch == $branch)] as $rows
  | "\(config.emoji) *`\($branch)`*: "
    + (if $rows == [] then config.none else "top \(top($branch)) \(config.title)" end)
    + " (<\(grafana_url($branch))|Grafana>)"
    + (team_groups($rows)
       | map("\n\n" + ([team_header($max_name), (.shown[] | test_lines($max_links; $max_name))] | join("\n")))
       | join(""));

# The section with as many links and as much of each test name as fit
def fitted_section($branch):
  [section($branch; 3; 0), section($branch; 1; 0), section($branch; 1; 80), section($branch; 0; 80)]
  | (map(select(length <= max_section_length)) | first) as $fit
  | if $fit == null then {text: (last | .[:max_section_length - 1] + "…"), shortened: true}
    else {text: $fit, shortened: ($fit != first)}
    end;

def tests_on($branch):
  [$tests[] | select(.branch == $branch) | .tests_on_branch] | first // 0;

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
