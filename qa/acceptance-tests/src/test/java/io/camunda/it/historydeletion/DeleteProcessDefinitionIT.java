/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.historydeletion;

import static io.camunda.it.util.TestHelper.assertAllProcessInstanceDependantDataDeleted;
import static io.camunda.it.util.TestHelper.deployProcessAndWaitForIt;
import static io.camunda.it.util.TestHelper.startProcessInstance;
import static io.camunda.it.util.TestHelper.waitForBatchOperationCompleted;
import static io.camunda.it.util.TestHelper.waitForBatchOperationWithCorrectTotalCount;
import static io.camunda.it.util.TestHelper.waitForProcessInstances;
import static io.camunda.it.util.TestHelper.waitForProcesses;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.DeleteResourceResponse;
import io.camunda.client.api.search.enums.ProcessInstanceState;
import io.camunda.configuration.HistoryDeletion;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.test.util.Strings;
import java.time.Duration;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

@MultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
public class DeleteProcessDefinitionIT {

  @MultiDbTestApplication
  static final TestStandaloneBroker BROKER =
      new TestStandaloneBroker()
          .withProcessingConfig(
              config ->
                  config
                      .getEngine()
                      .getBatchOperations()
                      .setSchedulerInterval(Duration.ofMillis(100)))
          .withDataConfig(
              config -> {
                final var historyDeletionConfig = new HistoryDeletion();
                historyDeletionConfig.setDelayBetweenRuns(Duration.ofMillis(100));
                historyDeletionConfig.setMaxDelayBetweenRuns(Duration.ofMillis(100));
                config.setHistoryDeletion(historyDeletionConfig);
              });

  private static final Duration DELETION_TIMEOUT = Duration.ofSeconds(30);
  private static CamundaClient camundaClient;

  @Test
  void shouldDeleteProcessDefinitionWithHistory() {
    // given
    final var processId = Strings.newRandomValidBpmnId();
    final var process =
        deployProcessAndWaitForIt(
            camundaClient,
            Bpmn.createExecutableProcess(processId).startEvent().endEvent().done(),
            processId + ".bpmn");
    final var processDefinitionKey = process.getProcessDefinitionKey();
    final long piKey = startProcessInstance(camundaClient, processId).getProcessInstanceKey();
    waitForProcessInstances(
        camundaClient,
        f -> f.processDefinitionId(processId).state(ProcessInstanceState.COMPLETED),
        1);

    // when
    final DeleteResourceResponse result =
        camundaClient
            .newDeleteResourceCommand(processDefinitionKey)
            .deleteHistory(true)
            .send()
            .join();

    // then
    final var batchOperationKey = result.getCreateBatchOperationResponse().getBatchOperationKey();
    waitForBatchOperationWithCorrectTotalCount(camundaClient, batchOperationKey, 1);
    waitForBatchOperationCompleted(camundaClient, batchOperationKey, 1, 0);
    assertAllProcessInstanceDependantDataDeleted(camundaClient, piKey);
    assertProcessDefinitionDeleted(camundaClient, processDefinitionKey);
  }

  @Test
  void shouldDeleteProcessDefinitionWithMultipleCompletedProcessInstances() {
    // given
    final var processId = Strings.newRandomValidBpmnId();
    final var process =
        deployProcessAndWaitForIt(
            camundaClient,
            Bpmn.createExecutableProcess(processId).startEvent().endEvent().done(),
            processId + ".bpmn");
    final var processDefinitionKey = process.getProcessDefinitionKey();
    final long piKey1 = startProcessInstance(camundaClient, processId).getProcessInstanceKey();
    final long piKey2 = startProcessInstance(camundaClient, processId).getProcessInstanceKey();
    final long piKey3 = startProcessInstance(camundaClient, processId).getProcessInstanceKey();
    waitForProcessInstances(
        camundaClient,
        f -> f.processDefinitionId(processId).state(ProcessInstanceState.COMPLETED),
        3);

    // when
    final DeleteResourceResponse result =
        camundaClient
            .newDeleteResourceCommand(processDefinitionKey)
            .deleteHistory(true)
            .send()
            .join();

    // then - deletion should be successful
    final var batchOperationKey = result.getCreateBatchOperationResponse().getBatchOperationKey();
    waitForBatchOperationWithCorrectTotalCount(camundaClient, batchOperationKey, 3);
    waitForBatchOperationCompleted(camundaClient, batchOperationKey, 3, 0);
    assertAllProcessInstanceDependantDataDeleted(camundaClient, piKey1);
    assertAllProcessInstanceDependantDataDeleted(camundaClient, piKey2);
    assertAllProcessInstanceDependantDataDeleted(camundaClient, piKey3);
    assertProcessDefinitionDeleted(camundaClient, processDefinitionKey);
  }

  @Test
  void shouldDeleteProcessDefinitionWithoutHistory() {
    // given
    final var processId = Strings.newRandomValidBpmnId();
    final var process =
        deployProcessAndWaitForIt(
            camundaClient,
            Bpmn.createExecutableProcess(processId).startEvent().endEvent().done(),
            processId + ".bpmn");
    final var processDefinitionKey = process.getProcessDefinitionKey();
    startProcessInstance(camundaClient, processId).getProcessInstanceKey();
    startProcessInstance(camundaClient, processId).getProcessInstanceKey();
    waitForProcessInstances(
        camundaClient,
        f -> f.processDefinitionId(processId).state(ProcessInstanceState.COMPLETED),
        2);

    // when
    final DeleteResourceResponse result =
        camundaClient
            .newDeleteResourceCommand(processDefinitionKey)
            .deleteHistory(false)
            .send()
            .join();

    // then - deletion should be successful
    assertThat(result.getCreateBatchOperationResponse()).isNull();
    waitForProcessInstances(camundaClient, f -> f.processDefinitionId(processId), 2);
    waitForProcesses(camundaClient, f -> f.processDefinitionId(processId), 1);
  }

  /** Asserts that a process definition has been deleted from secondary storage. */
  private void assertProcessDefinitionDeleted(
      final CamundaClient client, final long processDefinitionKey) {
    Awaitility.await("Process definition should be deleted")
        .atMost(DELETION_TIMEOUT)
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final var processDefinitions =
                  client
                      .newProcessDefinitionSearchRequest()
                      .filter(f -> f.processDefinitionKey(processDefinitionKey))
                      .send()
                      .join()
                      .items();
              assertThat(processDefinitions).isEmpty();
            });
  }
}
