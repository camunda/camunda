/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.client;

import static io.camunda.it.client.QueryTest.deployResource;
import static io.camunda.it.client.QueryTest.startProcessInstance;
import static io.camunda.it.client.QueryTest.waitForFlowNodeInstances;
import static io.camunda.it.client.QueryTest.waitForProcessInstanceToBeTerminated;
import static io.camunda.it.client.QueryTest.waitForProcessInstancesToStart;
import static io.camunda.it.client.QueryTest.waitForProcessesToBeDeployed;
import static io.camunda.it.client.QueryTest.waitUntilFlowNodeInstanceHasIncidents;
import static io.camunda.it.client.QueryTest.waitUntilProcessInstanceHasIncidents;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.Process;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.client.api.search.response.BatchOperationItems.BatchOperationItem;
import io.camunda.client.api.search.response.BatchOperationState;
import io.camunda.qa.util.multidb.MultiDbTest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@MultiDbTest
public class CancelProcessInstanceBatchTest {

  static final List<Process> DEPLOYED_PROCESSES = new ArrayList<>();
  static final List<ProcessInstanceEvent> ACTIVE_PROCESS_INSTANCES = new ArrayList<>();

  private static CamundaClient camundaClient;

  @BeforeAll
  public static void beforeAll() {
    Objects.requireNonNull(camundaClient);
    final List<String> processes =
        List.of(
            "service_tasks_v1.bpmn",
            "service_tasks_v2.bpmn",
            "incident_process_v1.bpmn",
            "manual_process.bpmn",
            "parent_process_v1.bpmn",
            "child_process_v1.bpmn");
    processes.forEach(
        process ->
            DEPLOYED_PROCESSES.addAll(
                deployResource(camundaClient, String.format("process/%s", process))
                    .getProcesses()));

    waitForProcessesToBeDeployed(camundaClient, DEPLOYED_PROCESSES.size());

    // Does two will not be active when we cancel, since they just have manual steps
    startProcessInstance(camundaClient, "manual_process");
    startProcessInstance(camundaClient, "parent_process_v1");

    ACTIVE_PROCESS_INSTANCES.add(
        startProcessInstance(camundaClient, "service_tasks_v1", "{\"xyz\":\"bar\"}"));
    ACTIVE_PROCESS_INSTANCES.add(
        startProcessInstance(camundaClient, "service_tasks_v2", "{\"path\":222}"));
    ACTIVE_PROCESS_INSTANCES.add(startProcessInstance(camundaClient, "incident_process_v1"));

    waitForProcessInstancesToStart(camundaClient, 6);
    waitForFlowNodeInstances(camundaClient, 20);
    waitUntilFlowNodeInstanceHasIncidents(camundaClient, 1);
    waitUntilProcessInstanceHasIncidents(camundaClient, 1);
  }

  @AfterAll
  static void afterAll() {
    DEPLOYED_PROCESSES.clear();
    ACTIVE_PROCESS_INSTANCES.clear();
  }

  @Test
  void shouldCancelProcessInstancesWithBatch() throws InterruptedException {
    // when
    final var result = camundaClient.newCancelInstancesBatchCommand(b -> {}).send().join();
    final var batchOperationKey = result.getBatchOperationKey();

    // then
    assertThat(result).isNotNull();
    assertThat(batchOperationKey).isNotNull();

    // and
    Awaitility.await("should complete batch operation")
        .atMost(Duration.ofSeconds(15))
        .pollInterval(Duration.ofMillis(100))
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () -> {
              // and
              final var batch =
                  camundaClient
                      .newGetBatchOperationCommand(result.getBatchOperationKey())
                      .send()
                      .join();
              assertThat(batch).isNotNull();
              assertThat(batch.getEndDate()).isNotNull();
              assertThat(batch.getState()).isEqualTo(BatchOperationState.COMPLETED);
            });

    final var activeKeys =
        ACTIVE_PROCESS_INSTANCES.stream().map(ProcessInstanceEvent::getProcessInstanceKey).toList();
    for (final Long key : activeKeys) {
      waitForProcessInstanceToBeTerminated(camundaClient, key);
    }

    // and
    final var itemsObj =
        camundaClient.newGetBatchOperationItemsCommand(batchOperationKey).send().join();
    final var itemKeys = itemsObj.items().stream().map(BatchOperationItem::getKey).toList();

    assertThat(itemsObj.items()).hasSize(3);
    assertThat(itemsObj.items().stream().map(BatchOperationItem::getState).distinct().toList())
        .containsExactly(BatchOperationState.COMPLETED.name());
    assertThat(itemKeys).containsExactlyInAnyOrder(activeKeys.toArray(Long[]::new));
  }
}
