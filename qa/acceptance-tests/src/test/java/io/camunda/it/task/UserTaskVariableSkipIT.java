/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.task;

import static io.camunda.qa.util.multidb.CamundaMultiDBExtension.TIMEOUT_DATA_AVAILABILITY;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.builder.AbstractUserTaskBuilder;
import java.util.Map;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

/**
 * Integration test verifying the optimization from issue #47843: variables are only exported to the
 * tasklist-task index when the process definition has user tasks. This test deploys real processes,
 * starts instances with variables, and verifies export behavior through the CamundaClient search
 * API without explicitly calling the exporter.
 */
@MultiDbTest
class UserTaskVariableSkipIT {

  private static CamundaClient camundaClient;

  @Test
  void shouldExportVariablesToTasklistForProcessWithUserTasks() {
    // given - a process WITH a user task
    final var processId = "with-user-task-var-skip-it";
    final var process =
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .userTask("myTask", AbstractUserTaskBuilder::zeebeUserTask)
            .endEvent()
            .done();

    camundaClient
        .newDeployResourceCommand()
        .addProcessModel(process, processId + ".bpmn")
        .send()
        .join();

    // when - start a process instance with variables
    final var processInstanceKey =
        camundaClient
            .newCreateInstanceCommand()
            .bpmnProcessId(processId)
            .latestVersion()
            .variables(Map.of("testVar", "testValue"))
            .send()
            .join()
            .getProcessInstanceKey();

    // then - user task should be findable via processInstanceVariables filter,
    // proving the variable was exported to the tasklist-task index
    Awaitility.await("variable should be exported to tasklist-task index")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final var tasks =
                  camundaClient
                      .newUserTaskSearchRequest()
                      .filter(
                          f ->
                              f.processInstanceKey(processInstanceKey)
                                  .processInstanceVariables(Map.of("testVar", "\"testValue\"")))
                      .send()
                      .join()
                      .items();
              assertThat(tasks).hasSize(1);
            });
  }

  @Test
  void shouldNotExportVariablesToTasklistForProcessWithoutUserTasks() {
    // given - a process WITHOUT any user tasks (service task keeps instance active)
    final var processId = "no-user-task-var-skip-it";
    final var process =
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .serviceTask("waitTask", t -> t.zeebeJobType("no-user-task-var-skip-it-job"))
            .endEvent()
            .done();

    camundaClient
        .newDeployResourceCommand()
        .addProcessModel(process, processId + ".bpmn")
        .send()
        .join();

    // when - start a process instance with a variable
    camundaClient
        .newCreateInstanceCommand()
        .bpmnProcessId(processId)
        .latestVersion()
        .variables(Map.of("skipVar", "skipValue"))
        .send()
        .join();

    // then - wait for the variable to appear in the operate-variable index (proves it was exported)
    Awaitility.await("variable should appear in operate-variable index")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final var variables =
                  camundaClient
                      .newVariableSearchRequest()
                      .filter(f -> f.name("skipVar"))
                      .send()
                      .join()
                      .items();
              assertThat(variables).isNotEmpty();
              assertThat(variables.getFirst().getValue()).isEqualTo("\"skipValue\"");
            });

    // The variable is in the operate-variable index but should NOT be in tasklist-task.
    // Since there are no user tasks in this process, querying user tasks filtered by
    // processInstanceVariables should return empty.
    final var tasks =
        camundaClient
            .newUserTaskSearchRequest()
            .filter(f -> f.processInstanceVariables(Map.of("skipVar", "\"skipValue\"")))
            .send()
            .join()
            .items();
    assertThat(tasks)
        .describedAs(
            "No user tasks should be found via processInstanceVariables for a process without user tasks")
        .isEmpty();
  }
}
