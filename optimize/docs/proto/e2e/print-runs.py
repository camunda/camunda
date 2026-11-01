#!/usr/bin/env python3
"""Prints the agent run documents Optimize imported, read as JSON search results from stdin."""

import json
import sys

hits = json.load(sys.stdin)["hits"]["hits"]
if not hits:
    print("  none yet")
for hit in hits:
    r = hit["_source"]
    tools = ", ".join(
        f"{t.get('toolName') or t['toolFlowNodeId']}={t.get('count')}"
        for t in r.get("toolCallsByTool", [])
    )
    print(f"  run {r['agentInstanceId']}  process instance {r['processInstanceId']}")
    print(f"    agent:        {r.get('agentId')}  model: {r.get('provider')}/{r.get('model')}")
    print(f"    status:       {r.get('status')}  started {r.get('startDate')}  ended {r.get('endDate')}")
    print(f"    duration ms:  {r.get('durationMs')}")
    print(
        f"    tokens:       total {r.get('totalTokens')} (in {r.get('inputTokens')}, "
        f"out {r.get('outputTokens')}, cache write {r.get('cacheCreationTokens')}, "
        f"cache read {r.get('cacheReadTokens')})"
    )
    print(
        f"    model calls:  {r.get('modelCalls')} (max {r.get('maxModelCalls')})  "
        f"tool calls: {r.get('toolCalls')}  hit limit: {r.get('hitLimit')}"
    )
    print(f"    per tool:     {tools or '-'}")
    print(
        f"    enrichment:   process {r.get('processInstanceState')}, "
        f"incident {r.get('hasIncident')}, user tasks {r.get('processUserTaskCount')}"
    )
