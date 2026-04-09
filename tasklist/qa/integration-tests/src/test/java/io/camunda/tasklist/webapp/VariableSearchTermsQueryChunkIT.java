/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.queries.TaskByVariables;
import io.camunda.tasklist.util.MockMvcHelper;
import io.camunda.tasklist.util.TasklistZeebeIntegrationTest;
import io.camunda.tasklist.webapp.dto.TaskQueryDTO;
import io.camunda.tasklist.webapp.security.TasklistURIs;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

public class VariableSearchTermsQueryChunkIT extends TasklistZeebeIntegrationTest {

  @Autowired private ObjectMapper objectMapper;

  @Autowired private WebApplicationContext context;

  private MockMvcHelper mockMvcHelper;

  private final String benchmarkProcess = "VariableSearch_Process";

  @DynamicPropertySource
  static void configureProperties(final DynamicPropertyRegistry registry) {
    registry.add("camunda.tasklist.elasticsearch.max-terms-count", () -> 5);
    registry.add("camunda.tasklist.opensearch.max-terms-count", () -> 5);
  }

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
}
