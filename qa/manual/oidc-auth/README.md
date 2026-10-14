# Manual tests: OIDC and physical tenant auth (8.10.4)

A local setup to test the OIDC and physical tenant auth fixes of 8.10.4 by hand. It is not part of
the build.

- One Keycloak on `http://localhost:18080` with the realms `idp-root`, `idp-a` and `idp-b`.
- One Camunda on `http://localhost:8080` (REST and web apps) and `localhost:26500` (gRPC), with
  in-memory H2.

Camunda uses the network of the Keycloak container. Thus the browser and Camunda use the same
issuer URL, and you do not need a hosts entry.

## Accounts

Each realm has the same accounts:

|                      Account                      |                        Use                        |                        Extra claim                         |
|---------------------------------------------------|---------------------------------------------------|------------------------------------------------------------|
| user `alice` / `alice`                            | browser login, through the client `camunda-login` | `tenant_user` = `<realm>-alice`, for example `idp-a-alice` |
| client `<realm>-m2m`, secret `<realm>-m2m-secret` | M2M tokens                                        | `tenant_client` = `<realm>-app`                            |

Keycloak admin console: `http://localhost:18080/admin`, `admin` / `admin`.

## Run

```bash
cd qa/manual/oidc-auth

# Scenario 1: no physical tenants, two named providers
SCENARIO=multi-idp docker compose up -d --wait
./scripts/check-multi-idp.sh

# Scenarios 2 and 4: physical tenants
docker compose down -v
SCENARIO=physical-tenants docker compose up -d --wait
./scripts/check-physical-tenants.sh

# Compare with another version. Always use "down -v" first: an older version cannot start on
# the data of a newer one.
docker compose down -v
CAMUNDA_VERSION=8.10.3 SCENARIO=physical-tenants docker compose up -d --wait
```

The scripts wait until Camunda is ready. `docker compose up --wait` does not wait for it.

The check scripts need `curl` and Docker. They run `grpcurl` in a container.

## Scenario 1: named providers use the global claims (#65633)

Config: [`config/multi-idp.yaml`](config/multi-idp.yaml). The default slot sets only
`username-claim: preferred_username` and `client-id-claim: client_id`. Provider `alpha` (`idp-a`)
sets no claims. Provider `beta` (`idp-b`) sets only `username-claim: tenant_user`.

|           Check            |   8.9   |                              8.10.3                               |    8.10.4     |
|----------------------------|---------|-------------------------------------------------------------------|---------------|
| `check-multi-idp.sh`, REST | 200     | 403 for alpha, 400 for beta ("clientId claim (<not configured>)") | 200           |
| `check-multi-idp.sh`, gRPC | OK      | OK                                                                | OK            |
| Browser login with `alpha` | `alice` | the token's `sub`, and the web apps deny access                   | `alice`       |
| Browser login with `beta`  | `alice` | `idp-b-alice`                                                     | `idp-b-alice` |

8.9 does not use the `username-claim` of a named provider, so `beta` logs in as `alice`. 8.10 uses
it. This change is intended.

For the browser login, open `http://localhost:8080/operate` and select the provider. For a fast
check without a browser, run `./scripts/login.sh "" alpha`.

## Scenario 2: each physical tenant uses its own OIDC settings (#65607, #65633)

Config: [`config/physical-tenants.yaml`](config/physical-tenants.yaml).

|  Tenant   |                      Login                      |                             Claims                             | Expected username |
|-----------|-------------------------------------------------|----------------------------------------------------------------|-------------------|
| `default` | `oidc` (`idp-root`), and `partnera`, `partnerb` | `preferred_username`, `client_id`                              | `alice`           |
| `tenanta` | only `partnera` (`idp-a`)                       | from the tenant's default slot: `tenant_user`, `tenant_client` | `idp-a-alice`     |
| `tenantb` | only its own `oidc` slot (`idp-b`)              | `tenant_user`, `tenant_client`                                 | `idp-b-alice`     |

1. Run `./scripts/check-physical-tenants.sh`. The scenario 2 checks must pass.
2. Log in with a browser on each tenant and open Operate and Tasklist:
   - `http://localhost:8080/physical-tenants/tenanta/operate`
   - `http://localhost:8080/physical-tenants/tenantb/operate`
   - `http://localhost:8080/operate`
3. Check the username in the web app and on `/physical-tenants/<tenant>/v2/authentication/me`.
4. For camunda/camunda-security-library#720, log in to the tenants in a different order, each in a new private window: `tenantb`,
   then `default`, then `tenanta`. The usernames must not change with the order. `tenantb` and
   `default` use the same registration id `oidc` with different issuers.

On 8.10.3, the M2M token of `idp-a` gets 403 on `tenanta` over REST, because `partnera` does not
use the tenant's `client-id-claim`.

## Scenario 4: a tenant rejects tokens from IdPs that it does not assign (#65501)

Same config as scenario 2. Run `./scripts/check-physical-tenants.sh`. The scenario 4 checks send
tokens from `idp-b` and `idp-root` to `tenanta`, and from `idp-a` and `idp-root` to `tenantb`.

|      |      8.10.4       |                                            8.10.3                                             |
|------|-------------------|-----------------------------------------------------------------------------------------------|
| REST | 401               | 401                                                                                           |
| gRPC | `Unauthenticated` | `PermissionDenied`: the token passes authentication, and only the missing permissions stop it |

## Clean up

```bash
docker compose down -v
```

