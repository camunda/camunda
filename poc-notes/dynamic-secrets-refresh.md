# Proof of concept: dynamic secrets for physical tenants

Epic: product-hub#3816 (Dynamic Configuration & Secrets), slice 0 and the RDBMS part of slice 1.
Built on PR #67248 (SecretStoreRegistries close path), merged in unchanged.

## What it proves

An operator changes the secondary-storage password, username or URL of one physical tenant in
the secret store, and the running node uses the new value within one refresh interval, with no
restart. The tenant's readers and its RDBMS exporter both move, because both reach the database
through one data source. Other tenants are not touched. Jobs cannot read these secrets. A
literal credential in a tenant configuration can be rejected before it is applied.

|                                          Capability                                           |                                  Where                                   |                                             Proven by                                             |
|-----------------------------------------------------------------------------------------------|--------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------|
| `internal-config` block in a store entry builds a second store per tenant, `default` included | `Secrets`, `SecretStoreConfiguration#internalConfigSecrets`              | `InternalConfigSecretsTest`                                                                       |
| Jobs cannot resolve from it; configuration references resolve only from it                    | `InternalConfigSecrets` is a separate bean, never handed to the job path | `InternalConfigSecretsTest`                                                                       |
| Startup fails when jobs can reach the internal-config scope, naming both keys and values      | `InternalConfigScopes`                                                   | `InternalConfigScopesTest`, `InternalConfigSecretsTest`                                           |
| Literal-credential guard for a tenant config blob                                             | `PhysicalTenantCredentialGuard`, `@Credential`                           | `PhysicalTenantCredentialGuardTest`                                                               |
| CI fails on an unmarked credential-named field                                                |                                                                          | `CredentialFieldMarkingTest`                                                                      |
| Per-tenant refresher, authoritative reads, push on change                                     | `InternalConfigRefresher(s)`                                             | `InternalConfigRefresherTest`                                                                     |
| Build, verify, swap, drain, close; failed refresh keeps the old client                        | `RefreshableClient`                                                      | `RefreshableClientTest`                                                                           |
| Degraded tenant reported, not in any probe group                                              | `SecretRefreshHealthIndicator` (`physicalTenantSecretRefresh`)           | `InternalConfigRefresherTest`                                                                     |
| RDBMS: password rotation in place, URL swap with a new pool, wrong password keeps the pool    | `RdbmsDataSources`, `RefreshableDataSource`                              | `RdbmsDataSourcesRefreshTest` (H2), `PhysicalTenantRdbmsSecretRotationIT` (PostgreSQL, 4 tenants) |

## Configuration

```yaml
camunda:
  secrets:
    refresh-interval: 5m                 # per tenant overridable
    stores:
      file:
        default:
          path: /etc/camunda/secrets/jobs
          internal-config:
            path: /etc/camunda/secrets/internal
  data:
    secondary-storage:
      rdbms:
        url: camunda.secrets.db-url
        username: camunda.secrets.db-username
        password: camunda.secrets.db-password
```

AWS and GCP take `internal-config.path-prefix` and `internal-config.container-secret-id`.

## How to run

```bash
./mvnw verify -pl configuration -Dtest='PhysicalTenantCredentialGuardTest,CredentialFieldMarkingTest,SecondaryStorageIsolationValidationTest' -DskipTests=false -DskipITs -Dquickly
./mvnw verify -pl dist -Dtest='io.camunda.application.commons.secrets.*Test,RdbmsDataSourcesRefreshTest' -DskipTests=false -DskipITs -Dquickly
# needs Docker; the rdbms profile is what includes @Tag("rdbms") tests
./mvnw verify -pl qa/acceptance-tests -Prdbms -Dit.test=PhysicalTenantRdbmsSecretRotationIT -DskipTests=false -DskipUTs -Dquickly
```

The IT rotates on PostgreSQL the way a dual-user rotation does, since a PostgreSQL role has one
password: a new login role, member of the old one, is put in the store; once no session of the
old role remains, the old role is set to `NOLOGIN`. That gives the overlap the scoping asks for.

## Design decisions

- **Two beans, not one.** `InternalConfigSecrets` is its own bean with its own stores and close
  path. The job path takes `SecretStoreRegistries`, so isolation is structural.
- **Scope check is reachability, not equality.** A reference name is one `[\p{Alnum}_-]+`
  token, so a job prefix `camunda/` also reaches `camunda/internal-x`. The check rejects any
  internal-config scope a job reference could form, and a file directory inside the job directory
  (symlinks inside the job directory are followed).
- **Annotation plus a short external list.** `@Credential` sits on the field, so it survives
  moves and renames and is cheap to review. The OIDC client secret, keystore passwords, the
  initial user password and the search proxy password live in modules that cannot see the
  annotation; they are listed in `PhysicalTenantCredentialGuard.CREDENTIALS_OF_OTHER_MODULES`.
  False positives of the name heuristic are listed with a reason in the test.
- **The guard takes the tenant's own overlay**, bound alone, plus whether the effective
  configuration has an internal-config store. Inherited literals from the static configuration are
  therefore allowed. It is not wired into startup.
- **Push, not poll.** The refresher pushes a changed value to registered consumers. One
  scheduler thread serves all tenants.
- **Default interval 5 minutes.** Open in the scoping; chosen as a balance between store load
  and the 1 to 7 day overlap.
- **Every config read is `resolveFromStore`.** The internal-config store's cache is never used.
- **RDBMS password change is in place**: verify with one `DriverDataSource` connection, then
  `HikariConfigMXBean#setUsername/setPassword` and `softEvictConnections`. No second pool, so no
  doubled connection count. A URL change builds a second pool named `camunda-rdbms-<tenant>-<n>`
  (avoids a Micrometer meter clash with the draining pool), drains up to 30 s, then closes.
- **A URL change to another vendor is rejected**: MyBatis statements are chosen per vendor at
  startup.
- **Static storage isolation skips references.** Two tenants may legitimately use the same
  reference name; only their stores know the values.

## Stubbed or missing

- Elasticsearch/OpenSearch search clients and the Camunda exporter: not done. A reference in an
  ES/OS connection key fails at client construction today. Proposed: a mutable
  `BasicCredentialsProvider` for an in-place password change, a delegating transport for a URL
  change, and an exporter restart for the Camunda exporter.
- Start degraded (US-7): a reference that cannot be resolved at startup still fails startup.
- The guard is not called from anywhere in production code; the lifecycle API will call it.
- Health hook is minimal; the per-tenant health model belongs to the lifecycle work.
- No runtime cross-tenant check that two tenants resolve to the same storage.
- Free-form maps (exporter args) are scanned for patterns only.
- A failed refresh is retried every interval with no backoff.

## Measurements

- `PhysicalTenantRdbmsSecretRotationIT` with a 1 s refresh interval: each scenario, including
  the rotation, revoke, a new process instance exported and read back, completes in 1.1 to 2.1 s;
  the class (PostgreSQL container plus broker with four tenants) runs in about 25 s.

## Open questions for the team

1. Is reachability the right scope rule, or only equality as in the scoping?
2. Default refresh interval, and backoff for a failing refresh.
3. Should an unresolvable reference at boot degrade the tenant instead of failing startup?
4. C1: should a URL change check that the target holds the tenant's data (schema version,
   exporter position) before the swap?
5. Drain timeout of 30 s: enough for long exporter flushes?
6. Should the guard also flag `username` and URLs as credentials, or only scan them?
7. Is a pool name suffix acceptable for dashboards keyed on the pool name?

