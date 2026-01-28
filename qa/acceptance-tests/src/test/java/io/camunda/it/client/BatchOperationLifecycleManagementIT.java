/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.client;

import static io.camunda.it.util.TestHelper.deployResource;
import static io.camunda.it.util.TestHelper.getScopedVariables;
import static io.camunda.it.util.TestHelper.startScopedProcessInstance;
import static io.camunda.it.util.TestHelper.waitForBatchOperationStatus;
import static io.camunda.it.util.TestHelper.waitForProcessesToBeDeployed;
import static io.camunda.it.util.TestHelper.waitForScopedProcessInstancesToStart;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.Process;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.client.api.search.enums.BatchOperationState;
import io.camunda.qa.util.compatibility.CompatibilityTest;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneApplication;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

@MultiDbTest
@CompatibilityTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
public class BatchOperationLifecycleManagementIT {

  private static CamundaClient camundaClient;

  @MultiDbTestApplication
  private static final TestStandaloneApplication<?> APPLICATION =
      new TestStandaloneBroker()
          .withUnauthenticatedAccess()
          .withUnifiedConfig(
              cfg ->
                  cfg.getProcessing()
                      .getEngine()
                      .getBatchOperations()
                      .setSchedulerInterval(Duration.ofDays(1)));

  String testScopeId;

  Process deployedProcess;
  final List<ProcessInstanceEvent> activeProcessInstances = new ArrayList<>();

  @BeforeEach
  public void beforeEach(final TestInfo testInfo) {
    Objects.requireNonNull(camundaClient);
    testScopeId =
        testInfo.getTestMethod().map(Method::toString).orElse(UUID.randomUUID().toString());

    deployedProcess =
        deployResource(camundaClient, "process/service_tasks_v1.bpmn").getProcesses().getFirst();

    waitForProcessesToBeDeployed(camundaClient, 1);

    IntStream.range(0, 10)
        .forEach(
            i ->
                activeProcessInstances.add(
                    startScopedProcessInstance(
                        camundaClient, "service_tasks_v1", testScopeId, Map.of("xyz", "bar"))));

    waitForScopedProcessInstancesToStart(camundaClient, testScopeId, activeProcessInstances.size());
  }

  @AfterEach
  void afterEach() {
    activeProcessInstances.clear();
  }

  @Test
  void shouldCancelBatchOperation() {
    // given a batch operation
    final var result =
        camundaClient
            .newCreateBatchOperationCommand()
            .processInstanceCancel()
            .filter(b -> b.variables(getScopedVariables(testScopeId)))
            .send()
            .join();
    final var batchOperationKey = result.getBatchOperationKey();
    assertThat(result).isNotNull();

    // when we cancel the batch operation
    camundaClient.newCancelBatchOperationCommand(batchOperationKey).send().join();

    // and
    waitForBatchOperationStatus(camundaClient, batchOperationKey, BatchOperationState.CANCELED);
  }

  @Test
  void shouldSuspendBatchOperation() {
    // given a batch operation
    final var result =
        camundaClient
            .newCreateBatchOperationCommand()
            .processInstanceCancel()
            .filter(b -> b.variables(getScopedVariables(testScopeId)))
            .send()
            .join();
    final var batchOperationKey = result.getBatchOperationKey();
    assertThat(result).isNotNull();

    // when we suspend the batch operation
    camundaClient.newSuspendBatchOperationCommand(batchOperationKey).send().join();

    // and
    waitForBatchOperationStatus(camundaClient, batchOperationKey, BatchOperationState.SUSPENDED);
  }

  @Test
  void shouldResumeBatchOperation() {
    // given a batch operation
    final var result =
        camundaClient
            .newCreateBatchOperationCommand()
            .processInstanceCancel()
            .filter(b -> b.variables(getScopedVariables(testScopeId)))
            .send()
            .join();
    final var batchOperationKey = result.getBatchOperationKey();
    assertThat(result).isNotNull();

    // and it is suspended
    camundaClient.newSuspendBatchOperationCommand(batchOperationKey).send().join();

    waitForBatchOperationStatus(camundaClient, batchOperationKey, BatchOperationState.SUSPENDED);

    // when we resume the batch operation
    camundaClient.newResumeBatchOperationCommand(batchOperationKey).send().join();

    // Then it should be activated again, and eventually completed
    // Note: The batch operation might complete too fast, or take too long to complete. So we wait
    // for either state.
    waitForBatchOperationStatus(
        camundaClient,
        batchOperationKey,
        Set.of(BatchOperationState.ACTIVE, BatchOperationState.COMPLETED));
  }
}
