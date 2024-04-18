/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE (“USE”), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * “Licensee” means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */
package io.camunda.tasklist.graphql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.graphql.spring.boot.test.GraphQLResponse;
import io.camunda.tasklist.util.TasklistZeebeIntegrationTest;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.io.IOException;
import org.junit.jupiter.api.Test;
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
