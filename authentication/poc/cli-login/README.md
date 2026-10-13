# PoC: CLI login via cluster discovery

A CLI (think c8ctl) logs a user in with nothing but the cluster URL:

1. `GET <cluster>/.well-known/camunda-auth` (unauthenticated) returns the authentication method
   and, for OIDC, each provider's issuer with the **public client** the CLI should use.
2. The CLI reads the issuer's `/.well-known/openid-configuration`.
3. Authorization code + PKCE in the browser, redirected to a random `127.0.0.1` port (RFC 8252).
4. The CLI calls the cluster API with the access token.

Everything runs bare metal: Keycloak from its distribution, the Orchestration Cluster from the
locally built `dist`, the CLI with plain Node (no dependencies).

## Discovery endpoint

```http
GET /.well-known/camunda-auth
```

```json
{
  "version": 1,
  "method": "oidc",
  "providers": [
    {
      "id": "oidc",
      "issuer": "http://localhost:18080/realms/camunda",
      "client_id": "c8ctl",
      "scopes": ["openid", "offline_access"]
    }
  ]
}
```

- `method` is `oidc`, `basic` (the CLI asks for username and password), or `none` (unprotected API).
- Only providers with a configured CLI client are listed. `id` is the OIDC registration id: `oidc`
  for the flat `camunda.security.authentication.oidc.*` block, otherwise the
  `providers.oidc.<id>` key.
- The issuer comes from the configuration; the endpoint never contacts the identity provider.

Configuration (see [`oc-application.yaml`](oc-application.yaml)):

```yaml
camunda.security.authentication.cli-clients:
  oidc:                       # registration id
    client-id: c8ctl          # required
    scopes: [openid, offline_access]
    # client-secret: ...      # rejected at startup: CLI clients must be public
```

## Run it

Prerequisites: Java 21, Node 18+, a built distribution:

```sh
./mvnw install -pl dist -am -Dquickly -T1C
```

Then, from this directory, in three terminals:

```sh
./start-keycloak.sh           # downloads Keycloak once, starts it on :18080 with the realm imported
./start-oc.sh                 # Orchestration Cluster on :8080 (OIDC, H2)
node c8ctl-login.mjs http://localhost:8080 /v2/topology
```

The browser opens the Keycloak login. Log in as `demo` / `demo`. The CLI prints the token claims
and the topology response.

Options: `--provider <id>` to skip the provider prompt, `--no-browser` to only print the login URL.

## Keycloak setup (what the realm import does)

| Client    | Type                                    | Purpose                                                    |
|-----------|-----------------------------------------|------------------------------------------------------------|
| `camunda` | confidential, secret `camunda-secret`   | the cluster's own webapp login                             |
| `c8ctl`   | public, PKCE S256, redirect `http://127.0.0.1/*` | the CLI; Keycloak accepts any loopback port       |

Both carry an audience mapper adding `camunda-api` to the access token, which is the audience the
cluster validates (`camunda.security.authentication.oidc.audiences`). Without it, the CLI gets a
valid token the cluster rejects with 401.

Keycloak admin console: http://localhost:18080 (`admin` / `admin`).

## Out of scope

Physical tenants, linking from RFC 9728 protected-resource metadata, CIMD / dynamic client
registration, device authorization grant in the CLI, token caching and refresh.
