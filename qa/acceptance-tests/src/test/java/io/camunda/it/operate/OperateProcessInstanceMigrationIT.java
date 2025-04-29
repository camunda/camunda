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
import static io.camunda.client.api.search.enums.ResourceType.BATCH;
import static io.camunda.client.api.search.enums.ResourceType.PROCESS_DEFINITION;
import static io.camunda.client.api.search.enums.ResourceType.RESOURCE;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.DeploymentEvent;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.client.api.search.enums.ElementInstanceState;
import io.camunda.client.api.search.enums.ElementInstanceType;
import io.camunda.client.api.search.response.ElementInstance;
import io.camunda.qa.util.auth.Authenticated;
import io.camunda.qa.util.auth.Permissions;
import io.camunda.qa.util.auth.User;
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
public class OperateProcessInstanceMigrationIT {
  @MultiDbTestApplication
  static final TestCamundaApplication CAMUNDA_APPLICATION =
      new TestCamundaApplication().withAuthorizationsEnabled().withBasicAuth();

  private static final String SUPER_USER_USERNAME = "super";

  @UserDefinition
  private static final User SUPER_USER =
      new User(
          SUPER_USER_USERNAME,
          "password",
          List.of(
              new Permissions(BATCH, CREATE, List.of("*")),
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
  void shouldMigrateSubprocessToSubprocess(
      @Authenticated(SUPER_USER_USERNAME) final CamundaClient client) {
    // given
    // process instances that are running
    deployProcess(client, "process/migration-subprocess.bpmn");
    final long processInstanceKey = createProcessInstance(client, "prWithSubprocess");
    completeJob(client, "taskA");
    final var processDefinitionTo = deployProcess(client, "process/migration-subprocess2.bpmn");

    // we have the given migration plan
    final var migrationPlan =
        MigrationPlan.newBuilder()
            .withTargetProcessDefinitionKey(processDefinitionTo)
            .addMappingInstruction("taskA", "taskA")
            .addMappingInstruction("subprocess", "subprocess2")
            .addMappingInstruction("innerSubprocess", "innerSubprocess2")
            .addMappingInstruction("taskB", "taskB")
            .build();

    // when
    // execute MIGRATE_PROCESS_INSTANCE
    final var batchOperationEntity =
        operateClient.migrateProcessInstanceWith(processInstanceKey, migrationPlan);

    // then
    // This should:
    //
    //   * Create a batch operation
    //   * Create an operation for the migration
    //   * Trigger the migration via Command on Zeebe
    //   * Zeebe should process and export the respective events
    //   * The process should be migrated
    //   * Operation should be marked completed by exporter at the end
    assertThat(batchOperationEntity.isRight())
        .withFailMessage("Expected batch operation to be created.")
        .isTrue();
    waitForFNIState(client, processInstanceKey, "subProcess2");

    final var elementInstanceSearchQueryResponse =
        client
            .newElementInstanceSearchRequest()
            .filter(
                flownodeInstanceFilter ->
                    flownodeInstanceFilter.processInstanceKey(processInstanceKey))
            .filter(
                flownodeInstanceFilter ->
                    flownodeInstanceFilter.type(ElementInstanceType.SUB_PROCESS))
            .send()
            .join();

    final var items = elementInstanceSearchQueryResponse.items();
    assertThat(items).hasSize(2);
    assertMigratedFieldsByFlowNodeId(items, "subprocess2", processInstanceKey, processDefinitionTo);
    assertMigratedFieldsByFlowNodeId(
        items, "innerSubprocess2", processInstanceKey, processDefinitionTo);
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

  private static long waitForFNIState(
      final CamundaClient client, final long processInstanceKey, final String elementId) {
    Awaitility.await("should wait until flow node instance is in given state")
        .atMost(Duration.ofSeconds(60))
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () -> {
              final var result =
                  client
                      .newElementInstanceSearchRequest()
                      .filter(
                          flownodeInstanceFilter ->
                              flownodeInstanceFilter.processInstanceKey(processInstanceKey))
                      .filter(flownodeInstanceFilter -> flownodeInstanceFilter.elementId(elementId))
                      .send()
                      .join();

              assertThat(result.items()).isNotEmpty();
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

    Awaitility.await("should wait until flow node instances are available")
        .atMost(Duration.ofSeconds(60))
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () -> {
              final var result = client.newElementInstanceSearchRequest().send().join();
              final var any =
                  result.items().stream()
                      .filter(event -> event.getProcessInstanceKey() == processInstanceKey)
                      .findAny();
              assertThat(any).isPresent();
            });

    return processInstanceKey;
  }

  private static long completeJob(final CamundaClient client, final String jobType) {
    final var jobsResponse =
        client
            .newActivateJobsCommand()
            .jobType(jobType)
            .maxJobsToActivate(1)
            .workerName("this")
            .send()
            .join();
    assertThat(jobsResponse.getJobs()).isNotEmpty();

    final var job = jobsResponse.getJobs().getFirst();
    client.newCompleteCommand(job).send().join();

    Awaitility.await("should wait until flow node instances are available")
        .atMost(Duration.ofSeconds(60))
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () -> {
              final var result = client.newElementInstanceSearchRequest().send().join();
              final var any =
                  result.items().stream()
                      .filter(
                          event ->
                              event.getElementInstanceKey() == job.getElementInstanceKey()
                                  && event.getState().equals(ElementInstanceState.COMPLETED))
                      .findAny();
              assertThat(any).isPresent();
            });

    return job.getKey();
  }

  private void assertMigratedFieldsByFlowNodeId(
      final List<ElementInstance> fnis,
      final String flowNodeId,
      final Long instanceKey,
      final Long processDefinitionTo) {
    final var flowNode =
        fnis.stream().filter(fn -> fn.getElementId().equals(flowNodeId)).findFirst().orElseThrow();
    assertThat(flowNode.getProcessInstanceKey()).isEqualTo(instanceKey);
    assertThat(flowNode.getProcessDefinitionKey()).isEqualTo(processDefinitionTo);
  }
}
