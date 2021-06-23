/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.tasklist.metric;

import static io.camunda.tasklist.Metrics.COUNTER_NAME_CLAIMED_TASKS;
import static io.camunda.tasklist.Metrics.COUNTER_NAME_COMPLETED_TASKS;
import static io.camunda.tasklist.Metrics.TASKLIST_NAMESPACE;
import static io.camunda.tasklist.util.CollectionUtil.filter;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.tasklist.graphql.TaskIT;
import io.camunda.tasklist.util.TasklistZeebeIntegrationTest;
import io.camunda.tasklist.webapp.graphql.entity.TaskDTO;
import io.camunda.tasklist.webapp.graphql.entity.UserDTO;
import io.camunda.tasklist.webapp.graphql.mutation.TaskMutationResolver;
import java.io.IOException;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;

public class MetricIT extends TasklistZeebeIntegrationTest {

  public static final String ENDPOINT = "/actuator/prometheus";

  @Autowired private TaskMutationResolver taskMutationResolver;

  @Autowired private TestRestTemplate testRestTemplate;

  @Before
  public void before() {
    super.before();
    taskMutationResolver.setZeebeClient(super.getClient());
  }

  @Test
  public void providesClaimedTasks() throws IOException {
    // create users
    final UserDTO joe = new UserDTO().setUsername("joe").setFirstname("Joe").setLastname("Doe");
    final UserDTO jane = new UserDTO().setUsername("jane").setFirstname("Jane").setLastname("Doe");
    final UserDTO demo = new UserDTO().setUsername("demo").setFirstname("Demo").setLastname("User");
    // create tasks
    final List<TaskDTO> createdTasks =
        tester
            .createCreatedAndCompletedTasks("testProcess", "taskA", 5, 0)
            .then()
            .getCreatedTasks();
    // when
    setCurrentUser(joe);
    tester.claimTask(
        String.format(TaskIT.CLAIM_TASK_MUTATION_PATTERN, createdTasks.get(2).getId()));
    setCurrentUser(jane);
    tester.claimTask(
        String.format(TaskIT.CLAIM_TASK_MUTATION_PATTERN, createdTasks.get(1).getId()));
    tester.claimTask(
        String.format(TaskIT.CLAIM_TASK_MUTATION_PATTERN, createdTasks.get(3).getId()));
    setCurrentUser(demo);
    tester.claimTask(
        String.format(TaskIT.CLAIM_TASK_MUTATION_PATTERN, createdTasks.get(4).getId()));

    tester.waitFor(2000);
    final List<String> claimedTasksMetrics = metricsFor(COUNTER_NAME_CLAIMED_TASKS);
    assertThat(claimedTasksMetrics).hasSize(3);
    assertThat(filter(claimedTasksMetrics, m -> m.contains("joe")))
        .hasSize(1)
        .allMatch(
            s ->
                s.equals(
                    "tasklist_claimed_tasks_total{assignee=\"joe\",bpmnProcessId=\"testProcess\",flowNodeId=\"taskA\",} 1.0"));
    assertThat(filter(claimedTasksMetrics, m -> m.contains("jane")))
        .hasSize(1)
        .allMatch(
            s ->
                s.equals(
                    "tasklist_claimed_tasks_total{assignee=\"jane\",bpmnProcessId=\"testProcess\",flowNodeId=\"taskA\",} 2.0"));
    assertThat(filter(claimedTasksMetrics, m -> m.contains("demo")))
        .hasSize(1)
        .allMatch(
            s ->
                s.equals(
                    "tasklist_claimed_tasks_total{assignee=\"demo\",bpmnProcessId=\"testProcess\",flowNodeId=\"taskA\",} 1.0"));
  }

  @Test
  public void providesCompletedTasks() throws IOException {
    // create users
    final UserDTO joe = new UserDTO().setUsername("joe").setFirstname("Joe").setLastname("Doe");
    final UserDTO jane = new UserDTO().setUsername("jane").setFirstname("Jane").setLastname("Doe");
    final UserDTO demo = new UserDTO().setUsername("demo").setFirstname("Demo").setLastname("User");
    // given
    tester
        .createAndDeploySimpleProcess(TaskIT.BPMN_PROCESS_ID, TaskIT.ELEMENT_ID)
        .waitUntil()
        .processIsDeployed();

    tester
        .startProcessInstance(TaskIT.BPMN_PROCESS_ID)
        .waitUntil()
        .taskIsCreated(TaskIT.ELEMENT_ID);
    setCurrentUser(joe);
    tester.claimAndCompleteHumanTask(TaskIT.ELEMENT_ID);

    tester
        .startProcessInstance(TaskIT.BPMN_PROCESS_ID)
        .waitUntil()
        .taskIsCreated(TaskIT.ELEMENT_ID);
    setCurrentUser(jane);
    tester.claimAndCompleteHumanTask(TaskIT.ELEMENT_ID);

    tester
        .startProcessInstance(TaskIT.BPMN_PROCESS_ID)
        .waitUntil()
        .taskIsCreated(TaskIT.ELEMENT_ID);
    tester.claimAndCompleteHumanTask(TaskIT.ELEMENT_ID);

    tester
        .startProcessInstance(TaskIT.BPMN_PROCESS_ID)
        .waitUntil()
        .taskIsCreated(TaskIT.ELEMENT_ID);
    setCurrentUser(demo);
    tester.claimAndCompleteHumanTask(TaskIT.ELEMENT_ID);

    tester.waitFor(2000);
    // when
    final List<String> completedTasksMetrics = metricsFor(COUNTER_NAME_COMPLETED_TASKS);
    // then
    assertThat(completedTasksMetrics).hasSize(3);
    assertThat(filter(completedTasksMetrics, m -> m.contains("joe")))
        .hasSize(1)
        .allMatch(
            s ->
                s.equals(
                    "tasklist_completed_tasks_total{assignee=\"joe\",bpmnProcessId=\"testProcess\",flowNodeId=\"taskA\",} 1.0"));
    assertThat(filter(completedTasksMetrics, m -> m.contains("jane")))
        .hasSize(1)
        .allMatch(
            s ->
                s.equals(
                    "tasklist_completed_tasks_total{assignee=\"jane\",bpmnProcessId=\"testProcess\",flowNodeId=\"taskA\",} 2.0"));
    assertThat(filter(completedTasksMetrics, m -> m.contains("demo")))
        .hasSize(1)
        .allMatch(
            s ->
                s.equals(
                    "tasklist_completed_tasks_total{assignee=\"demo\",bpmnProcessId=\"testProcess\",flowNodeId=\"taskA\",} 1.0"));
  }

  protected List<String> metricsFor(final String key) {
    final ResponseEntity<String> prometheusResponse =
        testRestTemplate.getForEntity(ENDPOINT, String.class);
    assertThat(prometheusResponse.getStatusCodeValue()).isEqualTo(200);
    final List<String> metricLines = List.of(prometheusResponse.getBody().split("\n"));
    return filter(
        metricLines, line -> line.startsWith((TASKLIST_NAMESPACE + key).replaceAll("\\.", "_")));
  }
}
