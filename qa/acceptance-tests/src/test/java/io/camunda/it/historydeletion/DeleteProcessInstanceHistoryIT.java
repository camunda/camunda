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
import static io.camunda.it.util.TestHelper.waitForProcessInstancesToBeCompleted;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ProblemException;
import io.camunda.client.api.response.DeleteProcessInstanceResponse;
import io.camunda.client.api.search.enums.BatchOperationState;
import io.camunda.client.api.search.filter.ProcessInstanceFilter;
import io.camunda.client.api.search.response.ProcessInstance;
import io.camunda.configuration.HistoryDeletion;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.test.util.Strings;
import java.time.Duration;
import java.util.Map;
import java.util.function.Consumer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

@MultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
public class DeleteProcessInstanceHistoryIT {

  public static final String MESSAGE_NAME = "testMessage";

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
  void shouldDeleteTwoProcessInstancesByProcessDefinitionIdUsingBatchOperation() {
    // given - 2 completed process instances
    final var processId = Strings.newRandomValidBpmnId();
    deployProcessAndWaitForIt(
        camundaClient,
        Bpmn.createExecutableProcess(processId).startEvent().endEvent().done(),
        processId + ".bpmn");
    final long piKey1 = startProcessInstance(camundaClient, processId).getProcessInstanceKey();
    final long piKey2 = startProcessInstance(camundaClient, processId).getProcessInstanceKey();
    waitForProcessInstances(camundaClient, f -> f.processDefinitionId(processId), 2);

    // when - delete by process definition id
    final var batchResult =
        camundaClient
            .newCreateBatchOperationCommand()
            .processInstanceDelete()
            .filter(f -> f.processDefinitionId(processId))
            .send()
            .join();

    // then - batch operation should be created and complete successfully
    assertThat(batchResult).isNotNull();
    assertThat(batchResult.getBatchOperationKey()).isNotNull();
    waitForBatchOperationWithCorrectTotalCount(
        camundaClient, batchResult.getBatchOperationKey(), 2);
    waitForBatchOperationCompleted(camundaClient, batchResult.getBatchOperationKey(), 2, 0);

    // and - all related data should be deleted
    assertAllProcessInstanceDependantDataDeleted(camundaClient, piKey1);
    assertAllProcessInstanceDependantDataDeleted(camundaClient, piKey2);
  }

  @Test
  void shouldDeleteSubProcessInstancesByProcessDefinitionIdUsingBatchOperation() {
    // given - 2 completed process instances
    final var subProcessId = Strings.newRandomValidBpmnId();
    deployProcessAndWaitForIt(
        camundaClient,
        Bpmn.createExecutableProcess(subProcessId).startEvent().endEvent().done(),
        subProcessId + ".bpmn");
    final var rootProcessId = Strings.newRandomValidBpmnId();
    deployProcessAndWaitForIt(
        camundaClient,
        Bpmn.createExecutableProcess(rootProcessId)
            .startEvent()
            .callActivity()
            .zeebeProcessId(subProcessId)
            .endEvent()
            .done(),
        rootProcessId + ".bpmn");
    final long rootProcessInstanceKey =
        startProcessInstance(camundaClient, rootProcessId).getProcessInstanceKey();
    final Consumer<ProcessInstanceFilter> subProcessFilter =
        f -> f.processDefinitionId(subProcessId);
    waitForProcessInstances(camundaClient, subProcessFilter, 1);

    final var subprocessSearchResult =
        camundaClient.newProcessInstanceSearchRequest().filter(subProcessFilter).send().join();

    assertThat(subprocessSearchResult.items()).hasSize(1);
    final ProcessInstance subprocessInstance = subprocessSearchResult.items().getFirst();
    assertThat(subprocessInstance.getProcessInstanceKey()).isNotEqualTo(rootProcessInstanceKey);
    assertThat(subprocessInstance.getRootProcessInstanceKey()).isEqualTo(rootProcessInstanceKey);

    // when - delete by (sub) process definition id
    final var batchResult =
        camundaClient
            .newCreateBatchOperationCommand()
            .processInstanceDelete()
            .filter(f -> f.processDefinitionId(subProcessId))
            .send()
            .join();

    // then - batch operation should be created and complete successfully
    assertThat(batchResult).isNotNull();
    assertThat(batchResult.getBatchOperationKey()).isNotNull();
    waitForBatchOperationWithCorrectTotalCount(
        camundaClient, batchResult.getBatchOperationKey(), 1);
    waitForBatchOperationCompleted(camundaClient, batchResult.getBatchOperationKey(), 1, 0);

    // and - all related data should be deleted
    assertAllProcessInstanceDependantDataDeleted(
        camundaClient, subprocessInstance.getProcessInstanceKey());
  }

  @Test
  void shouldDeleteOneOfTwoProcessInstancesByKeyUsingBatchOperation() {
    // given - 2 completed process instances
    final var processId = Strings.newRandomValidBpmnId();
    deployProcessAndWaitForIt(
        camundaClient,
        Bpmn.createExecutableProcess(processId).startEvent().endEvent().done(),
        processId + ".bpmn");
    final long piKey1 = startProcessInstance(camundaClient, processId).getProcessInstanceKey();
    final long piKey2 = startProcessInstance(camundaClient, processId).getProcessInstanceKey();
    waitForProcessInstances(camundaClient, f -> f.processDefinitionId(processId), 2);

    // when - delete only one by key
    final var batchResult =
        camundaClient
            .newCreateBatchOperationCommand()
            .processInstanceDelete()
            .filter(f -> f.processInstanceKey(piKey1))
            .send()
            .join();

    // then - batch operation should be created and complete successfully
    assertThat(batchResult).isNotNull();
    waitForBatchOperationWithCorrectTotalCount(
        camundaClient, batchResult.getBatchOperationKey(), 1);
    waitForBatchOperationCompleted(camundaClient, batchResult.getBatchOperationKey(), 1, 0);

    // and - first process instance should be deleted
    assertAllProcessInstanceDependantDataDeleted(camundaClient, piKey1);

    // and - second process instance should still exist with all its data
    waitForProcessInstances(camundaClient, f -> f.processInstanceKey(piKey2), 1);
    assertProcessInstanceDataExists(camundaClient, piKey2);
  }

  @Test
  void shouldFailToDeleteActiveProcessInstanceWithBatchOperation() {
    // given - an active process instance
    final var processId = Strings.newRandomValidBpmnId();
    deployProcessAndWaitForIt(
        camundaClient,
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .serviceTask("task", t -> t.zeebeJobType("test"))
            .endEvent()
            .done(),
        processId + ".bpmn");
    final long piKey = startProcessInstance(camundaClient, processId).getProcessInstanceKey();
    waitForProcessInstances(camundaClient, f -> f.processInstanceKey(piKey), 1);

    // when - try to delete active process instance
    final var batchResult =
        camundaClient
            .newCreateBatchOperationCommand()
            .processInstanceDelete()
            .filter(f -> f.processInstanceKey(piKey))
            .send()
            .join();

    // then - batch operation should be created
    assertThat(batchResult).isNotNull();
    waitForBatchOperationWithCorrectTotalCount(
        camundaClient, batchResult.getBatchOperationKey(), 1);

    // and - operation should fail with proper error message
    Awaitility.await("batch operation should complete with failure")
        .atMost(DELETION_TIMEOUT)
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final var batch =
                  camundaClient
                      .newBatchOperationGetRequest(batchResult.getBatchOperationKey())
                      .send()
                      .join();
              assertThat(batch.getStatus()).isEqualTo(BatchOperationState.COMPLETED);
              assertThat(batch.getOperationsFailedCount()).isEqualTo(1);
              assertThat(batch.getOperationsCompletedCount()).isEqualTo(0);
            });

    // and - process instance should still exist
    waitForProcessInstances(camundaClient, f -> f.processInstanceKey(piKey), 1);
  }

  @Test
  void shouldDeleteProcessInstanceByUsingSingularDeletion() {
    // given - a completed process instance
    final var processId = Strings.newRandomValidBpmnId();
    deployProcessAndWaitForIt(
        camundaClient,
        Bpmn.createExecutableProcess(processId).startEvent().endEvent().done(),
        processId + ".bpmn");
    final long piKey = startProcessInstance(camundaClient, processId).getProcessInstanceKey();
    waitForProcessInstances(camundaClient, f -> f.processInstanceKey(piKey), 1);

    // when - delete using singular deletion
    final DeleteProcessInstanceResponse result =
        camundaClient.newDeleteInstanceCommand(piKey).send().join();

    // then - deletion should be successful
    assertThat(result).isNotNull();
    assertAllProcessInstanceDependantDataDeleted(camundaClient, piKey);
  }

  @Test
  void shouldDeleteOneOfTwoProcessInstancesByKeyUsingSingularDeletion() {
    // given - 2 completed process instances
    final var processId = Strings.newRandomValidBpmnId();
    deployProcessAndWaitForIt(
        camundaClient,
        Bpmn.createExecutableProcess(processId).startEvent().endEvent().done(),
        processId + ".bpmn");
    final long piKey1 = startProcessInstance(camundaClient, processId).getProcessInstanceKey();
    final long piKey2 = startProcessInstance(camundaClient, processId).getProcessInstanceKey();
    waitForProcessInstances(camundaClient, f -> f.processDefinitionId(processId), 2);

    // when - delete only one by key
    final DeleteProcessInstanceResponse result =
        camundaClient.newDeleteInstanceCommand(piKey1).send().join();

    // then - deletion should be successful
    assertThat(result).isNotNull();
    assertAllProcessInstanceDependantDataDeleted(camundaClient, piKey1);

    // and - second process instance should still exist with all its data
    waitForProcessInstances(camundaClient, f -> f.processInstanceKey(piKey2), 1);
    assertProcessInstanceDataExists(camundaClient, piKey2);
  }

  @Test
  void shouldFailToDeleteActiveProcessInstanceWithSingularDeletion() {
    // given - an active process instance
    final var processId = Strings.newRandomValidBpmnId();
    deployProcessAndWaitForIt(
        camundaClient,
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .serviceTask("task", t -> t.zeebeJobType("test"))
            .endEvent()
            .done(),
        processId + ".bpmn");
    final long piKey = startProcessInstance(camundaClient, processId).getProcessInstanceKey();
    waitForProcessInstances(camundaClient, f -> f.processInstanceKey(piKey), 1);

    // when
    final var resultFuture = camundaClient.newDeleteInstanceCommand(piKey).send();

    // then
    assertThatExceptionOfType(ProblemException.class)
        .isThrownBy(resultFuture::join)
        .satisfies(
            ex -> {
              final var details = ex.details();
              assertThat(details.getTitle()).isEqualTo("INVALID_STATE");
              assertThat(details.getStatus()).isEqualTo(409);
              assertThat(details.getDetail())
                  .contains(
                      "Expected to delete history for process instance with key '%d', but it is still active"
                          .formatted(piKey));
            });
    waitForProcessInstances(camundaClient, f -> f.processInstanceKey(piKey), 1);
  }

  @Test
  void shouldDeleteProcessInstanceWithJob() {
    // given - a completed process instance with job
    final var processId = Strings.newRandomValidBpmnId();
    deployProcessAndWaitForIt(
        camundaClient,
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .serviceTask("task", t -> t.zeebeJobType("test"))
            .endEvent()
            .done(),
        processId + ".bpmn");
    final var piKey = startAndCompleteProcessInstanceWithJob(camundaClient, processId);

    // when - delete using singular deletion
    final DeleteProcessInstanceResponse result =
        camundaClient.newDeleteInstanceCommand(piKey).send().join();

    // then - deletion should be successful
    assertThat(result).isNotNull();
    assertAllProcessInstanceDependantDataDeleted(camundaClient, piKey);
  }

  @Test
  void shouldDeleteProcessInstanceWithMessageCorrelation() {
    // given - a completed process instance with message correlation
    final var processId = Strings.newRandomValidBpmnId();
    deployProcessAndWaitForIt(
        camundaClient,
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .intermediateCatchEvent(
                "messageEvent",
                e ->
                    e.message(
                        m -> m.name(MESSAGE_NAME).zeebeCorrelationKeyExpression("correlationKey")))
            .serviceTask("task", t -> t.zeebeJobType("test"))
            .endEvent()
            .done(),
        processId + ".bpmn");
    final long piKey =
        startAndCompleteProcessInstanceWithMessage(camundaClient, processId, processId);

    // when - delete using singular deletion
    final DeleteProcessInstanceResponse result =
        camundaClient.newDeleteInstanceCommand(piKey).send().join();

    // then - deletion should be successful
    assertThat(result).isNotNull();
    assertAllProcessInstanceDependantDataDeleted(camundaClient, piKey);
  }

  @Test
  void shouldDeleteProcessInstanceWithResolvedIncident() {
    // given - a completed process instance that had an incident which was resolved
    final var processId = Strings.newRandomValidBpmnId();
    deployProcessAndWaitForIt(
        camundaClient,
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .serviceTask("failingTask", t -> t.zeebeJobType("failingJob"))
            .endEvent()
            .done(),
        processId + ".bpmn");
    final long piKey =
        startAndCompleteProcessInstanceWithResolvedIncident(camundaClient, processId);

    // when - delete using singular deletion
    final DeleteProcessInstanceResponse result =
        camundaClient.newDeleteInstanceCommand(piKey).send().join();

    // then - deletion should be successful
    assertThat(result).isNotNull();
    assertAllProcessInstanceDependantDataDeleted(camundaClient, piKey);
  }

  @Test
  void shouldReturnNotFoundForNonExistingProcessInstanceWithSingularDeletion() {
    // given - non-existing process instance key
    final long nonExistingKey = 99999L;

    // when/then - try to delete non-existing process instance should throw not found exception
    assertThatExceptionOfType(ProblemException.class)
        .isThrownBy(() -> camundaClient.newDeleteInstanceCommand(nonExistingKey).send().join())
        .satisfies(
            exception -> {
              assertThat(exception.code()).isEqualTo(404);
              assertThat(exception.details().getDetail())
                  .containsIgnoringCase("process instance")
                  .containsIgnoringCase("not found");
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
   * Starts a process instance and completes it by completing all jobs.
   *
   * @return the process instance key
   */
  private long startAndCompleteProcessInstanceWithJob(
      final CamundaClient client, final String processId) {
    final long piKey = startProcessInstance(client, processId).getProcessInstanceKey();

    // Wait for job to be created and complete it
    Awaitility.await("Job should be available")
        .atMost(DELETION_TIMEOUT)
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final var jobs =
                  client
                      .newJobSearchRequest()
                      .filter(f -> f.processInstanceKey(piKey))
                      .send()
                      .join()
                      .items();
              assertThat(jobs).hasSize(1);
              final var jobKey = jobs.getFirst().getJobKey();
              client.newCompleteCommand(jobKey).send().join();
            });

    // Wait for process instance to be completed
    waitForProcessInstancesToBeCompleted(client, f -> f.processInstanceKey(piKey), 1);

    return piKey;
  }

  /**
   * Starts a process instance with message correlation and completes it.
   *
   * @return the process instance key
   */
  private long startAndCompleteProcessInstanceWithMessage(
      final CamundaClient client, final String correlationKey, final String processId) {
    // Start process instance with correlation key variable
    final long piKey =
        client
            .newCreateInstanceCommand()
            .bpmnProcessId(processId)
            .latestVersion()
            .variables(Map.of("correlationKey", correlationKey))
            .send()
            .join()
            .getProcessInstanceKey();

    // Wait for process instance to be at message catch event
    waitForProcessInstances(client, f -> f.processInstanceKey(piKey), 1);

    // Wait for message subscription to be created
    Awaitility.await("Message subscription should be created")
        .atMost(DELETION_TIMEOUT)
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final var subscriptions =
                  client
                      .newMessageSubscriptionSearchRequest()
                      .filter(f -> f.processInstanceKey(piKey))
                      .send()
                      .join()
                      .items();
              assertThat(subscriptions).hasSize(1);
            });

    // Correlate the message
    client
        .newCorrelateMessageCommand()
        .messageName(MESSAGE_NAME)
        .correlationKey(correlationKey)
        .send()
        .join();

    // Complete the job after message correlation
    Awaitility.await("Job should be available after message")
        .atMost(DELETION_TIMEOUT)
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final var jobs =
                  client
                      .newJobSearchRequest()
                      .filter(f -> f.processInstanceKey(piKey))
                      .send()
                      .join()
                      .items();
              assertThat(jobs).hasSize(1);
              final var jobKey = jobs.getFirst().getJobKey();
              client.newCompleteCommand(jobKey).send().join();
            });

    // Wait for process instance to be completed
    waitForProcessInstancesToBeCompleted(client, f -> f.processInstanceKey(piKey), 1);

    return piKey;
  }

  /**
   * Starts a process instance, creates an incident by failing a job, resolves it, and completes the
   * process.
   *
   * @return the process instance key
   */
  private long startAndCompleteProcessInstanceWithResolvedIncident(
      final CamundaClient client, final String processId) {
    final long piKey = startProcessInstance(client, processId).getProcessInstanceKey();

    // Wait for job to be created
    final long jobKey =
        Awaitility.await("Job should be available")
            .atMost(DELETION_TIMEOUT)
            .ignoreExceptions()
            .until(
                () -> {
                  final var jobs =
                      client
                          .newJobSearchRequest()
                          .filter(f -> f.processInstanceKey(piKey))
                          .send()
                          .join()
                          .items();
                  return jobs.isEmpty() ? null : jobs.getFirst().getJobKey();
                },
                key -> key != null);

    client
        .newFailCommand(jobKey)
        .retries(0)
        .errorMessage("Simulated failure to create incident")
        .send()
        .join();

    // Wait for incident to be created
    final long incidentKey =
        Awaitility.await("Incident should be created")
            .atMost(DELETION_TIMEOUT)
            .ignoreExceptions()
            .until(
                () -> {
                  final var incidents =
                      client
                          .newIncidentSearchRequest()
                          .filter(f -> f.processInstanceKey(piKey))
                          .send()
                          .join()
                          .items();
                  return incidents.isEmpty() ? null : incidents.getFirst().getIncidentKey();
                },
                key -> key != null);

    // Resolve the incident by updating job retries
    client.newUpdateRetriesCommand(jobKey).retries(3).send().join();

    // Resolve the incident
    client.newResolveIncidentCommand(incidentKey).send().join();

    // Complete the job
    Awaitility.await("Job should be available for completion")
        .atMost(DELETION_TIMEOUT)
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              client.newCompleteCommand(jobKey).send().join();
            });

    // Wait for process instance to be completed
    waitForProcessInstancesToBeCompleted(client, f -> f.processInstanceKey(piKey), 1);

    return piKey;
  }
}
