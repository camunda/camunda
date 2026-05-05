# Optimize — JWT Bearer Token Authentication for `/api/**`

By default, Optimize's non-public REST API (`/api/**`) only accepts requests authenticated
via an Identity session cookie (browser login flow). This guide explains how to enable
**JWT bearer token authentication** so that any HTTP client can call those endpoints directly
using a standard `Authorization: Bearer <token>` header.

---

## How it works

When `jwtAuthForApiEnabled` is `true`, Optimize enables Spring Security's built-in
`oauth2ResourceServer` support, which adds `BearerTokenAuthenticationFilter` to the security
chain. The cookie filter runs before it (at a lower order). On every request that carries an
`Authorization: Bearer` header the bearer filter:

1. Validates the JWT signature, expiry, and audience against the configured Keycloak JWK Set URI.
2. On success — places a `JwtAuthenticationToken` in the `SecurityContextHolder`; `SessionService`
   resolves the authenticated user directly from that token, skipping cookie extraction.
3. On failure — immediately returns `401 Unauthorized`; the request does not fall back to
   cookie-based authentication, even though the cookie filter runs earlier in the chain (a bad
   bearer header is never silently ignored, per RFC 6750 §3.1).

Requests without a `Bearer` header are unaffected and continue to use the cookie flow.

---

## Prerequisites

|         Requirement         |                                     Details                                      |
|-----------------------------|----------------------------------------------------------------------------------|
| Keycloak running            | Default local URL: `http://localhost:18080`                                      |
| Optimize running            | Default local URL: `http://localhost:8090`                                       |
| `jq` installed              | Used to parse JSON responses in the shell examples                               |
| Optimize client in Keycloak | Pre-configured by Identity (`client_id=optimize`, `secret=demo-optimize-secret`) |

---

## Step 1 — Enable Direct Access Grants on the Optimize Keycloak client

The Optimize Keycloak client has Direct Access Grants (Resource Owner Password Credentials)
disabled by default. Enable it via the Keycloak Admin API:

```bash
# 1. Obtain a Keycloak admin token
ADMIN_TOKEN=$(curl -s -X POST \
  "http://localhost:18080/auth/realms/master/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password" \
  -d "client_id=admin-cli" \
  -d "username=admin" \
  -d "password=admin" \
  | jq -r '.access_token')

# 2. Resolve the internal UUID of the 'optimize' client
CLIENT_UUID=$(curl -s \
  "http://localhost:18080/auth/admin/realms/camunda-platform/clients?clientId=optimize" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  | jq -r '.[0].id')

# 3. Patch the client to enable Direct Access Grants
curl -s -X PUT \
  "http://localhost:18080/auth/admin/realms/camunda-platform/clients/$CLIENT_UUID" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"directAccessGrantsEnabled": true}'
```

> **Note:** This change persists in Keycloak until reverted. It is safe for local development
> but should be evaluated carefully before enabling in a shared or production environment.

---

## Step 2 — Configure Optimize

Two properties must be set. Both can be provided as environment variables, YAML overrides,
or Java system properties.

### Required properties

|                        Property                         |                                           Description                                           |
|---------------------------------------------------------|-------------------------------------------------------------------------------------------------|
| `CAMUNDA_OPTIMIZE_API_JWT_AUTH_ENABLED`                 | Enables JWT bearer auth for `/api/**`. Default: `false`                                         |
| `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWK_SET_URI` | Keycloak JWK Set URI used to validate token signatures                                          |
| `CAMUNDA_OPTIMIZE_API_AUDIENCE`                         | Must match the `aud` claim in the token. Default: `optimize`. For this setup use `optimize-api` |

### Option A — Environment variables (recommended for containers / local run)

```bash
export CAMUNDA_OPTIMIZE_API_JWT_AUTH_ENABLED=true
export SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWK_SET_URI=http://localhost:18080/auth/realms/camunda-platform/protocol/openid-connect/certs
export CAMUNDA_OPTIMIZE_API_AUDIENCE=optimize-api
```

### Option B — `environment-config.yaml` override file

Place this file alongside the Optimize distribution or in the working directory:

```yaml
api:
  jwtAuthForApiEnabled: true
  jwtSetUri: "http://localhost:18080/auth/realms/camunda-platform/protocol/openid-connect/certs"
  audience: "optimize-api"
```

### Option C — Java system properties (IDE / `java -jar`)

```bash
java \
  -DCAMUNDA_OPTIMIZE_API_JWT_AUTH_ENABLED=true \
  -DSPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWK_SET_URI=http://localhost:18080/auth/realms/camunda-platform/protocol/openid-connect/certs \
  -DCAMUNDA_OPTIMIZE_API_AUDIENCE=optimize-api \
  -jar optimize.jar
```

### Option D — Docker / Docker Compose

```yaml
services:
  optimize:
    image: camunda/optimize:latest
    environment:
      CAMUNDA_OPTIMIZE_API_JWT_AUTH_ENABLED: "true"
      SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWK_SET_URI: "http://keycloak:18080/auth/realms/camunda-platform/protocol/openid-connect/certs"
      CAMUNDA_OPTIMIZE_API_AUDIENCE: "optimize-api"
```

> **Note:** When Optimize and Keycloak run in the same Docker network, replace `localhost`
> with the Keycloak service name (e.g. `keycloak`).

---

## Step 3 — Obtain a JWT token

After enabling Direct Access Grants (Step 1) and restarting Optimize with the new config
(Step 2), obtain a token for a Keycloak user:

```bash
TOKEN=$(curl -s -X POST \
  "http://localhost:18080/auth/realms/camunda-platform/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password" \
  -d "client_id=optimize" \
  -d "client_secret=demo-optimize-secret" \
  -d "username=demo" \
  -d "password=demo" \
  | jq -r '.access_token')

echo $TOKEN
```

The `demo` user and `demo-optimize-secret` are the defaults from the local Identity preset.
Adjust if your environment uses different credentials.

---

## Step 4 — Call the Optimize API

Pass the token as a standard Bearer header:

```bash
# Health check (always public, useful to verify connectivity)
curl -s "http://localhost:8090/api/readyz"

# Management dashboard (requires auth)
curl -s \
  -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8090/api/dashboard/management" | jq .

# Specific dashboard by ID
curl -s \
  -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8090/api/dashboard/<dashboard-id>" | jq .

# List reports (public API — requires separate M2M token, see /api/public docs)
curl -s \
  -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8090/api/report" | jq .
```

---

## Step 5 — Disable when done (optional)

Set the flag back to `false` and restart Optimize. All `/api/**` endpoints will revert to
cookie-only authentication. No `BearerTokenAuthenticationFilter` is registered, so requests
without a cookie are rejected at the authorization layer as before.

```bash
export CAMUNDA_OPTIMIZE_API_JWT_AUTH_ENABLED=false
```

To also revoke Direct Access Grants in Keycloak:

```bash
ADMIN_TOKEN=$(curl -s -X POST \
  "http://localhost:18080/auth/realms/master/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password" \
  -d "client_id=admin-cli" \
  -d "username=admin" \
  -d "password=admin" \
  | jq -r '.access_token')

CLIENT_UUID=$(curl -s \
  "http://localhost:18080/auth/admin/realms/camunda-platform/clients?clientId=optimize" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  | jq -r '.[0].id')

curl -s -X PUT \
  "http://localhost:18080/auth/admin/realms/camunda-platform/clients/$CLIENT_UUID" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"directAccessGrantsEnabled": false}'
```

---

## Reference

### Configuration properties summary

|          YAML key          |                  Environment variable                   |  Default   |                               Description                                |
|----------------------------|---------------------------------------------------------|------------|--------------------------------------------------------------------------|
| `api.jwtAuthForApiEnabled` | `CAMUNDA_OPTIMIZE_API_JWT_AUTH_ENABLED`                 | `false`    | Enable JWT bearer auth for `/api/**`                                     |
| `api.jwtSetUri`            | `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWK_SET_URI` | `null`     | Keycloak JWK Set URI. **Required** when flag is `true`                   |
| `api.audience`             | `CAMUNDA_OPTIMIZE_API_AUDIENCE`                         | `optimize` | Expected `aud` claim in the token. Must be `optimize-api` for this setup |

### Default local Keycloak endpoints

|     Purpose     |                                         URL                                         |
|-----------------|-------------------------------------------------------------------------------------|
| Token endpoint  | `http://localhost:18080/auth/realms/camunda-platform/protocol/openid-connect/token` |
| JWK Set (certs) | `http://localhost:18080/auth/realms/camunda-platform/protocol/openid-connect/certs` |
| Admin API       | `http://localhost:18080/auth/admin/realms/camunda-platform`                         |

### Endpoints that remain public (no auth required)

These endpoints are always accessible regardless of the flag:

|              Endpoint              |                       Description                       |
|------------------------------------|---------------------------------------------------------|
| `GET /api/readyz`                  | Health / readiness probe                                |
| `GET /api/authentication/callback` | Identity OIDC redirect callback                         |
| `GET /api/authentication/logout`   | Session logout (must work with expired tokens)          |
| `GET /api/ui-configuration`        | UI bootstrap configuration                              |
| `GET /api/localization`            | Locale files                                            |
| `/api/external/**`                 | Public share API (dashboards / reports shared publicly) |
| `/external/**`                     | Public share UI resources                               |
| `/actuator/**`                     | Spring Boot Actuator (management port `8092`)           |

### Troubleshooting

|                          Symptom                           |                     Likely cause                      |                             Fix                              |
|------------------------------------------------------------|-------------------------------------------------------|--------------------------------------------------------------|
| `401` with a valid token                                   | `audience` mismatch                                   | Set `CAMUNDA_OPTIMIZE_API_AUDIENCE=optimize-api`             |
| `401` with `"Client not allowed for direct access grants"` | Direct Access Grants disabled                         | Run Step 1                                                   |
| `401` with no error body                                   | Flag is `false`                                       | Set `CAMUNDA_OPTIMIZE_API_JWT_AUTH_ENABLED=true` and restart |
| `500 IllegalStateException` on startup                     | `jwtSetUri` not set while flag is `true`              | Set `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWK_SET_URI`  |
| Token obtained but still `401`                             | Token expired                                         | Re-run Step 3 to get a fresh token                           |
| `NoResourceFoundException`                                 | Wrong port — hitting actuator (`8092`) instead of app | Use port `8090` (HTTP) or `8091` (HTTPS)                     |

