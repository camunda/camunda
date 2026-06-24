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
import static io.camunda.it.util.TestHelper.waitForMessageSubscriptions;
import static io.camunda.it.util.TestHelper.waitForProcessInstances;
import static io.camunda.it.util.TestHelper.waitForProcesses;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ProblemException;
import io.camunda.client.api.response.DeleteResourceResponse;
import io.camunda.client.api.search.enums.MessageSubscriptionType;
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
public class DeleteProcessDefinitionHistoryIT {

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

  @Test
  void shouldDeleteProcessDefinitionHistoryAfterDeletingWithoutHistory() {
    // given - a deployed process definition with completed process instances
    final var processId = Strings.newRandomValidBpmnId();
    final var process =
        deployProcessAndWaitForIt(
            camundaClient,
            Bpmn.createExecutableProcess(processId).startEvent().endEvent().done(),
            processId + ".bpmn");
    final var processDefinitionKey = process.getProcessDefinitionKey();
    final long piKey1 = startProcessInstance(camundaClient, processId).getProcessInstanceKey();
    final long piKey2 = startProcessInstance(camundaClient, processId).getProcessInstanceKey();
    waitForProcessInstances(
        camundaClient,
        f -> f.processDefinitionId(processId).state(ProcessInstanceState.COMPLETED),
        2);

    // and - first delete without history, leaving process instances intact
    final DeleteResourceResponse resultWithoutHistory =
        camundaClient
            .newDeleteResourceCommand(processDefinitionKey)
            .deleteHistory(false)
            .send()
            .join();
    assertThat(resultWithoutHistory.getCreateBatchOperationResponse()).isNull();
    waitForProcessInstances(camundaClient, f -> f.processDefinitionId(processId), 2);

    // when - now delete with history to clean up the remaining historical data
    final DeleteResourceResponse resultWithHistory =
        camundaClient
            .newDeleteResourceCommand(processDefinitionKey)
            .deleteHistory(true)
            .send()
            .join();

    // then - the batch operation should be created and complete successfully
    final var batchOperationKey =
        resultWithHistory.getCreateBatchOperationResponse().getBatchOperationKey();
    waitForBatchOperationWithCorrectTotalCount(camundaClient, batchOperationKey, 2);
    waitForBatchOperationCompleted(camundaClient, batchOperationKey, 2, 0);
    assertAllProcessInstanceDependantDataDeleted(camundaClient, piKey1);
    assertAllProcessInstanceDependantDataDeleted(camundaClient, piKey2);
    assertProcessDefinitionDeleted(camundaClient, processDefinitionKey);
  }

  @Test
  void shouldReturnNotFoundWhenDeletingProcessDefinitionWithHistoryTwice() {
    // given - a deployed process with a completed instance, deleted with history
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

    final DeleteResourceResponse firstResult =
        camundaClient
            .newDeleteResourceCommand(processDefinitionKey)
            .deleteHistory(true)
            .send()
            .join();
    final var batchOperationKey =
        firstResult.getCreateBatchOperationResponse().getBatchOperationKey();
    waitForBatchOperationCompleted(camundaClient, batchOperationKey, 1, 0);
    assertAllProcessInstanceDependantDataDeleted(camundaClient, piKey);
    assertProcessDefinitionDeleted(camundaClient, processDefinitionKey);

    // when / then - a second delete with history should return not found
    assertThatExceptionOfType(ProblemException.class)
        .isThrownBy(
            () ->
                camundaClient
                    .newDeleteResourceCommand(processDefinitionKey)
                    .deleteHistory(true)
                    .send()
                    .join())
        .satisfies(
            exception -> {
              assertThat(exception.code()).isEqualTo(404);
              assertThat(exception.details().getDetail()).containsIgnoringCase("NOT_FOUND");
            });
  }

  @Test
  void shouldDeleteStartEventSubscriptionsWhenDeletingProcessDefinitionWithHistory() {
    // given - a deployed process with a message start event (no instances started)
    final var processId = Strings.newRandomValidBpmnId();
    final var messageName = "start-" + processId;
    final var process =
        deployProcessAndWaitForIt(
            camundaClient,
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .message(m -> m.name(messageName).id("startMessage"))
                .endEvent()
                .done(),
            processId + ".bpmn");
    final var processDefinitionKey = process.getProcessDefinitionKey();

    // wait for the start event subscription to be indexed
    waitForMessageSubscriptions(
        camundaClient,
        f ->
            f.processDefinitionKey(processDefinitionKey)
                .messageSubscriptionType(MessageSubscriptionType.START_EVENT),
        1);

    // when - delete the process definition with history
    camundaClient.newDeleteResourceCommand(processDefinitionKey).deleteHistory(true).send().join();

    // then - the process definition and its start event subscriptions are deleted
    assertProcessDefinitionDeleted(camundaClient, processDefinitionKey);
    Awaitility.await("Start event subscriptions should be deleted")
        .atMost(DELETION_TIMEOUT)
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final var subscriptions =
                  camundaClient
                      .newMessageSubscriptionSearchRequest()
                      .filter(
                          f ->
                              f.processDefinitionKey(processDefinitionKey)
                                  .messageSubscriptionType(MessageSubscriptionType.START_EVENT))
                      .send()
                      .join()
                      .items();
              assertThat(subscriptions).isEmpty();
            });
  }

  @Test
  void shouldNotDeleteStartEventSubscriptionsWhenDeletingProcessInstanceHistory() {
    // given - a process with a message start event that has created a process instance
    final var processId = Strings.newRandomValidBpmnId();
    final var messageName = "start-" + processId;
    final var process =
        deployProcessAndWaitForIt(
            camundaClient,
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .message(m -> m.name(messageName).id("startMessage"))
                .endEvent()
                .done(),
            processId + ".bpmn");
    final var processDefinitionKey = process.getProcessDefinitionKey();

    // wait for the start event subscription to appear
    waitForMessageSubscriptions(
        camundaClient,
        f ->
            f.processDefinitionKey(processDefinitionKey)
                .messageSubscriptionType(MessageSubscriptionType.START_EVENT),
        1);

    // trigger the start event to create a process instance and wait for it to complete
    camundaClient
        .newPublishMessageCommand()
        .messageName(messageName)
        .correlationKey("")
        .send()
        .join();
    waitForProcessInstances(
        camundaClient,
        f -> f.processDefinitionId(processId).state(ProcessInstanceState.COMPLETED),
        1);
    final var piKey =
        camundaClient
            .newProcessInstanceSearchRequest()
            .filter(f -> f.processDefinitionId(processId))
            .send()
            .join()
            .items()
            .getFirst()
            .getProcessInstanceKey();

    // when - delete the process instance history
    camundaClient.newDeleteProcessInstanceCommand(piKey).send().join();
    assertAllProcessInstanceDependantDataDeleted(camundaClient, piKey);

    // then - start event subscription should still exist after PI history deletion
    final var remainingSubscriptions =
        camundaClient
            .newMessageSubscriptionSearchRequest()
            .filter(
                f ->
                    f.processDefinitionKey(processDefinitionKey)
                        .messageSubscriptionType(MessageSubscriptionType.START_EVENT))
            .send()
            .join()
            .items();
    assertThat(remainingSubscriptions).hasSize(1);
  }

  @Test
  void shouldNotDeleteStartEventSubscriptionsOfOtherProcessDefinition() {
    // given - two process definitions each with a message start event
    final var processIdA = Strings.newRandomValidBpmnId();
    final var messageNameA = "start-" + processIdA;
    final var processA =
        deployProcessAndWaitForIt(
            camundaClient,
            Bpmn.createExecutableProcess(processIdA)
                .startEvent()
                .message(m -> m.name(messageNameA).id("startMessage"))
                .endEvent()
                .done(),
            processIdA + ".bpmn");
    final var processDefinitionKeyA = processA.getProcessDefinitionKey();

    final var processIdB = Strings.newRandomValidBpmnId();
    final var messageNameB = "start-" + processIdB;
    final var processB =
        deployProcessAndWaitForIt(
            camundaClient,
            Bpmn.createExecutableProcess(processIdB)
                .startEvent()
                .message(m -> m.name(messageNameB).id("startMessage"))
                .endEvent()
                .done(),
            processIdB + ".bpmn");
    final var processDefinitionKeyB = processB.getProcessDefinitionKey();

    waitForMessageSubscriptions(
        camundaClient,
        f ->
            f.processDefinitionKey(processDefinitionKeyA)
                .messageSubscriptionType(MessageSubscriptionType.START_EVENT),
        1);
    waitForMessageSubscriptions(
        camundaClient,
        f ->
            f.processDefinitionKey(processDefinitionKeyB)
                .messageSubscriptionType(MessageSubscriptionType.START_EVENT),
        1);

    // when - delete only process definition A with history
    camundaClient.newDeleteResourceCommand(processDefinitionKeyA).deleteHistory(true).send().join();

    // then - A's subscriptions are deleted, B's subscriptions remain untouched
    assertProcessDefinitionDeleted(camundaClient, processDefinitionKeyA);
    Awaitility.await("Start event subscriptions of A should be deleted")
        .atMost(DELETION_TIMEOUT)
        .ignoreExceptions()
        .untilAsserted(
            () ->
                assertThat(
                        camundaClient
                            .newMessageSubscriptionSearchRequest()
                            .filter(
                                f ->
                                    f.processDefinitionKey(processDefinitionKeyA)
                                        .messageSubscriptionType(
                                            MessageSubscriptionType.START_EVENT))
                            .send()
                            .join()
                            .items())
                    .isEmpty());

    final var remainingSubscriptions =
        camundaClient
            .newMessageSubscriptionSearchRequest()
            .filter(
                f ->
                    f.processDefinitionKey(processDefinitionKeyB)
                        .messageSubscriptionType(MessageSubscriptionType.START_EVENT))
            .send()
            .join()
            .items();
    assertThat(remainingSubscriptions).hasSize(1);
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
