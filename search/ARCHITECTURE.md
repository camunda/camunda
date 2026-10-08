---
architecture_md: 1
component: camunda/camunda/search
system: { id: orchestration-cluster, map: "camunda/camunda/SYSTEM.md" }
lifecycle: production
kind: [library]
summary: The read side of the Orchestration Cluster's secondary storage, a storage-agnostic search domain, reader and search-client API with Elasticsearch and OpenSearch implementations, that applies authorization, tenant and physical-tenant scoping to every query, plus the ES/OS connection factory and the customer header-plugin SDK.
team: { name: camunda/data-layer, contact: unknown }
intake: { how: issue, template: "2. feature_request.yml", labels: [component/data-layer] }
owns:
  - "The search domain (camunda-search-domain): read-model entity records (io.camunda.search.entities), filters and Operation/Operator, typed queries, sort options, pagination, aggregations and their results, and the search exceptions (CamundaSearchException, NoSecondaryStorageException, access-denied exceptions)"
  - "The storage-agnostic reader contract (camunda-search-client-reader): the *Reader interfaces, SearchEntityReader get/search semantics, SearchClientReaders and PhysicalTenantSearchClientReaders"
  - "The search-client API callers use (camunda-search-client): the *SearchClient interfaces, SearchClientsProxy.withSecurityContext / withPhysicalTenant, CamundaSearchClients, and the no-op and no-secondary-storage proxies"
  - "How a read is authorized in storage: search pre-filters with ResourceAccessChecks, get post-checks and throws (AbstractResourceAccessController), and the ES/OS controllers that turn checks into query clauses"
  - "Translating typed queries to Elasticsearch and OpenSearch: ServiceTransformers (filters, sorts, entities, aggregations, results), the *DocumentReader classes and the DB-neutral query model (camunda-search-client-query-transformer, -elasticsearch, -opensearch)"
  - "Connecting to Elasticsearch and OpenSearch (camunda-search-client-connect): ConnectConfiguration, ElasticsearchConnector, OpensearchConnector (incl. AWS SigV4), per-physical-tenant SearchClients, TLS, proxy and interceptor-plugin loading"
  - "The Search Plugin SDK (camunda-search-client-plugin, Apache 2.0): DatabaseCustomHeaderSupplier and CustomHeader"
  - "Test support for ES/OS integration tests (camunda-search-test-utils): SearchDBExtension, test containers, SearchClientAdapter"
does_not_own:
  - { concept: "ES/OS index templates, index descriptors and the document entity classes the exporters write", owner: camunda/camunda/webapps-schema }
  - { concept: "RDBMS readers (*DbReader, RdbmsTenantReaders, RdbmsResourceAccessController), MyBatis mappers and the Liquibase changelog", owner: camunda/camunda/db }
  - { concept: "Creating, migrating and initializing indices per physical tenant", owner: camunda/camunda/schema-manager }
  - { concept: "Writing the data that is read here", owner: camunda/camunda/zeebe/exporters }
  - { concept: "Which permission a read requires and the service API around a query", owner: camunda/camunda/service }
  - { concept: "REST search request/response shapes, filter property types and pagination contract", owner: camunda/camunda/zeebe/gateway-protocol }
  - { concept: "Mapping REST search requests to search-domain queries", owner: camunda/camunda/gateways/gateway-mapping-http }
  - { concept: "Permission model, SecurityContext, ResourceAccessChecks, ResourceAccessController and TenantOwnedEntity types", owner: camunda/camunda-security-library }
  - { concept: "Spring wiring of the search clients, readers and access controllers per physical tenant, and backend selection", owner: camunda/camunda/dist }
  - { concept: "camunda.data.secondary-storage.* properties", owner: camunda/camunda/configuration }
  - { concept: "ES/OS snapshot backup and restore", owner: camunda/camunda/webapps-backup }
depends_on:
  - id: security-library
    component: camunda/camunda-security-library
    kind: library
    contract: "io.camunda:camunda-security-library-api, -core: SecurityContext, ResourceAccessChecks, ResourceAccessController, AuthorizationCheck, TenantCheck, TenantOwnedEntity"
    versions: "pinned by version.camunda-security-library in parent/pom.xml"
    workaround_policy: never
  - id: webapps-schema
    component: camunda/camunda/webapps-schema
    kind: schema
    contract: "io.camunda:webapps-schema: IndexDescriptors and io.camunda.webapps.schema.entities used by the ES/OS transformers and readers"
    versions: same monorepo release
    workaround_policy: never
  - id: protocol
    component: camunda/camunda/zeebe/protocol
    kind: schema
    contract: "io.camunda:zeebe-protocol: record value types and enums reused in the search domain"
    versions: same monorepo release
    workaround_policy: never
  - id: zeebe-util
    component: camunda/camunda/zeebe/util
    kind: library
    contract: "io.camunda:zeebe-util: shared utilities"
    versions: same monorepo release
    workaround_policy: never
  - id: spring-utils
    component: camunda/camunda/spring-utils
    kind: library
    contract: "io.camunda:camunda-spring-utils (used by search-domain)"
    versions: same monorepo release
    workaround_policy: never
  - id: elasticsearch-client
    component: co.elastic.clients:elasticsearch-java
    kind: external
    contract: "Elasticsearch Java API client and low-level REST client"
    versions: "version.elasticsearch-java-client in parent/pom.xml; servers tested per .ci/db-versions.yml"
    workaround_policy: adapter-boundary
  - id: opensearch-client
    component: org.opensearch.client:opensearch-java
    kind: external
    contract: "OpenSearch Java client, ApacheHttpClient5Transport and AwsSdk2Transport"
    versions: "version.opensearch-java in parent/pom.xml; servers tested per .ci/db-versions.yml"
    workaround_policy: adapter-boundary
  - id: aws-sdk
    component: software.amazon.awssdk
    kind: external
    contract: "auth, regions, aws-crt-client for SigV4-signed OpenSearch requests"
    versions: "version.awssdk in parent/pom.xml"
    workaround_policy: adapter-boundary
  - id: libraries
    component: "Apache HttpClient 4/5, Jackson, Caffeine, commons-lang3, jspecify, Testcontainers (test-utils)"
    kind: external
    contract: "HTTP transport and interceptors, JSON mapping, caching, nullness annotations, ES/OS test containers"
    versions: "managed in parent/pom.xml"
    workaround_policy: adapter-boundary
  - id: search-engines
    component: "Elasticsearch, OpenSearch (incl. Amazon OpenSearch Service)"
    kind: platform
    contract: "the secondary-storage servers queried at runtime"
    versions: ".ci/db-versions.yml (ES 8.19, 9.x; OS 2.19, 3.x)"
    workaround_policy: adapter-boundary
consumers:
  - { who: camunda/camunda/service, via: "*SearchClient interfaces with withSecurityContext, search-domain queries and entities", promise: "internal Java API, same release; signature changes land with callers in the same PR" }
  - { who: camunda/camunda/db, via: "search-client-reader interfaces, SearchClientReaders, AbstractResourceAccessController, search-domain", promise: "internal Java API, same release; every reader change needs an RDBMS implementation" }
  - { who: camunda/camunda/dist, via: "CamundaSearchClients, connectors, SearchClientReaders, ServiceTransformers, ES/OS search clients", promise: "internal Java API, same release" }
  - { who: camunda/camunda/zeebe/engine, via: "SearchClientsProxy and search-domain queries for batch-operation item providers", promise: "internal Java API, same release" }
  - { who: "camunda/camunda/authentication, camunda/camunda/security (security-services)", via: "search clients, readers and search-domain entities for authorization and membership reads", promise: "internal Java API, same release" }
  - { who: "camunda/camunda/zeebe/gateway-rest, camunda/camunda/gateways/gateway-mapping-http, camunda/camunda/gateways/gateway-mcp, camunda/camunda/zeebe/gateway, camunda/camunda/zeebe/broker", via: "search-domain types (and search-client in broker and gateway-rest)", promise: "internal Java API, same release" }
  - { who: "camunda/camunda/zeebe/exporters, camunda/camunda/schema-manager, camunda/camunda/webapps-backup, camunda/camunda/configuration, camunda/camunda/operate, debug-cli", via: "search-client-connect (ConnectConfiguration, connectors); exporters also search-domain; search-test-utils in tests", promise: "internal Java API, same release" }
  - { who: "Optimize (optimize/, not a system member)", via: "search-client-connect and the plugin SDK", promise: "internal Java API, same release" }
  - { who: "Self-Managed operators", via: "DatabaseCustomHeaderSupplier plugins configured with camunda.data.secondary-storage.<type>.interceptor-plugins", promise: "published SDK; TODO(confirm) compatibility promise" }
  - { who: "qa/acceptance-tests, zeebe/qa, operate/data-generator, qa/archunit-tests", via: "search clients, readers, test-utils; archunit rules over io.camunda.search", promise: none }
exposes:
  - { contract: "SearchClientsProxy and the *SearchClient interfaces, scoped with withPhysicalTenant and withSecurityContext", spec: search-client/src/main/java/io/camunda/search/clients/SearchClientsProxy.java, policy: "internal; changed together with every caller in the same PR" }
  - { contract: "Search domain: entities, filters, queries, sorts, page, aggregations, exceptions", spec: search-domain/src/main/java/io/camunda/search/, policy: "internal; entities are records (SearchEntityArchTest); shape reaches REST users through gateway-mapping-http" }
  - { contract: "Extension point: reader interfaces and SearchClientReaders; a backend supplies one reader per entity (ES/OS *DocumentReader here, RDBMS *DbReader in db/rdbms)", spec: search-client-reader/src/main/java/io/camunda/search/clients/reader/, policy: "internal; a new reader method needs every backend" }
  - { contract: "Extension point: AbstractResourceAccessController; a backend subclasses it to turn authorization and tenant checks into its query; ResourceAccessDelegatingController picks the first controller whose supports() is true", spec: search-client/src/main/java/io/camunda/search/clients/auth/AbstractResourceAccessController.java, policy: "internal" }
  - { contract: "Extension point: DocumentBasedSearchClient / DocumentBasedWriteClient, implemented per document store (ES, OS)", spec: search-client-query-transformer/src/main/java/io/camunda/search/clients/, policy: "internal" }
  - { contract: "ES/OS connection factory: ConnectConfiguration, ElasticsearchConnector, OpensearchConnector, SearchClients.from", spec: search-client-connect/src/main/java/io/camunda/search/connect/, policy: "internal; shared with exporters, schema-manager, Operate, Optimize, backup" }
  - { contract: "Search Plugin SDK: DatabaseCustomHeaderSupplier adds one HTTP header to every ES/OS request (not on the AWS SigV4 transport); loaded from the classpath or an external JAR", spec: search-client-plugin/README.md, policy: "customer-facing, Apache 2.0; TODO(confirm) versioning and deprecation policy" }
  - { contract: "Test utilities: SearchDBExtension, TestSearchContainers, SearchClientAdapter", spec: search-test-utils/src/main/java/io/camunda/search/test/utils/, policy: "test-only, internal" }
constraints:
  - { id: C1, name: Works on every secondary-storage backend, hard: true, ref: "../docs/rest-api-endpoint-guidelines.md#212-upgrading-an-existing-filter-field-to-advanced-search" }
  - { id: C2, name: Authorization and tenant checks on every read, hard: true, ref: ../docs/adr/orchestration-cluster/0005-physical-tenant-routing-of-authorization-reads.md }
  - { id: C3, name: Explicit physical tenant, hard: true, ref: ../docs/adr/orchestration-cluster/0005-physical-tenant-routing-of-authorization-reads.md }
  - { id: C4, name: Schema field exists and is additive, hard: true, ref: ../docs/architecture/overview.md }
  - { id: C5, name: Entity record conventions, hard: true, ref: ../qa/archunit-tests/src/test/java/io/camunda/search/entities/SearchEntityArchTest.java }
  - { id: C6, name: No or degraded secondary storage, hard: true, ref: "../docs/rest-api-endpoint-guidelines.md#25-eventually-consistent-annotation-x-eventually-consistent" }
  - { id: C7, name: Supported database versions, hard: true, ref: ../.ci/db-versions.yml }
  - { id: C8, name: Callers beyond the service layer, hard: false, ref: ../docs/architecture/overview.md }
decisions: ../docs/adr/README.md
planning: { issue_templates: { increment: "3. task.yml", epic: "4. epic breakdown.yml" } }
---

# Architecture — camunda/camunda/search

> Draft from the repository; not reviewed by its team. Facts marked `TODO(confirm)` are inferred.

## 1. Purpose

The read path into secondary storage. `service`, the engine (batch operations), `authentication`
and `security` ask a `*SearchClient` for entities; this component checks the caller's authorization
and tenant access, and translates the typed query into Elasticsearch or OpenSearch queries (RDBMS
reads plug in from `db/rdbms`). It also gives the exporters, `schema-manager`, Operate and Optimize
one way to connect to ES/OS. Application code never queries ES/OS indices directly; it goes through
here ([architecture overview](../docs/architecture/overview.md), "ES/OS index schema"). A plain Java
library, assembled by `dist`. Part of the [Orchestration Cluster system](../SYSTEM.md) (role
`storage`, rule DR5).

## 2. Ownership boundary

**Owns:** the read model (entities, filters, sorts, queries), the reader and search-client
contracts every backend implements, how authorization and tenant checks become query clauses, the
ES/OS query translation, the ES/OS connectors, and the Search Plugin SDK. The full list is in the
front matter.

Owner: GitHub [CODEOWNERS](../CODEOWNERS) has no line for `search/`. The fine-grained ownership
file [`.codeowners`](../.codeowners) (codeowners-plus) assigns `/search/` to `@camunda/data-layer`
under "Database and search infrastructure", and the front matter uses that team.
TODO(confirm): data-layer owns the module, and whether a line should be added to CODEOWNERS. Contact
channel: TODO(confirm).

**Does not own (route here instead):**

| If you need… | It belongs to | How to ask |
|---|---|---|
| A new field in an ES/OS index | `camunda/camunda/webapps-schema` (data-layer owns the schema path) | issue, `component/data-layer` |
| A new RDBMS column, reader or MyBatis mapping | `camunda/camunda/db` | issue, `component/data-layer` |
| Data that is not exported yet | `camunda/camunda/zeebe/exporters` (Camunda exporter, RDBMS exporter) | issue, `component/data-layer` |
| A new REST search endpoint or filter property type | `camunda/camunda/zeebe/gateway-protocol`, `zeebe/gateway-rest` | issue, `component/c8-api` |
| Which permission a query requires | `camunda/camunda/service` (`Authorizations`) | issue |
| A new permission or resource type, tenant-ownership contract | Camunda Security Library ([security ADR 002](../docs/adr/security/002-tenant-access-provider-ownership-and-seam.md)), `camunda/camunda/security` | issue |
| How the search clients are wired or which backend is active | `camunda/camunda/dist`, `camunda/camunda/configuration` | issue |
| Index creation, migration, shard settings | `camunda/camunda/schema-manager` ([storage ADR 002](../docs/adr/storage/002-per-index-shard-configuration.md)) | issue, `component/data-layer` |

## 3. Structure

| Module | Holds | Depends on (in `search/`) |
|---|---|---|
| `search-domain` | `io.camunda.search.{entities,filter,query,sort,page,aggregation,result,exception}` | — |
| `search-client-reader` | `io.camunda.search.clients.reader`: `*Reader` interfaces, `SearchClientReaders`, `PhysicalTenantSearchClientReaders` | domain |
| `search-client` | `*SearchClient` interfaces, `SearchClientsProxy`, `CamundaSearchClients`, `auth/AbstractResourceAccessController`, no-DB proxies | domain, reader |
| `search-client-query-transformer` | ES/OS-neutral query model, `ServiceTransformers`, `reader/*DocumentReader`, `auth/` (document access controllers), `DocumentBasedSearchClient` | client, domain, reader |
| `search-client-elasticsearch`, `-opensearch` | `ElasticsearchSearchClient`, `OpensearchSearchClient` and the transformers to native client types | query-transformer, domain |
| `search-client-connect` | `ConnectConfiguration`, connectors, per-tenant `SearchClients`, plugin loading | client, plugin |
| `search-client-plugin` | `DatabaseCustomHeaderSupplier`, `CustomHeader` (Apache 2.0, no dependencies) | — |
| `search-test-utils` | JUnit 5 ES/OS extensions and helpers | connect |

Call path: caller → `searchClients.withPhysicalTenant(pt).withSecurityContext(ctx).searchX(query)`
→ `CamundaSearchClients` → that tenant's `ResourceAccessController.doSearch/doGet` → that tenant's
reader (`*DocumentReader` → `ServiceTransformers` → ES/OS client, or `*DbReader` in `db/rdbms`).

Direction rules:

- Domain at the bottom, vendor clients at the top; only `query-transformer`, `-elasticsearch` and
  `-opensearch` know ES/OS, and nothing here knows RDBMS. RDBMS code lives in `db/rdbms` and
  implements the reader contract. TODO(confirm): whether this is a rule or the current state; no
  ArchUnit test enforces it.
- No Spring configuration here; beans are built in `dist` (`application/commons/search/`,
  `application/commons/rdbms/RdbmsConfiguration.java`). TODO(confirm): rule or current state.
- Variant-specific code: by backend only (ES vs OS module, RDBMS in `db`), selected by
  `camunda.data.secondary-storage.type` (`elasticsearch`, `opensearch`, `rdbms`, `none`).

## 4. Binding decisions

ADR index: [`docs/adr/`](../docs/adr/README.md) (domain `storage` covers `db/`, `search/`,
`webapps-schema/`, `schema-manager/`). This module has no ADR folder of its own. The decisions
that most often shape search work:

- [Physical-tenant routing of the authorization layer](../docs/adr/orchestration-cluster/0005-physical-tenant-routing-of-authorization-reads.md)
  (ADR 0005): per-tenant readers and access controllers, no default-pinned search client.
- [Tenant context across async authorization reads](../docs/adr/orchestration-cluster/0009-propagating-physical-tenant-context-across-async-authorization-reads.md) (ADR 0009).
- [Remove numeric key from Identity entity filters](../docs/adr/storage/001-remove-numeric-key-from-identity-entity-filters.md)
  (storage ADR 001): filter Identity entities by their string IDs only.
- [Tenant-access provider ownership and seam](../docs/adr/security/002-tenant-access-provider-ownership-and-seam.md)
  (security ADR 002): tenant ownership is a CSL contract that search entities implement.
- [Per-physical-tenant schema initialization](../docs/adr/management/005-per-physical-tenant-schema-initialization.md).

Rules without an ADR: [REST API guidelines § 2.10–2.12, § 5.1](../docs/rest-api-endpoint-guidelines.md)
(search conventions, advanced filters, upgrading a filter) and the
[secondary-storage docs](../docs/monorepo-docs/architecture/components/secondary-storage/index.md).

## 5. Planning constraints

Every plan must answer each of these (applies / n/a + decision or reason).

### C1 — Works on every secondary-storage backend
- **Question:** Is the new entity, filter, sort or aggregation implemented for Elasticsearch,
  OpenSearch (transformers here) and RDBMS (`*DbReader` and MyBatis mapper in `db/rdbms`)? If one
  backend can't support it, what does the caller get?
- **Hard:** yes
- **Detail:** [Guidelines § 2.12](../docs/rest-api-endpoint-guidelines.md), `@MultiDbTest`

### C2 — Authorization and tenant checks on every read
- **Question:** Does the read go through `withSecurityContext` and the `ResourceAccessController`?
  For a new entity: how does its transformer build the authorization clause, and does it carry a
  tenant field? Is it a search (pre-filtered) or a get (post-checked, throws access denied)?
- **Hard:** yes
- **Detail:** [ADR 0005](../docs/adr/orchestration-cluster/0005-physical-tenant-routing-of-authorization-reads.md),
  [security ADR 002](../docs/adr/security/002-tenant-access-provider-ownership-and-seam.md)

### C3 — Explicit physical tenant
- **Question:** Is the physical tenant always explicit (`withPhysicalTenant`, per-tenant readers and
  connectors), with no fallback to `default`?
- **Hard:** yes
- **Detail:** [ADR 0005](../docs/adr/orchestration-cluster/0005-physical-tenant-routing-of-authorization-reads.md)

### C4 — Schema field exists and is additive
- **Question:** Is the field in `webapps-schema` and `db/rdbms-schema` first, written by the
  exporter, and what does the query return for data exported before the field existed?
- **Hard:** yes
- **Detail:** [Architecture overview](../docs/architecture/overview.md), "ES/OS index schema"

### C5 — Entity record conventions
- **Question:** Are new entities records with `@Nullable` where needed and collection fields
  defaulted to mutable empty collections?
- **Hard:** yes
- **Detail:** `SearchEntityArchTest` in [`qa/archunit-tests`](../qa/archunit-tests)

### C6 — No or degraded secondary storage
- **Question:** What happens with `camunda.data.secondary-storage.type=none`
  (`NoDBSearchClientsProxy`), and is the endpoint marked `x-eventually-consistent` and
  `@RequiresSecondaryStorage`?
- **Hard:** yes
- **Detail:** [Guidelines § 2.5](../docs/rest-api-endpoint-guidelines.md)

### C7 — Supported database versions
- **Question:** Does the query or client call work on every ES and OS version in
  `.ci/db-versions.yml` (and SaaS), and does a client upgrade keep them all?
- **Hard:** yes
- **Detail:** [`.ci/db-versions.yml`](../.ci/db-versions.yml), `zeebe-search-integration-tests.yml`

### C8 — Callers beyond the service layer
- **Question:** Does the change touch a type or connector used by the engine, `db/rdbms`,
  `authentication`, the exporters, `schema-manager`, Operate, Optimize or plugin authors? Are they
  updated in the same PR, and is the plugin SDK unchanged or versioned?
- **Hard:** no
- **Detail:** front matter `consumers`

## 6. Data and persistence

None owned: this component reads, it does not store. ES/OS indices are defined in `webapps-schema`
and created by `schema-manager`; RDBMS tables in `db/rdbms-schema`. Supported servers:
[`.ci/db-versions.yml`](../.ci/db-versions.yml) (ES 8.19 / 9.x, OS 2.19 / 3.x, PostgreSQL, MySQL,
MariaDB, MSSQL, Azure SQL, Oracle). Reads are eventually consistent with the engine.

## 7. Cross-cutting qualities

- **Security:** authorization and tenant checks are applied here per read (C2); anonymous
  authentication bypasses them (`AnonymousResourceAccessController`). Which permission a read needs
  is decided in `service`.
- **Tenancy:** physical tenants by per-tenant readers, connectors and controllers (C3);
  multi-tenancy `tenantId` by a terms clause on the tenant field.
- **Connectivity:** TLS, proxy, basic auth, AWS SigV4 for OpenSearch; custom headers through the
  plugin SDK (not applied on the AWS transport).
- **Performance:** TODO(confirm): query limits, pagination caps or timeouts the team expects plans to respect.

## 8. Delivery

As [Orchestration Cluster SYSTEM.md](../SYSTEM.md) § 6. The Java API is internal and ships in the
same release as its callers. Exception: `camunda-search-client-plugin` is a customer-facing SDK
under Apache 2.0 that customers build against
([README](search-client-plugin/README.md)). TODO(confirm): its compatibility promise across minors.

## 9. Testing expectations

- **Unit tests:** per module (transformers, access controllers, connectors with WireMock,
  `CamundaSearchClientsTest`). Run the sub-module, e.g.
  `./mvnw verify -pl search/search-client-query-transformer -DskipTests=false -Dquickly`.
- **Architecture:** `SearchEntityArchTest`, `RdbmsDbModelDependencyArchTest` in `qa/archunit-tests`.
- **Integration:** `@MultiDbTest` in [`qa/acceptance-tests`](../docs/testing/acceptance.md) across
  ES, OS and RDBMS; `zeebe-search-integration-tests.yml` runs them against the min/max ES/OS
  versions on weekdays and `zeebe-rdbms-integration-tests.yml` for RDBMS.
- `search-test-utils` gives ES/OS containers or an AWS OpenSearch target
  (`-Dtest.integration.opensearch.aws.url`).

## 10. Planning conventions

- Issues: templates `2. feature_request.yml`, `3. task.yml`, `4. epic breakdown.yml` in
  `.github/ISSUE_TEMPLATE/`; label `component/data-layer` (create-issue skill: "`db/` or `search/`").
- TODO(confirm): plans directory and ID prefix for plan refs.

## 11. Glossary

| Term | Meaning here |
|---|---|
| Secondary storage | ES, OS or RDBMS filled by exporters; the read path only |
| Search client | The caller-facing API (`*SearchClient`), scoped by physical tenant and security context |
| Reader | The per-backend implementation behind a search client (`*DocumentReader`, `*DbReader`) |
| Resource access checks | The authorization and tenant conditions a controller adds to a search or checks after a get |
| Physical tenant | An isolated tenant of the cluster with its own storage and readers; not the multi-tenancy `tenantId` |
| Document-based | ES and OS, which share the query-transformer layer; RDBMS does not |
