#!/usr/bin/env bash
# Cross-tenant API smoke for the Physical-Tenant Security PoC.
#
# Both supported PT API URL schemes (spec D7) are exercised:
#   * Webapp-aligned: /physical-tenant/<id>/v2/whoami   — inside the per-tenant
#     session cookie's Path scope (cookie auth or bearer auth both work).
#   * Direct API client: /v2/physical-tenants/<id>/whoami — outside the cookie's
#     Path scope (bearer-only in practice).
#
# For each scheme we run the same 5-cell matrix:
#   tenant-A token → /<scheme>/tenanta/whoami → 200
#   tenant-A token → /<scheme>/default/whoami → 403
#   default token  → /<scheme>/default/whoami → 200
#   default token  → /<scheme>/tenanta/whoami → 403
#   no token       → /<scheme>/tenanta/whoami → 401
#
# Requires: ./pt-poc-idp.sh and ./pt-poc-oc.sh running.
# Dependencies: curl, jq.

set -u

OC="http://localhost:8080"
KC_TENANTA="http://localhost:8082/realms/tenanta/protocol/openid-connect/token"
KC_DEFAULT="http://localhost:8081/realms/default/protocol/openid-connect/token"

# URL templates — %s gets substituted with the target tenant id.
WEBAPP_URL_TEMPLATE="/physical-tenant/%s/v2/whoami"
APICLIENT_URL_TEMPLATE="/v2/physical-tenants/%s/whoami"

token() {
  local url="$1" client_id="$2" client_secret="$3" user="$4" pass="$5"
  curl -fsS -X POST "$url" \
    -d "grant_type=password" \
    -d "client_id=$client_id" \
    -d "client_secret=$client_secret" \
    -d "username=$user" \
    -d "password=$pass" \
    | jq -r .access_token
}

# Runs the OAuth2 authorization-code flow against a webapp chain by entering at $entry_url and
# completing Keycloak's login form with $username/$password. Session cookies land in $jar.
#
# The shape differs between PT (prefixed) chains and CSL's standard OidcWebapp chain: PT chains
# forward directly to Keycloak after one redirect; CSL's chain has its own redirect dance to
# /oauth2/authorization/<regId>. The helper handles both by following redirects in a loop and only
# submitting Keycloak credentials when it lands on KC's login form.
oauth_login() {
  local entry_url="$1" user="$2" pass="$3" jar="$4"
  rm -f "$jar"
  local loc="$entry_url"
  local response_url
  # Follow redirects until we either land on Keycloak's login form or come back to OC.
  while true; do
    response_url=$(curl -sS -c "$jar" -b "$jar" -o /tmp/oauth-step.body -w "%{redirect_url}" "$loc")
    if [ -z "$response_url" ]; then
      break  # not a redirect; we landed on a page (KC login form, OC final page, etc.)
    fi
    loc="$response_url"
  done
  # If we landed on Keycloak's login form, submit credentials.
  if grep -q 'action=.*login-actions/authenticate' /tmp/oauth-step.body; then
    local form
    form=$(grep -oE 'action="[^"]+"' /tmp/oauth-step.body | head -1 \
           | sed -E 's/^action="(.*)"$/\1/' | sed 's/\&amp;/\&/g')
    local cb
    cb=$(curl -sS -c "$jar" -b "$jar" -o /dev/null -w "%{redirect_url}" -X POST "$form" \
          -d "username=$user" -d "password=$pass" -d "credentialId=")
    # Follow callback chain to completion.
    while [ -n "$cb" ]; do
      cb=$(curl -sS -c "$jar" -b "$jar" -o /dev/null -w "%{redirect_url}" "$cb")
    done
  fi
  rm -f /tmp/oauth-step.body
}

call() {
  local label="$1" token="$2" path="$3" expected="$4"
  local status
  if [[ -n "$token" ]]; then
    status=$(curl -sS -o /tmp/pt-poc-api-body -w "%{http_code}" -H "Authorization: Bearer $token" "$OC$path")
  else
    status=$(curl -sS -o /tmp/pt-poc-api-body -w "%{http_code}" "$OC$path")
  fi
  printf "%-22s %s %s  " "$label" "$status" "$path"
  if [[ "$status" == "$expected" ]]; then
    echo "OK"
  else
    echo "FAIL (expected $expected)"
    cat /tmp/pt-poc-api-body
    echo
  fi
}

call_with_cookies() {
  local label="$1" jar="$2" path="$3" expected="$4"
  local status
  status=$(curl -sS -b "$jar" -o /tmp/pt-poc-api-body -w "%{http_code}" "$OC$path")
  printf "%-30s %s %s  " "$label" "$status" "$path"
  if [[ "$status" == "$expected" ]]; then
    echo "OK"
  else
    echo "FAIL (expected $expected)"
    cat /tmp/pt-poc-api-body
    echo
  fi
}

matrix() {
  local label="$1" template="$2"
  echo "=== Cross-tenant matrix — $label ==="
  call "tenanta -> tenanta" "$TA"  "$(printf "$template" tenanta)" 200
  call "tenanta -> default" "$TA"  "$(printf "$template" default)" 403
  call "default -> default" "$DEF" "$(printf "$template" default)" 200
  call "default -> tenanta" "$DEF" "$(printf "$template" tenanta)" 403
  call "no token -> tenanta" ""    "$(printf "$template" tenanta)" 401
  echo
}

echo "=== Acquiring tokens ==="
TA=$(token "$KC_TENANTA" camunda-pt-tenanta-client tenanta-secret bob bob)
DEF=$(token "$KC_DEFAULT" camunda-pt-default-client default-secret alice alice)
# Default tenant's SECOND assigned IdP (Task 17): the tenanta Keycloak realm is shared between
# tenant A's primary client and default's secondary client. Same issuer; tenant separation
# rides on the audience claim emitted by each client at the IdP.
DVTA=$(token "$KC_TENANTA" camunda-pt-default-via-tenanta-client default-via-tenanta-secret bob bob)
echo "tenanta token:             ${TA:0:40}..."
echo "default token:             ${DEF:0:40}..."
echo "default-via-tenanta token: ${DVTA:0:40}..."
echo

matrix "Webapp-relative API Path" "$WEBAPP_URL_TEMPLATE"
matrix "API Path"    "$APICLIENT_URL_TEMPLATE"

# /v2/whoami is served by CSL's OidcApi chain (Stage 2). CSL's audience validator on each
# registration enforces the registration's declared audiences list. Since tenanta's root-level
# provider declares audiences=[pt-tenanta-aud] and default declares audiences=[pt-default-aud],
# any valid token issued by either IdP whose `aud` matches its own provider's declared list is
# accepted at /v2/whoami — including tenanta tokens. /v2/whoami is the cluster's API; it is NOT
# tenant-scoped, so per-tenant authorization is the PT chains' job (see the prefixed cells above).
# Cross-tenant prevention via audience only applies to PT-prefixed URLs.
echo "=== /v2/whoami (cluster API via CSL OidcApi) ==="
call "default token" "$DEF" "/v2/whoami" 200
call "tenanta token" "$TA"  "/v2/whoami" 200
call "no token"      ""    "/v2/whoami" 401
echo

# Session-based cross-tenant. CSL's OidcApi at /v2/whoami is bearer-only — it does not read
# the session cookie — so a session-only request lands 401 even with a valid default-tenant
# session. The PT-prefixed URLs are still session-or-bearer via the PT chains; tenanta's chain
# reads its own cookie name only, so default's session cookie does not authenticate there
# either (401 anonymous).
echo "=== Session cross-tenant (default session; PT-prefixed targets) ==="
DEF_JAR=/tmp/pt-poc-default-jar.txt
oauth_login "$OC/oauth2/authorization/oidc" alice alice "$DEF_JAR"
call_with_cookies "default session -> /v2/whoami (CSL bearer-only)"  "$DEF_JAR" "/v2/whoami"                          401
call_with_cookies "default session -> tenanta webapp-aligned"        "$DEF_JAR" "/physical-tenant/tenanta/v2/whoami"  401
call_with_cookies "default session -> tenanta API-client URL"        "$DEF_JAR" "/v2/physical-tenants/tenanta/whoami" 401
rm -f "$DEF_JAR"
echo

# Audience-based isolation when an IdP is shared between PTs (spec D8, Task 17). The token from
# the default-via-tenanta client is issued by the SAME realm (tenanta, :8082) as TA — so the
# issuer-allowlist check on tenant A's chain would pass it. The audience-allowlist check rejects
# it: this token carries aud=pt-default-via-tenanta-aud, which is in default's expected list but
# NOT in tenant A's.
echo "=== Audience isolation (shared tenanta realm; aud-based PT separation) ==="
call "dvta -> default (webapp)"    "$DVTA" "$(printf "$WEBAPP_URL_TEMPLATE" default)"    200
call "dvta -> default (apiclient)" "$DVTA" "$(printf "$APICLIENT_URL_TEMPLATE" default)" 200
call "dvta -> default (unpref.)"   "$DVTA" "/v2/whoami"                                  200
call "dvta -> tenanta (webapp)"    "$DVTA" "$(printf "$WEBAPP_URL_TEMPLATE" tenanta)"    403
call "dvta -> tenanta (apiclient)" "$DVTA" "$(printf "$APICLIENT_URL_TEMPLATE" tenanta)" 403
echo

rm -f /tmp/pt-poc-api-body
