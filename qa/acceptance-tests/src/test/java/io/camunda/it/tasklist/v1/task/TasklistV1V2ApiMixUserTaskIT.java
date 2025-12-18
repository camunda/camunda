/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.tasklist.v1.task;

import static io.camunda.it.util.TestHelper.deployResource;
import static io.camunda.it.util.TestHelper.startProcessInstance;

import io.camunda.application.Profile;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.response.SearchResponse;
import io.camunda.client.api.search.response.UserTask;
import io.camunda.qa.util.cluster.TestCamundaApplication;
import io.camunda.qa.util.cluster.TestRestTasklistClient;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.tasklist.webapp.api.rest.v1.entities.TaskSearchResponse;
import io.camunda.tasklist.webapp.api.rest.v1.entities.VariableSearchResponse;
import io.camunda.webapps.schema.entities.usertask.TaskState;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import org.assertj.core.api.Assertions;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

@MultiDbTest
// Tasklist V1 APIs are not available with RDBMS secondary storage.
// See the @ConditionalOnRdbmsDisabled annotation on
// io.camunda.tasklist.webapp.api.rest.v1.controllers.TaskController
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "rdbms.*$")
public class TasklistV1V2ApiMixUserTaskIT {

  @MultiDbTestApplication
  private static final TestCamundaApplication STANDALONE_CAMUNDA =
      new TestCamundaApplication()
          .withUnauthenticatedAccess()
          .withAdditionalProfile(Profile.TASKLIST);

  private static CamundaClient v2CamundaClient;

  @Test
  public void shouldRetrieveAllVariablesWithV1ApiWhenCompletingTaskWithV2Api() {

    // given: a process containing a task started with some variables

    // Define process with a single user task
    final String processId = "processWithTask";
    final BpmnModelInstance processDefinition =
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .userTask("userTask")
            .zeebeUserTask()
            .done();
    deployResource(v2CamundaClient, processDefinition, processId + ".bpmn");

    // Provide variables when starting the process
    final long processInstanceKey =
        startProcessInstance(v2CamundaClient, processId, Map.of("var1", "value1", "var2", "value2"))
            .getProcessInstanceKey();

    // Retrieve user task key when it is available
    final var taskKey = new AtomicLong();
    Awaitility.await("should create user task")
        .atMost(Duration.ofSeconds(30))
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .until(
            () -> {
              final SearchResponse<UserTask> searchResponse =
                  v2CamundaClient
                      .newUserTaskSearchRequest()
                      .filter(f -> f.processInstanceKey(processInstanceKey))
                      .send()
                      .join();
              if (searchResponse.items().isEmpty()) {
                return false;
              } else {
                taskKey.set(searchResponse.items().getFirst().getUserTaskKey());
                return true;
              }
            });

    // when: the task is completed with V2 APIs, providing some but not all variables
    v2CamundaClient
        .newCompleteUserTaskCommand(taskKey.get())
        .variables(Map.of("var1", "newvalue1", "var3", "value3"))
        .send()
        .join();

    // then: search through V1 APIs should return all variables
    try (final var v1TasklistClient = STANDALONE_CAMUNDA.newTasklistClient()) {

      // Note: it could take some time for the changes to be available in tasklist
      Awaitility.await("should return all variables")
          .atMost(Duration.ofSeconds(30))
          .ignoreExceptions()
          .untilAsserted(
              () -> {
                // Retrieve the task with V1 APIs, requesting variables too
                final var tasks =
                    TestRestTasklistClient.OBJECT_MAPPER.readValue(
                        v1TasklistClient
                            .searchRequest(
                                "v1/tasks/search",
                                "{\"includeVariables\": [{\"name\": \"var1\"},{\"name\":\"var2\"},{\"name\": \"var3\"}]}")
                            .body(),
                        TaskSearchResponse[].class);

                // Wait for the task to be marked complete (export could take a while)
                Assertions.assertThat(tasks[0].getTaskState()).isEqualTo(TaskState.COMPLETED);
                // Check correct variables are retrieved
                final Map<String, String> expectedVariables =
                    Map.of(
                        "var1",
                            "\"newvalue1\"", // from task completion (replacing original variable)
                        "var2",
                            "\"value2\"", // original value, not provided through task completion
                        "var3", "\"value3\"" // from task completion
                        );
                final Map<String, String> actualVariables =
                    Arrays.stream(tasks[0].getVariables())
                        .collect(
                            Collectors.toMap(
                                VariableSearchResponse::getName, VariableSearchResponse::getValue));
                Assertions.assertThat(actualVariables).isEqualTo(expectedVariables);
              });
    }
  }
}
