# Camunda 8 — Module Reference

> Auto-generated architecture reference for the `camunda/camunda` monorepo.
> Target audience: engineers who need to understand the platform's components,
> their responsibilities, and how they interact at runtime.

---

## zeebe/protocol

**Purpose:** Defines the binary protocol and data structures (record types, intents, value types) used for communication between Zeebe brokers, gateways, and clients.

**Exposes:** `Record<T>` interface, `RecordValue` base interface, `ValueType` enum (57 types including JOB, DEPLOYMENT, PROCESS_INSTANCE, INCIDENT, MESSAGE, USER_TASK, AUTHORIZATION, TENANT, etc.), `RecordType` enum (EVENT, COMMAND, COMMAND_REJECTION), `Intent` marker interface with 50+ implementations (e.g. `JobIntent`, `ProcessInstanceIntent`, `DeploymentIntent`), `RejectionType` enum, and 50+ record value interfaces (e.g. `JobRecordValue`, `ProcessInstanceRecordValue`).

**Depends on (internal):** `camunda-security-protocol`

**Depends on (external):** Agrona (binary serialization), Immutables (code generation)

**Key data objects:** `Record<T>` (position, key, intent, valueType, recordType, value, timestamp, partitionId), `ValueType` (57 domain object types), `Intent` enums per value type, `BpmnElementType`, `BpmnEventType`, `PartitionRole`

---

## zeebe/exporter-api

**Purpose:** Provides the minimal interface contract for Zeebe exporters — plugins that stream processed records from the broker to external systems.

**Exposes:** `Exporter` interface (configure, open, close, export, purge), `Context` interface (logger, meter registry, configuration, partition ID, record filtering), `Controller` interface (position tracking, task scheduling, metadata storage), `Configuration` wrapper, `ScheduledTask`, `ExporterException`.

**Depends on (internal):** `zeebe-protocol`

**Depends on (external):** None (pure API)

**Key data objects:** Consumes `Record<T>` from the protocol module; manages export position and metadata as byte arrays.

---

## zeebe/bpmn-model

**Purpose:** Fluent Java API for reading, creating, and manipulating BPMN 2.0 process models, including Zeebe-specific extensions.

**Exposes:** `Bpmn` factory (read/write models), `BpmnModelInstance`, 134+ BPMN element interfaces (Process, ServiceTask, UserTask, ExclusiveGateway, BoundaryEvent, etc.), Zeebe extension types (`ZeebeTaskDefinition`, `ZeebeIoMapping`, `ZeebeCalledDecision`, `ZeebeUserTask`, `ZeebeFormDefinition`, `ZeebeExecutionListener`), and fluent builders (`ProcessBuilder`, `ServiceTaskBuilder`).

**Depends on (internal):** None (standalone library)

**Depends on (external):** `camunda-xml-model` (XML parsing)

**Key data objects:** BPMN 2.0 elements — `Definitions`, `Process`, `FlowElement`, `SequenceFlow`, `Message`, `Signal`, `Error`, `DataObject`, plus Zeebe extensions for task definitions, I/O mappings, forms, and listeners.

---

## zeebe/engine

**Purpose:** State-machine workflow engine that executes BPMN processes and DMN decisions by processing a command/event stream, maintaining state in RocksDB, and producing follow-up commands and events.

**Exposes:** `Engine` (implements `RecordProcessor`), `EngineProcessors` (factory registering all processors), processors per domain: `BpmnStreamProcessor`, `JobCompleteProcessor`, `DeploymentCreateProcessor`, `UserTaskProcessor`, `DecisionEvaluationEvaluateProcessor`, `MessageEventProcessors`, `SignalBroadcastProcessor`, identity processors (`UserProcessors`, `AuthorizationProcessors`), writers (`CommandWriter`, `StateWriter`, `RejectionWriter`, `ResponseWriter`).

**Depends on (internal):** `zeebe-protocol`, `zeebe-protocol-impl`, `zeebe-stream-platform`, `zeebe-db` (RocksDB), `zeebe-bpmn-model`, `zeebe-dmn`, `zeebe-expression-language`, `zeebe-msgpack-core`, `camunda-search-client`, `camunda-security-core`, `camunda-security-validation`

**Depends on (external):** FEEL engine, DMN Scala engine, Agrona, Micrometer, cron-utils

**Key data objects:** All 57 `ValueType` records (commands in, events out). Maintains state for process definitions, process instances, jobs, variables, timers, message subscriptions, incidents, users, roles, authorizations, tenants, batch operations, and user tasks.

---

## zeebe/broker

**Purpose:** Core stateful server that manages workflow execution across partitions with Raft consensus, persists records in a distributed log, runs the engine, and streams data to exporters and job workers.

**Exposes:** `Broker` (main entry point), `CommandApiService` (gRPC command handler), `QueryApiRequestHandler` (read queries), `PartitionManager` / `ZeebePartition` (partition lifecycle), `ExporterDirector` (streams records to exporters), `JobStreamService` (streams activated jobs to workers), `ClusterServices` (Atomix cluster communication).

**Depends on (internal):** `zeebe-workflow-engine`, `zeebe-logstreams`, `zeebe-stream-platform`, `zeebe-scheduler`, `zeebe-atomix-cluster`, `zeebe-db`, `zeebe-snapshots`, `zeebe-journal`, `zeebe-backup` + backup stores (S3, GCS, Azure, filesystem), `zeebe-transport`, `zeebe-gateway`, `zeebe-broker-client`, `zeebe-exporter-api`

**Depends on (external):** Atomix (Raft consensus), Netty (transport), Spring Security (OAuth2/JWT), Micrometer, RocksDB

**Key data objects:** Produces serialized protocol records on the distributed log, state snapshots, exported records (via `ExporterDirector`), activated jobs (via `JobStreamService`). Consumes commands from clients and Raft log entries from peers.

---

## zeebe/gateway (gRPC)

**Purpose:** gRPC server acting as the client-facing entry point that routes requests to Zeebe brokers and provides job activation with long-polling support.

**Exposes:** gRPC `Gateway` service with 23 RPCs: `ActivateJobs`, `StreamActivatedJobs`, `CreateProcessInstance(WithResult)`, `CancelProcessInstance`, `CompleteJob`, `FailJob`, `ThrowError`, `UpdateJobRetries`, `UpdateJobTimeout`, `SetVariables`, `PublishMessage`, `ResolveIncident`, `DeployResource`, `DeleteResource`, `EvaluateDecision`, `BroadcastSignal`, `ModifyProcessInstance`, `MigrateProcessInstance`, `Topology`, and more.

**Depends on (internal):** `zeebe-gateway-protocol-impl`, `zeebe-broker-client`, `zeebe-protocol`, `zeebe-transport`, `zeebe-atomix-cluster`, `zeebe-scheduler`, `camunda-security-core`

**Depends on (external):** gRPC, Netty, Spring Security (OAuth2/JWT)

**Key data objects:** gRPC request/response types (e.g. `ActivateJobsRequest`/`Response`, `CreateProcessInstanceRequest`/`Response`), internally maps to 50+ `BrokerRequest` types routed by partition topology.

---

## zeebe/exporters/camunda-exporter

**Purpose:** Zeebe exporter that pushes process execution events and operational data to Elasticsearch or OpenSearch for search indexing, powering the Operate, Tasklist, and Optimize UIs.

**Exposes:** 83 event handlers covering all record types; writes to ES/OS indices: `process`, `decision`, `decisionrequirements`, `form`, `incident`, `user`, `group`, `role`, `tenant`, `authorization`, `user-task`, `variable`, `sequenceflow`, `message-subscription`, `global-listener`, `mapping-rule`, `cluster-variable`, `audit-log`, `history-deletion`, `batch-operation`, `flow-node`, `list-view`, and more.

**Depends on (internal):** `zeebe-exporter-api`, `zeebe-exporter-common`, `camunda-search-domain`, `camunda-search-client-connect`, `camunda-schema-manager`, `webapps-schema`, `zeebe-bpmn-model`

**Depends on (external):** `elasticsearch-java`, `opensearch-java`, Jackson, Caffeine (caching), Micrometer

**Key data objects:** All `ValueType` records consumed from the broker log stream; produces Elasticsearch/OpenSearch documents for each entity type.

---

## zeebe/exporters/rdbms-exporter

**Purpose:** Zeebe exporter that writes process execution events to relational databases (PostgreSQL, MySQL, MariaDB, Oracle, MS SQL, H2) as an alternative to Elasticsearch.

**Exposes:** 42 RDBMS export handlers writing to database tables managed by `camunda-db-rdbms`.

**Depends on (internal):** `zeebe-exporter-api`, `zeebe-exporter-common`, `camunda-db-rdbms`, `camunda-db-rdbms-schema`, `camunda-search-domain`, `zeebe-bpmn-model`, `zeebe-protocol`

**Depends on (external):** Caffeine (caching), Micrometer

**Key data objects:** `ProcessInstanceDbModel`, `DecisionInstanceDbModel`, `JobDbModel`, `VariableDbModel`, `IncidentDbModel`, `UserTaskDbModel`, `FormDbModel`, `FlowNodeDbModel`, `ProcessDefinitionDbModel`, `DecisionDefinitionDbModel`, `BatchOperation` entities, `MessageSubscription`, `AuditLog`, `ClusterVariable`, `GlobalListener`, `MappingRule`.

---

## search/

**Purpose:** Abstraction layer providing a unified interface for querying Elasticsearch and OpenSearch, decoupling business logic from search engine specifics.

**Exposes:** `SearchClientsProxy` (aggregates all search clients), per-entity search client interfaces: `ProcessInstanceSearchClient`, `JobSearchClient`, `IncidentSearchClient`, `UserTaskSearchClient`, `FormSearchClient`, `VariableSearchClient`, `DecisionDefinitionSearchClient`, `DecisionInstanceSearchClient`, `AuthorizationSearchClient`, `UserSearchClient`, `GroupSearchClient`, `RoleSearchClient`, `TenantSearchClient`, `BatchOperationSearchClient`, `MessageSubscriptionSearchClient`, `GlobalListenerSearchClient`, `ClusterVariableSearchClient`, `AuditLogSearchClient`, `UsageMetricsSearchClient`. Query builders and `SearchQueryResult<T>`.

**Depends on (internal):** `camunda-search-domain`, `camunda-security-core`, `zeebe-protocol`, `zeebe-util`

**Depends on (external):** `elasticsearch-java` (ES impl), `opensearch-java` (OS impl)

**Key data objects:** Entity classes in `search-domain`: `ProcessInstanceEntity`, `JobEntity`, `IncidentEntity`, `UserTaskEntity`, `FormEntity`, `VariableEntity`, `DecisionDefinitionEntity`, `DecisionInstanceEntity`, `FlowNodeInstanceEntity`, `ProcessDefinitionEntity`, `MessageSubscriptionEntity`, `ClusterVariableEntity`, `AuditLogEntity`, and query/filter objects.

---

## webapps-schema/

**Purpose:** Defines Elasticsearch/OpenSearch index mappings and entity structures shared by Operate, Tasklist, and Optimize, ensuring backward-compatible schema evolution.

**Exposes:** Entity definitions for ES/OS indexing: process, flow node, incident, user task, job, form, variable, decision, user management (user, group, role, tenant, authorization, mapping rule), audit log, message subscription, cluster variable, global listener, history deletion, batch operation. Base classes: `ExporterEntity`, `PartitionedEntity`.

**Depends on (internal):** `zeebe-protocol`, `camunda-security-protocol`

**Depends on (external):** Jackson, SLF4J

**Key data objects:** All entity types listed above, organized under `io.camunda.webapps.schema.entities.*`.

---

## db/

**Purpose:** RDBMS secondary-storage layer using MyBatis for ORM and Liquibase for schema management, supporting PostgreSQL, MySQL, MariaDB, Oracle, MS SQL Server, and H2.

**Exposes:** `RdbmsService` (aggregates all DB readers and writers), per-entity readers (`ProcessInstanceDbReader`, `JobDbReader`, `IncidentDbReader`, etc.) and writers, MyBatis mapper XMLs, Liquibase changelogs for schema migrations.

**Depends on (internal):** `camunda-search-client`, `camunda-search-domain`, `camunda-security-protocol`, `camunda-security-core`

**Depends on (external):** MyBatis, Liquibase, Caffeine, Micrometer, H2 (testing)

**Key data objects:** DB model classes mirroring search entities: `ProcessInstanceEntity`, `JobEntity`, `IncidentEntity`, `DecisionDefinitionEntity`, `UserEntity`, `GroupEntity`, `RoleEntity`, `TenantEntity`, `AuthorizationEntity`, `VariableEntity`, `FormEntity`, `BatchOperationEntity`, `MessageSubscriptionEntity`, `AuditLogEntity`, `GlobalListenerEntity`, `ClusterVariableEntity`.

---

## service/

**Purpose:** Business-logic orchestration layer bridging the Zeebe broker (for mutations) with the search/RDBMS layer (for queries), providing domain services with built-in authorization and security.

**Exposes:** `ProcessInstanceServices` (create, modify, migrate, cancel, search), `ProcessDefinitionServices`, `JobServices` (activate, complete, fail, search), `UserTaskServices` (assign, complete, update, search), `IncidentServices` (query, resolve), `VariableServices`, `FormServices`, `DocumentServices`, `MessageServices` (publish, correlate), `SignalServices` (broadcast), `DecisionDefinitionServices`, `DecisionInstanceServices`, `BatchOperationServices`, `AuthorizationServices`, `UserServices`, `GroupServices`, `RoleServices`, `TenantServices`, `TopologyServices`, `AuditLogServices`, `GlobalListenerServices`, `ClockServices`.

**Depends on (internal):** `camunda-search-client` (queries), `zeebe-broker-client` (mutations), `zeebe-gateway`, `zeebe-protocol`, `camunda-security-core`, `camunda-security-protocol`, `document-api`, `document-store`

**Depends on (external):** Caffeine, Micrometer, Jackson

**Key data objects:** Reads search entities (e.g. `ProcessInstanceEntity`, `JobEntity`); sends broker requests (e.g. `BrokerCreateProcessInstanceRequest`, `BrokerCompleteJobRequest`); applies authorization via `SecurityContext` and `CamundaAuthentication`.

---

## security/

**Purpose:** Three-layer security framework: `security-protocol` (authorization enums — `AuthorizationResourceType`, `PermissionType`, `EntityType`, `AuthorizationScope`), `security-core` (authentication context, authorization evaluation, multi-tenancy — `CamundaAuthentication`, `Authorization<T>`, `SecurityContext`, `ResourceAccessController`), `security-validation` (validates security entity configurations).

**Exposes:** See above. `security-protocol` is dependency-free; `security-core` integrates with Spring and OIDC; `security-validation` provides `UserValidator`, `RoleValidator`, `AuthorizationValidator`, etc.

**Depends on (internal):** `zeebe-util`, `zeebe-auth`

**Depends on (external):** Spring Context, Jackson, JSON Path, SLF4J

**Key data objects:** `CamundaAuthentication` (username, groups, roles, tenants, claims), `Authorization<T>`, `AuthorizationScope`, `AuthorizationResourceType`, `PermissionType`, `DefaultRole`.

---

## authentication/

**Purpose:** Spring Boot HTTP authentication framework implementing OIDC and Basic authentication flows, session management, token handling, and web security configuration.

**Exposes:** `DefaultCamundaAuthenticationProvider`, `OidcCamundaUserService`, `BasicCamundaUserService`, `CamundaUserService` interface, token converters (`OidcUserAuthenticationConverter`, `OidcTokenAuthenticationConverter`), security filters (`OAuth2RefreshTokenFilter`, `AdminUserCheckFilter`), `WebSecurityConfig`, session management (`WebSession`, `WebSessionRepository`), `CamundaUserDTO`.

**Depends on (internal):** `camunda-security-core`, `camunda-security-protocol`, `camunda-service`, `camunda-search-domain`, `camunda-gateway-model`

**Depends on (external):** Spring Security (OAuth2 Client, Resource Server, JOSE), Nimbus JOSE+JWT, Resilience4j, Micrometer

**Key data objects:** `CamundaUserDTO` (displayName, username, email, tenants, groups, roles, authorized components), `WebSession`, `AuthenticationProperties`.

---

## identity/

**Purpose:** Frontend-only module that bundles the Identity admin UI as a webjar (`/admin/`, `/identity/` paths); the Identity backend server is managed separately.

**Exposes:** Static web resources (HTML, JS, CSS) under `/META-INF/resources/admin/` and `/META-INF/resources/identity/`.

**Depends on (internal):** Spring Web (runtime)

**Depends on (external):** H2, PostgreSQL (runtime database drivers)

**Key data objects:** None (UI only).

---

## gateways/gateway-rest (zeebe/gateway-rest)

**Purpose:** REST API gateway exposing the Zeebe engine's capabilities as HTTP `/v2/*` endpoints with full CRUD, search, and batch operations.

**Exposes:** REST controllers for: `ProcessInstanceController` (`/v2/process-instances`), `JobController` (`/v2/jobs`), `IncidentController` (`/v2/incidents`), `UserTaskController` (`/v1/user-tasks`, `/v2/user-tasks`), `DecisionDefinitionController`, `DecisionInstanceController`, `ProcessDefinitionController`, `VariableController`, `MessageController`, `SignalController`, `ConditionalController`, `DocumentController` (`/v2/documents`), `ClusterVariableController`, `ElementInstanceController`, `GlobalTaskListenerController`, `BatchOperationController`, `TopologyController`, `LicenseController`.

**Depends on (internal):** `zeebe-gateway`, `zeebe-protocol`, `zeebe-broker-client`, `camunda-gateway-mapping-http`, `camunda-gateway-model`, `camunda-service`, `camunda-search-domain`, `camunda-security-core`, `document-api`

**Depends on (external):** Spring Web/MVC, Spring Boot

**Key data objects:** REST request/response DTOs from `camunda-gateway-model` (generated from OpenAPI spec `rest-api.yaml`), search queries, batch operation requests.

---

## gateways/gateway-mcp

**Purpose:** Model Context Protocol (MCP) server exposing Camunda APIs as AI/LLM-consumable "tools" via two MCP endpoints (`/mcp/cluster` for static tools, `/mcp/processes` for dynamic process-specific tools).

**Exposes:** MCP tools: `ClusterTools` (getClusterStatus, getTopology), `IncidentTools` (search, get, resolve), `VariableTools` (search, get, set), `ProcessInstanceTools` (search, get, create, cancel, modify, migrate), `ProcessDefinitionTools` (search, get), `UserTaskTools` (search, get, assign, complete).

**Depends on (internal):** `camunda-gateway-model`, `camunda-gateway-mapping-http`, `camunda-service`, `camunda-search-domain`, `camunda-security-core`

**Depends on (external):** Spring AI MCP (spring-ai-starter-mcp-server-webmvc), JSON Schema Generator

**Key data objects:** MCP `CallToolResult` responses, search queries with pagination/filters/sorting, operation requests mapped through shared gateway-mapping-http layer.

---

## operate/

**Purpose:** Web application for monitoring, managing, and troubleshooting Zeebe process instances, decisions, and incidents in real-time.

**Exposes:** REST APIs under `/operate/api/` (process instances, decision instances, variables, incidents), web dashboard at `/operate`.

**Depends on (internal):** Spring Boot, `zeebe-protocol`, `webapps-schema`, `camunda-search-client`, `camunda-service`

**Depends on (external):** Elasticsearch/OpenSearch, Identity (optional auth)

**Key data objects:** `ProcessInstance`, `Variable`, `DecisionInstance`, `DecisionDefinition`, `Incident`.

**Frontend:** React 19, TypeScript, Vite, Carbon Design System, BPMN-JS, DMN-JS, TanStack Query.

---

## tasklist/

**Purpose:** Web application for human users to claim, complete, and manage workflow user tasks.

**Exposes:** REST APIs under `/tasklist/v1/` (tasks, processes, forms, variables), web dashboard at `/tasklist`.

**Depends on (internal):** Spring Boot, `zeebe-protocol`, `webapps-schema`, `camunda-search-client`, `camunda-service`

**Depends on (external):** Elasticsearch/OpenSearch, Identity (optional auth)

**Key data objects:** `Task`, `Process`, `Form`, `Variable`, `ProcessInstance`.

**Frontend:** React, TypeScript, Carbon Design System, Form JS, TanStack Query, i18next.

---

## optimize/

**Purpose:** Enterprise analytics and reporting module providing process performance insights, KPIs, dashboards, and alerts (requires Camunda enterprise license).

**Exposes:** REST APIs under `/optimize/api/` (26+ services: reports, dashboards, alerts, analysis, definitions, variables), analytics dashboard at `/optimize`.

**Depends on (internal):** Spring Boot, `zeebe-protocol`, Elasticsearch

**Depends on (external):** Elasticsearch, Identity, RDBMS (MySQL/PostgreSQL for report persistence), email (notifications)

**Key data objects:** `Report`, `Dashboard`, `Alert`, `KPI`, `Collection`, `ProcessOverview`, `Variable`, `DecisionVariable`.

---

## clients/java

**Purpose:** Java client library for interacting with Camunda 8 via gRPC and REST transports.

**Exposes:** `CamundaClient` interface (extends `JobClient`), fluent builder APIs for 80+ commands (process instances, jobs, deployments, users, groups, tenants, decisions, messages, tasks, documents, etc.), support for both gRPC and REST transports.

**Depends on (internal):** `zeebe-gateway-protocol-impl`

**Depends on (external):** gRPC (grpc-stub, grpc-netty), Jackson, HttpClient5, Protobuf, Auth0 JWT, Micrometer (optional)

**Key data objects:** Generated from REST OpenAPI spec — `ProcessInstance`, `Job`, `Decision`, `Document`, `Variable`, `User`, `Group`, `Tenant`, and command request/response types.

---

## clients/camunda-spring-boot-starter

**Purpose:** Spring Boot auto-configuration starter that provides a ready-to-use `CamundaClient` bean, job worker support, and health indicators.

**Exposes:** Auto-configured `CamundaClient` bean, `CamundaClientProperties` (configuration), job worker decorators with AOP support, Spring Boot Actuator health indicators.

**Depends on (internal):** `camunda-client-java`

**Depends on (external):** Spring Boot (core, autoconfigure, actuator), HttpClient5, Jackson, gRPC API

**Key data objects:** Worker properties, authentication properties (OAuth, API key, cloud credentials), Spring configuration objects.

---

## c8run/

**Purpose:** Standalone Go CLI tool that packages and runs the complete Camunda 8 stack locally in a single command (Zeebe + Operate + Tasklist + Identity + H2 database).

**Exposes:** CLI commands: `start`, `stop`, `status`. Bundles all Java services with H2 database and optional Connectors runtime.

**Depends on (internal):** Bundles compiled artifacts from zeebe, operate, tasklist, identity

**Depends on (external):** Go 1.25+, zerolog, godotenv, gofrs/flock

**Key data objects:** Configuration via `.env` file and `connectors-application.properties`.

---

## webapps-common/

**Purpose:** Shared utility library for the Camunda web applications (Operate, Tasklist, Optimize), providing common HTTP and path utilities.

**Exposes:** `HttpUtils` (URL extraction from HTTP requests), `TreePath` (path manipulation utilities).

**Depends on (internal):** None (lightweight utility)

**Depends on (external):** Jakarta Servlet API

**Key data objects:** Request path manipulation, URL redirect tracking. No domain objects.

---

## document/

**Purpose:** Pluggable document storage abstraction with implementations for cloud providers and local storage, used by the service layer for document upload/download operations.

**Exposes:** `DocumentStore` interface (create, get, delete documents, verify content hash), `DocumentReference`, `DocumentStoreRegistry`, `DocumentCreationRequest`, `DocumentLink`, `DocumentError`. Provider implementations for GCP Cloud Storage, AWS S3, Azure Blob Storage, and in-memory storage.

**Depends on (internal):** `zeebe-util`, Jackson

**Depends on (external):** Google Cloud Storage SDK, AWS SDK (S3), Azure Blob Storage SDK

**Key data objects:** `DocumentReference`, `DocumentContent`, `DocumentLink`, `DocumentError`, `DocumentCreationRequest`.

---

## configuration/

**Purpose:** Unified Spring Boot configuration system aggregating all Camunda component properties (cluster, security, database, processing, APIs, backups) into a single `UnifiedConfiguration` entry point.

**Exposes:** `UnifiedConfiguration` root bean, `Camunda` config class with nested properties for: Cluster, System, Data, Api, Processing, Security, Expression, Webapps. 40+ configuration classes covering Engine, Security, Cache, RDBMS, backup stores, etc.

**Depends on (internal):** All major Camunda modules (zeebe-broker, zeebe-gateway, zeebe-db, search, exporters, operate/tasklist-common)

**Depends on (external):** Spring Boot, RocksDB, RDBMS, Elasticsearch/OpenSearch, backup stores (GCS, S3, Azure), Azure KeyVault

**Key data objects:** 40+ Spring `@ConfigurationProperties` classes.

---

## testing/

**Purpose:** Testing framework for Camunda process applications using Testcontainers, supporting JUnit 5, Spring Boot, and remote runtime configurations.

**Exposes:** `CamundaProcessTest` annotation, `CamundaAssertAwaitBehavior`, `CamundaProcessTestContainerProvider`, `TestCaseRunner`, `ConditionalBehaviorBuilder`. Provides opinionated test harnesses for deploying BPMN, creating instances, and asserting outcomes.

**Depends on (internal):** `camunda-client-java`, `camunda-spring-boot-starter`

**Depends on (external):** Testcontainers (Docker), JUnit 5, Docker (for Camunda, Elasticsearch, Connectors containers)

**Key data objects:** Test runtime configurations, container context, assertion properties, test case definitions.

---

## qa/

**Purpose:** Cross-component quality assurance test suites covering acceptance tests, end-to-end UI tests, architecture compliance, and compatibility testing.

**Exposes:** Test topologies: acceptance tests (full integrated user journeys with Zeebe, Operate, Tasklist, Identity, Elasticsearch/RDBMS), E2E tests (Playwright-based UI/API testing), ArchUnit tests (architecture compliance), compatibility tests (cross-version/database).

**Depends on (internal):** All production modules

**Depends on (external):** Elasticsearch, PostgreSQL, Oracle, MySQL, H2, Keycloak, Playwright, JUnit 5, Testcontainers
