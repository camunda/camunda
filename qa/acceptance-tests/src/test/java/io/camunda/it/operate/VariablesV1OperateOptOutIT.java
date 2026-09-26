/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.operate;

import static io.camunda.it.util.TestHelper.deployProcessAndWaitForIt;
import static io.camunda.it.util.TestHelper.startProcessInstance;
import static io.camunda.it.util.TestHelper.waitUntilProcessInstanceHasVariable;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.camunda.client.CamundaClient;
import io.camunda.qa.util.cluster.TestCamundaApplication;
import io.camunda.qa.util.cluster.TestRestTasklistClient;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.tasklist.webapp.api.rest.v1.entities.VariableResponse;
import io.camunda.tasklist.webapp.api.rest.v1.entities.VariableSearchResponse;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.test.util.Strings;
import java.net.http.HttpResponse;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.springframework.http.HttpStatus;

/**
 * Verifies that {@code camunda.operate.v1-variable-by-key-enabled=false} resolves the {@code GET
 * /v1/variables/{id}} ambiguous-mapping collision (camunda#59478) in favor of Tasklist's V1
 * controller.
 */
@MultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "rdbms")
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
public class VariablesV1OperateOptOutIT {

  @MultiDbTestApplication
  static final TestCamundaApplication STANDALONE_CAMUNDA =
      new TestCamundaApplication()
          .withProperty("camunda.operate.v1-variable-by-key-enabled", "false");

  private static final String PROCESS_ID = Strings.newRandomValidBpmnId();
  private static final String VARIABLE_NAME = "testVariableA";
  private static final int VARIABLE_VALUE = 42;

  private static String tasklistVariableId;

  @BeforeAll
  public static void beforeAll(final CamundaClient adminClient) {
    deployProcessAndWaitForIt(
        adminClient,
        Bpmn.createExecutableProcess(PROCESS_ID).startEvent().userTask().endEvent().done(),
        "process.bpmn");
    final long processInstanceKey =
        startProcessInstance(adminClient, PROCESS_ID, Map.of(VARIABLE_NAME, VARIABLE_VALUE))
            .getProcessInstanceKey();
    waitUntilProcessInstanceHasVariable(
        adminClient, processInstanceKey, VARIABLE_NAME, String.valueOf(VARIABLE_VALUE));

    // Resolve Tasklist's own runtime variable id via its task-variable search, rather than
    // assuming the id format, since it must go through Tasklist's own indices/importer, not
    // Operate's.
    try (final var tasklistClient = STANDALONE_CAMUNDA.newTasklistClient()) {
      final var taskId = new String[1];
      await()
          .untilAsserted(
              () -> {
                final var tasks = tasklistClient.searchAndParseTasks(processInstanceKey);
                assertThat(tasks).hasSize(1);
                taskId[0] = tasks[0].getId();
              });

      final var variableId = new String[1];
      await()
          .untilAsserted(
              () -> {
                final var variables =
                    TestRestTasklistClient.OBJECT_MAPPER.readValue(
                        tasklistClient
                            .searchRequest("v1/tasks/" + taskId[0] + "/variables/search", "{}")
                            .body(),
                        VariableSearchResponse[].class);
                assertThat(variables).hasSize(1);
                variableId[0] = variables[0].getId();
              });
      tasklistVariableId = variableId[0];
    }
  }

  @Test
  void shouldReturnTasklistVariableWhenOperateOptsOut() throws Exception {
    // when
    final HttpResponse<String> response;
    try (final var client = STANDALONE_CAMUNDA.newTasklistClient()) {
      response = client.getRequest("v1/variables/" + tasklistVariableId);
    }

    // then
    assertThat(response.statusCode()).isEqualTo(HttpStatus.OK.value());
    final var variable =
        TestRestTasklistClient.OBJECT_MAPPER.readValue(response.body(), VariableResponse.class);
    assertThat(variable.getId()).isEqualTo(tasklistVariableId);
    assertThat(variable.getName()).isEqualTo(VARIABLE_NAME);
    assertThat(variable.getValue()).isEqualTo(String.valueOf(VARIABLE_VALUE));
  }
}
