---
architecture_md: 1
component: camunda/camunda/zeebe/gateway-rest
system: { id: orchestration-cluster, map: "camunda/camunda/SYSTEM.md" }
lifecycle: production
kind: [library]
summary: The Spring MVC layer that serves the Orchestration Cluster REST API (v2), with physical-tenant and cluster-wide routing, secondary-storage gating and the OpenAPI/Swagger endpoints, and hands every request to the service layer.
team: { name: camunda/core-features, contact: unknown }
intake: { how: issue, template: "2. feature_request.yml", labels: [component/c8-api] }
owns:
  - "REST controllers for every /v2 operation of the Orchestration Cluster REST API: route, HTTP method, status code, and the call into the matching service"
  - "Physical-tenant routing of the REST surface: the pre-security PhysicalTenantFilter, the /physical-tenants/{id}/v2 sibling routes (PhysicalTenantRequestMappingHandlerMapping), @PhysicalTenantId injection, and /v2/status scoping to the default tenant"
  - "Cluster-wide management routes under /cluster/v2 (@ClusterScoped): topology, status, exporting, runtime and history backups, rebalancing, recovery"
  - "Secondary-storage gating of endpoints: @RequiresSecondaryStorage and SecondaryStorageInterceptor (403 when absent or unsupported, 503 with Retry-After when degraded)"
  - "Turning the REST API and its parts on and off: camunda.rest.enabled, @CamundaRestController, and the filters that disable the Groups, Users and Tenants APIs by configuration"
  - "Serving the OpenAPI document and Swagger UI (Self-Managed and SaaS variants, camunda.rest.swagger.enabled) and the optional response validation against the spec (camunda.rest.response-validation.enabled)"
  - "Loading user-supplied REST servlet filters from external JARs (FilterRepository, camunda.api.rest.filters)"
  - "REST-specific request limits in GatewayRestConfiguration: name-field length, cluster-variable metadata size, job-metrics label limits"
does_not_own:
  - { concept: "The REST API contract: OpenAPI spec, x-* annotations and Spectral rules", owner: camunda/camunda/zeebe/gateway-protocol }
  - { concept: "Generated request and response DTOs (io.camunda.gateway.protocol.model)", owner: camunda/camunda/gateways/gateway-model }
  - { concept: "Request validation, mapping to service requests, search query and response mapping, error-to-ProblemDetail mapping", owner: camunda/camunda/gateways/gateway-mapping-http }
  - { concept: "Business logic, broker requests and read authorization behind an endpoint", owner: camunda/camunda/service }
  - { concept: "Command semantics, rejections and write authorization", owner: camunda/camunda/zeebe/engine }
  - { concept: "Query execution, filters and sorting over secondary storage", owner: camunda/camunda/search }
  - { concept: "Authentication, security filter chains (including cluster-admin) and unknown-tenant rejection", owner: camunda/camunda/authentication }
  - { concept: "Permission model and identity entity validation", owner: camunda/camunda/security }
  - { concept: "gRPC API, job long-polling and the gateway filter configuration model", owner: camunda/camunda/zeebe/gateway }
  - { concept: "MCP tools that mirror REST endpoints", owner: camunda/camunda/gateways/gateway-mcp }
  - { concept: "Java client commands and queries for new endpoints", owner: camunda/camunda/clients }
  - { concept: "Binding of camunda.* configuration properties to GatewayRestConfiguration", owner: camunda/camunda/configuration }
  - { concept: "Wiring the module into the application (component scan, filter registration, message converters)", owner: camunda/camunda/dist }
  - { concept: "Actuator management endpoints on the management port", owner: unknown }
depends_on:
  - id: service
    component: camunda/camunda/service
    kind: library
    contract: "io.camunda:camunda-service: ServiceRegistry (keyed by physical tenant), *Services classes, ServiceException and the secondary-storage exceptions"
    versions: same monorepo release
    workaround_policy: never
  - id: rest-spec
    component: camunda/camunda/zeebe/gateway-protocol
    kind: schema
    contract: "OpenAPI v2 spec zeebe/gateway-protocol/src/main/proto/v2/rest-api.yaml (io.camunda:zeebe-gateway-protocol); loaded at runtime for the served OpenAPI document"
    versions: same monorepo release
    workaround_policy: never
  - id: gateway-model
    component: camunda/camunda/gateways/gateway-model
    kind: library
    contract: "io.camunda:camunda-gateway-model: DTOs generated from the spec, with bean-validation constraints"
    versions: same monorepo release
    workaround_policy: never
  - id: gateway-mapping-http
    component: camunda/camunda/gateways/gateway-mapping-http
    kind: library
    contract: "io.camunda:camunda-gateway-mapping-http: RequestMapper and *Mapper classes, SearchQueryRequestMapper/ResponseMapper, ResponseMapper, GatewayErrorMapper"
    versions: same monorepo release
    workaround_policy: never
  - id: zeebe-gateway
    component: camunda/camunda/zeebe/gateway
    kind: library
    contract: "io.camunda:zeebe-gateway: FilterCfg, job ResponseObserver for long-polling job activation"
    versions: same monorepo release
    workaround_policy: never
  - id: platform-types
    component: camunda/camunda/zeebe/broker
    kind: library
    contract: "zeebe-dynamic-config, zeebe-backup, zeebe-rebalance, zeebe-broker-client: response and request types of the management services (cluster configuration, backup status, rebalance plans)"
    versions: same monorepo release
    workaround_policy: never
  - id: protocol
    component: camunda/camunda/zeebe/protocol
    kind: schema
    contract: "io.camunda:zeebe-protocol, zeebe-protocol-impl, zeebe-msgpack-value: record value types used in a few responses (agent instances, global listeners, backup encoding)"
    versions: same monorepo release
    workaround_policy: never
  - id: search-domain
    component: camunda/camunda/search
    kind: library
    contract: "io.camunda:camunda-search-domain (query, filter and entity types passed to services), camunda-search-client-connect (DatabaseType)"
    versions: same monorepo release
    workaround_policy: never
  - id: security
    component: camunda/camunda/security
    kind: library
    contract: "io.camunda:camunda-security-core, camunda-security-validation: identity entity validators, authorization types"
    versions: same monorepo release
    workaround_policy: never
  - id: authentication
    component: camunda/camunda/authentication
    kind: library
    contract: "io.camunda:camunda-authentication: WebAppProviderAdapter (web app paths that get physical-tenant routes), the security filter chains the REST API runs behind"
    versions: same monorepo release
    workaround_policy: never
  - id: security-library
    component: camunda/camunda-security-library
    kind: library
    contract: "io.camunda:camunda-security-library-api, -core, -spring-boot-starter, -validation: CamundaAuthenticationProvider, MultiTenancyConfiguration, SaaS/Self-Managed conditions"
    versions: "pinned by version.camunda-security-library in parent/pom.xml"
    workaround_policy: never
  - id: cluster
    component: camunda/camunda/cluster
    kind: library
    contract: "io.camunda:camunda-cluster: SecondaryStorageReadiness, physical tenant identifiers"
    versions: same monorepo release
    workaround_policy: never
  - id: spring-utils
    component: camunda/camunda/spring-utils
    kind: library
    contract: "io.camunda:camunda-spring-utils: PhysicalTenantContext (request-scoped physical tenant id)"
    versions: same monorepo release
    workaround_policy: never
  - id: document-api
    component: camunda/camunda/document
    kind: library
    contract: "io.camunda:document-api: document types for the documents endpoints"
    versions: same monorepo release
    workaround_policy: never
  - id: spring-web
    component: Spring Framework / Spring Boot (spring-webmvc, spring-boot-webmvc)
    kind: external
    contract: "Spring MVC controllers, HandlerInterceptor, RequestMappingHandlerMapping, servlet filters"
    versions: "Spring Boot version managed in parent/pom.xml"
    workaround_policy: adapter-boundary
  - id: springdoc-swagger
    component: springdoc-openapi and swagger-parser
    kind: external
    contract: "springdoc-openapi-starter-common, swagger-parser-v3: OpenAPI document and Swagger UI serving"
    versions: "version.swagger-parser and springdoc managed in parent/pom.xml"
    workaround_policy: adapter-boundary
consumers:
  - { who: "API users: applications, job workers, scripts and other SDKs calling the cluster over HTTP", via: Orchestration Cluster REST API v2, promise: "forward-compatible between minor versions; breaking changes only deliberately, deprecation before removal (at least two minors); alpha endpoints exempt" }
  - { who: "camunda/camunda/clients (Java client, Spring Boot starters) and Camunda Process Test through them", via: REST API v2, promise: "as API users; client and starter compatibility tested in CI" }
  - { who: "camunda/camunda/webapp/client (Operate, Tasklist, Admin pods) via @camunda/camunda-api-zod-schemas", via: REST API v2, promise: as API users }
  - { who: "Operators and their tooling (backup, restore, exporting control, rebalancing, status probes)", via: "/v2 management endpoints and /cluster/v2", promise: "as API users; endpoint set per management ADR 003" }
  - { who: "camunda/camunda/dist (application assembly)", via: "ConditionalOnRestGatewayEnabled, FilterRepository, GatewayRestConfiguration, ResponseObserverProvider, PhysicalTenantRestConfigProvider", promise: "internal Java API; changes land with dist in the same PR" }
  - { who: "camunda/camunda/configuration and camunda/camunda/webapp/server", via: "GatewayRestConfiguration, WebappConfiguration", promise: "internal Java API; same release" }
  - { who: "camunda/camunda/gateways/gateway-mcp", via: "controller behavior it mirrors (schemas, error mapping, search mapping)", promise: "no code dependency; convention only" }
  - { who: "qa (archunit-tests, acceptance-tests, c8-orchestration-cluster-e2e-test-suite, forward-compatibility nightly), zeebe/qa integration tests, load-tests", via: "REST API v2; archunit-tests also the test-jar", promise: none }
  - { who: "Other Camunda products (Web Modeler, Desktop Modeler, Console, Connectors)", via: REST API v2, promise: as API users }
exposes:
  - { contract: "Orchestration Cluster REST API v2 under /v2", spec: ../gateway-protocol/src/main/proto/v2/rest-api.yaml, policy: "spec-first; forward-compatible between minors (guidelines § 2.7); every operation carries x-added-in-version; alpha endpoints and properties may change without deprecation (§ 2.8)" }
  - { contract: "Physical-tenant routes /physical-tenants/{id}/v2/** for every non-cluster-scoped endpoint", spec: ../../docs/adr/orchestration-cluster/0003-physical-tenant-request-scoping-via-pre-security-filter.md, policy: "x-scope: physical-tenant in the spec; unprefixed /v2 resolves to the default physical tenant" }
  - { contract: "Cluster-wide management API under /cluster/v2", spec: ../../docs/adr/management/003-physical-tenant-management-endpoint-inventory.md, policy: "x-scope: cluster-wide; cluster-admin only (management ADR 002 D3); same compatibility policy as /v2" }
  - { contract: "OpenAPI document (/v3/api-docs) and Swagger UI", spec: src/main/java/io/camunda/zeebe/gateway/rest/config/OpenApiResourceConfig.java, policy: "on by default, off with camunda.rest.swagger.enabled=false; security schemes mirror the spec (guidelines § 2.15)" }
  - { contract: "Extension point: user-supplied servlet filters from external JARs, configured as camunda.api.rest.filters (id, jar-path, class-name)", spec: src/main/java/io/camunda/zeebe/gateway/rest/impl/filters/FilterRepository.java, policy: "user-facing configuration; a filter may inspect, modify or reject any REST request; no documented compatibility promise" }
  - { contract: "Configuration: camunda.rest.enabled, camunda.rest.swagger.enabled, camunda.rest.response-validation.enabled, GatewayRestConfiguration limits", spec: src/main/java/io/camunda/zeebe/gateway/rest/config/GatewayRestConfiguration.java, policy: "user-facing configuration; response validation is for development and testing only" }
constraints:
  - { id: C1, name: Spec first and controller-spec alignment, hard: true, ref: "../../docs/rest-api-endpoint-guidelines.md#1-end-to-end-workflow" }
  - { id: C2, name: Forward compatibility between minors, hard: true, ref: "../../docs/rest-api-endpoint-guidelines.md#27-backwards-compatibility-and-breaking-changes" }
  - { id: C3, name: Thin controllers that go through the service layer, hard: true, ref: "../../docs/rest-api-endpoint-guidelines.md#42-controller-conventions" }
  - { id: C4, name: Physical-tenant or cluster-wide scope, hard: true, ref: ../../docs/adr/management/003-physical-tenant-management-endpoint-inventory.md }
  - { id: C5, name: Secondary-storage dependency and eventual consistency, hard: true, ref: "../../docs/rest-api-endpoint-guidelines.md#25-eventually-consistent-annotation-x-eventually-consistent" }
  - { id: C6, name: Required permissions and who enforces them, hard: true, ref: ../../docs/adr/security/001-endpoint-required-permission-mapping.md }
  - { id: C7, name: Version and alpha annotations, hard: true, ref: "../../docs/rest-api-endpoint-guidelines.md#217-operation-and-property-versioning-annotations-x-added-in-version-x-properties-added-in-version" }
  - { id: C8, name: Endpoint can be disabled with the REST API, hard: true, ref: ../../qa/archunit-tests/src/test/java/io/camunda/zeebe/gateway/rest/RestControllerAnnotationArchTest.java }
  - { id: C9, name: Clients and mirrors follow the endpoint, hard: false, ref: "../../docs/rest-api-endpoint-guidelines.md#6-camunda-client-extension" }
decisions: ../../docs/adr/README.md
planning: { issue_templates: { increment: "3. task.yml", epic: "4. epic breakdown.yml" } }
---

# Architecture — camunda/camunda/zeebe/gateway-rest

> Draft from the repository; not reviewed by its team. Facts marked `TODO(confirm)` are inferred.

## 1. Purpose

This module serves the Orchestration Cluster REST API (v2). It is the main entry point for new
features: Operate, Tasklist and Admin, the Java client, operators and any HTTP caller go through
it. Its controllers check scope and secondary-storage readiness, map the request with
`gateway-mapping-http`, call the service in `service/` and map the result or error back to HTTP
([REST API endpoint guidelines § 4](../../docs/rest-api-endpoint-guidelines.md#4-rest-controller-implementation)).
The module is not deployed on its own: `dist` scans it into the Camunda application when the REST
API is enabled. Part of the [Orchestration Cluster system](../../SYSTEM.md) (role `gateways`).

## 2. Ownership boundary

**Owns:** what happens to an HTTP request between the security chain and the service call: which
controller handles it, under which routes (`/v2`, `/physical-tenants/{id}/v2`, `/cluster/v2`),
whether secondary storage is needed and ready, the HTTP status, and the async handling of the
service result. It also serves the OpenAPI document, and it can switch off the REST API or parts
of it. The full list is in the front matter.

Owner: GitHub [CODEOWNERS](../../CODEOWNERS) has no line for `zeebe/gateway-rest/`. The repo's
fine-grained ownership file [`.codeowners`](../../.codeowners) (codeowners-plus; used to attribute
failing tests and incidents) assigns it to `@camunda/core-features`, and the front matter uses that
team. TODO(confirm): whether core-features owns it as a team, or holds it as the shared surface that
feature teams add controllers to (guidelines § 1 names "Feature team" for the controller stage and
`@camunda/c8-api-team` as spec reviewer). Contact channel: TODO(confirm).

**Does not own (route here instead):**

| If you need… | It belongs to | How to ask |
|---|---|---|
| A new or changed endpoint contract, schema, enum or `x-*` annotation | `camunda/camunda/zeebe/gateway-protocol` (spec, reviewed by `@camunda/c8-api-team`); design review in `#top-c8-cluster-api-governance` | issue, `component/c8-api`; `api-proposal.md` for guideline changes |
| Generated DTO shape | `camunda/camunda/gateways/gateway-model` (regenerated from the spec) | change the spec |
| Request validation messages, request/response mapping, error-to-status mapping | `camunda/camunda/gateways/gateway-mapping-http` | issue, `component/gateway` |
| What an operation does, broker requests, read authorization | `camunda/camunda/service` | issue |
| Command behavior, rejections, write authorization | `camunda/camunda/zeebe/engine` | issue, `component/zeebe-engine` |
| Search filters, sorting, pagination behavior in storage | `camunda/camunda/search` | issue, `component/data-layer` |
| Login, OIDC, basic auth, cluster-admin chain, 401/404 before the controller | `camunda/camunda/authentication`, Camunda Security Library | issue |
| The gRPC API, job long-polling | `camunda/camunda/zeebe/gateway-grpc`, `zeebe/gateway` | issue |
| An MCP tool for the endpoint | `camunda/camunda/gateways/gateway-mcp` | issue, `component/mcp` |
| A Java client command | `camunda/camunda/clients` (`@camunda/c8-api-team`) | issue, `component/clients` |
| Actuator endpoints on the management port | TODO(confirm) | — |

## 3. Structure

Paths below are under `src/main/java/io/camunda/zeebe/gateway/rest/`.

| Path | What it holds |
|---|---|
| `controller/` | One `@CamundaRestController` per resource (`ProcessInstanceController`, `JobController`, …); `Cluster*Controller` for `/cluster/v2`; sub-packages `usermanagement/`, `tenant/`, `authentication/`, `auditlog/`, `setup/`, `system/` |
| `controller/` (filters) | `PhysicalTenantFilter`, `PhysicalTenantStatusScopeFilter`, `PhysicalTenantSwaggerFilter`, `EndpointAccessErrorFilter` |
| `annotation/` | `@Camunda{Get,Post,Put,Patch,Delete}Mapping`, `@RequiresSecondaryStorage`, `@ClusterScoped`, `@PhysicalTenantId` |
| `mapper/` | `RequestExecutor` (async service call), `RestErrorMapper`, `PhysicalTenantRequestMappingHandlerMapping`, backup and exporting response mappers |
| `interceptor/` | `SecondaryStorageInterceptor` |
| `resolver/` | `PhysicalTenantIdArgumentResolver` |
| `deserializer/` | Jackson deserializers for filter properties and polymorphic request bodies |
| `config/` | `ApiFiltersConfiguration`, OpenAPI/Swagger configurers (`SelfManaged…`, `SaaS…`), `GatewayRestConfiguration`, `WebappConfiguration`, physical-tenant MVC config |
| `validation/` | `ResponseValidationAdvice` (dev/test response validation) |
| `impl/filters/` | `FilterRepository` for user-supplied filters |

Request path: `PhysicalTenantFilter` (order −101, before Spring Security) stamps the tenant id →
security chain (`authentication`, CSL) → user filters and the disabled-API filters →
`SecondaryStorageInterceptor` → controller → `gateway-mapping-http` mapper → `ServiceRegistry`
service for the physical tenant → `RequestExecutor` / `RestErrorMapper` → `ProblemDetail` or body.

Direction rules:

- Controllers only map input, call one service, and map the result
  ([guidelines § 4.2](../../docs/rest-api-endpoint-guidelines.md#42-controller-conventions)). They
  reach the engine and storage only through `service/`, never the gRPC gateway, the broker client or
  search directly ([overview](../../docs/architecture/overview.md), SYSTEM.md DR2: no ArchUnit rule
  yet). Management controllers use broker-module types (`dynamic-config`, `backup`, `rebalance`) only
  as service results.
- Mapping and validation logic goes into `gateways/gateway-mapping-http`, not here.
- Only this package may use Spring web stereotypes among `io.camunda.zeebe.broker|gateway|shared`
  (`ForbidWebStereotypeArchTest`). Every controller is `@CamundaRestController` so the REST API can be
  disabled (`RestControllerAnnotationArchTest`).
- `FilterRepository` has a gRPC twin (`InterceptorRepository` in `zeebe/gateway-grpc`); changes are
  ported to both.

Variant-specific code: SaaS vs Self-Managed differs only in conditional beans
(`SaaSOpenApiConfigurer`, `SelfManagedOpenApiConfigurer`, `SaaSTokenController`, which is `@Hidden`
from the spec). Authentication-mode differences (OIDC, groups claim, tenants API off) are handled with
`EndpointAccessErrorFilter` registrations in `ApiFiltersConfiguration`.

## 4. Binding decisions

ADR index: [`docs/adr/`](../../docs/adr/README.md). This module has no ADR folder of its own.
The decisions that most often shape REST features:

- [Physical-tenant request scoping via a pre-security filter](../../docs/adr/orchestration-cluster/0003-physical-tenant-request-scoping-via-pre-security-filter.md)
  (orchestration-cluster ADR 0003) and the other
  [orchestration-cluster ADRs](../../docs/adr/orchestration-cluster/README.md) 0004–0009.
- Management endpoints: [health, status and topology per physical tenant](../../docs/adr/management/001-physical-tenant-health-status-topology.md),
  [management endpoint authorization](../../docs/adr/management/002-management-endpoint-authorization.md),
  [endpoint inventory](../../docs/adr/management/003-physical-tenant-management-endpoint-inventory.md),
  [cluster-wide history backup](../../docs/adr/management/004-cluster-wide-history-backup.md).
- [Endpoint required-permission mapping](../../docs/adr/security/001-endpoint-required-permission-mapping.md)
  (`x-required-permissions`, status Proposed).
- [Removing numeric keys from identity entity filters](../../docs/adr/storage/001-remove-numeric-key-from-identity-entity-filters.md).

Rules without an ADR live in the [REST API endpoint guidelines](../../docs/rest-api-endpoint-guidelines.md)
(spec rules, controller conventions, testing, merge checklist) and the
[architecture overview](../../docs/architecture/overview.md) ("REST API first", "`service/` as the
REST-to-engine bridge").

## 5. Planning constraints

Every plan must answer each of these (applies / n/a + decision or reason).

### C1 — Spec first and controller-spec alignment
- **Question:** Which operations does the change add or alter in `zeebe/gateway-protocol/.../v2/`,
  and has the contract been reviewed in `#top-c8-cluster-api-governance` and by
  `@camunda/c8-api-team`? Does every new controller method have a spec operation (or is it
  `@Hidden`, and why)? Which `ProblemDetail` status codes does it return (§ 2.9)?
- **Hard:** yes
- **Detail:** [Guidelines § 1, § 2.16](../../docs/rest-api-endpoint-guidelines.md#1-end-to-end-workflow)

### C2 — Forward compatibility between minors
- **Question:** Does the change remove or rename anything, change `required`/`nullable`, change a
  type, add an enum value, or harden a type? If it is breaking or SDK-breaking, who signs off, how is
  it deprecated first, and which older-branch API tests must change?
- **Hard:** yes
- **Detail:** [Guidelines § 2.7](../../docs/rest-api-endpoint-guidelines.md#27-backwards-compatibility-and-breaking-changes),
  forward-compatibility nightly (§ 7.6)

### C3 — Thin controllers that go through the service layer
- **Question:** Does the controller only map, call one service method and map back? Where does new
  validation or mapping go (`gateway-mapping-http`), and which service method carries the logic?
- **Hard:** yes
- **Detail:** [Guidelines § 4.2](../../docs/rest-api-endpoint-guidelines.md#42-controller-conventions),
  [overview](../../docs/architecture/overview.md)

### C4 — Physical-tenant or cluster-wide scope
- **Question:** Does the operation act on one physical tenant (default: gets a
  `/physical-tenants/{id}/v2` route, passes `@PhysicalTenantId` to the service) or on the whole cluster
  (`@ClusterScoped`, `/cluster/v2`, cluster-admin only)? Does the spec's `x-scope` match?
- **Hard:** yes
- **Detail:** [Management ADR 003](../../docs/adr/management/003-physical-tenant-management-endpoint-inventory.md),
  [guidelines § 2.20](../../docs/rest-api-endpoint-guidelines.md#220-scope-annotation-x-scope)

### C5 — Secondary-storage dependency and eventual consistency
- **Question:** Does the endpoint read secondary storage? Then it needs `@RequiresSecondaryStorage`
  (which storage types, if not all) and `x-eventually-consistent: true`. What does it return with no
  secondary storage (403), or while storage is degraded (503)? No CI check matches the two yet.
- **Hard:** yes
- **Detail:** [Guidelines § 2.5, § 2.16](../../docs/rest-api-endpoint-guidelines.md#25-eventually-consistent-annotation-x-eventually-consistent)

### C6 — Required permissions and who enforces them
- **Question:** Which permission does the operation require (`x-required-permissions`)? Is it
  enforced by the engine (commands), the service (reads), or, for per-tenant management endpoints,
  against the tenant's authorization data in secondary storage? Is a new permission needed?
- **Hard:** yes
- **Detail:** [Security ADR 001](../../docs/adr/security/001-endpoint-required-permission-mapping.md),
  [management ADR 002](../../docs/adr/management/002-management-endpoint-authorization.md)

### C7 — Version and alpha annotations
- **Question:** Does every new operation carry `x-added-in-version`, and every property whose version
  differs carry `x-properties-added-in-version`? Is the endpoint or property alpha, marked with the
  exact wording?
- **Hard:** yes
- **Detail:** [Guidelines § 2.8, § 2.17](../../docs/rest-api-endpoint-guidelines.md#217-operation-and-property-versioning-annotations-x-added-in-version-x-properties-added-in-version)

### C8 — Endpoint can be disabled with the REST API
- **Question:** Is the controller `@CamundaRestController`? Must the endpoint also be unavailable in
  some configuration (OIDC, groups claim, multi-tenancy API off, SaaS only)? If so, through which
  condition or `EndpointAccessErrorFilter`?
- **Hard:** yes
- **Detail:** `RestControllerAnnotationArchTest`, `ApiFiltersConfiguration`

### C9 — Clients and mirrors follow the endpoint
- **Question:** Who adds the Java client command, the `@camunda/camunda-api-zod-schemas` update, the
  MCP tool (if any), and the E2E API tests, and in which release?
- **Hard:** no
- **Detail:** [Guidelines § 6, § 7](../../docs/rest-api-endpoint-guidelines.md#6-camunda-client-extension),
  `gateways/gateway-mcp/AGENTS.md`

## 6. Data and persistence

None owned. The module keeps no state; reads go through `service/` to `search` (ES/OS or RDBMS),
writes go through `service/` to the broker. Per-tenant secondary-storage readiness comes from
`SecondaryStorageReadiness` (`cluster`). Request limits sometimes follow storage limits:
`maxClusterVariableMetadataSize` matches the RDBMS `CLUSTER_VARIABLE` column sizes, so a schema
change there should revisit it.

## 7. Cross-cutting qualities

- **Security:** authentication runs in the security chains from `authentication`/CSL before any
  controller; this module only places the physical-tenant filter before the chain (order −101).
  `/v2/status` is unauthenticated and default-tenant only (management ADR 001 D3). `/cluster/v2`
  needs the cluster admin (management ADR 002 D3). The security schemes in the spec must mirror
  `OpenApiResourceConfig`.
- **Tenancy:** two kinds. Physical tenants are routed by path (C4). Multi-tenancy `tenantId` checks
  are passed into the mappers (`multiTenancyCfg.isChecksEnabled()`), and the Tenants API is
  switched off when multi-tenancy's API is disabled.
- **User-supplied filters:** custom filters run inside every REST request (`camunda.api.rest.filters`);
  a change to the request path can break them. TODO(confirm): where they sit relative to Spring
  Security, and what is promised to filter authors.
- **Performance:** command endpoints return `CompletableFuture` and do not block servlet threads;
  job activation uses the long-polling handler from `zeebe/gateway`. Response validation must stay
  off in production.
- **Observability:** `Loggers`; job metrics export settings in `GatewayRestConfiguration`.
- **API language:** no "flow node" in any API surface; keys are strings (guidelines § 10).

## 8. Delivery

As [Orchestration Cluster SYSTEM.md](../../SYSTEM.md) § 6, plus:

- Public API docs are generated from the spec's descriptions; alpha features get banners
  (guidelines § 9).
- Deprecated items are removed only in a major version or after at least two minors (§ 2.7).
- A deliberate breaking change needs the forward-compatibility tests on older `stable/*` branches
  updated with `@camunda/core-features` before merging (§ 7.6).
- `@camunda/camunda-api-zod-schemas` is released separately (`publish-zod-schemas.yml`) and must
  follow REST changes the webapps use.
- TODO(confirm): which endpoints Web Modeler, Desktop Modeler, Console and Connectors call, so a
  breaking-change review knows whom to tell.

## 9. Testing expectations

- **Controllers (required):** unit tests extending `RestControllerTest` with `WebTestClient`, mocking
  the services; cover input mapping, output mapping and error mapping. JSON responses are compared
  strictly (`ControllerStrictJsonCompareArchTest`).
- **Architecture:** `RestControllerAnnotationArchTest`, `RequiresSecondaryStorageAnnotationArchTest`,
  `ControllerStrictJsonCompareArchTest`, `ForbidWebStereotypeArchTest` in
  [`qa/archunit-tests`](../../qa/archunit-tests).
- **Spec:** Spectral, two passes, plus rule tests in `zeebe/gateway-protocol/spectral-tests`.
- **Integration:** `zeebe/qa/integration-tests` for new broker commands (optional, recommended);
  [`qa/acceptance-tests`](../../docs/testing/acceptance.md) across ES, OS and RDBMS.
- **API E2E:** Playwright `api-tests` in
  [`qa/c8-orchestration-cluster-e2e-test-suite`](../../qa/c8-orchestration-cluster-e2e-test-suite/README.md);
  generated request-validation tests (never edited by hand); forward-compatibility nightly.
- Detail: [guidelines § 7](../../docs/rest-api-endpoint-guidelines.md#7-testing-strategy).

## 10. Planning conventions

- Issues: templates `2. feature_request.yml`, `3. task.yml`, `4. epic breakdown.yml` in
  `.github/ISSUE_TEMPLATE/`; `api-proposal.md` for changes to the API guidelines themselves.
- Labels: PRs touching this module get `component/c8-api` (`.github/labeler.yml`), but the
  `create-issue` skill files the REST API surface under `component/zeebe-engine`.
  TODO(confirm): which label intake should use.
- Endpoint designs go to `#top-c8-cluster-api-governance` before the spec PR (guidelines § 1).
- TODO(confirm): plans directory, ID prefix for plan refs, and whether REST plans use a spec format
  beyond the OpenAPI diff.

## 11. Glossary

| Term | Meaning here |
|---|---|
| Physical tenant | An isolated tenant of the cluster with its own configuration, storage and security chain, selected by the `/physical-tenants/{id}` path prefix; not the same as the multi-tenancy `tenantId` |
| Cluster-scoped | An endpoint under `/cluster/v2` acting on all physical tenants (`@ClusterScoped`, `x-scope: cluster-wide`) |
| Command vs. query endpoint | A command goes to the broker and is consistent; a query reads secondary storage and is eventually consistent |
| Secondary storage | ES, OS or RDBMS filled by exporters; `none` disables every query endpoint |
| Alpha | Endpoint or property that may change without deprecation, marked in the spec |
| ProblemDetail | RFC 9457 error body (`application/problem+json`) every error returns |
| Element instance | The API term for what older APIs called a "flow node instance" |
