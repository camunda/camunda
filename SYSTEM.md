---
system_md: 1
id: orchestration-cluster
name: Orchestration Cluster
summary: The Camunda 8 runtime that executes BPMN processes and DMN decisions and serves the Orchestration Cluster API, Operate, Tasklist and Admin, for SaaS and Self-Managed.
dri: unknown
roles:
  - { id: contracts, meaning: "API and record specifications other roles and outside consumers code against (OpenAPI, gRPC proto, SBE records, exporter API)" }
  - { id: clients, meaning: "Published client libraries that call the cluster through its APIs" }
  - { id: webapps, meaning: "Browser UIs served by the cluster (Operate, Tasklist, Admin) and their server side" }
  - { id: gateways, meaning: "Entry points that authenticate requests and hand them on (REST, gRPC, MCP)" }
  - { id: services, meaning: "Domain service layer between gateways and the engine or secondary storage" }
  - { id: security, meaning: "Authentication, the permission model and authorization checks" }
  - { id: engine, meaning: "BPMN/DMN execution and the state of every runtime and identity entity" }
  - { id: platform, meaning: "Broker, Raft log, partitioning, backup and the other distributed-platform modules the engine runs on" }
  - { id: export, meaning: "Exporters that turn engine records into secondary-storage data" }
  - { id: storage, meaning: "Secondary-storage schemas, schema management, search and RDBMS access" }
  - { id: shared, meaning: "Utilities and configuration used across roles" }
  - { id: assembly, meaning: "Wires components into deployable applications; no application logic" }
components:
  - { component: camunda/camunda/zeebe/gateway-protocol, role: contracts }
  - { component: camunda/camunda/zeebe/protocol, role: contracts }
  - { component: camunda/camunda/zeebe/exporter-api, role: contracts }
  - { component: camunda/camunda/clients, role: clients }
  - { component: camunda/camunda/webapp/client, role: webapps }
  - { component: camunda/camunda/webapp/client/apps/orchestration-cluster-webapp/src/tasklist, role: webapps }
  - { component: camunda/camunda/webapp/client/apps/orchestration-cluster-webapp/src/operate, role: webapps }
  - { component: camunda/camunda/webapp/client/apps/orchestration-cluster-webapp/src/admin, role: webapps }
  - { component: camunda/camunda/webapp/server, role: webapps }
  - { component: camunda/camunda/operate, role: webapps }
  - { component: camunda/camunda/identity, role: webapps }
  - { component: camunda/camunda/zeebe/gateway-rest, role: gateways }
  - { component: camunda/camunda/zeebe/gateway-grpc, role: gateways }
  - { component: camunda/camunda/zeebe/gateway, role: gateways }
  - { component: camunda/camunda/gateways/gateway-mcp, role: gateways }
  - { component: camunda/camunda/gateways/gateway-mapping-http, role: gateways }
  - { component: camunda/camunda/gateways/gateway-model, role: gateways }
  - { component: camunda/camunda/service, role: services }
  - { component: camunda/camunda/document, role: services }
  - { component: camunda/camunda/secret-store, role: services }
  - { component: camunda/camunda/authentication, role: security }
  - { component: camunda/camunda/security, role: security }
  - { component: camunda/camunda/zeebe/engine, role: engine }
  - { component: camunda/camunda/zeebe/broker, role: platform }
  - { component: camunda/camunda/zeebe/exporters, role: export }
  - { component: camunda/camunda/search, role: storage }
  - { component: camunda/camunda/db, role: storage }
  - { component: camunda/camunda/webapps-schema, role: storage }
  - { component: camunda/camunda/schema-manager, role: storage }
  - { component: camunda/camunda/webapps-backup, role: storage }
  - { component: camunda/camunda/configuration, role: shared }
  - { component: camunda/camunda/webapps-common, role: shared }
  - { component: camunda/camunda/dist, role: assembly }
rules:
  - { id: DR1, rule: "clients and webapps depend on the cluster only through contracts (REST OpenAPI, gRPC proto); never on gateways, services, engine or storage modules", enforced_by: "ClientForbidProtocolDependencyArchTest (client API vs generated REST protocol package only); webapps by build separation (npm workspace); otherwise none yet (gap)" }
  - { id: DR2, rule: "REST and MCP gateways reach the engine and storage only through services; they never call the gRPC gateway, the broker or search directly", enforced_by: "none yet (gap)" }
  - { id: DR3, rule: "services send commands to the engine through the broker and read only through storage (search, db); never read engine state", enforced_by: "ServiceSecurityContextArchTest, ServiceRegistryArchTest (search calls carry a security context; services reached via ServiceRegistry); direction itself: none yet (gap)" }
  - { id: DR4, rule: "export reads engine output only through contracts (zeebe/protocol records, zeebe/exporter-api); nothing outside engine and platform reads RocksDB", enforced_by: "none yet (gap)" }
  - { id: DR5, rule: "application code reaches secondary storage only through storage (search, db/rdbms); nothing queries Elasticsearch, OpenSearch or the RDBMS directly", enforced_by: "none yet (gap)" }
  - { id: DR6, rule: "gateways, services, engine and webapps depend on security for authentication and permissions, never the reverse", enforced_by: "none yet (gap)" }
  - { id: DR7, rule: "assembly may depend on any role; no role depends on assembly. shared depends on no other role", enforced_by: "none yet (gap)" }
---

# System — Orchestration Cluster

> Draft from the repository; not reviewed by its DRI. Facts marked `TODO(confirm)` are inferred.
> DRI: TODO(confirm) — no person or team is named yet.

## 1. Purpose and products

The Orchestration Cluster is the Camunda 8 runtime: the Zeebe engine executes BPMN processes and
DMN decisions, and the cluster serves the Orchestration Cluster REST API, the Zeebe gRPC API, an MCP
server for AI agents, and the Operate, Tasklist and Admin (Identity) web UIs. It ships as one
artifact (JAR, `camunda/camunda` Docker image) for SaaS and Self-Managed; `dist/` also builds
standalone variants (broker, gateway, Operate, schema manager, backup manager), and components can
run separately ([README](README.md), [architecture overview](docs/architecture/overview.md)).

Users: developers who deploy models and write job workers and applications against the APIs and
clients; operations engineers in Operate; task workers in Tasklist; administrators in Admin.

Optimize lives in this repo but is not part of the system (it reads exported data; § 4). Console,
Web Modeler, Desktop Modeler, Connectors and Management Identity are other parts of Camunda 8.

Member granularity: the components above are the ones that get their own ARCHITECTURE.md. Some
cover several Maven modules until their teams split them: `zeebe/broker` stands for the
distributed-platform modules (`zeebe/atomix`, `journal`, `logstreams`, `snapshot`, `backup`,
`backup-stores`, `restore`, `dynamic-config`, `scheduler`, `transport`, `stream-platform`,
`broker-client`, `dynamic-node-id-provider`, `zb-db`), and `zeebe/engine` for the libraries it
executes with (`bpmn-model`, `feel`, `dmn`, `expression-language`, `msgpack-*`).
TODO(confirm): this grouping, and whether `cluster/`, `spring-utils/` and `zeebe/util` are members.

## 2. Roles and dependency rules

The roles follow the CQRS split in the [architecture overview](docs/architecture/overview.md):
writes go gateway → services → broker → engine → exporters → secondary storage; reads go gateway →
services → search → secondary storage, never through the engine. Roles listed earlier in the front
matter are closer to users; dependencies point down that list, with `contracts`, `security` and
`shared` usable from any role that needs them.

- **DR1** keeps outside consumers on the published contracts. The REST API is specified in
  `zeebe/gateway-protocol/src/main/proto/v2/` and the gRPC API in `gateway.proto`; the webapps code
  against `@camunda/camunda-api-zod-schemas`, written by hand from that spec.
- **DR2, DR3** come from "`service/` as the REST-to-engine bridge" in the overview. The gRPC gateway
  sends commands to the broker directly (`broker-client`). TODO(confirm): whether DR2 should cover
  it, and whether the MCP gateway goes through `service/` like REST does.
- **DR4, DR5** come from the record/exporter contract and "never query ES/OS indices directly from
  application code" in the overview.
- **DR6** comes from "`security/` permission model" and "`authentication/` claim mapping" in the
  overview. Authorization decisions are delegated to the Camunda Security Library (§ 4).

ArchUnit rules for the system live in `qa/archunit-tests`; only the rules named in `enforced_by`
touch these directions. The rest are known gaps.

## 3. Composition paths

- **A new API capability** (command or query): `engine` (record, processor, state) →
  `zeebe/protocol` → `service` → `zeebe/gateway-rest` + `zeebe/gateway-protocol` (OpenAPI) →
  `clients` (Java client, Spring Boot starters) and `gateways/gateway-mcp`. Queries add
  `zeebe/exporters` (Camunda exporter, RDBMS exporter) → `webapps-schema` / `db/rdbms-schema` →
  `search` before `service`. The REST API is the primary surface: new core features land there
  first; gRPC is kept for compatibility and job streaming.
- **A UI feature**: the REST capability above → `@camunda/camunda-api-zod-schemas` (in
  `webapp/client/packages`, published to npm) → the pod under
  `webapp/client/apps/orchestration-cluster-webapp/src/{operate,tasklist,admin}` → served by
  `webapp/server`. Code in `src/shared` or `webapp/client/packages` reaches all three pods. The
  legacy frontends in `operate/client` and `identity/client` are being replaced.
- **A permission or authentication change**: `security` / `authentication` (on CSL) → `engine`
  (engine identity adapters), `service`, gateways and webapps; it must land in `security` before any
  enforcement is wired.
- **A secondary-storage field**: `webapps-schema` (ES/OS, additive only) or `db/rdbms-schema`
  (Liquibase, additive only) → `schema-manager` → `zeebe/exporters` (writes) and `search` (reads)
  → `service` → REST API and webapps. Optimize does not see it: it reads the dedicated
  Elasticsearch exporter's indices.
- **Consumers that are not products of this system** get every change to a contract too:
  - Camunda Process Test (`testing/`, @camunda/c8-testing) runs the cluster in a container and
    drives it through the Java client.
  - C8 Run (`c8run/`, @camunda/distribution) packages the cluster for local use.
  - `load-tests/`, `qa/` (acceptance, compatibility, E2E suites), `debug-cli/`, `microbenchmarks/`
    and nightly CI workflows run against the cluster or its modules.
  - Coding agents work through `gateways/gateway-mcp` and through the repo's AGENTS.md and skills.
  - Other products: Web Modeler and Desktop Modeler deploy and start processes via the REST API;
    Connectors run as job workers through the clients; Console manages SaaS clusters.
    TODO(confirm) the APIs each of them uses.

## 4. Edges to other systems

- **Camunda Security Library** (`camunda/camunda-security-library`): compiled in, version pinned by
  `version.camunda-security-library` in `parent/pom.xml`; it makes authentication and authorization
  decisions through extension points OC implements ([Identity architecture](identity/docs/architecture.md) § 5).
  TODO(confirm): whether it belongs to another system or is a library of this one.
- **bpmn-io/form-js**: external library. Tasklist renders forms with `@bpmn-io/form-js-viewer`
  (pinned in `webapp/client/apps/orchestration-cluster-webapp/package.json`). It is not a member.
- **Optimize** (`optimize/`, in this repo): consumes the data the Elasticsearch/OpenSearch
  exporters write and keeps its own schema; no RDBMS support. Released separately for 8.7.
- **Management Identity** (`camunda-cloud/identity`): the runtime must not depend on it; it serves
  Web Modeler, Console and Optimize in Self-Managed.
- **Modeling** (Web Modeler, Desktop Modeler): deploy models and read cluster data through the REST
  API. TODO(confirm) whether they form a declared system to list in `depends_on_systems` the other
  way round.
- **Infrastructure the system runs on**: Elasticsearch, OpenSearch or a relational database for
  secondary storage; an OIDC identity provider; backup stores (S3, GCS, Azure, filesystem).

## 5. Conventions

- REST API first: new core features are exposed in the REST API before anything else; endpoints
  follow [REST API endpoint guidelines](docs/rest-api-endpoint-guidelines.md), each operation
  carries `x-added-in-version`, and required permissions are declared with `x-required-permissions`
  ([security ADR 001](docs/adr/security/001-endpoint-required-permission-mapping.md)).
- New export features target the Camunda exporter; the dedicated ES/OS exporters are kept, not
  extended.
- Secondary-storage schemas are additive only: ES/OS templates are `"dynamic": "strict"`; RDBMS
  changes are Liquibase changesets.
- Generated code (gRPC stubs, SBE codecs) is never edited by hand.
- Decisions: cross-cutting ADRs in [`docs/adr/`](docs/adr/README.md), system-scoped ones in
  [`docs/adr/orchestration-cluster/`](docs/adr/orchestration-cluster/README.md) (physical tenants,
  images, configuration), module ADRs in `<module>/docs/adr/`.
- Physical tenants: requests, configuration, exporters and authorization reads are scoped per
  physical tenant (orchestration-cluster ADRs 0003–0009); services are reached through
  `ServiceRegistry` keyed by physical tenant.

## 6. Shared qualities and delivery

- **Release**: one monorepo release for all members ([monorepo release](docs/monorepo-docs/release/release-monorepo.md)):
  minors about every six months from `stable/8.x`, monthly alphas from `main`, monthly patches.
  Artifacts go to Maven Central, Docker Hub and GitHub releases.
- **Compatibility**: the REST API is forward-compatible between minor versions; breaking changes
  must be accepted deliberately ([guidelines § 2.7](docs/rest-api-endpoint-guidelines.md)). Client
  and Spring Boot starter compatibility runs in CI (`camunda-client-compatibility-test.yml`,
  `camunda-spring-boot-starter-compatibility-test.yml`).
- **Backports** to `stable/*` go through the backport action ([CONTRIBUTING](CONTRIBUTING.md#backporting-changes)).
- **Security and tenancy**: every entry point authenticates via `authentication`; authorization is
  resource-based and checked in the engine (writes) and `service` (reads); multi-tenancy is enforced
  in the cluster, not in Management Identity.
- **Storage support**: Elasticsearch, OpenSearch and RDBMS for secondary storage; archiving is
  ES/OS-only, RDBMS uses TTL cleanup.
- **Build**: Java 21 and Maven; Docker images ship JRE 25 while JRE 21 stays supported
  ([ADR 0002](docs/adr/orchestration-cluster/0002-jdk-25-base-images-with-jdk-21-runtime-support.md));
  frontends in React/TypeScript with Carbon. TODO(confirm) the supported-versions matrix for
  databases and identity providers.
