#!/bin/bash
# Verifies physical tenant isolation in a running c8run started with a physical tenant, e.g.:
#   ./c8run start --physical-tenants pt1
#   PHYSICAL_TENANT=pt1 ./e2e_tests/physical_tenants_tests.sh
# Set CHECK_CONNECTORS=1 (with C8RUN_DIR pointing at the c8run directory) to also verify that the
# tenant's own connectors runtime serves its jobs and no other runtime does.
# Set TENANT_SECRET_VALUE to the value of C8RUN_E2E_SECRET stored with
# `c8run secrets --tenant <id> set` to verify the tenant resolves its own secret, not the default one.
# Use C8RUN_AUTH for the tenant login and C8RUN_DEFAULT_AUTH for the default tenant login.
set -euo pipefail

tenant="${PHYSICAL_TENANT:-pt1}"
base="${C8RUN_URL:-http://localhost:8080}"
auth="${C8RUN_AUTH:-demo:demo}"
default_auth="${C8RUN_DEFAULT_AUTH:-demo:demo}"
process_id="c8runPhysicalTenantIsolation"
grpc_address="${C8RUN_GRPC_URL:-http://localhost:26500}"
script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

fail() { printf "test failed: %s\n" "$1"; exit 1; }

count_definitions() {
        curl --silent --show-error --fail -u "$2" -X POST "$1/v2/process-definitions/search" \
                -H 'Content-Type: application/json' -H 'Accept: application/json' \
                --data-raw "{\"filter\":{\"processDefinitionId\":\"$process_id\"}}" | jq '.items | length'
}

printf "\nTest: physical tenant %s topology\n" "$tenant"
curl --silent --show-error --fail -u "$auth" "$base/physical-tenants/$tenant/v2/topology" >/dev/null \
        || fail "tenant $tenant is not reachable"

printf "\nTest: unknown physical tenant returns 404\n"
status="$(curl --silent -o /dev/null -w '%{http_code}' -u "$auth" "$base/physical-tenants/doesnotexist/v2/topology")"
[[ "$status" == "404" ]] || fail "expected 404 for unknown tenant, got $status"

default_before="$(count_definitions "$base" "$default_auth")"

if [[ "$auth" != "$default_auth" ]]; then
        printf "\nTest: tenant login is rejected by the default tenant\n"
        status="$(curl --silent -o /dev/null -w '%{http_code}' -u "$auth" "$base/v2/topology")"
        [[ "$status" == "401" ]] || fail "expected 401 for $tenant login on default, got $status"

        printf "\nTest: default login is rejected by physical tenant %s\n" "$tenant"
        status="$(curl --silent -o /dev/null -w '%{http_code}' -u "$default_auth" "$base/physical-tenants/$tenant/v2/topology")"
        [[ "$status" == "401" ]] || fail "expected 401 for default login on $tenant, got $status"
fi

# Calls gRPC Topology with raw HTTP/2 (an empty protobuf message is 5 zero bytes) and prints
# the grpc-status trailer, so no gRPC client tooling is needed.
grpc_status() {
        local frame
        frame="$(mktemp)"
        printf '\x00\x00\x00\x00\x00' >"$frame"
        curl --silent --http2-prior-knowledge -D - -o /dev/null -X POST \
                "$grpc_address/gateway_protocol.Gateway/Topology" \
                -H 'content-type: application/grpc' -H 'te: trailers' \
                -H "authorization: Basic $(printf '%s' "$1" | base64)" \
                -H "Camunda-Physical-Tenant: $2" \
                --data-binary "@$frame" | tr -d '\r' | sed -n 's/^grpc-status: //p' | tail -1
        rm -f "$frame"
}

printf "\nTest: gRPC Camunda-Physical-Tenant header routes to %s\n" "$tenant"
status="$(grpc_status "$auth" "$tenant")"
[[ "$status" == "0" ]] || fail "gRPC call to $tenant returned grpc-status '$status'"

printf "\nTest: gRPC rejects an unknown physical tenant\n"
status="$(grpc_status "$auth" doesnotexist)"
[[ "$status" != "0" ]] || fail "gRPC call to an unknown tenant succeeded"

if [[ "$auth" != "$default_auth" ]]; then
        printf "\nTest: gRPC rejects the default login on %s\n" "$tenant"
        status="$(grpc_status "$default_auth" "$tenant")"
        [[ "$status" == "16" ]] || fail "expected UNAUTHENTICATED (16) for default login on $tenant over gRPC, got '$status'"
fi

printf "\nTest: deploy to physical tenant %s\n" "$tenant"
curl --silent --show-error --fail -u "$auth" -X POST "$base/physical-tenants/$tenant/v2/deployments" \
        -H 'Accept: application/json' -F "resources=@$script_dir/physical_tenant_isolation.bpmn" >/dev/null \
        || fail "deployment to $tenant failed"

printf "\nTest: process is visible in %s\n" "$tenant"
for _ in $(seq 1 30); do
        visible="$(count_definitions "$base/physical-tenants/$tenant" "$auth")"
        [[ "$visible" -ge 1 ]] && break
        sleep 2
done
[[ "$visible" -ge 1 ]] || fail "deployed process not visible in $tenant"

printf "\nTest: process is isolated from the default tenant\n"
default_after="$(count_definitions "$base" "$default_auth")"
[[ "$default_after" == "$default_before" ]] || fail "process leaked into the default tenant"

if [[ -n "${TENANT_SECRET_VALUE:-}" ]]; then
        tenant_api="$base/physical-tenants/$tenant/v2"
        printf "\nTest: physical tenant %s resolves its own secrets\n" "$tenant"
        curl --silent --show-error --fail -u "$auth" -X POST "$tenant_api/deployments" \
                -F "resources=@$script_dir/centralized_secrets.bpmn" >/dev/null || fail "secrets process deployment failed"
        curl --silent --show-error --fail -u "$auth" -X POST "$tenant_api/process-instances" \
                -H 'Content-Type: application/json' --data-raw '{"processDefinitionId":"c8runSecretPresent"}' >/dev/null \
                || fail "secrets process instance creation failed"
        resolved=""
        for _ in $(seq 1 10); do
                resolved="$(curl --silent --show-error --fail -u "$auth" -X POST "$tenant_api/jobs/activation" \
                        -H 'Content-Type: application/json' \
                        --data-raw '{"type":"c8run-secret-present","worker":"c8run-e2e","timeout":30000,"maxJobsToActivate":1,"requestTimeout":5000}' \
                        | jq -r '.jobs[0].variables.resolvedSecret // empty')"
                [[ -n "$resolved" ]] && break
                sleep 2
        done
        [[ "$resolved" == "$TENANT_SECRET_VALUE" ]] || fail "$tenant resolved '$resolved' instead of its own secret"
fi

if [[ "${CHECK_CONNECTORS:-0}" == "1" ]]; then
        tenant_api="$base/physical-tenants/$tenant/v2"
        run_connector() {
                curl --silent -o /dev/null -w '%{http_code}' -u "$auth" -X POST "$tenant_api/process-instances" \
                        -H 'Content-Type: application/json' \
                        --data-raw "{\"processDefinitionId\":\"c8runPhysicalTenantConnector\",\"awaitCompletion\":true,\"requestTimeout\":$1}"
        }

        printf "\nTest: connector job in %s is served by its own connectors runtime\n" "$tenant"
        curl --silent --show-error --fail -u "$auth" -X POST "$tenant_api/deployments" \
                -F "resources=@$script_dir/physical_tenant_connector.bpmn" >/dev/null || fail "connector process deployment failed"
        status=""
        for _ in $(seq 1 10); do
                status="$(run_connector 30000)"
                [[ "$status" == "200" ]] && break
                sleep 3
        done
        [[ "$status" == "200" ]] || fail "connector job in $tenant did not complete (HTTP $status)"

        printf "\nTest: other connectors runtimes do not take %s jobs\n" "$tenant"
        pid_file="${C8RUN_DIR:?C8RUN_DIR is required with CHECK_CONNECTORS=1}/connectors-$tenant.process"
        [[ -f "$pid_file" ]] || fail "no connectors runtime for $tenant ($pid_file missing)"
        pid="$(head -1 "$pid_file")"
        if command -v taskkill >/dev/null 2>&1; then
                taskkill //F //T //PID "$pid" >/dev/null
        else
                kill "$pid"
        fi
        sleep 5
        status="$(run_connector 15000)"
        [[ "$status" != "200" ]] || fail "a connectors runtime of another tenant completed a $tenant job"
fi

printf "\nPhysical tenant tests passed.\n"
