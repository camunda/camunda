/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.metric;

import static io.camunda.tasklist.Metrics.COUNTER_NAME_CLAIMED_TASKS;
import static io.camunda.tasklist.Metrics.COUNTER_NAME_COMPLETED_TASKS;
import static io.camunda.tasklist.Metrics.TASKLIST_NAMESPACE;
import static io.camunda.tasklist.util.CollectionUtil.filter;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.tasklist.util.TasklistZeebeIntegrationTest;
import io.camunda.tasklist.webapp.api.rest.v1.entities.TaskSearchResponse;
import io.camunda.tasklist.webapp.dto.UserDTO;
import io.camunda.tasklist.webapp.security.Permission;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.http.ResponseEntity;

public class MetricIT extends TasklistZeebeIntegrationTest {

  public static final String ENDPOINT = "/actuator/prometheus";
  public static final String BPMN_PROCESS_ID = "testProcess";
  public static final String ELEMENT_ID = "taskA";

  @Autowired private TestRestTemplate testRestTemplate;
  @LocalManagementPort private int managementPort;

  private final UserDTO joe = buildAllAccessUserWith("joe", "Joe Doe");
  private final UserDTO jane = buildAllAccessUserWith("jane", "Jane Doe");
  private final UserDTO demo = buildAllAccessUserWith(DEFAULT_USER_ID, DEFAULT_DISPLAY_NAME);

  private static UserDTO buildAllAccessUserWith(final String userId, final String displayName) {
    return new UserDTO()
        .setUserId(userId)
        .setDisplayName(displayName)
        .setPermissions(List.of(Permission.WRITE));
  }

  @Override
  @BeforeEach
  public void before() {
    super.before();
    clearMetrics();
  }

  @Test
  public void providesClaimedTasks() throws IOException {
    // given users: joe, jane and demo
    // create tasks
    final List<TaskSearchResponse> createdTasks =
        tester
            .createCreatedAndCompletedTasks("testProcess", "taskA", 5, 0)
            .then()
            .getCreatedTasks();
    // when
    setCurrentUser(joe);
    tester.assignTask(createdTasks.get(2).getId());
    setCurrentUser(jane);
    tester.assignTask(createdTasks.get(1).getId());
    tester.assignTask(createdTasks.get(3).getId());
    setCurrentUser(demo);
    tester.assignTask(createdTasks.get(4).getId());

    tester.waitFor(2000);
    final List<String> claimedTasksMetrics = metricsFor(COUNTER_NAME_CLAIMED_TASKS);
    assertThat(claimedTasksMetrics).hasSize(3);
    assertThat(filter(claimedTasksMetrics, m -> m.contains("joe")))
        .hasSize(1)
        .allMatch(
            s ->
                s.equals(
                    "tasklist_claimed_tasks_total{bpmnProcessId=\"testProcess\",flowNodeId=\"taskA\",organizationId=\"joe-org\",userId=\"joe\"} 1.0"));
    assertThat(filter(claimedTasksMetrics, m -> m.contains("jane")))
        .hasSize(1)
        .allMatch(
            s ->
                s.equals(
                    "tasklist_claimed_tasks_total{bpmnProcessId=\"testProcess\",flowNodeId=\"taskA\",organizationId=\"jane-org\",userId=\"jane\"} 2.0"));
    assertThat(filter(claimedTasksMetrics, m -> m.contains(DEFAULT_USER_ID)))
        .hasSize(1)
        .allMatch(
            s ->
                s.equals(
                    "tasklist_claimed_tasks_total{bpmnProcessId=\"testProcess\",flowNodeId=\"taskA\",organizationId=\"null\",userId=\""
                        + DEFAULT_USER_ID
                        + "\"} 1.0"));
  }

  @Test
  public void providesCompletedTasks() {
    // given users: joe, jane and demo
    // and
    tester
        .createAndDeploySimpleProcess(BPMN_PROCESS_ID, ELEMENT_ID)
        .waitUntil()
        .processIsDeployed();

    tester.startProcessInstance(BPMN_PROCESS_ID).waitUntil().taskIsCreated(ELEMENT_ID);
    setCurrentUser(joe);
    tester.claimAndCompleteHumanTask(ELEMENT_ID);

    tester.startProcessInstance(BPMN_PROCESS_ID).waitUntil().taskIsCreated(ELEMENT_ID);
    setCurrentUser(jane);
    tester.claimAndCompleteHumanTask(ELEMENT_ID);

    tester.startProcessInstance(BPMN_PROCESS_ID).waitUntil().taskIsCreated(ELEMENT_ID);
    tester.claimAndCompleteHumanTask(ELEMENT_ID);

    tester.startProcessInstance(BPMN_PROCESS_ID).waitUntil().taskIsCreated(ELEMENT_ID);
    setCurrentUser(demo);
    tester.claimAndCompleteHumanTask(ELEMENT_ID);

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
                    "tasklist_completed_tasks_total{bpmnProcessId=\"testProcess\",flowNodeId=\"taskA\",organizationId=\"joe-org\",userId=\"joe\"} 1.0"));
    assertThat(filter(completedTasksMetrics, m -> m.contains("jane")))
        .hasSize(1)
        .allMatch(
            s ->
                s.equals(
                    "tasklist_completed_tasks_total{bpmnProcessId=\"testProcess\",flowNodeId=\"taskA\",organizationId=\"jane-org\",userId=\"jane\"} 2.0"));
    assertThat(filter(completedTasksMetrics, m -> m.contains(DEFAULT_USER_ID)))
        .hasSize(1)
        .allMatch(
            s ->
                s.equals(
                    "tasklist_completed_tasks_total{bpmnProcessId=\"testProcess\",flowNodeId=\"taskA\",organizationId=\"null\",userId=\""
                        + DEFAULT_USER_ID
                        + "\"} 1.0"));
  }

  protected List<String> metricsFor(final String key) {
    final ResponseEntity<String> prometheusResponse =
        testRestTemplate.getForEntity(
            "http://localhost:" + managementPort + ENDPOINT, String.class);
    assertThat(prometheusResponse.getStatusCodeValue()).isEqualTo(200);
    final List<String> metricLines = List.of(prometheusResponse.getBody().split("\n"));
    return filter(
        metricLines, line -> line.startsWith((TASKLIST_NAMESPACE + key).replaceAll("\\.", "_")));
  }
}
