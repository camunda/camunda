/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.client;

import static io.camunda.it.util.TestHelper.deployProcessAndWaitForIt;
import static io.camunda.it.util.TestHelper.startProcessInstance;
import static io.camunda.it.util.TestHelper.waitForBatchOperationWithCorrectTotalCount;
import static io.camunda.it.util.TestHelper.waitForProcessInstances;
import static io.camunda.it.util.TestHelper.waitForProcessInstancesToBeCompleted;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.DeleteProcessInstanceResponse;
import io.camunda.client.api.search.enums.BatchOperationType;
import io.camunda.configuration.HistoryDeletion;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import java.time.Duration;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

@MultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
public class DeleteProcessInstanceIT {

  @MultiDbTestApplication
  private static final TestStandaloneBroker BROKER =
      new TestStandaloneBroker()
          .withDataConfig(
              config -> {
                final var historyDeletionConfig = new HistoryDeletion();
                historyDeletionConfig.setDelayBetweenRuns(Duration.ofMillis(100));
                historyDeletionConfig.setMaxDelayBetweenRuns(Duration.ofMillis(100));
                config.setHistoryDeletion(historyDeletionConfig);
              });

  private static CamundaClient camundaClient;
  final String processName = "processA";
  long processInstanceKey;

  @BeforeEach
  public void beforeEach(final TestInfo testInfo) {
    Objects.requireNonNull(camundaClient);

    deployProcessAndWaitForIt(
        camundaClient,
        Bpmn.createExecutableProcess(processName).startEvent().endEvent().done(),
        processName + ".bpmn");
    processInstanceKey = startProcessInstance(camundaClient, processName).getProcessInstanceKey();

    waitForProcessInstancesToBeCompleted(
        camundaClient, f -> f.processInstanceKey(processInstanceKey), 1);
  }

  @Test
  void shouldDeleteProcessInstance() {
    // when
    final DeleteProcessInstanceResponse result =
        camundaClient.newDeleteInstanceCommand(processInstanceKey).send().join();

    // then
    assertThat(result).isNotNull();
    assertThat(result.getBatchOperationType())
        .isEqualTo(BatchOperationType.DELETE_PROCESS_INSTANCE);

    // and check that batch operation has been created with correct operation count
    waitForBatchOperationWithCorrectTotalCount(camundaClient, result.getBatchOperationKey(), 1);

    // and check that process instance has been deleted
    waitForProcessInstances(camundaClient, f -> f.processInstanceKey(processInstanceKey), 0);
  }
}
