#!/usr/bin/env bash
# Scenario 1 (#65633), M2M part. Run with CONFIG=multi-idp.
#
# The M2M clients of alpha and beta are admin only by their client_id claim, which comes from the
# global client-id-claim. The M2M client of gamma is admin only by its tenant_client claim, which
# gamma sets as its own client-id-claim. If a provider resolves the wrong claim, the deployment is
# rejected with 403, 400 or PermissionDenied (gRPC).
set -uo pipefail
source "$(dirname "$0")/lib.sh"
wait_ready

alpha="$(token idp-a idp-a-m2m)"
beta="$(token idp-b idp-b-m2m)"
gamma="$(token idp-c idp-c-m2m)"

expect "alpha (no claims set) M2M deploys over REST" 200 "$(rest_deploy "" "$alpha")"
expect "alpha (no claims set) M2M deploys over gRPC" OK "$(grpc_deploy "" "$alpha")"
expect "beta (only username-claim set) M2M deploys over REST" 200 "$(rest_deploy "" "$beta")"
expect "beta (only username-claim set) M2M deploys over gRPC" OK "$(grpc_deploy "" "$beta")"
expect "gamma (only client-id-claim set) M2M deploys over REST" 200 "$(rest_deploy "" "$gamma")"
expect "gamma (only client-id-claim set) M2M deploys over gRPC" OK "$(grpc_deploy "" "$gamma")"

finish
