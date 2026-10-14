# Manual tests: OIDC and physical tenant auth (8.10.4)

A local setup to test the OIDC and physical tenant auth fixes of 8.10.4 by hand. It is not part of
the build.

- One Keycloak on `http://localhost:18080` with the realms `idp-root`, `idp-a`, `idp-b` and `idp-c`.
- One Camunda on `http://localhost:8080` (REST and web apps) and `localhost:26500` (gRPC), with
  in-memory H2.

Camunda uses the network of the Keycloak container. Thus the browser and Camunda use the same
issuer URL, and you do not need a hosts entry.

## Scenarios

| # |                             What it checks                              | Physical tenants |      `CONFIG`      |           Script            |
|---|-------------------------------------------------------------------------|------------------|--------------------|-----------------------------|
| 1 | Named providers use the global claims, as in 8.9                        | no               | `multi-idp`        | `check-multi-idp.sh`        |
| 2 | Each physical tenant reads tokens and logins with its own OIDC settings | yes              | `physical-tenants` | `check-physical-tenants.sh` |
| 3 | A physical tenant rejects tokens from IdPs that it does not assign      | yes              | `physical-tenants` | `check-physical-tenants.sh` |

Scenarios 2 and 3 use the same config and the same script.

## Accounts

Each realm has the same accounts:

|                      Account                      |                        Use                        |                           Extra claim                            |
|---------------------------------------------------|---------------------------------------------------|------------------------------------------------------------------|
| user `alice` / `alice`                            | browser login, through the client `camunda-login` | `tenant_user` = `<realm>-alice`, for example `idp-a-alice`       |
| client `<realm>-grp`, secret `<realm>-grp-secret` | M2M tokens for the groups checks                  | `groups` = `["grp-admins"]`, `tenant_client` = `<realm>-grp-app` |
| client `<realm>-m2m`, secret `<realm>-m2m-secret` | M2M tokens                                        | `tenant_client` = `<realm>-app`                                  |

Keycloak admin console: `http://localhost:18080/admin`, `admin` / `admin`.

## Run

```bash
cd qa/manual/oidc-auth

# Scenario 1: no physical tenants, two named providers
CONFIG=multi-idp docker compose up -d --wait
./scripts/check-multi-idp.sh

# Scenarios 2 and 3: physical tenants
docker compose down -v
CONFIG=physical-tenants docker compose up -d --wait
./scripts/check-physical-tenants.sh

# Compare with another version. Always use "down -v" first: an older version cannot start on
# the data of a newer one.
docker compose down -v
CAMUNDA_VERSION=8.10.3 CONFIG=physical-tenants docker compose up -d --wait
```

The scripts wait until Camunda is ready. `docker compose up --wait` does not wait for it.

The check scripts need `curl` and Docker. They run `grpcurl` in a container.

## Scenario 1: named providers use the global claims ([#65633](https://github.com/camunda/camunda/issues/65633))

Config: [`config/multi-idp.yaml`](config/multi-idp.yaml). The default slot sets only
`username-claim: preferred_username` and `client-id-claim: client_id`. Provider `alpha` (`idp-a`)
sets no claims. Provider `beta` (`idp-b`) sets only `username-claim: tenant_user`. Provider `gamma`
(`idp-c`) sets only `client-id-claim: tenant_client`.

|           Check            |         8.9.23          |                              8.10.3                               |    8.10.4     |
|----------------------------|-------------------------|-------------------------------------------------------------------|---------------|
| `check-multi-idp.sh`, REST | 200                     | 403 for alpha, 400 for beta ("clientId claim (<not configured>)") | 200           |
| `check-multi-idp.sh`, gRPC | OK                      | OK                                                                | OK            |
| `gamma` M2M, REST and gRPC | 403, `PermissionDenied` | not run                                                           | 200, OK       |
| Browser login with `alpha` | `alice`                 | the token's `sub`, and the web apps deny access                   | `alice`       |
| Browser login with `beta`  | `alice`                 | `idp-b-alice`                                                     | `idp-b-alice` |
| Browser login with `gamma` | `alice`                 | not run                                                           | `alice`       |

New in 8.10: a named provider can override the global `username-claim`, `client-id-claim` and
`prefer-username-claim`. 8.9 binds these settings but does not use them, so `beta` logs in as
`alice` there. Since 8.10.4, a claim that the provider does not set comes from the global slot.
The docs for 8.9 and 8.10 describe only the global claims; [camunda/camunda-docs#10357](https://github.com/camunda/camunda-docs/pull/10357) adds the
override.

8.9 also ignores the `client-id-claim` of `gamma`, so its two checks fail on 8.9.23. This is
expected.

For the browser login, open `http://localhost:8080/operate` and select the provider. For a fast
check without a browser, run `./scripts/login.sh "" alpha`.

Manual checks:

- Web apps: log in with `alpha`, then with `beta`. Operate, Tasklist and Admin open with the
  expected user. After logout, the next visit asks for a login again.
- Real client: use the Java client or `c8ctl` with the client credentials of `idp-a-m2m`
  (secret `idp-a-m2m-secret`, token URL
  `http://localhost:18080/realms/idp-a/protocol/openid-connect/token`). Deploy and start a process
  over gRPC and over REST.
- Upgrade: start with `CAMUNDA_VERSION=8.9.23`, then 8.10.4. M2M and login with `alpha` work on
  both.

### Upgrade from 8.9

By default the stack keeps the secondary storage in memory. The overlay
[`config/persistent-h2.yaml`](config/persistent-h2.yaml) writes it to the data volume, so it
survives when compose recreates the container with another version. Do not run
`docker compose down` between the versions, it removes the volume.

```bash
docker compose down -v
export CONFIG=multi-idp OVERLAY=persistent-h2
CAMUNDA_VERSION=8.9.23 docker compose up -d --wait
./scripts/check-multi-idp.sh                          # gamma fails, as expected on 8.9
c8ctl run scripts/process.bpmn --profile s1-alpha
c8ctl list pi --profile s1-alpha --state COMPLETED

CAMUNDA_VERSION=8.10.4 docker compose up -d --wait    # recreates only camunda, keeps the data
./scripts/check-multi-idp.sh                          # all checks pass
c8ctl list pi --profile s1-alpha --state COMPLETED    # the instance from 8.9.23 is still there
```

A version cannot start on the data of a newer version. To go back, run `docker compose down -v`.

### c8ctl profiles

c8ctl uses only REST. Add `/v2` to the base URL, and use the tenant path for a physical tenant:

```bash
c8ctl add profile s1-alpha --baseUrl http://localhost:8080/v2 \
  --clientId idp-a-m2m --clientSecret idp-a-m2m-secret \
  --oAuthUrl http://localhost:18080/realms/idp-a/protocol/openid-connect/token
c8ctl add profile s2-tenanta --baseUrl http://localhost:8080/physical-tenants/tenanta/v2 \
  --clientId idp-a-m2m --clientSecret idp-a-m2m-secret \
  --oAuthUrl http://localhost:18080/realms/idp-a/protocol/openid-connect/token

c8ctl run scripts/process.bpmn --profile s1-alpha
c8ctl list pi --profile s1-alpha --state COMPLETED    # the test process completes at once
```

## Scenario 2: each physical tenant uses its own OIDC settings ([#65607](https://github.com/camunda/camunda/issues/65607), [#65633](https://github.com/camunda/camunda/issues/65633), [#65582](https://github.com/camunda/camunda/issues/65582))

Config: [`config/physical-tenants.yaml`](config/physical-tenants.yaml).

|  Tenant   |                 Login                 |                             Claims                             | Expected username |
|-----------|---------------------------------------|----------------------------------------------------------------|-------------------|
| `default` | only its own `oidc` slot (`idp-root`) | `preferred_username`, `client_id`                              | `alice`           |
| `tenanta` | only `partnera` (`idp-a`)             | from the tenant's default slot: `tenant_user`, `tenant_client` | `idp-a-alice`     |
| `tenantb` | only its own `oidc` slot (`idp-b`)    | `tenant_user`, `tenant_client`                                 | `idp-b-alice`     |

1. Run `./scripts/check-physical-tenants.sh`. The scenario 2 checks must pass.
2. Log in with a browser on each tenant and open Operate and Tasklist:
   - `http://localhost:8080/physical-tenants/tenanta/operate`
   - `http://localhost:8080/physical-tenants/tenantb/operate`
   - `http://localhost:8080/operate`
3. Check the username in the web app and on `/physical-tenants/<tenant>/v2/authentication/me`.
4. For [camunda/camunda-security-library#720](https://github.com/camunda/camunda-security-library/issues/720), log in to the tenants in a different order, each in a new private window: `tenantb`,
   then `default`, then `tenanta`. The usernames must not change with the order. `tenantb` and
   `default` use the same registration id `oidc` with different issuers.

On 8.10.3, the M2M token of `idp-a` gets 403 on `tenanta` over REST, because `partnera` does not
use the tenant's `client-id-claim`.

### Groups over gRPC ([#65582](https://github.com/camunda/camunda/issues/65582))

On 8.10.3, gRPC used the `groups-claim` setting of the `default` tenant for every tenant. Only
`tenantb` sets `groups-claim: groups`, and `idp-b-grp` is admin on `tenantb` only through its
group `grp-admins`. Run the script twice:

```bash
CONFIG=physical-tenants docker compose up -d --wait
./scripts/check-physical-tenants.sh
docker compose down -v
OVERLAY=default-groups-claim CONFIG=physical-tenants docker compose up -d --wait
./scripts/check-physical-tenants.sh
```

[`config/default-groups-claim.yaml`](config/default-groups-claim.yaml) adds `groups-claim` to the
`default` tenant. Then `default` reads groups from the token, and `tenanta` does not.

|                                   Check                                    | 8.10.4 |       8.10.3       |
|----------------------------------------------------------------------------|--------|--------------------|
| Without overlay: `idp-b-grp` deploys on `tenantb` over gRPC                | OK     | `PermissionDenied` |
| With overlay: `idp-a-m2m` deploys on `tenanta` over gRPC                   | OK     | `Internal` (NPE)   |
| Without overlay: `idp-b-grp` deploys on `tenantb` over REST (`KNOWN` line) | 403    | 403                |

REST still reads groups with the `groups-claim` of the `default` tenant. This is the open bug
[#65597](https://github.com/camunda/camunda/issues/65597), not part of 8.10.4. The script prints
this check as `KNOWN` and does not count it as a failure. With the overlay, REST returns 200,
because then the `default` tenant also reads the `groups` claim.

Manual checks:

- Sessions are per tenant. Each tenant sets its own cookie, `camunda-session-physical-tenants-<id>`.
  After a login on `tenanta`, `tenantb` asks for its own login. A logout on `tenanta` keeps you
  logged in on `tenantb`.
- Real client: use the Java client (`physicalTenantId`) or `c8ctl` on `tenanta` with `idp-a-m2m`
  and on `tenantb` with `idp-b-m2m`. Deploy and start a process over gRPC and over REST.
- A tenant that changes its IdP needs its own secret
  ([#65517](https://github.com/camunda/camunda/pull/65517)): remove the `client-secret` of
  `tenantb` from `config/physical-tenants.yaml` and start again. Camunda does not start, and the
  log says: "A physical tenant that changes 'issuer-uri' or 'token-uri' of a cluster OIDC provider
  must set its own 'client-secret'". On 8.10.3, Camunda starts and sends the secret of `idp-root`
  to `idp-b`.

## Scenario 3: a tenant rejects tokens from IdPs that it does not assign ([#65501](https://github.com/camunda/camunda/issues/65501), [#65527](https://github.com/camunda/camunda/pull/65527))

Same config as scenario 2. Run `./scripts/check-physical-tenants.sh`. The scenario 3 checks send
tokens from `idp-b` and `idp-root` to `tenanta`, from `idp-a` and `idp-root` to `tenantb`, and
from `idp-b` to `default`.

|      |      8.10.4       |                                            8.10.3                                             |
|------|-------------------|-----------------------------------------------------------------------------------------------|
| REST | 401               | 401                                                                                           |
| gRPC | `Unauthenticated` | `PermissionDenied`: the token passes authentication, and only the missing permissions stop it |

The last checks send the `idp-b` token to `default` through the percent-encoded path
`/%70hysical-tenants/tenantb/...`. On 8.10.3, the security chain of `tenantb` accepts the token,
but the request runs on `default`: the topology returns 200, and a deployment returns 403 only
because the principal has no permissions there. On 8.10.4, both return 400.

|                                                | 8.10.4 | 8.10.3 |
|------------------------------------------------|--------|--------|
| `default`, `idp-b` token, topology             | 401    | 401    |
| `default` through the encoded path, topology   | 400    | 200    |
| `default` through the encoded path, deployment | 400    | 403    |

Manual checks:

- `http://localhost:8080/physical-tenants/tenanta/operate` sends you directly to `idp-a`, because
  `partnera` is the only login of `tenanta`. `tenantb` sends you directly to `idp-b`.
- A login with a provider that the tenant does not assign, for example
  `/physical-tenants/tenanta/oauth2/authorization/partnerb`, returns 500 ("Invalid Client
  Registration") and does not send you to the IdP.
- After a login on `tenantb`, `/physical-tenants/tenanta/v2/authentication/me` returns 401 in the
  same browser.
- Real client: use the Java client or `c8ctl` on `tenanta` with the credentials of `idp-b-m2m`.
  Every command fails with `Unauthenticated` (gRPC) or 401 (REST), not with a permission error.

## Clean up

```bash
docker compose down -v
```

