#!/usr/bin/env python3
"""Prints imported agent runs per agent, read as an Elasticsearch aggregation response from stdin."""

import json
import sys

response = json.load(sys.stdin)
if "aggregations" not in response:
    sys.exit("  no optimize-agent-instance index yet: is Optimize running against this Elasticsearch?")
buckets = response["aggregations"]["agents"]["buckets"]
print(f"  {'agent':55} {'runs':>5} {'incid':>6} {'limit':>6} {'avg tok':>8} {'tool calls':>10} {'per tool':>9}")
for b in buckets:
    print(
        f"  {b['key']:55} {b['doc_count']:>5} {b['incidents']['doc_count']:>6} "
        f"{b['limits']['doc_count']:>6} {b['tokens']['value'] or 0:>8.0f} "
        f"{b['tools']['value']:>10.0f} {b['perTool']['count']['value']:>9.0f}"
    )
print("  tool calls = the run's total from the engine; per tool = counted from tool elements in the process")
