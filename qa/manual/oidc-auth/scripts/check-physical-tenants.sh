#!/usr/bin/env bash
# Scenarios 2 and 4, M2M part. Run with SCENARIO=physical-tenants.
#
# Each M2M client is admin only on its own tenant, and only by the client-id claim that tenant
# configures. A token from an IdP that a tenant does not assign must be rejected with 401 or
# Unauthenticated (gRPC).
set -uo pipefail
source "$(dirname "$0")/lib.sh"
wait_ready

root="$(token idp-root idp-root-m2m)"
a="$(token idp-a idp-a-m2m)"
b="$(token idp-b idp-b-m2m)"

echo "Scenario 2: each tenant reads tokens with its own claims"
expect "default: idp-root token deploys over REST" 200 "$(rest_deploy "" "$root")"
expect "default: idp-root token deploys over gRPC" OK "$(grpc_deploy "" "$root")"
expect "tenanta: idp-a token deploys over REST (claim from tenant slot)" 200 "$(rest_deploy tenanta "$a")"
expect "tenanta: idp-a token deploys over gRPC (claim from tenant slot)" OK "$(grpc_deploy tenanta "$a")"
expect "tenantb: idp-b token deploys over REST" 200 "$(rest_deploy tenantb "$b")"
expect "tenantb: idp-b token deploys over gRPC" OK "$(grpc_deploy tenantb "$b")"

echo
echo "Scenario 4: a tenant rejects tokens from IdPs it does not assign"
expect "tenanta: idp-b token rejected over REST" 401 "$(rest_deploy tenanta "$b")"
expect "tenanta: idp-b token rejected over gRPC" Unauthenticated "$(grpc_deploy tenanta "$b")"
expect "tenanta: idp-root token rejected over REST" 401 "$(rest_deploy tenanta "$root")"
expect "tenanta: idp-root token rejected over gRPC" Unauthenticated "$(grpc_deploy tenanta "$root")"
expect "tenantb: idp-a token rejected over REST" 401 "$(rest_deploy tenantb "$a")"
expect "tenantb: idp-a token rejected over gRPC" Unauthenticated "$(grpc_deploy tenantb "$a")"
expect "tenantb: idp-root token rejected over REST" 401 "$(rest_deploy tenantb "$root")"
expect "tenantb: idp-root token rejected over gRPC" Unauthenticated "$(grpc_deploy tenantb "$root")"

finish
