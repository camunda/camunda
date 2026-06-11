#!/usr/bin/env python3
"""Build and post the daily load-test results table to Slack.

Renders a side-by-side gRPC vs REST metrics table from the end-of-soak metric
snapshots and posts it to the reliability-testing Slack channel via an incoming
webhook. Invoked by the `notify-results` job of
`.github/workflows/camunda-daily-load-tests.yml`.

Metric names, descriptions, and display formats are sourced from `queries.yaml`
(the single source of truth shared with `loadTestMetrics.sh`), so adding a metric
there automatically adds a row here.

Required environment variables:
  GRPC_RESULTS_JSON   JSON object of {metric_name: value} for the gRPC run.
  REST_RESULTS_JSON   JSON object of {metric_name: value} for the REST run.
  BENCHMARK           Benchmark name, e.g. medic-daily-YYYY-MM-DD-<sha>-test.
  SLACK_WEBHOOK_URL   Incoming-webhook URL to post to.
  REPO                GitHub repo slug, e.g. camunda/camunda.
  RUN_ID              GitHub Actions run id (used to link the workflow run).

Optional:
  QUERIES_YAML        Path to queries.yaml. Default:
                      load-tests/docs/scripts/queries.yaml
"""
import json
import os
import socket
import time
import urllib.error
import urllib.request

import yaml

grpc    = json.loads(os.environ.get('GRPC_RESULTS_JSON') or '{}')
rest    = json.loads(os.environ.get('REST_RESULTS_JSON') or '{}')
bench   = os.environ['BENCHMARK']
grpc_ns = f'c8-{bench}'
rest_ns = f'c8-{bench}-rest'
repo    = os.environ['REPO']
run_id  = os.environ['RUN_ID']
webhook = os.environ['SLACK_WEBHOOK_URL']

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
gw = max(len('gRPC'),   *(len(fmt(grpc.get(q['name']), q)) for q in queries))
rw = max(len('REST'),   *(len(fmt(rest.get(q['name']), q)) for q in queries))

header = f"{'Metric':<{lw}}  {'gRPC':<{gw}}  {'REST':<{rw}}"
sep    = '-' * (lw + 2 + gw + 2 + rw)
rows   = [
    f"{q['description']:<{lw}}  {fmt(grpc.get(q['name']), q):<{gw}}  {fmt(rest.get(q['name']), q):<{rw}}"
    for q in queries
]
table  = '\n'.join([header, sep] + rows)

soak_end  = int(os.environ.get('SOAK_END_EPOCH') or time.time())
from_ms   = (soak_end - 10800) * 1000
to_ms     = soak_end * 1000
grpc_dash = f'https://dashboard.benchmark.camunda.cloud/d/zeebe-dashboard/zeebe?var-namespace={grpc_ns}&from={from_ms}&to={to_ms}'
rest_dash = f'https://dashboard.benchmark.camunda.cloud/d/zeebe-dashboard/zeebe?var-namespace={rest_ns}&from={from_ms}&to={to_ms}'
run_url   = f'https://github.com/{repo}/actions/runs/{run_id}'

payload = {
    'blocks': [
        {
            'type': 'section',
            'text': {
                'type': 'mrkdwn',
                'text': f':bar_chart: *Daily Load Test Results — {date}*\nDuration: 3 h · <{run_url}|Workflow run>',
            },
        },
        {
            'type': 'section',
            'text': {
                'type': 'mrkdwn',
                'text': f'<{grpc_dash}|Grafana gRPC> · <{rest_dash}|Grafana REST>',
            },
        },
        {
            'type': 'section',
            'text': {
                'type': 'mrkdwn',
                'text': '```\n' + table + '\n```',
            },
        },
    ]
}

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
