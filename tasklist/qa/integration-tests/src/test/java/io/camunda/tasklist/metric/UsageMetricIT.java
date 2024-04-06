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

import static io.camunda.tasklist.property.ElasticsearchProperties.DATE_FORMAT_DEFAULT;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.tasklist.entities.TaskEntity;
import io.camunda.tasklist.graphql.TaskIT;
import io.camunda.tasklist.store.TaskMetricsStore;
import io.camunda.tasklist.util.TasklistZeebeIntegrationTest;
import io.camunda.tasklist.webapp.graphql.entity.UserDTO;
import io.camunda.tasklist.webapp.management.dto.UsageMetricDTO;
import io.camunda.tasklist.webapp.security.Permission;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

public class UsageMetricIT extends TasklistZeebeIntegrationTest {

  public static final String ASSIGNEE_ENDPOINT =
      "/actuator/usage-metrics/assignees?startTime={startTime}&endTime={endTime}";
  public static final DateTimeFormatter FORMATTER =
      DateTimeFormatter.ofPattern(DATE_FORMAT_DEFAULT);

  @Autowired private TestRestTemplate testRestTemplate;
  @Autowired private TaskMetricsStore taskMetricsStore;
  private final UserDTO joe = buildAllAccessUserWith("joe", "Joe", "Doe");
  private final UserDTO jane = buildAllAccessUserWith("jane", "Jane", "Doe");
  private final UserDTO demo = buildAllAccessUserWith("demo", "Demo", "User");

  private static UserDTO buildAllAccessUserWith(
      String username, String firstname, String lastname) {
    return new UserDTO()
        .setUserId(username)
        .setDisplayName(String.format("%s %s", firstname, lastname))
        .setPermissions(List.of(Permission.WRITE));
  }

  @BeforeEach
  public void before() {
    super.before();
  }

  @Test
  public void validateActuatorEndpointRegistered() {
    final Map<String, String> parameters = new HashMap<>();
    parameters.put("startTime", "1970-11-14T10:50:26.963-0100");
    parameters.put("endTime", "1970-11-14T10:50:26.963-0100");
    final ResponseEntity<UsageMetricDTO> response =
        testRestTemplate.getForEntity(ASSIGNEE_ENDPOINT, UsageMetricDTO.class, parameters);

    assertThat(response.getStatusCodeValue()).isEqualTo(200);
    assertThat(response.getBody().getAssignees()).isEqualTo(List.of());
    assertThat(response.getBody().getTotal()).isEqualTo(0);
  }

  @Test
  public void shouldReturnExpectedDataForCorrectTimeRange() {
    final OffsetDateTime now = OffsetDateTime.now();
    insertMetricForAssignee("John Lennon", now);
    insertMetricForAssignee("Angela Merkel", now);
    insertMetricForAssignee("Angela Merkel", now);
    insertMetricForAssignee("Angela Merkel", now);
    insertMetricForAssignee("Angela Merkel", now);

    databaseTestExtension.refreshTasklistIndices();

    final Map<String, String> parameters = new HashMap<>();
    parameters.put("startTime", now.minusSeconds(1L).format(FORMATTER));
    parameters.put("endTime", now.plusSeconds(1L).format(FORMATTER));
    final ResponseEntity<UsageMetricDTO> response =
        testRestTemplate.getForEntity(ASSIGNEE_ENDPOINT, UsageMetricDTO.class, parameters);

    final UsageMetricDTO expectedDto =
        new UsageMetricDTO(List.of("Angela Merkel", "John Lennon")); // just repeat once

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isEqualTo(expectedDto);
  }

  @Test
  public void shouldReturnEmptyDataIfWrongTimeRange() throws IOException, InterruptedException {
    final OffsetDateTime now = OffsetDateTime.now();
    insertMetricForAssignee("John Lennon", now);
    insertMetricForAssignee("Angela Merkel", now);
    insertMetricForAssignee("Angela Merkel", now);
    insertMetricForAssignee("Angela Merkel", now);
    insertMetricForAssignee("Angela Merkel", now);

    databaseTestExtension.refreshTasklistIndices();

    final Map<String, String> parameters = new HashMap<>();
    parameters.put("startTime", now.plusMinutes(5L).format(FORMATTER)); // out of range
    parameters.put("endTime", now.plusMinutes(15L).format(FORMATTER));
    final ResponseEntity<UsageMetricDTO> response =
        testRestTemplate.getForEntity(ASSIGNEE_ENDPOINT, UsageMetricDTO.class, parameters);

    final UsageMetricDTO expectedDto = new UsageMetricDTO(List.of());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isEqualTo(expectedDto);
  }

  @Test
  @Disabled(
      "Ignoring this test for now as it is quite slow - we can remove this mark to verify at any time")
  public void shouldReturnOverTenThousandObjects() {
    final OffsetDateTime now = OffsetDateTime.now();
    for (int i = 0; i <= 10_000; i++) {
      insertMetricForAssignee("Assignee " + i, now); // 10_001 different assignees
    }
    databaseTestExtension.refreshTasklistIndices();

    final Map<String, String> parameters = new HashMap<>();
    parameters.put("startTime", now.minusSeconds(1L).format(FORMATTER));
    parameters.put("endTime", now.plusSeconds(1L).format(FORMATTER));
    final ResponseEntity<UsageMetricDTO> response =
        testRestTemplate.getForEntity(ASSIGNEE_ENDPOINT, UsageMetricDTO.class, parameters);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().getTotal()).isEqualTo(10_001);
  }

  @Test
  public void providesCompletedTasks() throws IOException, InterruptedException {
    final OffsetDateTime now = OffsetDateTime.now();

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
    databaseTestExtension.refreshTasklistIndices();
    // when
    final Map<String, String> parameters = new HashMap<>();
    parameters.put("startTime", now.minusMinutes(5L).format(FORMATTER));
    parameters.put("endTime", now.plusMinutes(15L).format(FORMATTER));
    final ResponseEntity<UsageMetricDTO> response =
        testRestTemplate.getForEntity(ASSIGNEE_ENDPOINT, UsageMetricDTO.class, parameters);

    // then
    final UsageMetricDTO expectedDto = new UsageMetricDTO(List.of("jane", "demo", "joe"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isEqualTo(expectedDto);
  }

  private void insertMetricForAssignee(String assignee, OffsetDateTime eventTime) {
    final var task = new TaskEntity().setAssignee(assignee).setCompletionTime(eventTime);
    taskMetricsStore.registerTaskCompleteEvent(task);
  }
}
