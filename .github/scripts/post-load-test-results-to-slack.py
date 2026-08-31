#!/usr/bin/env python3
"""Build and post the daily load-test results table to Slack.

Renders a side-by-side metrics table — one column per variant — from the end-of-soak metric
snapshots and posts it to the reliability-testing Slack channel via an incoming webhook. Invoked
by the `notify-results` job of `.github/workflows/camunda-daily-load-tests.yml`.

Metric names, descriptions, and display formats are sourced from `queries.yaml` (the single
source of truth shared with `loadTestMetrics.sh`), so adding a metric there automatically adds a
row here. Metrics tied to secondary storage (e.g. importer/exporter lag) naturally render as n/a
for a no-secondary-storage variant.

The table has one column per entry in VARIANTS_JSON — adding a daily variant (a new matrix entry
in camunda-daily-load-tests.yml) needs no change here.

Required environment variables:
  VARIANTS_JSON       JSON array of {key, label, namespace, soakEndEpoch, results} objects, one
                      per variant, in display order. `results` is {metric_name: value}.
  BENCHMARK           Benchmark name, e.g. medic-daily-YYYY-MM-DD-<sha>-test.
  SLACK_WEBHOOK_URL   Incoming-webhook URL to post to.
  REPO                GitHub repo slug, e.g. camunda/camunda.
  RUN_ID              GitHub Actions run id (used to link the workflow run).

Optional:
  QUERIES_YAML        Path to queries.yaml. Default:
                      load-tests/docs/scripts/queries.yaml
  FLAMEGRAPH_LINKS    Pre-rendered Slack mrkdwn links to this run's flamegraph
                      artifacts, one bullet per line (e.g.
                      "• <url|name>\n• <url|name>"), built by the
                      `List flamegraph artifacts` step in the calling job via
                      actions/github-script + listWorkflowRunArtifacts. Empty
                      or unset omits the flamegraphs line.
"""
import json
import os
import socket
import time
import urllib.error
import urllib.request

import yaml

variants = json.loads(os.environ.get('VARIANTS_JSON') or '[]')
bench    = os.environ['BENCHMARK']
repo     = os.environ['REPO']
run_id   = os.environ['RUN_ID']
webhook  = os.environ['SLACK_WEBHOOK_URL']
flamegraph_links = os.environ.get('FLAMEGRAPH_LINKS', '').strip()

queries_yaml = os.environ.get('QUERIES_YAML', 'load-tests/docs/scripts/queries.yaml')

# Extract YYYY-MM-DD from medic-daily-YYYY-MM-DD-<sha>-test
parts = bench.split('-')
date  = '-'.join(parts[2:5]) if len(parts) >= 5 else bench

# Derive table rows from queries.yaml — single source of truth for
# metric names, descriptions, and display formats.
with open(queries_yaml) as f:
    queries = yaml.safe_load(f)['queries']


def fmt(v, q):
    if v is None:
        return 'n/a'
    try:
        n = float(v)
    except (TypeError, ValueError):
        return 'n/a'
    fmt_type = q.get('format', 'float')
    dec  = q.get('decimals', 2)
    unit = q.get('unit') or ''
    if fmt_type == 'integer':
        return f'{round(n):,}'
    elif fmt_type == 'percent':
        return f'{n:.{dec}f}{unit or "%"}'
    else:
        return f'{n:.{dec}f}{(" " + unit) if unit else ""}'


lw = max(len('Metric'), *(len(q['description']) for q in queries))
col_widths = {
    v['label']: max(len(v['label']), *(len(fmt(v['results'].get(q['name']), q)) for q in queries))
    for v in variants
}

header = f"{'Metric':<{lw}}" + ''.join(f"  {label:<{w}}" for label, w in col_widths.items())
sep    = '-' * (lw + sum(2 + w for w in col_widths.values()))
rows   = [
    f"{q['description']:<{lw}}" + ''.join(
        f"  {fmt(v['results'].get(q['name']), q):<{col_widths[v['label']]}}" for v in variants
    )
    for q in queries
]
table  = '\n'.join([header, sep] + rows)

# All variants' Grafana links share one time range, anchored to the first (primary) variant's
# soak end — mirrors prior behavior, which always anchored the range on gRPC's soak end.
soak_end = int(variants[0]['soakEndEpoch']) if variants else int(time.time())
from_ms  = (soak_end - 10800) * 1000
to_ms    = soak_end * 1000
run_url  = f'https://github.com/{repo}/actions/runs/{run_id}'

dash_links = ' · '.join(
    f"<https://dashboard.benchmark.camunda.cloud/d/zeebe-dashboard/zeebe?var-namespace={v['namespace']}&from={from_ms}&to={to_ms}|Grafana {v['label']}>"
    for v in variants
)

# Slack caps a section block's mrkdwn text.text at 3000 characters. The header is always
# short, but dash_links, the table, and (especially) the flamegraph-links list all grow with
# the number of daily variants -- the flamegraph list also grows with pods-per-variant and
# profiler events, so it's the one most likely to blow the cap (18 lines/2937 chars with two
# variants; 27 lines/4395 chars once a third variant tripled the profiled-pod count, which is
# exactly what produced the "400 invalid_blocks" failure this guards against). Split any text
# that would exceed the cap into multiple blocks on line boundaries, so a link/row is never
# cut mid-line.
SLACK_TEXT_LIMIT = 3000


def _chunk_lines(text, limit):
    lines = text.split('\n')
    chunks = []
    current = ''
    for line in lines:
        candidate = line if not current else f'{current}\n{line}'
        if len(candidate) > limit and current:
            chunks.append(current)
            current = line
        else:
            current = candidate
    if current:
        chunks.append(current)
    return chunks


def text_blocks(text, wrap=lambda chunk: chunk):
    """Split `text` into one or more section blocks, each within SLACK_TEXT_LIMIT after
    `wrap` is applied. Pass `wrap` to fence a table: wrapping happens per chunk, so a split
    table still renders as valid, self-contained code blocks instead of one chunk opening a
    fence whose closing ``` landed in a different block."""
    overhead = len(wrap(''))
    return [
        {'type': 'section', 'text': {'type': 'mrkdwn', 'text': wrap(chunk)}}
        for chunk in _chunk_lines(text, SLACK_TEXT_LIMIT - overhead)
    ]


blocks = text_blocks(
    f':bar_chart: *Daily Load Test Results — {date}*\nDuration: 3 h · <{run_url}|Workflow run>'
)

# Slack also rejects a section block with empty text — dash_links is empty only when variants
# is empty, which the calling workflow already gates on, but keep this defensive at the script
# level too (e.g. against direct/manual invocation with an empty VARIANTS_JSON).
if dash_links:
    blocks.extend(text_blocks(dash_links))

blocks.extend(text_blocks(table, wrap=lambda chunk: f'```\n{chunk}\n```'))

if flamegraph_links:
    blocks.extend(text_blocks(f':fire: *Flamegraphs:*\n{flamegraph_links}'))

payload = {'blocks': blocks}

body = json.dumps(payload).encode()
req  = urllib.request.Request(
    webhook, data=body, headers={'Content-Type': 'application/json'}
)
try:
    with urllib.request.urlopen(req, timeout=30) as resp:
        print(f'Slack response: {resp.status}')
except urllib.error.HTTPError as e:
    print(f'Slack HTTP error: {e.code} {e.read().decode()}')
    raise
except (urllib.error.URLError, socket.timeout) as e:
    print(f'Slack connection error: {e}')
    raise
