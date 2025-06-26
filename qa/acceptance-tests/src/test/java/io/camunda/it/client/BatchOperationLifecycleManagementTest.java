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
import io.camunda.qa.util.multidb.MultiDbTest;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

@MultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
public class BatchOperationLifecycleManagementTest {

  private static CamundaClient camundaClient;

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
    final var batchOperationId = result.getBatchOperationId();
    assertThat(result).isNotNull();

    // when we cancel the batch operation
    camundaClient.newCancelBatchOperationCommand(batchOperationId).send().join();

    // and
    waitForBatchOperationStatus(camundaClient, batchOperationId, BatchOperationState.CANCELED);
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
    final var batchOperationId = result.getBatchOperationId();
    assertThat(result).isNotNull();

    // when we suspend the batch operation
    camundaClient.newSuspendBatchOperationCommand(batchOperationId).send().join();

    // and
    waitForBatchOperationStatus(camundaClient, batchOperationId, BatchOperationState.SUSPENDED);
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
    final var batchOperationId = result.getBatchOperationId();
    assertThat(result).isNotNull();

    // and it is suspended
    camundaClient.newSuspendBatchOperationCommand(batchOperationId).send().join();

    waitForBatchOperationStatus(camundaClient, batchOperationId, BatchOperationState.SUSPENDED);

    // when we resume the batch operation
    camundaClient.newResumeBatchOperationCommand(batchOperationId).send().join();

    // then it's again active and can complete
    waitForBatchOperationStatus(camundaClient, batchOperationId, BatchOperationState.COMPLETED);
  }
}
