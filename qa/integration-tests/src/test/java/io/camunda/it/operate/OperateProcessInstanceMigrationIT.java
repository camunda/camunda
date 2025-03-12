/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.operate;

import static io.camunda.zeebe.client.protocol.rest.PermissionTypeEnum.UPDATE;
import static io.camunda.zeebe.client.protocol.rest.ResourceTypeEnum.BATCH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import io.camunda.qa.util.cluster.TestRestOperateClient;
import io.camunda.qa.util.cluster.TestStandaloneCamunda;
import io.camunda.webapps.schema.entities.operation.BatchOperationEntity;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.command.MigrationPlan;
import io.camunda.zeebe.client.api.response.ActivateJobsResponse;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.client.api.response.CompleteJobResponse;
import io.camunda.zeebe.client.api.response.DeploymentEvent;
import io.camunda.zeebe.client.api.response.ProcessInstanceEvent;
import io.camunda.zeebe.client.api.search.response.FlowNodeInstance;
import io.camunda.zeebe.it.util.AuthorizationsUtil;
import io.camunda.zeebe.it.util.AuthorizationsUtil.Permissions;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.util.Either;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@ZeebeIntegration
public class OperateProcessInstanceMigrationIT {

  private static final String SUPER_USER = "super";
  private static final String RESTRICTED_USER = "restricted";
  private static final String PROCESS_DEFINITION_ID_1 = "service_tasks_v1";
  private static final String PROCESS_DEFINITION_ID_2 = "incident_process_v1";

  @TestZeebe(initMethod = "initTestStandaloneCamunda")
  private static TestStandaloneCamunda testStandaloneCamunda;

  private static ZeebeClient zeebeClient;
  private static TestRestOperateClient superOperateClient;

  @SuppressWarnings("unused")
  static void initTestStandaloneCamunda() {
    testStandaloneCamunda = new TestStandaloneCamunda();
    zeebeClient = testStandaloneCamunda.newClientBuilder().build();
  }

  @BeforeAll
  public static void before() {
    final var authorizationsUtil =
        new AuthorizationsUtil(
            testStandaloneCamunda, testStandaloneCamunda.getElasticSearchHostAddress());
    final var defaultClient = authorizationsUtil.getDefaultClient();
    // create super user that can read all process definitions
    final var superZeebeClient =
        authorizationsUtil.createUserAndClient(
            SUPER_USER, "password", new Permissions(BATCH, UPDATE, List.of("*")));
    superOperateClient = testStandaloneCamunda.newOperateClient(SUPER_USER, "password");
  }

  @Test
  void shouldMigrateSubprocessToSubprocess() throws Exception {
    // given
    // process instances that are running
    deployProcess("process/migration-subprocess.bpmn");
    final long processInstanceKey = createProcessInstance("prWithSubprocess");
    completeJob("taskA");
    final var processDefinitionTo = deployProcess("process/migration-subprocess2.bpmn");

    // we have the given migration plan
    final MigrationPlan migrationPlan =
        MigrationPlan.newBuilder()
            .withTargetProcessDefinitionKey(processDefinitionTo)
            .addMappingInstruction("taskA", "taskA")
            .addMappingInstruction("subprocess", "subprocess2")
            .addMappingInstruction("innerSubprocess", "innerSubprocess2")
            .addMappingInstruction("taskB", "taskB")
            .build();

    // when
    // execute MIGRATE_PROCESS_INSTANCE
    final Either<Exception, BatchOperationEntity> response =
        superOperateClient.migrateProcessInstanceWith(processInstanceKey, migrationPlan);

    // then
    // This should:
    //
    //   * Create a batch operation
    //   * Create an operation for the migration
    //   * Trigger the migration via Command on Zeebe
    //   * Zeebe should process and export the respective events
    //   * Process should be migrated
    //   * Operation should be marked completed by exporter at the end
    if (response.isLeft()) {
      fail(response.getLeft());
    }
    waitForFNIState(processInstanceKey, "subProcess2");

    final var flowNodeInstanceSearchQueryResponse =
        zeebeClient
            .newFlownodeInstanceQuery()
            .filter(
                flownodeInstanceFilter ->
                    flownodeInstanceFilter.processInstanceKey(processInstanceKey))
            .filter(flownodeInstanceFilter -> flownodeInstanceFilter.type("SUB_PROCESS"))
            .send()
            .join();

    final List<FlowNodeInstance> items = flowNodeInstanceSearchQueryResponse.items();
    assertThat(items).hasSize(2);
    assertMigratedFieldsByFlowNodeId(items, "subprocess2", processInstanceKey, processDefinitionTo);
    assertMigratedFieldsByFlowNodeId(
        items, "innerSubprocess2", processInstanceKey, processDefinitionTo);
  }

  private static long deployProcess(final String path) {
    final DeploymentEvent deploymentEvent =
        zeebeClient.newDeployResourceCommand().addResourceFromClasspath(path).send().join();
    final long processDefinitionKey =
        deploymentEvent.getProcesses().get(0).getProcessDefinitionKey();

    assertThat(processDefinitionKey).isNotZero();

    Awaitility.await("should deploy processes and import in Operate")
        .atMost(Duration.ofSeconds(15))
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () -> {
              final var result =
                  zeebeClient
                      .newProcessDefinitionQuery()
                      .filter(instance -> instance.processDefinitionKey(processDefinitionKey))
                      .send()
                      .join();
              assertThat(result.items()).isNotEmpty();
            });
    return processDefinitionKey;
  }

  private static long waitForFNIState(final long processInstanceKey, final String elementId) {
    Awaitility.await("should wait until flow node instance is in given state")
        .atMost(Duration.ofSeconds(60))
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () -> {
              final var result =
                  zeebeClient
                      .newFlownodeInstanceQuery()
                      .filter(
                          flownodeInstanceFilter ->
                              flownodeInstanceFilter.processInstanceKey(processInstanceKey))
                      .filter(
                          flownodeInstanceFilter -> flownodeInstanceFilter.flowNodeId(elementId))
                      .send()
                      .join();

              assertThat(result.items()).isNotEmpty();
            });

    return processInstanceKey;
  }

  private static long createProcessInstance(final String bpmnProcessId) {

    final ProcessInstanceEvent processInstanceEvent =
        zeebeClient
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
              final var result = zeebeClient.newFlownodeInstanceQuery().send().join();
              final Optional<FlowNodeInstance> any =
                  result.items().stream()
                      .filter(event -> event.getProcessInstanceKey() == processInstanceKey)
                      .findAny();
              assertThat(any).isPresent();
            });

    return processInstanceKey;
  }

  private static long completeJob(final String jobType) {
    final ActivateJobsResponse jobsResponse =
        zeebeClient
            .newActivateJobsCommand()
            .jobType(jobType)
            .maxJobsToActivate(1)
            .workerName("this")
            .send()
            .join();
    assertThat(jobsResponse.getJobs()).isNotEmpty();

    final ActivatedJob job = jobsResponse.getJobs().get(0);
    final CompleteJobResponse join = zeebeClient.newCompleteCommand(job).send().join();

    Awaitility.await("should wait until flow node instances are available")
        .atMost(Duration.ofSeconds(60))
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () -> {
              final var result = zeebeClient.newFlownodeInstanceQuery().send().join();
              final Optional<FlowNodeInstance> any =
                  result.items().stream()
                      .filter(
                          event ->
                              event.getFlowNodeInstanceKey() == job.getElementInstanceKey()
                                  && "COMPLETED".equals(event.getState()))
                      .findAny();
              assertThat(any).isPresent();
            });

    return job.getKey();
  }

  private void assertMigratedFieldsByFlowNodeId(
      final List<FlowNodeInstance> fnis,
      final String flowNodeId,
      final Long instanceKey,
      final Long processDefinitionTo) {
    final var flowNode =
        fnis.stream().filter(fn -> fn.getFlowNodeId().equals(flowNodeId)).findFirst().orElseThrow();
    assertThat(flowNode.getProcessInstanceKey()).isEqualTo(instanceKey);
    assertThat(flowNode.getProcessDefinitionKey()).isEqualTo(processDefinitionTo);
  }
}
