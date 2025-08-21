/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.metric;

import static io.camunda.tasklist.metric.MetricIT.BPMN_PROCESS_ID;
import static io.camunda.tasklist.metric.MetricIT.ELEMENT_ID;
import static io.camunda.tasklist.property.ElasticsearchProperties.DATE_FORMAT_DEFAULT;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.tasklist.store.TaskMetricsStore;
import io.camunda.tasklist.util.TasklistZeebeIntegrationTest;
import io.camunda.tasklist.webapp.management.dto.UsageMetricDTO;
import io.camunda.webapps.schema.entities.usertask.TaskEntity;
import io.camunda.zeebe.util.HashUtil;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

public class UsageMetricIT extends TasklistZeebeIntegrationTest {

  public static final String ASSIGNEE_ENDPOINT =
      "/actuator/usage-metrics/assignees?startTime={startTime}&endTime={endTime}";
  public static final DateTimeFormatter FORMATTER =
      DateTimeFormatter.ofPattern(DATE_FORMAT_DEFAULT);
  public static final long ASSIGNEE_HASH_1 = HashUtil.getStringHashValue("Angela Merkel");
  public static final long ASSIGNEE_HASH_2 = HashUtil.getStringHashValue("John Lennon");

  @Autowired private TestRestTemplate testRestTemplate;
  @Autowired private TaskMetricsStore taskMetricsStore;
  @LocalManagementPort private int managementPort;
  private final CamundaAuthentication joe = buildAllAccessUserWith("joe");
  private final CamundaAuthentication jane = buildAllAccessUserWith("jane");
  private final CamundaAuthentication demo = buildAllAccessUserWith("demo");

  private static CamundaAuthentication buildAllAccessUserWith(final String username) {
    return CamundaAuthentication.of(b -> b.user(username));
  }

  @Override
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
        testRestTemplate.getForEntity(
            "http://localhost:" + managementPort + ASSIGNEE_ENDPOINT,
            UsageMetricDTO.class,
            parameters);

    assertThat(response.getStatusCodeValue()).isEqualTo(200);
    assertThat(response.getBody().getAssignees()).isEqualTo(Set.of());
    assertThat(response.getBody().getTotal()).isEqualTo(0);
  }

  @Test
  public void shouldReturnExpectedDataForCorrectTimeRange() {
    final OffsetDateTime now = OffsetDateTime.now();
    insertMetricForAssignee(12L, "John Lennon", now);
    insertMetricForAssignee(23L, "Angela Merkel", now);
    insertMetricForAssignee(45L, "Angela Merkel", now);
    insertMetricForAssignee(56L, "Angela Merkel", now);
    insertMetricForAssignee(78L, "Angela Merkel", now);

    databaseTestExtension.refreshTasklistIndices();

    final Map<String, String> parameters = new HashMap<>();
    parameters.put("startTime", now.minusSeconds(1L).format(FORMATTER));
    parameters.put("endTime", now.plusSeconds(1L).format(FORMATTER));

    final UsageMetricDTO expectedDto =
        new UsageMetricDTO(Set.of(ASSIGNEE_HASH_1, ASSIGNEE_HASH_2)); // just repeat once

    Awaitility.await()
        .untilAsserted(
            () -> {
              final ResponseEntity<UsageMetricDTO> response =
                  testRestTemplate.getForEntity(
                      "http://localhost:" + managementPort + ASSIGNEE_ENDPOINT,
                      UsageMetricDTO.class,
                      parameters);
              assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
              assertThat(response.getBody()).isEqualTo(expectedDto);
            });
  }

  @Test
  public void shouldReturnEmptyDataIfWrongTimeRange() throws IOException, InterruptedException {
    final OffsetDateTime now = OffsetDateTime.now();
    insertMetricForAssignee(12L, "John Lennon", now);
    insertMetricForAssignee(23L, "Angela Merkel", now);
    insertMetricForAssignee(45L, "Angela Merkel", now);
    insertMetricForAssignee(56L, "Angela Merkel", now);
    insertMetricForAssignee(78L, "Angela Merkel", now);

    databaseTestExtension.refreshTasklistIndices();

    final Map<String, String> parameters = new HashMap<>();
    parameters.put("startTime", now.plusMinutes(5L).format(FORMATTER)); // out of range
    parameters.put("endTime", now.plusMinutes(15L).format(FORMATTER));
    final ResponseEntity<UsageMetricDTO> response =
        testRestTemplate.getForEntity(
            "http://localhost:" + managementPort + ASSIGNEE_ENDPOINT,
            UsageMetricDTO.class,
            parameters);

    final UsageMetricDTO expectedDto = new UsageMetricDTO(Set.of());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isEqualTo(expectedDto);
  }

  @Test
  @Disabled(
      "Ignoring this test for now as it is quite slow - we can remove this mark to verify at any time")
  public void shouldReturnOverTenThousandObjects() {
    final OffsetDateTime now = OffsetDateTime.now();
    for (int i = 0; i <= 10_000; i++) {
      insertMetricForAssignee(123L, "Assignee " + i, now); // 10_001 different assignees
    }
    databaseTestExtension.refreshTasklistIndices();

    final Map<String, String> parameters = new HashMap<>();
    parameters.put("startTime", now.minusSeconds(1L).format(FORMATTER));
    parameters.put("endTime", now.plusSeconds(1L).format(FORMATTER));
    final ResponseEntity<UsageMetricDTO> response =
        testRestTemplate.getForEntity(
            "http://localhost:" + managementPort + ASSIGNEE_ENDPOINT,
            UsageMetricDTO.class,
            parameters);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().getTotal()).isEqualTo(10_001);
  }

  @Test
  public void providesCompletedTasks() throws IOException, InterruptedException {
    final OffsetDateTime now = OffsetDateTime.now();

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
    databaseTestExtension.refreshTasklistIndices();
    // when
    final Map<String, String> parameters = new HashMap<>();
    parameters.put("startTime", now.minusMinutes(5L).format(FORMATTER));
    parameters.put("endTime", now.plusMinutes(15L).format(FORMATTER));
    final ResponseEntity<UsageMetricDTO> response =
        testRestTemplate.getForEntity(
            "http://localhost:" + managementPort + ASSIGNEE_ENDPOINT,
            UsageMetricDTO.class,
            parameters);

    // then
    final UsageMetricDTO expectedDto =
        new UsageMetricDTO(
            Set.of(
                HashUtil.getStringHashValue("jane"),
                HashUtil.getStringHashValue("demo"),
                HashUtil.getStringHashValue("joe")));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isEqualTo(expectedDto);
  }

  private void insertMetricForAssignee(
      final long key, final String assignee, final OffsetDateTime eventTime) {
    final var task = new TaskEntity().setKey(key).setAssignee(assignee).setCreationTime(eventTime);
    taskMetricsStore.registerTaskAssigned(task);
  }
}
