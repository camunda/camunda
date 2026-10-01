#!/bin/bash
# Verifies physical tenant isolation in a running c8run started with a physical tenant, e.g.:
#   ./c8run start --physical-tenants pt1
#   PHYSICAL_TENANT=pt1 ./e2e_tests/physical_tenants_tests.sh
# Use C8RUN_AUTH for the tenant login and C8RUN_DEFAULT_AUTH for the default tenant login.
set -euo pipefail

tenant="${PHYSICAL_TENANT:-pt1}"
base="${C8RUN_URL:-http://localhost:8080}"
auth="${C8RUN_AUTH:-demo:demo}"
default_auth="${C8RUN_DEFAULT_AUTH:-demo:demo}"
process_id="c8runPhysicalTenantIsolation"
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

printf "\nPhysical tenant tests passed.\n"
