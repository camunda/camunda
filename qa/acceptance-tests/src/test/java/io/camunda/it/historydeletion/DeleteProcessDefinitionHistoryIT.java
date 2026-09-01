/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.historydeletion;

import static io.camunda.cluster.PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID;
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
import io.camunda.client.api.search.enums.BatchOperationType;
import io.camunda.client.api.search.enums.MessageSubscriptionType;
import io.camunda.client.api.search.enums.ProcessDefinitionState;
import io.camunda.client.api.search.enums.ProcessInstanceState;
import io.camunda.configuration.HistoryDeletion;
import io.camunda.qa.util.multidb.CamundaMultiDBExtension;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.search.clients.reader.PhysicalTenantSearchClientReaders;
import io.camunda.search.query.AgentDefinitionQuery;
import io.camunda.security.core.authz.ResourceAccessChecks;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.test.util.Strings;
import java.time.Duration;
import java.util.Optional;
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
  private static final String AGENT_ELEMENT_ID = "agentAhsp";
  private static final String AGENT_JOB_TYPE = "agent-task";
  private static final String AGENT_ELEMENT_ID_V1_1 = "agentAhspV1First";
  private static final String AGENT_ELEMENT_ID_V1_2 = "agentAhspV1Second";
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

    // then - no batch is returned; draining finalizes the deletion asynchronously
    assertThat(result.getCreateBatchOperationResponse()).isNull();
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

    // then - no batch is returned; draining finalizes the deletion asynchronously
    assertThat(result.getCreateBatchOperationResponse()).isNull();
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
  void shouldMarkProcessDefinitionAsDeletedWhenDeletedWithoutHistory() {
    // given
    final var processId = Strings.newRandomValidBpmnId();
    final var process =
        deployProcessAndWaitForIt(
            camundaClient,
            Bpmn.createExecutableProcess(processId).startEvent().endEvent().done(),
            processId + ".bpmn");
    final var processDefinitionKey = process.getProcessDefinitionKey();

    // when - delete without history (keeps secondary storage records but marks as deleted)
    final DeleteResourceResponse result =
        camundaClient
            .newDeleteResourceCommand(processDefinitionKey)
            .deleteHistory(false)
            .send()
            .join();
    assertThat(result.getCreateBatchOperationResponse()).isNull();

    // then - wait until the process definition is marked as deleted in secondary storage
    Awaitility.await("Process definition should be marked as deleted")
        .atMost(DELETION_TIMEOUT)
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final var deleted =
                  camundaClient
                      .newProcessDefinitionSearchRequest()
                      .filter(
                          f ->
                              f.processDefinitionKey(processDefinitionKey)
                                  .state(ProcessDefinitionState.DELETED))
                      .send()
                      .join()
                      .items();
              assertThat(deleted).hasSize(1);
              assertThat(deleted.getFirst().getState()).isEqualTo(ProcessDefinitionState.DELETED);
            });

    // and - searching with state=ACTIVE should not return the deleted definition
    final var nonDeleted =
        camundaClient
            .newProcessDefinitionSearchRequest()
            .filter(
                f ->
                    f.processDefinitionKey(processDefinitionKey)
                        .state(ProcessDefinitionState.ACTIVE))
            .send()
            .join()
            .items();
    assertThat(nonDeleted).isEmpty();
  }

  @Test
  void shouldMarkProcessDefinitionAsDrainingWhileInstanceStillRunning() {
    // given - a definition kept alive by a still-running instance (a user task never completes on
    // its own, so the instance stays active and prevents the deletion from finalizing)
    final var processId = Strings.newRandomValidBpmnId();
    final var process =
        deployProcessAndWaitForIt(
            camundaClient,
            Bpmn.createExecutableProcess(processId).startEvent().userTask("wait").endEvent().done(),
            processId + ".bpmn");
    final var processDefinitionKey = process.getProcessDefinitionKey();
    startProcessInstance(camundaClient, processId);
    waitForProcessInstances(
        camundaClient, f -> f.processDefinitionId(processId).state(ProcessInstanceState.ACTIVE), 1);

    // when - delete: the running instance blocks finalization, so the definition drains rather than
    // being deleted outright
    final DeleteResourceResponse result =
        camundaClient
            .newDeleteResourceCommand(processDefinitionKey)
            .deleteHistory(false)
            .send()
            .join();
    assertThat(result.getCreateBatchOperationResponse()).isNull();

    // then - the DRAINING state is propagated into secondary storage
    Awaitility.await("Process definition should be marked as draining")
        .atMost(DELETION_TIMEOUT)
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final var draining =
                  camundaClient
                      .newProcessDefinitionSearchRequest()
                      .filter(
                          f ->
                              f.processDefinitionKey(processDefinitionKey)
                                  .state(ProcessDefinitionState.DRAINING))
                      .send()
                      .join()
                      .items();
              assertThat(draining).hasSize(1);
              assertThat(draining.getFirst().getState()).isEqualTo(ProcessDefinitionState.DRAINING);
            });

    // and - a draining definition is no longer returned as ACTIVE
    final var active =
        camundaClient
            .newProcessDefinitionSearchRequest()
            .filter(
                f ->
                    f.processDefinitionKey(processDefinitionKey)
                        .state(ProcessDefinitionState.ACTIVE))
            .send()
            .join()
            .items();
    assertThat(active).isEmpty();
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

    // then - the definition is already gone from primary storage, so this delete purges the
    // leftover history directly and returns the batch operation details
    final var batchOperation = resultWithHistory.getCreateBatchOperationResponse();
    assertThat(batchOperation).isNotNull();
    assertThat(batchOperation.getBatchOperationKey()).isNotNull();
    assertThat(Long.valueOf(batchOperation.getBatchOperationKey())).isGreaterThan(0);
    assertThat(batchOperation.getBatchOperationType())
        .isEqualTo(BatchOperationType.DELETE_PROCESS_INSTANCE);

    final var batchOperationKey = batchOperation.getBatchOperationKey();
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
    assertThat(firstResult.getCreateBatchOperationResponse()).isNull();
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

  @Test
  void shouldDeleteAgentDefinitionsWhenDeletingProcessDefinitionWithHistory() {
    // given - two versions of one process, each with an agent-calling element, plus a second,
    // unrelated process with its own agent-calling element
    final var processId = Strings.newRandomValidBpmnId();
    // v1 has two agent-calling elements, so the delete assertion below proves a whole version's
    // agent definitions are removed together, not just one
    final var processV1 =
        deployProcessAndWaitForIt(
            camundaClient,
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .adHocSubProcess(AGENT_ELEMENT_ID_V1_1, p -> p.task("agentTask1"))
                .zeebeJobType(AGENT_JOB_TYPE)
                .zeebeAiAgentSubProcessDefinition()
                .adHocSubProcess(AGENT_ELEMENT_ID_V1_2, p -> p.task("agentTask2"))
                .zeebeJobType(AGENT_JOB_TYPE)
                .zeebeAiAgentSubProcessDefinition()
                .endEvent("end")
                .done(),
            processId + "-v1.bpmn");
    final var processDefinitionKeyV1 = processV1.getProcessDefinitionKey();

    final var processV2 =
        deployProcessAndWaitForIt(
            camundaClient,
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .adHocSubProcess(AGENT_ELEMENT_ID, p -> p.task("agentTaskV2"))
                .zeebeJobType(AGENT_JOB_TYPE)
                .zeebeAiAgentSubProcessDefinition()
                .endEvent("end")
                .done(),
            processId + "-v2.bpmn");
    final var processDefinitionKeyV2 = processV2.getProcessDefinitionKey();

    assertThat(processDefinitionKeyV2)
        .describedAs(
            "v1 and v2 differ only by inner task id, so Zeebe treats them as distinct deployments")
        .isNotEqualTo(processDefinitionKeyV1);
    assertThat(processV1.getVersion()).isEqualTo(1);
    assertThat(processV2.getVersion()).isEqualTo(2);

    final var otherProcessId = Strings.newRandomValidBpmnId();
    final var otherProcess =
        deployProcessAndWaitForIt(
            camundaClient,
            Bpmn.createExecutableProcess(otherProcessId)
                .startEvent()
                .adHocSubProcess(AGENT_ELEMENT_ID, p -> p.task("agentTask"))
                .zeebeJobType(AGENT_JOB_TYPE)
                .zeebeAiAgentSubProcessDefinition()
                .endEvent("end")
                .done(),
            otherProcessId + ".bpmn");
    final var otherProcessDefinitionKey = otherProcess.getProcessDefinitionKey();

    // and - deploy-time creation of all agent definitions has completed (v1 has two, v2 and the
    // other process have one each)
    awaitAgentDefinitionCount(processDefinitionKeyV1, 2);
    awaitAgentDefinitionCount(processDefinitionKeyV2, 1);
    awaitAgentDefinitionCount(otherProcessDefinitionKey, 1);

    // when - only v1's history is hard-deleted
    camundaClient
        .newDeleteResourceCommand(processDefinitionKeyV1)
        .deleteHistory(true)
        .send()
        .join();
    assertProcessDefinitionDeleted(camundaClient, processDefinitionKeyV1);

    // then - both of v1's agent definitions are gone, while v2's and the other process's survive
    awaitAgentDefinitionCount(processDefinitionKeyV1, 0);
    awaitAgentDefinitionCount(processDefinitionKeyV2, 1);
    awaitAgentDefinitionCount(otherProcessDefinitionKey, 1);
  }

  @Test
  void shouldNotDeleteAgentDefinitionsWhenDeletingProcessDefinitionWithoutHistory() {
    // given - a deployed process with an agent-calling element
    final var processId = Strings.newRandomValidBpmnId();
    final var process =
        deployProcessAndWaitForIt(
            camundaClient,
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .adHocSubProcess(AGENT_ELEMENT_ID, p -> p.task("agentTask"))
                .zeebeJobType(AGENT_JOB_TYPE)
                .zeebeAiAgentSubProcessDefinition()
                .endEvent("end")
                .done(),
            processId + ".bpmn");
    final var processDefinitionKey = process.getProcessDefinitionKey();

    // and - deploy-time creation of the agent definition has completed
    awaitAgentDefinitionCount(processDefinitionKey, 1);

    // when - the process definition is deleted without history (undeploy only, no hard delete)
    final DeleteResourceResponse result =
        camundaClient
            .newDeleteResourceCommand(processDefinitionKey)
            .deleteHistory(false)
            .send()
            .join();
    assertThat(result.getCreateBatchOperationResponse()).isNull();

    // then - the process definition is marked as deleted in secondary storage, which confirms
    // the deletion pipeline has run
    Awaitility.await("Process definition should be marked as deleted")
        .atMost(DELETION_TIMEOUT)
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final var deleted =
                  camundaClient
                      .newProcessDefinitionSearchRequest()
                      .filter(
                          f ->
                              f.processDefinitionKey(processDefinitionKey)
                                  .state(ProcessDefinitionState.DELETED))
                      .send()
                      .join()
                      .items();
              assertThat(deleted).hasSize(1);
            });

    // and - its agent definition is untouched, because undeploy without history must not remove
    // secondary storage data
    awaitAgentDefinitionCount(processDefinitionKey, 1);
  }

  private void awaitAgentDefinitionCount(final long processDefinitionKey, final int expectedCount) {
    // under physical-tenant mode, camundaClient routes deploys through the tenant-scoped REST
    // path, so the process (and its agent definitions) land in that tenant's own store, not
    // "default" - read back from the same physical tenant the client wrote to
    final var physicalTenantId =
        Optional.ofNullable(CamundaMultiDBExtension.getPhysicalTenant())
            .orElse(DEFAULT_PHYSICAL_TENANT_ID);
    Awaitility.await(
            "Agent definitions for process definition "
                + processDefinitionKey
                + " should number "
                + expectedCount)
        .atMost(DELETION_TIMEOUT)
        .ignoreExceptions()
        .untilAsserted(
            () ->
                assertThat(
                        BROKER
                            .bean(PhysicalTenantSearchClientReaders.class)
                            .readersByPhysicalTenant()
                            .get(physicalTenantId)
                            .agentDefinitionReader()
                            .search(
                                AgentDefinitionQuery.of(
                                    b ->
                                        b.filter(
                                            f -> f.processDefinitionKeys(processDefinitionKey))),
                                ResourceAccessChecks.disabled())
                            .items())
                    .hasSize(expectedCount));
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
