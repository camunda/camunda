#!/usr/bin/env bash
#
# Fetches the current MCP tools schema from a running local Camunda cluster
# and prints it to STDOUT in the snapshot format expected by
# ToolsSchemaRegressionTest.
#
# Prerequisites:
#   - A local Camunda cluster running with the MCP gateway enabled, serving
#     the cluster MCP server at http://localhost:8080/mcp/cluster (override
#     via the CAMUNDA_MCP_ENDPOINT env var).
#   - The cluster is reachable without authentication (run locally with auth
#     disabled).
#   - curl and jq on PATH.
#
# Usage (from the repository root):
#   ./gateways/gateway-mcp/src/test/resources/schema/update-tools-schema-snapshot.sh \
#     > gateways/gateway-mcp/src/test/resources/schema/tools-schema-snapshot.json
#
# Or from the gateway-mcp module directory:
#   ./src/test/resources/schema/update-tools-schema-snapshot.sh \
#     > src/test/resources/schema/tools-schema-snapshot.json

set -euo pipefail

ENDPOINT="${CAMUNDA_MCP_ENDPOINT:-http://localhost:8080/mcp/cluster}"

if ! curl -sS --max-time 5 -o /dev/null "$ENDPOINT" 2>/dev/null; then
  echo "ERROR: Cannot reach MCP endpoint at $ENDPOINT" >&2
  echo "Make sure a local Camunda cluster is running with the MCP gateway enabled." >&2
  exit 1
fi

curl -fsS -X POST "$ENDPOINT" \
  -H 'Content-Type: application/json' \
  -H 'Accept: application/json, text/event-stream' \
  --data '{"jsonrpc":"2.0","id":1,"method":"tools/list"}' \
  | jq '{tools: .result.tools}'
