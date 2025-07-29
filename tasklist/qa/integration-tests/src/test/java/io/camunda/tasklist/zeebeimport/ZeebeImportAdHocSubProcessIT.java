/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.zeebeimport;

import static io.camunda.tasklist.util.assertions.CustomAssertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.assertj.core.api.AssertionsForClassTypes.tuple;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.store.TaskStore;
import io.camunda.tasklist.util.MockMvcHelper;
import io.camunda.tasklist.util.TasklistZeebeIntegrationTest;
import io.camunda.tasklist.webapp.api.rest.v1.entities.VariableSearchResponse;
import io.camunda.tasklist.webapp.security.TasklistURIs;
import io.camunda.webapps.schema.entities.usertask.TaskEntity;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@Disabled("https://github.com/camunda/ad-hoc-sub-process-phase-3/issues/26")
public class ZeebeImportAdHocSubProcessIT extends TasklistZeebeIntegrationTest {

  @Autowired private TaskStore taskStore;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private WebApplicationContext context;

  private MockMvcHelper mockMvcHelper;

  @BeforeEach
  public void setUp() {
    mockMvcHelper =
        new MockMvcHelper(MockMvcBuilders.webAppContextSetup(context).build(), objectMapper);
  }

  @Test
  public void shouldImportAdHocSubProcess() {
    final String bpmnProcessId = "AdhocSubProcess";

    final String processInstanceId =
        tester
            .deployProcess("adhoc-subprocess.bpmn")
            .waitUntil()
            .processIsDeployed()
            .startProcessInstance(
                bpmnProcessId, "{\"someText\": \"text\", \"someNumber\": 1312, \"unmapped\": true}")
            .waitUntil()
            .taskIsCreated("task1")
            .taskIsCreated("task2")
            .taskIsCreated("task3")
            .getProcessInstanceId();

    // then
    org.assertj.core.api.Assertions.assertThat(processInstanceId).isNotNull();
    final List<String> taskIds = taskStore.getTaskIdsByProcessInstanceId(processInstanceId);
    org.assertj.core.api.Assertions.assertThat(taskIds.size()).isEqualTo(3);

    for (final String taskId : taskIds) {
      final TaskEntity entity = taskStore.getTask(taskId);
      final var result =
          mockMvcHelper.doRequest(
              post(TasklistURIs.TASKS_URL_V1.concat("/{taskId}/variables/search"), taskId));
      switch (entity.getFlowNodeBpmnId()) {
        case "task1" ->
            assertThat(result)
                .hasOkHttpStatus()
                .hasApplicationJsonContentType()
                .extractingListContent(objectMapper, VariableSearchResponse.class)
                .extracting("name", "previewValue", "value")
                .containsExactlyInAnyOrder(
                    tuple("someNumber", "1312", "1312"),
                    tuple("someText", "\"text\"", "\"text\""),
                    tuple("task1var", "\"text\"", "\"text\""),
                    tuple("unmapped", "true", "true"),
                    tuple(
                        "tasks",
                        "[\"task1\",\"task2\",\"task3\"]",
                        "[\"task1\",\"task2\",\"task3\"]"));
        case "task2" ->
            assertThat(result)
                .hasOkHttpStatus()
                .hasApplicationJsonContentType()
                .extractingListContent(objectMapper, VariableSearchResponse.class)
                .extracting("name", "previewValue", "value")
                .containsExactlyInAnyOrder(
                    tuple("someNumber", "1312", "1312"),
                    tuple("someText", "\"text\"", "\"text\""),
                    tuple("task2var", "1312", "1312"),
                    tuple("unmapped", "true", "true"),
                    tuple(
                        "tasks",
                        "[\"task1\",\"task2\",\"task3\"]",
                        "[\"task1\",\"task2\",\"task3\"]"));
        case "task3" ->
            assertThat(result)
                .hasOkHttpStatus()
                .hasApplicationJsonContentType()
                .extractingListContent(objectMapper, VariableSearchResponse.class)
                .extracting("name", "previewValue", "value")
                .containsExactlyInAnyOrder(
                    tuple("someNumber", "1312", "1312"),
                    tuple("someText", "\"text\"", "\"text\""),
                    tuple("unmapped", "true", "true"),
                    tuple(
                        "tasks",
                        "[\"task1\",\"task2\",\"task3\"]",
                        "[\"task1\",\"task2\",\"task3\"]"));
        default -> fail("Unexpected task id: " + taskId);
      }
    }
  }
}
