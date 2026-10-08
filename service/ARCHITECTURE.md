---
architecture_md: 1
component: camunda/camunda/service
system: { id: orchestration-cluster, map: "camunda/camunda/SYSTEM.md" }
lifecycle: production
kind: [library]
summary: The domain service layer of the Orchestration Cluster, one *Services class per resource, reached through a ServiceRegistry keyed by physical tenant, that sends authorized commands to the broker and runs read-authorized queries against secondary storage for the REST and MCP gateways and the authentication layer.
team: { name: camunda/core-features, contact: unknown }
intake: { how: issue, template: "2. feature_request.yml" }
owns:
  - "The Java service API behind every REST /v2 operation and MCP tool: one *Services class per resource (process instances, user tasks, jobs, users, groups, roles, tenants, authorizations, documents, secrets, cluster variables, backups, ...) with its request and result records"
  - "ServiceRegistry and DefaultServiceRegistry: the per-physical-tenant service instances and the parameter-less cluster-wide services (Cluster*Services, ManagementServices)"
  - "Dispatch of API commands to the broker: ApiServices.sendBrokerRequest and the broker-request mutators that attach the caller's authorization claims and, for tenant-scoped services, the physical tenant as partition group"
  - "Read authorization of secondary-storage queries: the RequiredAuthorization constants in Authorizations and attaching a SecurityContext (withSecurityContext) to every search-client call"
  - "The service error model: ServiceException and its Status values, ErrorMapper (broker errors, rejections, search errors, timeouts), and the secondary-storage unavailable, degraded and type-not-supported exceptions"
  - "Cluster-wide fan-out semantics over physical tenants: all-or-nothing outcome, failing tenants named in the error, one shared status or INTERNAL (PhysicalTenantFanOut)"
  - "The API services executor (ApiServicesExecutorProvider): the bounded pool all async service work runs on, with physical-tenant context propagation"
  - "The process definition cache used to enrich API responses (ProcessCache, metrics namespace camunda.gateway.rest.cache)"
  - "The HistoryBackupApi port for secondary-storage history backups, and Camunda license parsing (CamundaLicense, LicenseType)"
does_not_own:
  - { concept: "HTTP routes, status codes, @RequiresSecondaryStorage gating and the async HTTP response", owner: camunda/camunda/zeebe/gateway-rest }
  - { concept: "Request validation, mapping HTTP DTOs to service request records, ServiceException to ProblemDetail", owner: camunda/camunda/gateways/gateway-mapping-http }
  - { concept: "MCP tool definitions over the services", owner: camunda/camunda/gateways/gateway-mcp }
  - { concept: "Broker request classes (io.camunda.zeebe.gateway.impl.broker.request), RequestRetryHandler and the job activation handler", owner: camunda/camunda/zeebe/gateway }
  - { concept: "Command semantics, rejections and write authorization", owner: camunda/camunda/zeebe/engine }
  - { concept: "Query execution, filters, sorting, pagination and the search query and entity types", owner: camunda/camunda/search }
  - { concept: "Permission model, RequiredAuthorization and SecurityContext types, BrokerRequestAuthorizationConverter", owner: camunda/camunda/security }
  - { concept: "Who the caller is (CamundaAuthentication), login, OIDC and basic-auth filter chains", owner: camunda/camunda/authentication }
  - { concept: "Building the ServiceRegistry per physical tenant, the executor bean, HistoryBackupApi implementation", owner: camunda/camunda/dist }
  - { concept: "Binding camunda.api.rest.executor and other camunda.* properties", owner: camunda/camunda/configuration }
  - { concept: "Broker client transport, topology, dynamic cluster configuration, rebalancing, runtime backups", owner: camunda/camunda/zeebe/broker }
  - { concept: "Document store backends", owner: camunda/camunda/document }
  - { concept: "Secret store backends and their registry", owner: camunda/camunda/secret-store }
  - { concept: "The gRPC API (it calls the broker client directly, not this layer)", owner: camunda/camunda/zeebe/gateway-grpc }
depends_on:
  - id: broker-client
    component: camunda/camunda/zeebe/broker
    kind: library
    contract: "io.camunda:zeebe-broker-client (BrokerClient, BrokerRequest/BrokerResponse, BrokerTopologyManager); zeebe-cluster-config, zeebe-rebalance, zeebe-backup, zeebe-atomix-cluster and -utils for the management services"
    versions: same monorepo release
    workaround_policy: never
  - id: zeebe-gateway
    component: camunda/camunda/zeebe/gateway
    kind: library
    contract: "io.camunda:zeebe-gateway: broker request classes (impl.broker.request.*), RequestRetryHandler, ActivateJobsHandler, gateway validation helpers"
    versions: same monorepo release
    workaround_policy: never
  - id: protocol
    component: camunda/camunda/zeebe/protocol
    kind: schema
    contract: "io.camunda:zeebe-protocol, zeebe-protocol-impl, zeebe-msgpack-core, zeebe-msgpack-value: record values, intents, rejection types and msgpack encoding of variables"
    versions: same monorepo release
    workaround_policy: never
  - id: bpmn-model
    component: camunda/camunda/zeebe/engine
    kind: library
    contract: "io.camunda:zeebe-bpmn-model: parsing deployed BPMN XML for the process cache and ad-hoc sub-process activities"
    versions: same monorepo release
    workaround_policy: never
  - id: search
    component: camunda/camunda/search
    kind: library
    contract: "io.camunda:camunda-search-client (the *SearchClient interfaces with withSecurityContext), camunda-search-domain (query, filter, sort and entity types)"
    versions: same monorepo release
    workaround_policy: never
  - id: security
    component: camunda/camunda/security
    kind: library
    contract: "io.camunda:camunda-security-core, camunda-security-protocol: RequiredAuthorization, SecurityContext, AuthorizationCondition, BrokerRequestAuthorizationConverter"
    versions: same monorepo release
    workaround_policy: never
  - id: security-library
    component: camunda/camunda-security-library
    kind: library
    contract: "io.camunda:camunda-security-library-api, -core: CamundaAuthentication and the authorization model passed into every service call"
    versions: "pinned by version.camunda-security-library in parent/pom.xml"
    workaround_policy: never
  - id: document
    component: camunda/camunda/document
    kind: library
    contract: "io.camunda:document-api, document-store: DocumentStore, SimpleDocumentStoreRegistry for DocumentServices"
    versions: same monorepo release
    workaround_policy: never
  - id: secret-store
    component: camunda/camunda/secret-store
    kind: library
    contract: "io.camunda:camunda-secret-store-api: SecretStore, SecretStoreRegistry for SecretServices"
    versions: same monorepo release
    workaround_policy: never
  - id: cluster
    component: camunda/camunda/cluster
    kind: library
    contract: "io.camunda:camunda-cluster: secondary-storage readiness and migration status per physical tenant"
    versions: same monorepo release
    workaround_policy: never
  - id: spring-utils
    component: camunda/camunda/spring-utils
    kind: library
    contract: "io.camunda:camunda-spring-utils: PhysicalTenantPropagatingExecutorService"
    versions: same monorepo release
    workaround_policy: never
  - id: zeebe-util
    component: camunda/camunda/zeebe/util
    kind: library
    contract: "io.camunda:zeebe-util: shared utilities"
    versions: same monorepo release
    workaround_policy: never
  - id: license-check
    component: org.camunda.bpm:camunda-license-check
    kind: external
    contract: "License key validation behind CamundaLicense"
    versions: "version.camunda-license-check in parent/pom.xml"
    workaround_policy: adapter-boundary
  - id: libraries
    component: "Agrona, Caffeine, Micrometer, Jackson core, Netty transport, commons-lang3, spring-security-crypto"
    kind: external
    contract: "buffers, the process cache, cache metrics, PasswordEncoder for UserServices"
    versions: "managed in parent/pom.xml"
    workaround_policy: adapter-boundary
consumers:
  - { who: camunda/camunda/zeebe/gateway-rest, via: "ServiceRegistry, *Services, ServiceException, secondary-storage exceptions", promise: "internal Java API, same release; a signature change breaks it at compile time and lands in the same PR" }
  - { who: camunda/camunda/gateways/gateway-mapping-http, via: "service request and result records, ServiceException and Status", promise: "internal Java API, same release" }
  - { who: camunda/camunda/gateways/gateway-mcp, via: "ServiceRegistry, request records, ServiceException.Status", promise: "internal Java API, same release" }
  - { who: camunda/camunda/authentication, via: "ServiceRegistry (users, roles, groups, tenants, authorizations for basic auth and membership), Authorizations.COMPONENT_ACCESS_AUTHORIZATION, ServiceException", promise: "internal Java API, same release" }
  - { who: "camunda/camunda/zeebe/gateway-grpc and camunda/camunda/zeebe/broker", via: "UserServices for gRPC basic authentication (embedded gateway)", promise: "internal Java API, same release" }
  - { who: camunda/camunda/dist, via: "DefaultServiceRegistry.Builder, ApiServicesExecutorProvider, SecurityContextProvider, HistoryBackupApi, CamundaLicense, ManagementServices", promise: "internal Java API; changes land with dist in the same PR" }
  - { who: "operate/data-generator (dev tooling), qa/archunit-tests, zeebe/qa/integration-tests", via: "ServiceRegistry and request records; archunit rules over io.camunda.service", promise: none }
  - { who: "Indirectly: REST API and MCP users, the Operate, Tasklist and Admin webapps, the Java client", via: "the REST API v2 and MCP tools built on these services", promise: "as the REST API (forward-compatible between minors); see zeebe/gateway-rest ARCHITECTURE.md" }
exposes:
  - { contract: "ServiceRegistry: typed accessors for every service, tenant-scoped ones keyed by a single physicalTenantId", spec: src/main/java/io/camunda/service/registry/ServiceRegistry.java, policy: "internal; every concrete *Services class must be registered (ServiceRegistryArchTest)" }
  - { contract: "*Services classes with their nested request/result records, returning CompletableFuture for commands and SearchQueryResult for queries", spec: src/main/java/io/camunda/service/, policy: "internal; changed together with every caller in the same PR" }
  - { contract: "Extension point: ApiServices / PhysicalTenantScopedApiServices / SearchQueryService base classes for new services; subclasses add behavior, the broker-request mutator list is final for tenant-scoped services", spec: src/main/java/io/camunda/service/ApiServices.java, policy: "internal; send paths that bypass sendBrokerRequest must call applyBrokerRequestMutators" }
  - { contract: "ServiceException with Status (INVALID_ARGUMENT, NOT_FOUND, FORBIDDEN, UNAVAILABLE, ...) and the SecondaryStorage*Exception types", spec: src/main/java/io/camunda/service/exception/ServiceException.java, policy: "internal, but the Status reaches API users as the HTTP status via gateway-mapping-http; changing a mapping is a REST behavior change" }
  - { contract: "Authorizations: the RequiredAuthorization constants each read checks", spec: src/main/java/io/camunda/service/authorization/Authorizations.java, policy: "internal; must match x-required-permissions in the REST spec (security ADR 001)" }
  - { contract: "Configuration consumed: camunda.api.rest.executor (core/max pool multipliers, keep-alive, queue capacity), process cache size and idle expiry", spec: ../dist/src/main/java/io/camunda/application/commons/service/CamundaServicesConfiguration.java, policy: "user-facing configuration owned by configuration/dist" }
constraints:
  - { id: C1, name: Physical-tenant scope and registry access, hard: true, ref: ../qa/archunit-tests/src/test/java/io/camunda/service/ServiceRegistryArchTest.java }
  - { id: C2, name: Who enforces the permission, hard: true, ref: ../qa/archunit-tests/src/test/java/io/camunda/service/ServiceSecurityContextArchTest.java }
  - { id: C3, name: Engine command exists and goes through the mutators, hard: true, ref: "../docs/rest-api-endpoint-guidelines.md#52-command-services" }
  - { id: C4, name: Async work on the managed executor, hard: true, ref: ../docs/adr/orchestration-cluster/0009-propagating-physical-tenant-context-across-async-authorization-reads.md }
  - { id: C5, name: Secondary-storage dependency, hard: true, ref: "../docs/rest-api-endpoint-guidelines.md#25-eventually-consistent-annotation-x-eventually-consistent" }
  - { id: C6, name: Error status the caller sees, hard: true, ref: src/main/java/io/camunda/service/exception/ErrorMapper.java }
  - { id: C7, name: Cluster-wide fan-out, hard: true, ref: ../docs/adr/management/003-physical-tenant-management-endpoint-inventory.md }
  - { id: C8, name: Callers beyond the REST API, hard: false, ref: ../docs/architecture/overview.md }
decisions: ../docs/adr/README.md
planning: { issue_templates: { increment: "3. task.yml", epic: "4. epic breakdown.yml" } }
---

# Architecture — camunda/camunda/service

> Draft from the repository; not reviewed by its team. Facts marked `TODO(confirm)` are inferred.

## 1. Purpose

The domain service layer between the cluster's entry points and its engine and storage. The REST
gateway, the MCP gateway and the authentication layer call a `*Services` class here; the service
sends an authorized command to the broker (write path) or runs a read-authorized query against
secondary storage through `search` (read path), and maps failures to `ServiceException`
([architecture overview](../docs/architecture/overview.md): CQRS and "`service/` as the
REST-to-engine bridge"). A plain Java library, assembled by `dist`. Part of the
[Orchestration Cluster system](../SYSTEM.md) (role `services`).

## 2. Ownership boundary

**Owns:** what an API operation *does* once a gateway has mapped the request: which broker request
it sends or which search client it queries, which permission a read needs, how a failure becomes a
`ServiceException.Status`, and how a cluster-wide operation combines its per-tenant results. The
full list is in the front matter.

Owner: GitHub [CODEOWNERS](../CODEOWNERS) has no line for `service/`. The fine-grained ownership
file [`.codeowners`](../.codeowners) (codeowners-plus, used to attribute failing tests) assigns
`/service/` to `@camunda/core-features` under "Shared modules", and the front matter uses that team.
TODO(confirm): whether core-features owns the module as a team or holds it as a shared layer that
feature teams extend (the [REST API guidelines § 1](../docs/rest-api-endpoint-guidelines.md#1-end-to-end-workflow)
name "Feature team" for the service-layer stage). Contact channel: TODO(confirm).

**Does not own (route here instead):**

| If you need… | It belongs to | How to ask |
|---|---|---|
| A new endpoint, HTTP status, or `@RequiresSecondaryStorage` gate | `camunda/camunda/zeebe/gateway-rest` (contract in `zeebe/gateway-protocol`) | issue, `component/c8-api` |
| Request validation or a different HTTP mapping of a `Status` | `camunda/camunda/gateways/gateway-mapping-http` | issue, `component/gateway` |
| A new MCP tool | `camunda/camunda/gateways/gateway-mcp` | issue, `component/mcp` |
| A new broker request class or job activation behavior | `camunda/camunda/zeebe/gateway` | issue |
| A new command, intent, rejection or write-permission check | `camunda/camunda/zeebe/engine` | issue, `component/zeebe-engine` |
| A new filter, sort field, entity or query in storage | `camunda/camunda/search` (schema in `webapps-schema`, `db/rdbms-schema`) | issue, `component/data-layer` |
| A new permission type or resource type | `camunda/camunda/security` | issue |
| How the caller is authenticated | `camunda/camunda/authentication`, Camunda Security Library | issue |
| How the registry is wired, executor sizing | `camunda/camunda/dist`, `camunda/camunda/configuration` | issue |
| Topology, dynamic configuration, rebalancing, runtime backups on the broker | `camunda/camunda/zeebe/broker` (`@camunda/zeebe-distributed-platform`) | issue, `component/zeebe-platform` |

## 3. Structure

Paths below are under `src/main/java/io/camunda/service/`.

| Path | What it holds |
|---|---|
| `*Services.java` | One class per resource. Tenant-scoped ones extend `PhysicalTenantScopedApiServices` (commands) or `search/core/SearchQueryService` (queries); `Cluster*Services` and `ManagementServices` are cluster-wide |
| `ApiServices`, `PhysicalTenantScopedApiServices` | Broker dispatch, mutators (authorization claims, partition group), broker error handling |
| `ApiServicesExecutorProvider` | The bounded, tenant-propagating executor all async work uses |
| `PhysicalTenantFanOut`, `MigrationStatusAggregator`, `TenantRestoreEnvironment` | Cluster-wide aggregation over physical tenants |
| `registry/` | `ServiceRegistry` (interface) and `DefaultServiceRegistry` (+ `Builder` used by `dist`) |
| `search/core/` | `SearchQueryService` base class |
| `authorization/` | `Authorizations`: the `RequiredAuthorization` per read |
| `security/` | `SecurityContextProvider` |
| `exception/` | `ServiceException`, `ServiceError`, `ErrorMapper`, secondary-storage exceptions |
| `cache/` | `ProcessCache` (Caffeine) and `ProcessDefinitionProvider` |
| `backup/` | `HistoryBackupApi` port and its value types |
| `license/` | `CamundaLicense`, `LicenseType` |

Call path: gateway → `ServiceRegistry.xServices(physicalTenantId)` → service method →
either `sendBrokerRequest` (mutators applied → `BrokerClient` → engine) or
`searchClient.withSecurityContext(...).searchX(...)` (→ `search` → ES/OS/RDBMS) → result or
`ServiceException`.

Direction rules:

- Services write only through the broker and read only through `search`; they never read engine
  state ([SYSTEM.md](../SYSTEM.md) DR3; direction itself not enforced yet).
- Gateways and other callers get services only from `ServiceRegistry`, never by constructing or
  injecting them (`ServiceRegistryArchTest`).
- The module has no Spring beans or annotations (only `spring-security-crypto`'s `PasswordEncoder`);
  wiring lives in `dist` (`CamundaServicesConfiguration`, `ManagementServicesConfiguration`).
  TODO(confirm): whether this is a rule or just the current state.
- Services build on `zeebe/gateway` internals (broker request classes, `RequestRetryHandler`,
  `ActivateJobsHandler`), and `authentication`, `zeebe/gateway-grpc` and `zeebe/broker` call back into
  services. Both edges run against the role order in SYSTEM.md (DR6: security never depends on
  services). TODO(confirm): whether these are accepted exceptions or debt to remove.

Variant-specific code: none by product variant. Differences per deployment are configuration of
the physical tenant (security config, secondary-storage type, backup mode) passed in by `dist`.

## 4. Binding decisions

ADR index: [`docs/adr/`](../docs/adr/README.md). This module has no ADR folder of its own. The
decisions that most often shape service work:

- Physical tenants: [authorization reads routed per tenant](../docs/adr/orchestration-cluster/0005-physical-tenant-routing-of-authorization-reads.md)
  (ADR 0005), [tenant context across async authorization reads](../docs/adr/orchestration-cluster/0009-propagating-physical-tenant-context-across-async-authorization-reads.md)
  (ADR 0009: the executor is the single chokepoint), and the other
  [orchestration-cluster ADRs](../docs/adr/orchestration-cluster/README.md).
  `CamundaServicesConfiguration` cites an ADR `0001-physical-tenant-service-serviceRegistry` that is
  not in the repo. TODO(confirm): where it lives.
- Management: [endpoint authorization](../docs/adr/management/002-management-endpoint-authorization.md),
  [endpoint inventory](../docs/adr/management/003-physical-tenant-management-endpoint-inventory.md),
  [cluster-wide history backup](../docs/adr/management/004-cluster-wide-history-backup.md),
  [exporting state via dynamic config](../docs/adr/management/006-exporting-state-via-dynamic-config.md).
- [Endpoint required-permission mapping](../docs/adr/security/001-endpoint-required-permission-mapping.md).
- [Central secret resolution](../docs/adr/secrets/001-central-secret-resolution-architecture.md)
  (`SecretServices`: `SECRET:REVEAL` per reference, reads on the API executor).

Rules without an ADR: [REST API guidelines § 5](../docs/rest-api-endpoint-guidelines.md#5-service-layer)
and the [architecture overview](../docs/architecture/overview.md).

## 5. Planning constraints

Every plan must answer each of these (applies / n/a + decision or reason).

### C1 — Physical-tenant scope and registry access
- **Question:** Is the new service tenant-scoped (extends `PhysicalTenantScopedApiServices`,
  registry accessor takes `physicalTenantId`) or cluster-wide (parameter-less accessor)? Is it added
  to `ServiceRegistry`, `DefaultServiceRegistry` and its `Builder`, and wired in `dist`?
- **Hard:** yes
- **Detail:** `ServiceRegistryArchTest`, [orchestration-cluster ADRs](../docs/adr/orchestration-cluster/README.md)

### C2 — Who enforces the permission
- **Question:** For a read: which `RequiredAuthorization` in `Authorizations` does it check, and does
  every search-client call go through `withSecurityContext`? For a command: does the engine enforce
  the permission (the service only forwards the caller's claims)? Does it match
  `x-required-permissions` in the spec, and does a new permission land in `security` first?
- **Hard:** yes
- **Detail:** `ServiceSecurityContextArchTest`,
  [security ADR 001](../docs/adr/security/001-endpoint-required-permission-mapping.md),
  [ADR 0005](../docs/adr/orchestration-cluster/0005-physical-tenant-routing-of-authorization-reads.md)

### C3 — Engine command exists and goes through the mutators
- **Question:** Does the broker command exist and is it tested in the engine first? Which broker
  request class (in `zeebe/gateway`) carries it? If the call bypasses `sendBrokerRequest` (retry
  handler, job activation), does it call `applyBrokerRequestMutators`?
- **Hard:** yes
- **Detail:** [Guidelines § 5.2](../docs/rest-api-endpoint-guidelines.md#52-command-services),
  `ApiServices`

### C4 — Async work on the managed executor
- **Question:** Does every async step run on `ApiServicesExecutorProvider.getExecutor()` (no
  executor-less `supplyAsync`/`runAsync`), so the physical tenant reaches the worker thread?
- **Hard:** yes
- **Detail:** `ServiceAsyncExecutorArchTest`,
  [ADR 0009](../docs/adr/orchestration-cluster/0009-propagating-physical-tenant-context-across-async-authorization-reads.md)

### C5 — Secondary-storage dependency
- **Question:** Does the operation read secondary storage? What happens with no secondary storage,
  an unsupported type (RDBMS vs ES/OS), or degraded storage (`SecondaryStorage*Exception`)? Is the
  query supported by all three backends in `search`?
- **Hard:** yes
- **Detail:** [Guidelines § 2.5](../docs/rest-api-endpoint-guidelines.md#25-eventually-consistent-annotation-x-eventually-consistent),
  `ServiceRegistry.clusterHistoryBackupServices()` Javadoc

### C6 — Error status the caller sees
- **Question:** Which `ServiceException.Status` does each failure produce (broker rejection, not
  found, timeout, search error)? Does a new rejection type or status need a mapping in `ErrorMapper`
  and in `gateway-mapping-http`?
- **Hard:** yes
- **Detail:** `exception/ErrorMapper.java`, `gateways/gateway-mapping-http` `GatewayErrorMapper`

### C7 — Cluster-wide fan-out
- **Question:** For a cluster-wide operation: does it fan out over every physical tenant through
  `PhysicalTenantFanOut` (all-or-nothing, failing tenants named, shared status or 500)? Is it
  cluster-admin only?
- **Hard:** yes
- **Detail:** [Management ADR 003](../docs/adr/management/003-physical-tenant-management-endpoint-inventory.md),
  [ADR 002](../docs/adr/management/002-management-endpoint-authorization.md)

### C8 — Callers beyond the REST API
- **Question:** Does the change alter a signature or behavior used by `gateway-mcp`, `authentication`,
  `zeebe/gateway-grpc` (basic auth via `UserServices`), `dist` or `operate/data-generator`? Are they
  updated in the same PR?
- **Hard:** no
- **Detail:** front matter `consumers`

## 6. Data and persistence

None owned. Commands change engine state through the broker; reads go through `search` to
Elasticsearch, OpenSearch or an RDBMS. The only state kept here is the in-memory `ProcessCache`
(bounded size, optional idle expiry), filled from `search` with its own security context.

## 7. Cross-cutting qualities

- **Security:** reads are authorized here (C2); commands are authorized in the engine from the
  claims the service attaches. `SecretServices` checks `SECRET:REVEAL` per reference.
- **Tenancy:** physical tenants by registry instance and partition group (C1); multi-tenancy
  `tenantId` checks travel in the authorization claims (`BrokerRequestAuthorizationConverter`, per
  tenant config).
- **Performance:** one shared, bounded executor (`CallerRunsPolicy` when full) for all tenants; the
  wiring notes "no isolation yet". TODO(confirm): whether per-tenant isolation is planned.
- **Observability:** process cache stats via Micrometer (`camunda.gateway.rest.cache`).

## 8. Delivery

As [Orchestration Cluster SYSTEM.md](../SYSTEM.md) § 6. The Java API is internal: it ships in the
same monorepo release as all its callers, so it has no compatibility promise of its own; the REST
API built on it does (see `zeebe/gateway-rest/ARCHITECTURE.md` § 8).

## 9. Testing expectations

- **Service unit tests (required):** in `src/test/java/io/camunda/service/`, mocking broker client
  and search clients (Mockito, `StubbedBrokerClient` from the `zeebe-gateway` test-jar, Instancio);
  cover input transformation, the authorization passed to search, and broker error mapping
  ([guidelines § 7.1](../docs/rest-api-endpoint-guidelines.md#71-unit-tests-required)).
- **Architecture:** `ServiceRegistryArchTest`, `ServiceSecurityContextArchTest`,
  `ServiceAsyncExecutorArchTest` in [`qa/archunit-tests`](../qa/archunit-tests).
- **Integration:** `zeebe/qa/integration-tests` for new broker commands;
  [`qa/acceptance-tests`](../docs/testing/acceptance.md) across ES, OS and RDBMS.
- Run: `./mvnw verify -pl service -DskipTests=false -Dquickly`.

## 10. Planning conventions

- Issues: templates `2. feature_request.yml`, `3. task.yml`, `4. epic breakdown.yml` in
  `.github/ISSUE_TEMPLATE/`.
- Labels: no `component/*` label maps to `service/` (neither `.github/labeler.yml` nor the
  `create-issue` skill). TODO(confirm): which label intake should use.
- TODO(confirm): plans directory and ID prefix for plan refs.

## 11. Glossary

| Term | Meaning here |
|---|---|
| Physical tenant | An isolated tenant of the cluster with its own configuration, storage and partition group; the registry holds one set of services per physical tenant. Not the multi-tenancy `tenantId` |
| Tenant-scoped vs cluster-wide service | Accessor takes `physicalTenantId` vs none; cluster-wide services fan out over all tenants |
| Broker request mutator | A step that tags every outgoing broker request (authorization claims, partition group) |
| Security context | The authentication plus required authorization attached to a search-client call |
| Secondary storage | ES, OS or RDBMS filled by exporters; read path only |
