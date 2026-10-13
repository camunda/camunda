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
- `physical_tenant` / `api_base` name the physical tenant the document describes and its API prefix
  (see [Physical tenants](#physical-tenants)).
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

### Physical tenants

Each physical tenant authenticates on its own - its providers (`providers.assigned`), audiences and
CLI clients can all differ - so each has its own document, at the RFC 9728 style path suffix:

```http
GET /.well-known/camunda-auth/physical-tenants/pt1
```

```json
{ "physical_tenant": "pt1", "api_base": "/physical-tenants/pt1", "providers": [ ... ] }
```

The unsuffixed document describes the default physical tenant (unprefixed API). An unknown physical
tenant is a 404.

By default physical tenants are **not listed**: the client names the physical tenant, so the
endpoint does not disclose which ones exist. Listing is opt-in, for setups where the ids are not
sensitive (environments or departments of one organization rather than different customers):

```yaml
camunda.security.authentication.cli-discovery:
  list-physical-tenants: true      # cluster-wide, default false
  display-name: Default            # name of the default physical tenant in the list
camunda.physical-tenants.pt1.security.authentication.cli-discovery:
  display-name: Sales EU           # each physical tenant names itself
```

The unsuffixed document then carries the list (the physical tenants' own documents do not):

```json
"physical_tenants": [
  { "id": "default", "name": "Default",   "discovery": "/.well-known/camunda-auth/physical-tenants/default" },
  { "id": "pt1",     "name": "Sales EU",  "discovery": "/.well-known/camunda-auth/physical-tenants/pt1" },
  { "id": "pt2",     "name": "ACME Corp", "discovery": "/.well-known/camunda-auth/physical-tenants/pt2" }
]
```

A tenant's CLI clients are the cluster's `cli-clients`, replaced per registration id by the
tenant's own `camunda.physical-tenants.<id>.security.authentication.cli-clients` - a provider only
the tenant knows needs one there.

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
c8poc login http://localhost:8080 --provider oidc   # default tenant: demo / demo
c8poc whoami                        # context, tenant, user, issuer, client, audience, expiry
c8poc topology                      # GET /v2/topology with the stored token
c8poc api /v2/license               # GET any path below the tenant's API
c8poc logout
```

`login` options: `--provider <id>` to skip the provider prompt, `--no-browser` to only print the
login URL. Without `npm link`, run `node c8poc.mjs <command>` instead.

### Contexts: several tenants and identity providers at once

Every login creates a **context** - cluster + physical tenant + identity + tokens - named
`<host>[/<physical tenant>]` unless `--context <name>` is given, and makes it current (like
kubectl). The physical tenant comes from the url, `--physical-tenant`, or - if the cluster lists its
physical tenants - a selection:

```sh
c8poc physical-tenants                                       # what the cluster lists (kit: listing on)
# PHYSICAL TENANT  NAME       LOGIN
# default          Default    http://localhost:8080
# pt1              Sales EU   http://localhost:8080/physical-tenants/pt1
# pt2              ACME Corp  http://localhost:8080/physical-tenants/pt2

c8poc login                                                  # asks: [1] default [2] pt1 [3] pt2
c8poc login http://localhost:8080/physical-tenants/pt1       # demo / demo     -> localhost:8080/pt1
c8poc login --physical-tenant pt2                            # alice / alice   -> localhost:8080/pt2 (acme realm)

c8poc contexts
#    CONTEXT             PHYSICAL TENANT  PROVIDER  USER   ACCESS TOKEN EXPIRES
#    localhost:8080      default          oidc      demo   ...
#    localhost:8080/pt1  pt1              oidc      demo   ...
# *  localhost:8080/pt2  pt2              acme      alice  ...

c8poc use localhost:8080/pt1                     # switch
c8poc topology                                   # -> /physical-tenants/pt1/v2/topology
c8poc topology --context localhost:8080/pt2      # one-off on another context
c8poc logout localhost:8080/pt2                  # or: c8poc logout --all
```

Tokens do not cross tenants: the default tenant, pt1 and pt2 each accept only their own context's
token (different issuer or audience), every other combination is a 401.

Contexts (tokens, or basic credentials) are stored in `~/.c8poc/session.json` with mode 600; set
`C8POC_HOME` to use another directory. An expired access token is refreshed with the refresh token
before the next call.

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

The `camunda-api-pt1` client scope does the same for physical tenant pt1, whose audience differs.

A second realm, `acme` (user `alice` / `alice`), is the identity provider only physical tenant pt2
knows, with its own `c8ctl` client and `camunda-api` scope.

Keycloak admin console: http://localhost:18080 (`admin` / `admin`).

## Out of scope

Listing physical tenants after login ("which physical tenants accept my identity"), linking from RFC 9728 protected-resource metadata, CIMD / dynamic client
registration, device authorization grant in the CLI, secure credential storage (OS keychain).
