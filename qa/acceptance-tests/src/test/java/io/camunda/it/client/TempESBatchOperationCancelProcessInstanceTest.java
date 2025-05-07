/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.client;

import static io.camunda.it.util.TestHelper.deployResource;
import static io.camunda.it.util.TestHelper.startProcessInstance;
import static io.camunda.it.util.TestHelper.waitForElementInstances;
import static io.camunda.it.util.TestHelper.waitForProcessInstanceToBeTerminated;
import static io.camunda.it.util.TestHelper.waitForProcessInstancesToStart;
import static io.camunda.it.util.TestHelper.waitForProcessesToBeDeployed;
import static io.camunda.it.util.TestHelper.waitUntilElementInstanceHasIncidents;
import static io.camunda.it.util.TestHelper.waitUntilProcessInstanceHasIncidents;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.Process;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.client.api.search.enums.BatchOperationState;
import io.camunda.client.api.search.enums.BatchOperationType;
import io.camunda.client.impl.search.filter.ProcessInstanceFilterImpl;
import io.camunda.qa.util.multidb.MultiDbTest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

/**
 * TODO: This test is just for the implementation of batch operations in camunda exporter! It can be
 * delete after the implementation is done.
 */
@MultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
public class TempESBatchOperationCancelProcessInstanceTest {

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
    waitForElementInstances(camundaClient, 20);
    waitUntilElementInstanceHasIncidents(camundaClient, 1);
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
    final var result =
        camundaClient
            .newCreateBatchOperationCommand()
            .processInstanceCancel()
            .filter(new ProcessInstanceFilterImpl())
            .send()
            .join();
    final var batchOperationKey = result.getBatchOperationKey();

    // then
    assertThat(result).isNotNull();

    // and
    Awaitility.await("should complete batch operation")
        .atMost(Duration.ofSeconds(15))
        .pollInterval(Duration.ofMillis(100))
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () -> {
              // and
              final var batch =
                  camundaClient.newBatchOperationGetRequest(batchOperationKey).send().join();
              assertThat(batch).isNotNull();
              assertThat(batch.getBatchOperationId()).isEqualTo(String.valueOf(batchOperationKey));
              assertThat(batch.getStartDate()).isNotNull();
              assertThat(batch.getType()).isEqualTo(BatchOperationType.CANCEL_PROCESS_INSTANCE);
              assertThat(batch.getStatus()).isEqualTo(BatchOperationState.COMPLETED);
              assertThat(batch.getOperationsTotalCount())
                  .isEqualTo(ACTIVE_PROCESS_INSTANCES.size());
              assertThat(batch.getEndDate()).isNotNull();
            });

    final var activeKeys =
        ACTIVE_PROCESS_INSTANCES.stream().map(ProcessInstanceEvent::getProcessInstanceKey).toList();
    for (final Long key : activeKeys) {
      waitForProcessInstanceToBeTerminated(camundaClient, key);
    }

    final var items =
        camundaClient.newBatchOperationItemsGetRequest(batchOperationKey).send().join();

    assertThat(items.items()).hasSize(ACTIVE_PROCESS_INSTANCES.size());
  }
}
