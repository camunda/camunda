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
import static io.camunda.it.util.TestHelper.waitForBatchOperationWithCorrectTotalCount;
import static io.camunda.qa.util.multidb.CamundaMultiDBExtension.TIMEOUT_DATA_AVAILABILITY;
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
import java.time.Duration;
import java.util.List;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@MultiDbTest
public class OperateProcessInstanceCancellationIT {
  @MultiDbTestApplication
  static final TestCamundaApplication CAMUNDA_APPLICATION =
      new TestCamundaApplication().withAuthorizationsEnabled().withBasicAuth();

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

  @BeforeAll
  public static void beforeAll(
      @Authenticated(SUPER_USER_USERNAME) final CamundaClient superUserClient) {
    operateClient =
        CAMUNDA_APPLICATION.newOperateClient(SUPER_USER.username(), SUPER_USER.password());
  }

  @Test
  void shouldCancelProcess(
      @Authenticated(SUPER_USER_USERNAME) final CamundaClient client) {
    // given
    // process instances that are running
    deployProcess(client, "process/service_tasks_v1.bpmn");
    final long processInstanceKey = createProcessInstance(client, "service_tasks_v1");

    // when
    // execute CANCEL_PROCESS_INSTANCE
    final var batchOperationEntity = operateClient.cancelProcessInstancesBatchOperationRequest(
        new ListViewQueryDto().setIds(List.of(String.valueOf(processInstanceKey)))
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

    waitForBatchOperationWithCorrectTotalCount(client, batchOperationId, 1);

    waitForProcessCancelledState(client, processInstanceKey);
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

  private static long waitForProcessCancelledState(
      final CamundaClient client, final long processInstanceKey) {
    Awaitility.await("should wait until flow node instance is in given state")
        .atMost(Duration.ofSeconds(60))
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () -> {
              final var result =
                  client
                      .newProcessInstanceGetRequest(processInstanceKey)
                      .send()
                      .join();

              assertThat(result.getState()).isEqualTo(ProcessInstanceState.TERMINATED);
            });

    return processInstanceKey;
  }

  private static long createProcessInstance(
      final CamundaClient client, final String bpmnProcessId) {

    final ProcessInstanceEvent processInstanceEvent =
        client
            .newCreateInstanceCommand()
            .bpmnProcessId(bpmnProcessId)
            .latestVersion()
            .send()
            .join();
    final long processInstanceKey = processInstanceEvent.getProcessInstanceKey();

    TestHelper.waitForProcessInstancesToStart(
        client,
        b -> b
            .processInstanceKey(processInstanceKey)
            .state(ProcessInstanceState.ACTIVE),
        1
    );

    Awaitility.await("operate should find the process instance")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () -> {
              // and
              final var processEither = operateClient.getProcessInstanceWith(processInstanceKey);
              assertThat(processEither.isRight()).isTrue();
              assertThat(processEither.get().processInstances().getFirst().getState()).isEqualTo("ACTIVE");
            });

    try {
      Thread.sleep(30000);
    } catch (final InterruptedException e) {
      throw new RuntimeException(e);
    }

    Awaitility.await("operate should find the process instance")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () -> {
              // and
              final var processEither = operateClient.getProcessInstanceWith(processInstanceKey);
              assertThat(processEither.isRight()).isTrue();
              assertThat(processEither.get().processInstances().getFirst().getState()).isEqualTo("ACTIVE");
            });

    Awaitility.await("operate should find the process instance")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () -> {
              // and
              final var processEither = operateClient.searchProcessInstances(
                  new ListViewRequestDto().setQuery(
                      new ListViewQueryDto()
                          .setIds(List.of(String.valueOf(processInstanceKey)))
                  )
              );
              assertThat(processEither.isRight()).isTrue();
              assertThat(processEither.get().getProcessInstances().getFirst().getState()).isEqualTo(
                  ProcessInstanceStateDto.ACTIVE);
            });

    return processInstanceKey;
  }

}
