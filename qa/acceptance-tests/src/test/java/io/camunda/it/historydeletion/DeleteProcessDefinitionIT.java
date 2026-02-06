/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.historydeletion;

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
import java.util.Collection;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;
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
    assertAllProcessInstanceDataDeleted(camundaClient, piKey);
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
    assertAllProcessInstanceDataDeleted(camundaClient, piKey1);
    assertAllProcessInstanceDataDeleted(camundaClient, piKey2);
    assertAllProcessInstanceDataDeleted(camundaClient, piKey3);
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

  /**
   * Asserts that all data related to a process instance has been deleted from secondary storage.
   */
  private void assertAllProcessInstanceDataDeleted(
      final CamundaClient client, final long processInstanceKey) {
    Awaitility.await("All process instance data should be deleted")
        .atMost(DELETION_TIMEOUT)
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final Map<String, Collection<?>> results =
                  getDataVerifiers(client, processInstanceKey).entrySet().parallelStream()
                      .collect(
                          Collectors.toConcurrentMap(Map.Entry::getKey, e -> e.getValue().get()));

              assertThat(results)
                  .as("All verifiers should find no data")
                  .allSatisfy((name, data) -> assertThat(data).as("Data for %s", name).isEmpty());
            });
  }

  /**
   * Asserts that all data related to a process instance still exists in secondary storage. This is
   * used to verify that a non-deleted process instance still has all its data intact.
   */
  private void assertProcessInstanceDataExists(
      final CamundaClient client, final long processInstanceKey) {
    Awaitility.await("Process instance should still exist")
        .atMost(DELETION_TIMEOUT)
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              // At minimum, the process instance itself should exist
              final var processInstances =
                  client
                      .newProcessInstanceSearchRequest()
                      .filter(f -> f.processInstanceKey(processInstanceKey))
                      .send()
                      .join()
                      .items();
              assertThat(processInstances).hasSize(1);

              // Element instances should exist (at least start/end events)
              final var elementInstances =
                  client
                      .newElementInstanceSearchRequest()
                      .filter(f -> f.processInstanceKey(processInstanceKey))
                      .send()
                      .join()
                      .items();
              assertThat(elementInstances).isNotEmpty();
            });
  }

  /**
   * Returns a map of data verifiers for exhaustive checking of all process instance related data in
   * secondary storage.
   */
  private Map<String, Supplier<Collection<?>>> getDataVerifiers(
      final CamundaClient client, final long processInstanceKey) {
    return Map.of(
        "process instance",
        () ->
            client
                .newProcessInstanceSearchRequest()
                .filter(f -> f.processInstanceKey(processInstanceKey))
                .send()
                .join()
                .items(),
        "element instance",
        () ->
            client
                .newElementInstanceSearchRequest()
                .filter(f -> f.processInstanceKey(processInstanceKey))
                .send()
                .join()
                .items(),
        "variable",
        () ->
            client
                .newVariableSearchRequest()
                .filter(f -> f.processInstanceKey(processInstanceKey))
                .send()
                .join()
                .items(),
        "incident",
        () ->
            client
                .newIncidentSearchRequest()
                .filter(f -> f.processInstanceKey(processInstanceKey))
                .send()
                .join()
                .items(),
        "job",
        () ->
            client
                .newJobSearchRequest()
                .filter(f -> f.processInstanceKey(processInstanceKey))
                .send()
                .join()
                .items(),
        "user task",
        () ->
            client
                .newUserTaskSearchRequest()
                .filter(f -> f.processInstanceKey(processInstanceKey))
                .send()
                .join()
                .items(),
        "decision instance",
        () ->
            client
                .newDecisionInstanceSearchRequest()
                .filter(f -> f.processInstanceKey(processInstanceKey))
                .send()
                .join()
                .items(),
        "correlated message subscription",
        () ->
            client
                .newCorrelatedMessageSubscriptionSearchRequest()
                .filter(f -> f.processInstanceKey(processInstanceKey))
                .send()
                .join()
                .items(),
        "sequence flow",
        () -> client.newProcessInstanceSequenceFlowsRequest(processInstanceKey).send().join());
  }
}
