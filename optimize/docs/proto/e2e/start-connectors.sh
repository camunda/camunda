#!/usr/bin/env bash
# Starts the connectors runtime for the local agentic end-to-end test.
# The Anthropic API key is read from your shell and only passed into the
# container's environment; it never lands in the BPMN or Elasticsearch.
# In the AI Agent template, set the API key field to {{secrets.ANTHROPIC_API_KEY}}.
# Pass --mock to start without a key, for processes that call mock-anthropic.py.
set -euo pipefail

if [ "${1:-}" = "--mock" ]; then
  ANTHROPIC_API_KEY=unused
  export ANTHROPIC_API_KEY
elif [ -z "${ANTHROPIC_API_KEY:-}" ]; then
  read -rsp "Anthropic API key (input hidden): " ANTHROPIC_API_KEY
  echo
  export ANTHROPIC_API_KEY
fi

docker rm -f e2e-connectors >/dev/null 2>&1 || true
docker run -d --name e2e-connectors --network e2e -p 8086:8080 \
  -e CAMUNDA_CLIENT_MODE=self-managed \
  -e CAMUNDA_CLIENT_RESTADDRESS=http://e2e-camunda:8080 \
  -e CAMUNDA_CLIENT_GRPCADDRESS=http://e2e-camunda:26500 \
  -e CAMUNDA_CLIENT_AUTH_METHOD=basic \
  -e CAMUNDA_CLIENT_AUTH_USERNAME=demo \
  -e CAMUNDA_CLIENT_AUTH_PASSWORD=demo \
  -e ANTHROPIC_API_KEY \
  camunda/connectors-bundle:8.10.2 >/dev/null

echo "Connectors starting; follow with: docker logs -f e2e-connectors"
