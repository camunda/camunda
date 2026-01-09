/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.client;

import static io.camunda.it.util.TestHelper.deployProcessAndWaitForIt;
import static io.camunda.it.util.TestHelper.getScopedVariables;
import static io.camunda.it.util.TestHelper.startScopedProcessInstance;
import static io.camunda.it.util.TestHelper.waitForBatchOperationCompleted;
import static io.camunda.it.util.TestHelper.waitForBatchOperationWithCorrectTotalCount;
import static io.camunda.it.util.TestHelper.waitForProcessInstances;
import static io.camunda.it.util.TestHelper.waitForProcessInstancesToBeCompleted;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.configuration.HistoryDeletion;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

@MultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
public class BatchOperationDeleteProcessInstanceIT {

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
  String testScopeId;
  final String processName = "processA";

  @BeforeEach
  public void beforeEach(final TestInfo testInfo) {
    Objects.requireNonNull(camundaClient);
    testScopeId =
        testInfo.getTestMethod().map(Method::toString).orElse(UUID.randomUUID().toString());

    deployProcessAndWaitForIt(
        camundaClient,
        Bpmn.createExecutableProcess(processName).startEvent().endEvent().done(),
        processName + ".bpmn");
    startScopedProcessInstance(camundaClient, processName, testScopeId);
    startScopedProcessInstance(camundaClient, processName, testScopeId);

    waitForProcessInstancesToBeCompleted(camundaClient, 2);
  }

  @Test
  void shouldDeleteProcessInstancesWithBatch() {
    // when
    final var result =
        camundaClient
            .newCreateBatchOperationCommand()
            .processInstanceDelete()
            .filter(f -> f.variables(getScopedVariables(testScopeId)))
            .send()
            .join();

    // then
    assertThat(result).isNotNull();
    final var batchOperationKey = result.getBatchOperationKey();

    // and check that batch operation has been created with correct operation count
    waitForBatchOperationWithCorrectTotalCount(camundaClient, batchOperationKey, 2);
    waitForBatchOperationCompleted(camundaClient, batchOperationKey, 2, 0);

    // and check that process instances have been deleted
    waitForProcessInstances(camundaClient, f -> f.variables(getScopedVariables(testScopeId)), 0);
  }
}
