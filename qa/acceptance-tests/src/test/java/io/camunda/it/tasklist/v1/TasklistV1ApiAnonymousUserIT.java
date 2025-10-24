/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.tasklist.v1;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.enums.UserTaskState;
import io.camunda.it.util.TestHelper;
import io.camunda.qa.util.cluster.TestCamundaApplication;
import io.camunda.qa.util.cluster.TestRestTasklistClient;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.springframework.http.HttpStatus;

@MultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "rdbms")
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
public class TasklistV1ApiAnonymousUserIT {

  @MultiDbTestApplication
  static final TestCamundaApplication STANDALONE_CAMUNDA =
      new TestCamundaApplication().withUnauthenticatedAccess();

  private static long processInstanceKey;

  @BeforeAll
  public static void beforeAll(final CamundaClient client) throws Exception {
    final String processId = "user-task-process";
    final BpmnModelInstance bpmnModel =
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .userTask()
            .zeebeUserTask()
            .endEvent()
            .done();

    TestHelper.deployResource(client, bpmnModel, "user-task-process.bpmn");

    processInstanceKey = TestHelper.startProcessInstance(client, processId).getProcessInstanceKey();
    TestHelper.waitForUserTasks(
        client, q -> q.state(UserTaskState.CREATED).processInstanceKey(processInstanceKey), 1);
  }

  @Test
  public void shouldReturnTaskViaV1Api() throws Exception {
    // given
    try (final var tasklistClient = STANDALONE_CAMUNDA.newTasklistClient()) {

      // when
      final HttpResponse<String> searchResponse = tasklistClient.searchTasks(processInstanceKey);

      // then
      assertThat(searchResponse.statusCode()).isEqualTo(HttpStatus.OK.value());

      final List<Map<String, Object>> tasks =
          TestRestTasklistClient.OBJECT_MAPPER.readValue(searchResponse.body(), List.class);

      assertThat(tasks).hasSize(1);

      final Map<String, Object> task = tasks.get(0);
      assertThat(task).containsEntry("processInstanceKey", Long.toString(processInstanceKey));
    }
  }
}
