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
import io.camunda.tasklist.webapp.security.Permission;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;

public class MetricIT extends TasklistZeebeIntegrationTest {

  public static final String ENDPOINT = "/actuator/prometheus";

  @Autowired private TaskMutationResolver taskMutationResolver;

  @Autowired private TestRestTemplate testRestTemplate;

  private final UserDTO joe = buildAllAccessUserWith("joe", "Joe Doe");
  private final UserDTO jane = buildAllAccessUserWith("jane", "Jane Doe");
  private final UserDTO demo = buildAllAccessUserWith(DEFAULT_USER_ID, DEFAULT_DISPLAY_NAME);

  private static UserDTO buildAllAccessUserWith(String userId, String displayName) {
    return new UserDTO()
        .setUserId(userId)
        .setDisplayName(displayName)
        .setPermissions(List.of(Permission.WRITE));
  }

  @BeforeEach
  public void before() {
    super.before();
    clearMetrics();
  }

  @Test
  public void providesClaimedTasks() throws IOException {
    // given users: joe, jane and demo
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
                    "tasklist_claimed_tasks_total{bpmnProcessId=\"testProcess\",flowNodeId=\"taskA\",organizationId=\"joe-org\",userId=\"joe\",} 1.0"));
    assertThat(filter(claimedTasksMetrics, m -> m.contains("jane")))
        .hasSize(1)
        .allMatch(
            s ->
                s.equals(
                    "tasklist_claimed_tasks_total{bpmnProcessId=\"testProcess\",flowNodeId=\"taskA\",organizationId=\"jane-org\",userId=\"jane\",} 2.0"));
    assertThat(filter(claimedTasksMetrics, m -> m.contains(DEFAULT_USER_ID)))
        .hasSize(1)
        .allMatch(
            s ->
                s.equals(
                    "tasklist_claimed_tasks_total{bpmnProcessId=\"testProcess\",flowNodeId=\"taskA\",organizationId=\"null\",userId=\""
                        + DEFAULT_USER_ID
                        + "\",} 1.0"));
  }

  @Test
  public void providesCompletedTasks() {
    // given users: joe, jane and demo
    // and
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
                    "tasklist_completed_tasks_total{bpmnProcessId=\"testProcess\",flowNodeId=\"taskA\",organizationId=\"joe-org\",userId=\"joe\",} 1.0"));
    assertThat(filter(completedTasksMetrics, m -> m.contains("jane")))
        .hasSize(1)
        .allMatch(
            s ->
                s.equals(
                    "tasklist_completed_tasks_total{bpmnProcessId=\"testProcess\",flowNodeId=\"taskA\",organizationId=\"jane-org\",userId=\"jane\",} 2.0"));
    assertThat(filter(completedTasksMetrics, m -> m.contains(DEFAULT_USER_ID)))
        .hasSize(1)
        .allMatch(
            s ->
                s.equals(
                    "tasklist_completed_tasks_total{bpmnProcessId=\"testProcess\",flowNodeId=\"taskA\",organizationId=\"null\",userId=\""
                        + DEFAULT_USER_ID
                        + "\",} 1.0"));
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
