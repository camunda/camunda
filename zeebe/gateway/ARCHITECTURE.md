---
architecture_md: 1
component: camunda/camunda/zeebe/gateway
system: { id: orchestration-cluster, map: "camunda/camunda/SYSTEM.md" }
lifecycle: production
kind: [library]
summary: The gateway core shared by the gRPC gateway, the REST API's service layer and the broker - one typed broker request per engine command, partition dispatch and retry, job activation with long polling, the gateway configuration model, and cluster admin and migration-status requests.
team: { name: camunda/core-features, contact: unknown }
intake: { how: issue, template: "2. feature_request.yml", labels: [component/zeebe-engine] }
owns:
  - "Broker request classes (io.camunda.zeebe.gateway.impl.broker.request, Broker*Request): how each engine command an API can send is encoded for the broker - record value, value type and intent, partition targeting, response decoding - for every caller (gRPC gateway, service layer, broker partition scaling)"
  - "Partition dispatch for commands that are not addressed by a key: RequestRetryHandler (tries the other partitions on connection or resource-exhausted errors, never on timeouts) and the hash-based strategies (HashBasedDispatchStrategy by correlation key or business id, aware of the scaling routing state; PublishMessageDispatchStrategy)"
  - "Job activation across partitions for both APIs: ActivateJobsHandler, RoundRobinActivateJobsHandler, LongPollingActivateJobsHandler (pending requests per job type, per-physical-tenant jobsAvailable notifications, probing, empty-response threshold, failing open requests on cluster purge) and the ResponseObserver / JobActivationResult contract"
  - "Long-polling metrics (LongPollingMetricsDoc), tagged by physical tenant"
  - "The gateway configuration model: GatewayCfg and its parts (NetworkCfg, ClusterCfg, MembershipCfg, ThreadsCfg, SecurityCfg, KeyStoreCfg, LongPollingCfg, InterceptorCfg, FilterCfg, ConfigManagerCfg) and their defaults in ConfigurationDefaults"
  - "Gateway-side request errors (io.camunda.zeebe.gateway.cmd: ClientException and the invalid-tenant, illegal-tenant, invalid-variable and invalid-business-id exceptions) that the gRPC and REST error mappers translate"
  - "Request input checks shared by the APIs: VariableNameLengthValidator (32 KiB default) and RequestUtil.ensureJsonSet (JSON to MessagePack)"
  - "Cluster admin requests (BrokerAdminRequest: pause, soft-pause and resume exporting, exporting state, step down, flow control get/set, ban instance, migration status) and topology checks (TopologyValidation, IncompleteTopologyException)"
  - "Cluster-wide upgrade-readiness conditions rocksDbMigrated, exporterMigrated and brokerVersionMigrated: the MigrationStatusProvider implementations that query every partition replica of every physical tenant"
does_not_own:
  - { concept: "gRPC server, endpoints, request/response mapping to proto, interceptor loading, gRPC authentication and job streaming (StreamJobs, JobStreamClient)", owner: camunda/camunda/zeebe/gateway-grpc }
  - { concept: "REST controllers, REST filter loading and HTTP-side job activation observer", owner: camunda/camunda/zeebe/gateway-rest }
  - { concept: "The API contracts: gateway.proto and the REST OpenAPI spec", owner: camunda/camunda/zeebe/gateway-protocol }
  - { concept: "BrokerClient, the BrokerRequest base class, topology manager, request transport and job-available notification delivery", owner: camunda/camunda/zeebe/broker }
  - { concept: "Command semantics, validation of record values, rejections and write authorization", owner: camunda/camunda/zeebe/engine }
  - { concept: "Record value types, intents and the SBE admin/migration encodings", owner: camunda/camunda/zeebe/protocol }
  - { concept: "Which broker request an API operation sends, read authorization, and the broker request mutators", owner: camunda/camunda/service }
  - { concept: "Authorization claims attached to broker requests (BrokerRequestAuthorizationConverter) and the authorization enums", owner: camunda/camunda/security }
  - { concept: "Binding camunda.* and legacy zeebe.gateway.* properties onto GatewayCfg", owner: camunda/camunda/configuration }
  - { concept: "Wiring handlers, admin requests and status providers into applications and actuator endpoints", owner: camunda/camunda/dist }
  - { concept: "The MigrationStatusProvider SPI and physical tenant ids", owner: camunda/camunda/cluster }
depends_on:
  - id: broker-client
    component: camunda/camunda/zeebe/broker
    kind: library
    contract: "io.camunda:zeebe-broker-client (BrokerClient, BrokerRequest/BrokerResponse, BrokerTopologyManager, RequestDispatchStrategy, job-available subscriptions); zeebe-transport, zeebe-scheduler (actors), zeebe-atomix-cluster and -utils, zeebe-cluster-config (routing state, gossip config)"
    versions: same monorepo release
    workaround_policy: never
  - id: protocol
    component: camunda/camunda/zeebe/protocol
    kind: schema
    contract: "io.camunda:zeebe-protocol, zeebe-protocol-impl, zeebe-msgpack-value: record values, intents, AdminRequest/AdminResponse, PartitionMigrationStatus, MsgPackConverter"
    versions: same monorepo release
    workaround_policy: never
  - id: zeebe-auth
    component: camunda/camunda/zeebe/auth
    kind: library
    contract: "io.camunda:zeebe-auth: authorization claim keys (Authorization) carried on job activation requests"
    versions: same monorepo release
    workaround_policy: never
  - id: security-protocol
    component: camunda/camunda/security
    kind: schema
    contract: "io.camunda:camunda-security-protocol: authorization enums (PermissionType, AuthorizationResourceType, ...) used by the authorization, role, group and tenant requests"
    versions: same monorepo release
    workaround_policy: never
  - id: security-library
    component: camunda/camunda-security-library
    kind: library
    contract: "io.camunda:camunda-security-library-api and -core: CamundaAuthentication and RequiredAuthorization in batch operation requests"
    versions: "pinned by version.camunda-security-library in parent/pom.xml"
    workaround_policy: never
  - id: search-domain
    component: camunda/camunda/search
    kind: library
    contract: "io.camunda:camunda-search-domain: FilterBase carried by batch operation requests"
    versions: same monorepo release
    workaround_policy: never
  - id: cluster
    component: camunda/camunda/cluster
    kind: library
    contract: "io.camunda:camunda-cluster: PhysicalTenantIds and the default physical tenant id, MigrationStatusProvider SPI and MigrationConditionStatus"
    versions: same monorepo release
    workaround_policy: never
  - id: zeebe-util
    component: camunda/camunda/zeebe/util
    kind: library
    contract: "io.camunda:zeebe-util: buffers, VisibleForTesting, micrometer helpers"
    versions: same monorepo release
    workaround_policy: never
  - id: runtime-libs
    component: "Agrona, Micrometer, SLF4J, Jackson core, Spring core (DataSize only), netty-tcnative (TLS, runtime)"
    kind: external
    contract: "DirectBuffer, meters and ExtendedMeterDocumentation, logging, JsonParseException"
    versions: "managed in parent/pom.xml"
    workaround_policy: adapter-boundary
consumers:
  - { who: camunda/camunda/zeebe/gateway-grpc, via: "Broker*Request classes, RequestRetryHandler, ActivateJobsHandler and long polling, GatewayCfg and InterceptorCfg, cmd exceptions, VariableNameLengthValidator", promise: "internal Java API; same release" }
  - { who: camunda/camunda/service, via: "Broker*Request classes, RequestRetryHandler, ActivateJobsHandler, cmd exceptions", promise: "internal Java API; same release" }
  - { who: "camunda/camunda/zeebe/gateway-rest and camunda/camunda/gateways/gateway-mapping-http", via: "ResponseObserver, JobActivationResult, FilterCfg, Loggers", promise: "internal Java API; same release" }
  - { who: camunda/camunda/zeebe/broker, via: "EmbeddedGatewayCfg extends GatewayCfg; partition scaling requests (BrokerPartitionScaleUpRequest, BrokerPartitionBootstrappedRequest, GetScaleUpProgress)", promise: "internal Java API; same release" }
  - { who: camunda/camunda/configuration, via: "GatewayBasedProperties and LegacyGatewayBasedProperties extend GatewayCfg; FilterCfg, InterceptorCfg, ConfigurationDefaults", promise: "internal Java API; same release" }
  - { who: camunda/camunda/dist, via: "HttpJobHandlerConfiguration (REST job activation), BrokerAdminRequest in the ban-instance, flow-control and rebalancing endpoints, the migration status providers", promise: "internal Java API; same release" }
  - { who: "gateway-grpc, gateway-rest and service tests; zeebe/qa/integration-tests", via: "test-jar (StubbedBrokerClient, StubbedTopologyManager, job request stubs) and the module itself", promise: none }
  - { who: "Job workers and clients through either API", via: "ActivateJobs behavior over gRPC and REST: long-poll timeout, empty responses, RESOURCE_EXHAUSTED, cancellation on purge", promise: "as the gRPC and REST APIs (zeebe/gateway-protocol)" }
  - { who: "Operators and dashboards", via: "gateway configuration (zeebe.gateway.* legacy, camunda.* unified) and long-polling metric names", promise: "user-facing configuration and metrics; TODO(confirm) the deprecation policy" }
exposes:
  - { contract: "Broker request classes, one per engine command, with RequestRetryHandler and the dispatch strategies", spec: src/main/java/io/camunda/zeebe/gateway/impl/broker/request, policy: "internal Java API, changed with callers in the same PR; the bytes they write must stay compatible with the other minor during a rolling upgrade" }
  - { contract: "Job activation handlers: ActivateJobsHandler<T>, LongPollingActivateJobsHandler builder, ResponseObserver<T>, JobActivationResult<T>", spec: src/main/java/io/camunda/zeebe/gateway/impl/job, policy: "internal Java API; observable behavior is part of the gRPC and REST ActivateJobs contract" }
  - { contract: "Gateway configuration model (GatewayCfg tree) and ConfigurationDefaults", spec: src/main/java/io/camunda/zeebe/gateway/impl/configuration, policy: "internal shape of user-facing configuration; property names and defaults are what users see" }
  - { contract: "Extension point model: InterceptorCfg and FilterCfg (id, jarPath, className) for user-supplied gRPC interceptors and REST filters loaded from external JARs", spec: src/main/java/io/camunda/zeebe/gateway/impl/configuration/BaseExternalCodeCfg.java, policy: "user-facing configuration; loading and what an extension may do are defined in gateway-grpc (InterceptorRepository) and gateway-rest (FilterRepository)" }
  - { contract: "Upgrade-readiness conditions rocksDbMigrated, exporterMigrated, brokerVersionMigrated", spec: src/main/java/io/camunda/zeebe/gateway/admin, policy: "UNKNOWN unless every replica answers within the shared timeout; condition names are read by the upgrade-readiness check, TODO(confirm) by whom outside the cluster" }
  - { contract: "Long-polling metrics", spec: src/main/java/io/camunda/zeebe/gateway/metrics/LongPollingMetricsDoc.java, policy: "metric names and tags are observable; TODO(confirm) compatibility promise" }
  - { contract: "test-jar with broker client and topology stubs", spec: src/test/java/io/camunda/zeebe/gateway/api/util, policy: "test-only; no promise" }
constraints:
  - { id: C1, name: Engine command first then one broker request for every API, hard: true, ref: "../../docs/rest-api-endpoint-guidelines.md#52-command-services" }
  - { id: C2, name: Partition targeting and retry safety, hard: true, ref: src/main/java/io/camunda/zeebe/gateway/impl/broker/RequestRetryHandler.java }
  - { id: C3, name: Physical-tenant routing, hard: true, ref: ../docs/adr/0004-810-physical-tenant-job-streaming.md }
  - { id: C4, name: Mixed-version compatibility during rolling upgrades, hard: true, ref: ../docs/adr/0004-810-physical-tenant-job-streaming.md }
  - { id: C5, name: Both APIs get the change, hard: true, ref: ../../docs/architecture/overview.md }
  - { id: C6, name: Configuration model changes reach every binding, hard: true, ref: src/main/java/io/camunda/zeebe/gateway/impl/configuration/ConfigurationDefaults.java }
  - { id: C7, name: Authorization data on the request, hard: true, ref: ../../security/ARCHITECTURE.md }
  - { id: C8, name: Job activation latency and load, hard: false, ref: src/main/java/io/camunda/zeebe/gateway/impl/job/LongPollingActivateJobsHandler.java }
decisions: ../../docs/adr/README.md
planning: { issue_templates: { increment: "3. task.yml", epic: "4. epic breakdown.yml" } }
---

# Architecture — camunda/camunda/zeebe/gateway

> Draft from the repository; not reviewed by its team. Facts marked `TODO(confirm)` are inferred.

## 1. Purpose

Despite its name, this module is not the gRPC server: it is the gateway core that every path to the
broker shares. It turns API calls into broker requests, picks partitions, activates jobs (with long
polling) and holds the gateway configuration model. Its callers are `zeebe/gateway-grpc` (the gRPC
API), `service/` (behind the REST and MCP APIs), `dist` (management endpoints) and `zeebe/broker`
(embedded gateway, partition scaling). It is a library, never deployed on its own. Part of the
[Orchestration Cluster system](../../SYSTEM.md) (role `gateways`).

## 2. Ownership boundary

**Owns:** the step between "an API knows which command to send" and "the broker client sends bytes
to a partition": the request class, the partition choice and retry, and, for job activation, the
fan-out over partitions and the long poll. It also owns the configuration model the gateways read,
and the cluster admin and migration-status requests. The full list is in the front matter.

Owner: GitHub [CODEOWNERS](../../CODEOWNERS) has no line for `zeebe/gateway/`. The fine-grained
ownership file [`.codeowners`](../../.codeowners) (codeowners-plus, used to attribute failing tests
and incidents) assigns it to `@camunda/core-features` with the other gateway modules, and the front
matter uses that team. TODO(confirm): the owning team, its contact channel, and whether
`@camunda/zeebe-distributed-platform` co-owns the admin, topology and partition-scaling requests
(it owns `zeebe/broker-client` and the scaling code that calls them).

**Does not own (route here instead):**

| If you need… | It belongs to | How to ask |
|---|---|---|
| A new gRPC endpoint, proto mapping, gRPC interceptor behavior, job streaming | `camunda/camunda/zeebe/gateway-grpc` | issue, `component/zeebe-engine` |
| A REST endpoint or REST filter behavior | `camunda/camunda/zeebe/gateway-rest` | issue, `component/c8-api` |
| An API contract change (`gateway.proto`, OpenAPI) | `camunda/camunda/zeebe/gateway-protocol` (`@camunda/c8-api-team` for v2) | issue, `component/c8-api` |
| Broker client transport, topology, request timeouts, job-available notification delivery | `camunda/camunda/zeebe/broker` (`zeebe/broker-client`, `@camunda/zeebe-distributed-platform`) | issue, `component/zeebe-platform` |
| What a command does, its validation and rejections, write authorization | `camunda/camunda/zeebe/engine` | issue, `component/zeebe-engine` |
| Which command an API operation sends, read authorization, request mutators | `camunda/camunda/service` | issue |
| A new configuration property name or its unified-config binding | `camunda/camunda/configuration` | issue |
| Authorization claims on requests, permission enums | `camunda/camunda/security` | issue |

## 3. Structure

Paths below are under `src/main/java/io/camunda/zeebe/gateway/`.

| Path | What it holds |
|---|---|
| `impl/broker/request/` | One `Broker*Request` per command (`BrokerCreateProcessInstanceRequest`, `BrokerCompleteJobRequest`, …); sub-packages `group/`, `role/`, `tenant/` (identity entities), `scaling/` (partition scale-up) |
| `impl/broker/` | `RequestRetryHandler`, `HashBasedDispatchStrategy`, `PublishMessageDispatchStrategy` |
| `impl/job/` | `ActivateJobsHandler`, `RoundRobinActivateJobsHandler`, `LongPollingActivateJobsHandler` and its in-flight state, `NotificationThrottle`, `ResponseObserver`, `JobActivationResult`/`Response` |
| `impl/configuration/` | `GatewayCfg` and its parts, `BaseExternalCodeCfg` (`InterceptorCfg`, `FilterCfg`), `ConfigurationDefaults` |
| `admin/` | `BrokerAdminRequest`, `TopologyValidation`, `Cluster*MigrationStatusProvider`, `ClusterMigrationStatusReader` |
| `cmd/` | `ClientException` and its subclasses |
| `metrics/` | `LongPollingMetrics`, `LongPollingMetricsDoc`, `LongPollingMetricsFactory` |
| `validation/`, `RequestUtil`, `Loggers` | Shared input checks, JSON to MessagePack, the `io.camunda.zeebe.gateway` loggers |

Command path: gRPC endpoint (`zeebe/gateway-grpc`) or REST controller → `service/` → `Broker*Request`
built here → `BrokerClient` (`zeebe/broker-client`), directly or through `RequestRetryHandler` / a
dispatch strategy → partition leader. Job activation: both APIs call the same `ActivateJobsHandler`;
`dist` builds one for HTTP (`HttpJobHandlerConfiguration`) and `Gateway` builds one for gRPC.

Direction rules:

- This module knows no API: no gRPC, Spring web or servlet types. Mapping to and from proto or HTTP
  stays in `gateway-grpc`, `gateway-mapping-http` and `service`. No Spring web stereotypes in
  `io.camunda.zeebe.gateway..` outside `.rest` (`ForbidWebStereotypeArchTest`); the rest is
  convention, no ArchUnit rule. TODO(confirm) as a rule.
- It talks to brokers only through `zeebe/broker-client`; it never reads engine state.
- Configuration classes are plain Java beans; Spring binding happens in `configuration`.
- `zeebe/broker` depends on this module (embedded gateway config, scaling requests), which runs
  against the role order in SYSTEM.md (`gateways` above `platform`). TODO(confirm): accepted, or
  debt to move the shared types down.

Variant-specific code: none. Standalone gateway, embedded gateway (`EmbeddedGatewayCfg` in the
broker) and the unified Camunda application all use the same classes.

## 4. Binding decisions

ADR index: [`docs/adr/`](../../docs/adr/README.md) and the Zeebe ADRs in
[`zeebe/docs/adr/`](../docs/adr/README.md). This module has no ADR folder of its own. The decisions
that most often shape changes here:

- [Physical-tenant-aware job streaming](../docs/adr/0004-810-physical-tenant-job-streaming.md)
  (Zeebe ADR 0004): job-available notifications stay cluster-wide and keyed by job type; the long
  poller subscribes per physical tenant plus the legacy prefix-less topic for the default tenant,
  to be removed in 8.11.
- [Job lease](../docs/adr/0005-810-job-lease.md) (Zeebe ADR 0005): no gateway-side version-skew
  mechanism; an old gateway drops new fields.
- [Physical-tenant scoped gRPC authentication](../../docs/adr/orchestration-cluster/0006-physical-tenant-scoped-grpc-authentication.md)
  and the other [orchestration-cluster ADRs](../../docs/adr/orchestration-cluster/README.md).
- [Health, status and topology per physical tenant](../../docs/adr/management/001-physical-tenant-health-status-topology.md)
  (gateway health indicators become group-aware).
- [Reserved poll capacity for the job worker](../../docs/adr/clients/0002-reserved-poll-capacity-for-job-worker.md):
  clients rely on the long poll's empty-response timing.

Rules without an ADR: [REST API guidelines § 5.2](../../docs/rest-api-endpoint-guidelines.md#52-command-services)
("reuse existing broker request classes … or create new ones") and the
[architecture overview](../../docs/architecture/overview.md) (gRPC kept for compatibility and
streaming; REST first for new features).

## 5. Planning constraints

Every plan must answer each of these (applies / n/a + decision or reason).

### C1 — Engine command first then one broker request for every API
- **Question:** Does the engine already process the command (implemented and tested there first)?
  Is there an existing `Broker*Request` to reuse, or which new one is added here, and do the gRPC
  endpoint and the service use that same class?
- **Hard:** yes
- **Detail:** [Guidelines § 5.2](../../docs/rest-api-endpoint-guidelines.md#52-command-services)

### C2 — Partition targeting and retry safety
- **Question:** How does the request choose its partition: from a key, by hash of a correlation key
  or business id (`HashBasedDispatchStrategy`, which must respect the scaling routing state), or
  round-robin? If it goes through `RequestRetryHandler`, is it safe to send to another partition
  after a connection or resource-exhausted error?
- **Hard:** yes
- **Detail:** `RequestRetryHandler`, `HashBasedDispatchStrategy`, [Zeebe ADR 0001](../docs/adr/0001-810-message-correlation-business-id-cross-partition.md)

### C3 — Physical-tenant routing
- **Question:** Does the request reach the right physical tenant's partition group (default when
  none is given)? For job activation and long polling, which notifications and metrics are scoped
  per tenant, and can one tenant's traffic wake or block another's requests?
- **Hard:** yes
- **Detail:** [Zeebe ADR 0004](../docs/adr/0004-810-physical-tenant-job-streaming.md),
  [orchestration-cluster ADRs](../../docs/adr/orchestration-cluster/README.md)

### C4 — Mixed-version compatibility during rolling upgrades
- **Question:** During an upgrade, a gateway of one minor talks to brokers of the other. Does the
  change alter what a request writes, a notification topic, or an admin request type? What does the
  old side do with it, and when can the bridge be removed?
- **Hard:** yes
- **Detail:** [Zeebe ADR 0004 D4–D5](../docs/adr/0004-810-physical-tenant-job-streaming.md),
  [Zeebe ADR 0005](../docs/adr/0005-810-job-lease.md), `zeebe/qa/update-tests`. TODO(confirm) the
  upgrade test suite that covers gateway–broker skew.

### C5 — Both APIs get the change
- **Question:** Code here serves gRPC and REST (and MCP through `service`). Is the change meant for
  both? If only one, why does it belong here and not in that API's module? Are both error mappers
  (gRPC `GrpcErrorMapper`, REST via `service` `ErrorMapper`) updated for a new `cmd` exception?
- **Hard:** yes
- **Detail:** [Architecture overview](../../docs/architecture/overview.md)

### C6 — Configuration model changes reach every binding
- **Question:** A new or changed field in `GatewayCfg` needs: a default in `ConfigurationDefaults`,
  the unified `camunda.*` binding and the legacy `zeebe.gateway.*` one in `configuration`, the
  broker's embedded gateway, and the docs. Which of them, and is the old property deprecated?
- **Hard:** yes
- **Detail:** `ConfigurationDefaults`, `configuration/` (`GatewayBasedPropertiesOverride`,
  `LegacyGatewayBasedProperties`). TODO(confirm) the property deprecation policy.

### C7 — Authorization data on the request
- **Question:** Which authorization claims and which `RequiredAuthorization` does the new request
  carry so the engine can check write permissions? Is a new permission added in `security` first?
- **Hard:** yes
- **Detail:** [security ARCHITECTURE.md](../../security/ARCHITECTURE.md), `BrokerRequestAuthorizationConverter`

### C8 — Job activation latency and load
- **Question:** Does the change alter how often the gateway polls brokers, how long requests stay
  open, or how many requests are held per job type? What do the long-polling metrics and
  `LongPollingIT` show before and after?
- **Hard:** no
- **Detail:** `LongPollingActivateJobsHandler`, `LongPollingMetricsDoc`

## 6. Data and persistence

None. The module keeps only in-memory state: pending long-poll requests per job type (failed with a
retryable error when the cluster is purged) and nothing else. Durable state lives in the engine.

## 7. Cross-cutting qualities

- **Security:** authentication happens before this module (`gateway-grpc` interceptors, REST
  security chain). Requests here carry the caller's claims for the engine to authorize writes. TLS
  settings for the gRPC server are modelled in `SecurityCfg`/`KeyStoreCfg`; netty-tcnative is the
  runtime TLS provider.
- **Tenancy:** two kinds. Physical tenants pick the partition group (C3). Logical `tenantId`
  checks produce `InvalidTenantRequestException`/`IllegalTenantRequestException`.
- **Performance:** job activation runs on one actor; long polling avoids busy polling (defaults:
  10 s timeout; after 3 empty rounds for a job type, new requests wait for a notification or the
  10 s probe; 100 ms notification batch window).
  Requests and responses are bounded by the broker's max message size.
- **Observability:** long-polling metrics per physical tenant; `Loggers` (gateway, long polling).

## 8. Delivery

As [Orchestration Cluster SYSTEM.md](../../SYSTEM.md) § 6, plus:

- Gateway and broker are upgraded node by node, so every change here must work against the other
  minor's brokers (C4). Legacy topics kept for 8.9 compatibility are due for removal in 8.11
  (Zeebe ADR 0004).
- Configuration changes follow the unified configuration and its legacy properties (C6).

## 9. Testing expectations

- **Unit (here):** JUnit 5 and JUnit 4 side by side (`junit-vintage-engine`); migrate JUnit 4 tests
  when touched. Long polling: `LongPollingActivateJobsHandler*Test`, physical-tenant and purge tests;
  requests: `Broker*RequestTest`, dispatch and retry strategy tests; admin: migration status providers.
- **Through the gRPC gateway:** `zeebe/gateway-grpc` tests use this module's test-jar
  (`StubbedBrokerClient`) and cover request mapping and physical-tenant routing.
- **Integration:** `zeebe/qa/integration-tests` (`LongPollingIT`, `ActivateJobsTest`,
  `PurgeCancelsPendingJobActivationRequestsIT`, `PhysicalTenantJobAvailableNotificationIT`).
- **Upgrade:** TODO(confirm) which suite covers mixed gateway and broker versions.
- Commands: `./mvnw verify -pl zeebe/gateway -DskipTests=false -Dquickly`.

## 10. Planning conventions

- Issues: templates `2. feature_request.yml`, `3. task.yml`, `4. epic breakdown.yml` in
  `.github/ISSUE_TEMPLATE/`.
- Label: the `create-issue` skill files `zeebe/gateway` under `component/zeebe-engine`
  (grey area for job streaming: the more affected layer). `.github/labeler.yml` has no rule for
  this module.
- TODO(confirm): plans directory, ID prefix for plan refs, spec format.

## 11. Glossary

| Term | Meaning here |
|---|---|
| Gateway | Here the shared gateway core; "the gRPC gateway" is `zeebe/gateway-grpc`, "the REST gateway" `zeebe/gateway-rest` |
| Broker request | A `Broker*Request`: one engine command encoded for the broker, with its target partition |
| Long polling | Keeping an `ActivateJobs` request open until jobs of its type are available or the timeout ends |
| Job streaming | Brokers push jobs to open worker streams; owned by `zeebe/gateway-grpc`, not by the long poller |
| Physical tenant | An independent engine with its own partition group in one cluster; not the logical `tenantId` |
| Embedded gateway | The gateway running inside a broker process (`EmbeddedGatewayCfg`), versus a standalone gateway |
| Admin request | A management request to one broker partition (`BrokerAdminRequest`), not an engine command |
