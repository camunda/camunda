/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.historycleanup;

import static io.camunda.it.client.QueryTest.deployResource;
import static io.camunda.it.client.QueryTest.startProcessInstance;
import static io.camunda.it.client.QueryTest.waitForFlowNodeInstances;
import static io.camunda.it.client.QueryTest.waitForProcessInstancesToStart;
import static io.camunda.it.client.QueryTest.waitForProcessesToBeDeployed;
import static io.camunda.it.client.QueryTest.waitUntilProcessInstanceIsEnded;
import static io.camunda.it.client.QueryTest.waitUntilProcessInstanceIsGone;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.client.api.search.response.UserTask;
import io.camunda.qa.util.multidb.HistoryMultiDbTest;
import java.time.Duration;
import java.util.Objects;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

@HistoryMultiDbTest
public class HistoryCleanupIT {

  static final String RESOURCE_NAME = "process/process_with_assigned_user_task.bpmn";
  private static CamundaClient camundaClient;

  @Test
  void shouldDeleteProcessesWhichAreMarkedForCleanup() {
    // given
    deployResource(camundaClient, RESOURCE_NAME).getProcesses().getFirst();
    waitForProcessesToBeDeployed(camundaClient, 1);
    final ProcessInstanceEvent processInstanceEvent =
        startProcessInstance(camundaClient, "foo", "{\"variable\":\"bud\"}");
    waitForProcessInstancesToStart(camundaClient, 1);
    waitForFlowNodeInstances(camundaClient, 2);

    // when we complete the user task
    Awaitility.await("until a task is available for completion")
        .atMost(Duration.ofMinutes(1))
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .until(
            () -> camundaClient.newUserTaskQuery().send().join().items().getFirst(),
            Objects::nonNull);
    final UserTask userTask = camundaClient.newUserTaskQuery().send().join().items().getFirst();
    camundaClient.newUserTaskCompleteCommand(userTask.getUserTaskKey()).send().join();

    // then process should be ended
    waitUntilProcessInstanceIsEnded(camundaClient, processInstanceEvent.getProcessInstanceKey());

    // and soon it should be gone
    waitUntilProcessInstanceIsGone(camundaClient, processInstanceEvent.getProcessInstanceKey());

    Awaitility.await("should wait until tasks are deleted")
        .atMost(Duration.ofMinutes(5))
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () -> {
              final var taskAmount =
                  camundaClient
                      .newUserTaskQuery()
                      .filter(b -> b.userTaskKey(userTask.getUserTaskKey()))
                      .send()
                      .join()
                      .page()
                      .totalItems();
              assertThat(taskAmount).isZero();
            });
  }
}
