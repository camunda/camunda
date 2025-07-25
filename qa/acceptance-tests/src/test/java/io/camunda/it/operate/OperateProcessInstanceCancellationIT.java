/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.operate;

import static io.camunda.client.api.search.enums.PermissionType.CREATE;
import static io.camunda.client.api.search.enums.PermissionType.CREATE_PROCESS_INSTANCE;
import static io.camunda.client.api.search.enums.PermissionType.READ_PROCESS_DEFINITION;
import static io.camunda.client.api.search.enums.PermissionType.READ_PROCESS_INSTANCE;
import static io.camunda.client.api.search.enums.PermissionType.UPDATE_PROCESS_INSTANCE;
import static io.camunda.client.api.search.enums.ResourceType.PROCESS_DEFINITION;
import static io.camunda.client.api.search.enums.ResourceType.RESOURCE;
import static io.camunda.it.util.TestHelper.startScopedProcessInstance;
import static io.camunda.it.util.TestHelper.waitForBatchOperationWithCorrectTotalCount;
import static io.camunda.it.util.TestHelper.waitForProcessInstanceToBeTerminated;
import static io.camunda.it.util.TestHelper.waitForScopedProcessInstancesToStart;
import static io.camunda.qa.util.multidb.CamundaMultiDBExtension.TIMEOUT_DATA_AVAILABILITY;
import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.DeploymentEvent;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.client.api.search.enums.BatchOperationState;
import io.camunda.client.api.search.enums.ElementInstanceState;
import io.camunda.client.api.search.enums.ElementInstanceType;
import io.camunda.client.api.search.enums.ProcessInstanceState;
import io.camunda.client.api.search.response.ElementInstance;
import io.camunda.it.util.TestHelper;
import io.camunda.operate.webapp.rest.dto.listview.ListViewQueryDto;
import io.camunda.operate.webapp.rest.dto.listview.ListViewRequestDto;
import io.camunda.operate.webapp.rest.dto.listview.ProcessInstanceStateDto;
import io.camunda.qa.util.auth.Authenticated;
import io.camunda.qa.util.auth.Permissions;
import io.camunda.qa.util.auth.TestUser;
import io.camunda.qa.util.auth.UserDefinition;
import io.camunda.qa.util.cluster.TestCamundaApplication;
import io.camunda.qa.util.cluster.TestRestOperateClient;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.zeebe.client.api.command.MigrationPlan;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

@MultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "rdbms")
public class OperateProcessInstanceCancellationIT {
  @MultiDbTestApplication
  static final TestCamundaApplication CAMUNDA_APPLICATION =
      new TestCamundaApplication()
          // Enable the StartupBean which enables the operation executor
          .withAdditionalProfile("test-executor")
          .withAuthorizationsDisabled()
          .withMultiTenancyDisabled()
          .withBasicAuth();

  private static final String SUPER_USER_USERNAME = "super";

  @UserDefinition
  private static final TestUser SUPER_USER =
      new TestUser(
          SUPER_USER_USERNAME,
          "password",
          List.of(
              new Permissions(RESOURCE, CREATE, List.of("*")),
              new Permissions(PROCESS_DEFINITION, READ_PROCESS_DEFINITION, List.of("*")),
              new Permissions(PROCESS_DEFINITION, CREATE_PROCESS_INSTANCE, List.of("*")),
              new Permissions(PROCESS_DEFINITION, UPDATE_PROCESS_INSTANCE, List.of("*")),
              new Permissions(PROCESS_DEFINITION, READ_PROCESS_INSTANCE, List.of("*"))));

  private static TestRestOperateClient operateClient;
  final List<ProcessInstanceEvent> activeProcessInstances = new ArrayList<>();

  @BeforeAll
  public static void beforeAll(
      @Authenticated(SUPER_USER_USERNAME) final CamundaClient superUserClient) {
    operateClient =
        CAMUNDA_APPLICATION.newOperateClient(SUPER_USER.username(), SUPER_USER.password());
  }

  @Test
  void shouldCancelProcess(
      @Authenticated(SUPER_USER_USERNAME) final CamundaClient client, final TestInfo testInfo) {
    final var testScopeId =
        testInfo.getTestMethod().map(Method::toString).orElse(UUID.randomUUID().toString());
    final int processInstanceCount = 10;

    // given
    // process instances that are running
    deployProcess(client, "process/service_tasks_v1.bpmn");
    final List<Long> processInstanceKeys = createProcessInstances(client, "service_tasks_v1", processInstanceCount, testScopeId);

    // when
    // execute CANCEL_PROCESS_INSTANCE
    final var batchOperationEntity = operateClient.cancelProcessInstancesBatchOperationRequest(
        new ListViewQueryDto()
            .setRunning(true)
            .setActive(true)
            .setIncidents(true)
            .setFinished(false)
            .setCompleted(false)
            .setCanceled(false)
            .setRetriesLeft(false)
            .setIds(emptyList())
            .setExcludeIds(emptyList())
    );

    // then
    // This should:
    //
    //   * Create a batch operation
    //   * Create an operation for the cancellation
    //   * Trigger the cancellation via Command on Zeebe
    //   * Zeebe should process and export the respective events
    //   * The process should be cancelled
    //   * Operation should be marked completed by exporter at the end
    assertThat(batchOperationEntity.isRight())
        .withFailMessage("Expected batch operation to be created.")
        .isTrue();
    final var batchOperationId = batchOperationEntity.get().getId();

    waitForBatchOperationWithCorrectTotalCount(client, batchOperationId, processInstanceCount);

    for (final Long key : processInstanceKeys) {
      waitForProcessInstanceToBeTerminated(client, key);
    }
  }

  private static long deployProcess(final CamundaClient client, final String path) {
    final DeploymentEvent deploymentEvent =
        client.newDeployResourceCommand().addResourceFromClasspath(path).send().join();
    final long processDefinitionKey =
        deploymentEvent.getProcesses().getFirst().getProcessDefinitionKey();

    assertThat(processDefinitionKey).isNotZero();

    Awaitility.await("should deploy processes and import in Operate")
        .atMost(Duration.ofSeconds(15))
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () -> {
              final var result =
                  client
                      .newProcessDefinitionSearchRequest()
                      .filter(instance -> instance.processDefinitionKey(processDefinitionKey))
                      .send()
                      .join();
              assertThat(result.items()).isNotEmpty();
            });
    return processDefinitionKey;
  }

  private List<Long> createProcessInstances(
      final CamundaClient client, final String bpmnProcessId, final int amount, final String testScopeId) {

    IntStream.range(0, amount)
        .forEach(
            i ->
                activeProcessInstances.add(
                    startScopedProcessInstance(
                        client, bpmnProcessId, testScopeId, Map.of())));

    waitForScopedProcessInstancesToStart(client, testScopeId, activeProcessInstances.size());

    return activeProcessInstances.stream().map(ProcessInstanceEvent::getProcessInstanceKey).collect(
        Collectors.toList());
  }

}
