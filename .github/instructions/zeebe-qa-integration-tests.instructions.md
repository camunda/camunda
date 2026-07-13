```yaml
---
applyTo: "zeebe/qa/integration-tests/**"
---
```
# Zeebe QA Integration Tests

Integration tests for the Zeebe distributed process engine, verifying broker clusters, client API commands, backup/restore, networking, authorization, management endpoints, and gateway behavior against real (embedded or containerized) broker instances.

## Module Architecture

All code is under `src/test/java/io/camunda/zeebe/it/` with four top-level packages:

- **`cluster/`** — Cluster infrastructure tests: backup (S3/GCS/Azure/filesystem), clustering (failover, replication, dynamic scaling, data deletion, snapshots), exporter lifecycle, health monitoring, management actuator endpoints, network (TLS, compression, IPv6), startup/restart
- **`engine/`** — Engine behavior tests: authorization (OIDC/Basic over REST/gRPC), client commands (one test per command type), multi-tenancy, processing (banning, timers, forms), query API
- **`shared/`** — Cross-cutting tests: gateway (health probes, graceful shutdown, authentication, REST filters), observability (metrics), security headers, smoke tests
- **`util/`** — Test helpers: `ZeebeAssertHelper`, `ZeebeResourcesHelper`, `GrpcClientRule`, `BrokerClassRuleHelper`, `RecordingJobHandler`, `ZeebeContainerUtil`

## Two Test Infrastructure Generations

### Modern (JUnit 5) — Preferred for New Tests
Use `@ZeebeIntegration` annotation with `@TestZeebe` field injection from `zeebe-qa-util`:
```java
@ZeebeIntegration
final class MyNewIT {
  @TestZeebe
  private static final TestStandaloneBroker ZEEBE =
      new TestStandaloneBroker().withRecordingExporter(true).withUnauthenticatedAccess();

  @AutoClose CamundaClient client;

  @BeforeEach
  void init() {
    client = ZEEBE.newClientBuilder().defaultRequestTimeout(Duration.ofSeconds(15)).build();
  }
}
```
For multi-broker tests, use `TestCluster.builder()` instead of `TestStandaloneBroker`. See `ScaleUpBrokersTest`, `FilesystemBackupAcceptanceIT`.

### Legacy (JUnit 4) — Existing Tests Only
Uses `ClusteringRule` (multi-broker) or `EmbeddedBrokerRule` (single-broker) with `GrpcClientRule` via JUnit 4 `@Rule` / `RuleChain`:
```java
public class FailOverReplicationTest {
  private final ClusteringRule clusteringRule = new ClusteringRule(1, 3, 3, ...);
  public final GrpcClientRule clientRule = new GrpcClientRule(clusteringRule);
  @Rule public RuleChain ruleChain = RuleChain.outerRule(Timeout.seconds(120))
      .around(clusteringRule).around(clientRule);
}
```
`ClusteringRuleExtension` wraps `ClusteringRule` for JUnit 5 usage as a bridge. Do not create new tests with JUnit 4 rules.

## Key Test Utilities

- **`ZeebeResourcesHelper`** (`util/`): Deploys processes, creates jobs/process instances, waits for deployment distribution. Instantiate with `new ZeebeResourcesHelper(client)`.
- **`ZeebeAssertHelper`** (`util/`): Static assertion methods wrapping `RecordingExporter` queries — `assertProcessInstanceCompleted()`, `assertJobCreated()`, `assertUserCreated()`, `assertTenantCreated()`, etc.
- **`RecordingJobHandler`** (`util/`): Captures `ActivatedJob` instances for later assertion. Supports chained handlers.
- **`BrokerClassRuleHelper`** (`util/`): JUnit 4 `TestWatcher` that resets `RecordingExporter` per test and generates test-method-scoped identifiers (`getJobType()`, `getBpmnProcessId()`).
- **`ZeebeContainerUtil`** (`util/`): Factory for `CamundaClientBuilder` from `ZeebeCluster` or `ContainerEngine`.
- **Actuator helpers** (from `zeebe-qa-util`): `BackupActuator`, `ClusterActuator`, `BanningActuator`, `ActorClockActuator` — typed clients for management API endpoints.

## Test Patterns

### Client Command Tests (`engine/client/command/`)
Each command type has its own test class. Most use `@ParameterizedTest` with `@ValueSource(booleans = {true, false})` to test both REST and gRPC paths:
```java
@ParameterizedTest
@ValueSource(booleans = {true, false})
public void shouldCompleteJobWithVariables(final boolean useRest, final TestInfo testInfo) {
    final String jobType = "job-" + testInfo.getDisplayName();
    // given / when / then ...
}
```
Use `TestInfo` to derive unique job types and process IDs. See `CompleteJobTest`, `CreateProcessInstanceTest`.

### Backup Acceptance Tests (`cluster/backup/`)
Interface-based test contracts define reusable acceptance scenarios. Storage-specific ITs implement the interface and configure the store:
- `BackupAcceptance` → `FilesystemBackupAcceptanceIT`, `S3BackupAcceptanceIT`, `GcsBackupAcceptanceIT`, `AzureBackupAcceptanceIT`
- `BackupRetentionAcceptance` → `*BackupRetentionAcceptanceIT`
- `RestoreAcceptance` → `*RestoreAcceptanceIT`

### Smoke Tests (`shared/smoke/`)
Use `@SmokeTest` (JUnit 5 `@Tag("smoke-test")`) for basic startup verification. Use `@ContainerSmokeTest` for Docker-based smoke tests. The Maven `smoke-test` profile filters to these tests. See `StandaloneBrokerIT`, `ContainerClusterSmokeIT`.

### Authorization Tests (`engine/authorization/`)
Test OIDC and Basic auth over REST and gRPC. Use Testcontainers for Keycloak and Elasticsearch. Configure `TestStandaloneBroker` with `.withAuthenticatedAccess()`, `.withAuthenticationMethod(AuthenticationMethod.OIDC)`, and security config. See `OidcAuthOverRestIT`.

## Naming and Execution

- **`*Test` suffix**: Runs via Maven Surefire (unit test phase) — typically embedded broker tests
- **`*IT` suffix**: Runs via Maven Failsafe (integration test phase) — typically container or cluster tests
- Scoped execution: `./mvnw -pl zeebe/qa/integration-tests -am verify -DskipChecks -Dtest=<ClassName> -DfailIfNoTests=false -T1C`
- Smoke tests only: `./mvnw -pl zeebe/qa/integration-tests -am verify -P smoke-test -T1C`

## Data Flow in Tests

1. Test creates `TestStandaloneBroker`/`TestCluster` → embedded broker(s) start with `RecordingExporter`
2. `CamundaClient` sends commands (deploy, create instance, complete job, etc.)
3. Engine processes commands → produces records captured by `RecordingExporter`
4. Assertions query `RecordingExporter` streams (e.g., `RecordingExporter.jobRecords(JobIntent.COMPLETED).withType(type).getFirst()`)
5. Management API tests use actuator clients to interact with `/actuator/*` endpoints

## Common Pitfalls

- Always call `.withRecordingExporter(true)` on `TestStandaloneBroker`/`TestCluster` or assertions using `RecordingExporter` will hang
- Always use `@AutoClose` on `CamundaClient` fields to prevent resource leaks
- Use `Awaitility` for async assertions — never `Thread.sleep`
- Use `.limit()` or `.getFirst()` on `RecordingExporter` streams to avoid infinite blocking
- Derive unique job types and process IDs from `TestInfo.getDisplayName()` to prevent cross-test interference
- For multi-partition tests, use `ZeebeResourcesHelper.waitUntilDeploymentIsDone(key)` before creating instances

## Key Reference Files

- `src/test/java/.../engine/client/command/CompleteJobTest.java` — canonical modern client command test
- `src/test/java/.../cluster/backup/BackupAcceptance.java` — interface-based backup test contract
- `src/test/java/.../cluster/backup/FilesystemBackupAcceptanceIT.java` — backup acceptance implementation
- `src/test/java/.../cluster/clustering/ClusteringRule.java` — legacy multi-broker rule (still used)
- `src/test/java/.../util/ZeebeAssertHelper.java` — static assertion helpers via RecordingExporter
- `src/test/java/.../util/ZeebeResourcesHelper.java` — process/job creation helpers
- `src/test/java/.../shared/smoke/StandaloneBrokerIT.java` — smoke test pattern