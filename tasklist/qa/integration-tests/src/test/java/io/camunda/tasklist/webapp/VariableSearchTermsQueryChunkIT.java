/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp;

import static io.camunda.tasklist.util.assertions.CustomAssertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.queries.TaskByVariables;
import io.camunda.tasklist.store.VariableStore;
import io.camunda.tasklist.util.MockMvcHelper;
import io.camunda.tasklist.util.TasklistZeebeIntegrationTest;
import io.camunda.tasklist.webapp.api.rest.v1.entities.TaskSearchResponse;
import io.camunda.tasklist.webapp.dto.TaskQueryDTO;
import io.camunda.tasklist.webapp.security.TasklistURIs;
import io.camunda.webapps.schema.descriptors.template.VariableTemplate;
import java.io.IOException;
import java.time.Duration;
import org.assertj.core.api.Assertions;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

public class VariableSearchTermsQueryChunkIT extends TasklistZeebeIntegrationTest {

  @Autowired private ObjectMapper objectMapper;

  @Autowired private VariableTemplate variableTemplate;

  @Autowired private WebApplicationContext context;

  @Autowired private VariableStore variableStore;

  private MockMvcHelper mockMvcHelper;

  private final String benchmarkProcess = "VariableSearch_Process";

  @BeforeEach
  public void setUp() throws IOException, InterruptedException {
    mockMvcHelper =
        new MockMvcHelper(MockMvcBuilders.webAppContextSetup(context).build(), objectMapper);

    camundaClient
        .newDeployResourceCommand()
        .addResourceFromClasspath("variable_search.bpmn")
        .send()
        .join()
        .getProcesses()
        .getFirst()
        .getProcessDefinitionKey();

    databaseTestExtension.setIndexMaxTermsCount(variableTemplate.getFullQualifiedName(), 5);

    org.assertj.core.api.Assertions.assertThat(
            databaseTestExtension.getIndexMaxTermsCount(variableTemplate.getFullQualifiedName()))
        .isEqualTo(5);

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

    databaseTestExtension.refreshTasklistIndices();

    // Using the CamundaExporter and the shared Operate indices having the FNIs
    // present requires more time compared to the User Task created state check
    Awaitility.await()
        .atMost(Duration.ofMinutes(2))
        .pollInterval(Duration.ofSeconds(5))
        .untilAsserted(
            () -> {
              // User Task in Embedded Subprocess
              final var result = search(taskLocalVar, "\"" + invar + "subp\"");
              assertSearch(result, processCount, "SUBP");
            });

    // Job Worker User Task
    var result = search(taskLocalVar, "\"" + invar + "jw\"");
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
            task ->
                Assertions.assertThat(task.getTaskDefinitionId())
                    .isEqualTo("Activity_" + activitySuffix));
  }
}
