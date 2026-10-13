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

The unsuffixed document lists all physical tenants (the physical tenants' own documents do not);
without physical tenants configured, that is just the default one. Each can have a display name:

```yaml
camunda.security.authentication.cli-discovery:
  display-name: Default            # name of the default physical tenant in the list
camunda.physical-tenants.pt1.security.authentication.cli-discovery:
  display-name: Sales EU           # each physical tenant names itself
```

Note that the document is unauthenticated, so the list discloses the physical tenant ids to anyone
who can reach the cluster - fine when physical tenants are environments or departments of one
organization, to be reviewed when they are different customers.

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
c8poc login                         # choose physical tenant + provider; Keycloak: demo / demo
c8poc whoami                        # context, physical tenant, identity, issuer, client, audience
c8poc topology                      # GET /v2/topology with the stored token
c8poc api /v2/license               # GET any path below the physical tenant's API
c8poc logout                        # deletes the context and its user
```

`login` options: `--physical-tenant <id>`, `--provider <id>` to skip the prompts, `--name <context>`
for the context name, `--no-browser` to only print the login URL. Without `npm link`, run
`node c8poc.mjs <command>` instead.

### Configuration: clusters, users, contexts (like kubectl)

The configuration in `~/.c8poc/config.json` (mode 600; `C8POC_HOME` changes the directory) is
structured like a kubeconfig:

- **clusters** - a cluster's URL, shared by its contexts
- **users** - an identity and its credentials: provider, issuer, client, tokens (or basic
  credentials). A token is only valid for the physical tenant it was issued for, so every login
  creates its own user, named `<identity>@<context>`.
- **contexts** - cluster + physical tenant (what a namespace is to kubectl) + user, one of them
  current. Every command uses the current context, or the one given with the global `--context`.

Every `login` stores a cluster, a user and a context - named `<host>[/<physical tenant>]` unless
`--name` (or `--context`) is given - and makes the context current. Logging in again with the same
name replaces that context's user. The physical tenant comes from the url, `--physical-tenant`, or -
if the cluster has more than the default one - a selection:

```sh
c8poc physical-tenants                                    # the cluster's physical tenants
# PHYSICAL TENANT  NAME       LOGIN
# default          Default    http://localhost:8080
# pt1              Sales EU   http://localhost:8080/physical-tenants/pt1
# pt2              ACME Corp  http://localhost:8080/physical-tenants/pt2

c8poc login                                               # asks: [1] default [2] pt1 [3] pt2
c8poc login http://localhost:8080/physical-tenants/pt1    # demo / demo   -> context localhost:8080/pt1
c8poc login --physical-tenant pt2 --name acme             # alice / alice -> context acme
```

Managing the configuration works like `kubectl config`:

```sh
c8poc config get-contexts
# CURRENT   NAME                 CLUSTER          PHYSICAL TENANT   USER
#           localhost:8080       localhost:8080   default           demo@localhost:8080
#           localhost:8080/pt1   localhost:8080   pt1               demo@localhost:8080/pt1
# *         acme                 localhost:8080   pt2               alice@acme

c8poc config use-context localhost:8080/pt1     # switch
c8poc config current-context
c8poc config rename-context localhost:8080/pt1 sales
c8poc config delete-context acme                # also deletes its user (and an unused cluster)
c8poc config get-clusters
c8poc config get-users
c8poc config view                               # whole configuration, tokens REDACTED

c8poc --context acme topology                   # one command on another context
```

Tokens do not cross physical tenants: the default physical tenant, pt1 and pt2 each accept only
their own context's token (different issuer or audience), every other combination is a 401. An
expired access token is refreshed with the refresh token before the next call. A
`session.json` of an earlier c8poc version is migrated on first use.

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
