/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.historycleanup;

import static io.camunda.it.util.TestHelper.deployResource;
import static io.camunda.it.util.TestHelper.startProcessInstance;
import static io.camunda.it.util.TestHelper.waitForElementInstances;
import static io.camunda.it.util.TestHelper.waitForProcessInstancesToStart;
import static io.camunda.it.util.TestHelper.waitForProcessesToBeDeployed;
import static io.camunda.it.util.TestHelper.waitUntilProcessInstanceIsEnded;
import static io.camunda.qa.util.multidb.CamundaMultiDBExtension.TIMEOUT_DATA_AVAILABILITY;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.response.UserTask;
import io.camunda.qa.util.multidb.HistoryMultiDbTest;
import java.time.Duration;
import java.util.Objects;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

@HistoryMultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
public class HistoryCleanupIT {

  static final String RESOURCE_NAME = "process/process_with_assigned_user_task.bpmn";
  private static CamundaClient camundaClient;

  @Test
  void shouldDeleteProcessesWhichAreMarkedForCleanup() {
    // given
    deployResource(camundaClient, RESOURCE_NAME).getProcesses().getFirst();
    waitForProcessesToBeDeployed(camundaClient, 1);
    // start two PIs
    startProcessInstance(
        camundaClient, "PROCESS_WITH_USER_TASK_PRE_ASSIGNED", "{\"variable\":\"bud\"}");
    startProcessInstance(
        camundaClient, "PROCESS_WITH_USER_TASK_PRE_ASSIGNED", "{\"variable\":\"bud2\"}");
    // await them
    waitForProcessInstancesToStart(camundaClient, 2);
    waitForElementInstances(camundaClient, 4);

    // when we complete the user task of one instance
    final UserTask userTask =
        Awaitility.await("until a task is available for completion")
            .atMost(TIMEOUT_DATA_AVAILABILITY)
            .ignoreExceptions() // Ignore exceptions and continue retrying
            .until(
                () -> camundaClient.newUserTaskSearchRequest().send().join().items().getFirst(),
                Objects::nonNull);
    camundaClient.newUserTaskCompleteCommand(userTask.getUserTaskKey()).send().join();

    // then one of the process instance should be ended, but still exist to query
    final Long processInstanceKey = userTask.getProcessInstanceKey();
    waitUntilProcessInstanceIsEnded(camundaClient, processInstanceKey);

    // and soon it should be gone
    Awaitility.await("should wait until process and tasks are deleted")
        .atMost(Duration.ofMinutes(5))
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () -> {
              final var result =
                  camundaClient
                      .newProcessInstanceSearchRequest()
                      .filter(f -> f.processInstanceKey(processInstanceKey))
                      .send()
                      .join();
              assertThat(result.page().totalItems()).isEqualTo(0);

              final var taskAmount =
                  camundaClient
                      .newUserTaskSearchRequest()
                      .filter(b -> b.userTaskKey(userTask.getUserTaskKey()))
                      .send()
                      .join()
                      .page()
                      .totalItems();
              assertThat(taskAmount).isZero();
            });

    // the other should still exist
    final var result = camundaClient.newProcessInstanceSearchRequest().send().join();
    assertThat(result.page().totalItems()).isEqualTo(1);
    assertThat(result.items().getFirst().getProcessInstanceKey()).isNotEqualTo(processInstanceKey);
  }

  @Disabled("flaky, will be fixed #35023")
  @Test
  void shouldDeleteBatchOperationsWhichAreMarkedForCleanup() {
    // given
    deployResource(camundaClient, RESOURCE_NAME).getProcesses().getFirst();
    waitForProcessesToBeDeployed(camundaClient, 1);
    // start two PIs
    startProcessInstance(
        camundaClient, "PROCESS_WITH_USER_TASK_PRE_ASSIGNED", "{\"variable\":\"bud\"}");
    startProcessInstance(
        camundaClient, "PROCESS_WITH_USER_TASK_PRE_ASSIGNED", "{\"variable\":\"bud2\"}");
    // await them
    waitForProcessInstancesToStart(camundaClient, 2);
    waitForElementInstances(camundaClient, 4);

    // when we complete the user task of one instance
    final UserTask userTask =
        Awaitility.await("until a task is available for completion")
            .atMost(TIMEOUT_DATA_AVAILABILITY)
            .ignoreExceptions() // Ignore exceptions and continue retrying
            .until(
                () -> camundaClient.newUserTaskSearchRequest().send().join().items().getFirst(),
                Objects::nonNull);
    camundaClient.newUserTaskCompleteCommand(userTask.getUserTaskKey()).send().join();

    // then one of the process instance should be ended, but still exist to query
    final Long processInstanceKey = userTask.getProcessInstanceKey();
    waitUntilProcessInstanceIsEnded(camundaClient, processInstanceKey);

    // and soon it should be gone
    Awaitility.await("should wait until process and tasks are deleted")
        .atMost(Duration.ofMinutes(5))
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () -> {
              final var result =
                  camundaClient
                      .newProcessInstanceSearchRequest()
                      .filter(f -> f.processInstanceKey(processInstanceKey))
                      .send()
                      .join();
              assertThat(result.page().totalItems()).isEqualTo(0);

              final var taskAmount =
                  camundaClient
                      .newUserTaskSearchRequest()
                      .filter(b -> b.userTaskKey(userTask.getUserTaskKey()))
                      .send()
                      .join()
                      .page()
                      .totalItems();
              assertThat(taskAmount).isZero();
            });

    // the other should still exist
    final var result = camundaClient.newProcessInstanceSearchRequest().send().join();
    assertThat(result.page().totalItems()).isEqualTo(1);
    assertThat(result.items().getFirst().getProcessInstanceKey()).isNotEqualTo(processInstanceKey);
  }
}
