/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp;

import static io.camunda.tasklist.util.assertions.CustomAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.queries.TaskByVariables;
import io.camunda.tasklist.schema.indices.VariableIndex;
import io.camunda.tasklist.store.VariableStore;
import io.camunda.tasklist.util.MockMvcHelper;
import io.camunda.tasklist.util.TasklistZeebeIntegrationTest;
import io.camunda.tasklist.webapp.api.rest.v1.entities.TaskSearchResponse;
import io.camunda.tasklist.webapp.graphql.entity.TaskQueryDTO;
import io.camunda.tasklist.webapp.security.TasklistURIs;
import java.io.IOException;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

public class VariableSearchTermsQueryChunkIT extends TasklistZeebeIntegrationTest {

  @Autowired private ObjectMapper objectMapper;

  @Autowired private VariableIndex variableIndex;

  @Autowired private WebApplicationContext context;

  @Autowired private VariableStore variableStore;

  private MockMvcHelper mockMvcHelper;

  private final String benchmarkProcess = "VariableSearch_Process";

  @BeforeEach
  public void setUp() throws IOException, InterruptedException {
    mockMvcHelper =
        new MockMvcHelper(MockMvcBuilders.webAppContextSetup(context).build(), objectMapper);

    zeebeClient
        .newDeployResourceCommand()
        .addResourceFromClasspath("variable_search.bpmn")
        .send()
        .join()
        .getProcesses()
        .getFirst()
        .getProcessDefinitionKey();

    databaseTestExtension.setIndexMaxTermsCount(variableIndex.getFullQualifiedName(), 5);

    assertEquals(
        5, databaseTestExtension.getIndexMaxTermsCount(variableIndex.getFullQualifiedName()));

    variableStore.refreshMaxTermsCount();
  }

  @Test
  public void shouldReturnAllMatchedUserTasks() {
    final int processCount = 50;
    final String invar = "inputvar";
    final String taskLocalVar = "taskvar";

    tester
        .startProcessInstances(benchmarkProcess, processCount, "{\"inputvar\": \"" + invar + "\"}")
        .then()
        .tasksAreCreated("Activity_SUBP", processCount);

    // User Task in Embedded Subprocess
    var result = search(taskLocalVar, "\"" + invar + "subp\"");
    assertSearch(result, processCount, "SUBP");

    // Job Worker User Task
    result = search(taskLocalVar, "\"" + invar + "jw\"");
    assertSearch(result, processCount, "JW");

    // Zeebe User Tasks
    result = search(taskLocalVar, "\"" + invar + "zb\"");
    assertSearch(result, processCount, "ZBT");

    // Search for a process variable
    result = search("var1", "1");

    // Each process instance has 3 user tasks
    assertThat(result)
        .hasOkHttpStatus()
        .hasApplicationJsonContentType()
        .extractingListContent(objectMapper, TaskSearchResponse.class)
        .hasSize(processCount * 3);
  }

  private MockHttpServletResponse search(final String varName, final String varValue) {
    final var searchQuery =
        new TaskQueryDTO()
            .setPageSize(999)
            .setTaskVariables(
                new TaskByVariables[] {
                  new TaskByVariables().setName(varName).setValue(varValue).setOperator("eq")
                });

    return mockMvcHelper.doRequest(post(TasklistURIs.TASKS_URL_V1.concat("/search")), searchQuery);
  }

  private void assertSearch(
      final MockHttpServletResponse result, final int size, final String activitySuffix) {
    assertThat(result)
        .hasOkHttpStatus()
        .hasApplicationJsonContentType()
        .extractingListContent(objectMapper, TaskSearchResponse.class)
        .hasSize(size)
        .allSatisfy(
            task -> {
              Assertions.assertThat(task.getTaskDefinitionId())
                  .isEqualTo("Activity_" + activitySuffix);
            });
  }
}
