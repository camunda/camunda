/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.metric;

import static io.camunda.tasklist.property.ElasticsearchProperties.DATE_FORMAT_DEFAULT;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.tasklist.entities.MetricEntity;
import io.camunda.tasklist.graphql.TaskIT;
import io.camunda.tasklist.schema.indices.MetricIndex;
import io.camunda.tasklist.util.TasklistZeebeIntegrationTest;
import io.camunda.tasklist.webapp.es.MetricReaderWriter;
import io.camunda.tasklist.webapp.es.dao.UsageMetricDAO;
import io.camunda.tasklist.webapp.graphql.entity.UserDTO;
import io.camunda.tasklist.webapp.graphql.mutation.TaskMutationResolver;
import io.camunda.tasklist.webapp.management.dto.UsageMetricDTO;
import io.camunda.tasklist.webapp.security.Permission;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.elasticsearch.client.RestHighLevelClient;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
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
  @Autowired private UsageMetricDAO dao;
  @Autowired private RestHighLevelClient esClient;
  @Autowired private MetricIndex index;
  @Autowired private TaskMutationResolver taskMutationResolver;
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

  @Before
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
  public void shouldReturnExpectedDataForCorrectTimeRange()
      throws IOException, InterruptedException {
    final OffsetDateTime now = OffsetDateTime.now();
    insertMetricForAssignee("John Lennon", now);
    insertMetricForAssignee("Angela Merkel", now);
    insertMetricForAssignee("Angela Merkel", now);
    insertMetricForAssignee("Angela Merkel", now);
    insertMetricForAssignee("Angela Merkel", now);

    elasticsearchTestRule.refreshTasklistESIndices();

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

    elasticsearchTestRule.refreshTasklistESIndices();

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
  @Ignore(
      "Ignoring this test for now as it is quite slow - we can remove this mark to verify at any time")
  public void shouldReturnOverTenThousandObjects() throws IOException, InterruptedException {
    final OffsetDateTime now = OffsetDateTime.now();
    for (int i = 0; i <= 10_000; i++) {
      insertMetricForAssignee("Assignee " + i, now); // 10_001 different assignees
    }
    elasticsearchTestRule.refreshTasklistESIndices();

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
    elasticsearchTestRule.refreshTasklistESIndices();
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
    final MetricEntity entity = new MetricEntity();
    entity.setEvent(MetricReaderWriter.EVENT_TASK_COMPLETED_BY_ASSIGNEE);
    entity.setEventTime(eventTime);
    entity.setValue(assignee);
    dao.insert(entity);
  }

  /** This class is temporary and it is here exclusively for the initial setup */
  private static class TemporaryMetricResponseDTO {
    private Long startTime;
    private Long endTime;

    public Long getStartTime() {
      return startTime;
    }

    public void setStartTime(Long startTime) {
      this.startTime = startTime;
    }

    public Long getEndTime() {
      return endTime;
    }

    public void setEndTime(Long endTime) {
      this.endTime = endTime;
    }
  }
}
