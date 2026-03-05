# Client ID Metadata Documents (CIMD) for MCP Clients on Keycloak

## Overview

MCP clients (Nov 2025 spec) that don't have pre-registered credentials can use
[Client ID Metadata Documents](https://datatracker.ietf.org/doc/draft-ietf-oauth-client-id-metadata-document/)
(CIMD) as an alternative to Dynamic Client Registration (DCR). Instead of
registering with the authorization server, the client publishes a JSON metadata
document at an HTTPS URL and uses that URL as its `client_id`. The authorization
server fetches and validates the metadata on the fly.

Examples of CIMD client IDs:
- `https://vscode.dev/oauth/client-metadata.json` (VS Code — tested end-to-end)
- `https://github.com/modelcontextprotocol/inspector` (MCP Inspector — planned)

## Status: experimental (Keycloak nightly)

CIMD was merged in Keycloak [PR #45285](https://github.com/keycloak/keycloak/pull/45285)
on 2026-02-23, targeted for release in **26.6.0** as an **experimental** feature.
As of this writing, it is only available in nightly builds.

## Prerequisites

| Component | Version | Notes |
|-----------|---------|-------|
| **Keycloak** | nightly (or >= 26.6.0 when released) | Docker image: `quay.io/keycloak/keycloak:nightly` |
| **Spring Security** | 6.5+ | For `protectedResourceMetadata()` support (OAuth Protected Resource Metadata / RFC 9728) |
| **Camunda** | This branch (`oauth-protected-resource-metadata`) | Adds PRM endpoint + `.well-known` to unprotected paths |

---

## What needs to change

### 1. Camunda: WebSecurityConfig.java

Two changes are needed in `WebSecurityConfig.java`:

**a) Add `.well-known/**` to API and unprotected paths** (already done in this branch)

The OAuth Protected Resource Metadata (PRM) endpoint at
`/.well-known/oauth-protected-resource` must be reachable without authentication.
MCP clients discover it from the `WWW-Authenticate` header on a 401 response.

```java
public static final Set<String> API_PATHS =
    Set.of("/api/**", "/v1/**", "/v2/**", "/mcp/**", "/.well-known/**");
public static final Set<String> UNPROTECTED_API_PATHS =
    Set.of(
        "/v2/license",
        "/v2/internal/cluster",
        "/v1/external/process/**",
        "/.well-known/**");
```

**b) Add the PRM customizer to the OAuth2 resource server** (already done in this branch)

This makes Camunda advertise the Keycloak issuer URI in the PRM response:

```java
private Customizer<ProtectedResourceMetadataConfigurer>
    oauthProtectedResourceMetadataCustomizer(
        final ClientRegistrationRepository clientRegistrationRepository) {
  return prm -> {
    final var authorizationServers =
        StreamSupport.stream(
                ((Iterable<ClientRegistration>) clientRegistrationRepository).spliterator(),
                false)
            .map(clientRegistration ->
                clientRegistration.getProviderDetails().getIssuerUri())
            .toList();  // IMPORTANT: .toList() — stream is consumed on every request

    prm.protectedResourceMetadataCustomizer(
        prmBuilder -> authorizationServers.forEach(prmBuilder::authorizationServer));
  };
}
```

> **Bug fix note**: The `authorization_servers` array in the PRM must contain
> **issuer URIs** (e.g., `http://localhost:18080/realms/camunda-platform`), NOT
> authorization endpoint URLs. The `.toList()` call is also critical — without
> it, the Java Stream is consumed on the first request and subsequent requests
> get a 500 error.

### 2. Docker Compose: Keycloak service

Two changes to the Keycloak service in `docker-compose.yml`:

```yaml
keycloak:
  image: quay.io/keycloak/keycloak:nightly          # was :26.x.x
  command: start-dev --import-realm --features=cimd  # added --features=cimd
```

> Keycloak must be able to reach CIMD metadata URLs on the public internet
> (e.g., `vscode.dev`). For metadata hosted on the Docker host, add
> `extra_hosts: ["host.docker.internal:host-gateway"]`.

### 3. Keycloak: CIMD client policy and profile

CIMD uses Keycloak's **Client Policies** system. You need:
1. A **Client Profile** with the `client-id-metadata-document` executor
2. A **Client Policy** with the `client-id-uri` condition, linked to the profile

See [Configuration via API](#option-a-via-keycloak-admin-rest-api) or
[Configuration via UI](#option-b-via-keycloak-admin-ui) below.

### 4. Keycloak: `offline_access` workaround

The CIMD executor forces `fullScopeAllowed=false` on all CIMD-created clients
(by design, for security). This conflicts with MCP clients like VS Code that
hardcode `offline_access` in their scope request. See
[Workaround: offline_access and fullScopeAllowed](#workaround-offline_access-and-fullscopeallowed)
for the fix.

---

## Configuration via API {#option-a-via-keycloak-admin-rest-api}

### Step 1: Get admin token

```bash
KC_HOST="http://keycloak.c8-oidc-es-connector-dmr.localhost"  # adjust to your setup
REALM="camunda-platform"

ADMIN_TOKEN=$(curl -sf -X POST \
  "$KC_HOST/realms/master/protocol/openid-connect/token" \
  -d "grant_type=password&client_id=admin-cli&username=admin&password=admin" \
  | python3 -c "import json,sys; print(json.load(sys.stdin)['access_token'])")
```

### Step 2: Create the client profile

```bash
curl -sf -X PUT -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  "$KC_HOST/admin/realms/$REALM/client-policies/profiles" \
  -d '{
    "profiles": [
      {
        "name": "cimd-profile",
        "description": "Profile for OAuth Client ID Metadata Document support",
        "executors": [
          {
            "executor": "client-id-metadata-document",
            "configuration": {
              "cimd-allow-http-scheme": true,
              "cimd-allow-permitted-domains": [
                "localhost",
                "127.0.0.1",
                "*.github.com",
                "vscode.dev"
              ],
              "cimd-restrict-same-domain": false,
              "cimd-required-properties": []
            }
          }
        ]
      }
    ]
  }'
```

### Step 3: Create the client policy

```bash
curl -sf -X PUT -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  "$KC_HOST/admin/realms/$REALM/client-policies/policies" \
  -d '{
    "policies": [
      {
        "name": "cimd-policy",
        "description": "Apply CIMD processing when client_id is a URL",
        "enabled": true,
        "conditions": [
          {
            "condition": "client-id-uri",
            "configuration": {
              "client-id-uri-scheme": ["https", "http"],
              "client-id-uri-allow-permitted-domains": [
                "localhost",
                "127.0.0.1",
                "*.github.com",
                "vscode.dev"
              ]
            }
          }
        ],
        "profiles": ["cimd-profile"]
      }
    ]
  }'
```

### Step 4: Verify CIMD is advertised

```bash
curl -sf "$KC_HOST/realms/$REALM/.well-known/openid-configuration" \
  | python3 -c "import json,sys; d=json.load(sys.stdin); \
    print('client_id_metadata_document_supported:', d.get('client_id_metadata_document_supported'))"
# Expected: client_id_metadata_document_supported: True
```

### Step 5: Apply the `offline_access` workaround

After the first MCP client authorization request creates the CIMD client, you
must add the `offline_access` realm role to the client's scope mappings. See
[Workaround: offline_access and fullScopeAllowed](#workaround-offline_access-and-fullscopeallowed).

### Step 6: Test with a curl authorization request

```bash
# Should return the Keycloak login page (HTTP 200), not "Client not found" (HTTP 400)
curl -sf -o /dev/null -w "%{http_code}" \
  "$KC_HOST/realms/$REALM/protocol/openid-connect/auth?client_id=https%3A%2F%2Fvscode.dev%2Foauth%2Fclient-metadata.json&response_type=code&code_challenge=dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk&code_challenge_method=S256&scope=openid&redirect_uri=http%3A%2F%2F127.0.0.1%3A33418%2F&state=test"
# Expected: 200
```

---

## Configuration via UI {#option-b-via-keycloak-admin-ui}

1. Open **Keycloak Admin Console** (e.g., `http://keycloak.c8-oidc-es-connector-dmr.localhost/admin/`)
2. Log in with admin credentials (`admin`/`admin` for dev)
3. Select the **camunda-platform** realm

### Create the Client Profile

4. Go to **Realm settings** → **Client policies** → **Profiles** tab
5. Click **Create client profile**
   - **Name**: `cimd-profile`
   - **Description**: `Profile for OAuth Client ID Metadata Document support`
   - Click **Save**
6. Click **Add executor**, select **client-id-metadata-document**
7. Configure:
   - **Allow http scheme**: `ON` (for dev only!)
   - **Trusted domains**: add `localhost`, `127.0.0.1`, `*.github.com`, `vscode.dev`
   - **Restrict same domain**: `OFF`
   - **Required properties**: (leave empty)
8. Click **Save**

### Create the Client Policy

9. Go to **Realm settings** → **Client policies** → **Policies** tab
10. Click **Create client policy**
    - **Name**: `cimd-policy`
    - **Description**: `Apply CIMD processing when client_id is a URL`
    - Click **Save**
11. Under **Conditions**, click **Add condition**, select **client-id-uri**
12. Configure:
    - **URI scheme**: add `https` and `http`
    - **Trusted domains**: add `localhost`, `127.0.0.1`, `*.github.com`, `vscode.dev`
13. Click **Save**
14. Under **Client profiles**, click **Add client profile** and select `cimd-profile`

### Apply the offline_access workaround (via UI)

After the first MCP client auth flow creates the CIMD client:

15. Go to **Clients** → find the client with ID `https://vscode.dev/oauth/client-metadata.json`
16. Go to the **Client scopes** tab → verify `offline_access` is listed as Optional
17. Go to the **Scope** tab (this is the role scope, not client scopes)
18. Click **Assign role**, filter by realm roles, and add `offline_access`

Also ensure the user has the `offline_access` role:

19. Go to **Users** → find the user (e.g., `demo`)
20. Go to **Role mapping** → **Assign role** → add realm role `offline_access`

---

## Workaround: `offline_access` and `fullScopeAllowed` {#workaround-offline_access-and-fullscopeallowed}

### The problem

The CIMD executor forces `fullScopeAllowed=false` on all CIMD-created clients
(on every authorization request — not just initial creation). This is a
security-by-default design: CIMD clients are untrusted, so their tokens only
include roles explicitly assigned to the client's scope.

However, MCP clients like VS Code hardcode `offline_access` in their scope
request (`scope=openid profile offline_access roles email`). Keycloak's offline
token check requires **both**:

1. The **client** must have the `offline_access` realm role in its scope mappings
2. The **user** must have the `offline_access` realm role

With `fullScopeAllowed=false` and no explicit scope role mapping, the check
fails with:

```
CODE_TO_TOKEN_ERROR error="not_allowed" reason="Offline tokens not allowed for the user or client"
```

### The fix

Two things need to be configured:

**a) Add `offline_access` realm role to the CIMD client's scope mappings**

This must be done after the CIMD executor creates the client (i.e., after the
first authorization request from the MCP client). The CIMD executor does NOT
reset scope role mappings on subsequent requests, so this fix persists.

```bash
# Find the CIMD client UUID
CLIENT_UUID=$(curl -sf -H "Authorization: Bearer $ADMIN_TOKEN" \
  "$KC_HOST/admin/realms/$REALM/clients" \
  | python3 -c "
import json, sys
for c in json.load(sys.stdin):
    if c['clientId'] == 'https://vscode.dev/oauth/client-metadata.json':
        print(c['id']); break")

# Get the offline_access realm role ID
ROLE_ID=$(curl -sf -H "Authorization: Bearer $ADMIN_TOKEN" \
  "$KC_HOST/admin/realms/$REALM/roles/offline_access" \
  | python3 -c "import json,sys; print(json.load(sys.stdin)['id'])")

# Add the role to the client's scope mappings
curl -sf -X POST -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  "$KC_HOST/admin/realms/$REALM/clients/$CLIENT_UUID/scope-mappings/realm" \
  -d "[{\"id\":\"$ROLE_ID\",\"name\":\"offline_access\"}]"
```

**b) Add `offline_access` realm role to the user**

If the user (e.g., `demo`) was created without the `default-roles-camunda-platform`
composite role, they may not have `offline_access`:

```bash
USER_ID=$(curl -sf -H "Authorization: Bearer $ADMIN_TOKEN" \
  "$KC_HOST/admin/realms/$REALM/users?username=demo&exact=true" \
  | python3 -c "import json,sys; print(json.load(sys.stdin)[0]['id'])")

curl -sf -X POST -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  "$KC_HOST/admin/realms/$REALM/users/$USER_ID/role-mappings/realm" \
  -d "[{\"id\":\"$ROLE_ID\",\"name\":\"offline_access\"}]"
```

### Why this is a workaround

- The CIMD executor has **no configuration option** for `fullScopeAllowed`.
  It's hardcoded to `false`.
- The scope role mapping must be applied **after** the CIMD client is created
  (on first auth request), which means the first authorization attempt will
  fail. The user must retry.
- A proper fix would be a CIMD executor option like
  `cimd-full-scope-allowed: true` or `cimd-scope-roles: [offline_access]`.

---

## All-in-one script

Copy-paste this to configure CIMD from scratch on a running Keycloak.

> **Note**: This script configures the CIMD policy/profile. The `offline_access`
> workaround must be applied separately after the first MCP client auth attempt
> creates the CIMD client (see step 2 in the script output).

```bash
#!/bin/bash
set -e

KC_HOST="${1:-http://keycloak.c8-oidc-es-connector-dmr.localhost}"
REALM="${2:-camunda-platform}"
TRUSTED_DOMAINS='["localhost", "127.0.0.1", "*.github.com", "vscode.dev"]'

echo "Getting admin token..."
ADMIN_TOKEN=$(curl -sf -X POST \
  "$KC_HOST/realms/master/protocol/openid-connect/token" \
  -d "grant_type=password&client_id=admin-cli&username=admin&password=admin" \
  | python3 -c "import json,sys; print(json.load(sys.stdin)['access_token'])")

echo "Checking CIMD feature..."
CIMD_SUPPORTED=$(curl -sf "$KC_HOST/realms/$REALM/.well-known/openid-configuration" \
  | python3 -c "import json,sys; print(json.load(sys.stdin).get('client_id_metadata_document_supported', False))")
if [ "$CIMD_SUPPORTED" != "True" ]; then
  echo "ERROR: CIMD not supported. Start Keycloak with --features=cimd"
  exit 1
fi
echo "CIMD feature is enabled."

echo "Creating client profile..."
curl -sf -X PUT -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  "$KC_HOST/admin/realms/$REALM/client-policies/profiles" \
  -d "{
    \"profiles\": [{
      \"name\": \"cimd-profile\",
      \"description\": \"Profile for OAuth Client ID Metadata Document support\",
      \"executors\": [{
        \"executor\": \"client-id-metadata-document\",
        \"configuration\": {
          \"cimd-allow-http-scheme\": true,
          \"cimd-allow-permitted-domains\": $TRUSTED_DOMAINS,
          \"cimd-restrict-same-domain\": false,
          \"cimd-required-properties\": []
        }
      }]
    }]
  }"

echo "Creating client policy..."
curl -sf -X PUT -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  "$KC_HOST/admin/realms/$REALM/client-policies/policies" \
  -d "{
    \"policies\": [{
      \"name\": \"cimd-policy\",
      \"description\": \"Apply CIMD processing when client_id is a URL\",
      \"enabled\": true,
      \"conditions\": [{
        \"condition\": \"client-id-uri\",
        \"configuration\": {
          \"client-id-uri-scheme\": [\"https\", \"http\"],
          \"client-id-uri-allow-permitted-domains\": $TRUSTED_DOMAINS
        }
      }],
      \"profiles\": [\"cimd-profile\"]
    }]
  }"

echo ""
echo "=== CIMD configured ==="
echo ""
echo "Step 1: Trigger the first auth flow from the MCP client (e.g., VS Code)."
echo "        The first attempt will FAIL because the CIMD client doesn't have"
echo "        the offline_access role in its scope yet."
echo ""
echo "Step 2: Run the offline_access workaround:"
echo ""
echo "  ADMIN_TOKEN=\$(curl -sf -X POST '$KC_HOST/realms/master/protocol/openid-connect/token' \\"
echo "    -d 'grant_type=password&client_id=admin-cli&username=admin&password=admin' \\"
echo "    | python3 -c \"import json,sys; print(json.load(sys.stdin)['access_token'])\")"
echo ""
echo "  # Find CIMD client UUID"
echo "  CLIENT_UUID=\$(curl -sf -H \"Authorization: Bearer \$ADMIN_TOKEN\" \\"
echo "    '$KC_HOST/admin/realms/$REALM/clients' \\"
echo "    | python3 -c \"import json,sys; [print(c['id']) for c in json.load(sys.stdin) if c['clientId'].startswith('https://')]\")"
echo ""
echo "  # Get offline_access role ID"
echo "  ROLE_ID=\$(curl -sf -H \"Authorization: Bearer \$ADMIN_TOKEN\" \\"
echo "    '$KC_HOST/admin/realms/$REALM/roles/offline_access' \\"
echo "    | python3 -c \"import json,sys; print(json.load(sys.stdin)['id'])\")"
echo ""
echo "  # Add role to client scope"
echo "  curl -sf -X POST -H \"Authorization: Bearer \$ADMIN_TOKEN\" \\"
echo "    -H 'Content-Type: application/json' \\"
echo "    '$KC_HOST/admin/realms/$REALM/clients/'\$CLIENT_UUID'/scope-mappings/realm' \\"
echo "    -d \"[{\\\"id\\\":\\\"\$ROLE_ID\\\",\\\"name\\\":\\\"offline_access\\\"}]\""
echo ""
echo "  # Add role to user (if not present)"
echo "  USER_ID=\$(curl -sf -H \"Authorization: Bearer \$ADMIN_TOKEN\" \\"
echo "    '$KC_HOST/admin/realms/$REALM/users?username=demo&exact=true' \\"
echo "    | python3 -c \"import json,sys; print(json.load(sys.stdin)[0]['id'])\")"
echo "  curl -sf -X POST -H \"Authorization: Bearer \$ADMIN_TOKEN\" \\"
echo "    -H 'Content-Type: application/json' \\"
echo "    '$KC_HOST/admin/realms/$REALM/users/'\$USER_ID'/role-mappings/realm' \\"
echo "    -d \"[{\\\"id\\\":\\\"\$ROLE_ID\\\",\\\"name\\\":\\\"offline_access\\\"}]\""
echo ""
echo "Step 3: Retry the auth flow from the MCP client. It should succeed."
```

---

## How CIMD works (the flow)

### Discovery (RFC 9728)

1. MCP client calls the MCP endpoint (e.g., `POST /mcp/cluster`) without auth
2. Camunda returns `401` with `WWW-Authenticate: Bearer resource_metadata="..."`
3. MCP client fetches `/.well-known/oauth-protected-resource` (or path-based
   variant `/.well-known/oauth-protected-resource/mcp/cluster`)
4. PRM response contains `authorization_servers: ["http://localhost:18080/realms/camunda-platform"]`
5. MCP client fetches `{issuer}/.well-known/openid-configuration` (or
   `/.well-known/oauth-authorization-server` per RFC 8414)

### Authorization (CIMD)

6. MCP client sends authorization request with
   `client_id=https://vscode.dev/oauth/client-metadata.json`
7. Keycloak's `client-id-uri` condition detects the URL-shaped client_id
8. The `client-id-metadata-document` executor **fetches** the metadata from
   that URL
9. Keycloak validates the metadata (redirect_uris, domains, auth method, etc.)
10. Keycloak **persists** the client (cached, respects HTTP Cache-Control headers)
11. The authorization flow proceeds: login page → consent → auth code → token

### VS Code specifics

VS Code's MCP client sends these parameters:
- `scope=openid profile offline_access roles email` (hardcoded, not configurable)
- `resource=http://localhost:8080/mcp/cluster` (the MCP endpoint URL)
- `redirect_uri=http://127.0.0.1:33418/` (local callback server)
- `code_challenge_method=S256` (PKCE)

VS Code's CIMD metadata at `https://vscode.dev/oauth/client-metadata.json`:
```json
{
  "client_name": "Visual Studio Code",
  "grant_types": ["authorization_code", "refresh_token",
                   "urn:ietf:params:oauth:grant-type:device_code"],
  "response_types": ["code"],
  "token_endpoint_auth_method": "none",
  "application_type": "native",
  "client_id": "https://vscode.dev/oauth/client-metadata.json",
  "client_uri": "https://vscode.dev/product",
  "redirect_uris": ["http://127.0.0.1:33418/", "https://vscode.dev/redirect"]
}
```

---

## Verified end-to-end flow (VS Code → Camunda MCP)

| Step | Action | Result |
|------|--------|--------|
| 1 | `POST /mcp/cluster` (no auth) | `401` + `WWW-Authenticate: Bearer resource_metadata="..."` |
| 2 | `GET /.well-known/oauth-protected-resource` | PRM with `authorization_servers` (issuer URI) |
| 3 | `GET {issuer}/.well-known/openid-configuration` | Full AS metadata (PKCE S256, scopes, endpoints) |
| 4 | Browser → `{authorization_endpoint}` + PKCE | Keycloak fetches CIMD metadata, shows login + consent |
| 5 | User logs in and consents | Auth code returned to `http://127.0.0.1:33418/` |
| 6 | `POST {token_endpoint}` (code + code_verifier) | Access token + offline refresh token |
| 7 | `POST /mcp/cluster` with Bearer token | MCP tools discovered (17 tools) |

---

## Dev shortcuts and known issues

### Shortcuts taken in this dev setup

| Shortcut | What we did | Production requirement |
|----------|------------|----------------------|
| **HTTP client_id URLs** | `cimd-allow-http-scheme: true` in executor | Must be `false` — CIMD client_id URLs must use HTTPS |
| **HTTP in condition schemes** | `client-id-uri-scheme: ["https", "http"]` | Should only allow `["https"]` |
| **Broad trusted domains** | `localhost`, `127.0.0.1`, `*.github.com`, `vscode.dev` | Restrict to only the specific domains you need |
| **Manual `offline_access` fix** | Added realm role to client scope mappings via Admin API after first auth | Should be automated (startup script, event listener, or Keycloak fix) |
| **Manual user role** | Added `offline_access` role to demo user manually | Users should get this via `default-roles-camunda-platform` composite role |
| **Keycloak nightly** | Using `quay.io/keycloak/keycloak:nightly` (unstable) | Wait for 26.6.0 stable release |

### Known Keycloak CIMD issues (nightly build)

These are limitations or bugs in the current Keycloak CIMD implementation:

1. **`fullScopeAllowed` is hardcoded to `false`** — The CIMD executor resets
   `fullScopeAllowed=false` on every authorization request. There is no
   configuration option to change this. This breaks MCP clients that request
   `offline_access` because the `offline_access` realm role is not in the
   client's effective scope.

   **Workaround**: Manually add the `offline_access` realm role to the CIMD
   client's scope mappings (Scope tab). This persists across CIMD executor
   updates because the executor doesn't reset scope role mappings.

   **Proper fix needed**: A CIMD executor option like `cimd-full-scope-allowed`
   or `cimd-additional-scope-roles`.

2. **Token exchange logs `client_auth_method="client-secret"`** — Even though
   CIMD clients are created with `publicClient=true` and
   `token_endpoint_auth_method=none`, Keycloak event logs show
   `client_auth_method="client-secret"`. This appears to be a logging bug —
   the flow works despite the incorrect log entry.

3. **Domain matching documentation is wrong** — The admin UI describes trusted
   domains as "regex", but the actual implementation uses **exact string
   matching** (same as `TrustedHostClientRegistrationPolicy`). Use
   `*.example.com` to match `example.com` and all subdomains. Do NOT use
   regex patterns like `(.*\.)?example\.com`.

4. **Condition and executor have separate domain lists** — Both the
   `client-id-uri` condition and the `client-id-metadata-document` executor
   have their own trusted domains configuration. Both must include the domains
   you want to allow. The condition decides whether to trigger the policy; the
   executor validates the actual client_id and metadata URLs.

5. **First auth attempt always fails** — The CIMD client is created on the
   first authorization request, but the `offline_access` workaround can only
   be applied after creation. This means the first end-to-end flow from a new
   MCP client will fail with `CODE_TO_TOKEN_ERROR`. The user must retry after
   the workaround is applied.

### What a production setup should look like

1. **HTTPS only**: Set `cimd-allow-http-scheme: false` (default) and only
   allow `"https"` in the condition's URI schemes
2. **Restrictive trusted domains**: Only allow specific client_id domains
   that you've vetted (e.g., `vscode.dev`, `cursor.sh`)
3. **Automated `offline_access` fix**: Either:
   - Run a post-startup script that applies the workaround for known CIMD
     clients
   - Use a Keycloak SPI event listener to auto-assign roles on client creation
   - Wait for Keycloak to add a `fullScopeAllowed` option to the CIMD executor
4. **User roles via realm defaults**: Ensure the `default-roles-camunda-platform`
   composite role includes `offline_access` so new users automatically get it
5. **Stable Keycloak version**: Wait for Keycloak 26.6.0 (or later) where
   CIMD exits experimental status

---

## Executor configuration reference

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `cimd-allow-http-scheme` | boolean | `false` | Allow `http://` client IDs (dev only) |
| `cimd-allow-permitted-domains` | string[] | `[]` | Trusted domains for client_id and metadata URIs |
| `cimd-restrict-same-domain` | boolean | `false` | Require client_id and redirect_uri to share a trusted domain |
| `cimd-required-properties` | string[] | `[]` | Required metadata properties (e.g., `client_name`, `logo_uri`) |

## CIMD-created client state (for reference)

When Keycloak's CIMD executor processes VS Code's metadata, it creates a client
with these properties:

```
clientId:                  https://vscode.dev/oauth/client-metadata.json
publicClient:              true
fullScopeAllowed:          false  (hardcoded by CIMD executor)
consentRequired:           true
standardFlowEnabled:       true
implicitFlowEnabled:       false
directAccessGrantsEnabled: false
serviceAccountsEnabled:    false
device authorization:      true   (from grant_types in metadata)
defaultClientScopes:       [openid, profile, roles, email]
optionalClientScopes:      [offline_access]
```
