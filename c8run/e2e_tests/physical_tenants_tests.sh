#!/bin/bash
# Verifies physical tenant isolation in a running c8run started with a physical tenant, e.g.:
#   ./c8run start --physical-tenants pt1
#   PHYSICAL_TENANT=pt1 ./e2e_tests/physical_tenants_tests.sh
# Set CHECK_CONNECTORS=1 (with C8RUN_DIR pointing at the c8run directory) to also verify that the
# tenant's own connectors runtime serves its jobs and no other runtime does.
# Set TENANT_SECRET_VALUE to the value of C8RUN_E2E_SECRET stored with
# `c8run secrets --physical-tenant <id> set` to verify the physical tenant resolves its own secret, not the default one.
# Set DEFAULT_SECRET_VALUE to the default tenant's C8RUN_E2E_SECRET to verify it too.
# Set CHECK_MISSING_SECRET=1 to verify an unset C8RUN_E2E_MISSING raises a SECRET_RESOLUTION_ERROR
# incident in the physical tenant.
# Set SECRETS_LIST_EXPECT to a secret name (with C8RUN_DIR) to verify `c8run secrets list` prints it
# and no secret value.
# Set CONNECTOR_ENV_PREFIX to C8RUN_SECRETS_ENV_PREFIX (with C8RUN_DIR, Linux only) to verify each
# connectors runtime's environment holds no other tenant's secret variables.
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

if curl --version | grep -qw HTTP2; then
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
else
        printf "\nSkipping gRPC tests: this curl has no HTTP/2 support\n"
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

# Runs the secrets process in a tenant and prints the secret its job received.
resolve_secret() {
        local api="$1" login="$2" resolved=""
        curl --silent --show-error --fail -u "$login" -X POST "$api/deployments" \
                -F "resources=@$script_dir/centralized_secrets.bpmn" >/dev/null || fail "secrets process deployment to $api failed"
        curl --silent --show-error --fail -u "$login" -X POST "$api/process-instances" \
                -H 'Content-Type: application/json' --data-raw '{"processDefinitionId":"c8runSecretPresent"}' >/dev/null \
                || fail "secrets process instance creation in $api failed"
        for _ in $(seq 1 10); do
                resolved="$(curl --silent --show-error --fail -u "$login" -X POST "$api/jobs/activation" \
                        -H 'Content-Type: application/json' \
                        --data-raw '{"type":"c8run-secret-present","worker":"c8run-e2e","timeout":30000,"maxJobsToActivate":1,"requestTimeout":5000}' \
                        | jq -r '.jobs[0].variables.resolvedSecret // empty')"
                [[ -n "$resolved" ]] && break
                sleep 2
        done
        printf '%s' "$resolved"
}

if [[ -n "${TENANT_SECRET_VALUE:-}" ]]; then
        printf "\nTest: physical tenant %s resolves its own secrets\n" "$tenant"
        resolved="$(resolve_secret "$base/physical-tenants/$tenant/v2" "$auth")"
        [[ "$resolved" == "$TENANT_SECRET_VALUE" ]] || fail "$tenant resolved '$resolved' instead of its own secret"
fi

if [[ -n "${DEFAULT_SECRET_VALUE:-}" ]]; then
        printf "\nTest: the default tenant resolves its own secrets\n"
        resolved="$(resolve_secret "$base/v2" "$default_auth")"
        [[ "$resolved" == "$DEFAULT_SECRET_VALUE" ]] || fail "default tenant resolved '$resolved' instead of its own secret"
fi

if [[ "${CHECK_MISSING_SECRET:-0}" == "1" ]]; then
        tenant_api="$base/physical-tenants/$tenant/v2"
        printf "\nTest: a missing secret in %s raises a secret resolution incident\n" "$tenant"
        curl --silent --show-error --fail -u "$auth" -X POST "$tenant_api/deployments" \
                -F "resources=@$script_dir/centralized_secrets.bpmn" >/dev/null || fail "secrets process deployment failed"
        instance_key="$(curl --silent --show-error --fail -u "$auth" -X POST "$tenant_api/process-instances" \
                -H 'Content-Type: application/json' --data-raw '{"processDefinitionId":"c8runSecretMissing"}' \
                | jq -r '.processInstanceKey')"
        jobs="$(curl --silent --show-error --fail -u "$auth" -X POST "$tenant_api/jobs/activation" \
                -H 'Content-Type: application/json' \
                --data-raw '{"type":"c8run-secret-missing","worker":"c8run-e2e","timeout":30000,"maxJobsToActivate":1,"requestTimeout":5000}' \
                | jq '.jobs | length')"
        [[ "$jobs" == "0" ]] || fail "a job with a missing secret was activated in $tenant"
        incident=""
        for _ in $(seq 1 15); do
                incident="$(curl --silent --show-error --fail -u "$auth" -X POST "$tenant_api/process-instances/$instance_key/incidents/search" \
                        -H 'Content-Type: application/json' --data-raw '{}' \
                        | jq -r '.items | any(.errorType == "SECRET_RESOLUTION_ERROR" and .state == "ACTIVE")')"
                [[ "$incident" == "true" ]] && break
                sleep 2
        done
        [[ "$incident" == "true" ]] || fail "missing secret did not raise a SECRET_RESOLUTION_ERROR incident in $tenant"
fi

if [[ -n "${SECRETS_LIST_EXPECT:-}" ]]; then
        printf "\nTest: c8run secrets list prints names and no values\n"
        c8run="${C8RUN_DIR:?C8RUN_DIR is required with SECRETS_LIST_EXPECT}/c8run"
        [[ -x "$c8run" ]] || c8run="$c8run.exe"
        names="$("$c8run" secrets list --username "${default_auth%%:*}" --password "${default_auth#*:}")" \
                || fail "c8run secrets list failed"
        grep -qx "$SECRETS_LIST_EXPECT" <<<"$names" || fail "c8run secrets list did not print $SECRETS_LIST_EXPECT: $names"
        for value in "${DEFAULT_SECRET_VALUE:-}" "${TENANT_SECRET_VALUE:-}"; do
                if [[ -n "$value" ]] && grep -qF "$value" <<<"$names"; then
                        fail "c8run secrets list printed a secret value"
                fi
        done
fi

if [[ -n "${CONNECTOR_ENV_PREFIX:-}" ]]; then
        if [[ "$(uname -s)" == "Linux" ]]; then
                printf "\nTest: each connectors runtime sees only its own tenant's secret variables\n"
                tenant_upper="$(printf '%s' "$tenant" | tr '[:lower:]' '[:upper:]')"
                runtime_env() {
                        local pid_file="${C8RUN_DIR:?C8RUN_DIR is required with CONNECTOR_ENV_PREFIX}/$1"
                        [[ -f "$pid_file" ]] || fail "$pid_file is missing"
                        tr '\0' '\n' <"/proc/$(head -1 "$pid_file")/environ"
                }
                default_env="$(runtime_env connectors.process)"
                tenant_env="$(runtime_env "connectors-$tenant.process")"
                grep -qi "^${CONNECTOR_ENV_PREFIX}DEFAULT_" <<<"$default_env" || fail "the default connectors runtime lacks its own secret variables"
                ! grep -qi "^${CONNECTOR_ENV_PREFIX}${tenant_upper}_" <<<"$default_env" || fail "the default connectors runtime sees $tenant's secret variables"
                grep -qi "^${CONNECTOR_ENV_PREFIX}${tenant_upper}_" <<<"$tenant_env" || fail "$tenant's connectors runtime lacks its own secret variables"
                ! grep -qi "^${CONNECTOR_ENV_PREFIX}DEFAULT_" <<<"$tenant_env" || fail "$tenant's connectors runtime sees the default tenant's secret variables"
        else
                printf "\nSkipping connectors runtime environment test: it reads /proc, which only Linux has\n"
        fi
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
