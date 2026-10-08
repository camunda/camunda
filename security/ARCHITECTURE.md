---
architecture_md: 1
component: camunda/camunda/security
system: { id: orchestration-cluster, map: "camunda/camunda/SYSTEM.md" }
lifecycle: production
kind: [library]
summary: The Orchestration Cluster's in-repo security modules on top of the Camunda Security Library (CSL) — the engine-layer authorization enums and their mapping to CSL, the authorization claims carried in broker requests, the engine's security configuration, the secondary-storage authorization-scope adapter, and shared security helpers.
team: { name: camunda/identity, contact: unknown }
intake: { how: issue, template: "2. feature_request.yml", labels: [component/identity] }
owns:
  - "The engine-layer authorization enums in security-protocol (AuthorizationResourceType with each type's allowed PermissionType set, PermissionType, AuthorizationOwnerType, AuthorizationResourceMatcher, AuthorizationScope, DefaultRole, EntityType): part of the SBE log and RocksDB serialization schema"
  - "The resource-type → permission matrix as code: AuthorizationResourceType.buildResourcePermissionsMap(), mirrored by the REST spec's resource-permissions.json and served to the Admin UI"
  - "AuthzModelMapper: the translation between CSL's canonical authz enums (io.camunda.security.api.model.authz) and the engine-layer enums"
  - "Which identity and authorization claims a broker command carries (BrokerRequestAuthorizationConverter): username and client id always, groups and token claims only when authorizations or multi-tenancy checks are enabled, the anonymous marker"
  - "The non-Spring security configuration for engine, broker and gRPC gateway (EngineSecurityConfig, EngineSecurityConfigurations presets and default id validation patterns)"
  - "The secondary-storage implementation of CSL's AuthorizationScopeRepositoryPort (SearchAuthorizationScopeRepository) and the per-physical-tenant AuthorizationChecker factory (AuthorizationCheckerFactory)"
  - "The set of authorization owner ids an authentication reaches (AuthenticatedOwnerIdsUtil) and fail-soft OIDC claim extraction (OidcClaimExtractor)"
  - "SaaS vs Self-Managed bean conditions (@ConditionalOnSaaSConfigured, @ConditionalOnSelfManagedConfigured, SaasConfigurationHelper)"
  - "Cluster-variable request validation (ClusterVariableValidator in security-validation)"
does_not_own:
  - { concept: "Authorization and authentication decisions, the canonical authz enum catalogue, RequiredAuthorization, SecurityContext, CamundaAuthentication, TenantAccess, TenantOwnedEntity, AuthorizationChecker, identifier validators", owner: camunda/camunda-security-library }
  - { concept: "Spring Security filter chains, login, OIDC and basic auth, membership resolution, session store and the other CSL port adapters", owner: camunda/camunda/authentication }
  - { concept: "Write-path authorization checks (CslAuthorizationCheck, CslTenantCheck, the RocksDB state adapters) and identity entity processing", owner: camunda/camunda/zeebe/engine }
  - { concept: "SBE schema and record values of authorization and identity records (protocol.xml, AuthorizationRecordValue)", owner: camunda/camunda/zeebe/protocol }
  - { concept: "Which RequiredAuthorization each read operation checks (Authorizations) and SecurityContextProvider", owner: camunda/camunda/service }
  - { concept: "ResourceAccessProvider and ResourceAccessController implementations, turning authorization into search filters", owner: camunda/camunda/search }
  - { concept: "Endpoint → required-permission mapping (x-required-permissions) and resource-permissions.json", owner: camunda/camunda/zeebe/gateway-protocol }
  - { concept: "Per-physical-tenant wiring of checkers, scope repositories and the tenant-aware AuthorizationCheckPort", owner: camunda/camunda/dist }
  - { concept: "Binding camunda.security.* properties", owner: camunda/camunda/configuration }
depends_on:
  - id: csl
    component: camunda/camunda-security-library
    kind: library
    contract: "io.camunda:camunda-security-library-api (authz enums, CamundaAuthentication, configuration model), -core (AuthorizationChecker, AuthorizationScopeRepositoryPort, ResourceAccessChecks), -validation (IdentifierValidator, ErrorMessages)"
    versions: "pinned by version.camunda-security-library in parent/pom.xml (1.1.0 on main); CSL is released independently"
    workaround_policy: never
  - id: zeebe-auth
    component: camunda/camunda/zeebe/auth
    kind: library
    contract: "io.camunda:zeebe-auth: the claim keys of the broker-request authorization map (AUTHORIZED_USERNAME, AUTHORIZED_CLIENT_ID, AUTHORIZED_ANONYMOUS_USER, USER_GROUPS_CLAIMS, USER_TOKEN_CLAIMS)"
    versions: same monorepo release
    workaround_policy: never
  - id: search
    component: camunda/camunda/search
    kind: library
    contract: "io.camunda:camunda-search-domain, camunda-search-client-reader: AuthorizationReader, AuthorizationQuery, AuthorizationEntity, SearchClientReaders (security-services only)"
    architecture: search/ARCHITECTURE.md
    versions: same monorepo release
    workaround_policy: never
  - id: libraries
    component: "Spring Framework (core, context), Jackson annotations, JSpecify, SLF4J"
    kind: external
    contract: "Spring Condition for the SaaS / Self-Managed annotations; serialization annotations; nullness annotations; logging"
    versions: "managed in parent/pom.xml"
    workaround_policy: adapter-boundary
consumers:
  - { who: camunda/camunda/zeebe/engine, via: "EngineSecurityConfig, BrokerRequestAuthorizationConverter, AuthzModelMapper, the engine-layer enums", promise: "internal Java API, same release; enum constants follow the serialization contract (C1)" }
  - { who: "camunda/camunda/zeebe/protocol (and protocol-impl, protocol-asserts, test-util)", via: "the engine-layer enums, which authorization record values expose", promise: "never remove, rename or reorder a constant (C1)" }
  - { who: "camunda/camunda/zeebe/exporters (camunda-exporter, rdbms-exporter) and zeebe/exporter-common", via: "AuthzModelMapper, the engine-layer enums", promise: "internal Java API, same release" }
  - { who: camunda/camunda/service, via: "BrokerRequestAuthorizationConverter (every command service), AuthenticatedOwnerIdsUtil, the engine-layer enums", promise: "internal Java API, same release" }
  - { who: "camunda/camunda/zeebe/broker, camunda/camunda/zeebe/gateway-grpc, camunda/camunda/zeebe/gateway", via: "EngineSecurityConfig, BrokerRequestAuthorizationConverter, OidcClaimExtractor, the engine-layer enums", promise: "internal Java API, same release" }
  - { who: camunda/camunda/zeebe/gateway-rest, via: "@ConditionalOnSaaSConfigured / @ConditionalOnSelfManagedConfigured, SaasConfigurationHelper, ClusterVariableValidator; buildResourcePermissionsMap() in ResourcePermissionsRegistryTest", promise: "internal Java API, same release" }
  - { who: camunda/camunda/gateways/gateway-mapping-http, via: ClusterVariableValidator, promise: "internal Java API, same release" }
  - { who: camunda/camunda/authentication, via: OidcClaimExtractor, promise: "internal Java API, same release" }
  - { who: camunda/camunda/webapp/server, via: SaasConfigurationHelper, promise: "internal Java API, same release" }
  - { who: camunda/camunda/dist, via: "SearchAuthorizationScopeRepository, AuthorizationCheckerFactory, EngineSecurityConfig; buildResourcePermissionsMap() for the Admin client config", promise: "internal Java API; changes land with dist in the same PR" }
  - { who: "qa/util, qa/acceptance-tests, zeebe/qa/integration-tests, microbenchmarks", via: "engine-layer enums, EngineSecurityConfigurations presets", promise: none }
  - { who: "Indirectly: exporters and tools outside the repo that read zeebe-protocol records", via: "the engine-layer enums, published transitively with io.camunda:zeebe-protocol", promise: "as zeebe-protocol; TODO(confirm) in § 8" }
exposes:
  - { contract: "Engine-layer authorization enums and AuthorizationResourceType.buildResourcePermissionsMap()", spec: security-protocol/README.md, policy: "SBE/RocksDB serialization schema: add only; never remove, rename or reorder; deprecate first. Java 8 bytecode" }
  - { contract: "AuthzModelMapper.toProtocol / fromProtocol (name-based, both directions)", spec: security-protocol/src/main/java/io/camunda/zeebe/protocol/record/mapper/AuthzModelMapper.java, policy: "every constant on both sides must map; AuthzModelMapperTest asserts round trips" }
  - { contract: "BrokerRequestAuthorizationConverter: CamundaAuthentication → broker-request claims map", spec: security-core/src/main/java/io/camunda/security/auth/BrokerRequestAuthorizationConverter.java, policy: "internal; the map is written to the log with each command and read by the engine (authorization) and exporters (audit-log actor)" }
  - { contract: "EngineSecurityConfig and EngineSecurityConfigurations presets", spec: security-core/src/main/java/io/camunda/security/configuration/EngineSecurityConfig.java, policy: "internal and transitional: to be sourced from CSL's CamundaSecurityLibraryProperties (camunda-security-library#274)" }
  - { contract: "Adapter of a CSL extension point: SearchAuthorizationScopeRepository implements AuthorizationScopeRepositoryPort; AuthorizationCheckerFactory.forPhysicalTenant builds one AuthorizationChecker per physical tenant", spec: security-services/src/main/java/io/camunda/security/impl/, policy: "follows CSL's port signature; queries run with ResourceAccessChecks.disabled() by design" }
  - { contract: "Spring conditions @ConditionalOnSaaSConfigured, @ConditionalOnSelfManagedConfigured and SaasConfigurationHelper", spec: security-core/src/main/java/io/camunda/security/, policy: "internal, same release" }
  - { contract: "ClusterVariableValidator (violations list per request)", spec: security-validation/src/main/java/io/camunda/security/validation/ClusterVariableValidator.java, policy: "internal; its messages reach REST callers as 400 details" }
constraints:
  - { id: C1, name: Engine-layer enum stability, hard: true, ref: security-protocol/README.md }
  - { id: C2, name: A new resource or permission type in every place, hard: true, ref: security-protocol/README.md }
  - { id: C3, name: CSL or this component, hard: true, ref: ../identity/docs/architecture.md }
  - { id: C4, name: Java 8 for security-protocol, hard: true, ref: security-protocol/pom.xml }
  - { id: C5, name: Claims in broker requests, hard: true, ref: security-core/src/main/java/io/camunda/security/auth/BrokerRequestAuthorizationConverter.java }
  - { id: C6, name: Physical-tenant scope, hard: true, ref: ../docs/adr/orchestration-cluster/0005-physical-tenant-routing-of-authorization-reads.md }
  - { id: C7, name: Read and write path parity, hard: false, ref: ../identity/docs/architecture.md }
  - { id: C8, name: Consumers updated together, hard: false, ref: ../docs/architecture/overview.md }
decisions: ../docs/adr/README.md
planning: { issue_templates: { increment: "3. task.yml", epic: "4. epic breakdown.yml" } }
---

# Architecture — camunda/camunda/security

> Draft from the repository; not reviewed by its team. Facts marked `TODO(confirm)` are inferred.

## 1. Purpose

The Orchestration Cluster's own security modules. The authorization and authentication logic
itself lives in the [Camunda Security Library](https://github.com/camunda/camunda-security-library)
(CSL), compiled in; this component holds what the cluster needs around it: the authorization enums
the engine serializes to its log and RocksDB, their mapping to CSL, the claims a command carries to
the engine, the engine's security configuration, the secondary-storage adapter CSL reads
authorizations through, and a few shared helpers ([README](README.md),
[Identity architecture § 5](../identity/docs/architecture.md)). Plain Java libraries, assembled by
`dist`. Part of the [Orchestration Cluster system](../SYSTEM.md) (role `security`).

## 2. Ownership boundary

**Owns:** the engine-side shape of the permission model (which resource types and permissions the
engine can store and how they map to CSL's), what goes into a broker request's claims, and the
small adapters and helpers in the front matter. The README states the direction of travel: "step by
step migrate the existing authentication and authorization APIs" to CSL
([epic](https://github.com/camunda/camunda-security-library/issues/9)), so this component shrinks
over time.

Owner: [CODEOWNERS](../CODEOWNERS) assigns `/security/` to `@camunda/identity` (so does
[`.codeowners`](../.codeowners)); the same team owns `/authentication/`. The two security ADRs name
"Identity / Core Features team" as DRI. Contact channel: TODO(confirm).

Neighbouring drafts route "the permission model", "authentication" and "tenant access" here. The
code says otherwise: the canonical enum catalogue, `RequiredAuthorization`, `SecurityContext`,
`TenantAccess` and the decisions are CSL's; authentication is `authentication`'s. TODO(confirm)
which the team wants to be the intake point for "a new permission" requests (this component or CSL).

**Does not own (route here instead):**

| If you need… | It belongs to | How to ask |
|---|---|---|
| A new authorization rule, authentication behavior, or change to `RequiredAuthorization` / `SecurityContext` / `TenantAccess` | Camunda Security Library (`camunda/camunda-security-library`) | issue in that repo |
| A new canonical resource or permission type | CSL first (`api/.../model/authz/`), then here (C2) | issue in CSL |
| Login, OIDC, basic auth, sessions, membership lookup | `camunda/camunda/authentication` | issue, same team |
| An authorization check on a command | `camunda/camunda/zeebe/engine` | issue, `component/zeebe-engine` |
| Which permission a read operation needs | `camunda/camunda/service` (`Authorizations`) | issue |
| Which permission an endpoint declares | `camunda/camunda/zeebe/gateway-protocol` (`x-required-permissions`) | issue, `component/c8-api` |
| Authorization filters on search queries | `camunda/camunda/search` | issue, `component/data-layer` |
| Per-tenant wiring, bean configuration | `camunda/camunda/dist` | issue |
| A `camunda.security.*` property | `camunda/camunda/configuration` | issue |

## 3. Structure

| Module (artifact) | Path | What it holds |
|---|---|---|
| `security-protocol` (`camunda-security-protocol`) | `io.camunda.zeebe.protocol.record.value`, `.mapper` | Engine-layer enums, `AuthorizationScope`, `AuthzModelMapper`. Java 8 |
| `security-core` (`camunda-security-core`) | `io.camunda.security.{auth,configuration,oidc,entity,util}` | `BrokerRequestAuthorizationConverter`, `AuthenticatedOwnerIdsUtil`, `EngineSecurityConfig(urations)`, `SaasConfigurationHelper`, `OidcClaimExtractor`, `ClusterMetadata`, `ArgumentUtil`, the two Spring conditions |
| `security-services` (`camunda-security-services`) | `io.camunda.security.impl` | `SearchAuthorizationScopeRepository`, `AuthorizationCheckerFactory` |
| `security-validation` (`camunda-security-validation`) | `io.camunda.security.validation` | `ClusterVariableValidator`, on CSL's validation module (same package name) |

Layering ([security-protocol README](security-protocol/README.md), CSL
[ADR 0008](https://github.com/camunda/camunda-security-library/blob/main/docs/adr/0008-authz-enum-ownership-and-layered-usage.md)):
code above the engine (service, search, exporters, persistence) uses CSL's enums; the engine, log
and RocksDB use the enums in `security-protocol`; `AuthzModelMapper` translates. The README says
the mapper lives in `service/` and links CSL ADR-0016; the mapper is in `security-protocol` and the
ADR is 0008. TODO(confirm) and fix the README.

Direction: `security-protocol` depends only on CSL's API; `security-core` adds `zeebe-auth` and
Spring; only `security-services` reaches `search`. Nothing here depends on `service`, the engine or
the gateways ([SYSTEM.md](../SYSTEM.md) DR6; not enforced by a test yet). No variant-specific code
except the SaaS / Self-Managed conditions.

`ClusterMetadata` and `ArgumentUtil` have no main-code user outside this component. TODO(confirm):
still needed.

## 4. Binding decisions

ADR index: [`docs/adr/`](../docs/adr/README.md); this component has no ADR folder. Decisions that
most often shape work here:

- [Security ADR 001](../docs/adr/security/001-endpoint-required-permission-mapping.md): endpoint →
  permission in `x-required-permissions`, validated against a registry generated from
  `AuthorizationResourceType`.
- [Security ADR 002](../docs/adr/security/002-tenant-access-provider-ownership-and-seam.md):
  tenant-ownership and the concrete tenant-access provider belong to CSL core.
- CSL [ADR 0008](https://github.com/camunda/camunda-security-library/blob/main/docs/adr/0008-authz-enum-ownership-and-layered-usage.md)
  (enum ownership and layered usage) and [ADR 0014](https://github.com/camunda/camunda-security-library/blob/main/docs/adr/0014-unified-authz-framework-in-core.md)
  (unified authz framework in core).
- Physical tenants: [ADR 0005](../docs/adr/orchestration-cluster/0005-physical-tenant-routing-of-authorization-reads.md)
  (authorization reads per tenant), [ADR 0009](../docs/adr/orchestration-cluster/0009-propagating-physical-tenant-context-across-async-authorization-reads.md).
- Identity module ADRs ([`identity/docs/adr/`](../identity/docs/adr/README.md)), notably
  [0003 resource-based authorization model](../identity/docs/adr/0003-resource-based-authorization-model.md).

## 5. Planning constraints

Every plan must answer each of these (applies / n/a + decision or reason).

### C1 — Engine-layer enum stability
- **Question:** Does the change remove, rename or reorder a constant in `security-protocol`? If a
  constant is obsolete, is it deprecated instead? Revapi on `zeebe/protocol` includes only the
  `zeebe-protocol` archive, so it does not appear to check these enums. TODO(confirm) which check
  guards them; the README's `ignored-changes.json` does not exist in the repo.
- **Hard:** yes
- **Detail:** [security-protocol README](security-protocol/README.md) § Rules for contributors

### C2 — A new resource or permission type in every place
- **Question:** Is the value added to the CSL enum (released and pinned), here, to the SBE schema in
  `zeebe/protocol`, to `AuthzModelMapper` and `AuthzModelMapperTest`, to `resource-permissions.json`
  and the `x-required-permissions` of the endpoints that enforce it, and to the engine/service
  checks? Which order of PRs lets each one build?
- **Hard:** yes
- **Detail:** [security-protocol README](security-protocol/README.md),
  [security ADR 001](../docs/adr/security/001-endpoint-required-permission-mapping.md),
  [overview: `security/` permission model](../docs/architecture/overview.md)

### C3 — CSL or this component
- **Question:** Is the change decision logic or a boundary type (→ CSL, released first and pinned)
  or OC-specific glue (→ here, `authentication`, the engine or `dist`)? Does it add to what the
  migration to CSL is removing?
- **Hard:** yes
- **Detail:** [Identity architecture § 5.1](../identity/docs/architecture.md) (CSL extension points
  and OC adapters)

### C4 — Java 8 for security-protocol
- **Question:** Does code added to `security-protocol` compile for Java 8, like `zeebe-protocol`?
  Does a newer CSL API it calls still run there? TODO(confirm) how CSL's API stays Java 8 compatible.
- **Hard:** yes
- **Detail:** `security-protocol/pom.xml` (`version.java` 8, "must match the zeebe-protocol lowest
  support JDK version")

### C5 — Claims in broker requests
- **Question:** Does the change alter which claims a command carries or when? The engine reads
  them for authorization and tenancy, and exporters read username and client id for the audit-log
  actor. Does it read lazy memberships (`authenticatedGroupIds`) only when they are sent?
  TODO(confirm) the compatibility promise for claims already written to the log.
- **Hard:** yes
- **Detail:** `BrokerRequestAuthorizationConverter` Javadoc and `BrokerRequestAuthorizationConverterTest`

### C6 — Physical-tenant scope
- **Question:** Is anything that reads authorization data built per physical tenant (one
  `SearchAuthorizationScopeRepository` / `AuthorizationChecker` per tenant's readers) and wired in
  `dist`?
- **Hard:** yes
- **Detail:** [ADR 0005](../docs/adr/orchestration-cluster/0005-physical-tenant-routing-of-authorization-reads.md),
  [ADR 0009](../docs/adr/orchestration-cluster/0009-propagating-physical-tenant-context-across-async-authorization-reads.md)

### C7 — Read and write path parity
- **Question:** Authorization data is read twice: from secondary storage here
  (`SearchAuthorizationScopeRepository`) and from RocksDB in the engine
  (`AuthorizationScopeStateAdapter`). Does the change keep both answering the same?
- **Hard:** no
- **Detail:** [Identity architecture § 5.1](../identity/docs/architecture.md) (callback extension points)

### C8 — Consumers updated together
- **Question:** Which of the consumers in the front matter (engine, protocol, exporters, service,
  gateways, `authentication`, `dist`) change in the same PR? Is a permission model change landed
  here before enforcement is wired elsewhere?
- **Hard:** no
- **Detail:** [overview](../docs/architecture/overview.md), front matter `consumers`

## 6. Data and persistence

No store of its own. The enums in `security-protocol` are persisted by the engine in log records and
RocksDB (C1). `SearchAuthorizationScopeRepository` reads authorization entities from secondary
storage (Elasticsearch, OpenSearch or RDBMS) through `search`, with resource-access checks disabled
because it *is* the authorization source.

## 7. Cross-cutting qualities

- **Security:** this component is part of the trust boundary; a wrong mapping or a dropped claim
  grants or denies access silently. Authorization is enforced in the engine (writes) and through
  `search` (reads), both via CSL.
- **Tenancy:** physical tenants per C6; multi-tenancy (`tenantId`) checks travel as claims (C5) and
  are switched by `EngineSecurityConfig.isMultiTenancyChecksEnabled()`.
- **Performance:** `hasAuthorizedScope` queries with page size 1; `findAuthorizedScopes` and
  `findPermissionTypes` are unlimited queries. TODO(confirm) whether large authorization sets are a
  known concern.
- **Nullness:** `security-services` uses JSpecify `@NullMarked`; other modules not yet.

## 8. Delivery

As [Orchestration Cluster SYSTEM.md](../SYSTEM.md) § 6, plus:

- CSL is released on its own cadence; a change that needs CSL first waits for a CSL release and a
  bump of `version.camunda-security-library` (C3). As of 8.10, core security API classes moved to
  CSL ([README](README.md)).
- `camunda-security-protocol` is a dependency of `zeebe-protocol`, so it ships with every artifact
  that carries the protocol. TODO(confirm) whether it is covered by `zeebe-protocol`'s public
  compatibility promise to exporter authors.
- Backports via the backport action; enum additions in a patch release need the SBE schema and
  rolling-update compatibility. TODO(confirm) the team's rule for backporting new permission types.

## 9. Testing expectations

- **Unit tests per module:** `AuthzModelMapperTest` (round trips for every constant, both
  directions), `PermissionTypeTest`, `AuthorizationScopeTest`, `BrokerRequestAuthorizationConverterTest`,
  `SearchAuthorizationScopeRepositoryTest` (with `FakeAuthorizationReader`), and the helper tests.
- **Guards elsewhere:** `ResourcePermissionsRegistryTest` (`zeebe/gateway-rest`) keeps
  `resource-permissions.json` equal to `buildResourcePermissionsMap()`; `AuthorizationArchTest`
  (`qa/archunit-tests`) requires engine processors to check authorization.
- **Runtime:** the per-resource auth suites in `qa/acceptance-tests/.../it/auth/` (owned by
  `@camunda/identity`) are the oracle for granted and denied access.
- Run: `./mvnw verify -pl security/security-core,security/security-protocol,security/security-services -DskipTests=false -Dquickly`.

## 10. Planning conventions

- Issues: `.github/ISSUE_TEMPLATE/` (`2. feature_request.yml`, `3. task.yml`, `4. epic breakdown.yml`).
  Work that starts in CSL is filed in `camunda/camunda-security-library`.
- Labels: no `component/*` label maps to `security/`; `component/identity` (for `identity/`) and
  `area/security` are the closest. TODO(confirm) which label intake uses.
- TODO(confirm): plans directory and ID prefix.

## 11. Glossary

| Term | Meaning here |
|---|---|
| CSL | Camunda Security Library, `camunda/camunda-security-library`; owns the decisions and boundary types |
| Engine-layer enums | The copies of the authz enums in `security-protocol`, serialized by the engine; not CSL's enums of the same name |
| Claims map | The `Map<String,Object>` of authorization claims attached to every broker request |
| Authorization scope | One grant's matcher (any, id, property) and resource id or property name |
| Owner ids | The user or client plus every group, role and mapping rule it belongs to, by `EntityType` |
| Physical tenant | An isolated tenant of the cluster with its own storage and configuration; not the multi-tenancy `tenantId` |
