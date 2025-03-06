/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.multitenancy;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.TopologyRequestStep1;
import io.camunda.client.impl.basicauth.BasicAuthCredentialsProviderBuilder;
import io.camunda.security.configuration.ConfiguredUser;
import io.camunda.zeebe.it.util.AuthorizationsUtil;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.testcontainers.TestSearchContainers;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@ZeebeIntegration
public class MultiTenancyOverIdentityIT {

  @Container
  private static final ElasticsearchContainer CONTAINER =
      TestSearchContainers.createDefeaultElasticsearchContainer();

  private static final String DEFAULT_TENANT = TenantOwned.DEFAULT_TENANT_IDENTIFIER;
  private static final String TENANT_A = "tenanA";
  private static final String TENANT_B = "tenantB";
  private static final String USER_TENANT_A = "userTenantA";
  private static final String USER_TENANT_B = "userTenantB";
  private static final String USER_TENANT_A_AND_B = "userTenantAB";
  private static final String USER_TENANT_A_WITHOUT_DEFAULT_TENANT = "userTenantANoDefault";

  @TestZeebe(autoStart = false)
  private static final TestStandaloneBroker BROKER =
      new TestStandaloneBroker()
          .withRecordingExporter(true)
          .withBasicAuth()
          .withMultiTenancyEnabled()
          .withAuthenticatedAccess()
          .withSecurityConfig(
              cfg ->
                  cfg.getInitialization()
                      .setUsers(
                          List.of(
                              new ConfiguredUser(
                                  USER_TENANT_A, USER_TENANT_A, USER_TENANT_A, "test@camunda.com"),
                              new ConfiguredUser(
                                  USER_TENANT_B, USER_TENANT_B, USER_TENANT_B, "test@camunda.com"),
                              new ConfiguredUser(
                                  USER_TENANT_A_AND_B,
                                  USER_TENANT_A_AND_B,
                                  USER_TENANT_A_AND_B,
                                  "test@camunda.com"),
                              new ConfiguredUser(
                                  USER_TENANT_A_WITHOUT_DEFAULT_TENANT,
                                  USER_TENANT_A_WITHOUT_DEFAULT_TENANT,
                                  USER_TENANT_A_WITHOUT_DEFAULT_TENANT,
                                  "test@camunda.com"))));

  private String processId;
  private String migratedProcessId;
  private BpmnModelInstance process;
  private BpmnModelInstance migratedProcess;

  @BeforeAll
  static void init() {
    BROKER.withCamundaExporter("http://" + CONTAINER.getHttpHostAddress()).start();

    // We can do the setup with any user. We pick user A for convenience.
    try (final var client = createCamundaClient(USER_TENANT_A)) {
      final var authUtil = new AuthorizationsUtil(BROKER, client, CONTAINER.getHttpHostAddress());
      authUtil.awaitUserExistsInElasticsearch(USER_TENANT_A);
      authUtil.awaitUserExistsInElasticsearch(USER_TENANT_B);
      authUtil.awaitUserExistsInElasticsearch(USER_TENANT_A_AND_B);
      authUtil.awaitUserExistsInElasticsearch(USER_TENANT_A_WITHOUT_DEFAULT_TENANT);

      assignUsersToTenant(
          client, DEFAULT_TENANT, USER_TENANT_A, USER_TENANT_B, USER_TENANT_A_AND_B);
      createTenantAndAssignUsers(
          client,
          TENANT_A,
          USER_TENANT_A,
          USER_TENANT_A_AND_B,
          USER_TENANT_A_WITHOUT_DEFAULT_TENANT);
      createTenantAndAssignUsers(client, TENANT_B, USER_TENANT_B, USER_TENANT_A_AND_B);
    }
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

    migratedProcessId = Strings.newRandomValidBpmnId();
    migratedProcess =
        Bpmn.createExecutableProcess(migratedProcessId)
            .startEvent()
            .serviceTask("migrated-task", b -> b.zeebeJobType("type"))
            .endEvent()
            .done();
  }

  @ParameterizedTest
  @MethodSource("provideTopologyCases")
  void shouldAuthorizeTopologyRequestWithTenantAccess(
      final UnaryOperator<TopologyRequestStep1> apiPicker) {
    try (final var client = createCamundaClient(USER_TENANT_A)) {
      // when
      final var topology = apiPicker.apply(client.newTopologyRequest()).send().join();

      // then
      assertThat(topology.getBrokers()).hasSize(1);
    }
  }

  //
  //  @ParameterizedTest
  //  @MethodSource("provideTopologyCases")
  //  void shouldAuthorizeTopologyRequestWithoutTenantAccess(
  //      final UnaryOperator<TopologyRequestStep1> apiPicker) {
  //    try (final var client = createZeebeClient(ZEEBE_CLIENT_ID_WITHOUT_TENANT)) {
  //      // when
  //      final var topology = apiPicker.apply(client.newTopologyRequest()).send().join();
  //
  //      // then
  //      assertThat(topology.getBrokers()).hasSize(1);
  //    }
  //  }
  //
  //  @Test
  //  void shouldAuthorizeDeployProcess() {
  //    // given
  //    try (final var client = createZeebeClient(ZEEBE_CLIENT_ID_TENANT_A)) {
  //      // when
  //      final Future<DeploymentEvent> response =
  //          client
  //              .newDeployResourceCommand()
  //              .addProcessModel(process, "process.bpmn")
  //              .tenantId(TENANT_A)
  //              .send();
  //
  //      // then
  //      assertThat(response)
  //          .describedAs("Expect that process can be deployed for tenant-a")
  //          .succeedsWithin(Duration.ofSeconds(10));
  //    }
  //  }
  //
  //  @Test
  //  void shouldDenyDeployResourceWhenUnauthorized() {
  //    // given
  //    try (final var client = createZeebeClient(ZEEBE_CLIENT_ID_TENANT_A)) {
  //      // when
  //      final Future<DeploymentEvent> result =
  //          client
  //              .newDeployResourceCommand()
  //              .addProcessModel(process, "process.bpmn")
  //              .tenantId(TENANT_B)
  //              .send();
  //
  //      // then
  //      assertThat(result)
  //          .failsWithin(Duration.ofSeconds(10))
  //          .withThrowableThat()
  //          .withMessageContaining("PERMISSION_DENIED")
  //          .withMessageContaining(
  //              "Expected to handle gRPC request DeployResource with tenant identifier
  // 'tenant-b'")
  //          .withMessageContaining("but tenant is not authorized to perform this request");
  //    }
  //  }
  //
  //  @Test
  //  void shouldDenyDeployResourceWhenUnauthorizedForDefaultTenant() {
  //    // given
  //    try (final var client = createZeebeClient(ZEEBE_CLIENT_ID_TENANT_A_WITHOUT_DEFAULT_TENANT))
  // {
  //      // when
  //      final Future<DeploymentEvent> result =
  //          client
  //              .newDeployResourceCommand()
  //              .addProcessModel(process, "process.bpmn")
  //              .tenantId(DEFAULT_TENANT)
  //              .send();
  //
  //      // then
  //      assertThat(result)
  //          .failsWithin(Duration.ofSeconds(10))
  //          .withThrowableThat()
  //          .withMessageContaining("PERMISSION_DENIED")
  //          .withMessageContaining(
  //              "Expected to handle gRPC request DeployResource with tenant identifier
  // '<default>'")
  //          .withMessageContaining("but tenant is not authorized to perform this request");
  //    }
  //  }
  //
  //  @SuppressWarnings("deprecation")
  //  @Test
  //  void shouldDenyDeployProcessWhenUnauthorizedForDefaultTenant() {
  //    // given
  //    try (final var client = createZeebeClient(ZEEBE_CLIENT_ID_TENANT_A_WITHOUT_DEFAULT_TENANT))
  // {
  //      // when
  //      // note that deploy process command is always only for the default tenant
  //      final Future<DeploymentEvent> result =
  //          client.newDeployCommand().addProcessModel(process, "process.bpmn").send();
  //
  //      // then
  //      assertThat(result)
  //          .failsWithin(Duration.ofSeconds(10))
  //          .withThrowableThat()
  //          .withMessageContaining("PERMISSION_DENIED")
  //          .withMessageContaining(
  //              "Expected to handle gRPC request DeployProcess with tenant identifier
  // '<default>'")
  //          .withMessageContaining("but tenant is not authorized to perform this request");
  //    }
  //  }
  //
  //  @Test
  //  void shouldAuthorizeDeleteResource() throws ExecutionException, InterruptedException {
  //    // given
  //    try (final var client = createZeebeClient(ZEEBE_CLIENT_ID_TENANT_A)) {
  //      final Future<DeploymentEvent> deploymentResponse =
  //          client
  //              .newDeployResourceCommand()
  //              .addProcessModel(process, "process.bpmn")
  //              .tenantId(TENANT_A)
  //              .send();
  //      assertThat(deploymentResponse)
  //          .describedAs("Expect that process was deployed for tenant-a")
  //          .succeedsWithin(Duration.ofSeconds(10));
  //      final long resourceKey =
  //          deploymentResponse.get().getProcesses().get(0).getProcessDefinitionKey();
  //
  //      // when
  //      final Future<DeleteResourceResponse> response =
  //          client.newDeleteResourceCommand(resourceKey).send();
  //
  //      // then
  //      assertThat(response)
  //          .describedAs("Expect that process can be deleted for tenant-a")
  //          .succeedsWithin(Duration.ofSeconds(10));
  //    }
  //  }
  //
  //  @Test
  //  void shouldDenyDeleteResourceWhenUnauthorized() throws ExecutionException,
  // InterruptedException {
  //    // given
  //    final long resourceKey;
  //    try (final var client = createZeebeClient(ZEEBE_CLIENT_ID_TENANT_A)) {
  //      final Future<DeploymentEvent> deploymentResponse =
  //          client
  //              .newDeployResourceCommand()
  //              .addProcessModel(process, "process.bpmn")
  //              .tenantId(TENANT_A)
  //              .send();
  //      assertThat(deploymentResponse)
  //          .describedAs("Expect that process was deployed for tenant-a")
  //          .succeedsWithin(Duration.ofSeconds(10));
  //      resourceKey = deploymentResponse.get().getProcesses().get(0).getProcessDefinitionKey();
  //    }
  //
  //    // when
  //    try (final var client = createZeebeClient(ZEEBE_CLIENT_ID_TENANT_B)) {
  //      final Future<DeleteResourceResponse> response =
  //          client.newDeleteResourceCommand(resourceKey).send();
  //
  //      // then
  //      assertThat(response)
  //          .failsWithin(Duration.ofSeconds(10))
  //          .withThrowableThat()
  //          .withMessageContaining("NOT_FOUND");
  //    }
  //  }
  //
  //  @Test
  //  void shouldIncrementProcessVersionPerTenant() {
  //    // given
  //    try (final var client = createZeebeClient(ZEEBE_CLIENT_ID_TENANT_A)) {
  //      client
  //          .newDeployResourceCommand()
  //          .addProcessModel(process, "process.bpmn")
  //          .tenantId(TENANT_A)
  //          .send()
  //          .join();
  //    }
  //    try (final var client = createZeebeClient(ZEEBE_CLIENT_ID_TENANT_B)) {
  //      client
  //          .newDeployResourceCommand()
  //          .addProcessModel(process, "process.bpmn")
  //          .tenantId(TENANT_B)
  //          .send()
  //          .join();
  //    }
  //
  //    try (final var client = createZeebeClient(ZEEBE_CLIENT_ID_TENANT_B)) {
  //      // when
  //      final var processV2 = Bpmn.createExecutableProcess(processId).startEvent().done();
  //      final Future<DeploymentEvent> result =
  //          client
  //              .newDeployResourceCommand()
  //              .addProcessModel(processV2, "process.bpmn")
  //              .tenantId(TENANT_B)
  //              .send();
  //
  //      // then
  //      assertThat(result)
  //          .succeedsWithin(Duration.ofSeconds(10))
  //          .describedAs("Process version is incremented for tenant-b but not for tenant-a")
  //          .extracting(deploymentEvent -> deploymentEvent.getProcesses().get(0))
  //          .extracting(Process::getVersion, Process::getTenantId)
  //          .containsExactly(2, TENANT_B);
  //    }
  //  }
  //
  //  @Test
  //  void shouldAuthorizeCreateProcessInstance() {
  //    // given
  //    final long processDefinitionKey;
  //    try (final var client = createZeebeClient(ZEEBE_CLIENT_ID_TENANT_A_AND_B)) {
  //      processDefinitionKey =
  //          client
  //              .newDeployResourceCommand()
  //              .addProcessModel(process, "process.bpmn")
  //              .tenantId(TENANT_A)
  //              .send()
  //              .join()
  //              .getProcesses()
  //              .stream()
  //              .map(Process::getProcessDefinitionKey)
  //              .findFirst()
  //              .orElseThrow();
  //
  //      // when
  //      final Future<ProcessInstanceEvent> result =
  //          client
  //              .newCreateInstanceCommand()
  //              .processDefinitionKey(processDefinitionKey)
  //              .tenantId(TENANT_A)
  //              .send();
  //
  //      // then
  //      assertThat(result)
  //          .describedAs(
  //              "Expect that process instance can be created as the client has access process of
  // tenant-a")
  //          .succeedsWithin(Duration.ofSeconds(10));
  //    }
  //  }
  //
  //  @Test
  //  void shouldNotFindOtherTenantsProcessById() {
  //    // given
  //    try (final var client = createZeebeClient(ZEEBE_CLIENT_ID_TENANT_A)) {
  //      client
  //          .newDeployResourceCommand()
  //          .addProcessModel(process, "process.bpmn")
  //          .tenantId(TENANT_A)
  //          .send()
  //          .join();
  //    }
  //
  //    try (final var client = createZeebeClient(ZEEBE_CLIENT_ID_TENANT_B)) {
  //      // when
  //      final Future<ProcessInstanceEvent> result =
  //          client
  //              .newCreateInstanceCommand()
  //              .bpmnProcessId(processId)
  //              .latestVersion()
  //              .tenantId(TENANT_B)
  //              .send();
  //
  //      // then
  //      assertThat(result)
  //          .failsWithin(Duration.ofSeconds(10))
  //          .withThrowableThat()
  //          .describedAs("Process definition should exist for tenant-a but not for tenant-b")
  //          .withMessageContaining("NOT_FOUND")
  //          .withMessageContaining("Expected to find process definition with process ID")
  //          .withMessageContaining("but none found");
  //    }
  //  }
  //
  //  @Test
  //  void shouldNotFindOtherTenantsProcessByKey() {
  //    // given
  //    final long processDefinitionKey;
  //    try (final var client = createZeebeClient(ZEEBE_CLIENT_ID_TENANT_A)) {
  //      processDefinitionKey =
  //          client
  //              .newDeployResourceCommand()
  //              .addProcessModel(process, "process.bpmn")
  //              .tenantId(TENANT_A)
  //              .send()
  //              .join()
  //              .getProcesses()
  //              .stream()
  //              .map(Process::getProcessDefinitionKey)
  //              .findFirst()
  //              .orElseThrow();
  //    }
  //
  //    try (final var client = createZeebeClient(ZEEBE_CLIENT_ID_TENANT_B)) {
  //      // when
  //      final Future<ProcessInstanceEvent> result =
  //          client
  //              .newCreateInstanceCommand()
  //              .processDefinitionKey(processDefinitionKey)
  //              .tenantId(TENANT_B)
  //              .send();
  //
  //      // then
  //      assertThat(result)
  //          .failsWithin(Duration.ofSeconds(10))
  //          .withThrowableThat()
  //          .describedAs("Process definition should exist for tenant-a but not for tenant-b")
  //          .withMessageContaining("NOT_FOUND")
  //          .withMessageContaining("Expected to find process definition with key")
  //          .withMessageContaining("but none found");
  //    }
  //  }
  //
  //  @Test
  //  void shouldNotFindOtherTenantsProcessInCallActivity() {
  //    // given
  //    try (final var client = createZeebeClient(ZEEBE_CLIENT_ID_TENANT_A)) {
  //      client
  //          .newDeployResourceCommand()
  //          .addProcessModel(process, "process.bpmn")
  //          .tenantId(TENANT_A)
  //          .send()
  //          .join();
  //    }
  //
  //    try (final var client = createZeebeClient(ZEEBE_CLIENT_ID_TENANT_B)) {
  //      client
  //          .newDeployResourceCommand()
  //          .addProcessModel(
  //              Bpmn.createExecutableProcess("parent")
  //                  .startEvent()
  //                  .callActivity("call", c -> c.zeebeProcessId(processId))
  //                  .endEvent()
  //                  .done(),
  //              "parent.bpmn")
  //          .tenantId(TENANT_B)
  //          .send()
  //          .join();
  //    }
  //
  //    try (final var client = createZeebeClient(ZEEBE_CLIENT_ID_TENANT_B)) {
  //      // when
  //      client
  //          .newCreateInstanceCommand()
  //          .bpmnProcessId("parent")
  //          .latestVersion()
  //          .tenantId(TENANT_B)
  //          .send();
  //
  //      // then
  //      Assertions.assertThat(
  //
  // RecordingExporter.incidentRecords().withBpmnProcessId("parent").getFirst().getValue())
  //          .hasErrorMessage(
  //              "Expected process with BPMN process id '%s' to be deployed, but not found."
  //                  .formatted(processId));
  //    }
  //  }
  //
  //  /**
  //   * This test case may become obsolete when we allow shared processes definitions across
  // tenants.
  //   */
  //  @Test
  //  void shouldNotFindOtherTenantsProcessEvenWhenClientIsAuthorizedForTenant() {
  //    // given
  //    final long processDefinitionKey;
  //    try (final var client = createZeebeClient(ZEEBE_CLIENT_ID_TENANT_A_AND_B)) {
  //      processDefinitionKey =
  //          client
  //              .newDeployResourceCommand()
  //              .addProcessModel(process, "process.bpmn")
  //              .tenantId(TENANT_A)
  //              .send()
  //              .join()
  //              .getProcesses()
  //              .stream()
  //              .map(Process::getProcessDefinitionKey)
  //              .findFirst()
  //              .orElseThrow();
  //    }
  //
  //    try (final var client = createZeebeClient(ZEEBE_CLIENT_ID_TENANT_A_AND_B)) {
  //      // when
  //      final Future<ProcessInstanceEvent> result =
  //          client
  //              .newCreateInstanceCommand()
  //              .processDefinitionKey(processDefinitionKey)
  //              .tenantId(TENANT_B)
  //              .send();
  //
  //      // then
  //      assertThat(result)
  //          .failsWithin(Duration.ofSeconds(10))
  //          .withThrowableThat()
  //          .describedAs("Process definition should exist for tenant-a but not for tenant-b")
  //          .withMessageContaining("NOT_FOUND")
  //          .withMessageContaining("Expected to find process definition with key")
  //          .withMessageContaining("but none found");
  //    }
  //  }
  //
  //  @Test
  //  void shouldStartProcessWhenPublishingMessageForTenant() {
  //    // given
  //    final String messageName = "message";
  //    process =
  //
  // Bpmn.createExecutableProcess(processId).startEvent().message(messageName).endEvent().done();
  //    try (final var client = createZeebeClient(ZEEBE_CLIENT_ID_TENANT_A)) {
  //      client
  //          .newDeployResourceCommand()
  //          .addProcessModel(process, "process.bpmn")
  //          .tenantId(TENANT_A)
  //          .send()
  //          .join();
  //
  //      // when
  //      final Future<PublishMessageResponse> result =
  //          client
  //              .newPublishMessageCommand()
  //              .messageName(messageName)
  //              .withoutCorrelationKey()
  //              .tenantId(TENANT_A)
  //              .send();
  //
  //      // then
  //      assertThat(result)
  //          .describedAs(
  //              "Expect that message can be published as the client has access process of
  // tenant-a")
  //          .succeedsWithin(Duration.ofSeconds(10));
  //    }
  //  }
  //
  //  @Test
  //  void shouldDenyPublishMessageWhenUnauthorized() {
  //    // given
  //    final String messageName = "message";
  //    process =
  //
  // Bpmn.createExecutableProcess(processId).startEvent().message(messageName).endEvent().done();
  //    try (final var client = createZeebeClient(ZEEBE_CLIENT_ID_TENANT_A)) {
  //      client
  //          .newDeployResourceCommand()
  //          .addProcessModel(process, "process.bpmn")
  //          .tenantId(TENANT_A)
  //          .send()
  //          .join();
  //
  //      // when
  //      final Future<PublishMessageResponse> result =
  //          client
  //              .newPublishMessageCommand()
  //              .messageName(messageName)
  //              .withoutCorrelationKey()
  //              .tenantId(TENANT_B)
  //              .send();
  //
  //      // then
  //      assertThat(result)
  //          .failsWithin(Duration.ofSeconds(10))
  //          .withThrowableThat()
  //          .withMessageContaining("PERMISSION_DENIED")
  //          .withMessageContaining(
  //              "Expected to handle gRPC request PublishMessage with tenant identifier
  // 'tenant-b'")
  //          .withMessageContaining("but tenant is not authorized to perform this request");
  //    }
  //  }
  //
  //  @Test
  //  void shouldActivateJobForTenant() {
  //    // given
  //    try (final var client = createZeebeClient(ZEEBE_CLIENT_ID_TENANT_A)) {
  //      client
  //          .newDeployResourceCommand()
  //          .addProcessModel(process, "process.bpmn")
  //          .tenantId(TENANT_A)
  //          .send()
  //          .join();
  //      client
  //          .newCreateInstanceCommand()
  //          .bpmnProcessId(processId)
  //          .latestVersion()
  //          .tenantId(TENANT_A)
  //          .send()
  //          .join();
  //
  //      // when
  //      final Future<ActivateJobsResponse> result =
  //          client
  //              .newActivateJobsCommand()
  //              .jobType("type")
  //              .maxJobsToActivate(1)
  //              .tenantId(TENANT_A)
  //              .send();
  //
  //      // then
  //      assertThat(result)
  //          .describedAs(
  //              "Expect that job can be activated as the client has access process of tenant-a")
  //          .succeedsWithin(Duration.ofSeconds(10));
  //    }
  //  }
  //
  //  @Test
  //  void shouldDenyActivateJobWhenUnauthorized() {
  //    // given
  //    try (final var client = createZeebeClient(ZEEBE_CLIENT_ID_TENANT_A)) {
  //      client
  //          .newDeployResourceCommand()
  //          .addProcessModel(process, "process.bpmn")
  //          .tenantId(TENANT_A)
  //          .send()
  //          .join();
  //      client
  //          .newCreateInstanceCommand()
  //          .bpmnProcessId(processId)
  //          .latestVersion()
  //          .tenantId(TENANT_A)
  //          .send()
  //          .join();
  //    }
  //
  //    // when
  //    try (final var client = createZeebeClient(ZEEBE_CLIENT_ID_TENANT_B)) {
  //      final Future<ActivateJobsResponse> result =
  //          client
  //              .newActivateJobsCommand()
  //              .jobType("type")
  //              .maxJobsToActivate(1)
  //              .tenantId(TENANT_A)
  //              .send();
  //
  //      // then
  //      assertThat(result)
  //          .failsWithin(Duration.ofSeconds(10))
  //          .withThrowableThat()
  //          .withMessageContaining("PERMISSION_DENIED")
  //          .withMessageContaining(
  //              "Expected to handle gRPC request ActivateJobs with tenant identifier 'tenant-a'")
  //          .withMessageContaining("but tenant is not authorized to perform this request");
  //    }
  //  }
  //
  //  @Test
  //  void shouldCompleteJobForTenant() {
  //    // given
  //    try (final var client = createZeebeClient(ZEEBE_CLIENT_ID_TENANT_A)) {
  //      client
  //          .newDeployResourceCommand()
  //          .addProcessModel(process, "process.bpmn")
  //          .tenantId(TENANT_A)
  //          .send()
  //          .join();
  //      client
  //          .newCreateInstanceCommand()
  //          .bpmnProcessId(processId)
  //          .latestVersion()
  //          .tenantId(TENANT_A)
  //          .send()
  //          .join();
  //
  //      final var activatedJob =
  //          client
  //              .newActivateJobsCommand()
  //              .jobType("type")
  //              .maxJobsToActivate(1)
  //              .tenantId(TENANT_A)
  //              .send()
  //              .join()
  //              .getJobs()
  //              .get(0);
  //
  //      // when
  //      final Future<CompleteJobResponse> result = client.newCompleteCommand(activatedJob).send();
  //
  //      // then
  //      assertThat(result)
  //          .describedAs(
  //              "Expect that job can be competed as the client has access process of tenant-a")
  //          .succeedsWithin(Duration.ofSeconds(10));
  //    }
  //  }
  //
  //  @Test
  //  void shouldCompleteUserTaskForTenant() {
  //    // given
  //    try (final var client = createZeebeClient(ZEEBE_CLIENT_ID_TENANT_A)) {
  //      client
  //          .newDeployResourceCommand()
  //          .addProcessModel(process, "process.bpmn")
  //          .tenantId(TENANT_A)
  //          .send()
  //          .join();
  //      client
  //          .newCreateInstanceCommand()
  //          .bpmnProcessId(processId)
  //          .latestVersion()
  //          .tenantId(TENANT_A)
  //          .send()
  //          .join();
  //
  //      final var activatedJob =
  //          client
  //              .newActivateJobsCommand()
  //              .jobType("type")
  //              .maxJobsToActivate(1)
  //              .tenantId(TENANT_A)
  //              .send()
  //              .join()
  //              .getJobs()
  //              .get(0);
  //
  //      // when
  //      final Future<CompleteJobResponse> result = client.newCompleteCommand(activatedJob).send();
  //
  //      // then
  //      assertThat(result)
  //          .describedAs(
  //              "Expect that job can be competed as the client has access process of tenant-a")
  //          .succeedsWithin(Duration.ofSeconds(10));
  //    }
  //  }
  //
  //  @Test
  //  void shouldUpdateJobTimeoutForTenant() {
  //    // given
  //    try (final var client = createZeebeClient(ZEEBE_CLIENT_ID_TENANT_A)) {
  //      client
  //          .newDeployResourceCommand()
  //          .addProcessModel(process, "process.bpmn")
  //          .tenantId(TENANT_A)
  //          .send()
  //          .join();
  //      client
  //          .newCreateInstanceCommand()
  //          .bpmnProcessId(processId)
  //          .latestVersion()
  //          .tenantId(TENANT_A)
  //          .send()
  //          .join();
  //
  //      final var activatedJob =
  //          client
  //              .newActivateJobsCommand()
  //              .jobType("type")
  //              .maxJobsToActivate(1)
  //              .tenantId(TENANT_A)
  //              .timeout(Duration.ofMinutes(10))
  //              .send()
  //              .join()
  //              .getJobs()
  //              .get(0);
  //
  //      // when
  //      final Future<UpdateTimeoutJobResponse> result =
  //          client.newUpdateTimeoutCommand(activatedJob).timeout(Duration.ofMinutes(11)).send();
  //
  //      // then
  //      assertThat(result)
  //          .describedAs(
  //              "Expect that job timeout can be updated as the client has access process of
  // tenant-a")
  //          .succeedsWithin(Duration.ofSeconds(10));
  //    }
  //  }
  //
  //  @Test
  //  void shouldNotUpdateJobTimeoutWhenUnauthorized() {
  //    // given
  //    final ActivatedJob activatedJob;
  //    try (final var client = createZeebeClient(ZEEBE_CLIENT_ID_TENANT_A)) {
  //      client
  //          .newDeployResourceCommand()
  //          .addProcessModel(process, "process.bpmn")
  //          .tenantId(TENANT_A)
  //          .send()
  //          .join();
  //      client
  //          .newCreateInstanceCommand()
  //          .bpmnProcessId(processId)
  //          .latestVersion()
  //          .tenantId(TENANT_A)
  //          .send()
  //          .join();
  //      activatedJob =
  //          client
  //              .newActivateJobsCommand()
  //              .jobType("type")
  //              .maxJobsToActivate(1)
  //              .tenantId(TENANT_A)
  //              .timeout(Duration.ofMinutes(10))
  //              .send()
  //              .join()
  //              .getJobs()
  //              .get(0);
  //    }
  //
  //    // when
  //    try (final var client = createZeebeClient(ZEEBE_CLIENT_ID_TENANT_B)) {
  //      final Future<UpdateTimeoutJobResponse> result =
  //          client.newUpdateTimeoutCommand(activatedJob).timeout(Duration.ofMinutes(11)).send();
  //
  //      // then
  //      assertThat(result)
  //          .failsWithin(Duration.ofSeconds(10))
  //          .withThrowableThat()
  //          .withMessageContaining("NOT_FOUND")
  //          .withMessageContaining(
  //              "Command 'UPDATE_TIMEOUT' rejected with code 'NOT_FOUND': Expected to update job
  // with key '%d', but no such job was found"
  //                  .formatted(activatedJob.getKey()));
  //    }
  //  }
  //
  //  @Test
  //  void shouldNotFindJobWhenUnauthorized() {
  //    // given
  //    final ActivatedJob activatedJob;
  //    try (final var client = createZeebeClient(ZEEBE_CLIENT_ID_TENANT_A)) {
  //      client
  //          .newDeployResourceCommand()
  //          .addProcessModel(process, "process.bpmn")
  //          .tenantId(TENANT_A)
  //          .send()
  //          .join();
  //      client
  //          .newCreateInstanceCommand()
  //          .bpmnProcessId(processId)
  //          .latestVersion()
  //          .tenantId(TENANT_A)
  //          .send()
  //          .join();
  //      activatedJob =
  //          client
  //              .newActivateJobsCommand()
  //              .jobType("type")
  //              .maxJobsToActivate(1)
  //              .tenantId(TENANT_A)
  //              .send()
  //              .join()
  //              .getJobs()
  //              .get(0);
  //    }
  //
  //    // when
  //    try (final var client = createZeebeClient(ZEEBE_CLIENT_ID_TENANT_B)) {
  //      final Future<CompleteJobResponse> result = client.newCompleteCommand(activatedJob).send();
  //
  //      // then
  //      assertThat(result)
  //          .failsWithin(Duration.ofSeconds(10))
  //          .withThrowableThat()
  //          .withMessageContaining("NOT_FOUND")
  //          .withMessageContaining(
  //              "Command 'COMPLETE' rejected with code 'NOT_FOUND': Expected to update retries for
  // job with key '%d', but no such job was found"
  //                  .formatted(activatedJob.getKey()));
  //    }
  //  }
  //
  //  @Test
  //  void shouldResolveIncidentForTenant() {
  //    // given
  //    process =
  //        Bpmn.createExecutableProcess(processId)
  //            .startEvent()
  //            .zeebeOutputExpression("assert(foo, foo != null)", "target")
  //            .endEvent()
  //            .done();
  //    try (final var client = createZeebeClient(ZEEBE_CLIENT_ID_TENANT_A)) {
  //      client
  //          .newDeployResourceCommand()
  //          .addProcessModel(process, "process.bpmn")
  //          .tenantId(TENANT_A)
  //          .send()
  //          .join();
  //      client
  //          .newCreateInstanceCommand()
  //          .bpmnProcessId(processId)
  //          .latestVersion()
  //          .tenantId(TENANT_A)
  //          .send()
  //          .join();
  //
  //      final var incidentKey =
  //          RecordingExporter.incidentRecords().withBpmnProcessId(processId).getFirst().getKey();
  //
  //      // when
  //      final Future<ResolveIncidentResponse> result =
  //          client.newResolveIncidentCommand(incidentKey).send();
  //
  //      // then
  //      assertThat(result)
  //          .describedAs(
  //              "Expect that incident can be resolved as the client has access process of
  // tenant-a")
  //          .succeedsWithin(Duration.ofSeconds(10));
  //    }
  //  }
  //
  //  @Test
  //  void shouldNotFindIncidentForTenantWhenUnauthorized() {
  //    // given
  //    process =
  //        Bpmn.createExecutableProcess(processId)
  //            .startEvent()
  //            .zeebeOutputExpression("assert(foo, foo != null)", "target")
  //            .endEvent()
  //            .done();
  //    final long incidentKey;
  //    try (final var client = createZeebeClient(ZEEBE_CLIENT_ID_TENANT_A)) {
  //      client
  //          .newDeployResourceCommand()
  //          .addProcessModel(process, "process.bpmn")
  //          .tenantId(TENANT_A)
  //          .send()
  //          .join();
  //      client
  //          .newCreateInstanceCommand()
  //          .bpmnProcessId(processId)
  //          .latestVersion()
  //          .tenantId(TENANT_A)
  //          .send()
  //          .join();
  //
  //      incidentKey =
  //          RecordingExporter.incidentRecords().withBpmnProcessId(processId).getFirst().getKey();
  //    }
  //
  //    try (final var client = createZeebeClient(ZEEBE_CLIENT_ID_TENANT_B)) {
  //      // when
  //      final Future<ResolveIncidentResponse> result =
  //          client.newResolveIncidentCommand(incidentKey).send();
  //
  //      // then
  //      assertThat(result)
  //          .failsWithin(Duration.ofSeconds(10))
  //          .withThrowableThat()
  //          .withMessageContaining("NOT_FOUND")
  //          .withMessageContaining(
  //              "Command 'RESOLVE' rejected with code 'NOT_FOUND': Expected to resolve incident
  // with key '%d', but no such incident was found"
  //                  .formatted(incidentKey));
  //    }
  //  }
  //
  //  @Test
  //  void shouldAllowModifyProcessInstanceForDefaultTenant() {
  //    try (final var client = createZeebeClient(ZEEBE_CLIENT_ID_TENANT_DEFAULT)) {
  //      // given
  //      client.newDeployResourceCommand().addProcessModel(process, "process.bpmn").send().join();
  //
  //      final long processInstanceKey =
  //          client
  //              .newCreateInstanceCommand()
  //              .bpmnProcessId(processId)
  //              .latestVersion()
  //              .send()
  //              .join()
  //              .getProcessInstanceKey();
  //
  //      // when
  //      final Future<ModifyProcessInstanceResponse> response =
  //
  // client.newModifyProcessInstanceCommand(processInstanceKey).activateElement("task").send();
  //
  //      // then
  //      assertThat(response).succeedsWithin(Duration.ofSeconds(10));
  //    }
  //  }
  //
  //  @Test
  //  void shouldAllowModifyProcessInstanceForOtherTenant() {
  //    try (final var client = createZeebeClient(ZEEBE_CLIENT_ID_TENANT_A)) {
  //      // given
  //      client
  //          .newDeployResourceCommand()
  //          .addProcessModel(process, "process.bpmn")
  //          .tenantId(TENANT_A)
  //          .send()
  //          .join();
  //
  //      final long processInstanceKey =
  //          client
  //              .newCreateInstanceCommand()
  //              .bpmnProcessId(processId)
  //              .latestVersion()
  //              .tenantId(TENANT_A)
  //              .send()
  //              .join()
  //              .getProcessInstanceKey();
  //
  //      // when
  //      final Future<ModifyProcessInstanceResponse> response =
  //
  // client.newModifyProcessInstanceCommand(processInstanceKey).activateElement("task").send();
  //
  //      // then
  //      assertThat(response).succeedsWithin(Duration.ofSeconds(10));
  //    }
  //  }
  //
  //  @Test
  //  void shouldRejectModifyProcessInstanceForUnauthorizedTenant() {
  //    final long processInstanceKey;
  //    try (final var client = createZeebeClient(ZEEBE_CLIENT_ID_TENANT_A)) {
  //      // given
  //      client
  //          .newDeployResourceCommand()
  //          .addProcessModel(process, "process.bpmn")
  //          .tenantId(TENANT_A)
  //          .send()
  //          .join();
  //
  //      processInstanceKey =
  //          client
  //              .newCreateInstanceCommand()
  //              .bpmnProcessId(processId)
  //              .latestVersion()
  //              .tenantId(TENANT_A)
  //              .send()
  //              .join()
  //              .getProcessInstanceKey();
  //    }
  //
  //    // when
  //    try (final var client = createZeebeClient(ZEEBE_CLIENT_ID_TENANT_B)) {
  //      final Future<ModifyProcessInstanceResponse> response =
  //
  // client.newModifyProcessInstanceCommand(processInstanceKey).activateElement("task").send();
  //
  //      // then
  //      assertThat(response)
  //          .failsWithin(Duration.ofSeconds(10))
  //          .withThrowableThat()
  //          .withMessageContaining("NOT_FOUND")
  //          .withMessageContaining(
  //              "Expected to modify process instance but no process instance found with key");
  //    }
  //  }
  //
  //  @Test
  //  void shouldAllowMigrateProcessInstanceForDefaultTenant() {
  //    try (final var client = createZeebeClient(ZEEBE_CLIENT_ID_TENANT_DEFAULT)) {
  //      // given
  //      client.newDeployResourceCommand().addProcessModel(process, "process.bpmn").send().join();
  //      final var deploymentResponse =
  //          client
  //              .newDeployResourceCommand()
  //              .addProcessModel(migratedProcess, "migrated-process.bpmn")
  //              .send()
  //              .join();
  //      final long targetProcessDefinitionKey =
  //          deploymentResponse.getProcesses().get(0).getProcessDefinitionKey();
  //
  //      final long processInstanceKey =
  //          client
  //              .newCreateInstanceCommand()
  //              .bpmnProcessId(processId)
  //              .latestVersion()
  //              .send()
  //              .join()
  //              .getProcessInstanceKey();
  //
  //      // when
  //      final Future<MigrateProcessInstanceResponse> response =
  //          client
  //              .newMigrateProcessInstanceCommand(processInstanceKey)
  //              .migrationPlan(targetProcessDefinitionKey)
  //              .addMappingInstruction("task", "migrated-task")
  //              .send();
  //
  //      // then
  //      assertThat(response).succeedsWithin(Duration.ofSeconds(10));
  //    }
  //  }
  //
  //  @Test
  //  void shouldAllowMigrateProcessInstanceForOtherTenant() {
  //    try (final var client = createZeebeClient(ZEEBE_CLIENT_ID_TENANT_A)) {
  //      // given
  //      client
  //          .newDeployResourceCommand()
  //          .addProcessModel(process, "process.bpmn")
  //          .tenantId(TENANT_A)
  //          .send()
  //          .join();
  //      final var deploymentResponse =
  //          client
  //              .newDeployResourceCommand()
  //              .addProcessModel(migratedProcess, "migrated-process.bpmn")
  //              .tenantId(TENANT_A)
  //              .send()
  //              .join();
  //      final long targetProcessDefinitionKey =
  //          deploymentResponse.getProcesses().get(0).getProcessDefinitionKey();
  //
  //      final long processInstanceKey =
  //          client
  //              .newCreateInstanceCommand()
  //              .bpmnProcessId(processId)
  //              .latestVersion()
  //              .tenantId(TENANT_A)
  //              .send()
  //              .join()
  //              .getProcessInstanceKey();
  //
  //      // when
  //      final Future<MigrateProcessInstanceResponse> response =
  //          client
  //              .newMigrateProcessInstanceCommand(processInstanceKey)
  //              .migrationPlan(targetProcessDefinitionKey)
  //              .addMappingInstruction("task", "migrated-task")
  //              .send();
  //
  //      // then
  //      assertThat(response).succeedsWithin(Duration.ofSeconds(10));
  //    }
  //  }
  //
  //  @Test
  //  void shouldRejectMigrateProcessInstanceForUnauthorizedTenant() {
  //    final long processInstanceKey;
  //    final long targetProcessDefinitionKey;
  //    try (final var client = createZeebeClient(ZEEBE_CLIENT_ID_TENANT_A)) {
  //      // given
  //      client
  //          .newDeployResourceCommand()
  //          .addProcessModel(process, "process.bpmn")
  //          .tenantId(TENANT_A)
  //          .send()
  //          .join();
  //      final var deploymentResponse =
  //          client
  //              .newDeployResourceCommand()
  //              .addProcessModel(migratedProcess, "process.bpmn")
  //              .tenantId(TENANT_A)
  //              .send()
  //              .join();
  //      targetProcessDefinitionKey =
  //          deploymentResponse.getProcesses().get(0).getProcessDefinitionKey();
  //
  //      processInstanceKey =
  //          client
  //              .newCreateInstanceCommand()
  //              .bpmnProcessId(processId)
  //              .latestVersion()
  //              .tenantId(TENANT_A)
  //              .send()
  //              .join()
  //              .getProcessInstanceKey();
  //    }
  //
  //    // when
  //    try (final var client = createZeebeClient(ZEEBE_CLIENT_ID_TENANT_B)) {
  //      final Future<MigrateProcessInstanceResponse> response =
  //          client
  //              .newMigrateProcessInstanceCommand(processInstanceKey)
  //              .migrationPlan(targetProcessDefinitionKey)
  //              .addMappingInstruction("task", "migrated-task")
  //              .send();
  //
  //      // then
  //      assertThat(response)
  //          .failsWithin(Duration.ofSeconds(10))
  //          .withThrowableThat()
  //          .withMessageContaining("NOT_FOUND")
  //          .withMessageContaining(
  //              String.format(
  //                  "Expected to migrate process instance but no process instance found with key
  // '%s'",
  //                  processInstanceKey));
  //    }
  //  }
  //
  //  @Test
  //  void shouldAllowEvaluateDecisionForDefaultTenant() {
  //    try (final var client = createZeebeClient(ZEEBE_CLIENT_ID_TENANT_DEFAULT)) {
  //      // given
  //      client
  //          .newDeployResourceCommand()
  //          .addResourceFromClasspath("dmn/decision-table.dmn")
  //          .send()
  //          .join();
  //
  //      // when
  //      final Future<EvaluateDecisionResponse> response =
  //          client
  //              .newEvaluateDecisionCommand()
  //              .decisionId("jedi_or_sith")
  //              .variable("lightsaberColor", "blue")
  //              .send();
  //      // then
  //      assertThat(response).succeedsWithin(Duration.ofSeconds(10));
  //    }
  //  }
  //
  //  @Test
  //  void shouldAllowEvaluateDecisionForCustomTenant() {
  //    try (final var client = createZeebeClient(ZEEBE_CLIENT_ID_TENANT_DEFAULT)) {
  //      // given
  //      client
  //          .newDeployResourceCommand()
  //          .addResourceFromClasspath("dmn/decision-table.dmn")
  //          .tenantId(TENANT_A)
  //          .send()
  //          .join();
  //
  //      // when
  //      final Future<EvaluateDecisionResponse> response =
  //          client
  //              .newEvaluateDecisionCommand()
  //              .decisionId("jedi_or_sith")
  //              .variable("lightsaberColor", "blue")
  //              .tenantId(TENANT_A)
  //              .send();
  //      // then
  //      assertThat(response).succeedsWithin(Duration.ofSeconds(10));
  //    }
  //  }
  //
  //  @Test
  //  void shouldDenyEvaluateDecisionForCustomTenant() {
  //    try (final var client = createZeebeClient(ZEEBE_CLIENT_ID_TENANT_DEFAULT)) {
  //      // given
  //      client
  //          .newDeployResourceCommand()
  //          .addResourceFromClasspath("dmn/decision-table.dmn")
  //          .tenantId(TENANT_A)
  //          .send()
  //          .join();
  //
  //      // when
  //      final Future<EvaluateDecisionResponse> response =
  //          client
  //              .newEvaluateDecisionCommand()
  //              .decisionId("jedi_or_sith")
  //              .variable("lightsaberColor", "blue")
  //              .tenantId(TENANT_B)
  //              .send();
  //      // then
  //      assertThat(response)
  //          .failsWithin(Duration.ofSeconds(10))
  //          .withThrowableThat()
  //          .withMessageContaining("PERMISSION_DENIED")
  //          .withMessageContaining(
  //              "Expected to handle gRPC request EvaluateDecision with tenant identifier
  // 'tenant-b'")
  //          .withMessageContaining("but tenant is not authorized to perform this request");
  //    }
  //  }
  //
  //  @Test
  //  void shouldStartInstanceWhenBroadcastSignalForTenant() {
  //    final String signalName = "signal";
  //    process =
  //
  // Bpmn.createExecutableProcess(processId).startEvent().signal(signalName).endEvent().done();
  //    try (final var client = createZeebeClient(ZEEBE_CLIENT_ID_TENANT_A)) {
  //      // given
  //      client
  //          .newDeployResourceCommand()
  //          .addProcessModel(process, "process.bpmn")
  //          .tenantId(TENANT_A)
  //          .send()
  //          .join();
  //
  //      // when
  //      final Future<BroadcastSignalResponse> result =
  //          client.newBroadcastSignalCommand().signalName(signalName).tenantId(TENANT_A).send();
  //
  //      // then
  //      assertThat(result)
  //          .describedAs(
  //              "Expect that signal can be broadcast as the client has access to the process of
  // 'tenant-a'")
  //          .succeedsWithin(Duration.ofSeconds(20));
  //    }
  //  }
  //
  //  @Test
  //  void shouldDenyBroadcastSignalWhenUnauthorized() {
  //    final String signalName = "signal";
  //    process =
  //
  // Bpmn.createExecutableProcess(processId).startEvent().signal(signalName).endEvent().done();
  //    try (final var client = createZeebeClient(ZEEBE_CLIENT_ID_TENANT_A)) {
  //      // given
  //      client
  //          .newDeployResourceCommand()
  //          .addProcessModel(process, "process.bpmn")
  //          .tenantId(TENANT_A)
  //          .send()
  //          .join();
  //
  //      // when
  //      final Future<BroadcastSignalResponse> result =
  //          client.newBroadcastSignalCommand().signalName(signalName).tenantId(TENANT_B).send();
  //
  //      // then
  //      assertThat(result)
  //          .failsWithin(Duration.ofSeconds(10))
  //          .withThrowableThat()
  //          .withMessageContaining("PERMISSION_DENIED")
  //          .withMessageContaining(
  //              "Expected to handle gRPC request BroadcastSignal with tenant identifier
  // 'tenant-b'")
  //          .withMessageContaining("but tenant is not authorized to perform this request");
  //    }
  //  }
  //
  //  @Test
  //  void shouldAllowAssignUserTaskForTenant() {
  //    try (final var clientTenantA = createZeebeClient(ZEEBE_CLIENT_ID_TENANT_A);
  //        final var clientTenantB = createZeebeClient(ZEEBE_CLIENT_ID_TENANT_A_AND_B)) {
  //      // given
  //      final var resourceHelper = new ZeebeResourcesHelper(clientTenantA);
  //      final var userTaskKey = resourceHelper.createSingleUserTask(TENANT_A);
  //
  //      // when
  //      final Future<AssignUserTaskResponse> result =
  //          clientTenantB.newUserTaskAssignCommand(userTaskKey).assignee("Skeletor").send();
  //
  //      // then
  //      assertThat(result).succeedsWithin(Duration.ofSeconds(10));
  //    }
  //  }
  //
  //  @Test
  //  void shouldRejectAssignUserTaskForTenant() {
  //    try (final var clientTenantA = createZeebeClient(ZEEBE_CLIENT_ID_TENANT_A);
  //        final var skeletorClient = createZeebeClient(ZEEBE_CLIENT_ID_WITHOUT_TENANT)) {
  //      // given
  //      final var resourceHelper = new ZeebeResourcesHelper(clientTenantA);
  //      final var userTaskKey = resourceHelper.createSingleUserTask(TENANT_A);
  //
  //      // when
  //      final Future<AssignUserTaskResponse> result =
  //          skeletorClient.newUserTaskAssignCommand(userTaskKey).assignee("Skeletor").send();
  //
  //      // then
  //      assertThat(result)
  //          .failsWithin(Duration.ofSeconds(10))
  //          .withThrowableThat()
  //          .havingCause()
  //          .asInstanceOf(InstanceOfAssertFactories.throwable(ProblemException.class))
  //          .returns(HttpStatus.SC_NOT_FOUND, ProblemException::code);
  //    }
  //  }
  //
  //  @Test
  //  void shouldAllowCompleteUserTaskForTenant() {
  //    try (final var clientTenantA = createZeebeClient(ZEEBE_CLIENT_ID_TENANT_A);
  //        final var clientTenantB = createZeebeClient(ZEEBE_CLIENT_ID_TENANT_A_AND_B)) {
  //      // given
  //      final var resourceHelper = new ZeebeResourcesHelper(clientTenantA);
  //      final var userTaskKey = resourceHelper.createSingleUserTask(TENANT_A);
  //
  //      // when
  //      final Future<CompleteUserTaskResponse> result =
  //          clientTenantB.newUserTaskCompleteCommand(userTaskKey).send();
  //
  //      // then
  //      assertThat(result).succeedsWithin(Duration.ofSeconds(10));
  //    }
  //  }
  //
  //  @Test
  //  void shouldRejectCompleteUserTaskForTenant() {
  //    try (final var clientTenantA = createZeebeClient(ZEEBE_CLIENT_ID_TENANT_A);
  //        final var invalidClient = createZeebeClient(ZEEBE_CLIENT_ID_WITHOUT_TENANT)) {
  //      // given
  //      final var resourceHelper = new ZeebeResourcesHelper(clientTenantA);
  //      final var userTaskKey = resourceHelper.createSingleUserTask(TENANT_A);
  //
  //      // when
  //      final Future<CompleteUserTaskResponse> result =
  //          invalidClient.newUserTaskCompleteCommand(userTaskKey).send();
  //
  //      // then
  //      assertThat(result)
  //          .failsWithin(Duration.ofSeconds(10))
  //          .withThrowableThat()
  //          .havingCause()
  //          .asInstanceOf(InstanceOfAssertFactories.throwable(ProblemException.class))
  //          .returns(HttpStatus.SC_NOT_FOUND, ProblemException::code);
  //    }
  //  }
  //
  //  @Test
  //  void shouldAllowUnassignUserTaskForTenant() {
  //    try (final var clientTenantA = createZeebeClient(ZEEBE_CLIENT_ID_TENANT_A);
  //        final var clientTenantB = createZeebeClient(ZEEBE_CLIENT_ID_TENANT_A_AND_B)) {
  //      // given
  //      final var resourceHelper = new ZeebeResourcesHelper(clientTenantA);
  //      final var userTaskKey = resourceHelper.createSingleUserTask(TENANT_A);
  //      clientTenantA.newUserTaskAssignCommand(userTaskKey).assignee("Skeletor").send().join();
  //
  //      // when
  //      final Future<UnassignUserTaskResponse> result =
  //          clientTenantB.newUserTaskUnassignCommand(userTaskKey).send();
  //
  //      // then
  //      assertThat(result).succeedsWithin(Duration.ofSeconds(10));
  //    }
  //  }
  //
  //  @Test
  //  void shouldRejectUnassignUserTaskForTenant() {
  //    try (final var clientTenantA = createZeebeClient(ZEEBE_CLIENT_ID_TENANT_A);
  //        final var invalidClient = createZeebeClient(ZEEBE_CLIENT_ID_WITHOUT_TENANT)) {
  //      // given
  //      final var resourceHelper = new ZeebeResourcesHelper(clientTenantA);
  //      final var userTaskKey = resourceHelper.createSingleUserTask(TENANT_A);
  //      clientTenantA.newUserTaskAssignCommand(userTaskKey).assignee("Skeletor").send().join();
  //
  //      // when
  //      final Future<UnassignUserTaskResponse> result =
  //          invalidClient.newUserTaskUnassignCommand(userTaskKey).send();
  //
  //      // then
  //      assertThat(result)
  //          .failsWithin(Duration.ofSeconds(10))
  //          .withThrowableThat()
  //          .havingCause()
  //          .asInstanceOf(InstanceOfAssertFactories.throwable(ProblemException.class))
  //          .returns(HttpStatus.SC_NOT_FOUND, ProblemException::code);
  //    }
  //  }
  //
  //  @Test
  //  void shouldAllowUpdateUserTaskForTenant() {
  //    try (final var clientTenantA = createZeebeClient(ZEEBE_CLIENT_ID_TENANT_A);
  //        final var clientTenantB = createZeebeClient(ZEEBE_CLIENT_ID_TENANT_A_AND_B)) {
  //      // given
  //      final var resourceHelper = new ZeebeResourcesHelper(clientTenantA);
  //      final var userTaskKey = resourceHelper.createSingleUserTask(TENANT_A);
  //
  //      // when
  //      final Future<UpdateUserTaskResponse> result =
  //          clientTenantB.newUserTaskUpdateCommand(userTaskKey).candidateUsers("Skeletor").send();
  //
  //      // then
  //      assertThat(result).succeedsWithin(Duration.ofSeconds(10));
  //    }
  //  }
  //
  //  @Test
  //  void shouldRejectUpdateUserTaskForTenant() {
  //    try (final var clientTenantA = createZeebeClient(ZEEBE_CLIENT_ID_TENANT_A);
  //        final var invalidClient = createZeebeClient(ZEEBE_CLIENT_ID_WITHOUT_TENANT)) {
  //      // given
  //      final var resourceHelper = new ZeebeResourcesHelper(clientTenantA);
  //      final var userTaskKey = resourceHelper.createSingleUserTask(TENANT_A);
  //
  //      // when
  //      final Future<UpdateUserTaskResponse> result =
  //          invalidClient.newUserTaskUpdateCommand(userTaskKey).candidateUsers("Skeletor").send();
  //
  //      // then
  //      assertThat(result)
  //          .failsWithin(Duration.ofSeconds(10))
  //          .withThrowableThat()
  //          .havingCause()
  //          .asInstanceOf(InstanceOfAssertFactories.throwable(ProblemException.class))
  //          .returns(HttpStatus.SC_NOT_FOUND, ProblemException::code);
  //    }
  //  }
  //
  private static Stream<Named<UnaryOperator<TopologyRequestStep1>>> provideTopologyCases() {
    return Stream.of(
        Named.of("grpc", TopologyRequestStep1::useGrpc),
        Named.of("rest", TopologyRequestStep1::useRest));
  }

  //
  /**
   * Creates a new Camunda Client with the given user. Note that the username and password are equal
   */
  private static CamundaClient createCamundaClient(final String user) {
    return BROKER
        .newClientBuilder()
        .credentialsProvider(
            new BasicAuthCredentialsProviderBuilder().username(user).password(user).build())
        .defaultRequestTimeout(Duration.ofSeconds(10))
        .build();
  }

  /**
   * Creates a tenant and assigns the provided users
   *
   * @param client The camunda client to create the association with
   * @param tenantId The id of the tenant to create
   * @param usernames The usernames of the users to associate the tenant with
   */
  private static void createTenantAndAssignUsers(
      final CamundaClient client, final String tenantId, final String... usernames) {
    client.newCreateTenantCommand().tenantId(tenantId).name(tenantId).send().join();
    assignUsersToTenant(client, tenantId, usernames);
  }

  /**
   * Assigns the provided users to a given tenant
   *
   * @param client The camunda client to create the association with
   * @param tenantId The id of the tenant to create
   * @param usernames The usernames of the users to associate the tenant with
   */
  private static void assignUsersToTenant(
      final CamundaClient client, final String tenantId, final String... usernames) {
    Arrays.stream(usernames)
        .forEach(
            username ->
                client.newAssignUserToTenantCommand(tenantId).username(username).send().join());
  }
}
