#!/usr/bin/env bash
# Scenario 1 (#65633), M2M part. Run with SCENARIO=multi-idp.
#
# Each M2M client is admin only by its client_id claim, which comes from the global
# client-id-claim. If a named provider does not inherit it, the principal is the token's subject,
# and the deployment is rejected with 403 or PermissionDenied (gRPC).
set -uo pipefail
source "$(dirname "$0")/lib.sh"
wait_ready

alpha="$(token idp-a idp-a-m2m)"
beta="$(token idp-b idp-b-m2m)"

expect "alpha (no claims set) M2M deploys over REST" 200 "$(rest_deploy "" "$alpha")"
expect "alpha (no claims set) M2M deploys over gRPC" OK "$(grpc_deploy "" "$alpha")"
expect "beta (only username-claim set) M2M deploys over REST" 200 "$(rest_deploy "" "$beta")"
expect "beta (only username-claim set) M2M deploys over gRPC" OK "$(grpc_deploy "" "$beta")"

finish
