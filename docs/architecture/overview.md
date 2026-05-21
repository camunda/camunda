# Architecture Overview

## What this repo is

Camunda 8 is a process automation platform that executes BPMN workflows and evaluates DMN decisions
at scale, for both self-managed and SaaS deployments. The monorepo contains the full platform: Zeebe
(write-side execution engine), Operate (process monitoring), Tasklist (human task management),
Identity (authentication and authorization), and Optimize (process analytics), plus shared
libraries, gateways, and supporting infrastructure. Java 21 backend built with Maven; React/Carbon
frontends. `dist/` wires the components into deployable Spring Boot application variants (e.g.
`StandaloneBroker`, `StandaloneCamunda`); components can also be deployed independently.

## Runtime topology

### Write path

Clients submit commands (deploy a process, start an instance, complete a job) through one of two
entry points:

- **gRPC gateway** (`zeebe/gateway`, `zeebe/gateway-grpc`): exposes the Zeebe gRPC API defined in
  `zeebe/gateway-protocol`. Used by the Java client (`clients/java`), the Go client (`clients/go`),
  and the Spring Boot starter (`clients/camunda-spring-boot-starter`).
- **REST gateway** (`zeebe/gateway-rest`): exposes the Camunda v2 REST API. Commands flow through
  `service/`, which translates them into calls to the internal Zeebe gateway.
  `gateways/gateway-mapping-http` provides HTTP request/response mapping utilities used by this
  layer.
- **MCP gateway** (`gateways/gateway-mcp`): exposes the Camunda API as a Model Context Protocol
  server for AI agent integrations.

Both entry points authenticate via `authentication/` (OIDC token processing, Spring Security).
Authorization is enforced by `security/` against rules managed by `identity/`.

The **Zeebe broker** (`zeebe/broker`) receives commands, appends them to a partitioned append-only
log (Raft consensus via `zeebe/atomix`), and executes them against its primary state store (RocksDB
via `zeebe/zb-db`).

### Export path

After processing, the broker emits records to configured exporters:

|           Exporter            |                  Module                  |           Target            |
|-------------------------------|------------------------------------------|-----------------------------|
| Elasticsearch exporter        | `zeebe/exporters/elasticsearch-exporter` | Elasticsearch indices       |
| OpenSearch exporter           | `zeebe/exporters/opensearch-exporter`    | OpenSearch indices          |
| RDBMS exporter                | `zeebe/exporters/rdbms-exporter`         | Relational DB via `db/`     |
| Camunda exporter (all-in-one) | `zeebe/exporters/camunda-exporter`       | All of the above (combined) |

ES/OS index shapes are defined in `webapps-schema/` and applied at startup by `schema-manager/`.

The Camunda Exporter also runs background **archiver jobs** that move completed process data from
active ES/OS indices into dated archive indices (e.g. `operate-list-view-8.3.0_2024-01-01`),
keeping primary indices lean. Archiving is ES/OS-only; RDBMS deployments use TTL-based cleanup
instead. Operate and Tasklist queries span both active and archive indices.

### Read path

Operate and Tasklist are read-side webapps that query secondary storage (ES/OS or RDBMS) through
the `search/` abstraction layer. The REST API also serves read queries: `zeebe/gateway-rest` calls
`service/`, which delegates to `search/`. Optimize reads from the same ES/OS indices through
its own internal analytics pipeline.

## Architectural decisions

Cross-cutting architectural decisions are recorded as ADRs in `docs/adr/`. Before making any
architectural change, check the index there first. If the decision is not covered by an existing
ADR, draft a new one using the `create-architecture-decision` skill before proceeding.

## Architectural boundaries

Contracts that must not be bypassed:

**Zeebe gRPC protocol** (`zeebe/gateway-protocol/src/main/proto/gateway.proto`)\
The interface between external clients and the gRPC gateway. Java stubs are generated at build time
in `zeebe/gateway-protocol-impl/`; never edit them manually. Changing the `.proto` requires
regenerating stubs and updating all consumers in `clients/`.

**Zeebe record/exporter contract** (`zeebe/exporter-api`, `zeebe/protocol`)\
The SBE-encoded record format emitted by the broker and consumed by exporters. Access broker state
only through this contract; never read from RocksDB directly.

**ES/OS index schema** (`webapps-schema/`)\
All templates use `"dynamic": "strict"` — new fields must be explicitly added to the template
definitions before the exporter writes them. Changing a field type is a breaking change that
requires a migration plan. Never query ES/OS indices directly from application code; always go
through `search/`.

**`service/` as the REST-to-engine bridge**\
All REST commands must flow through `service/` before reaching Zeebe. REST controllers in
`zeebe/gateway-rest` must not call the Zeebe gRPC gateway directly.

**RDBMS schema migrations** (`db/rdbms-schema`)\
Schema changes are versioned Liquibase changesets. Never alter tables outside a migration.

## Cross-module blast radius

Changes that propagate farthest:

- **`.proto` change in `zeebe/gateway-protocol`**: regenerates stubs in `gateway-protocol-impl`,
  breaks `clients/java`, `clients/go`, and `clients/camunda-spring-boot-starter`, and affects every
  call in `service/` and
  `gateways/` that maps to the changed RPC.
- **Field type change or removal in `webapps-schema/`**: breaks exporter indexing (strict mapping
  rejects documents with unknown or mistyped fields) and all queries in `operate/`, `tasklist/`, and
  `search/` that reference that field. Requires a versioned template migration.
- **`service/` interface change**: simultaneous breakage in `zeebe/gateway-rest`, `operate/`, and
  `tasklist/`, which all depend on these interfaces.
- **`security/` permission model change**: new or renamed permissions must be added to `security/`
  first; enforcement in `service/`, `gateways/`, and `identity/` must be updated before the
  permission is used.
- **`authentication/` claim mapping change**: affects all webapps and gateways that extract tenant
  ID, user ID, or roles from OIDC tokens.

## Path rules

- `zeebe/gateway-protocol-impl/target/generated-sources/` — gRPC Java stubs generated from
  `gateway.proto`; never edit. Modify the `.proto` source in
  `zeebe/gateway-protocol/src/main/proto/` instead.
- `zeebe/protocol/src/main/resources/` (`protocol.xml`, `common-types.xml`,
  `cluster-management-protocol.xml`) — SBE protocol definitions; generated Java lives in `target/`.
- `target/` everywhere — Maven build output; never edit or commit.
- `optimize/` — independent internal build; excluded by default with `-Dquickly`. Build explicitly
  with `-pl optimize` when needed.
- `webapps-schema/src/main/resources/schema/` — canonical ES/OS template JSON; changes here affect
  exporters, `schema-manager/`, `operate/`, `tasklist/`, and `search/` simultaneously.
- `bom-deprecated/` — legacy BOM kept for backwards compatibility; do not add new entries.
- `dist/` — distribution assembly only; no application logic lives here.

## Further detail

- Data layer guide (ES/OS and RDBMS best practices): `docs/data-layer/working-with-secondary-storage.md`
- Archiving (active vs archive indices, archiver jobs): `docs/data-layer/archiving.md`
- Cross-cutting ADRs: `docs/adr/`
- Module-specific ADRs: `<module>/docs/adr/`
- Zeebe internals: `docs/zeebe/`
- Module-specific architecture: `<module>/docs/architecture.md` (where present)
- Module-specific behavioral rules: `<module>/AGENTS.md`

