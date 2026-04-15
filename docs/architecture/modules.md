# Camunda 8 Module Summaries

This file documents every major module in the Camunda 8 monorepo, covering its purpose,
public API surface, internal and external dependencies, and the key data objects that flow
through it. Modules are ordered dependency-first (most foundational first).

---

## zeebe/protocol

**Purpose**: Defines the binary protocol shared across all Zeebe components — every command,
event, and record type produced or consumed by the engine is declared here as Java interfaces
and SBE-generated value objects.

**Exposes**:
- `Record<T extends RecordValue>` — generic envelope with position, key, timestamp, partition
  ID, intent, and record type
- 80+ `RecordValue` sub-interfaces covering every domain object (Process, ProcessInstance, Job,
  Variable, Incident, UserTask, Decision, Message, Signal, Timer, Deployment, etc.)
- `Intent` enums for each domain type (one per `ValueType`)
- `ValueType` enum (68+ entries) and `Protocol` constants (partition encoding, key-space)
- `BpmnElementType`, `BpmnEventType`, `ErrorType`, `JobKind`, and other domain enums

**Depends on (internal)**: `camunda-security-protocol`

**Depends on (external)**: `agrona` (binary encoding), `org.immutables` (code generation)

**Key data objects**: All domain record values — ProcessDefinition, ProcessInstance, Job,
Variable, Incident, UserTask, Decision, DecisionRequirements, Form, Message, Signal, Timer,
Deployment, BatchOperation, Authorization, User, Role, Group, Tenant, Checkpoint

---

## zeebe/exporter-api

**Purpose**: Defines the `Exporter` interface contract that all exporter implementations must
satisfy, enabling pluggable sinks for the Zeebe event log.

**Exposes**:
- `Exporter` interface with lifecycle hooks: `configure()`, `open()`, `export(Record)`,
  `close()`, `purge()`
- `Context` (partition ID, clock, logger, `RecordFilter` for metadata-first filtering)
- `Controller` (tracks export position, schedules cancellable tasks, reads/writes metadata)
- `Configuration` (exporter ID, argument map)

**Depends on (internal)**: `zeebe-protocol`

**Depends on (external)**: `slf4j-api`, `micrometer-core`

**Key data objects**: `Record<T>` (consumed), exporter position checkpoints (produced)

---

## zeebe/bpmn-model

**Purpose**: Java library for creating, parsing, validating, and transforming BPMN 2.0 process
models with Zeebe-specific extension elements.

**Exposes**:
- `Bpmn` — static entry point (`readModelFromFile`, `createProcess`, `convertToString`,
  `validateModel`)
- `BpmnModelInstance` — in-memory DOM of a BPMN document
- 93 fluent builder classes (ProcessBuilder, ServiceTaskBuilder, etc.) including Zeebe
  extension builders (`ZeebeTaskDefinition`, `ZeebeIoMapping`, `ZeebeFormDefinition`, etc.)
- ~130 BPMN element interfaces (`Process`, `ServiceTask`, `UserTask`, `Gateway`, `Event`, …)

**Depends on (internal)**: none (self-contained)

**Depends on (external)**: `camunda-xml-model` (XML parsing), `slf4j-api`

**Key data objects**: BPMN XML files (in), `BpmnModelInstance` / serialized BPMN XML (out)

---

## zeebe/engine

**Purpose**: Stream-based, single-threaded state machine that executes BPMN processes and DMN
decisions by processing commands from the log, emitting events, and persisting state to
RocksDB.

**Exposes**:
- `Engine` (implements `RecordProcessor`) — orchestrates processing and replay modes
- `EngineProcessors` — factory wiring all record processors and behaviors
- `ProcessingState` — read-only view over all engine state (jobs, instances, subscriptions, …)
- `TypedRecordProcessor<T>` — interface for per-record-type handlers
- `Writers` aggregate — processors write commands, events, rejections, and responses through it
- Key behaviors: `BpmnBehaviors`, `DecisionBehavior`, `AuthorizationCheckBehavior`,
  `CommandDistributionBehavior`

**Depends on (internal)**: `zeebe-protocol`, `zeebe-protocol-impl`, `zeebe-stream-platform`,
`zeebe-bpmn-model`, `zeebe-dmn`, `zeebe-db`, `zeebe-expression-language`,
`zeebe-feel-tagged-parameters`, `zeebe-msgpack-core`, `zeebe-auth`, `camunda-security-*`,
`camunda-search-client`, `camunda-search-domain`

**Depends on (external)**: RocksDB (via `zeebe-db`), FEEL / DMN Scala evaluation engines

**Key data objects**:
- **Consumed**: Commands (deploy, create instance, complete job, publish message, …)
- **Produced**: Events (ProcessInstanceCreated, JobActivated, IncidentCreated, …), export
  records, follow-up commands, command rejections

---

## zeebe/broker

**Purpose**: Distributed process-execution server that hosts one or more engine partitions,
manages Raft-based clustering, and exposes command APIs to gateways.

**Exposes**:
- `Broker` — main lifecycle class (startup, shutdown, health)
- `PartitionManager` — creates and routes work to partitions
- `CommandApiService` — gRPC service for workflow commands from gateways
- `TopologyManager` — tracks partition leaders; broadcasts topology to gateways
- `JobStreamService` — push-based job streaming to workers
- `EmbeddedGatewayService` — optional co-located gRPC gateway for single-node setups
- `SnapshotApiService`, `BackupService` — durability and disaster recovery

**Depends on (internal)**: `zeebe-engine`, `zeebe-gateway(-grpc)`, `zeebe-exporter-api`,
`zeebe-stream-platform`, `zeebe-db`, `zeebe-logstreams`, `zeebe-journal`, `zeebe-atomix-*`,
`zeebe-transport`, `zeebe-broker-client`, `zeebe-snapshots`, `zeebe-backup-*`,
`camunda-search-client`, `camunda-security-*`, `identity-sdk`

**Depends on (external)**: Atomix / Raft (clustering), RocksDB (state), S3/GCS/Azure
(backup stores)

**Key data objects**:
- **Consumed**: gRPC commands from gateways, Raft log entries, snapshots from leaders
- **Produced**: Workflow records/events, topology updates, snapshots, exported records,
  job streams, health status

---

## zeebe/gateway-grpc

**Purpose**: gRPC server that exposes 23 RPC methods to external clients (Java SDK, CLI,
Connectors, Web Modeler), translating them into broker commands.

**Exposes**:
- `GatewayGrpcService` — gRPC service stub routing to `EndpointManager`
- 23 RPC methods: `ActivateJobs`, `CreateProcessInstance`, `DeployResource`,
  `CompleteJob`, `Topology`, `BroadcastSignal`, `MigrateProcessInstance`, and more
- `LongPollingActivateJobsHandler` / `RoundRobinActivateJobsHandler`
- `StreamJobsHandler` — bidirectional streaming for job push
- `AuthenticationInterceptor` — JWT / OAuth2 validation
- `ExportingControlService` — admin pause/resume of exporters

**Depends on (internal)**: `zeebe-gateway`, `zeebe-gateway-protocol-impl`,
`zeebe-broker-client`, `zeebe-scheduler`, `zeebe-transport`, `zeebe-atomix-*`,
`camunda-service`, `camunda-security-core`

**Depends on (external)**: gRPC / Netty, Protocol Buffers, Spring Boot/Security, Micrometer

**Key data objects**:
- **Consumed**: gRPC requests (job activation, process creation, deployment, …)
- **Produced**: gRPC responses (activated jobs, instance keys, topology, …); broker commands

---

## zeebe/gateway-rest (REST Gateway)

**Purpose**: HTTP/JSON REST server (base path `/v2`, 60+ endpoints) providing a standard REST
API over the Zeebe broker for process operations and administrative queries.

**Exposes**:
- 38 `@RestController` classes organized across resource categories: process instances,
  jobs, deployments, decisions, user tasks, variables, messages, signals, incidents,
  users, groups, roles, authorizations, tenants, audit logs, documents, batch operations
- OpenAPI 3 spec generated from `zeebe/gateway-protocol/src/main/proto/v2/rest-api.yaml`
- `@RequiresSecondaryStorage` — marks endpoints needing ES/RDBMS read access
- RFC 7807 `ProblemDetail` error responses

**Depends on (internal)**: `zeebe-gateway-protocol`, `camunda-gateway-model`,
`camunda-gateway-mapping-http`, `zeebe-gateway`, `camunda-service`,
`camunda-security-*`, `camunda-authentication`, `zeebe-broker-client`,
`camunda-search-domain`, `document-api`

**Depends on (external)**: Spring Web MVC, Jackson, Hibernate Validator, springdoc-openapi

**Key data objects**:
- **Consumed**: JSON request bodies (ProcessInstanceCreationInstruction,
  JobActivationRequest, MessagePublicationRequest, UserTaskAssignmentRequest, …)
- **Produced**: JSON responses (paginated search results, instance keys, activated jobs, …)

---

## zeebe/exporters/camunda-exporter

**Purpose**: Exports Zeebe event-stream records to Elasticsearch or OpenSearch, providing
the live index backing Operate, Tasklist, and Optimize.

**Exposes**:
- `CamundaExporter` implements `Exporter`
- Handles 33 `ValueType`s including PROCESS, PROCESS_INSTANCE, JOB, INCIDENT, USER_TASK,
  DECISION, VARIABLE, AUTHORIZATION, BATCH_OPERATION_*, and more
- 76+ handler classes for specialized record types
- Configurable batching, flushing, index management, and background tasks

**Depends on (internal)**: `zeebe-exporter-api`, `zeebe-exporter-common`, `zeebe-protocol`,
`camunda-search-domain`, `webapps-schema`, `webapps-common`, `camunda-search-client-connect`,
`camunda-schema-manager`

**Depends on (external)**: `elasticsearch-java`, `opensearch-java`, Caffeine, Micrometer

**Key data objects**:
- **Consumed**: Zeebe `Record<T>` stream (33 value types)
- **Produced**: ES/OpenSearch documents in operate-*, tasklist-*, and shared indices

---

## zeebe/exporters/rdbms-exporter

**Purpose**: Exports Zeebe event-stream records to a relational database, providing the SQL
backing store for Operate and administrative queries when Elasticsearch is not used.

**Exposes**:
- `RdbmsExporterWrapper` implements `Exporter` (delegates to `RdbmsExporter`)
- Handles 20+ `ValueType`s via 42+ handler classes
- Entity caches (Caffeine) for Process, DecisionRequirements, BatchOperation
- History deletion and cleanup services

**Depends on (internal)**: `zeebe-exporter-api`, `zeebe-exporter-common`,
`camunda-db-rdbms`, `camunda-db-rdbms-schema`, `camunda-search-domain`,
`zeebe-protocol`, `zeebe-bpmn-model`

**Depends on (external)**: JDBC drivers (PostgreSQL, MySQL, Oracle, MSSQL, H2),
Caffeine, Micrometer

**Key data objects**:
- **Consumed**: Zeebe `Record<T>` stream (20+ value types)
- **Produced**: Rows in 41 RDBMS tables (PROCESS_INSTANCE, JOB, USER_TASK, VARIABLE,
  INCIDENT, DECISION_INSTANCE, AUDIT_LOG, BATCH_OPERATION, …)

---

## search/

**Purpose**: Multi-database abstraction layer that provides typed query interfaces and client
implementations for reading Camunda operational data from Elasticsearch or OpenSearch.

**Exposes**:
- `SearchClientsProxy` — aggregates 24+ individual search client interfaces
- Per-entity clients: `ProcessInstanceSearchClient`, `JobSearchClient`,
  `UserTaskSearchClient`, `IncidentSearchClient`, `FormSearchClient`, etc.
- `SearchQueryResult<T>` — paginated, cursor-based result type
- Domain entities: `ProcessInstanceEntity`, `JobEntity`, `UserTaskEntity`,
  `IncidentEntity`, `VariableEntity`, `FlowNodeInstanceEntity`, …
- 40+ typed query / filter classes; statistics / aggregation query types

**Sub-modules**: `search-domain`, `search-client`, `search-client-elasticsearch`,
`search-client-opensearch`, `search-client-connect`, `search-client-query-transformer`,
`search-client-reader`, `search-client-plugin`

**Depends on (internal)**: `webapps-schema`, `camunda-security-*`, `zeebe-protocol`,
`zeebe-util`, `camunda-spring-utils`

**Depends on (external)**: `elasticsearch-java`, `opensearch-java`, Apache HttpComponents,
AWS SDK (for OpenSearch on AWS with SigV4)

**Key data objects**: All operational entities read from ES/OS (process instances, jobs,
incidents, user tasks, variables, decisions, forms, audit logs, statistics)

---

## webapps-schema/

**Purpose**: Shared Elasticsearch/OpenSearch index mappings (36 index/template descriptors,
81 JSON mapping files) and entity classes used by Operate, Tasklist, and Identity.

**Exposes**:
- 16 static index descriptors (`UserIndex`, `ProcessIndex`, `FormIndex`, …)
- 20+ template descriptors (`TaskTemplate`, `FlowNodeInstanceTemplate`,
  `IncidentTemplate`, `ListViewTemplate`, …)
- 45+ entity classes matching those indices (`UserEntity`, `TaskEntity`,
  `FlowNodeInstanceEntity`, `IncidentEntity`, …)
- All indices use `dynamic: "strict"` mappings; `@BeforeVersion880` tracks breaking changes

**Depends on (internal)**: `zeebe-protocol`, `camunda-security-protocol`

**Depends on (external)**: Jackson annotations, SLF4J

**Key data objects**: Index/template descriptors consumed by the Camunda Exporter and
webapps; entity POJOs used by the search layer

---

## db/

**Purpose**: RDBMS secondary-storage layer that persists Camunda operational and audit data
exported by the RDBMS Exporter, using MyBatis for SQL access and Liquibase for schema
migration.

**Exposes**:
- `RdbmsService` — central facade providing access to all readers and writers
- Readers: `ProcessInstanceDbReader`, `VariableDbReader`, `IncidentDbReader`,
  `JobDbReader`, `UserTaskDbReader`, `DecisionInstanceDbReader`, and ~10 more
- Writers: `ProcessInstanceWriter`, `VariableWriter`, `IncidentWriter`, `JobWriter`,
  `UserTaskWriter`, `FlowNodeInstanceWriter`, and ~10 more
- `RdbmsSchemaManager` / `LiquibaseSchemaManager` for schema versioning

**Sub-modules**: `db/rdbms` (MyBatis readers/writers), `db/rdbms-schema` (Liquibase migrations)

**Depends on (internal)**: `camunda-search-client`, `camunda-search-domain`,
`camunda-search-client-reader`, `camunda-security-*`, `zeebe-util`

**Depends on (external)**: MyBatis, Liquibase, Jackson, Micrometer, Caffeine;
drivers for PostgreSQL, MySQL/MariaDB, H2, Oracle, MSSQL

**Key data objects**: 41 tables — PROCESS_INSTANCE, VARIABLE, FLOW_NODE_INSTANCE, JOB,
USER_TASK, INCIDENT, DECISION_DEFINITION, DECISION_INSTANCE, AUDIT_LOG,
AUTHORIZATION, BATCH_OPERATION, EXPORTER_POSITION, WEB_SESSION, and more

---

## service/

**Purpose**: Business-logic service layer that acts as a facade between REST/gRPC controllers
and the broker / search subsystems, enforcing authorization and providing 34+ domain
service beans.

**Exposes**:
- Spring-managed service classes: `ProcessInstanceServices`, `JobServices`,
  `UserTaskServices`, `IncidentServices`, `VariableServices`, `DecisionInstanceServices`,
  `FormServices`, `ResourceServices`, `AuditLogServices`, `BatchOperationServices`, etc.
- Each service handles CRUD + search for its domain, delegating mutations to
  `BrokerClient` (gRPC) and reads to `SearchClientsProxy` (ES/RDBMS)

**Depends on (internal)**: `camunda-search-client`, `camunda-search-domain`,
`zeebe-broker-client`, `zeebe-gateway`, `zeebe-protocol(-impl)`,
`camunda-security-*`, `document-api`, `document-store`

**Depends on (external)**: Caffeine, Jackson, Agrona, Spring Security

**Key data objects**: All domain entities flowing through search + broker (JobRecord,
ProcessInstanceRecord, UserTaskRecord, VariableEntity, IncidentEntity, …)

---

## security/

**Purpose**: Low-level security primitives, authorization rule definitions, and security
configuration shared across all Camunda components.

**Sub-modules**: `security-protocol` (enums/DTOs), `security-core` (auth beans and OIDC
integration), `security-services` (authorization checker), `security-validation`

**Exposes**:
- `CamundaAuthentication`, `SecurityContext` — identity + authorization context
- `CamundaAuthenticationProvider`, `CamundaAuthenticationConverter`
- `OidcGroupsLoader`, `OidcPrincipalLoader` — OIDC claim extraction
- `AuthorizationResourceType`, `PermissionType`, `EntityType` enums
- `AuthorizationsConfiguration`, `MultiTenancyConfiguration`, `CsrfConfiguration`
- HTTP security headers (CSP, cache-control)

**Depends on (internal)**: `zeebe-auth`, `zeebe-util`, `zeebe-protocol`

**Depends on (external)**: Spring Security, Nimbus JOSE JWT, JSONPath

**Key data objects**: `CamundaAuthentication` (user identity + groups + tenant),
`SecurityContext` (auth + authorization condition), JWT token claims

---

## authentication/

**Purpose**: Spring Boot security filter chain for OAuth2/OIDC token exchange, JWT
validation, session management, and login/logout flows for Camunda web applications.

**Exposes**:
- `WebSecurityConfig` — main Spring Security configuration
- `OidcTokenAuthenticationConverter`, `CamundaAuthenticationDelegatingConverter`
- `OAuth2RefreshTokenFilter`, `AdminUserCheckFilter`
- `AuthenticationController`, `SaaSTokenController`
- `ClientRegistrationFactory`, `AudienceValidator`, `ClusterValidator`

**Depends on (internal)**: `camunda-service`, `camunda-search-client`,
`camunda-security-core`, `camunda-security-protocol`, `camunda-gateway-model`,
`camunda-spring-utils`

**Depends on (external)**: Spring Security OAuth2 (client + resource server + JOSE),
Nimbus JOSE JWT, Resilience4j

**Key data objects**:
- **Consumed**: HTTP requests with Bearer tokens, OAuth2 authorization codes, refresh tokens
- **Produced**: `CamundaUserDTO`, `CamundaAuthentication` (mapped from JWT claims),
  HTTP sessions

---

## identity/

**Purpose**: Administration component providing a management UI and backend for users, roles,
permissions, and OAuth clients in self-managed Camunda deployments.

**Sub-modules**: `client` (TypeScript/React SPA), `backend` (Spring Boot app), `common`

**Exposes**:
- Web UI (`/admin`) for managing users, roles, permissions, OAuth clients, mapping rules
- REST management API backed by the consolidated auth system
- Spring profiles: `consolidated-auth`, `identity`, `rdbmsH2`, `elasticsearch`

**Depends on (internal)**: Spring Web/Beans; actual user/auth data stored in ES or RDBMS

**Depends on (external)**: Keycloak (OIDC), Elasticsearch, H2/PostgreSQL

**Key data objects**: User, Role, Permission, TenantMembership, OAuthClientRegistration

---

## gateways/gateway-rest (mapping layer)

> **Note**: The HTTP REST gateway *server* lives in `zeebe/gateway-rest`. The `gateways/`
> directory hosts supporting sub-modules: `gateway-model` (OpenAPI-generated DTOs),
> `gateway-mapping-http` (request/response mappers), and `gateway-mcp` (MCP server).

See `zeebe/gateway-rest` above for the full REST gateway summary.

---

## gateways/gateway-mcp

**Purpose**: MCP (Model Context Protocol) server that exposes 16 Camunda operations as
AI-callable tools, allowing AI agents to query and control Camunda 8 workflows.

**Exposes**:
- Two HTTP MCP server endpoints: `/mcp/cluster` (static tools) and `/mcp/processes`
  (dynamic tools per deployed process definition)
- 16 tools across 6 domains: cluster status/topology, process definition search/get/XML,
  process instance search/get/create, user task search/get/assign/variables, incident
  search/get/resolve, variable search/get
- `@CamundaMcpTool` annotation for marking tool beans
- `ToolRepository` — dynamically creates per-process-definition tools at runtime

**Depends on (internal)**: `camunda-gateway-model`, `camunda-gateway-mapping-http`,
`camunda-search-domain`, `camunda-security-core`, `camunda-service`, `zeebe-protocol-impl`

**Depends on (external)**: Spring AI MCP (`spring-ai-starter-mcp-server-webmvc`),
`mcp-core`, Jackson 3, `jsonschema-generator`

**Key data objects**:
- **Consumed**: MCP tool call JSON payloads (process keys, search filters, variable JSON)
- **Produced**: MCP `CallToolResult` with cluster info, process/task/incident details, BPMN XML

---

## operate/

**Purpose**: Process monitoring and operations webapp providing a UI and REST API (v1) for
viewing, debugging, and operating running process instances.

**Exposes**:
- REST v1 API: `/v1/process-instances`, `/v1/incidents`, `/v1/variables`,
  `/v1/flow-node-instances`, `/v1/decision-instances`, `/v1/search`, and more
- Web UI (React 18, TypeScript, Carbon React, bpmn-js, MobX)
- Operation executors: cancel, modify, delete, retry, migrate process instances

**Sub-modules**: `webapp`, `common`, `schema`, `client` (frontend), `config`,
`data-generator`, `archiver`, `importer`

**Depends on (internal)**: `webapps-schema`, `webapps-common`, `camunda-client-java`,
`camunda-schema-manager`, `camunda-authentication`, `camunda-security-*`,
`camunda-service`, `zeebe-protocol(-impl)`, `zeebe-bpmn-model`

**Depends on (external)**: `elasticsearch-java`, `opensearch-java`, Spring Boot OAuth2,
AWS SigV4 (optional)

**Key data objects**:
- **Reads** from ES/OS: process instances, flow nodes, incidents, variables,
  decision instances, batch operations
- **Writes** via Zeebe gRPC: cancel, modify, migrate, delete, retry operations

---

## tasklist/

**Purpose**: User-task management webapp providing a UI and REST API (v1) for assigning,
completing, and managing human tasks in running processes.

**Exposes**:
- REST v1 API: `/tasks/v1` (search, assign, unassign, complete), `/variables/v1`,
  `/forms/v1`, `/processes/v1`
- Web UI (React 19, TypeScript, Carbon React, bpmn-io/form-js viewer)
- Draft variable support (persisting WIP edits before task completion)

**Sub-modules**: `webapp`, `common`, `els-schema`, `client` (frontend), `data-generator`,
`importer-860`, `importer-870`, `archiver`, `qa`

**Depends on (internal)**: `webapps-schema`, `camunda-search-client(-connect)`,
`camunda-service`, `camunda-authentication`, `camunda-security-*`,
`camunda-gateway-mapping-http`, `camunda-client-java`, `zeebe-protocol(-impl)`,
`camunda-schema-manager`, `camunda-spring-utils`

**Depends on (external)**: `elasticsearch-java`, `opensearch-java`, Spring Boot OAuth2

**Key data objects**:
- **Reads** from ES/OS: TaskEntity, FormEntity, VariableEntity, ProcessEntity
- **Sends** to Zeebe: task assignment/unassignment/completion gRPC commands

---

## optimize/

**Purpose**: Enterprise analytics and reporting webapp providing dashboards, KPI monitoring,
outlier detection, and branch analysis over historical process execution data.

**Exposes**:
- 25+ REST services: ReportRestService, DashboardRestService, AlertRestService,
  AnalysisRestService, DefinitionRestService, IngestionRestService, SharingRestService,
  CollectionRestService, ExportRestService, HealthRestService, etc.
- Web UI (React, TypeScript, Vite) with report builder, dashboard editor, and
  visualization components
- External variable ingestion REST endpoint (push data from non-Camunda systems)

**Depends on (internal)**: `camunda-search-client-connect`, `zeebe-protocol`,
`zeebe-bpmn-model`, `camunda-client-java`, `identity-spring-boot-starter`, `identity-sdk`

**Depends on (external)**: `elasticsearch-java` / `opensearch-java`, Spring Boot,
Camunda BPM engine (BPMN/DMN model parsing), SMTP (alert emails)

**Key data objects**:
- **Reads** from ES/OS: process/task/decision execution history, flow node durations,
  variables, incidents
- **Produces**: ReportDefinitionDto (analytics), DashboardDto, KpiDefinitionDto,
  alerts, CSV/JSON exports

---

## clients/java (camunda-client-java)

**Purpose**: Java client library for communicating with a Camunda 8 cluster via gRPC and
REST, covering the full API surface (job workers, process lifecycle, search, admin).

**Exposes**:
- `CamundaClient` — main interface; factory methods `newClient()`, `newClientBuilder()`,
  `newCloudClientBuilder()`
- Fluent command builders: `DeployResourceCommand`, `CreateProcessInstanceCommand`,
  `ActivateJobsCommand`, `CompleteJobCommand`, `PublishMessageCommand`,
  `EvaluateDecisionCommand`, `AssignUserTaskCommand`, and 40+ more
- Search request builders: `ProcessInstanceSearchRequest`, `JobSearchRequest`,
  `UserTaskSearchRequest`, `IncidentSearchRequest`, and more
- `JobWorkerBuilderStep1` — declarative job worker registration
- Statistics APIs: `ProcessDefinitionInstanceStatisticsRequest`,
  `JobErrorStatisticsRequest`, etc.

**Depends on (internal)**: `zeebe-bpmn-model`, `zeebe-gateway-protocol-impl`

**Depends on (external)**: gRPC / Netty, Protocol Buffers, Jackson, `httpclient5`,
`java-jwt`, Micrometer

**Key data objects**:
- **Sends**: Process creation/modification commands, job completions, messages, decisions
- **Receives**: Activated jobs, topology, instance keys, search results

---

## clients/camunda-spring-boot-starter

**Purpose**: Spring Boot auto-configuration starter that wires `CamundaClient` into a Spring
context and enables declarative job worker registration via annotations.

**Exposes**:
- Auto-configured `CamundaClient` singleton bean
- `@JobWorker` — method-level annotation for inline job handlers
- `@Deployment` — deploys BPMN/DMN resources on application startup
- `@Variable`, `@CustomHeaders`, `@VariablesAsType` — parameter injection for job handlers
- Spring Boot Actuator health endpoint verifying broker connectivity
- Properties namespace: `camunda.client.*`

**Depends on (internal)**: `camunda-client-java`, `zeebe-gateway-protocol-impl`

**Depends on (external)**: Spring Boot (autoconfigure, actuator, context, AOP),
Jackson, `httpclient5`, gRPC, Micrometer

**Key data objects**: Spring application context, `application.yaml` configuration,
job handler method signatures; delegates all domain objects to `camunda-client-java`

---

## c8run/

**Purpose**: Go-based local-development launcher that bundles the Java runtime, all Camunda
components, and H2 as a default secondary storage into a single cross-platform executable
for quick local setup.

**Exposes**:
- `c8run` binary (Linux/macOS) / `c8run.exe` (Windows)
- Pre-configured Spring profiles: broker, consolidated-auth, admin, operate, tasklist
- Bundled components: Zeebe broker + gateway, Operate, Tasklist, Identity, Connectors
- Configurable via `.env` file (LDAP credentials, Java options, secondary storage)

**Depends on (internal)**: packages zeebe/broker, operate, tasklist, identity, connectors

**Depends on (external)**: Go stdlib + `flock`, `godotenv`, `zerolog`, `yaml.v3`;
runtime: H2 (default), PostgreSQL/MySQL/MSSQL/Oracle (configurable),
Elasticsearch/OpenSearch (optional)

**Key data objects**: BPMN/DMN process files (input), H2 database files and process logs
(output)

---

## qa/acceptance-tests

**Purpose**: JUnit 5 acceptance-test suite that validates end-to-end Camunda 8 platform
behaviour across multiple storage backends, authentication providers, and cluster topologies.

**Exposes** (test topology only):
- Containerized test harness using Testcontainers for: Zeebe broker + gateway,
  Elasticsearch, OpenSearch, PostgreSQL, MySQL, MariaDB, MSSQL, H2, Keycloak,
  LocalStack (S3), Toxiproxy (network chaos), Operate, Tasklist, Identity
- 426 Java test files organized by feature area: auth, orchestration, cluster, backup,
  exporter, identity, rdbms, task, tasklist, client, schema, document, auditlog,
  historycleanup, nodb, network, tenancy, spring
- Maven profiles: `default` (ES), `rdbms`, `multi-db-test`, `identity-tests`,
  `compatibility-test`, `history`, `e2e-elasticsearch-test`, `e2e-rdbms-test`

**Depends on (internal)**: zeebe-broker, zeebe-gateway, operate-webapp, tasklist-webapp,
rdbms-exporter, camunda-exporter, camunda-client-java, camunda-security-validation,
camunda-authentication

**Depends on (external)**: JUnit 5, Mockito, AssertJ, Awaitility, Testcontainers, WireMock,
Toxiproxy, Keycloak Testcontainer, all JDBC drivers

**Key data objects**: Full platform data lifecycle under test — process definitions,
instances, tasks, variables, incidents, decisions, auth records
