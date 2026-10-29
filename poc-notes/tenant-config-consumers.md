# Silent stale values: who copies a tenant's configuration, and does a restart really apply it?

Branch `pt-poc-tenant-config-consumers`, on top of `ae1a3b56a1ab` (the integrated proof of concept with
runtime HTTP security). Local only. Written for Carlo Sana, DRI of the No-Restart Physical Tenant
Lifecycle API epic. It answers risk 1 of the scoping ("a key can look applied and not be"), the
visibility unknown in section 7, and the question "how hard is it to make the tenant list dynamic,
and does it create any unforeseen issue?".

## Summary

- **The classification was mostly right.** A differential harness checked 124 of the 125 `RESTART` keys
  against the objects that consume them. For 113 keys, a module restart gives the tenant exactly what a
  boot with the new value gives it, both for a replaced tenant and for a tenant added at runtime.
- **11 entries were wrong (10 found by the harness, 1 by reading code), all fixed on this branch:**
  - 6 job-metrics keys (`monitoring.metrics.job-metrics.*`) were applied by the engine but not by the
    REST API. Fixed in the consumer (`9d51833a1470`).
  - 3 keys never reach a tenant, not even at boot: `system.clock-controlled`, `monitoring.jfr`,
    `monitoring.metrics.actor`. Now `REJECT root-only`.
  - `cluster.partition-count` is accepted, reported as applied, and changes nothing. Now
    `REJECT partitions` (a new reason). This contradicts the proposed Q3 answer ("accepted, no
    effect"); revert it if that answer stands, but then the API should say so.
  - `monitoring.metrics.enable-exporter-execution-metrics` changes the exporter set, which the cluster
    configuration fixes at provisioning. Now `REJECT exporter-ids` (from code; see below).
- **One more is wrong on RDBMS only** (`data.wait-states.enabled`, read by `RdbmsExporterFactory`). It is
  in flight on `pt-poc-runtime-secondary-storage`. Not changed here.
- **Some `REJECT` keys are applied anyway** by a module restart. `document.thread-pool-size` reaches the
  replaced and the added tenant's REST services. It stays rejected, because each change leaks the old
  document thread pool (see unforeseen issues).
- **Consumers.** 24 consumers outside the tenant module copy a tenant's configuration (not counting
  RDBMS, which adds about 15). 2 of them are live now (REST config, storage type). 22 remain.
- **Tenant-set freezes.** 27 call sites (not counting RDBMS, about 13) still know only the boot tenants;
  2 are live now, so 25 remain. 14 more were already made live by earlier commits of this tree.
- **Most expensive to make live:** Elasticsearch/OpenSearch secondary storage (one chain of about 15
  beans, L). On an ES/OS cluster a runtime tenant must itself use ES/OS (storage homogeneity), and its
  REST services cannot be built today. Next: the standalone gateway (L), backup and checkpoint
  scheduling (M), the cluster-wide fan-outs (S–M each).
- **Visibility.** No endpoint reports a wrong value as current by default. `/actuator/env` is disabled by
  default; enabled, it shows the boot value of a changed file tenant (stale) and nothing for an API
  tenant. `/actuator/configprops` shows only the root. The `physicalTenants` API shows versions, not
  values.
- **Recommendation.** Keep the classification approach, and make the harness a gate: every new
  `RESTART` key must be found in a consumer by `PhysicalTenantKeyEffectIT`, or listed as not held on
  the heap. Make the cheap consumers live now (backup actuator, cluster fan-outs, REST secret stores).
  Treat ES/OS for runtime tenants as its own work item: until then, reject a runtime add on a node whose
  boot tenants use ES/OS, instead of letting it half-work.

## How the harness works

`PhysicalTenantKeyEffectIT` (zeebe/qa/integration-tests) runs one broker in the test JVM. For each batch:

1. Configuration A: tenant `a` boots with value X.
2. Configuration B: tenant `b` boots with value Y and is replaced at runtime with X.
3. Tenant `c` is added at runtime with X.
4. `EffectiveState` walks the object graph reachable from every Spring singleton, breadth first. JDK
   objects are entered only through their API (collections, maps, optionals, atomics, thread pools);
   every other object field by field. It reports where each value is held, by the class and field
   that holds it (the "consumer") and a shortest path.
5. A, B and C are compared by consumer, not by path: a consumer that holds X for `a` must hold the new
   value for `b` and for `c`, and no consumer may still hold Y.

Numbers, durations, sizes, decimals and strings use unique sentinel values (for example
`7_331_011 ms`), so one broker tests 34 keys at once. Durations are matched in milli-, micro- and
nanoseconds, sizes in bytes. Flags and choices cannot be sentinels: for them the harness attributes
leaves to a tenant when the path names it, and compares the replaced tenant with a tenant restarted
with an unchanged configuration, and the added tenant with a boot tenant. A twin boot tenant gives
the noise baseline.

Bookkeeping is ignored, because it is not a consumer: the boot snapshots
(`physicalTenantResolver.resolved`, `systemContext.physicalTenantContexts`), the lifecycle's own
records, the applied subtrees, the Spring environment and the cluster configuration, and objects of
the configuration classes themselves.

Cost: a walk takes 0.2–0.9 s; a batch with boot, replace, add and walk takes 4–8 s; the whole class
(13 tests, 9 brokers) takes about 50 s on an Apple-silicon laptop. Reports land in
`zeebe/qa/integration-tests/target/physical-tenant-key-effect/`.

Limits:

- The walk sees the Java heap only. A value handed to RocksDB, written to the log, or combined with
  other values is not found (5 keys, listed in `NOT_HELD_ON_THE_HEAP`).
- Static fields are not roots.
- A consumer that reads the boot resolver at call time keeps no copy, so the walk cannot see it. The
  code inventory below covers those; two were proven by targeted tests.
- Only `secondary-storage: none`. ES/OS and RDBMS consumers are not exercised.
- `SECURITY` keys are not in the harness. They need an identity provider;
  `PhysicalTenantRuntimeSecurityIT` covers them end to end (not re-run here).

## Harness results

|                                              What                                               | Keys |                                                                                                                                                                                                                       Result                                                                                                                                                                                                                        |
|-------------------------------------------------------------------------------------------------|------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Engine, processing, exporting, disk, log, RocksDB numbers, durations, sizes, strings (`engine`) | 64   | applied on replace and add, after the REST fix. `log-segment-size`: the open segment keeps the old size, the next one gets the new size (journal design)                                                                                                                                                                                                                                                                                            |
| Request flow control, 6 algorithms                                                              | 20   | applied in `RequestLimiter` and the Netflix limit objects; legacy-vegas alpha/beta are combined into other values and not found as such                                                                                                                                                                                                                                                                                                             |
| Exporter arguments (`data.exporters`)                                                           | 1    | a tenant-private exporter is configured with the new arguments on replace and add                                                                                                                                                                                                                                                                                                                                                                   |
| Flags and choices (`flags-and-choices`)                                                         | 37   | no consumer of a replaced tenant differs from an unchanged restart; for an added tenant only the known freezes differ (below)                                                                                                                                                                                                                                                                                                                       |
| Root-only check                                                                                 | 3    | `system.clock-controlled`, `monitoring.jfr`, `monitoring.metrics.actor` change no consumer of the tenant                                                                                                                                                                                                                                                                                                                                            |
| Partition count                                                                                 | 1    | after a replace from 1 to 2, the tenant reports 2 and runs 1 partition                                                                                                                                                                                                                                                                                                                                                                              |
| Not tested                                                                                      | 1    | `processing.engine.storage-ordinals.enable-archiverless` needs secondary storage                                                                                                                                                                                                                                                                                                                                                                    |
| Reverse, `REJECT outside-module` sample                                                         | 11   | `checkpoint-interval`: the checkpoint scheduler keeps the old interval and has no schedule for an added tenant (REJECT is right). `document.thread-pool-size`: the replaced and the added tenant's REST services get the new value (REJECT is conservative). Others (`api.rest.process-cache.*`, `secrets.cache.*`, `secrets.max-concurrency`, `history-deletion.*`, `extension-properties.*`) have no consumer without secondary storage or stores |
| Three brokers, through the API and dynamic-config                                               | 2    | `PhysicalTenantConfigRolloutIT#shouldGiveTheEngineAndTheRestApiOfEveryBrokerTheChangedValues`: on every broker the new value is in the engine, the stream processor and the REST config, and the old one only in the boot snapshot                                                                                                                                                                                                                  |

### Stale values found, and what was done

|                                  Keys                                   |                                                                                                                      Symptom                                                                                                                       |                                                                                Done                                                                                 |
|-------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `monitoring.metrics.job-metrics.{enabled, export-interval, max-*}`      | engine had the new values; `GET /v2/system/configuration` and the job-metrics endpoint gate kept the old ones; an added tenant failed with "No REST config for physical tenant"                                                                    | `PhysicalTenantRestConfiguration` reads the lifecycle's current configuration (`9d51833a1470`)                                                                      |
| `system.clock-controlled`, `monitoring.jfr`, `monitoring.metrics.actor` | accepted, no effect, not even at boot (node-wide components built from the root)                                                                                                                                                                   | `REJECT root-only` (`758223d6242d`)                                                                                                                                 |
| `cluster.partition-count`                                               | accepted, configuration says 2, tenant keeps 1 partition                                                                                                                                                                                           | `REJECT partitions` (`758223d6242d`)                                                                                                                                |
| `monitoring.metrics.enable-exporter-execution-metrics`                  | adds or removes the `MetricsExporter` id in `BrokerCfg.init`; the exporter-id rule only looks at `data.exporters.*` names; the cluster configuration lists exporters only at provisioning (`StaticConfigurationGenerator.generatePartitionConfig`) | `REJECT exporter-ids` (`758223d6242d`). From code; the single-broker harness could not attribute the metrics exporter to a tenant                                   |
| storage type of a tenant added at runtime (`databaseTypeProvider`)      | a `@RequiresSecondaryStorage` request answered 400 ("unknown physical tenant") instead of the 403 a boot tenant without storage gets                                                                                                               | reads the lifecycle's configuration (`8f05648eba6f`), `PhysicalTenantRuntimeLifecycleIT#shouldAnswerASecondaryStorageRequestOfATenantAddedAtRuntimeLikeABootTenant` |
| `data.wait-states.enabled` on RDBMS                                     | `RdbmsExporterFactory` keeps the boot value                                                                                                                                                                                                        | in flight on `pt-poc-runtime-secondary-storage`                                                                                                                     |

## Inventory (a): consumers that copy a tenant's configuration outside the module

Built from code by reading every reader of `PhysicalTenantResolver`, `PhysicalTenantContext` and the
per-tenant helpers. Rows marked **H** were confirmed by the harness. "Add" says what a tenant added at
runtime gets. Paths are relative to `dist/src/main/java/io/camunda/` unless they start with a module.

| #  |                                                                        Consumer (file, bean)                                                                         |                                                        Keys                                                         |                       Restart rebuilds it?                        |                   Stale symptom                   |                                                         Add                                                          |                           Classification today                            |                            Cost to make live                            |
|----|----------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------|---------------------------------------------------|----------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------|-------------------------------------------------------------------------|
| 1  | `zeebe/broker/.../bootstrap/CheckpointSchedulerServiceStep` → `CheckpointSchedulingService`                                                                          | `primary-storage.backup.{checkpoint-interval, schedule, offset, retention.*, continuous, store}`                    | no (broker step)                                                  | silent: old schedule and retention, old store     | nothing scheduled                                                                                                    | REJECT, right (**H**)                                                     | M: per-tenant add/remove on the service, or move it into the module     |
| 2  | `zeebe/shared/management/BackupEndpoint` (`backupRuntime`, `physicalTenantBackups`)                                                                                  | `backup.{continuous, schedule, checkpoint-interval, offset}`                                                        | no                                                                | silent: wrong backup-id mode                      | "unknown tenant" (**H**)                                                                                             | REJECT, right                                                             | S: build per call from `lifecycle.configuration(id)`                    |
| 3  | `application/commons/rest/PhysicalTenantRestConfiguration#physicalTenantRestConfigProvider`                                                                          | `monitoring.metrics.job-metrics.*`, `api.rest.cluster-variable.max-metadata-size`                                   | **now yes**                                                       | was silent: old job-metrics settings              | was an error                                                                                                         | RESTART, was **wrong** (**H**)                                            | done (`9d51833a1470`)                                                   |
| 4  | `application/commons/service/CamundaServicesConfiguration`: cluster-wide `ClusterRecoveryServices`, `ClusterRuntimeBackupServices`, `ClusterStatus/TopologyServices` | `secondary-storage.type`, `backup.*`                                                                                | no (`PhysicalTenantServiceRegistry` routes only per-tenant calls) | silent: old modes in cluster restore and backup   | missing from fan-outs (**H**)                                                                                        | REJECT, right                                                             | M                                                                       |
| 5  | same, `SecretServices` with the boot `secretStoreRegistries`                                                                                                         | `secrets.stores.*`, `secrets.cache.*`, `secrets.max-concurrency`                                                    | no                                                                | silent: REST secret API reads old stores          | no stores (**H**)                                                                                                    | REJECT, right                                                             | M: the module builds its own stores already; hand them to the REST side |
| 6  | `application/commons/secrets/SecretStoreConfiguration#internalConfigSecrets`                                                                                         | internal-config store                                                                                               | refresher yes, boot map no                                        | harmless off RDBMS                                | absent                                                                                                               | REJECT, right                                                             | S                                                                       |
| 7  | `application/commons/pt/DatabaseTypeProviderConfiguration#databaseTypeProvider`                                                                                      | `secondary-storage.type`                                                                                            | **now yes**                                                       | none (storage-location)                           | was 400 instead of 403                                                                                               | REJECT storage-location, right                                            | done (`8f05648eba6f`)                                                   |
| 8  | `application/commons/search/PhysicalTenantSearchClientReadersConfiguration#searchClients` (ES/OS)                                                                    | ES/OS `url(s)`, credentials, TLS, proxy, timeouts, pool sizes, plugins                                              | no                                                                | error or silent: old credentials                  | no client                                                                                                            | REJECT, right                                                             | L (chain 8–19, 22)                                                      |
| 9  | same, `physicalTenantDocumentSearchClients`, `physicalTenantQueryExecutors`                                                                                          | as 8 + `index-prefix`                                                                                               | no                                                                | as 8                                              | missing                                                                                                              | REJECT, right                                                             | in L                                                                    |
| 10 | same, `physicalTenantSearchClientReaders`                                                                                                                            | `api.rest.process-cache.*` + 8, 9                                                                                   | no                                                                | silent: old cache sizing in readers               | missing                                                                                                              | REJECT, right                                                             | in L                                                                    |
| 11 | `application/commons/search/SearchClientReaderConfiguration#physicalTenantScopedIndexDescriptors`                                                                    | `index-prefix`, `type`                                                                                              | no                                                                | none (storage-location)                           | missing                                                                                                              | REJECT storage-location, right                                            | in L                                                                    |
| 12 | `application/commons/search/PhysicalTenantSearchEngineConfigurations#searchEngineConfigurationsByTenant`                                                             | shards, replicas, template priority, refresh interval, retry, create-schema, ILM policies, retention, version check | no                                                                | silent: old index settings and ILM                | missing                                                                                                              | REJECT, right                                                             | in L                                                                    |
| 13 | `application/commons/search/SearchEngineDatabaseConfiguration#searchEngineSchemaInitializer`                                                                         | as 12                                                                                                               | no                                                                | schema/ILM never re-applied                       | no schema, readiness never ready                                                                                     | REJECT, right                                                             | M                                                                       |
| 14 | same, `searchEngineStatusHealthIndicator`                                                                                                                            | as 8, 12                                                                                                            | no                                                                | health on old client                              | absent from health                                                                                                   | REJECT, right                                                             | S once 8 is live                                                        |
| 15 | `application/commons/search/ElasticsearchSchemaMigrationStatusProviderConfiguration`                                                                                 | as 12                                                                                                               | no                                                                | old settings                                      | absent from `upgradeReadiness`                                                                                       | REJECT, right                                                             | S once 12 is live                                                       |
| 16 | `application/commons/backup/BackupServiceRegistryConfiguration#backupServiceRegistry` (+ webapp `BackupController`)                                                  | ES/OS `backup.{repository-name, snapshot-timeout, incomplete-check-timeout}` + 8, 11, 12                            | no                                                                | silent: snapshots to the old repository           | absent                                                                                                               | REJECT, right                                                             | M                                                                       |
| 17 | `application/commons/search/SearchClientConfiguration#camundaSearchClients` (also given to the broker)                                                               | derived from 8, 10, 19                                                                                              | no                                                                | old clients and caches                            | **on ES/OS the REST services of an added tenant cannot be built** (`CamundaSearchClients` "Unknown physical tenant") | —                                                                         | in L                                                                    |
| 18 | `application/commons/security/AuthorizationCheckerProviderConfiguration`                                                                                             | from 10                                                                                                             | no                                                                | old readers                                       | "No AuthorizationChecker registered"                                                                                 | —                                                                         | in L                                                                    |
| 19 | `application/commons/search/ResourceAccessControllerConfiguration` (document controllers, `physicalTenantResourceAccessProvider`)                                    | `security.authorizations.enabled` + 10                                                                              | no                                                                | silent: authorization flag stuck for search reads | error                                                                                                                | REJECT, right                                                             | M                                                                       |
| 20 | `application/commons/security/PhysicalTenantSecurityPropertiesConfiguration`                                                                                         | `security.*` (uses `authorizations.enabled`)                                                                        | no                                                                | feeds 19, 21                                      | absent                                                                                                               | REJECT, right                                                             | M                                                                       |
| 21 | `application/commons/security/WebAppAuthorizationCheckPortConfiguration` (`authorizationCheckPort`, `webAppAuthorizationEnabledPort`)                                | `security.authorizations.enabled`                                                                                   | no                                                                | silent: webapp authorization uses the old flag    | check port throws; enabled port defaults to true                                                                     | REJECT, right                                                             | M                                                                       |
| 22 | `application/commons/identity/WebSessionRepositoryConfiguration` (search session client + store-port cache)                                                          | from 8, 11                                                                                                          | no                                                                | sessions with the old client                      | fails on `withPhysicalTenant`                                                                                        | —                                                                         | in L                                                                    |
| 23 | `zeebe/gateway/GatewayModuleConfiguration` (standalone gateway profile)                                                                                              | `security.authentication.*`, authorizations, multi-tenancy, initialization, id patterns                             | **no runtime path at all**                                        | old OIDC on gRPC                                  | unknown tenant                                                                                                       | SECURITY is right only on brokers                                         | L: a gateway needs to follow the cluster configuration                  |
| 24 | dynamic-config provisioning (`StaticConfigurationGenerator`, `DynamicClusterConfigurationService#provisionPhysicalTenant`), persisted                                | `cluster.partition-count`, exporter id set                                                                          | no (written once per group)                                       | silent (**H** for partition count)                | provisioned correctly                                                                                                | partition-count and exporter-execution-metrics were **wrong**, now REJECT | reclassify (done); a real change is a scaling or exporter operation     |

RDBMS, listed only, **in flight on `pt-poc-runtime-secondary-storage`**: `RdbmsExporterConfiguration`
(`rdbmsExporterFactory`, `rdbmsExporterDescriptor`), `MyBatisConfiguration` (`rdbmsDataSources`,
`physicalTenantMyBatisResources`, `rdbmsMapperBundles`, `rdbmsSchemaInitializer`,
`rdbmsSchemaMigrationStatusProvider`, schema health), `RdbmsConfiguration` (`rdbmsTableRowCountMetrics`,
`physicalTenantSearchClientReaders`, `camundaSearchClients`, `rdbmsServiceFactory`), the exported-position
suppliers in `BrokerModuleConfiguration`, `WebSessionRepositoryConfiguration` (RDBMS client),
`ResourceAccessControllerConfiguration` (RDBMS controllers). About 15 beans.

Rebuilt by the module restart, so not counted (all confirmed by the harness where they hold a value):
the partition manager and partitions, engine configuration and processors, stream processor, exporter
director and exporters, RocksDB configuration, disk monitor, log stream and flow control, embedded
gateway, per-tenant REST/MCP services (incl. the document store and process cache), secret refresher
and job stores, HTTP security state.

### Compared with "about 25"

24 rows above. Merged rows: 9, 19, 21, 22 and 16 each stand for 2 beans, so 29 beans. 15 of them read
the resolved `Camunda` directly; with about 7 RDBMS beans that also do, about 22 read it directly.
"About 25" matches either count. Carlo's examples are all in: REST config = 3 (and 10), backup = 1, 2,
4, 16, document store = rebuilt (blocked only by 10 and 17), RDBMS audit settings =
`RdbmsExporterFactory`, exporter id set = 24.

## Inventory (b): call sites that freeze the tenant set

The live set is `DefaultPhysicalTenantRegistry`; it is the `@Primary` `PhysicalTenantIds`
(`UnifiedConfigurationModule`, made primary in `b6e2ad47fd04`). A bean that asks the registry on each
call is live. A bean that takes the resolver, or copies `known()` or `getAll()` once, is frozen.

### Still frozen (non-RDBMS)

|                                                                                                                                                    Call site                                                                                                                                                     |                     Added at runtime                     |              Removed at runtime               | Cost |
|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------|-----------------------------------------------|------|
| `CheckpointSchedulerServiceStep` → `CheckpointSchedulingService`                                                                                                                                                                                                                                                 | no checkpoints, no continuous backup (**H**)             | its scheduler keeps sending checkpoints       | M    |
| `GatewayModuleConfiguration` (standalone gateway)                                                                                                                                                                                                                                                                | 404/UNAVAILABLE until restart                            | still served                                  | L    |
| `CamundaServicesConfiguration`: `ClusterStatusServices`/`ClusterTopologyServices` (`Map.copyOf`)                                                                                                                                                                                                                 | missing from `/cluster/v2` status and topology           | still reported                                | S–M  |
| `ClusterRecoveryServices`                                                                                                                                                                                                                                                                                        | cluster restore cannot name it                           | stale entry                                   | S    |
| `ClusterRuntimeBackupServices`                                                                                                                                                                                                                                                                                   | silently missing from the cluster backup fan-out (**H**) | still targeted; likely fails the whole backup | S–M  |
| `ClusterHistoryBackupServices` (`getAll().keySet()`)                                                                                                                                                                                                                                                             | missing from history backup                              | still targeted                                | S    |
| REST `SecretServices` stores (`SecretStoreRegistries`)                                                                                                                                                                                                                                                           | no stores, silently (**H**)                              | stores stay open                              | M    |
| `BackupEndpoint` (`backupRuntime`)                                                                                                                                                                                                                                                                               | "unknown tenant", missing from fan-out (**H**)           | still targeted                                | S    |
| `SecondaryStorageReadinessMetrics.bindTo`                                                                                                                                                                                                                                                                        | no gauge                                                 | keeps reporting                               | S    |
| `searchEngineStatusHealthIndicator` (flat for 1 tenant, composite for more, decided once)                                                                                                                                                                                                                        | missing from health                                      | still probed                                  | S–M  |
| ES/OS chain: `searchClients`, document clients, query executors, readers, engine configurations, index descriptors, `camundaSearchClients`, access controllers, schema initializer (+ health), migration status, authorization checker, webapp check ports, security properties, session client, backup registry | ES/OS tenant cannot be served (see 17 above)             | clients and pools leak                        | L    |

Made live in this branch: `physicalTenantRestConfigProvider` (`9d51833a1470`) and `databaseTypeProvider`
(`8f05648eba6f`).

### Already live before this branch (verified in code)

|                                                                  Call site                                                                   |                 Commit                  |
|----------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------|
| job stream service, broker admin service, partition managers (`PhysicalTenantModulesStep`)                                                   | `55cb23119eeb`                          |
| embedded gRPC gateway security and interceptors (`GatewayPhysicalTenants`)                                                                   | `dc1ce39f0a22`, wired by `b6e2ad47fd04` |
| broker health check (`expectPhysicalTenant`/`unregister`)                                                                                    | `e3ef1aa0736c`                          |
| `PhysicalTenantsHealthContributor`                                                                                                           | `96c8500082f2`                          |
| REST served filter                                                                                                                           | `0d5a3781098e`, `b251175c90ef`          |
| REST/MCP `ServiceRegistry` (per-tenant services; nodes without secondary storage)                                                            | `2519791c65da`                          |
| MCP tenant filter (live registry)                                                                                                            | `b6e2ad47fd04` + `2519791c65da`         |
| scope provider                                                                                                                               | `6821bc236f9f`                          |
| OIDC converters and groups claims                                                                                                            | `837c3dbd8e72`                          |
| secret refreshers, broker job stores                                                                                                         | `1338056b10ff`, `5e283dcbf5d8`          |
| actuators `partitions`, `jobstreams`, `exporting`, `flowControl`, `rebalance`, `upgradeReadiness`, `cluster`, `exporters`, `physicalTenants` | read live sources                       |

### Compared with "about 21"

27 frozen non-RDBMS sites at the start of this work: 12 outside the ES/OS chain and 15 in it. Counting
the ES/OS client, reader, descriptor and configuration beans as one site gives 21. Carlo's examples (job
stream service, gRPC interceptor, REST filter, MCP, health) are all among the 14 that earlier commits of
this tree already made live, so his count was most likely taken before them, at a coarser grain. RDBMS
adds about 13 sites (in flight).

## Actuator and API visibility

`PhysicalTenantKeyEffectIT#shouldShowTheAppliedConfigurationOnlyThroughTheLifecycle` changes a file
tenant (11m → 13m) and an API tenant (created 11m, updated 13m, rolled out) and reads every endpoint.

|                      Endpoint                      |                        Default                        |                                                       After the change                                                       |
|----------------------------------------------------|-------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------|
| `/actuator/env`                                    | disabled (`management.endpoints.access.default=none`) | when enabled: the file tenant shows the boot value `11m`, **a stale value shown as current**; the API tenant is absent (404) |
| `/actuator/configprops`                            | enabled, shows values                                 | shows only the root `camunda` bean; no tenant appears, before or after                                                       |
| `/actuator/physicalTenants/<id>`                   | enabled                                               | version 2, `rolledOut: true`, applied version per member, local health; no values                                            |
| `PhysicalTenantLifecycleService#configuration(id)` | Java only                                             | the applied configuration (13m)                                                                                              |

Not fixed. The cheap fix (S) is to add the applied subtree to the `local` block of the
`physicalTenants` actuator: the executor already holds it, and the credential guard keeps literal
secrets out of it, so it holds only references. Making `/actuator/env` right would mean mutating the
Spring `Environment` at runtime, which other beans read; not recommended. Document that `env` and
`configprops` describe the node's startup.

## Cost of the rejected areas

|                                      Area                                      |                                                                                                                                                                                                                                       What it would take                                                                                                                                                                                                                                       |          Cost           |                                                        Can or should it be live?                                                        |
|--------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------------|-----------------------------------------------------------------------------------------------------------------------------------------|
| ES/OS secondary storage, added tenant                                          | One per-tenant "search module" as a `PhysicalTenantResources`: client and pool, index descriptors, engine configuration, readers, query executors, access controllers, authorization checker, webapp ports, session client; registries with add/remove instead of `Map.copyOf` in `CamundaSearchClients` and friends; schema initialisation per added tenant (today one thread per boot tenant); health and migration status from the live set; `ClusterHistoryBackup` and the backup registry | L                       | Yes. Today it fails: storage homogeneity forces an ES/OS tenant on an ES/OS node, and its REST services cannot be built                 |
| ES/OS, changed tenant                                                          | the same module rebuilt on replace; credentials already rotate through dynamic secrets                                                                                                                                                                                                                                                                                                                                                                                                         | M once the above exists | Yes for credentials, timeouts, pool sizes, ILM and index settings. Never for URL and index prefix (`storage-location`: the data splits) |
| Webapps (Operate, Tasklist, Identity)                                          | legacy webapp code has no per-tenant reads (root/default only); the unified webapp goes through REST, which follows the live services; authorization ports are rows 19–21                                                                                                                                                                                                                                                                                                                      | M                       | Yes, through the REST side                                                                                                              |
| Cluster-wide fan-outs (status, topology, recovery, runtime and history backup) | look up the live registry and per-tenant services at call time                                                                                                                                                                                                                                                                                                                                                                                                                                 | S–M each                | Yes                                                                                                                                     |
| Backup and checkpoint scheduling                                               | add/remove a tenant scheduler on the actor; `BackupEndpoint` from the lifecycle                                                                                                                                                                                                                                                                                                                                                                                                                | M + S                   | Yes. The backup store location should stay rejected (it addresses data)                                                                 |
| Document store                                                                 | already rebuilt per tenant; needs a close path for its thread pool first                                                                                                                                                                                                                                                                                                                                                                                                                       | S                       | Yes, after the leak is fixed                                                                                                            |
| Audit log, history deletion, extension properties                              | on ES/OS only the Camunda exporter reads them, inside the module (from code, not run); on RDBMS `RdbmsExporterFactory` (in flight)                                                                                                                                                                                                                                                                                                                                                             | S                       | Probably `RESTART` on ES/OS; verify with the harness on an ES node                                                                      |
| Exporter id set                                                                | changing it strands positions; a removed id becomes a blocking exporter                                                                                                                                                                                                                                                                                                                                                                                                                        | —                       | Never through a tenant change; it is an exporter operation                                                                              |
| Partition count                                                                | a scaling operation, not a configuration change                                                                                                                                                                                                                                                                                                                                                                                                                                                | —                       | Not through a tenant change                                                                                                             |
| `default` tenant                                                               | see the HTTP security notes (Q13)                                                                                                                                                                                                                                                                                                                                                                                                                                                              | L                       | Open                                                                                                                                    |

The one cheap and valuable item, the REST and storage-type lookups, is done. The next cheapest are the
backup actuator and the cluster fan-outs (S each).

## Unforeseen issues with a dynamic tenant list

Proven by tests:

- **Meter cardinality grows with every removed tenant.** Each removed tenant leaves 8 meters tagged with
  its id: `zeebe.cluster.partition.balanced`, `zeebe.gateway.topology.partition.roles` and six
  `zeebe.gateway.job.stream.*` meters (`PhysicalTenantChurnIT#shouldKeepMetersOfTenantsRemovedAtRuntime`).
  Ids are never reused, so this grows for the life of the node.
- **A removed tenant leaves the cross-tenant validations.** While it runs, a second tenant with the same
  filesystem backup location is rejected; after it is removed, the same configuration validates,
  although its backups stay there and retention deletes by partition wildcard
  (`PhysicalTenantChurnIT#shouldLetATenantClaimTheBackupLocationOfARemovedTenant`). The same applies by
  code to index prefixes and snapshot repositories. Fix: keep removed tenants' storage identities in
  the validation until their data is purged.
- **Changes look applied when they are not** (`partition-count`, root-only keys, exporter execution
  metrics); fixed by reclassification above.

From code, not tested here:

- **Thread pools per tenant without close.** `SimpleDocumentStoreRegistry` creates a fixed pool per
  tenant and has no shutdown; every runtime add or replace builds a new one, so a pool leaks per
  change once it was used. `PerTenantSchemaInitialization` runs one platform thread per boot tenant.
  The REST `api-service-thread-*` pool growth is in the HTTP security notes.
- **Caches keyed by tenant never evict:** `LongPollingActivateJobsHandler.metricsByPhysicalTenant`,
  `ClientStreamServiceImpl.registeredRestartPhysicalTenants`/`metricsByPhysicalTenantId`,
  `PhysicalTenantScopedSessionStorePortProvider.adaptersByPhysicalTenant`,
  `DefaultPhysicalTenantRegistry` keeps a `REMOVED` entry per id (by design),
  `PhysicalTenantServiceRegistry` keeps a removed boot tenant's startup services.
- **Gauges never removed:** `PhysicalTenantConfigExecutor.registerGauge` (until close),
  `BrokerClientTopologyMetrics` per partition and broker, `SchemaManagerMetrics`.
- **Gossip and cluster configuration grow:** removed partition groups stay as tombstones
  (`ClusterConfigurationManagerImpl`), and `GraphScopeReconciler`s are removed only on close.
- **Storage homogeneity blocks the documented workaround.** Runtime tenants were documented as needing
  `secondary-storage: none`, but `SecondaryStorageTypeHomogeneityValidation` rejects `none` next to
  ES/OS or RDBMS boot tenants, so on such a node a runtime tenant must use ES/OS or RDBMS and hits the
  frozen chain.
- **Response shapes depend on the tenant count.** The `partitions` and `flowControl` actuators switch
  from flat to keyed when the count goes from 1 to 2, at runtime. The search health indicator decides
  flat or composite once, at boot. `BackupEndpoint.describeTenants` too.
- **Ordering.** The resolver appends an implicit `default` last; `PhysicalTenantAuthConfigurations`
  puts it first. Nothing found that depends on boot order beyond that.
- **Restore** reads only file tenants; a tenant created through the API is probably absent from an
  offline restore (unverified).

## Tests and how to run them

```bash
./mvnw install -Dquickly -T2 -Dmaven.build.cache.enabled=false
./mvnw verify -pl zeebe/qa/integration-tests -Dquickly -DskipTests=false -DskipUTs \
  -Dit.test='PhysicalTenantKeyEffectIT,PhysicalTenantChurnIT,PhysicalTenantConfigRolloutIT,PhysicalTenantRuntimeLifecycleIT'
./mvnw verify -pl configuration -Dquickly -DskipTests=false -DskipITs -Dtest=PhysicalTenantKeyClassificationTest
./mvnw verify -pl dist -Dquickly -DskipTests=false -DskipITs \
  -Dtest='PhysicalTenantRestConfigurationTest,DatabaseTypeProviderConfigurationTest'
```

|                                                     Test                                                      |                                                     What                                                     |
|---------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------|
| `PhysicalTenantKeyEffectIT` (13)                                                                              | 8 sentinel batches, flags and choices, root-only keys, partition count, rejected sample, actuator visibility |
| `PhysicalTenantChurnIT` (2)                                                                                   | meters after removal, backup location of a removed tenant                                                    |
| `PhysicalTenantConfigRolloutIT#shouldGiveTheEngineAndTheRestApiOfEveryBrokerTheChangedValues`                 | three-broker spot check through the API                                                                      |
| `PhysicalTenantRuntimeLifecycleIT#shouldAnswerASecondaryStorageRequestOfATenantAddedAtRuntimeLikeABootTenant` | storage type of an added tenant                                                                              |
| `PhysicalTenantKeyClassificationTest#shouldRejectKeysThatChangeNothingOnARunningTenant`                       | the five reclassified keys                                                                                   |
| `PhysicalTenantRestConfigurationTest` (8), `DatabaseTypeProviderConfigurationTest` (7)                        | the two live lookups                                                                                         |

To check a new key: add a sentinel to a batch in `PhysicalTenantKeyEffectIT`. The test fails if no
consumer holds it (unless it is listed in `NOT_HELD_ON_THE_HEAP`), if a replaced tenant keeps the old
value anywhere outside the boot snapshots, or if a replaced or added tenant lacks a consumer the boot
tenant has.
