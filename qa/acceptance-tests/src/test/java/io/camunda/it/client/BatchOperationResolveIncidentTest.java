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
import static io.camunda.it.util.TestHelper.waitForProcessesToBeDeployed;
import static io.camunda.it.util.TestHelper.waitForScopedProcessInstancesToStart;
import static io.camunda.it.util.TestHelper.waitUntilIncidentsAreActive;
import static io.camunda.it.util.TestHelper.waitUntilIncidentsAreResolved;
import static io.camunda.it.util.TestHelper.waitUntilScopedProcessInstanceHasIncidents;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.Process;
import io.camunda.client.api.search.enums.BatchOperationItemState;
import io.camunda.client.api.search.response.BatchOperationItems.BatchOperationItem;
import io.camunda.client.api.search.response.Incident;
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
public class BatchOperationResolveIncidentTest {

  static final int AMOUNT_OF_INCIDENTS = 3;

  private static CamundaClient camundaClient;
  String testScopeId;
  final List<Process> deployedProcesses = new ArrayList<>();
  final List<Long> activeIncidents = new ArrayList<>();

  @BeforeEach
  public void beforeEach(final TestInfo testInfo) {
    Objects.requireNonNull(camundaClient);
    testScopeId =
        testInfo.getTestMethod().map(Method::toString).orElse(UUID.randomUUID().toString());

    final var processes =
        List.of("service_tasks_v1.bpmn", "service_tasks_v2.bpmn", "incident_process_v1.bpmn");
    processes.forEach(
        process ->
            deployedProcesses.addAll(
                deployResource(camundaClient, String.format("process/%s", process))
                    .getProcesses()));

    waitForProcessesToBeDeployed(camundaClient, 3);

    final var processInstances = new ArrayList<Long>();
    processInstances.add(
        startScopedProcessInstance(camundaClient, "service_tasks_v1", testScopeId)
            .getProcessInstanceKey());
    processInstances.add(
        startScopedProcessInstance(
                camundaClient, "service_tasks_v2", testScopeId, Map.of("path", 222))
            .getProcessInstanceKey());
    processInstances.add(
        startScopedProcessInstance(camundaClient, "incident_process_v1", testScopeId)
            .getProcessInstanceKey());
    processInstances.add(
        startScopedProcessInstance(camundaClient, "incident_process_v1", testScopeId)
            .getProcessInstanceKey());
    processInstances.add(
        startScopedProcessInstance(camundaClient, "incident_process_v1", testScopeId)
            .getProcessInstanceKey());

    waitForScopedProcessInstancesToStart(camundaClient, testScopeId, 5);
    waitUntilScopedProcessInstanceHasIncidents(camundaClient, testScopeId, AMOUNT_OF_INCIDENTS);
    waitUntilIncidentsAreActive(camundaClient, AMOUNT_OF_INCIDENTS);

    activeIncidents.addAll(
        camundaClient.newIncidentSearchRequest().send().join().items().stream()
            .map(Incident::getIncidentKey)
            .toList());
  }

  @AfterEach
  void afterEach() {
    deployedProcesses.clear();
    activeIncidents.clear();
  }

  @Test
  void shouldResolveIncidentsWithBatch() throws InterruptedException {
    // when
    final var result =
        camundaClient
            .newCreateBatchOperationCommand()
            .resolveIncident()
            .filter(f -> f.variables(getScopedVariables(testScopeId)))
            .send()
            .join();
    final var batchOperationKey = result.getBatchOperationKey();

    // then
    assertThat(result).isNotNull();

    // and wait if batch has correct amount of items. (To fail fast if not)
    waitForBatchOperationWithCorrectTotalCount(camundaClient, batchOperationKey, 3);

    // and
    waitForBatchOperationCompleted(camundaClient, batchOperationKey, 3, 0);

    // and
    waitUntilIncidentsAreResolved(camundaClient, activeIncidents.size());

    // and
    final var itemsObj =
        camundaClient
            .newBatchOperationItemsSearchRequest()
            .filter(f -> f.batchOperationKey(batchOperationKey))
            .send()
            .join();
    final var itemKeys = itemsObj.items().stream().map(BatchOperationItem::getItemKey).toList();

    assertThat(itemsObj.items()).hasSize(3);
    assertThat(itemsObj.items().stream().map(BatchOperationItem::getStatus).distinct().toList())
        .containsExactly(BatchOperationItemState.COMPLETED);
    assertThat(itemKeys).containsExactlyInAnyOrder(activeIncidents.toArray(Long[]::new));
  }
}
