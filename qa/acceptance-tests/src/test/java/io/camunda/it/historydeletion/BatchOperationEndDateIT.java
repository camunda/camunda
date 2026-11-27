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
import static io.camunda.it.util.TestHelper.waitForBatchOperationWithCorrectTotalCount;
import static io.camunda.it.util.TestHelper.waitForProcessInstances;
import static io.camunda.it.util.TestHelper.waitForProcessInstancesToBeCompleted;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.enums.BatchOperationState;
import io.camunda.configuration.data.HistoryDeletion;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.test.util.Strings;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

@MultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
public class BatchOperationEndDateIT {

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
                // slow enough for the polling below to observe a batch operation whose items are
                // still active
                final var historyDeletionDelay = Duration.ofSeconds(3);
                final var historyDeletionConfig = new HistoryDeletion();
                historyDeletionConfig.setDelayBetweenRuns(historyDeletionDelay);
                historyDeletionConfig.setMaxDelayBetweenRuns(historyDeletionDelay);
                config.setHistoryDeletion(historyDeletionConfig);
              });

  private static final Duration TIMEOUT = Duration.ofSeconds(60);
  private static CamundaClient camundaClient;

  @Test
  void shouldSetEndDateOfHistoryDeletionOnlyAfterAllItemsSettled() {
    // given - one completed and one active instance of the same process definition, so that one
    // item can be deleted and the other one is rejected by the engine
    final var processId = Strings.newRandomValidBpmnId();
    final var jobType = Strings.newRandomValidBpmnId();
    deployProcessAndWaitForIt(
        camundaClient,
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .serviceTask("task", t -> t.zeebeJobType(jobType))
            .endEvent()
            .done(),
        processId + ".bpmn");
    startProcessInstance(camundaClient, processId);
    startProcessInstance(camundaClient, processId);
    waitForProcessInstances(camundaClient, f -> f.processDefinitionId(processId), 2);
    completeOneJob(jobType);
    waitForProcessInstancesToBeCompleted(camundaClient, f -> f.processDefinitionId(processId), 1);

    // when
    final var batchOperationKey =
        camundaClient
            .newCreateBatchOperationCommand()
            .deleteProcessInstance()
            .filter(f -> f.processDefinitionId(processId))
            .send()
            .join()
            .getBatchOperationKey();
    waitForBatchOperationWithCorrectTotalCount(camundaClient, batchOperationKey, 2);

    // then - the batch operation is never reported as ended while an item is unfinished
    final List<String> endedWithUnfinishedItems = new ArrayList<>();
    final var finished =
        Awaitility.await("batch operation should end")
            .atMost(TIMEOUT)
            .ignoreExceptions()
            .until(
                () -> {
                  final var batch =
                      camundaClient.newBatchOperationGetRequest(batchOperationKey).send().join();
                  if (batch.getEndDate() != null
                      && batch.getOperationsCompletedCount() + batch.getOperationsFailedCount()
                          < batch.getOperationsTotalCount()) {
                    endedWithUnfinishedItems.add(
                        "endDate=%s, completed=%d, failed=%d, total=%d"
                            .formatted(
                                batch.getEndDate(),
                                batch.getOperationsCompletedCount(),
                                batch.getOperationsFailedCount(),
                                batch.getOperationsTotalCount()));
                  }
                  return batch;
                },
                batch -> batch.getEndDate() != null);

    assertThat(endedWithUnfinishedItems).isEmpty();
    assertThat(finished.getStatus()).isEqualTo(BatchOperationState.COMPLETED);
    assertThat(finished.getOperationsTotalCount()).isEqualTo(2);
    assertThat(finished.getOperationsCompletedCount()).isEqualTo(1);
    assertThat(finished.getOperationsFailedCount()).isEqualTo(1);
  }

  private void completeOneJob(final String jobType) {
    Awaitility.await("a job should be available")
        .atMost(TIMEOUT)
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final var jobs =
                  camundaClient
                      .newJobSearchRequest()
                      .filter(f -> f.type(jobType))
                      .send()
                      .join()
                      .items();
              assertThat(jobs).hasSize(2);
              camundaClient.newCompleteCommand(jobs.getFirst().getJobKey()).send().join();
            });
  }
}
