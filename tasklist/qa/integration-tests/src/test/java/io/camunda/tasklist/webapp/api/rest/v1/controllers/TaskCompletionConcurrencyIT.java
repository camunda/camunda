/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.api.rest.v1.controllers;

import static io.camunda.tasklist.util.assertions.CustomAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.tuple;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.store.VariableStore;
import io.camunda.tasklist.util.MockMvcHelper;
import io.camunda.tasklist.util.TasklistZeebeIntegrationTest;
import io.camunda.tasklist.util.apps.config.ConcurrencyTestConfiguration;
import io.camunda.tasklist.webapp.api.rest.v1.entities.TaskCompleteRequest;
import io.camunda.tasklist.webapp.api.rest.v1.entities.TaskResponse;
import io.camunda.tasklist.webapp.api.rest.v1.entities.VariableSearchResponse;
import io.camunda.tasklist.webapp.dto.VariableInputDTO;
import io.camunda.tasklist.webapp.security.TasklistURIs;
import io.camunda.webapps.schema.entities.usertask.TaskState;
import io.camunda.zeebe.model.bpmn.builder.AbstractUserTaskBuilder;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@Import(ConcurrencyTestConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class TaskCompletionConcurrencyIT extends TasklistZeebeIntegrationTest {

  @Autowired private VariableStore variableStore;

  @Autowired private WebApplicationContext context;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private ConcurrencyTestConfiguration concurrencyTestConfiguration;

  private MockMvcHelper mockMvcHelper;

  @BeforeEach
  public void setUp() {
    mockMvcHelper =
        new MockMvcHelper(MockMvcBuilders.webAppContextSetup(context).build(), objectMapper);
    // Reset the latch before the test
    concurrencyTestConfiguration.completionLatch = new CountDownLatch(1);
  }

  /**
   * Test to reproduce concurrency issues when completing tasks with variables. This test runs the
   * task completion asynchronously while allowing the exporter to run in parallel, testing for race
   * conditions in variable persistence.
   */
  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void completeTaskWithVariablesConcurrentlyWithImporter(final boolean useZeebeUserTasks)
      throws Exception {
    // given
    final String bpmnProcessId = "simpleTestProcess";
    final String flowNodeBpmnId = "taskE_".concat(UUID.randomUUID().toString());

    final String taskId =
        tester
            .createAndDeploySimpleProcess(
                bpmnProcessId,
                flowNodeBpmnId,
                useZeebeUserTasks ? AbstractUserTaskBuilder::zeebeUserTask : (ignored) -> {},
                task -> task.zeebeAssignee(DEFAULT_USER_ID))
            .processIsDeployed()
            .then()
            .startProcessInstance(bpmnProcessId, "{\"process_var_0\": 0, \"process_var_1\": 1}")
            .then()
            .taskIsCreated(flowNodeBpmnId)
            .variablesExist("process_var_0", "process_var_1")
            .getTaskId();

    final var completeRequest =
        new TaskCompleteRequest()
            .setVariables(
                List.of(
                    new VariableInputDTO().setName("process_var_1").setValue("11"),
                    new VariableInputDTO().setName("task_var_2").setValue("22")));

    // when - run completion asynchronously to simulate concurrency
    final SecurityContext securityContext = SecurityContextHolder.getContext();
    final CompletableFuture<MockHttpServletResponse> asyncResult =
        CompletableFuture.supplyAsync(
            () -> {
              try {
                SecurityContextHolder.setContext(securityContext);
                return mockMvcHelper.doRequest(
                    patch(TasklistURIs.TASKS_URL_V1.concat("/{taskId}/complete"), taskId),
                    completeRequest);
              } finally {
                SecurityContextHolder.clearContext();
              }
            });

    // Allow the process instance to complete in Zeebe while task completion is waiting
    // This simulates the race condition where variables are being persisted while
    // the flow node is already terminated
    tester.taskIsCompleted(flowNodeBpmnId);
    // Now signal the adapter to finish task completion
    concurrencyTestConfiguration.completionLatch.countDown();

    // then - verify the async completion was successful
    assertThat(asyncResult.get())
        .hasOkHttpStatus()
        .hasApplicationJsonContentType()
        .extractingContent(objectMapper, TaskResponse.class)
        .satisfies(
            task -> {
              assertThat(task.getId()).isEqualTo(taskId);
              assertThat(task.getAssignee()).isEqualTo(DEFAULT_USER_ID);
              assertThat(task.getTaskState()).isEqualTo(TaskState.COMPLETED);
              assertThat(task.getCreationDate()).isNotNull();
              assertThat(task.getCompletionDate()).isNotNull();
            });

    // when - fetch task variables via search API
    Awaitility.await()
        .untilAsserted(
            () -> {
              final var result =
                  mockMvcHelper.doRequest(
                      post(TasklistURIs.TASKS_URL_V1.concat("/{taskId}/variables/search"), taskId));

              // then
              assertThat(result)
                  .hasOkHttpStatus()
                  .hasApplicationJsonContentType()
                  .extractingListContent(objectMapper, VariableSearchResponse.class)
                  .extracting("name", "value", "previewValue")
                  .containsExactlyInAnyOrder(
                      tuple("process_var_0", "0", "0"),
                      tuple("process_var_1", "11", "11"),
                      tuple("task_var_2", "22", "22"));
            });
  }
}
