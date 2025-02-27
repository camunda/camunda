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
import io.camunda.it.utils.MultiDbTest;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@MultiDbTest
public class CancelProcessInstanceBatchTest {

  static final List<Process> DEPLOYED_PROCESSES = new ArrayList<>();
  static final List<ProcessInstanceEvent> PROCESS_INSTANCES = new ArrayList<>();

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

    PROCESS_INSTANCES.add(
        startProcessInstance(camundaClient, "service_tasks_v1", "{\"xyz\":\"bar\"}"));
    PROCESS_INSTANCES.add(
        startProcessInstance(camundaClient, "service_tasks_v2", "{\"path\":222}"));
    PROCESS_INSTANCES.add(startProcessInstance(camundaClient, "manual_process"));
    PROCESS_INSTANCES.add(startProcessInstance(camundaClient, "incident_process_v1"));
    PROCESS_INSTANCES.add(startProcessInstance(camundaClient, "parent_process_v1"));

    waitForProcessInstancesToStart(camundaClient, 6);
    waitForFlowNodeInstances(camundaClient, 20);
    waitUntilFlowNodeInstanceHasIncidents(camundaClient, 1);
    waitUntilProcessInstanceHasIncidents(camundaClient, 1);
  }

  @AfterAll
  static void afterAll() {
    DEPLOYED_PROCESSES.clear();
    PROCESS_INSTANCES.clear();
  }

  @Test
  void shouldCancelProcessInstancesWithBatch() {
    // when
    final var result = camundaClient.newCancelInstancesBatchCommand(b -> {
        })
        .send().join();

    // then
    assertThat(result).isNotNull();
    assertThat(result.keys()).hasSize(3);

    for (final Long key : result.keys()) {
      waitForProcessInstanceToBeTerminated(camundaClient, key);
    }
  }
}
