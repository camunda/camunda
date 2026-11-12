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

# The default tenant's HTTP security at runtime

Branch `pt-poc-default-tenant-security`, on top of `pt-poc-csl-runtime-security` (ae1a3b56a1ab).
Built against the local CSL proof of concept `1.2.1-pt-poc-default-full-SNAPSHOT` (worktree
`mk-csl-poc-default-full-reload`, spike note `docs/spikes/primary-chains-runtime-reload.md`). Not
for merge as is.

Question: can the `default` physical tenant's HTTP security change at runtime exactly like any other
tenant's, with no node restart? For its HTTP security, yes: a change of
`camunda.physical-tenants.default.security.*` changes `/v2`, the webapps and login (the library's
primary chains) and the alias `/physical-tenants/default` on every broker, keeps the two equal, and
leaves the other tenants alone. What is not the same is everything a module restart applies to
another tenant (gRPC, the engine side, REST services), because the default tenant's module is not
restarted here; that side belongs to the other spike.

## What is proven, and by which test

`PhysicalTenantRuntimeSecurityIT` (zeebe/qa/integration-tests), the cluster of the section above
(three brokers in one JVM, RF 3, Keycloak 26, Toxiproxy, the boot tenant under continuous REST load).
At startup `default` declares authorizations on, admins `svc` and `admin-by-claim`, and its
exporters; everything else of it comes from the root. Every check goes over HTTP to each broker, on
`/v2/...` and on `/physical-tenants/default/v2/...`.

The default tenant is owned by the static configuration, so the management API refuses to change it
(`NOT_OWNER`). The tests change it through `PhysicalTenantConfigService#updateFromFile`, which
replaces a file-owned tenant's subtree as a changed file would and goes through the same
validators, dynamic-config rollout, executor and lifecycle as every change; only the file watcher is
missing (the other spike).

|                     Scenario                      |                                                                                                                                                                                                                                                                               What is checked                                                                                                                                                                                                                                                                               |                                             Test                                              |
|---------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------|
| Change issuer, audience, client id, claim mapping | before: `svc` 200 and `svc-other` 403 on publish, on both paths. After one change: old-IdP tokens 401 on both paths of every broker; the new mapping (`client-id-claim: alt_client`) decides the engine's authorisation on both paths of every broker (new `svc-other` 200, new `svc` 403); the sessions on `/v2` and on the alias (login on broker 0) 401; new logins on `/v2` and on the alias work; each member reports `REPLACED`; the boot tenant keeps its generation on every member, its session (200) and its tokens (200), and the new default token is 401 on it | `shouldChangeTheDefaultTenantsIdentityProviderAndClaimsMappingOnEveryBroker`                  |
| Rotate the client secret                          | Keycloak's rotation policy; the new value written to the default tenant's store; every member reports `CREDENTIALS_ROTATED`; old secret revoked; after the 5 s access token expired, the sessions on `/v2` and on the alias refresh (200); new logins on both work; config version unchanged                                                                                                                                                                                                                                                                                | `shouldRotateTheDefaultTenantsClientSecretFromTheSecretStoreAndKeepTheSessions`               |
| Unreachable new IdP                               | proxy down: broker 0 reports `DEGRADED` with `KEPT_PREVIOUS`, not rolled out; old tokens 200 and new 401 on both paths of every broker; the boot tenant `HEALTHY`. Proxy up: applied by dynamic-config's retry, new tokens 200, old 401, `default` `HEALTHY` on every member                                                                                                                                                                                                                                                                                                | `shouldKeepTheDefaultTenantsPreviousSecurityWhileItsNewIdentityProviderIsUnreachable`         |
| Switch authentication method                      | rejected naming `security.authentication.method`; version unchanged; still serving                                                                                                                                                                                                                                                                                                                                                                                                                                                                                          | `shouldRejectAChangeOfTheDefaultTenantsAuthenticationMethod`                                  |
| No leaks                                          | 10 changes (alternating realms), each awaited on both paths of every broker and rolled out: CSL `ScopeGeneration`, `ScopedOidcClients`, `DefaultSecurityFilterChain`, host bearer and claims converters, threads and meters equal before and after                                                                                                                                                                                                                                                                                                                          | `shouldNotLeakOverManySecurityChangesOfTheDefaultTenant`                                      |
| Other tenants unaffected                          | 0 failed requests of the boot tenant's load during the class                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                | `@AfterAll`                                                                                   |
| Without physical tenants, no generic exporter     | two brokers, no `camunda.physical-tenants.*` key at startup (the default subtree is empty): a session on `/v2` and the startup tokens work; after one change, new tokens 200 and old 401 on both paths of both brokers, the session 401, new logins on both paths work                                                                                                                                                                                                                                                                                                      | `WithoutPhysicalTenants#shouldChangeTheImplicitDefaultTenantsSecurityOnEveryBroker`           |
| Without physical tenants, with a generic exporter | the first key of `default` is refused: a security-only subtree with "must declare `camunda.physical-tenants.default.data.exporters-assigned`", the same subtree with `data.exporters-assigned[0]` with "`'data.exporters-assigned[0]' cannot change at runtime (exporter-ids)`"; still serving                                                                                                                                                                                                                                                                              | `WithoutPhysicalTenantsButAGenericExporter#shouldRejectTheFirstKeyOfTheImplicitDefaultTenant` |

Mutation check (made the named test fail, then reverted): calling only the alias for `default`
(skipping the primary chains) leaves `/v2` on the old issuer; the first scenario times out waiting
for the new token on `/v2`.

Unit/module tests: `authentication` 502 unit + 47 IT, 0 failures; three tests that verified a
root-built mock converter for a request of the default tenant now assert on the converted
authentication (`OidcUserAuthenticationConverterIntegrationTest`, `OidcBearerUserInfoClaimGapIT`,
`PhysicalTenantOidcSettingsRegistryTest`).

## How it works

|                                 Piece                                  |                                                                                                                                                                                            What changed                                                                                                                                                                                            |
|------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| CSL `PrimarySecurityReloadPort`                                        | New: changes the primary chains like a scope, see the CSL spike note. The primary chain beans are views over a slot; a change builds a generation with its own decoder and OIDC client stack and publishes both chains in one write. The primary sessions are bound to the serving configuration, so an authentication change ends them                                                            |
| `PhysicalTenantHttpSecurity`                                           | For `default`: primary chains first, then the alias; if the alias keeps its previous security, the primary chains are put back (`APPLY` with the served configuration; not covered by a test: with an unreachable IdP the primary chains already keep their previous security, so the alias is not asked). `rotateCredentials` and `refreshCredentials` likewise. `remove` still refuses `default` |
| `OidcOverrideBeansConfiguration`, `PhysicalTenantOidcSettingsRegistry` | `default` uses the converters and OIDC settings derived from `PhysicalTenantAuthentications` like every tenant; the root-built ones serve only a conversion outside any request                                                                                                                                                                                                                    |
| `PhysicalTenantConfigExecutor`                                         | A version of `default` whose changed keys are all `SECURITY` goes to the lifecycle; any other change of `default` is still `DEGRADED` "takes it only with a node restart"                                                                                                                                                                                                                          |
| `PhysicalTenantLifecycleService`                                       | `replace("default")` changes only the HTTP security (same `replaceSecurity` as every tenant, a kept-back change rejects the replace), records the new configuration and environment, closes the candidate context; it does not stop or start the module                                                                                                                                            |
| `PhysicalTenantRestSecurity.startWith`                                 | `default` included: its credential references are resolved at startup and its secret refresher rotates them                                                                                                                                                                                                                                                                                        |
| `PhysicalTenantConfigService#updateFromFile`                           | Test entry for a file-owned tenant (above)                                                                                                                                                                                                                                                                                                                                                         |

## Root-built beans: what each depends on, and what it costs

Code reading unless a test is named. "Default's startup configuration" means `cslProperties`
after `physicalTenantClusterAuthUnification`, which copies `default`'s resolved configuration into
it when any physical tenant is configured (and the raw root otherwise, which is then equal).

|                                                Bean                                                |                                   Built from                                   |                                                                                                              Used by `default` after this branch                                                                                                               |                                                              Cost to follow `default`                                                              |
|----------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------|
| CSL primary `JwtDecoder`                                                                           | default's startup configuration                                                | startup generation only; later generations use their own `ScopedJwtDecoderFactory` decoder (test: first scenario)                                                                                                                                              | done here (part of the CSL port)                                                                                                                   |
| CSL primary `ClientRegistrationRepository`                                                         | default's startup configuration                                                | startup generation only; generations own their registrations (test: logins after a change)                                                                                                                                                                     | done here                                                                                                                                          |
| host `authorizedClientManager` (private_key_jwt refresh, observation)                              | primary registrations, `AssertionJwkProvider`                                  | startup generation only; generations use CSL's per-generation manager, without the host's observation `RestClient` (as scopes)                                                                                                                                 | done; S for an observation hook in CSL                                                                                                             |
| host `OidcTokenEndpointCustomizer` (`resolvedJwks`, observation)                                   | `AssertionJwkProvider`                                                         | startup generation only; generations use CSL's token endpoint (as scopes)                                                                                                                                                                                      | done; same hook                                                                                                                                    |
| `OidcProviderConfigurationPort`                                                                    | default's startup configuration                                                | not by `default`'s chains after a change. Still read for every tenant's session access-token decoding (`AdditionalJwkSetUrisByRegistrationId`, `preferIdTokenClaims` by registration id) and by the root path                                                  | done for `default`; the per-registration-id lookups are a pre-existing gap of every tenant, M to make per tenant                                   |
| `tokenClaimsConverter` (root `LazyTokenClaimsConverter`)                                           | default's startup claims                                                       | REST bearer and login conversion of `default` now use derived converters (test: engine authorises by the new claim on both paths). Still used by the search-side `AuthorizationCheckPort` resolver for every tenant (not exercised: no secondary storage here) | done (S) for REST; search side not checked                                                                                                         |
| `TokenValidatorFactory` (root, with SaaS validators, root clock skew)                              | default's startup providers                                                    | not by `default`'s chains after a change: the generation's decoder uses CSL's validator with the default clock skew and no host extras, as every scope                                                                                                         | done; S/M for a CSL hook for clock skew and host validators                                                                                        |
| `idTokenDecoderFactory`'s root factory                                                             | default's startup providers                                                    | not by `default`: it now picks the derived settings like any tenant (test: logins after a change on both paths)                                                                                                                                                | done (S)                                                                                                                                           |
| cluster-admin OIDC chain (`/cluster/v2/**`)                                                        | primary `JwtDecoder` bean + default's startup `client-id-claim`/`groups-claim` | not changed: keeps default's startup issuer and claims after a change of `default`                                                                                                                                                                             | decision needed: root-only is S (build from `camunda.security.authentication.*`), following `default` is M (a host-side reload of a non-CSL chain) |
| `ProtectedResourceMetadataCustomizer` (RFC 9728 metadata on every OIDC API chain, scopes included) | primary `ClientRegistrationRepository`                                         | advertises default's startup issuers after a change, as it does for every tenant today                                                                                                                                                                         | S/M: pass the chain's issuers per generation                                                                                                       |
| cluster `/v2` unification `BeanPostProcessor`                                                      | startup only                                                                   | `cslProperties` stays on default's startup configuration; nothing on `default`'s request path reads it after a change except the cluster-admin chain and the CSL options read once (authorize max-age, diagnostics, headers)                                   | none for HTTP security                                                                                                                             |

No bean was genuinely hard to make follow `default`: every per-request consumer either became
tenant-derived (S) or is no longer reached once the primary chains serve a CSL-built generation.

## Other tenants are unaffected by a change of `default`

Code reading: `PhysicalTenantAuthConfigurations.forPhysicalTenant(id, env)` binds
`camunda.security.authentication` (the root) and then `camunda.physical-tenants.<id>.security.authentication`;
no tenant reads `camunda.physical-tenants.default.*`, and the lifecycle hands each tenant its own
descriptor. Test: during the first scenario the boot tenant keeps its generation on every member,
its session and tokens keep working, the new default token is 401 on it, and its load sees 0 failed
requests over the class. Only the startup unification copies `default` into `cslProperties`, and
that is read by the cluster-admin chain (above), not by other tenants.

## Without physical tenants

- With no generic exporter in the root catalog, it works the same (nested test).
- With one (the recording exporter in the test; whether the production Camunda exporter counts as
  generic was not checked), the first key under `camunda.physical-tenants.default` makes `default`
  explicitly declared, which requires `data.exporters-assigned`, a key that is `REJECT exporter-ids`
  at runtime. So `default` cannot be changed at runtime in such a deployment until it declares its
  exporters at startup. This is a configuration-model obstacle, not a security one: exempting a
  default declared only by security keys, or comparing the effective exporter sets instead of the
  keys, is S/M.

## Measurements

Apple M5 Pro, three brokers in one JVM, Keycloak in Docker, another heavy build running. Times from
the call (or the store write) until seen on both paths of every broker, polling every 50 ms.

|                                     What                                     |                                               Result                                                |
|------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------|
| Change IdP and claims, new token accepted on `/v2` and alias on every broker | 307–893 ms (5 runs)                                                                                 |
| Same, until rolled out (10 changes per run)                                  | 337–885 ms per change (4 runs); another tenant's change takes 3.3–5.8 s because its module restarts |
| Implicit default (two brokers)                                               | 176–699 ms (3 runs)                                                                                 |
| Client secret rotation, `CREDENTIALS_ROTATED` on every broker                | 241–863 ms after the store write, 1 s refresh interval (4 runs)                                     |
| Unreachable IdP, applied on every broker after it answered                   | 9.3–11.2 s (4 runs); dynamic-config's retry backoff starts at 10 s                                  |
| CSL change per member (log, every outcome incl. `UNCHANGED` at startup)      | primary chains 3–89 ms (p50 24 ms, 232 calls over all runs); alias 17–36 ms (one run)               |
| Boot tenant REST load, whole class                                           | 1,330,553 and 1,374,342 requests (2 full-class runs), 0 failed                                      |

## Results on this tree (2026-11-12)

|                                                                                                                                                           Suite                                                                                                                                                            |                                                                                                  Result                                                                                                   |
|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `./mvnw install -Dquickly -T2`                                                                                                                                                                                                                                                                                             | green, every module                                                                                                                                                                                       |
| CSL `./mvnw verify`                                                                                                                                                                                                                                                                                                        | api 241, core 180, validation 267, spring-boot-starter 1,757, 0 failures                                                                                                                                  |
| authentication                                                                                                                                                                                                                                                                                                             | 502 unit + 47 IT, 0 failures                                                                                                                                                                              |
| zeebe/qa/integration-tests `PhysicalTenantRuntimeSecurityIT` (15) + `PhysicalTenantCredentialGuardIT` (9)                                                                                                                                                                                                                  | 24/24 in 2 full runs; the 5 default scenarios 5/5 in 2 more runs, the 2 nested 2/2 in 1 more                                                                                                              |
| qa/acceptance-tests OIDC ITs (11 classes: `PhysicalTenantOidcLogin*`, `PhysicalTenantRestOidc*`, `PhysicalTenantOidcClaimInheritanceIT`, `PhysicalTenantOidcClientSecretIT`, `PhysicalTenantEncodedPathOidcRoutingIT`, `OidcNamedProvidersGlobalClaimsIT`, `OidcNoSecondaryStorageIT`, `ClusterAdminOidcAuthenticationIT`) | 38/39: only `PhysicalTenantEncodedPathOidcRoutingIT` fails, as on `pt-poc-csl-runtime-security` (see above). A first run also failed `PhysicalTenantOidcLoginSeparateIdpsIT`: the regression below, fixed |

## Not done, or not proven

- **The module side of a SECURITY change.** Another tenant's SECURITY change also restarts its
  module, which rebuilds its gRPC security (`GatewayPhysicalTenants`), its REST services with their
  `BrokerRequestAuthorizationConverter` (groups claims in broker requests depend on `groups-claim`)
  and its engine. `default`'s module is not restarted here, so gRPC keeps its startup security and a
  `groups-claim` change reaches REST conversion but not what the module reads (code reading). `S` for
  gRPC (`gateway.put`), the rest belongs to the module-restart spike.
- **Two publishes.** The primary chains and the alias are separate CSL slots: for the few
  milliseconds between them `/v2` and the alias serve different configurations. Atomic needs a CSL
  call that builds and publishes both (M).
- **Sessions are per node**, as for other tenants. **Persistent sessions**, logout and refresh-token
  revocation after a change of `default`, named providers and `private_key_jwt` on the primary chains
  in an IT: not tested.
- **Root configuration changes** (`camunda.security.authentication.*`) still apply only at node
  start; other tenants inherit them.
- **BASIC**: the CSL port refuses BASIC primary chains; the method stays cluster-wide.
- **Regression found and fixed on the way:** the in-place credential rotation of
  `mk-csl-poc-default-chain-reload` made the application's `OAuth2AuthorizedClientRepository`
  rebind every stored client to the primary registration of the same id, which broke
  `PhysicalTenantOidcLoginSeparateIdpsIT` (a tenant on another IdP read no username). Fixed in CSL
  `8526e673`.

## Cost

|                                              Piece                                               |                         Size                          |                                                      Genuinely hard?                                                       |
|--------------------------------------------------------------------------------------------------|-------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------|
| CSL primary chain port (views, generations, session binding, config-driven primary webapp chain) | M (+885/−236 main, +522/−250 tests)                   | No: the scope machinery fits; the only design choice is that later generations use CSL's stack instead of the host's beans |
| Host-side derived state for `default`                                                            | S (camunda main code of this branch in all: +164/−57) | No                                                                                                                         |
| Lifecycle, executor, startup credentials for `default` (security only)                           | S                                                     | No                                                                                                                         |
| Primary and alias in one atomic publish                                                          | M                                                     | No, a CSL API addition                                                                                                     |
| Cluster-admin chain                                                                              | S or M                                                | Needs a decision, not work                                                                                                 |
| Host hooks per generation (observation, SaaS validators, clock skew)                             | M (CSL)                                               | No; also missing for every tenant                                                                                          |
| Implicit `default` with a generic exporter                                                       | S/M                                                   | A configuration-model rule, not security                                                                                   |
| The module side of `default` (gRPC, REST services, engine)                                       | other spike                                           | Restarting the default tenant's module is the open question there                                                          |

## Open questions for the identity team

1. Should `/cluster/v2/**` (cluster admin) validate against the root identity provider or follow
   `default`? Today it silently follows `default`'s startup configuration when physical tenants are
   configured.
2. Is it acceptable that after the first change `default` is served by CSL-built generations, like
   every tenant, without the host's customised beans (observation of token requests, SaaS
   organisation/cluster validators, configured clock skew)? Or does CSL need a per-generation hook?
3. Should `/v2` and `/physical-tenants/default` change atomically (one CSL call), or is a
   milliseconds window acceptable?
4. `KEEP_PREVIOUS` for an unreachable new IdP of `default`, as for every tenant?
5. Should a default tenant declared only by security keys be exempt from the exporter manifest rule?

## How to run

```bash
# CSL 1.2.1-pt-poc-default-full-SNAPSHOT (worktree mk-csl-poc-default-full-reload, ./mvnw install
# -DskipTests) must be in the branch's local repository: copy
# ~/.m2/repository/io/camunda/camunda-security-library*/1.2.1-pt-poc-default-full-SNAPSHOT into
# ~/.m2/repository/installed/pt-poc-default-tenant-security/io/camunda/
./mvnw install -Dquickly -T2 -Dmaven.build.cache.enabled=false
./mvnw verify -pl zeebe/qa/integration-tests -Dquickly -DskipTests=false -DskipUTs \
  -Dmaven.build.cache.enabled=false '-Dit.test=PhysicalTenantRuntimeSecurityIT*,PhysicalTenantCredentialGuardIT'
./mvnw verify -pl authentication -Dquickly -DskipTests=false -Dmaven.build.cache.enabled=false
```

