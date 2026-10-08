---
architecture_md: 1
component: camunda/camunda/clients
system: { id: orchestration-cluster, map: "camunda/camunda/SYSTEM.md" }
lifecycle: production
kind: [library]
summary: The published Java client libraries of the Orchestration Cluster, the Camunda Java client (REST and gRPC) and the Camunda Spring Boot starters built on it, that applications, job workers and Camunda's own test tooling use to call the cluster.
team: { name: camunda/c8-api-team, contact: unknown }
intake: { how: issue, template: "2. feature_request.yml", labels: [component/clients] }
owns:
  - "The public Java client API io.camunda:camunda-client-java: CamundaClient, CamundaClientBuilder, CamundaClientConfiguration, ClientProperties and every command, search request, fetch request, statistics request and response type under io.camunda.client.api"
  - "How a client call reaches the cluster: REST over Apache HttpClient 5 and gRPC over grpc-netty, the preferRestOverGrpc choice (REST by default), retries, request timeouts, client-side load balancing and keep-alive"
  - "The client's REST model io.camunda.client.protocol.rest, generated at build time from the REST API v2 spec (openapi-generator 'java', type mappings, DiscriminatorModelPostProcessor in src/tool)"
  - "The job worker runtime: polling and gRPC job streaming, capacity (maxJobsActive, reserved poll lane), backoff, job handler executors, JobExceptionHandler and Micrometer job worker metrics"
  - "Client-side authentication: CredentialsProvider with the no-auth, basic-auth and OAuth (client secret, private-key JWT client assertion) providers, and the OAuth token cache (default ~/.camunda/credentials)"
  - "Client-side tenancy: the default tenant applied to tenant-aware commands, default job worker tenant IDs and filter, and the physical tenant ID sent as a REST path prefix or gRPC header"
  - "Client configuration from Properties and environment variables (ClientProperties, CamundaClientEnvironmentVariables)"
  - "The Spring Boot starter io.camunda:camunda-spring-boot-starter: auto-configuration, the camunda.client.* and camunda.clients.<name>.* properties (with the legacy zeebe.client.* remapping), the multi-client registry, and the annotations @JobWorker, @Deployment, @ClusterVariables, @Variable, @VariablesAsType, @Document, @CustomHeaders and the key annotations"
  - "Starter job handling: parameter resolution, result processing, error mapping (BpmnError, JobError), job worker lifecycle, actuator health indicator and job worker endpoint, metrics recording"
  - "The Spring Boot line variants: camunda-spring-boot-3-starter (Spring Boot 3.5), camunda-spring-boot-4-starter (relocation alias), camunda-spring-boot-starter-virtual-threads (Java 21 add-on)"
  - "The API compatibility checks of these artifacts: revapi.json per artifact and the protobuf runtime check in spring-boot-protobuf-compatibility"
does_not_own:
  - { concept: "The REST API v2 contract (paths, schemas, enums, x-* annotations) the client model is generated from", owner: camunda/camunda/zeebe/gateway-protocol }
  - { concept: "gateway.proto and the gRPC Java stubs the client calls through", owner: camunda/camunda/zeebe/gateway-protocol-impl }
  - { concept: "REST endpoint behavior, status codes and ProblemDetail errors the client maps", owner: camunda/camunda/zeebe/gateway-rest }
  - { concept: "gRPC server behavior, job long-polling and the server side of job streaming", owner: camunda/camunda/zeebe/gateway-grpc }
  - { concept: "What a command does, its rejections and permission checks", owner: camunda/camunda/zeebe/engine }
  - { concept: "The BPMN model API (Bpmn, BpmnModelInstance) accepted by deploy commands", owner: camunda/camunda/zeebe/engine }
  - { concept: "Camunda Process Test (CPT) and its Spring integration, which run a cluster and drive it through this client", owner: camunda/camunda/testing }
  - { concept: "The build parent of SDK artifacts: Java level defaults, revapi plugin setup and backwards.compat.version", owner: camunda/camunda/library-parent }
  - { concept: "The BOM that publishes the client and starter versions", owner: camunda/camunda/bom }
  - { concept: "The Go client and zbctl (deprecated in 8.6, community maintained)", owner: camunda-community-hub/zeebe-client-go }
  - { concept: "TypeScript, Python and other language SDKs", owner: unknown }
depends_on:
  - id: rest-api-spec
    component: camunda/camunda/zeebe/gateway-protocol
    kind: schema
    contract: "zeebe/gateway-protocol/src/main/proto/v2/rest-api.yaml (and the files it references), read at build time from ${maven.multiModuleProjectDirectory} by openapi-generator into io.camunda.client.protocol.rest"
    architecture: zeebe/gateway-protocol/ARCHITECTURE.md
    versions: same monorepo release; the spec is not validated again here
    workaround_policy: never
  - id: grpc-stubs
    component: camunda/camunda/zeebe/gateway-protocol-impl
    kind: library
    contract: "io.camunda:zeebe-gateway-protocol-impl: gRPC stubs and messages generated from gateway.proto (Java 8)"
    versions: same monorepo release
    workaround_policy: never
  - id: rest-api
    component: camunda/camunda/zeebe/gateway-rest
    kind: runtime-api
    contract: "Orchestration Cluster REST API v2 over HTTP, default https://0.0.0.0:8080; physical tenant as path prefix"
    architecture: zeebe/gateway-rest/ARCHITECTURE.md
    versions: "forward-compatible between minors (REST API guidelines § 2.7); older released clients against newer servers run daily in camunda-client-compatibility-test.yml"
    workaround_policy: never
  - id: grpc-api
    component: camunda/camunda/zeebe/gateway-grpc
    kind: runtime-api
    contract: "Zeebe gRPC API (gateway_protocol.Gateway), default https://0.0.0.0:26500; the only transport for job streaming"
    versions: same compatibility matrix as the REST API
    workaround_policy: never
  - id: bpmn-model
    component: camunda/camunda/zeebe/engine
    kind: library
    contract: "io.camunda:zeebe-bpmn-model (Java 8): BpmnModelInstance in deploy commands"
    architecture: zeebe/engine/ARCHITECTURE.md
    versions: same monorepo release
    workaround_policy: never
  - id: library-parent
    component: camunda/camunda/library-parent
    kind: platform
    contract: "io.camunda:camunda-library-parent: build parent of the client, starters and test tooling; revapi plugin, backwards.compat.version (8.10.0)"
    versions: same monorepo release
    workaround_policy: never
  - id: spring-boot
    component: "Spring Boot and Spring Framework (org.springframework.boot, org.springframework)"
    kind: external
    contract: "auto-configuration, configuration properties, actuator and health; the main starter builds against Spring Boot 4.1 / Spring 7, camunda-spring-boot-3-starter against Spring Boot 3.5 / Spring 6.2"
    versions: "version.spring-boot and version.spring in parent/pom.xml; overridden in camunda-spring-boot-3-starter/pom.xml"
    workaround_policy: adapter-boundary
  - id: grpc-protobuf
    component: "grpc-java, Netty, protobuf-java"
    kind: external
    contract: "gRPC transport and generated message runtime; protobuf must load on the lowest runtime the oldest supported Spring Boot manages"
    versions: "version.grpc, version.protobuf, version.spring-boot-oldest-managing-protobuf in parent/pom.xml"
    workaround_policy: adapter-boundary
  - id: http-json
    component: "Apache HttpClient 5, Jackson 2 (Jackson 3 optional in the starter), java-jwt, SLF4J, Micrometer (optional)"
    kind: external
    contract: "REST transport, JSON mapping behind JsonMapper, JWT client assertions, logging, metrics"
    versions: "managed in parent/pom.xml"
    workaround_policy: adapter-boundary
  - id: identity-provider
    component: "An OAuth 2.0 / OIDC identity provider (for example Keycloak, Camunda SaaS)"
    kind: external
    contract: "token endpoint for client credentials and private-key JWT client assertion"
    versions: TODO(confirm) the supported identity providers
    workaround_policy: adapter-boundary
consumers:
  - { who: "Users' Java and Spring Boot applications and job workers", via: "camunda-client-java public API, starter annotations, properties and beans", promise: "no breaking change to the public API between minors, checked by revapi against backwards.compat.version; io.camunda.client.impl and @ExperimentalApi carry no promise; TODO(confirm) the client-to-server version support matrix" }
  - { who: camunda/camunda/testing, via: "camunda-client-java, camunda-spring-boot-starter, CamundaSpringProcessTestContext, JobExceptionHandlerSupplier", promise: "same monorepo release; CPT and the client are tested together in camunda-spring-boot-starter-compatibility-test.yml" }
  - { who: "Connectors runtime (camunda/connectors)", via: "embeds camunda-spring-boot-starter", promise: "as users; TODO(confirm) whether internal starter types are used" }
  - { who: "camunda/camunda/operate (operate/common)", via: "CamundaClient in ZeebeConnector, including io.camunda.client.impl.util.AddressUtil", promise: "same release; impl use has no promise" }
  - { who: "qa/acceptance-tests, qa/compatibility-test, qa/util, qa/testcontainer, zeebe/qa, zeebe/test-util, load-tests/load-tester, operate/data-generator and test scopes of zeebe/broker, zeebe/gateway-grpc, zeebe/exporters/camunda-exporter, optimize/backend", via: "camunda-client-java (load-tester also the starter)", promise: "none; same release" }
  - { who: camunda/camunda/dist, via: "declares camunda-client-java as a compile dependency; no main-code import found", promise: "TODO(confirm) why dist ships it" }
exposes:
  - { contract: "Maven artifact io.camunda:camunda-client-java (Java 8, Apache 2.0): io.camunda.client and io.camunda.client.api..", spec: java/src/main/java/io/camunda/client/CamundaClient.java, policy: "revapi against backwards.compat.version; new interface methods allowed; @ExperimentalApi may change or be removed; io.camunda.client.impl and io.camunda.client.protocol.rest excluded" }
  - { contract: "Extension point: CamundaClientBuilder hooks: credentialsProvider (replace auth), withInterceptors (wrap gRPC calls), withChainHandlers (wrap REST calls), withJsonMapper (replace JSON mapping), jobWorkerExecutor / jobHandlingExecutor (replace executors), defaultJobWorkerExceptionHandler", spec: java/src/main/java/io/camunda/client/CamundaClientBuilder.java, policy: "public API, revapi" }
  - { contract: "Extension point: JobHandler, JobExceptionHandler, BackoffSupplier, JobWorkerMetrics on JobWorkerBuilderStep1", spec: java/src/main/java/io/camunda/client/api/worker/, policy: "public API, revapi" }
  - { contract: "Configuration: ClientProperties keys and CAMUNDA_* environment variables of the plain client", spec: java/src/main/java/io/camunda/client/impl/CamundaClientEnvironmentVariables.java, policy: "TODO(confirm): treated as public, though the class is in impl and outside revapi" }
  - { contract: "Maven artifacts io.camunda:camunda-spring-boot-starter (Java 17, Spring Boot 4), camunda-spring-boot-3-starter, camunda-spring-boot-4-starter (alias), camunda-spring-boot-starter-virtual-threads (Java 21)", spec: camunda-spring-boot-starter/pom.xml, policy: "revapi on the main starter; annotation processors, jobhandling, annotation.value, spring.event, spring.actuator and spring.testsupport excluded" }
  - { contract: "Starter annotations @JobWorker, @Deployment, @ClusterVariables, @Variable, @VariablesAsType, @Document, @CustomHeaders, key annotations; bean names camundaClient (alias) and defaultCamundaClient", spec: camunda-spring-boot-starter/src/main/java/io/camunda/client/annotation/, policy: "public API, revapi; the camundaClient bean name is kept (ADR clients/0001 D4)" }
  - { contract: "Starter properties camunda.client.* and camunda.clients.<name>.*, auth-methods/*.yaml and modes/*.yaml presets, legacy zeebe.client.* mappings", spec: camunda-spring-boot-starter/src/main/resources/META-INF/additional-spring-configuration-metadata.json, policy: "TODO(confirm): deprecation process for properties; legacy keys remapped by CamundaClientPropertiesPostProcessor" }
  - { contract: "Extension point: replaceable starter beans (@ConditionalOnMissingBean): CamundaClientExecutorService, JsonMapper, CredentialsProvider, ParameterResolverStrategy, ResultProcessorStrategy, JobExceptionHandlerSupplier, DocumentResultProcessorFailureHandlingStrategy, MetricsRecorder, JobWorkerMetricsFactory, backoffSupplier; additive beans: ClientInterceptor, AsyncExecChainHandler, JobWorkerValueCustomizer, CamundaClientLifecycleAware", spec: camunda-spring-boot-starter/src/main/java/io/camunda/client/spring/configuration/CamundaClientAllAutoConfiguration.java, policy: "TODO(confirm): the jobhandling strategies are excluded from revapi but documented as replaceable" }
  - { contract: "Spring events CamundaClientCreatedEvent, CamundaClientClosingEvent, CamundaPostDeploymentEvent", spec: camunda-spring-boot-starter/src/main/java/io/camunda/client/event/, policy: "public API, revapi (the spring.event wrappers are excluded)" }
constraints:
  - { id: C1, name: Public API compatibility, hard: true, ref: java/revapi.json }
  - { id: C2, name: Follows the REST API and keeps its model internal, hard: true, ref: "../docs/rest-api-endpoint-guidelines.md#6-camunda-client-extension" }
  - { id: C3, name: Transport, hard: true, ref: java/src/main/java/io/camunda/client/CamundaClientBuilder.java }
  - { id: C4, name: Java baseline, hard: true, ref: java/pom.xml }
  - { id: C5, name: Spring Boot lines and Jackson versions, hard: true, ref: camunda-spring-boot-3-starter/pom.xml }
  - { id: C6, name: Configuration surface, hard: true, ref: ../docs/adr/clients/0001-unify-spring-starter-on-multi-client-config-path.md }
  - { id: C7, name: Tenancy, hard: true, ref: ../qa/archunit-tests/src/test/java/io/camunda/client/DefaultTenantAwarenessArchTest.java }
  - { id: C8, name: Client and server version skew, hard: false, ref: ../.github/workflows/camunda-client-compatibility-test.yml }
  - { id: C9, name: Starter extension points and test tooling, hard: false, ref: ../testing/camunda-process-test-spring/src/main/java/io/camunda/process/test/impl/configuration/CamundaProcessTestDefaultConfiguration.java }
decisions: ../docs/adr/README.md
planning: { issue_templates: { increment: "3. task.yml", epic: "4. epic breakdown.yml" } }
---

# Architecture — camunda/camunda/clients

> Draft from the repository; not reviewed by its team. Facts marked `TODO(confirm)` are inferred.

## 1. Purpose

The Java libraries that users put on their classpath to talk to an Orchestration Cluster: the
Camunda Java client (commands, searches and job workers over REST and gRPC) and the Spring Boot
starters that wire it into Spring applications with `@JobWorker`, `@Deployment` and
`camunda.client.*` properties. Camunda Process Test, the Connectors runtime and most of this
repo's QA suites use the same libraries. Part of the [Orchestration Cluster system](../SYSTEM.md)
(role `clients`).

## 2. Ownership boundary

**Owns:** the client-side shape of every API capability: the Java method, its step builder, the
response type, which transport carries it, how the client authenticates and applies tenants, and
how the Spring starter exposes it as annotations, beans and properties. The full list is in the
front matter.

Owner: [CODEOWNERS](../CODEOWNERS) assigns `/clients/` to `@camunda/c8-api-team` (as does
`.codeowners`). The compatibility workflows for the client and starter are assigned to
`@camunda/core-features` in CODEOWNERS but to `@camunda/clients-sdks-ai-first-tooling` in
`.codeowners`. TODO(confirm): who answers for compatibility failures. Contact channel:
TODO(confirm).

**Does not own (route here instead):**

| If you need… | It belongs to | How to ask |
|---|---|---|
| A new endpoint, field, enum value or filter in the REST API | `camunda/camunda/zeebe/gateway-protocol` (`@camunda/c8-api-team`), then `zeebe/gateway-rest` | issue, `component/c8-api` |
| A gRPC RPC or message change | `zeebe/gateway-protocol` (`gateway.proto`), stubs in `zeebe/gateway-protocol-impl` | issue, `component/zeebe-engine` |
| Different command semantics, rejection or permission check | `camunda/camunda/zeebe/engine` | issue, `component/zeebe-engine` |
| Job streaming or long-polling behavior on the server | `camunda/camunda/zeebe/gateway-grpc` | issue, `component/zeebe-platform` |
| Process test assertions, CPT runtime, test containers | `camunda/camunda/testing` (`@camunda/c8-testing`) | issue, `component/camunda-process-test` |
| Java level defaults, revapi setup, `backwards.compat.version` | `camunda/camunda/library-parent` (`@camunda/c8-java-sdk`) | issue |
| A Go client or zbctl | community: `camunda-community-hub/zeebe-client-go` | that repo |

## 3. Structure

| Path | Artifact | What it holds |
|---|---|---|
| `java/` | `camunda-client-java` (Java 8) | `io.camunda.client` (entry points, builder, configuration), `api/` (public commands, search, fetch, statistics, responses, worker), `impl/` (transport, commands, `http/`, `oauth/`, `basicauth/`, `worker/`), generated `protocol.rest` model, `src/tool/` (`DiscriminatorModelPostProcessor`, runs at build) |
| `camunda-spring-boot-starter/` | `camunda-spring-boot-starter` (Java 17, Spring Boot 4) | `annotation/`, `jobhandling/` (parameter and result strategies), `spring/configuration/` (auto-configuration), `spring/properties/`, `spring/actuator/`, `metrics/`, `event/`, `spring/testsupport/`; property presets in `resources/auth-methods/`, `resources/modes/` |
| `camunda-spring-boot-3-starter/` | `camunda-spring-boot-3-starter` | Shades the base starter and overrides the classes that differ on Spring Boot 3.5 (actuator, `CamundaClientAllAutoConfiguration`, properties post-processor); runs the base starter's tests minus Jackson 3 and Spring Boot 4-only ones |
| `camunda-spring-boot-4-starter/` | `camunda-spring-boot-4-starter` | Pom only: Maven relocation to `camunda-spring-boot-starter` |
| `camunda-spring-boot-starter-virtual-threads/` | `camunda-spring-boot-starter-virtual-threads` (Java 21) | `VirtualThreadsAutoConfiguration`, runs before the main auto-configuration and supplies a virtual-thread `CamundaClientExecutorService` |
| `spring-boot-protobuf-compatibility/` | not published | Tests that the gRPC gencode loads on the protobuf runtime managed by the oldest supported Spring Boot |
| `go/` | none | README only: Go client deprecated in 8.6 and moved to the community |

Direction rules:

- Starters depend on the Java client, never the reverse; the 3-starter and virtual-threads add-on
  depend on the base starter.
- The client reaches the cluster only through the REST and gRPC contracts ([SYSTEM.md](../SYSTEM.md)
  DR1); its only compile dependencies on cluster code are the generated stubs and `bpmn-model`.
- Public types in `io.camunda.client.api..` do not reference the generated
  `io.camunda.client.protocol.rest` model (`ClientForbidProtocolDependencyArchTest`); conversion
  happens in `impl`.
- New starter features land on the single multi-client configuration path
  (`CamundaAutoConfiguration`, [ADR clients/0001](../docs/adr/clients/0001-unify-spring-starter-on-multi-client-config-path.md)).
  The ADR still reads "Proposed" while the code has only the unified path. TODO(confirm): status.

Variant-specific code: Spring Boot 3.5 differences go into `camunda-spring-boot-3-starter` as
class overrides excluded from the shaded base. SaaS and Self-Managed differ only in configuration
presets (`modes/saas.yaml`, `modes/self-managed.yaml`) and the cloud builder.

## 4. Binding decisions

ADR index: [`docs/adr/`](../docs/adr/README.md); client ADRs in [`docs/adr/clients/`](../docs/adr/clients/).

- [clients/0001](../docs/adr/clients/0001-unify-spring-starter-on-multi-client-config-path.md):
  one multi-client auto-configuration path; `camunda.client.*` remapped to
  `camunda.clients.default.*`; `defaultCamundaClient` `@Primary` with a `camundaClient` alias.
- [clients/0002](../docs/adr/clients/0002-reserved-poll-capacity-for-job-worker.md): a streaming
  worker reserves part of `maxJobsActive` for the poll path while pushed jobs starve it; one
  capacity count.
- [REST API guidelines § 6](../docs/rest-api-endpoint-guidelines.md#6-camunda-client-extension):
  how a new endpoint becomes a client command (step builders ending in `FinalCommandStep`, typed
  search requests, page types).
- [REST API guidelines § 2.7, § 2.12](../docs/rest-api-endpoint-guidelines.md#27-backwards-compatibility-and-breaking-changes):
  SDK-breaking spec changes; keep the `String` overload when a filter becomes advanced.
- [Architecture overview](../docs/architecture/overview.md): REST first; gRPC kept for
  compatibility and job streaming; a `gateway.proto` change cascades into the client and starter.
- [`ExperimentalApi`](java/src/main/java/io/camunda/client/api/ExperimentalApi.java): only new API
  may be marked experimental; removing the mark makes it stable.

## 5. Planning constraints

Every plan must answer each of these (applies / n/a + decision or reason).

### C1 — Public API compatibility
- **Question:** Does the change remove or change any type or method in `io.camunda.client.api..`,
  `io.camunda.client` or the starter's public packages? Does revapi pass against
  `backwards.compat.version`? Is new API `@ExperimentalApi` (with a tracking issue) or stable from
  the start? Does it need a revapi justification, and who accepts the break?
- **Hard:** yes
- **Detail:** `java/revapi.json`, `camunda-spring-boot-starter/revapi.json`, `ExperimentalApi`

### C2 — Follows the REST API and keeps its model internal
- **Question:** Does the endpoint exist in the spec first? Does the client command mirror it
  (names use "SearchRequest" not "Query", "Element" not "flow node", per `NamingArchTest`)? Do
  public types avoid the generated `protocol.rest` classes? Does the spec change need new
  `typeMappings` in `java/pom.xml`?
- **Hard:** yes
- **Detail:** [Guidelines § 6](../docs/rest-api-endpoint-guidelines.md#6-camunda-client-extension),
  `ClientForbidProtocolDependencyArchTest`, `NamingArchTest`

### C3 — Transport
- **Question:** Is the capability REST-only, gRPC-only or both? What does the client do when
  `preferRestOverGrpc` is false, or when job streaming (gRPC-only) is enabled? Does it work through
  `withInterceptors` (gRPC) and `withChainHandlers` (REST) both?
- **Hard:** yes
- **Detail:** `CamundaClientBuilder.preferRestOverGrpc`, [overview](../docs/architecture/overview.md)

### C4 — Java baseline
- **Question:** Does code in `java/` compile for Java 8 (no newer language or library API), and
  does it pull in no dependency above Java 8? Does starter code stay on Java 17, with Java 21
  features only in the virtual-threads add-on?
- **Hard:** yes
- **Detail:** `version.java` in each `pom.xml`

### C5 — Spring Boot lines and Jackson versions
- **Question:** Does a starter change work on Spring Boot 4 and on 3.5 (does
  `camunda-spring-boot-3-starter` need an override or a test exclusion)? Does it work with
  Jackson 2 and Jackson 3 `JsonMapper` beans?
- **Hard:** yes
- **Detail:** `camunda-spring-boot-3-starter/pom.xml`, `JsonMapperConfiguration`, `Jackson3JsonMapperConfiguration`

### C6 — Configuration surface
- **Question:** Is a new option available in the plain client (builder, `ClientProperties`,
  environment variable) and in the starter, under `camunda.client.*` and per client under
  `camunda.clients.<name>.*`, with configuration metadata? Is a renamed key mapped from the old one?
- **Hard:** yes
- **Detail:** [ADR clients/0001](../docs/adr/clients/0001-unify-spring-starter-on-multi-client-config-path.md),
  `camunda-client-legacy-property-mappings.properties`

### C7 — Tenancy
- **Question:** Does a tenant-aware command implement `CommandWithTenantStep` and take the default
  tenant from `CamundaClientConfiguration`? Does a new call carry the physical tenant (REST path
  prefix, gRPC header)? Are worker metrics tagged with it?
- **Hard:** yes
- **Detail:** `DefaultTenantAwarenessArchTest`, `PhysicalTenantInterceptor`

### C8 — Client and server version skew
- **Question:** What does this client version do against an older server that lacks the endpoint
  or field, and what does an older client do against the new server? Is the combination covered by
  the daily compatibility matrix? TODO(confirm): the supported client-to-server version range.
- **Hard:** no
- **Detail:** `camunda-client-compatibility-test.yml`, `camunda-spring-boot-starter-compatibility-test.yml`

### C9 — Starter extension points and test tooling
- **Question:** Does the change alter a replaceable starter bean (`ParameterResolverStrategy`,
  `ResultProcessorStrategy`, `JobExceptionHandlerSupplier`, ...) or `CamundaSpringProcessTestContext`
  that users or Camunda Process Test replace or use? Is `testing/` updated in the same PR?
- **Hard:** no
- **Detail:** front matter `exposes`, `consumers`

## 6. Data and persistence

None on the server side. The client keeps state only in memory (worker capacity, backoff), except
the OAuth credentials cache file (default `~/.camunda/credentials`, configurable by builder or
environment variable).

## 7. Cross-cutting qualities

- **Security:** credentials come from a `CredentialsProvider`; OAuth tokens are cached and
  refreshed client-side; TLS by CA certificate path or the JVM trust store. The server enforces
  permissions.
- **Tenancy:** see C7.
- **Performance:** job workers bound in-flight jobs by `maxJobsActive`; streaming and polling share
  one capacity count ([ADR clients/0002](../docs/adr/clients/0002-reserved-poll-capacity-for-job-worker.md)).
  The handler executor is replaceable (virtual threads via the add-on, see
  [virtual threads guidance](../docs/virtual_threads.md)).
- **Observability:** Micrometer job worker metrics (optional dependency), actuator health
  indicator and job worker endpoint in the starter.
- **Spring conventions:** [docs/spring-conventions.md](../docs/spring-conventions.md) (no
  `@ConditionalOnBean`/`@ConditionalOnMissingBean` on plain `@Configuration` classes).

## 8. Delivery

As [Orchestration Cluster SYSTEM.md](../SYSTEM.md) § 6: released with the monorepo to Maven
Central and listed in `bom/`. Specific here: the artifacts are used outside the cluster, so API
compatibility is checked against the last released minor (`backwards.compat.version` in
`library-parent/pom.xml`). Compatibility with released servers runs daily in CI. Backports go
through the backport action. TODO(confirm): who bumps `backwards.compat.version` after a release
(`update-backwards-compat-version.yml`), and how long removed V1 (`io.camunda.zeebe.client`) APIs
were announced before 8.10.

## 9. Testing expectations

- **Client unit tests (required):** in `java/src/test/`, REST interactions mocked with WireMock
  and gRPC with in-process servers; one package per resource
  ([guidelines § 6](../docs/rest-api-endpoint-guidelines.md#6-camunda-client-extension)).
  Authentication against Keycloak in Testcontainers (`*IT`).
- **Starter tests:** in `camunda-spring-boot-starter/src/test/`, with generated `@JobWorker`
  permutation tests (`JobWorkerPermutationsGenerator`); the 3-starter re-runs them on Spring Boot 3.5.
- **Architecture:** `ClientForbidProtocolDependencyArchTest`, `NamingArchTest`,
  `DefaultTenantAwarenessArchTest` in [`qa/archunit-tests`](../qa/archunit-tests).
- **API compatibility:** revapi in the build; protobuf runtime in `spring-boot-protobuf-compatibility`.
- **End to end:** [`qa/acceptance-tests`](../docs/testing/acceptance.md) drive the cluster through
  this client; daily client and starter compatibility workflows.
- Run: `./mvnw verify -pl clients/java -DskipTests=false -Dquickly` (and per starter module).

## 10. Planning conventions

- Issues: `2. feature_request.yml`, `3. task.yml`, `4. epic breakdown.yml` in
  `.github/ISSUE_TEMPLATE/`; label `component/clients` (create-issue skill).
- TODO(confirm): plans directory and ID prefix for plan refs.

## 11. Glossary

| Term | Meaning here |
|---|---|
| Java client | `camunda-client-java`, successor of the removed Zeebe Java client (`io.camunda.zeebe.client`) |
| Starter | `camunda-spring-boot-starter`; "3-starter" is its Spring Boot 3.5 variant |
| Job streaming | Server pushes jobs to a worker over a gRPC stream; polling is `ActivateJobs`. Both feed one worker |
| Poll lane | Capacity a streaming worker reserves for polled jobs (ADR clients/0002) |
| Default tenant | The multi-tenancy `tenantId` a tenant-aware command uses when none is set |
| Physical tenant | An isolated tenant of the cluster, addressed by a REST path prefix or gRPC header; not the multi-tenancy `tenantId` |
| `@ExperimentalApi` | Public API with no compatibility promise until the annotation is removed |
