# PoC: CLI login via cluster discovery

A CLI (`c8poc`, standing in for c8ctl) logs a user in with nothing but the cluster URL:

1. `GET <cluster>/.well-known/camunda-auth` (unauthenticated) returns the authentication method
   and, for OIDC, each provider's issuer with the **public client** the CLI should use.
2. The CLI reads the issuer's `/.well-known/openid-configuration`.
3. Authorization code + PKCE in the browser, redirected to a random `127.0.0.1` port (RFC 8252).
4. The CLI checks the token's issuer and audience against what the cluster announced, then calls
   the cluster API with it.

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
      "scopes": ["openid", "offline_access", "camunda-api"],
      "redirect_uri": "http://127.0.0.1/callback",
      "audience": ["camunda-api"]
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
    scopes: [openid, offline_access, camunda-api]
    redirect-uri: http://127.0.0.1/callback   # default; see below
    # resource: https://oc.example.com        # RFC 8707, see below
    # client-secret: ...      # rejected at startup: CLI clients must be public
```

### Audience

The cluster only accepts access tokens whose `aud` contains one of its configured
`camunda.security.authentication.oidc.audiences`. Discovery announces them as `audience` (no extra
configuration), and the CLI must make the identity provider issue such a token:

| Identity provider          | How the CLI asks for the audience                          | Configure            |
|----------------------------|------------------------------------------------------------|----------------------|
| Keycloak                   | optional client scope with an audience mapper (`camunda-api`) | `scopes`          |
| Entra ID                   | the API's scope, e.g. `api://camunda-oc/access_as_user`    | `scopes`             |
| RFC 8707 / Auth0-style     | `resource` on the authorize, token and refresh requests     | `resource`           |

After login the CLI compares the token's `iss` and `aud` with the announced `issuer` and
`audience` and fails with the reason (for example a Microsoft Graph token from Entra ID when the
API scope is missing) instead of a bare 401 on the first call. It skips this for opaque tokens; the
cluster always validates anyway.

### Redirect URI

Identity providers compare the redirect URI exactly with the client registration; for loopback
URIs only the port is ignored (RFC 8252). Keycloak allows wildcards, Entra ID does not, so the
cluster announces the URI the admin registered and the CLI only adds its port:

| `redirect-uri`                         | CLI listens on                               |
|----------------------------------------|----------------------------------------------|
| `http://127.0.0.1/callback` (default)  | `127.0.0.1:<random>`                         |
| `http://[::1]/callback`                | `[::1]:<random>`                             |
| `http://localhost/callback`            | `127.0.0.1` **and** `[::1]`, same random port (the browser may resolve `localhost` to either) |
| `http://127.0.0.1:8085/callback`       | `127.0.0.1:8085` (fixed port, for IdPs that need one) |

Anything that is not `http` on a loopback host aborts the cluster start: a CLI cannot receive the
browser redirect elsewhere.

## Run it

Prerequisites: Java 21, Node 18+, a built distribution:

```sh
./mvnw install -pl dist -am -Dquickly -T1C
```

Then, from this directory, in two terminals:

```sh
./start-keycloak.sh           # downloads Keycloak once, starts it on :18080 with the realm imported
./start-oc.sh                 # Orchestration Cluster on :8080 (OIDC, H2)
```

Install the CLI once (puts `c8poc` on your `PATH`; undo with `npm rm -g c8ctl-login-poc`):

```sh
npm link
```

Then:

```sh
c8poc login http://localhost:8080   # opens the browser: log in as demo / demo
c8poc whoami                        # cluster, user, issuer, client, token expiry
c8poc topology                      # GET /v2/topology with the stored token
c8poc api /v2/license               # GET any cluster path
c8poc logout
```

`login` options: `--provider <id>` to skip the provider prompt, `--no-browser` to only print the
login URL. Without `npm link`, run `node c8poc.mjs <command>` instead.

The session (tokens, or basic credentials) is stored in `~/.c8poc/session.json` with mode 600;
set `C8POC_HOME` to use another directory. An expired access token is refreshed with the refresh
token before the next call.

## Keycloak setup (what the realm import does)

| Client    | Type                                    | Purpose                                                    |
|-----------|-----------------------------------------|------------------------------------------------------------|
| `camunda` | confidential, secret `camunda-secret`   | the cluster's own webapp login; audience mapper on the client |
| `c8ctl`   | public, PKCE S256, redirects `http://127.0.0.1/*`, `http://localhost/*`, `http://[::1]/*` | the CLI; Keycloak accepts any loopback port |

The `camunda-api` client scope carries the audience mapper and is an **optional** scope of `c8ctl`:
a token only gets `aud: camunda-api` when the CLI requests that scope. Try it by removing
`camunda-api` from `cli-clients.oidc.scopes` - the login then stops with an audience error.

The realm declares Keycloak's standard client scopes (`basic`, `profile`, `email`, ...) explicitly,
taken from a Keycloak export: a realm import that declares any client scope gets none of the
built-in ones.

Keycloak admin console: http://localhost:18080 (`admin` / `admin`).

## Out of scope

Physical tenants, linking from RFC 9728 protected-resource metadata, CIMD / dynamic client
registration, device authorization grant in the CLI, secure credential storage (OS keychain).
