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
import static io.camunda.it.util.TestHelper.waitForBatchOperationCompleted;
import static io.camunda.it.util.TestHelper.waitForBatchOperationWithCorrectTotalCount;
import static io.camunda.it.util.TestHelper.waitForProcessInstanceToBeTerminated;
import static io.camunda.it.util.TestHelper.waitForProcessesToBeDeployed;
import static io.camunda.it.util.TestHelper.waitForScopedProcessInstancesToStart;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.Process;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.client.api.search.enums.BatchOperationItemState;
import io.camunda.client.api.search.response.BatchOperationItems.BatchOperationItem;
import io.camunda.qa.util.multidb.MultiDbTest;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

@MultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
public class BatchOperationCancelProcessInstanceTest {

  private static CamundaClient camundaClient;

  String testScopeId;

  final List<Process> deployedProcesses = new ArrayList<>();
  final List<ProcessInstanceEvent> activeProcessInstances = new ArrayList<>();

  @BeforeEach
  public void beforeEach(final TestInfo testInfo) {
    Objects.requireNonNull(camundaClient);
    testScopeId =
        testInfo.getTestMethod().map(Method::toString).orElse(UUID.randomUUID().toString());

    final List<String> processes = List.of("service_tasks_v1.bpmn", "service_tasks_v2.bpmn");

    processes.forEach(
        process ->
            deployedProcesses.addAll(
                deployResource(camundaClient, String.format("process/%s", process))
                    .getProcesses()));

    waitForProcessesToBeDeployed(camundaClient, deployedProcesses.size());

    activeProcessInstances.add(
        startScopedProcessInstance(
            camundaClient, "service_tasks_v1", testScopeId, Map.of("xyz", "bar")));
    activeProcessInstances.add(
        startScopedProcessInstance(
            camundaClient, "service_tasks_v2", testScopeId, Map.of("path", "222")));

    waitForScopedProcessInstancesToStart(camundaClient, testScopeId, activeProcessInstances.size());
  }

  @AfterEach
  void afterEach() {
    deployedProcesses.clear();
    activeProcessInstances.clear();
  }

  @Test
  void shouldCancelProcessInstancesWithBatch() {
    // when
    final var result =
        camundaClient
            .newCreateBatchOperationCommand()
            .processInstanceCancel()
            .filter(b -> b.variables(getScopedVariables(testScopeId)))
            .send()
            .join();
    final var batchOperationKey = result.getBatchOperationKey();

    // then
    assertThat(result).isNotNull();

    // and wait if batch has correct amount of items. (To fail fast if not)
    waitForBatchOperationWithCorrectTotalCount(
        camundaClient, batchOperationKey, activeProcessInstances.size());

    // and
    waitForBatchOperationCompleted(
        camundaClient, batchOperationKey, activeProcessInstances.size(), 0);

    // and
    final var activeKeys =
        activeProcessInstances.stream().map(ProcessInstanceEvent::getProcessInstanceKey).toList();
    for (final Long key : activeKeys) {
      waitForProcessInstanceToBeTerminated(camundaClient, key);
    }

    // and
    final var itemsObj =
        camundaClient
            .newBatchOperationItemsSearchRequest()
            .filter(f -> f.batchOperationKey(batchOperationKey))
            .send()
            .join();
    final var itemKeys = itemsObj.items().stream().map(BatchOperationItem::getItemKey).toList();

    assertThat(itemsObj.items()).hasSize(activeProcessInstances.size());
    assertThat(itemsObj.items().stream().map(BatchOperationItem::getStatus).distinct().toList())
        .containsExactly(BatchOperationItemState.COMPLETED);
    assertThat(itemKeys).containsExactlyInAnyOrder(activeKeys.toArray(Long[]::new));
  }
}
