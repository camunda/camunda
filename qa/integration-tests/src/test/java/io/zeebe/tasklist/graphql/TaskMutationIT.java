/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.zeebe.tasklist.graphql;

import static io.zeebe.tasklist.util.ThreadUtil.sleepFor;
import static org.assertj.core.api.Assertions.fail;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.graphql.spring.boot.test.GraphQLResponse;
import com.graphql.spring.boot.test.GraphQLTestTemplate;
import io.zeebe.tasklist.entities.TaskState;
import io.zeebe.tasklist.util.TasklistZeebeIntegrationTest;
import io.zeebe.tasklist.webapp.graphql.query.TaskMutationResolver;
import java.io.IOException;
import java.util.function.Predicate;
import org.junit.Before;
import org.junit.Test;
import org.mockito.internal.util.reflection.FieldSetter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.web.client.TestRestTemplate;

public class TaskMutationIT extends TasklistZeebeIntegrationTest {

  public static final String ELEMENT_ID = "taskA";
  public static final String BPMN_PROCESS_ID = "testProcess";
  public static final String COMPLETE_TASK_QUERY_PATTERN =
      "mutation {completeTask(taskId: \"%s\", variables: [%s])}";
  @Autowired private GraphQLTestTemplate graphQLTestTemplate;

  @Value("${graphql.servlet.mapping:/graphql}")
  private String graphqlMapping;

  @Autowired private TestRestTemplate restTemplate;
  @Autowired private ObjectMapper objectMapper;

  @Autowired
  @Qualifier("taskIsCreatedCheck")
  private Predicate<Object[]> taskIsCreatedCheck;

  @Autowired private TaskMutationResolver taskMutationResolver;

  @Before
  public void before() {
    super.before();
    try {
      FieldSetter.setField(
          taskMutationResolver,
          TaskMutationResolver.class.getDeclaredField("zeebeClient"),
          super.getClient());
    } catch (NoSuchFieldException e) {
      fail("Failed to inject ZeebeClient into some of the beans");
    }
  }

  @Test
  public void shouldCompleteTask() throws IOException {
    // having
    createCreatedAndCompletedTasks(1, 0);

    GraphQLResponse response =
        graphQLTestTemplate.postForResource("graphql/taskIT/get-all-tasks.graphql");
    final String taskId = response.get("$.data.tasks[0].id");

    // when
    final String completeTaskRequest =
        String.format(COMPLETE_TASK_QUERY_PATTERN, taskId, "{name: \"newVar\", value: \"123\"}");
    response = graphQLTestTemplate.postMultipart(completeTaskRequest, "{}");

    // then
    assertEquals("true", response.get("$.data.completeTask"));
    // task is marked as completed
    sleepFor(1000L);
    response = graphQLTestTemplate.postForResource("graphql/taskIT/get-all-tasks.graphql");
    assertEquals(taskId, response.get("$.data.tasks[0].id"));
    assertNotNull(response.get("$.data.tasks[0].completionTime"));
    assertEquals(TaskState.COMPLETED.name(), response.get("$.data.tasks[0].taskState"));
  }

  @Test
  public void shouldCompleteWithoutVariablesTask() throws IOException {
    // having
    createCreatedAndCompletedTasks(1, 0);

    GraphQLResponse response =
        graphQLTestTemplate.postForResource("graphql/taskIT/get-all-tasks.graphql");
    final String taskId = response.get("$.data.tasks[0].id");

    // when
    final String completeTaskRequest = String.format(COMPLETE_TASK_QUERY_PATTERN, taskId, "");
    response = graphQLTestTemplate.postMultipart(completeTaskRequest, "{}");

    // then
    assertEquals("true", response.get("$.data.completeTask"));
    // task is marked as completed
    sleepFor(1000L);
    response = graphQLTestTemplate.postForResource("graphql/taskIT/get-all-tasks.graphql");
    assertEquals(taskId, response.get("$.data.tasks[0].id"));
    assertNotNull(response.get("$.data.tasks[0].completionTime"));
    assertEquals(TaskState.COMPLETED.name(), response.get("$.data.tasks[0].taskState"));
  }

  @Test
  public void shouldFailOnAlreadyCompletedTask() throws IOException {
    // having
    createCreatedAndCompletedTasks(0, 1);

    GraphQLResponse response =
        graphQLTestTemplate.postForResource("graphql/taskIT/get-all-tasks.graphql");
    final String taskId = response.get("$.data.tasks[0].id");

    // when
    final String completeTaskRequest =
        String.format(COMPLETE_TASK_QUERY_PATTERN, taskId, "{name: \"newVar\", value: \"123\"}");
    response = graphQLTestTemplate.postMultipart(completeTaskRequest, "{}");

    // then
    assertNull(response.get("$.data"));
    assertEquals("1", response.get("$.errors.length()"));
    assertEquals(
        String.format(
            "Command rejected with code 'COMPLETE': Expected to complete job with key '%s', but no such job was found",
            taskId),
        response.get("$.errors[0].message"));
  }

  @Test
  public void shouldFailOnNotExistingTask() throws IOException {
    // having
    createCreatedAndCompletedTasks(1, 0);
    final String taskId = "123";

    // when
    final String completeTaskRequest =
        String.format(COMPLETE_TASK_QUERY_PATTERN, taskId, "{name: \"newVar\", value: \"123\"}");
    final GraphQLResponse response = graphQLTestTemplate.postMultipart(completeTaskRequest, "{}");

    // then
    assertNull(response.get("$.data"));
    assertEquals("1", response.get("$.errors.length()"));
    assertEquals(
        String.format(
            "Expected to execute command, but this command refers to an element that doesn't exist.",
            taskId),
        response.get("$.errors[0].message"));
  }

  private void createCreatedAndCompletedTasks(int created, int completed) {
    final String payload = "{\"var\": \"value\"}";
    tester
        .createAndDeploySimpleWorkflow(BPMN_PROCESS_ID, ELEMENT_ID)
        .waitUntil()
        .workflowIsDeployed()
        .and();
    // complete tasks
    for (int i = 0; i < completed; i++) {
      tester.startWorkflowInstance(BPMN_PROCESS_ID, payload).and().completeHumanTask(ELEMENT_ID);
    }
    // start more workflow instances
    for (int i = 0; i < created; i++) {
      tester.startWorkflowInstance(BPMN_PROCESS_ID, payload).waitUntil().taskIsCreated(ELEMENT_ID);
    }
  }
}
