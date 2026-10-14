#!/usr/bin/env bash
# Shared helpers for the check scripts. Source this file, do not run it.

KEYCLOAK=http://localhost:18080
CAMUNDA=http://localhost:8080
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../../../.." && pwd)"
FAILURES=0

# wait_ready: waits until Camunda is ready. "docker compose up --wait" does not wait for it.
wait_ready() {
  local i
  for i in $(seq 1 60); do
    curl -fsS -o /dev/null http://localhost:9600/actuator/health/readiness 2>/dev/null && return 0
    sleep 5
  done
  echo "Camunda is not ready after 5 minutes. See: docker logs oidc-auth-camunda" >&2
  exit 1
}

# token <realm> <client-id>: prints a client credentials access token.
token() {
  curl -fsS "$KEYCLOAK/realms/$1/protocol/openid-connect/token" \
    -d grant_type=client_credentials -d client_id="$2" -d client_secret="$2-secret" |
    sed -E 's/.*"access_token":"([^"]+)".*/\1/'
}

# bpmn <process-id>: writes a minimal process to a temp file and prints its path.
bpmn() {
  local file
  file="$(mktemp -d)/$1.bpmn"
  sed "s/PROCESS_ID/$1/" "$SCRIPT_DIR/process.bpmn" >"$file"
  echo "$file"
}

# rest_deploy <tenant or ""> <token>: deploys a process over REST and prints the HTTP status.
rest_deploy() {
  local prefix="" id file
  [ -n "$1" ] && prefix="/physical-tenants/$1"
  id="rest_${1:-default}_$RANDOM"
  file="$(bpmn "$id")"
  curl -s -o /dev/null -w '%{http_code}' -H "Authorization: Bearer $2" \
    -F "resources=@$file;filename=$id.bpmn" "$CAMUNDA$prefix/v2/deployments"
}

# grpc_deploy <tenant or ""> <token>: deploys a process over gRPC and prints the gRPC status.
grpc_deploy() {
  local id file content header=()
  [ -n "$1" ] && header=(-H "Camunda-Physical-Tenant: $1")
  id="grpc_${1:-default}_$RANDOM"
  file="$(bpmn "$id")"
  content="$(base64 <"$file" | tr -d '\n')"
  docker run --rm --network container:oidc-auth-keycloak \
    -v "$REPO_ROOT/zeebe/gateway-protocol/src/main/proto:/proto:ro" \
    fullstorydev/grpcurl:v1.9.3 -plaintext -import-path /proto -proto gateway.proto \
    -H "authorization: Bearer $2" ${header[@]+"${header[@]}"} \
    -d "{\"resources\":[{\"name\":\"$id.bpmn\",\"content\":\"$content\"}]}" \
    localhost:26500 gateway_protocol.Gateway/DeployResource 2>&1 |
    grep -oE 'Code: [A-Za-z]+|"key"' | head -1 |
    sed -E 's/Code: //; s/"key"/OK/'
}

# expect <description> <expected> <actual>
expect() {
  if [ "$2" = "$3" ]; then
    printf 'PASS  %-70s %s\n' "$1" "$3"
  else
    printf 'FAIL  %-70s expected %s, got %s\n' "$1" "$2" "$3"
    FAILURES=$((FAILURES + 1))
  fi
}

finish() {
  echo
  if [ "$FAILURES" -eq 0 ]; then echo "All checks passed."; else echo "$FAILURES check(s) failed."; fi
  [ "$FAILURES" -eq 0 ]
}
