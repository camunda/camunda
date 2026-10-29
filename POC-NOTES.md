# Physical Tenants at Scale: integrated proof of concept

Branch `pt-poc-integration`, on top of `pt-poc-lifecycle-api`. Local only, not for merge as is.

This branch combines the proof-of-concept branches of the initiative into one tree. Each branch
keeps its own notes under [`poc-notes/`](poc-notes):

|                       Branch                       |                                          Notes                                          |
|----------------------------------------------------|-----------------------------------------------------------------------------------------|
| `pt-poc-lifecycle-tenant-set` (lifecycle slice 0)  | [lifecycle-tenant-set.md](poc-notes/lifecycle-tenant-set.md)                            |
| `pt-poc-lifecycle-api` (lifecycle slice 1)         | [lifecycle-api.md](poc-notes/lifecycle-api.md)                                          |
| `pt-poc-dynamic-secrets-refresh` (incl. PR #67248) | [dynamic-secrets-refresh.md](poc-notes/dynamic-secrets-refresh.md)                      |
| `pt-poc-lazy-tenant-resources`                     | [lazy-tenant-resources.md](poc-notes/lazy-tenant-resources.md)                          |
| `pt-poc-tenant-disk-limit`                         | [tenant-disk-limit.md](poc-notes/tenant-disk-limit.md)                                  |
| `pt-poc-csl-runtime-security` (this branch's tip)  | [Runtime HTTP security](#runtime-http-security-with-the-camunda-security-library) below |
| `pt-poc-tenant-config-consumers`                   | [tenant-config-consumers.md](poc-notes/tenant-config-consumers.md)                      |

## What the integration proves

|              Step               |                                                                                                                                                                                                                                 What it proves                                                                                                                                                                                                                                  |                                                                             Proven by                                                                              |
|---------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Merges                          | The four branches build and test together; text conflicts only in the notes, `RdbmsDataSources` and `MyBatisConfigurationUnreachableDatabaseTest`.                                                                                                                                                                                                                                                                                                                              | full `./mvnw install -Dquickly` after each merge                                                                                                                   |
| RDBMS pool design               | The lazy holder sits inside the refreshable wrapper (`LazyPool` is the client of `RefreshableClient`). A rotation before first use builds nothing, and the pool is built with the latest settings on first use; after first use a password is applied in place and a URL change builds, verifies, swaps and drains as before. Close works never built, built, failed, closed twice and after a URL swap. The unpooled schema data source follows rotations.                     | `LazyPoolTest` (6), `RdbmsDataSourcesRefreshTest` (13)                                                                                                             |
| Key classification              | `used-space.processing/replication` are `RESTART` (read by the tenant's partition manager, rebuilt on every module restart). `rdbms.lazy-initialization` is `REJECT outside-module` (built once by Spring). `secrets.refresh-interval` was `REJECT outside-module` until the refreshers followed the lifecycle, now `RESTART`.                                                                                                                                                  | `PhysicalTenantKeyClassificationTest`, `PhysicalTenantDiskLimitUpdateIT`                                                                                           |
| Credential guard                | `PhysicalTenantCredentialGuard` runs as a `PhysicalTenantConfigValidator` (`PhysicalTenantCredentialValidator`) on API create, API update and the file registration of a new tenant, on the receiving node before anything is sent to the coordinator. A literal password reaches neither the data directory (tenant config blobs, `.topology.meta`, logs of Raft and RocksDB), the cluster configuration, the logs (all loggers, rendered with stack traces) nor the response. | `PhysicalTenantCredentialValidatorTest` (5), `PhysicalTenantCredentialGuardIT` (3)                                                                                 |
| Refreshers follow the lifecycle | `PhysicalTenantResources` hook in `PhysicalTenantLifecycleService`. An added tenant gets a refresher on its own internal-config store; a replaced tenant's refresher is re-pointed (new store, new interval, same consumers); a removed tenant's refresher is cancelled and closes its consumers, which for RDBMS closes the `RefreshableClient`.                                                                                                                               | `InternalConfigRefresherTest` (11), `RdbmsDataSourcesRefreshTest#shouldCloseThePoolWhenTheTenantIsRemoved`, `PhysicalTenantSecretRefreshLifecycleIT` (4)           |
| One health model                | `SecretRefreshHealth` and its indicator are gone; the refresher reports `DEGRADED` through `PhysicalTenantRegistry#reportHealth`, with a detail naming the consumer and error, never a value, and only clears a degradation it reported. It shows under `/actuator/health/physicalTenants/<id>`; liveness and readiness stay `UP`.                                                                                                                                              | `InternalConfigRefresherTest`, `PhysicalTenantSecretRefreshLifecycleIT#shouldReportAFailedRefreshOnlyInTheTenantsOwnHealth`, `PhysicalTenantRdbmsSecretRotationIT` |
| Secret stores closed            | A runtime context gets job secret stores built from its own configuration when its module starts, and the lifecycle's close helper releases them through #67248's `SecretStoreResources`. The leak test now configures a file job store and an internal-config store for the restarted tenant.                                                                                                                                                                                  | `PhysicalTenantRestartLeakIT`                                                                                                                                      |
| Disk limit at runtime           | A `PUT` through the actuator lowers a tenant's limit below its usage: only that tenant is paused (`RESOURCE_EXHAUSTED`), `default` keeps accepting; raising it resumes the tenant without compaction.                                                                                                                                                                                                                                                                           | `PhysicalTenantDiskLimitUpdateIT`                                                                                                                                  |

A bug found on the way: building the new job stores before closing the old ones on a replace
removed the new stores' cache meters (both publish the same meter ids), and so did validating a
change of a running tenant. The stores are now built only when the module starts, after the old
module released its own, and validation only checks the store configuration.

## How to run

```bash
./mvnw install -Dquickly -T3
./mvnw verify -pl zeebe/qa/integration-tests -Dquickly -DskipTests=false -DskipUTs \
  -Dit.test='PhysicalTenantRuntimeLifecycleIT,PhysicalTenantRestartLeakIT,PhysicalTenantRestartWithDataIT,PhysicalTenantConfigRolloutIT,PhysicalTenantDiskUsageIsolationIT,PhysicalTenantDiskUsageLimitIT,PhysicalTenantDiskReplicationLimitIT,PhysicalTenantCredentialGuardIT,PhysicalTenantSecretRefreshLifecycleIT,PhysicalTenantDiskLimitUpdateIT'
# PostgreSQL in Docker; the rdbms profile includes @Tag("rdbms") tests
./mvnw verify -pl qa/acceptance-tests -Prdbms -Dquickly -DskipTests=false -DskipUTs \
  -Dit.test=PhysicalTenantRdbmsSecretRotationIT
# not tagged rdbms, so run without the profile
./mvnw verify -pl qa/acceptance-tests -Dquickly -DskipTests=false -DskipUTs \
  -Dit.test=PhysicalTenantLazyRdbmsResourcesIT
./mvnw verify -pl configuration -Dquickly -DskipTests=false -DskipITs
./mvnw clean verify -pl dist -Dquickly -DskipTests=false -DskipITs \
  -Dtest='io.camunda.application.commons.secrets.**.*Test,io.camunda.application.commons.rdbms.**.*Test,io.camunda.application.commons.pt.**.*Test,io.camunda.zeebe.broker.**.*Test,io.camunda.zeebe.shared.management.**.*Test'
```

## Results on the combined tree (2026-10-29, Apple-silicon laptop)

|                             Suite                             |                                                                   Result                                                                   |
|---------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------|
| zeebe/qa/integration-tests, 10 classes above                  | 31 tests: 30/31 on the first run (`PhysicalTenantRestartLeakIT`: 114 threads after vs 113 before), 31/31 on the re-run                     |
| `PhysicalTenantRdbmsSecretRotationIT` (`-Prdbms`, PostgreSQL) | 3/3                                                                                                                                        |
| `PhysicalTenantLazyRdbmsResourcesIT`                          | 4/4                                                                                                                                        |
| `configuration` unit suite                                    | 1442/1442 (after the `fix:` that renames `ConfigurationSecretReference.PREFIX`, which `CodeQualityTest` rejected since the secrets branch) |
| dist touched packages (51 classes)                            | 1493 passed, 1 skipped (`MyBatisStatementSplitCostTest`, opt-in)                                                                           |
| zeebe/broker disk limit unit tests                            | 23/23                                                                                                                                      |

`PhysicalTenantRestartLeakIT` over 20 restarts: tenant meters 179 → 179 (7 of them secret cache
meters), actors, RocksDB instances and file descriptors constant, restarts 75–365 ms.

## What remains

- **RDBMS for a tenant added at runtime (step 8) is not done.** The lazy holder makes the pool and
  MyBatis cheap to add, but everything that would use them is built once from the boot tenants:

  - `RdbmsExporterConfiguration` (and the whole RDBMS Spring context) is loaded only when the root
    `secondary-storage.type` is `rdbms`, so a node whose boot tenants use none or a search engine
    has no RDBMS exporter factory at all;
  - `RdbmsExporterFactory`/`RdbmsExporterWrapper` (zeebe/exporters/rdbms-exporter) take a
    boot map of exporter configurations and throw for an unknown tenant in `configure`;
  - `RdbmsServiceFactory` (db/rdbms) takes `Map.copyOf` maps of mapper bundles and readers
    (`MyBatisConfiguration#rdbmsMapperBundles`, `RdbmsConfiguration#rdbmsTenantReaders`);
  - `RdbmsDataSources` and `PhysicalTenantMyBatisResources` hold fixed per-tenant maps;
  - schema initialisation (`physicalTenantSchemaConfigs`, `RdbmsSchemaManagerRegistry`,
    `PerTenantSchemaInitialization`) and the migration status provider iterate the boot resolver;
  - the exported-position suppliers are copied into `BrokerStartupContextImpl` at boot.

  Making these lookups live (functions or registries with add/remove instead of maps), adding a
  `PhysicalTenantResources` for RDBMS that registers the tenant in each and its pool with the
  refresher, and loading the RDBMS context whenever any tenant may use it, is the work; it crosses
  db/rdbms, the RDBMS exporter and the schema initialisation, so it was left out. Runtime-added
  tenants still need `secondary-storage.type=none`, which is documented but not enforced.

- A replaced **boot** tenant now runs on job stores of its own, but the boot stores stay open until
  shutdown and share meter ids with the new ones; removing it later removes those meters for both.
  The REST side keeps the boot stores, as it keeps the boot security chain.

- Removing a boot tenant with RDBMS closes its pool through the refresher; its Spring-side readers
  and row-count gauges stay and fail closed.

- A rotation before first use is accepted without connecting, so a wrong secret only shows when the
  tenant is first used.

- `PhysicalTenantRestartLeakIT` counted one extra thread once when it ran right after other classes
  in the same JVM; it passed alone and on the batch re-run. Not investigated further.

- The secret store configuration of a tenant is still `REJECT outside-module`, because the REST
  side keeps the boot stores; only `secrets.refresh-interval` follows a replace.

- Elasticsearch/OpenSearch clients and leadership transfer before a restart are unchanged from the
  branches' notes. The CSL dispatcher is done on `pt-poc-csl-runtime-security`, see below.

# Runtime HTTP security with the Camunda Security Library

Branch `pt-poc-csl-runtime-security`, on top of `pt-poc-integration` (834b56d765e). Built against
the local CSL proof of concept `1.2.1-pt-poc-SNAPSHOT` (worktree `csl-poc-runtime-security`,
ADR-0040, spike notes `docs/spikes/hot-reload-scoped-chains.md`). Not for merge as is.

A physical tenant's HTTP security (its scoped API and webapp chains in CSL, and the host-side
state built from its OIDC configuration) now follows the tenant lifecycle on every member, and its
OIDC client secret and `private_key_jwt` keystore rotate from its internal-config secret store,
without a restart and without a configuration change.

## What is proven, and by which test

All end-to-end scenarios run in `PhysicalTenantRuntimeSecurityIT` (zeebe/qa/integration-tests):
three brokers with embedded gateways in one JVM, RF 3, one partition, root authentication OIDC,
every tenant `secondary-storage: none`, Keycloak 26 as identity providers, a Toxiproxy in front of
Keycloak. Every check goes over real HTTP to the REST endpoint of each broker. A boot tenant serves
continuous REST load (bearer token, `GET /physical-tenants/boottenant/v2/topology`, one virtual
thread per broker, no pacing) for the whole class.

|                     Scenario                      |                                                                                                                                                                                                           What is checked                                                                                                                                                                                                           |                                  Test                                   |
|---------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------|
| Add an OIDC tenant at runtime                     | 404 on every broker before; after the API call 401 without a token, 200 with a token of its own IdP, 401 with a token of another IdP, and its token is 401 on another tenant; each member reports `ADDED`                                                                                                                                                                                                                           | `shouldServeAnOidcTenantAddedAtRuntimeOnEveryBroker`                    |
| Change issuer, audience, client id, claim mapping | before: `svc` (admin by `azp`) may publish a message (200), `svc-other` may not (403). After one PUT: old-IdP tokens 401 on every broker; the new mapping (`client-id-claim: alt_client`) decides the engine's authorization: new `svc-other` (alt_client names an admin) 200, new `svc` 403; the tenant's webapp session (login on broker 0) 401; the boot tenant's session 200; a new login works; each member reports `REPLACED` | `shouldApplyAChangedIdentityProviderAndClaimsMappingOnEveryBroker`      |
| Remove                                            | 404 on every broker for `/v2/topology`, `/v2/messages/publication`, `/oauth2/authorization/oidc`, `/login`, `/sso-callback`, `/operate` with a valid token; its webapp session 404; re-create rejected (`stays reserved`, slice 1 rule)                                                                                                                                                                                             | `shouldAnswerNotFoundForTheWholeBasePathOfARemovedTenantOnEveryBroker`  |
| Switch authentication method                      | rejected with 400 naming `security.authentication.method`; version unchanged; the tenant keeps serving. See "BASIC" below                                                                                                                                                                                                                                                                                                           | `shouldRejectAChangeOfATenantsAuthenticationMethod`                     |
| Unreachable new IdP                               | proxy down: the first member keeps generation 1 (`KEPT_PREVIOUS`), reports `DEGRADED` with the reason, the rollout stays `rolledOut: false`, old tokens still 200 and new tokens 401 on every broker, the boot tenant stays `HEALTHY`. Proxy up: dynamic-config's retry applies it, the rollout completes, new tokens 200, old 401, the tenant `HEALTHY` again                                                                      | `shouldKeepThePreviousSecurityWhileTheNewIdentityProviderIsUnreachable` |
| Rotate the client secret                          | Keycloak's secret rotation policy: old and new secret both valid (checked by introspection); the new value is written to the tenant's store; every member reports `CREDENTIALS_ROTATED`; the old secret is revoked; after the 5 s access token expired, the existing session refreshes with the new secret (200); a new login works; module contexts and config version unchanged                                                   | `shouldRotateTheClientSecretFromTheSecretStoreAndKeepTheSessions`       |
| Rotate the assertion keystore                     | `private_key_jwt` with the keystore path and passwords as references. (1) file replaced at the same path: every member `CREDENTIALS_REFRESHED`, Keycloak then only trusts the new key (keys cache cleared), the session refreshes (200), a new login works; (2) the store points at a new file with a new password: `CREDENTIALS_ROTATED`, same checks                                                                              | `shouldRotateTheAssertionKeystoreFromTheSecretStoreAndKeepTheSessions`  |
| No leaks                                          | 10 IdP/claim changes of one tenant (alternating realms and `azp`/`alt_client`, each rolled out and a message published on every broker): CSL `ScopeGeneration`, `ScopedOidcClients`, `DefaultSecurityFilterChain` instances equal, host bearer and claims converters not more, threads not more, meters equal                                                                                                                       | `shouldNotLeakOverManySecurityChangesOfOneTenant`                       |
| Other tenants unaffected                          | 0 failed requests of the boot tenant's load during all of the above                                                                                                                                                                                                                                                                                                                                                                 | `@AfterAll` of the class                                                |
| Literal credentials                               | a literal client secret (default slot, named provider) and keystore passwords on create, a literal client secret on update and in the static file are rejected and found in neither the data directory, the cluster configuration, the logs nor the response                                                                                                                                                                        | `PhysicalTenantCredentialGuardIT` (9)                                   |

Mutation checks (made the named tests fail, then reverted): not writing the rotated secret to the
store fails the client secret test (session 401 after the old secret was revoked); not replacing the
keystore file fails the keystore test (503).

Unit tests: `PhysicalTenantScopeProviderTest` (16), `PhysicalTenantAuthenticationsTest` (4),
`OidcOverrideBeansConfigurationPhysicalTenantWiringTest` (6, a tenant added after startup and a
changed claim), `PhysicalTenantCredentialsTest` (2), `PhysicalTenantServedFilterTest` (5, fail
closed), `PhysicalTenantKeyClassificationTest` (10), `PhysicalTenantCredentialGuardTest` (12),
`PhysicalTenantCredentialValidatorTest` (7), `InternalConfigRefresherTest` (13, polling consumers),
`CamundaServicesConfigurationTest` (13, services of an added, replaced and removed tenant).

## How it works

|                      Piece                      |        Where         |                                                                                                                                   What                                                                                                                                   |
|-------------------------------------------------|----------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `PhysicalTenantScopeProvider`                   | authentication       | Immutable descriptor map replaced as a whole by `put`/`remove`; `get()` returns the current set (what each tenant should be served with)                                                                                                                                 |
| `PhysicalTenantAuthentications`                 | authentication       | The configuration each tenant's chains serve. Takes a change only once CSL applied it. `derive(...)` gives per-tenant host state that is rebuilt when the tenant's configuration object changes and dropped on remove                                                    |
| `PhysicalTenantHttpSecurity`                    | authentication       | Per tenant, serialised: provider `put`, `ScopedSecurityReloadPort.apply`/`rotateCredentials`/`refreshCredentials`/`remove`, then `PhysicalTenantAuthentications`; `revert` after a change that was not made; `serves(id)` for the fail-closed filter                     |
| `PhysicalTenantRestSecurity`                    | dist                 | Builds the tenant's `AuthenticationConfiguration` from the lifecycle's environment exactly as at startup, resolves credential references from the tenant's internal-config store, registers a refresher consumer, keeps the last result per tenant for health and status |
| `PhysicalTenantLifecycleService`                | dist                 | add: module start, then security; replace: security first (a kept-back change rejects the whole replace, `Reason.SECURITY`), then module restart; remove: security removed first. A security problem reports `DEGRADED`; liveness/readiness never depend on it           |
| `PhysicalTenantServedFilter`                    | gateway-rest         | Lets a tenant through only when it is in the registry and its chains serve on this node; a node without scoped chains lets every live tenant through                                                                                                                     |
| `PhysicalTenantServiceRegistry`                 | dist                 | The REST/MCP `ServiceRegistry` routes per-tenant accessors to services built at runtime for an added or replaced tenant (a `PhysicalTenantResources`); needed for any 200 of a runtime tenant                                                                            |
| `key-classification.txt`                        | configuration        | 49 authentication keys `SECURITY`; 15 stay `REJECT` (`root-only` 4, `outside-module` 3, `initialization` 8)                                                                                                                                                              |
| `PhysicalTenantCredentials`, refresher `poll()` | authentication, dist | Client secret and keystore path/password/key password of the default slot and every named provider may be references; a changed value → `rotateCredentials`, a keystore whose SHA-256 changed at the same path → `refreshCredentials`, checked on every refresh run      |
| `/actuator/physicalTenants/<id>`                | dist                 | adds `local`: the answering member's tenant health and detail, and its last HTTP security change (outcome, generation, duration, failure)                                                                                                                                |

Decisions:

- **Order in the sequence.** Add: after the module started, so REST serves once it can; until then
  the filter answers 404. Replace: before the module stops, so that an unreachable new IdP leaves the
  tenant entirely on its previous version, gRPC included; the rollout waits (dynamic-config retry,
  10 s to 1 min) and the tenant reports `DEGRADED` until the IdP answers. Remove: first, so the base
  path answers 404 while the module stops.
- **`apply` decides rotation.** A replace always calls `apply`; CSL reports `CREDENTIALS_ROTATED` when
  only credentials changed and keeps the sessions. The refresher calls `rotateCredentials`, which
  rejects anything but credentials.
- **Keystore `path` may be a reference** but is not a credential: a literal path is allowed, and a
  reference lets an operator point at a new file without a configuration change.
- **Detection of a keystore changed at the same path**: SHA-256 of the file on every refresh run
  (default 5 min; 1 s in the test). Unreadable counts as changed and fails the refresh.
- **Boot tenants' references** are resolved once every startup chain exists
  (`SmartInitializingSingleton`) and applied as a credential rotation; until then the startup
  chains hold the reference string (only token endpoint calls would use it, and the broker is not
  ready yet).

## Host-side state built at startup, and what happened to each

|                                                                          State                                                                          |                                                                                                Handling                                                                                                 |
|---------------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `oidcTokenAuthenticationConverter` (per-tenant converters, issuer- and claim-keyed claim converters, `ScopedOidcClaimsProviderFactory` claims provider) | derived from `PhysicalTenantAuthentications`; always tenant-aware now (a node that started without tenants used the plain converter, so a runtime tenant would have been mapped with the root's claims) |
| `PhysicalTenantOidcSettingsRegistry` (ID-token decoder factory, login claim converters, identity claims)                                                | derived                                                                                                                                                                                                 |
| `ProviderAwareOidcUserAuthenticationConverter` per-tenant converters                                                                                    | through the settings registry                                                                                                                                                                           |
| `idTokenDecoderFactory`                                                                                                                                 | through the settings registry                                                                                                                                                                           |
| `PhysicalTenantGroupsClaims` (both membership services)                                                                                                 | derived                                                                                                                                                                                                 |
| `PhysicalTenantScopeProvider`                                                                                                                           | live                                                                                                                                                                                                    |
| `PhysicalTenantServedFilter` startup incarnations                                                                                                       | follows `PhysicalTenantHttpSecurity`                                                                                                                                                                    |
| `ServiceRegistry` (incl. the per-tenant `BrokerRequestAuthorizationConverter`)                                                                          | live for nodes without secondary storage; cluster-wide services (status, topology, recovery, backup fan-out) still fold over the startup tenants                                                        |
| `ClusterAdminUser`, cluster-admin chains                                                                                                                | cluster-wide (`camunda.security.cluster-admin.*`, not overridable per tenant): nothing per tenant to rebuild                                                                                            |
| cluster `/v2` unification `BeanPostProcessor`                                                                                                           | unchanged: it only runs at startup and only concerns `default`, which cannot change at runtime (Q13 below)                                                                                              |
| `OidcTokenEndpointCustomizer` (`resolvedJwks`), host `authorizedClientManager`                                                                          | unchanged: serve the primary chain only; scoped login chains use CSL's own token endpoint and assertion key cache                                                                                       |
| `TokenValidatorFactory` (root), clock skew of tenant ID tokens                                                                                          | unchanged: a tenant's ID-token validator uses the root `clock-skew`, at startup as at runtime                                                                                                           |
| `PhysicalTenantSecurityProperties` (webapp authorization flag, search-side resource access)                                                             | unchanged; `security.authorizations.*` stays `REJECT outside-module`                                                                                                                                    |
| `PhysicalTenantScopedSessionStorePortProvider` adapter cache                                                                                            | unchanged: only with persistent sessions on secondary storage, which runtime tenants cannot have; a removed tenant's adapter stays (one per id, ids are never reused)                                   |
| gRPC per-tenant security (`GatewayPhysicalTenants`)                                                                                                     | already rebuilt by the module restart (slice 0)                                                                                                                                                         |

## Measurements

Apple-silicon laptop, three brokers in one JVM, Keycloak in Docker, warm JVM, other builds idle.
Times are from the API call (or the secret store write) until the effect is seen on every broker by
polling each one every 50–100 ms.

|                               What                               |                                                             Result                                                              |
|------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------|
| Add an OIDC tenant, served (200) on every broker                 | 330–826 ms (5 runs)                                                                                                             |
| Remove, 404 on every broker                                      | 61–70 ms (4 runs)                                                                                                               |
| Change IdP and claims, new token accepted on every broker        | 522–3030 ms (4 runs); each member switches its chains before its module restarts, members one after another                     |
| Same, until rolled out and a command accepted on every broker    | 3.1–6.1 s per change (50 changes over 5 runs of the leak test)                                                                  |
| Client secret rotation, `CREDENTIALS_ROTATED` on every broker    | 765–875 ms after the store write, with a 1 s refresh interval (3 runs)                                                          |
| Unreachable IdP, applied on every broker after it answered again | 12.7–14.4 s (4 runs); dynamic-config's retry backoff starts at 10 s                                                             |
| CSL reload per member (`ScopeReloadResult.duration`, log)        | add 29–69 ms; credential rotation of the boot tenant at startup 113 ms                                                          |
| Boot tenant REST load during a whole class run                   | 202 096, 764 074, 827 245 and 733 434 requests, 0 failed                                                                        |
| Retained over 10 changes                                         | CSL generations 9 → 9 (18 → 18 with the other tests' tenants), client stacks 18 → 18, chains 33 → 33, host converters unchanged |

Two things are left out of the leak comparison. Pooled threads that start on demand: the REST API
executor (`api-service-thread-*`) grew from 6 to 42 threads across the three brokers during the
measured changes, because a `ThreadPoolExecutor` starts a thread per task until its core size; it
is a shared, bounded pool, but its exact bound was not checked against the count. Traffic meters
that register a series per topic, peer or HTTP outcome on first use (`zeebe.messaging.*`,
`http.server.requests`, `zeebe.gateway.failed.requests`), which appeared for other tenants'
traffic and for a 503 while a restarted partition elected its leader.

## Results on this tree (2026-10-29)

|                                                 Suite                                                  |                                                                                                                                                     Result                                                                                                                                                      |
|--------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `./mvnw clean install -Dquickly -T3`                                                                   | green; main and test sources of authentication, configuration, dist, gateway-rest, cluster, zeebe/qa/util, zeebe/qa/integration-tests and qa/acceptance-tests compile with checkstyle                                                                                                                           |
| authentication                                                                                         | 502 unit + 47 IT, 0 failures (baseline before the changes: 491 + 47)                                                                                                                                                                                                                                            |
| configuration                                                                                          | 1449/1449                                                                                                                                                                                                                                                                                                       |
| dist touched packages (secrets, rdbms, pt, service, security, identity, broker, management)            | 1584 passed, 1 skipped (`MyBatisStatementSplitCostTest`, opt-in)                                                                                                                                                                                                                                                |
| gateway-rest (`PhysicalTenant*Test`, filters, topology, message, authentication controllers)           | 106/106                                                                                                                                                                                                                                                                                                         |
| zeebe/qa/integration-tests, the integration branch's 10 classes plus `PhysicalTenantRuntimeSecurityIT` | 45/45 in one run; `PhysicalTenantRuntimeSecurityIT` 8/8 in 4 further full runs                                                                                                                                                                                                                                  |
| qa/acceptance-tests OIDC physical-tenant ITs (9 classes)                                               | 23/24: `PhysicalTenantEncodedPathOidcRoutingIT` expects 400 for a percent-encoded tenant path and gets 404 from `PhysicalTenantServedFilter`, which reads the raw URI; slice 0's version of the filter does the same for that input, so this is expected to fail on `pt-poc-integration` too (not re-run there) |

## Not proven, or not done

- **BASIC tenants at runtime: not possible in this tree.** A BASIC tenant looks users up in its
  secondary storage (`BasicAuthUserDetailsAdapter` → `UserServices`); BASIC with no secondary
  storage fails startup (`BasicAuthenticationNoDbConfiguration`); a tenant added at runtime must have
  `secondary-storage: none` (RDBMS at runtime, step 8 of the integration, is not done); and all
  tenants must share one storage class (`SecondaryStorageTypeHomogeneityValidation`). So a BASIC
  cluster cannot get a runtime tenant at all, and "401 without, 200 with credentials" for an added
  BASIC tenant cannot be shown. CSL itself builds BASIC scopes at runtime (its own tests).
- **Switching a tenant between BASIC and OIDC: rejected by policy.** `security.authentication.method`
  is a cluster-wide, non-overridable key (`PhysicalTenantOverridePolicyValidation`, #54731), and the
  host's and CSL's OIDC and BASIC beans are gated on the root method
  (`@ConditionalOnAuthenticationMethod`: `OidcOverrideBeansConfiguration`, `BasicAuthBeansConfiguration`,
  CSL `UserConfiguration`, …). A per-tenant method needs an identity-team decision, both sets of beans
  in one context (CSL and host), and the BASIC storage problem above solved.
- **`default` tenant and the primary chain (Q13): not changeable at runtime.** The unprefixed `/v2`
  and webapp chains are CSL's primary (unscoped) chains, built once from
  `CamundaSecurityLibraryProperties`; `ScopedSecurityReloadPort` only takes scopes (a base path other
  than `/`). Changing `default` would need: a CSL port for the primary chains (CSL change); rebuilding
  every root-built bean (`tokenClaimsConverter`, `TokenValidatorFactory`, `idTokenDecoderFactory`'s root
  factory, `OidcProviderConfigurationPort`, `ClientRegistrationRepository`, `JwtDecoder`,
  `authorizedClientManager`, `OidcTokenEndpointCustomizer`, the cluster-admin OIDC chain); and, because
  every tenant inherits the root's providers (`PhysicalTenantAuthConfigurations` merges them), applying
  the change to every tenant that inherits them. Changing the root method needs a context restart
  (bean conditions). The lifecycle still rejects a replace of `default`, and the executor reports a
  differing file version `DEGRADED`. Reloading only the `/physical-tenants/default` alias would break
  the invariant that it equals `/v2`, so it is not done either. References in the root's client
  secret are not resolved.
- **Window between chains and host state.** CSL publishes a generation, then
  `PhysicalTenantAuthentications` takes the configuration, within the same lock: a request in that
  window (microseconds) is authenticated by the new chain and mapped with the previous claims. A fix
  needs CSL to hand the generation to the host (for example a per-generation converter supplier, which
  CSL's builder already has a parameter for, `oidcAuthenticationConverterSupplier`, currently `null`).
- **Not tested in an IT:** an added tenant whose chains fail to build (unit-tested filter only);
  the short window between a tenant joining the registry and its chains being added; persistent
  (secondary-storage) sessions of a changed tenant; logout and refresh-token revocation after a
  rotation; named providers (`providers.oidc.<id>`) at runtime (unit-tested only); a standalone
  gateway (no lifecycle).
- **Sessions are per node** (in-memory). A session lives on the broker it was created on; the tests log
  in and check on one broker. During a rolling change, a node still on the old configuration ends a
  session created under the new one (ADR-0040); persistent sessions would make this visible.
- **One change at a time across the cluster** (slice 1): while a tenant waits for its unreachable IdP,
  every other tenant change answers `IN_PROGRESS`. The retry interval is dynamic-config's (10 s to
  1 min).
- **A rotation is eventually consistent.** Values in separate store entries (keystore path and its
  password) can be read in between writes; the rotation then fails, the tenant reports `DEGRADED`, and
  the next run applies it. The IdP must accept the new credential before the store changes.
- **Root-only keys stay rejected**: `authentication-refresh-interval`, `saas.*` (read from the root
  only, a tenant override has no effect at startup either); `authorizations.*`, `id-validation-pattern`
  (also read by startup-built components); `initialization.*` (the engine only adds entities).
- **Observability**: the outcome is in the logs and the actuator's `local` block of the answering
  member; there is no metric per outcome and no cluster-wide view of the security state per member.
- **Retained**: a removed tenant's CSL slot stays as a tombstone (bounded by base paths; ids are never
  reused), and its session store adapter if it had persistent sessions.

### Needs a change in CSL (beyond `1.2.1-pt-poc-SNAPSHOT`)

1. A port for the primary (unscoped) chains, or a way to serve `default` as a scope only, for Q13.
2. Credential marking that a host can read: `ScopeFingerprints.CREDENTIAL_PROPERTIES` is internal, so
   the host's guard keeps its own list (`PhysicalTenantCredentialGuard.CREDENTIALS_OF_OTHER_MODULES`,
   and `PROPERTY_NAMES_OF_OTHER_MODULES` for the keystore's property names). Exposing the list, or
   annotating the fields, would keep the two in step.
3. Handing the serving generation to the host, so per-tenant host state is selected by generation and
   not published separately (window above).
4. Method-independent bean registration (OIDC and BASIC scopes in one context), if per-tenant methods
   are wanted.
5. Optional: a metric or event per reload outcome.

### Needs a decision of the identity team

1. Per-tenant authentication method (BASIC/OIDC), today forbidden.
2. Whether ID tokens must carry the configured API `audiences`: CSL's `TokenValidatorFactory`
   validates ID tokens against them, so the test adds the audience to Keycloak's ID tokens too.
3. `KEEP_PREVIOUS` as the default for an unreachable new IdP keeps trusting the old IdP until it
   answers; withdrawing trust needs `APPLY`. Which one is right per change type (rotation vs IdP
   switch)?
4. Whether a changed tenant's sessions on other nodes should end at once (needs a cluster-wide signal)
   or as each node applies the change (today).
5. Whether the root/`default` tenant must become changeable at runtime (Q13).

## How to run

```bash
# CSL 1.2.1-pt-poc-SNAPSHOT must be in the branch's local repository, for example copied from
# ~/.m2/repository/io/camunda/camunda-security-library* into
# ~/.m2/repository/installed/pt-poc-csl-runtime-security/io/camunda/
./mvnw install -Dquickly -T3 -Dmaven.build.cache.enabled=false
./mvnw verify -pl zeebe/qa/integration-tests -Dquickly -DskipTests=false -DskipUTs \
  -Dit.test='PhysicalTenantRuntimeSecurityIT,PhysicalTenantCredentialGuardIT'
./mvnw verify -pl authentication -Dquickly -DskipTests=false
./mvnw verify -pl configuration -Dquickly -DskipTests=false -DskipITs
```

