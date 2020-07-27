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

import com.graphql.spring.boot.test.GraphQLResponse;
import com.graphql.spring.boot.test.GraphQLTestTemplate;
import io.zeebe.tasklist.entities.TaskState;
import io.zeebe.tasklist.util.TasklistZeebeIntegrationTest;
import io.zeebe.tasklist.webapp.graphql.entity.TaskDTO;
import io.zeebe.tasklist.webapp.graphql.entity.UserDTO;
import io.zeebe.tasklist.webapp.graphql.mutation.TaskMutationResolver;
import java.io.IOException;
import java.util.Map;
import java.util.function.Predicate;
import org.junit.Before;
import org.junit.Test;
import org.mockito.internal.util.reflection.FieldSetter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;

public class TaskMutationIT extends TasklistZeebeIntegrationTest {

  public static final String ELEMENT_ID = "taskA";
  public static final String BPMN_PROCESS_ID = "testProcess";
  public static final String COMPLETE_TASK_QUERY_PATTERN =
      "mutation {completeTask(taskId: \"%s\", variables: [%s])}";
  public static final String TASK_RESULT_PATTERN =
      "{id name assignee {username firstname lastname}}";
  public static final String CLAIM_TASK_MUTATION_PATTERN =
      "mutation {claimTask(taskId: \"%s\")" + TASK_RESULT_PATTERN + "}";
  public static final String UNCLAIM_TASK_MUTATION_PATTERN =
      "mutation {unclaimTask(taskId: \"%s\")" + TASK_RESULT_PATTERN + "}";
  @Autowired private GraphQLTestTemplate graphQLTestTemplate;

  @Value("${graphql.servlet.mapping:/graphql}")
  private String graphqlMapping;

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
  public void shouldFailCompleteNotActive() throws IOException {
    // having
    createCreatedAndCompletedTasks(0, 1);

    GraphQLResponse response = tester.getAllTasks();
    final String taskId = response.get("$.data.tasks[0].id");

    // when
    haveLoggedInUser(new UserDTO().setUsername("demo"));

    final String completeTaskRequest =
        String.format(COMPLETE_TASK_QUERY_PATTERN, taskId, "{name: \"newVar\", value: \"123\"}");
    response = graphQLTestTemplate.postMultipart(completeTaskRequest, "{}");

    // then
    assertEquals("Task is not active", response.get("$.errors[0].message"));
  }

  @Test
  public void shouldFailCompleteNotAssigned() throws IOException {
    // having
    createCreatedAndCompletedTasks(1, 0);

    GraphQLResponse response =
        graphQLTestTemplate.postForResource("graphql/taskIT/get-all-tasks.graphql");
    final String taskId = response.get("$.data.tasks[0].id");

    // when
    haveLoggedInUser(new UserDTO().setUsername("demo"));

    final String completeTaskRequest =
        String.format(COMPLETE_TASK_QUERY_PATTERN, taskId, "{name: \"newVar\", value: \"123\"}");

    response = graphQLTestTemplate.postMultipart(completeTaskRequest, "{}");

    // then
    assertEquals("Task is not assigned", response.get("$.errors[0].message"));
  }

  @Test
  public void shouldFailCompleteNotAssignedToMe() throws IOException {
    // having
    createCreatedAndCompletedTasks(1, 0);

    GraphQLResponse response =
        graphQLTestTemplate.postForResource("graphql/taskIT/get-all-tasks.graphql");
    final String taskId = response.get("$.data.tasks[0].id");

    haveLoggedInUser(new UserDTO().setUsername("demo"));
    tester.claimTask(String.format(CLAIM_TASK_MUTATION_PATTERN, taskId));

    // when
    haveLoggedInUser(new UserDTO().setUsername("joe"));
    final String completeTaskRequest =
        String.format(COMPLETE_TASK_QUERY_PATTERN, taskId, "{name: \"newVar\", value: \"123\"}");

    response = graphQLTestTemplate.postMultipart(completeTaskRequest, "{}");

    // then
    assertEquals("Task is not assigned to joe", response.get("$.errors[0].message"));
  }

  @Test
  public void shouldCompleteTask() throws IOException {
    // having
    createCreatedAndCompletedTasks(1, 0);

    GraphQLResponse response =
        graphQLTestTemplate.postForResource("graphql/taskIT/get-all-tasks.graphql");
    final String taskId = response.get("$.data.tasks[0].id");

    // when
    haveLoggedInUser(new UserDTO().setUsername("demo"));
    tester.claimTask(String.format(CLAIM_TASK_MUTATION_PATTERN, taskId));

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
    haveLoggedInUser(new UserDTO().setUsername("demo"));
    tester.claimTask(String.format(CLAIM_TASK_MUTATION_PATTERN, taskId));

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
        String.format("Task with id %s was not found", taskId),
        response.get("$.errors[0].message"));
  }

  @Test
  public void shouldFailClaimNotActive() throws IOException {
    // having
    haveLoggedInUser(new UserDTO().setUsername("demo").setFirstname("Demo").setLastname("User"));
    tester
        .having()
        .and()
        .createCreatedAndCompletedTasks(BPMN_PROCESS_ID, ELEMENT_ID, 0, 1)
        .when()
        .getAllTasks();

    final TaskDTO unclaimedTask = tester.getTasksByPath("$.data.tasks").get(0);

    final Map<String, Object> errors =
        tester
            .when()
            .claimTask(String.format(CLAIM_TASK_MUTATION_PATTERN, unclaimedTask.getId()))
            .then()
            .getByPath("$.errors[0]");
    assertEquals("Task is not active", errors.get("message"));
  }

  @Test
  public void shouldFailClaimAlreadyAssigned() throws IOException {
    haveLoggedInUser(new UserDTO().setUsername("demo").setFirstname("Demo").setLastname("User"));
    tester
        .having()
        .and()
        .createCreatedAndCompletedTasks(BPMN_PROCESS_ID, ELEMENT_ID, 1, 0)
        .when()
        .getAllTasks();

    final TaskDTO unclaimedTask = tester.getTasksByPath("$.data.tasks").get(0);
    tester.claimTask(String.format(CLAIM_TASK_MUTATION_PATTERN, unclaimedTask.getId()));

    // when
    haveLoggedInUser(new UserDTO().setUsername("joe"));
    final Map<String, Object> errors =
        tester
            .when()
            .claimTask(String.format(CLAIM_TASK_MUTATION_PATTERN, unclaimedTask.getId()))
            .then()
            .getByPath("$.errors[0]");
    assertEquals("Task is already assigned", errors.get("message"));
  }

  @Test
  public void shouldClaimUserToTask() throws IOException {
    haveLoggedInUser(new UserDTO().setUsername("demo").setFirstname("Demo").setLastname("User"));
    tester
        .having()
        .and()
        .createCreatedAndCompletedTasks(BPMN_PROCESS_ID, ELEMENT_ID, 1, 0)
        .when()
        .getAllTasks();

    final TaskDTO unclaimedTask = tester.getTasksByPath("$.data.tasks").get(0);

    final Map<String, Object> claimedTask =
        tester
            .when()
            .claimTask(String.format(CLAIM_TASK_MUTATION_PATTERN, unclaimedTask.getId()))
            .then()
            .getByPath("$.data.claimTask");

    final Map<String, Object> user = (Map<String, Object>) claimedTask.get("assignee");
    assertEquals(claimedTask.get("id"), unclaimedTask.getId());
    assertEquals("demo", user.get("username"));
    assertEquals("Demo", user.get("firstname"));
    assertEquals("User", user.get("lastname"));
  }

  @Test
  public void shouldFailUnclaimNotActive() throws IOException {
    // having
    tester.having().createCreatedAndCompletedTasks(BPMN_PROCESS_ID, ELEMENT_ID, 0, 1).getAllTasks();

    final String taskId = tester.get("$.data.tasks[0].id");
    haveLoggedInUser(new UserDTO().setUsername("demo").setFirstname("Demo").setLastname("User"));

    // when
    final Map<String, Object> errors =
        tester
            .when()
            .unclaimTask(String.format(UNCLAIM_TASK_MUTATION_PATTERN, taskId))
            .then()
            .getByPath("$.errors[0]");

    // then
    assertEquals("Task is not active", errors.get("message"));
  }

  @Test
  public void shouldFailUnclaimNotAssigned() throws IOException {
    // having
    tester.having().createCreatedAndCompletedTasks(BPMN_PROCESS_ID, ELEMENT_ID, 1, 0).getAllTasks();

    final String taskId = tester.get("$.data.tasks[0].id");
    haveLoggedInUser(new UserDTO().setUsername("demo").setFirstname("Demo").setLastname("User"));

    // when
    final Map<String, Object> errors =
        tester
            .when()
            .unclaimTask(String.format(UNCLAIM_TASK_MUTATION_PATTERN, taskId))
            .then()
            .getByPath("$.errors[0]");

    // then
    assertEquals("Task is not assigned", errors.get("message"));
  }

  @Test
  public void shouldUnclaimUserToTask() throws IOException {
    // having
    tester.having().createCreatedAndCompletedTasks(BPMN_PROCESS_ID, ELEMENT_ID, 1, 0).getAllTasks();

    final String taskId = tester.get("$.data.tasks[0].id");
    haveLoggedInUser(new UserDTO().setUsername("demo").setFirstname("Demo").setLastname("User"));
    tester.claimTask(String.format(CLAIM_TASK_MUTATION_PATTERN, taskId));

    // when
    final Map<String, Object> task =
        tester
            .when()
            .unclaimTask(String.format(UNCLAIM_TASK_MUTATION_PATTERN, taskId))
            .then()
            .getByPath("$.data.unclaimTask");

    // then
    assertEquals(taskId, task.get("id"));
    assertNull(task.get("assignee"));
  }

  private void createCreatedAndCompletedTasks(int created, int completed) {
    final String payload = "{\"var\": \"value\"}";
    tester
        .createAndDeploySimpleWorkflow(BPMN_PROCESS_ID, ELEMENT_ID)
        .waitUntil()
        .workflowIsDeployed()
        .and();
    sleepFor(5000);
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
