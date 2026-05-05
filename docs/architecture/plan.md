# Task: Incremental Architecture & Data-Flow Documentation for Camunda 8

## Goal

Produce two living artifacts that make the Camunda 8 platform understandable to
internal engineers and external users:

1. **Architecture diagram** – component-level view of every module, its
   responsibilities, and the runtime relationships between components.
2. **Data-flow diagram** – how data enters, moves through, and leaves the system
   (process definitions, process instances, jobs, variables, decisions, user tasks,
   events, exported data).

Both artifacts must be stored as text (Mermaid) inside the repo
under `docs/architecture/` so they are versionable and renderable in GitHub.

---

## Context

Repository: `camunda/camunda` (monorepo)

Key top-level modules and what they are:

| Module            | Role                                                                              |
| ----------------- | --------------------------------------------------------------------------------- |
| `zeebe/`          | Core BPMN/DMN engine: broker, engine, gateway (gRPC + REST), exporters, protocol  |
| `operate/`        | Monitoring webapp for process instances                                           |
| `tasklist/`       | User-task management webapp                                                       |
| `identity/`       | Authentication & authorization (OIDC/OAuth2)                                      |
| `optimize/`       | Analytics & reporting                                                             |
| `db/`             | RDBMS secondary-storage layer                                                     |
| `search/`         | Elasticsearch / OpenSearch abstraction                                            |
| `service/`        | Shared application services (process, job, variable, incident…)                   |
| `clients/`        | Java client, Camunda Spring Boot Starter                                          |
| `gateways/`       | HTTP REST gateway (`gateway-rest`), MCP gateway                                   |
| `security/`       | Security core, validation                                                         |
| `authentication/` | Auth integration                                                                  |
| `configuration/`  | Shared configuration beans                                                        |
| `webapps-schema/` | Elasticsearch/OS index schemas shared by Operate, Tasklist, Optimize              |
| `webapps-common/` | Shared backend utilities for webapps                                              |
| `document/`       | Document store abstraction                                                        |
| `testing/`        | Process-testing libraries                                                         |
| `qa/`             | Cross-component acceptance tests                                                  |
| `c8run/`          | Single-jar local-run packaging                                                    |

---

## Approach: Incremental Snapshots

Work module by module. After finishing each module, write a short **module
summary** and update the diagrams. Do NOT try to read the entire repo at once.

### For each module, answer

1. **What is it?** – one-sentence purpose.
2. **What does it expose?** – public APIs (gRPC, REST endpoints, exported
   interfaces, client API).
3. **What does it depend on?** – other modules in this repo it imports/calls.
4. **What external systems does it talk to?** – databases, message queues,
   Elasticsearch/OpenSearch, Keycloak, cloud services, etc.
5. **What data does it produce / consume?** – key domain objects that flow
   through it (ProcessDefinition, ProcessInstance, Job, Variable, Incident,
   UserTask, Decision, Signal, Message, Export record…).

### Module processing order (dependency-safe)

1. `zeebe/protocol`
2. `zeebe/exporter-api`
3. `zeebe/bpmn-model`
4. `zeebe/engine`
5. `zeebe/broker`
6. `zeebe/gateway` (gRPC)
7. `zeebe/exporters/camunda-exporter`
8. `zeebe/exporters/rdbms-exporter`
9. `search/`
10. `webapps-schema/`
11. `db/`
12. `service/`
13. `security/` + `authentication/`
14. `identity/`
15. `gateways/gateway-rest`
16. `gateways/gateway-mcp`
17. `operate/` (backend then `client/`)
18. `tasklist/` (backend then `client/`)
19. `optimize/`
20. `clients/java`
21. `clients/camunda-spring-boot-starter`
22. `c8run/`
23. `qa/` (acceptance-test topology only)

---

## Artifacts to produce

### 1. Module summaries

File: `docs/architecture/modules.md`

For every processed module, append a section:

```markdown
## <module-name>

**Purpose**: …
**Exposes**: …
**Depends on (internal)**: …
**Depends on (external)**: …
**Key data objects**: …
```

### 2. Architecture diagram

File: `docs/architecture/architecture.mmd` (Mermaid `graph TD`)

Nodes = components (Broker, Engine, gRPC Gateway, REST Gateway, MCP Gateway,
Camunda Exporter, RDBMS Exporter, Elasticsearch/OS cluster, RDBMS, Operate,
Tasklist, Identity, Optimize, Connectors [external], Web Modeler [external],
Java Client, Spring Boot Starter, Keycloak).

Edges = runtime communication relationships with a short label (gRPC, REST,
HTTP, JDBC, ES HTTP, export record, OAuth2 token, …).

Update this file after every module is processed.

### 3. Data-flow diagram

File: `docs/architecture/data-flow.mmd` (Mermaid `sequenceDiagram` or
`flowchart LR`)

Show the journey of:

- A **process definition** (deploy → engine → exporter → ES/RDBMS → Operate/Tasklist read)
- A **process instance** (create → engine execution → job activation → completion → export)
- A **user task** (creation → Tasklist → complete → engine)
- An **export record** (engine → exporter → ES/OS or RDBMS → webapp queries)

Update this file incrementally.

---

## Working instructions

- Use `view`, `grep`, and `glob` tools to read source code; do NOT run builds.
- For each module, start by reading its `README.md` (if present), then key
  `pom.xml` dependencies, then the main entry-point Java packages.
- Important files per module type:
  - Engine: `zeebe/engine/src/main/java/io/camunda/zeebe/engine/`
  - Broker: `zeebe/broker/src/main/java/io/camunda/zeebe/broker/`
  - Exporters: `zeebe/exporters/*/src/main/java/`
  - Gateways: `gateways/*/src/main/java/`
  - Services: `service/src/main/java/io/camunda/service/`
  - REST controllers: `zeebe/gateway-rest/src/main/java/`
- After each module, call `report_progress` to commit the module summary and
  updated diagrams, then move to the next module.
- Keep diagram nodes stable (don't rename existing nodes when adding new ones).
- Target audience for diagrams: senior engineers unfamiliar with the codebase.
  Omit test infrastructure and CI tooling from the diagrams.

---

## Definition of done

- [ ] `docs/architecture/modules.md` has a section for every module listed above.
- [ ] `docs/architecture/architecture.mmd` renders correctly as a Mermaid diagram
      and shows all major runtime components and their communication links.
- [ ] `docs/architecture/data-flow.mmd` renders correctly and traces the four
      key data journeys listed above end-to-end.
- [ ] Both `.mmd` files are committed and visible at `docs/architecture/` in the repo.
