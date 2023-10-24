/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.it.multitenancy;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.identity.sdk.Identity;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.response.ActivateJobsResponse;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.client.api.response.BroadcastSignalResponse;
import io.camunda.zeebe.client.api.response.CompleteJobResponse;
import io.camunda.zeebe.client.api.response.DeploymentEvent;
import io.camunda.zeebe.client.api.response.EvaluateDecisionResponse;
import io.camunda.zeebe.client.api.response.ModifyProcessInstanceResponse;
import io.camunda.zeebe.client.api.response.Process;
import io.camunda.zeebe.client.api.response.ProcessInstanceEvent;
import io.camunda.zeebe.client.api.response.PublishMessageResponse;
import io.camunda.zeebe.client.api.response.ResolveIncidentResponse;
import io.camunda.zeebe.client.impl.oauth.OAuthCredentialsProviderBuilder;
import io.camunda.zeebe.gateway.impl.configuration.AuthenticationCfg.AuthMode;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.qa.util.cluster.TestHealthProbe;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.junit.AutoCloseResources;
import io.camunda.zeebe.test.util.junit.AutoCloseResources.AutoCloseResource;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.testcontainers.ContainerLogsDumper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.function.Supplier;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;
import org.testcontainers.images.PullPolicy;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/** Verifies that data can be isolated per tenant using Identity as the tenant provider. */
@Testcontainers
@AutoCloseResources
public class MultiTenancyOverIdentityIT {

  @TempDir private static Path credentialsCacheDir;

  private static final String DEFAULT_TENANT = TenantOwned.DEFAULT_TENANT_IDENTIFIER;

  private static final String DATABASE_HOST = "postgres";
  private static final int DATABASE_PORT = 5432;
  private static final String DATABASE_USER = "postgres";
  private static final String DATABASE_PASSWORD = "zecret";
  private static final String DATABASE_NAME = "postgres";

  private static final String KEYCLOAK_USER = "admin";
  private static final String KEYCLOAK_PASSWORD = "admin";
  private static final String KEYCLOAK_PATH_CAMUNDA_REALM = "/realms/camunda-platform";
  private static final String ZEEBE_CLIENT_ID_TENANT_A = "zeebe-tenant-a-and-default";
  private static final String ZEEBE_CLIENT_ID_TENANT_B = "zeebe-tenant-b-and-default";
  private static final String ZEEBE_CLIENT_ID_TENANT_A_AND_B = "zeebe-tenant-a-and-b-and-default";
  private static final String ZEEBE_CLIENT_ID_TENANT_DEFAULT = ZEEBE_CLIENT_ID_TENANT_A;
  private static final String ZEEBE_CLIENT_ID_WITHOUT_TENANT = "zeebe-without-tenant";
  private static final String ZEEBE_CLIENT_AUDIENCE = "zeebe-api";
  private static final String ZEEBE_CLIENT_SECRET = "zecret";

  private static final Network NETWORK = Network.newNetwork();

  @Container
  @SuppressWarnings("resource")
  private static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:15.0-alpine")
          .withExposedPorts(DATABASE_PORT)
          .withDatabaseName(DATABASE_NAME)
          .withUsername(DATABASE_USER)
          .withPassword(DATABASE_PASSWORD)
          .withNetwork(NETWORK)
          .withNetworkAliases(DATABASE_HOST);

  @Container
  @SuppressWarnings("resource")
  private static final GenericContainer<?> KEYCLOAK =
      new GenericContainer<>("bitnami/keycloak:22.0.1")
          .dependsOn(POSTGRES)
          .withEnv("KC_HEALTH_ENABLED", "true")
          .withEnv("KEYCLOAK_ADMIN_USER", KEYCLOAK_USER)
          .withEnv("KEYCLOAK_ADMIN_PASSWORD", KEYCLOAK_PASSWORD)
          .withEnv("KEYCLOAK_DATABASE_HOST", DATABASE_HOST)
          .withEnv("KEYCLOAK_DATABASE_PORT", String.valueOf(DATABASE_PORT))
          .withEnv("KEYCLOAK_DATABASE_USER", DATABASE_USER)
          .withEnv("KEYCLOAK_DATABASE_PASSWORD", DATABASE_PASSWORD)
          .withEnv("KEYCLOAK_DATABASE_NAME", DATABASE_NAME)
          .withNetwork(NETWORK)
          .withNetworkAliases("keycloak")
          .withExposedPorts(8080)
          .waitingFor(
              new HttpWaitStrategy()
                  .forPort(8080)
                  .forPath("/health/ready")
                  .allowInsecure()
                  .forStatusCode(200));

  @Container
  @SuppressWarnings("resource")
  private static final GenericContainer<?> IDENTITY =
      new GenericContainer<>(
              DockerImageName.parse("camunda/identity")
                  .withTag(
                      System.getProperty(
                          "identity.docker.image.version",
                          Identity.class.getPackage().getImplementationVersion())))
          .withImagePullPolicy(
              System.getProperty("identity.docker.image.version", "SNAPSHOT").equals("SNAPSHOT")
                  ? PullPolicy.alwaysPull()
                  : PullPolicy.defaultPolicy())
          .dependsOn(POSTGRES, KEYCLOAK)
          .withEnv("MULTITENANCY_ENABLED", "true")
          .withEnv("RESOURCE_AUTHORIZATIONS_ENABLED", "true")
          .withEnv("IDENTITY_LOG_LEVEL", "TRACE")
          .withEnv("logging_level_org_springframework_security", "DEBUG")
          .withEnv("LOGGING_LEVEL_org.springframework", "DEBUG")
          .withEnv("KEYCLOAK_URL", "http://keycloak:8080")
          .withEnv(
              "IDENTITY_AUTH_PROVIDER_BACKEND_URL",
              "http://keycloak:8080" + KEYCLOAK_PATH_CAMUNDA_REALM)
          .withEnv("KEYCLOAK_SETUP_USER", KEYCLOAK_USER)
          .withEnv("KEYCLOAK_SETUP_PASSWORD", KEYCLOAK_PASSWORD)
          .withEnv("KEYCLOAK_INIT_ZEEBE_SECRET", ZEEBE_CLIENT_SECRET)
          .withEnv("KEYCLOAK_CLIENTS_0_NAME", ZEEBE_CLIENT_ID_TENANT_A)
          .withEnv("KEYCLOAK_CLIENTS_0_ID", ZEEBE_CLIENT_ID_TENANT_A)
          .withEnv("KEYCLOAK_CLIENTS_0_SECRET", ZEEBE_CLIENT_SECRET)
          .withEnv("KEYCLOAK_CLIENTS_0_TYPE", "m2m")
          .withEnv("KEYCLOAK_CLIENTS_0_PERMISSIONS_0_RESOURCE_SERVER_ID", ZEEBE_CLIENT_AUDIENCE)
          .withEnv("KEYCLOAK_CLIENTS_0_PERMISSIONS_0_DEFINITION", "write:*")
          .withEnv("KEYCLOAK_CLIENTS_1_NAME", ZEEBE_CLIENT_ID_TENANT_B)
          .withEnv("KEYCLOAK_CLIENTS_1_ID", ZEEBE_CLIENT_ID_TENANT_B)
          .withEnv("KEYCLOAK_CLIENTS_1_SECRET", ZEEBE_CLIENT_SECRET)
          .withEnv("KEYCLOAK_CLIENTS_1_TYPE", "m2m")
          .withEnv("KEYCLOAK_CLIENTS_1_PERMISSIONS_0_RESOURCE_SERVER_ID", ZEEBE_CLIENT_AUDIENCE)
          .withEnv("KEYCLOAK_CLIENTS_1_PERMISSIONS_0_DEFINITION", "write:*")
          .withEnv("KEYCLOAK_CLIENTS_2_NAME", ZEEBE_CLIENT_ID_TENANT_A_AND_B)
          .withEnv("KEYCLOAK_CLIENTS_2_ID", ZEEBE_CLIENT_ID_TENANT_A_AND_B)
          .withEnv("KEYCLOAK_CLIENTS_2_SECRET", ZEEBE_CLIENT_SECRET)
          .withEnv("KEYCLOAK_CLIENTS_2_TYPE", "m2m")
          .withEnv("KEYCLOAK_CLIENTS_2_PERMISSIONS_0_RESOURCE_SERVER_ID", ZEEBE_CLIENT_AUDIENCE)
          .withEnv("KEYCLOAK_CLIENTS_2_PERMISSIONS_0_DEFINITION", "write:*")
          .withEnv("KEYCLOAK_CLIENTS_3_NAME", ZEEBE_CLIENT_ID_WITHOUT_TENANT)
          .withEnv("KEYCLOAK_CLIENTS_3_ID", ZEEBE_CLIENT_ID_WITHOUT_TENANT)
          .withEnv("KEYCLOAK_CLIENTS_3_SECRET", ZEEBE_CLIENT_SECRET)
          .withEnv("KEYCLOAK_CLIENTS_3_TYPE", "m2m")
          .withEnv("KEYCLOAK_CLIENTS_3_PERMISSIONS_0_RESOURCE_SERVER_ID", ZEEBE_CLIENT_AUDIENCE)
          .withEnv("KEYCLOAK_CLIENTS_3_PERMISSIONS_0_DEFINITION", "write:*")
          .withEnv("IDENTITY_RETRY_ATTEMPTS", "90")
          .withEnv("IDENTITY_RETRY_DELAY_SECONDS", "1")
          .withEnv("IDENTITY_DATABASE_HOST", DATABASE_HOST)
          .withEnv("IDENTITY_DATABASE_PORT", String.valueOf(DATABASE_PORT))
          .withEnv("IDENTITY_DATABASE_NAME", DATABASE_NAME)
          .withEnv("IDENTITY_DATABASE_USERNAME", DATABASE_USER)
          .withEnv("IDENTITY_DATABASE_PASSWORD", DATABASE_PASSWORD)
          .withNetwork(NETWORK)
          .withExposedPorts(8080, 8082)
          .waitingFor(
              new HttpWaitStrategy()
                  .forPort(8082)
                  .forPath("/actuator/health")
                  .allowInsecure()
                  .forStatusCode(200))
          .withNetworkAliases("identity");

  @AutoCloseResource private static final TestStandaloneBroker ZEEBE = new TestStandaloneBroker();
  private static final String TENANT_A = "tenant-a";
  private static final String TENANT_B = "tenant-b";

  @SuppressWarnings("unused")
  @RegisterExtension
  final ContainerLogsDumper logsWatcher =
      new ContainerLogsDumper(
          () -> Map.of("postgres", POSTGRES, "keycloak", KEYCLOAK, "identity", IDENTITY));

  private String processId;
  private BpmnModelInstance process;

  @BeforeAll
  static void init() throws Exception {
    ZEEBE
        .withGatewayConfig(
            gateway -> {
              gateway.getMultiTenancy().setEnabled(true);
              gateway.getSecurity().getAuthentication().setMode(AuthMode.IDENTITY);
              gateway
                  .getSecurity()
                  .getAuthentication()
                  .getIdentity()
                  .setBaseUrl(
                      "http://%s:%d".formatted(IDENTITY.getHost(), IDENTITY.getMappedPort(8080)));
              gateway
                  .getSecurity()
                  .getAuthentication()
                  .getIdentity()
                  .setIssuerBackendUrl(
                      "http://%s:%d%s"
                          .formatted(
                              KEYCLOAK.getHost(),
                              KEYCLOAK.getMappedPort(8080),
                              KEYCLOAK_PATH_CAMUNDA_REALM));
              gateway
                  .getSecurity()
                  .getAuthentication()
                  .getIdentity()
                  .setAudience(ZEEBE_CLIENT_AUDIENCE);
            })
        .withRecordingExporter(true)
        .start()
        .await(TestHealthProbe.READY);

    awaitCamundaRealmAvailabilityOnKeycloak();

    associateTenantsWithClient(List.of(DEFAULT_TENANT, TENANT_A), ZEEBE_CLIENT_ID_TENANT_A);
    associateTenantsWithClient(List.of(DEFAULT_TENANT, TENANT_B), ZEEBE_CLIENT_ID_TENANT_B);
    associateTenantsWithClient(
        List.of(DEFAULT_TENANT, TENANT_A, TENANT_B), ZEEBE_CLIENT_ID_TENANT_A_AND_B);
  }

  @BeforeEach
  void setup() {
    RecordingExporter.reset();
    processId = Strings.newRandomValidBpmnId();
    process =
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .serviceTask("task", b -> b.zeebeJobType("type"))
            .endEvent()
            .done();
  }

  @Test
  void shouldAuthorizeTopologyRequestWithTenantAccess() {
    try (final var client = createZeebeClient(ZEEBE_CLIENT_ID_TENANT_A)) {
      // when
      final var topology = client.newTopologyRequest().send().join();

      // then
      assertThat(topology.getBrokers()).hasSize(1);
    }
  }

  @Test
  void shouldAuthorizeTopologyRequestWithoutTenantAccess() {
    try (final var client = createZeebeClient(ZEEBE_CLIENT_ID_WITHOUT_TENANT)) {
      // when
      final var topology = client.newTopologyRequest().send().join();

      // then
      assertThat(topology.getBrokers()).hasSize(1);
    }
  }

  @Test
  void shouldAuthorizeDeployProcess() {
    // given
    try (final var client = createZeebeClient(ZEEBE_CLIENT_ID_TENANT_A)) {
      // when
      final Future<DeploymentEvent> response =
          client
              .newDeployResourceCommand()
              .addProcessModel(process, "process.bpmn")
              .tenantId(TENANT_A)
              .send();

      // then
      assertThat(response)
          .describedAs("Expect that process can be deployed for tenant-a")
          .succeedsWithin(Duration.ofSeconds(10));
    }
  }

  @Test
  void shouldDenyDeployProcessWhenUnauthorized() {
    // given
    try (final var client = createZeebeClient(ZEEBE_CLIENT_ID_TENANT_A)) {
      // when
      final Future<DeploymentEvent> result =
          client
              .newDeployResourceCommand()
              .addProcessModel(process, "process.bpmn")
              .tenantId(TENANT_B)
              .send();

      // then
      assertThat(result)
          .failsWithin(Duration.ofSeconds(10))
          .withThrowableThat()
          .withMessageContaining("PERMISSION_DENIED")
          .withMessageContaining(
              "Expected to handle gRPC request DeployResource with tenant identifier 'tenant-b'")
          .withMessageContaining("but tenant is not authorized to perform this request");
    }
  }

  @Test
  void shouldIncrementProcessVersionPerTenant() {
    // given
    try (final var client = createZeebeClient(ZEEBE_CLIENT_ID_TENANT_A)) {
      client
          .newDeployResourceCommand()
          .addProcessModel(process, "process.bpmn")
          .tenantId(TENANT_A)
          .send()
          .join();
    }
    try (final var client = createZeebeClient(ZEEBE_CLIENT_ID_TENANT_B)) {
      client
          .newDeployResourceCommand()
          .addProcessModel(process, "process.bpmn")
          .tenantId(TENANT_B)
          .send()
          .join();
    }

    try (final var client = createZeebeClient(ZEEBE_CLIENT_ID_TENANT_B)) {
      // when
      final var processV2 = Bpmn.createExecutableProcess(processId).startEvent().done();
      final Future<DeploymentEvent> result =
          client
              .newDeployResourceCommand()
              .addProcessModel(processV2, "process.bpmn")
              .tenantId(TENANT_B)
              .send();

      // then
      assertThat(result)
          .succeedsWithin(Duration.ofSeconds(10))
          .describedAs("Process version is incremented for tenant-b but not for tenant-a")
          .extracting(deploymentEvent -> deploymentEvent.getProcesses().get(0))
          .extracting(Process::getVersion, Process::getTenantId)
          .containsExactly(2, TENANT_B);
    }
  }

  @Test
  void shouldAuthorizeCreateProcessInstance() {
    // given
    final long processDefinitionKey;
    try (final var client = createZeebeClient(ZEEBE_CLIENT_ID_TENANT_A_AND_B)) {
      processDefinitionKey =
          client
              .newDeployResourceCommand()
              .addProcessModel(process, "process.bpmn")
              .tenantId(TENANT_A)
              .send()
              .join()
              .getProcesses()
              .stream()
              .map(Process::getProcessDefinitionKey)
              .findFirst()
              .orElseThrow();

      // when
      final Future<ProcessInstanceEvent> result =
          client
              .newCreateInstanceCommand()
              .processDefinitionKey(processDefinitionKey)
              .tenantId(TENANT_A)
              .send();

      // then
      assertThat(result)
          .describedAs(
              "Expect that process instance can be created as the client has access process of tenant-a")
          .succeedsWithin(Duration.ofSeconds(10));
    }
  }

  @Test
  void shouldNotFindOtherTenantsProcessById() {
    // given
    try (final var client = createZeebeClient(ZEEBE_CLIENT_ID_TENANT_A)) {
      client
          .newDeployResourceCommand()
          .addProcessModel(process, "process.bpmn")
          .tenantId(TENANT_A)
          .send()
          .join();
    }

    try (final var client = createZeebeClient(ZEEBE_CLIENT_ID_TENANT_B)) {
      // when
      final Future<ProcessInstanceEvent> result =
          client
              .newCreateInstanceCommand()
              .bpmnProcessId(processId)
              .latestVersion()
              .tenantId(TENANT_B)
              .send();

      // then
      assertThat(result)
          .failsWithin(Duration.ofSeconds(10))
          .withThrowableThat()
          .describedAs("Process definition should exist for tenant-a but not for tenant-b")
          .withMessageContaining("NOT_FOUND")
          .withMessageContaining("Expected to find process definition with process ID")
          .withMessageContaining("but none found");
    }
  }

  @Test
  void shouldNotFindOtherTenantsProcessByKey() {
    // given
    final long processDefinitionKey;
    try (final var client = createZeebeClient(ZEEBE_CLIENT_ID_TENANT_A)) {
      processDefinitionKey =
          client
              .newDeployResourceCommand()
              .addProcessModel(process, "process.bpmn")
              .tenantId(TENANT_A)
              .send()
              .join()
              .getProcesses()
              .stream()
              .map(Process::getProcessDefinitionKey)
              .findFirst()
              .orElseThrow();
    }

    try (final var client = createZeebeClient(ZEEBE_CLIENT_ID_TENANT_B)) {
      // when
      final Future<ProcessInstanceEvent> result =
          client
              .newCreateInstanceCommand()
              .processDefinitionKey(processDefinitionKey)
              .tenantId(TENANT_B)
              .send();

      // then
      assertThat(result)
          .failsWithin(Duration.ofSeconds(10))
          .withThrowableThat()
          .describedAs("Process definition should exist for tenant-a but not for tenant-b")
          .withMessageContaining("NOT_FOUND")
          .withMessageContaining("Expected to find process definition with key")
          .withMessageContaining("but none found");
    }
  }

  @Test
  void shouldNotFindOtherTenantsProcessInCallActivity() {
    // given
    try (final var client = createZeebeClient(ZEEBE_CLIENT_ID_TENANT_A)) {
      client
          .newDeployResourceCommand()
          .addProcessModel(process, "process.bpmn")
          .tenantId(TENANT_A)
          .send()
          .join();
    }

    try (final var client = createZeebeClient(ZEEBE_CLIENT_ID_TENANT_B)) {
      client
          .newDeployResourceCommand()
          .addProcessModel(
              Bpmn.createExecutableProcess("parent")
                  .startEvent()
                  .callActivity("call", c -> c.zeebeProcessId(processId))
                  .endEvent()
                  .done(),
              "parent.bpmn")
          .tenantId(TENANT_B)
          .send()
          .join();
    }

    try (final var client = createZeebeClient(ZEEBE_CLIENT_ID_TENANT_B)) {
      // when
      client
          .newCreateInstanceCommand()
          .bpmnProcessId("parent")
          .latestVersion()
          .tenantId(TENANT_B)
          .send();

      // then
      Assertions.assertThat(
              RecordingExporter.incidentRecords().withBpmnProcessId("parent").getFirst().getValue())
          .hasErrorMessage(
              "Expected process with BPMN process id '%s' to be deployed, but not found."
                  .formatted(processId));
    }
  }

  /**
   * This test case may become obsolete when we allow shared processes definitions across tenants.
   */
  @Test
  void shouldNotFindOtherTenantsProcessEvenWhenClientIsAuthorizedForTenant() {
    // given
    final long processDefinitionKey;
    try (final var client = createZeebeClient(ZEEBE_CLIENT_ID_TENANT_A_AND_B)) {
      processDefinitionKey =
          client
              .newDeployResourceCommand()
              .addProcessModel(process, "process.bpmn")
              .tenantId(TENANT_A)
              .send()
              .join()
              .getProcesses()
              .stream()
              .map(Process::getProcessDefinitionKey)
              .findFirst()
              .orElseThrow();
    }

    try (final var client = createZeebeClient(ZEEBE_CLIENT_ID_TENANT_A_AND_B)) {
      // when
      final Future<ProcessInstanceEvent> result =
          client
              .newCreateInstanceCommand()
              .processDefinitionKey(processDefinitionKey)
              .tenantId(TENANT_B)
              .send();

      // then
      assertThat(result)
          .failsWithin(Duration.ofSeconds(10))
          .withThrowableThat()
          .describedAs("Process definition should exist for tenant-a but not for tenant-b")
          .withMessageContaining("NOT_FOUND")
          .withMessageContaining("Expected to find process definition with key")
          .withMessageContaining("but none found");
    }
  }

  @Test
  void shouldStartProcessWhenPublishingMessageForTenant() {
    // given
    final String messageName = "message";
    process =
        Bpmn.createExecutableProcess(processId).startEvent().message(messageName).endEvent().done();
    try (final var client = createZeebeClient(ZEEBE_CLIENT_ID_TENANT_A)) {
      client
          .newDeployResourceCommand()
          .addProcessModel(process, "process.bpmn")
          .tenantId(TENANT_A)
          .send()
          .join();

      // when
      final Future<PublishMessageResponse> result =
          client
              .newPublishMessageCommand()
              .messageName(messageName)
              .correlationKey("")
              .tenantId(TENANT_A)
              .send();

      // then
      assertThat(result)
          .describedAs(
              "Expect that message can be published as the client has access process of tenant-a")
          .succeedsWithin(Duration.ofSeconds(10));
    }
  }

  @Test
  void shouldDenyPublishMessageWhenUnauthorized() {
    // given
    final String messageName = "message";
    process =
        Bpmn.createExecutableProcess(processId).startEvent().message(messageName).endEvent().done();
    try (final var client = createZeebeClient(ZEEBE_CLIENT_ID_TENANT_A)) {
      client
          .newDeployResourceCommand()
          .addProcessModel(process, "process.bpmn")
          .tenantId(TENANT_A)
          .send()
          .join();

      // when
      final Future<PublishMessageResponse> result =
          client
              .newPublishMessageCommand()
              .messageName(messageName)
              .correlationKey("")
              .tenantId(TENANT_B)
              .send();

      // then
      assertThat(result)
          .failsWithin(Duration.ofSeconds(10))
          .withThrowableThat()
          .withMessageContaining("PERMISSION_DENIED")
          .withMessageContaining(
              "Expected to handle gRPC request PublishMessage with tenant identifier 'tenant-b'")
          .withMessageContaining("but tenant is not authorized to perform this request");
    }
  }

  @Test
  void shouldActivateJobForTenant() {
    // given
    try (final var client = createZeebeClient(ZEEBE_CLIENT_ID_TENANT_A)) {
      client
          .newDeployResourceCommand()
          .addProcessModel(process, "process.bpmn")
          .tenantId(TENANT_A)
          .send()
          .join();
      client
          .newCreateInstanceCommand()
          .bpmnProcessId(processId)
          .latestVersion()
          .tenantId(TENANT_A)
          .send()
          .join();

      // when
      final Future<ActivateJobsResponse> result =
          client
              .newActivateJobsCommand()
              .jobType("type")
              .maxJobsToActivate(1)
              .tenantId(TENANT_A)
              .send();

      // then
      assertThat(result)
          .describedAs(
              "Expect that job can be activated as the client has access process of tenant-a")
          .succeedsWithin(Duration.ofSeconds(10));
    }
  }

  @Test
  void shouldDenyActivateJobWhenUnauthorized() {
    // given
    try (final var client = createZeebeClient(ZEEBE_CLIENT_ID_TENANT_A)) {
      client
          .newDeployResourceCommand()
          .addProcessModel(process, "process.bpmn")
          .tenantId(TENANT_A)
          .send()
          .join();
      client
          .newCreateInstanceCommand()
          .bpmnProcessId(processId)
          .latestVersion()
          .tenantId(TENANT_A)
          .send()
          .join();
    }

    // when
    try (final var client = createZeebeClient(ZEEBE_CLIENT_ID_TENANT_B)) {
      final Future<ActivateJobsResponse> result =
          client
              .newActivateJobsCommand()
              .jobType("type")
              .maxJobsToActivate(1)
              .tenantId(TENANT_A)
              .send();

      // then
      assertThat(result)
          .failsWithin(Duration.ofSeconds(10))
          .withThrowableThat()
          .withMessageContaining("PERMISSION_DENIED")
          .withMessageContaining(
              "Expected to handle gRPC request ActivateJobs with tenant identifier 'tenant-a'")
          .withMessageContaining("but tenant is not authorized to perform this request");
    }
  }

  @Test
  void shouldCompleteJobForTenant() {
    // given
    try (final var client = createZeebeClient(ZEEBE_CLIENT_ID_TENANT_A)) {
      client
          .newDeployResourceCommand()
          .addProcessModel(process, "process.bpmn")
          .tenantId(TENANT_A)
          .send()
          .join();
      client
          .newCreateInstanceCommand()
          .bpmnProcessId(processId)
          .latestVersion()
          .tenantId(TENANT_A)
          .send()
          .join();

      final var activatedJob =
          client
              .newActivateJobsCommand()
              .jobType("type")
              .maxJobsToActivate(1)
              .tenantId(TENANT_A)
              .send()
              .join()
              .getJobs()
              .get(0);

      // when
      final Future<CompleteJobResponse> result = client.newCompleteCommand(activatedJob).send();

      // then
      assertThat(result)
          .describedAs(
              "Expect that job can be competed as the client has access process of tenant-a")
          .succeedsWithin(Duration.ofSeconds(10));
    }
  }

  @Test
  void shouldNotFindJobWhenUnauthorized() {
    // given
    final ActivatedJob activatedJob;
    try (final var client = createZeebeClient(ZEEBE_CLIENT_ID_TENANT_A)) {
      client
          .newDeployResourceCommand()
          .addProcessModel(process, "process.bpmn")
          .tenantId(TENANT_A)
          .send()
          .join();
      client
          .newCreateInstanceCommand()
          .bpmnProcessId(processId)
          .latestVersion()
          .tenantId(TENANT_A)
          .send()
          .join();
      activatedJob =
          client
              .newActivateJobsCommand()
              .jobType("type")
              .maxJobsToActivate(1)
              .tenantId(TENANT_A)
              .send()
              .join()
              .getJobs()
              .get(0);
    }

    // when
    try (final var client = createZeebeClient(ZEEBE_CLIENT_ID_TENANT_B)) {
      final Future<CompleteJobResponse> result = client.newCompleteCommand(activatedJob).send();

      // then
      assertThat(result)
          .failsWithin(Duration.ofSeconds(10))
          .withThrowableThat()
          .withMessageContaining("NOT_FOUND")
          .withMessageContaining(
              "Command 'COMPLETE' rejected with code 'NOT_FOUND': Expected to update retries for job with key '%d', but no such job was found"
                  .formatted(activatedJob.getKey()));
    }
  }

  @Test
  void shouldResolveIncidentForTenant() {
    // given
    process =
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .zeebeOutputExpression("assert(foo, foo != null)", "target")
            .endEvent()
            .done();
    try (final var client = createZeebeClient(ZEEBE_CLIENT_ID_TENANT_A)) {
      client
          .newDeployResourceCommand()
          .addProcessModel(process, "process.bpmn")
          .tenantId(TENANT_A)
          .send()
          .join();
      client
          .newCreateInstanceCommand()
          .bpmnProcessId(processId)
          .latestVersion()
          .tenantId(TENANT_A)
          .send()
          .join();

      final var incidentKey =
          RecordingExporter.incidentRecords().withBpmnProcessId(processId).getFirst().getKey();

      // when
      final Future<ResolveIncidentResponse> result =
          client.newResolveIncidentCommand(incidentKey).send();

      // then
      assertThat(result)
          .describedAs(
              "Expect that incident can be resolved as the client has access process of tenant-a")
          .succeedsWithin(Duration.ofSeconds(10));
    }
  }

  @Test
  void shouldNotFindIncidentForTenantWhenUnauthorized() {
    // given
    process =
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .zeebeOutputExpression("assert(foo, foo != null)", "target")
            .endEvent()
            .done();
    final long incidentKey;
    try (final var client = createZeebeClient(ZEEBE_CLIENT_ID_TENANT_A)) {
      client
          .newDeployResourceCommand()
          .addProcessModel(process, "process.bpmn")
          .tenantId(TENANT_A)
          .send()
          .join();
      client
          .newCreateInstanceCommand()
          .bpmnProcessId(processId)
          .latestVersion()
          .tenantId(TENANT_A)
          .send()
          .join();

      incidentKey =
          RecordingExporter.incidentRecords().withBpmnProcessId(processId).getFirst().getKey();
    }

    try (final var client = createZeebeClient(ZEEBE_CLIENT_ID_TENANT_B)) {
      // when
      final Future<ResolveIncidentResponse> result =
          client.newResolveIncidentCommand(incidentKey).send();

      // then
      assertThat(result)
          .failsWithin(Duration.ofSeconds(10))
          .withThrowableThat()
          .withMessageContaining("NOT_FOUND")
          .withMessageContaining(
              "Command 'RESOLVE' rejected with code 'NOT_FOUND': Expected to resolve incident with key '%d', but no such incident was found"
                  .formatted(incidentKey));
    }
  }

  @Test
  void shouldAllowModifyProcessInstanceForDefaultTenant() {
    try (final var client = createZeebeClient(ZEEBE_CLIENT_ID_TENANT_DEFAULT)) {
      // given
      client.newDeployResourceCommand().addProcessModel(process, "process.bpmn").send().join();

      final long processInstanceKey =
          client
              .newCreateInstanceCommand()
              .bpmnProcessId(processId)
              .latestVersion()
              .send()
              .join()
              .getProcessInstanceKey();

      // when
      final Future<ModifyProcessInstanceResponse> response =
          client.newModifyProcessInstanceCommand(processInstanceKey).activateElement("task").send();

      // then
      assertThat(response).succeedsWithin(Duration.ofSeconds(10));
    }
  }

  @Test
  void shouldAllowModifyProcessInstanceForOtherTenant() {
    try (final var client = createZeebeClient(ZEEBE_CLIENT_ID_TENANT_A)) {
      // given
      client
          .newDeployResourceCommand()
          .addProcessModel(process, "process.bpmn")
          .tenantId(TENANT_A)
          .send()
          .join();

      final long processInstanceKey =
          client
              .newCreateInstanceCommand()
              .bpmnProcessId(processId)
              .latestVersion()
              .tenantId(TENANT_A)
              .send()
              .join()
              .getProcessInstanceKey();

      // when
      final Future<ModifyProcessInstanceResponse> response =
          client.newModifyProcessInstanceCommand(processInstanceKey).activateElement("task").send();

      // then
      assertThat(response).succeedsWithin(Duration.ofSeconds(10));
    }
  }

  @Test
  void shouldRejectModifyProcessInstanceForUnauthorizedTenant() {
    final long processInstanceKey;
    try (final var client = createZeebeClient(ZEEBE_CLIENT_ID_TENANT_A)) {
      // given
      client
          .newDeployResourceCommand()
          .addProcessModel(process, "process.bpmn")
          .tenantId(TENANT_A)
          .send()
          .join();

      processInstanceKey =
          client
              .newCreateInstanceCommand()
              .bpmnProcessId(processId)
              .latestVersion()
              .tenantId(TENANT_A)
              .send()
              .join()
              .getProcessInstanceKey();
    }

    // when
    try (final var client = createZeebeClient(ZEEBE_CLIENT_ID_TENANT_B)) {
      final Future<ModifyProcessInstanceResponse> response =
          client.newModifyProcessInstanceCommand(processInstanceKey).activateElement("task").send();

      // then
      assertThat(response)
          .failsWithin(Duration.ofSeconds(10))
          .withThrowableThat()
          .withMessageContaining("NOT_FOUND")
          .withMessageContaining(
              "Expected to modify process instance but no process instance found with key");
    }
  }

  @Test
  void shouldAllowEvaluateDecisionForDefaultTenant() {
    try (final var client = createZeebeClient(ZEEBE_CLIENT_ID_TENANT_DEFAULT)) {
      // given
      client
          .newDeployResourceCommand()
          .addResourceFromClasspath("dmn/decision-table.dmn")
          .send()
          .join();

      // when
      final Future<EvaluateDecisionResponse> response =
          client
              .newEvaluateDecisionCommand()
              .decisionId("jedi_or_sith")
              .variable("lightsaberColor", "blue")
              .send();
      // then
      assertThat(response).succeedsWithin(Duration.ofSeconds(10));
    }
  }

  @Test
  void shouldAllowEvaluateDecisionForCustomTenant() {
    try (final var client = createZeebeClient(ZEEBE_CLIENT_ID_TENANT_DEFAULT)) {
      // given
      client
          .newDeployResourceCommand()
          .addResourceFromClasspath("dmn/decision-table.dmn")
          .tenantId(TENANT_A)
          .send()
          .join();

      // when
      final Future<EvaluateDecisionResponse> response =
          client
              .newEvaluateDecisionCommand()
              .decisionId("jedi_or_sith")
              .variable("lightsaberColor", "blue")
              .tenantId(TENANT_A)
              .send();
      // then
      assertThat(response).succeedsWithin(Duration.ofSeconds(10));
    }
  }

  @Test
  void shouldDenyEvaluateDecisionForCustomTenant() {
    try (final var client = createZeebeClient(ZEEBE_CLIENT_ID_TENANT_DEFAULT)) {
      // given
      client
          .newDeployResourceCommand()
          .addResourceFromClasspath("dmn/decision-table.dmn")
          .tenantId(TENANT_A)
          .send()
          .join();

      // when
      final Future<EvaluateDecisionResponse> response =
          client
              .newEvaluateDecisionCommand()
              .decisionId("jedi_or_sith")
              .variable("lightsaberColor", "blue")
              .tenantId(TENANT_B)
              .send();
      // then
      assertThat(response)
          .failsWithin(Duration.ofSeconds(10))
          .withThrowableThat()
          .withMessageContaining("PERMISSION_DENIED")
          .withMessageContaining(
              "Expected to handle gRPC request EvaluateDecision with tenant identifier 'tenant-b'")
          .withMessageContaining("but tenant is not authorized to perform this request");
    }
  }

  @Test
  void shouldStartInstanceWhenBroadcastSignalForTenant() {
    final String signalName = "signal";
    process =
        Bpmn.createExecutableProcess(processId).startEvent().signal(signalName).endEvent().done();
    try (final var client = createZeebeClient(ZEEBE_CLIENT_ID_TENANT_A)) {
      // given
      client
          .newDeployResourceCommand()
          .addProcessModel(process, "process.bpmn")
          .tenantId(TENANT_A)
          .send()
          .join();

      // when
      final Future<BroadcastSignalResponse> result =
          client.newBroadcastSignalCommand().signalName(signalName).tenantId(TENANT_A).send();

      // then
      assertThat(result)
          .describedAs(
              "Expect that signal can be broadcast as the client has access to the process of 'tenant-a'")
          .succeedsWithin(Duration.ofSeconds(20));
    }
  }

  @Test
  void shouldDenyBroadcastSignalWhenUnauthorized() {
    final String signalName = "signal";
    process =
        Bpmn.createExecutableProcess(processId).startEvent().signal(signalName).endEvent().done();
    try (final var client = createZeebeClient(ZEEBE_CLIENT_ID_TENANT_A)) {
      // given
      client
          .newDeployResourceCommand()
          .addProcessModel(process, "process.bpmn")
          .tenantId(TENANT_A)
          .send()
          .join();

      // when
      final Future<BroadcastSignalResponse> result =
          client.newBroadcastSignalCommand().signalName(signalName).tenantId(TENANT_B).send();

      // then
      assertThat(result)
          .failsWithin(Duration.ofSeconds(10))
          .withThrowableThat()
          .withMessageContaining("PERMISSION_DENIED")
          .withMessageContaining(
              "Expected to handle gRPC request BroadcastSignal with tenant identifier 'tenant-b'")
          .withMessageContaining("but tenant is not authorized to perform this request");
    }
  }

  /**
   * Awaits the presence of the Camunda realm and openid keys on the keycloak container. Once
   * Keycloak and Identity booted up, Identity will eventually configure the Camunda Realm on
   * Keycloak.
   */
  private static void awaitCamundaRealmAvailabilityOnKeycloak() {
    final var httpClient = HttpClient.newHttpClient();
    final HttpRequest request =
        HttpRequest.newBuilder()
            .uri(
                URI.create(
                    "http://localhost:%d%s/protocol/openid-connect/certs"
                        .formatted(KEYCLOAK.getFirstMappedPort(), KEYCLOAK_PATH_CAMUNDA_REALM)))
            .build();
    Awaitility.await()
        .atMost(Duration.ofSeconds(120))
        .pollInterval(Duration.ofSeconds(5))
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final HttpResponse<String> response =
                  httpClient.send(request, BodyHandlers.ofString());
              assertThat(response.statusCode()).isEqualTo(200);
            });
  }

  /**
   * Creates a new Zeebe Client with the given client ID such that Identity can provide the
   * associated tenant IDs. The credentials are cached separately for each test case.
   *
   * @param clientId The client ID to use for the new Zeebe Client
   * @return A new Zeebe Client to use in these tests
   */
  private static ZeebeClient createZeebeClient(final String clientId) {
    return ZEEBE
        .newClientBuilder()
        .credentialsProvider(
            new OAuthCredentialsProviderBuilder()
                .clientId(clientId)
                .clientSecret(ZEEBE_CLIENT_SECRET)
                .audience(ZEEBE_CLIENT_AUDIENCE)
                .authorizationServerUrl(
                    "http://localhost:%d%s/protocol/openid-connect/token"
                        .formatted(KEYCLOAK.getFirstMappedPort(), KEYCLOAK_PATH_CAMUNDA_REALM))
                .credentialsCachePath(credentialsCacheDir.resolve(clientId).toString())
                .build())
        .defaultRequestTimeout(Duration.ofSeconds(10))
        .build();
  }

  /**
   * Sets up the association between the given tenant IDs and the given client ID in Keycloak and
   * Identity.
   *
   * @param tenantIds The tenant ids to associate with the client
   * @param clientId The client id to associate the tenants with
   * @throws Exception If an error occurs while performing the database operations
   */
  private static void associateTenantsWithClient(
      final List<String> tenantIds, final String clientId) throws Exception {

    try (final PostgresHelper postgres = new PostgresHelper()) {
      final String accessRuleId;
      // Create access rule for service account
      try (final var resultSet =
          postgres.executeQuery(
              """
              INSERT INTO access_rules \
                (member_id, member_type, global) \
              VALUES ('%s', 'APPLICATION', false) \
              ON CONFLICT DO NOTHING \
              RETURNING id"""
                  .formatted(clientId))) {
        if (!resultSet.next()) {
          throw new IllegalStateException(
              "Expected to find access rule associated to service account.");
        }
        accessRuleId = resultSet.getString(1);
      }

      // Create tenant(s) if not already existing,
      // using tenantId for both id and name
      tenantIds.forEach(
          (tenantId) ->
              postgres.execute(
                  """
                  INSERT INTO tenants \
                    (name, tenant_id) \
                  VALUES ('%s', '%s') \
                  ON CONFLICT DO NOTHING"""
                      .formatted(tenantId, tenantId)));

      // Connect tenants to access rule
      tenantIds.forEach(
          tenantId ->
              postgres.execute(
                  """
                      INSERT INTO access_rules_tenants \
                        (tenant_id, access_rule_id) \
                      VALUES ('%s', '%s') \
                      ON CONFLICT DO NOTHING"""
                      .formatted(tenantId, accessRuleId)));
    }
  }

  /**
   * Helper class to perform queries against the Postgres database. It will automatically create and
   * close the connection. To be used within a try-with-resources block.
   */
  private static final class PostgresHelper implements AutoCloseable {
    private final Supplier<Connection> connector;
    private Connection connection;

    private PostgresHelper() {
      final String jdbcUrl = POSTGRES.getJdbcUrl();
      final String username = POSTGRES.getUsername();
      final String password = POSTGRES.getPassword();
      connector =
          () -> {
            try {
              return DriverManager.getConnection(jdbcUrl, username, password);
            } catch (final SQLException e) {
              throw new RuntimeException(e);
            }
          };
    }

    private ResultSet executeQuery(final String query) {
      if (connection == null) {
        connection = connector.get();
      }
      try {
        final var statement = connection.createStatement();
        return statement.executeQuery(query);
      } catch (final SQLException e) {
        throw new RuntimeException(e);
      }
    }

    private void execute(final String query) {
      if (connection == null) {
        connection = connector.get();
      }
      try (final var statement = connection.createStatement()) {
        statement.execute(query);
      } catch (final SQLException e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public void close() throws Exception {
      if (connection != null) {
        connection.close();
      }
    }
  }
}
