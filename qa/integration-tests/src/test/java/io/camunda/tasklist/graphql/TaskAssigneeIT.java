/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.graphql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.graphql.spring.boot.test.GraphQLResponse;
import io.camunda.tasklist.util.TasklistZeebeIntegrationTest;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.io.IOException;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class TaskAssigneeIT extends TasklistZeebeIntegrationTest {

  public static final String ELEMENT_ID = "taskA";
  public static final String BPMN_PROCESS_ID = "testProcess";
  public static final String ASSIGNEE = "user1";
  public static final String GROUP_1 = "group1";
  public static final String GROUP_2 = "group2";
  public static final String USER_1 = "user1";
  public static final String USER_2 = "user2";

  @Autowired private ObjectMapper objectMapper;

  @Test
  public void shouldReturnAssigneeAndCandidateGroups() throws IOException {
    final String candidateGroups = GROUP_1 + ", " + GROUP_2;
    final BpmnModelInstance model =
        Bpmn.createExecutableProcess(BPMN_PROCESS_ID)
            .startEvent("start")
            .userTask(
                ELEMENT_ID,
                task -> {
                  task.zeebeAssignee(ASSIGNEE);
                  task.zeebeCandidateGroups(candidateGroups);
                })
            .endEvent()
            .done();
    final GraphQLResponse response =
        tester
            .having()
            .deployProcess(model, "testProcess.bpmn")
            .waitUntil()
            .processIsDeployed()
            .and()
            .startProcessInstance(BPMN_PROCESS_ID)
            .waitUntil()
            .taskIsCreated(ELEMENT_ID)
            .getAllTasks();

    final String taskId = response.get("$.data.tasks[0].id");

    // when
    final GraphQLResponse taskResponse = tester.getTaskById(taskId);

    // then
    assertEquals(taskId, taskResponse.get("$.data.task.id"));
    assertEquals(ELEMENT_ID, taskResponse.get("$.data.task.name"));
    assertEquals(ASSIGNEE, taskResponse.get("$.data.task.assignee"));
    assertEquals("2", taskResponse.get("$.data.task.candidateGroups.length()"));
    assertThat(taskResponse.getList("$.data.task.candidateGroups", String.class))
        .containsExactlyInAnyOrder(GROUP_1, GROUP_2);
  }

  @Test
  public void shouldReturnAssigneeAndCandidateGroupsAsEmptyCandidateGroups() throws IOException {
    final BpmnModelInstance model =
        Bpmn.createExecutableProcess(BPMN_PROCESS_ID)
            .startEvent("start")
            .userTask(ELEMENT_ID, task -> task.zeebeCandidateGroups("=candidateGroups"))
            .endEvent()
            .done();
    final String payload = "{\"candidateGroups\": []}";
    final GraphQLResponse response =
        tester
            .having()
            .deployProcess(model, "testProcess.bpmn")
            .waitUntil()
            .processIsDeployed()
            .and()
            .startProcessInstance(BPMN_PROCESS_ID, payload)
            .waitUntil()
            .taskIsCreated(ELEMENT_ID)
            .getAllTasks();

    final String taskId = response.get("$.data.tasks[0].id");

    // when
    final GraphQLResponse taskResponse = tester.getTaskById(taskId);

    // then
    assertEquals(taskId, taskResponse.get("$.data.task.id"));
    assertEquals(ELEMENT_ID, taskResponse.get("$.data.task.name"));
    assertEquals(null, taskResponse.get("$.data.task.assignee"));
    assertEquals("0", taskResponse.get("$.data.task.candidateGroups.length()"));
  }

  @Test
  public void shouldReturnAssigneeAndCandidateGroupsAsExpression() throws IOException {
    final String candidateGroups = GROUP_1 + ", " + GROUP_2;
    final BpmnModelInstance model =
        Bpmn.createExecutableProcess(BPMN_PROCESS_ID)
            .startEvent("start")
            .userTask(
                ELEMENT_ID,
                task -> {
                  task.zeebeAssignee("=assignee");
                  task.zeebeCandidateGroups("=candidateGroups");
                })
            .endEvent()
            .done();
    final String payload =
        "{\"candidateGroups\": [\""
            + GROUP_1
            + "\", \""
            + GROUP_2
            + "\"],"
            + "\"assignee\": \""
            + ASSIGNEE
            + "\"}";
    final GraphQLResponse response =
        tester
            .having()
            .deployProcess(model, "testProcess.bpmn")
            .waitUntil()
            .processIsDeployed()
            .and()
            .startProcessInstance(BPMN_PROCESS_ID, payload)
            .waitUntil()
            .taskIsCreated(ELEMENT_ID)
            .getAllTasks();

    final String taskId = response.get("$.data.tasks[0].id");

    // when
    final GraphQLResponse taskResponse = tester.getTaskById(taskId);

    // then
    assertEquals(taskId, taskResponse.get("$.data.task.id"));
    assertEquals(ELEMENT_ID, taskResponse.get("$.data.task.name"));
    assertEquals(ASSIGNEE, taskResponse.get("$.data.task.assignee"));
    assertEquals("2", taskResponse.get("$.data.task.candidateGroups.length()"));
    assertThat(taskResponse.getList("$.data.task.candidateGroups", String.class))
        .containsExactlyInAnyOrder(GROUP_1, GROUP_2);
  }

  @Test
  public void shouldReturnAssigneeAndCandidateUsers() throws IOException {
    final String candidateUsers = USER_1 + ", " + USER_2;
    final GraphQLResponse response =
        this.getAllTasksForAssigneeAndCandidateUsers(ASSIGNEE, candidateUsers, null);

    final String taskId = response.get("$.data.tasks[0].id");

    // when
    final GraphQLResponse taskResponse = tester.getTaskById(taskId);

    // then
    assertEquals(taskId, taskResponse.get("$.data.task.id"));
    assertEquals(ELEMENT_ID, taskResponse.get("$.data.task.name"));
    assertEquals(ASSIGNEE, taskResponse.get("$.data.task.assignee"));
    assertEquals("2", taskResponse.get("$.data.task.candidateUsers.length()"));
    assertThat(taskResponse.getList("$.data.task.candidateUsers", String.class))
        .containsExactlyInAnyOrder(USER_1, USER_2);
  }

  @Test
  public void shouldReturnAssigneeAndCandidateUsersAsEmptyCandidateUsers() throws IOException {
    final String payload = "{\"candidateUsers\": []}";

    final GraphQLResponse response =
        this.getAllTasksForAssigneeAndCandidateUsers(null, "=candidateUsers", payload);

    final String taskId = response.get("$.data.tasks[0].id");

    // when
    final GraphQLResponse taskResponse = tester.getTaskById(taskId);

    // then
    assertEquals(taskId, taskResponse.get("$.data.task.id"));
    assertEquals(ELEMENT_ID, taskResponse.get("$.data.task.name"));
    assertEquals(null, taskResponse.get("$.data.task.assignee"));
    assertEquals("0", taskResponse.get("$.data.task.candidateUsers.length()"));
  }

  @Test
  public void shouldReturnAssigneeAndCandidateUsersAsExpression() throws IOException {

    final String payload =
        "{\"candidateUsers\": [\""
            + USER_1
            + "\", \""
            + USER_2
            + "\"],"
            + "\"assignee\": \""
            + ASSIGNEE
            + "\"}";

    final GraphQLResponse response =
        this.getAllTasksForAssigneeAndCandidateUsers("=assignee", "=candidateUsers", payload);
    final String taskId = response.get("$.data.tasks[0].id");
    final GraphQLResponse taskResponse = tester.getTaskById(taskId);

    assertEquals(taskId, taskResponse.get("$.data.task.id"));
    assertEquals(ELEMENT_ID, taskResponse.get("$.data.task.name"));
    assertEquals(ASSIGNEE, taskResponse.get("$.data.task.assignee"));
    assertEquals("2", taskResponse.get("$.data.task.candidateUsers.length()"));
    assertThat(taskResponse.getList("$.data.task.candidateUsers", String.class))
        .containsExactlyInAnyOrder(USER_1, USER_2);
  }

  public GraphQLResponse getAllTasksForAssigneeAndCandidateUsers(
      String assignee, String candidateUsers, String payload) throws IOException {
    final BpmnModelInstance model =
        Bpmn.createExecutableProcess(BPMN_PROCESS_ID)
            .startEvent("start")
            .userTask(
                ELEMENT_ID,
                task -> {
                  if (assignee != null) {
                    task.zeebeAssignee(ASSIGNEE);
                  }
                  if (candidateUsers != null) {
                    task.zeebeCandidateUsers(candidateUsers);
                  }
                })
            .endEvent()
            .done();
    final GraphQLResponse response =
        tester
            .having()
            .deployProcess(model, "testProcess.bpmn")
            .waitUntil()
            .processIsDeployed()
            .and()
            .startProcessInstance(BPMN_PROCESS_ID, payload)
            .waitUntil()
            .taskIsCreated(ELEMENT_ID)
            .getAllTasks();
    return response;
  }
}
