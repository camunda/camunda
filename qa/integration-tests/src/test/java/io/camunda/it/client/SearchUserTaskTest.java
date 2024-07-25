/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.client;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.protocol.rest.DateFilter;
import io.camunda.qa.util.cluster.TestStandaloneCamunda;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@ZeebeIntegration
class SearchUserTaskTest {
  @TestZeebe static final TestStandaloneCamunda testStandaloneCamunda = new TestStandaloneCamunda();
  static final CamundaClient camundaClient = testStandaloneCamunda.newClientBuilder().build();
  private static Long userTaskKeyTaskAssigned;

  @BeforeAll
  public static void setup() {
    deployProcess("process", "simple.bpmn", "test", "", "");
    deployProcess("process-2", "simple-2.bpmn", "test-2", "group", "user");

    startProcessInstance("process");
    startProcessInstance("process-2");
    startProcessInstance("process");

    waitForTasksBeingExported();
  }

  @Test
  public void shouldRetrieveTaskByAssignee() {
    final var result =
        camundaClient.newUserTaskQuery().filter(f -> f.userTaskAssignee("demo")).send().join();
    assertThat(result.items().size()).isEqualTo(1);
    assertThat(result.items().getFirst().getAssignee()).isEqualTo("demo");
    assertThat(result.items().getFirst().getUserTaskKey()).isEqualTo(userTaskKeyTaskAssigned);
  }

  @Test
  public void shouldRetrieveTaskByState() {
    final var resultCreated =
        camundaClient.newUserTaskQuery().filter(f -> f.userTaskState("CREATED")).send().join();
    assertThat(resultCreated.items().size()).isEqualTo(2);
    resultCreated.items().forEach(item -> assertThat(item.getTaskState()).isEqualTo("CREATED"));

    final var resultCompleted =
        camundaClient.newUserTaskQuery().filter(f -> f.userTaskState("COMPLETED")).send().join();
    assertThat(resultCompleted.items().size()).isEqualTo(1);
    resultCompleted.items().forEach(item -> assertThat(item.getTaskState()).isEqualTo("COMPLETED"));
  }

  @Test
  public void shouldRetrieveTaskByTaskDefinitionId() {
    final var result =
        camundaClient.newUserTaskQuery().filter(f -> f.userTaskElementId("test-2")).send().join();
    assertThat(result.items().size()).isEqualTo(1);
    result.items().forEach(item -> assertThat(item.getElementId()).isEqualTo("test-2"));
  }

  @Test
  public void shouldRetrieveTaskByBpmProcessDefinitionId() {
    final var result =
        camundaClient
            .newUserTaskQuery()
            .filter(f -> f.userTaskBpmProcessId("process"))
            .send()
            .join();
    assertThat(result.items().size()).isEqualTo(2);
    result.items().forEach(item -> assertThat(item.getBpmnProcessId()).isEqualTo("process"));
  }

  @Test
  public void shouldRetrieveTaskByCandidateGroup() {
    final var expectedGroup = List.of("group");
    final var result =
        camundaClient
            .newUserTaskQuery()
            .filter(f -> f.userTaskCandidateGroup("group"))
            .send()
            .join();
    assertThat(result.items().size()).isEqualTo(1);

    result.items().forEach(item -> assertThat(item.getCandidateGroup()).isEqualTo(expectedGroup));
  }

  @Test
  public void shouldRetrieveTaskByCandidateUser() {
    final var expectedUser = List.of("user");
    final var result =
        camundaClient.newUserTaskQuery().filter(f -> f.userTaskCandidateUser("user")).send().join();
    assertThat(result.items().size()).isEqualTo(1);

    result.items().forEach(item -> assertThat(item.getCandidateUser()).isEqualTo(expectedUser));
  }

  // retrieve by completitionDate
  @Test
  public void shouldRetrieveTaskByCompletionDate() {
    final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
    dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));

    final Date now = new Date();

    final Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
    calendar.setTime(now);
    calendar.add(Calendar.DAY_OF_YEAR, -1);
    String dayBefore = dateFormat.format(calendar.getTime());

    calendar.setTime(now);

    calendar.add(Calendar.DAY_OF_YEAR, 1);
    String dayAfter = dateFormat.format(calendar.getTime());

    // Create a DateFilter with the formatted date strings for the range
    final DateFilter dateFilter = new DateFilter().from(dayBefore).to(dayAfter);

    final var result =
        camundaClient
            .newUserTaskQuery()
            .filter(f -> f.userTaskCompletionDate(dateFilter))
            .send()
            .join();

    assertThat(result.items().size()).isEqualTo(1);
    assertThat(result.items().getFirst().getTaskState()).isEqualTo("COMPLETED");

    // Check if the completion date is without the range
    calendar.add(Calendar.DAY_OF_YEAR, -1);
    dayBefore = dateFormat.format(calendar.getTime());

    // Reset to current time
    calendar.setTime(now);

    // Calculate the date one day after
    calendar.add(Calendar.DAY_OF_YEAR, 1);
    dayAfter = dateFormat.format(calendar.getTime());

    final DateFilter dateFilterOutOfRange = new DateFilter().from(dayBefore).to(dayAfter);
    final var resultOutOfRange =
        camundaClient
            .newUserTaskQuery()
            .filter(f -> f.userTaskCompletionDate(dateFilterOutOfRange))
            .send()
            .join();
    assertThat(resultOutOfRange.items().size()).isEqualTo(0);
  }

  private static void deployProcess(
      final String processId,
      final String resourceName,
      final String userTaskName,
      final String candidateGroup,
      final String candidateUser) {
    camundaClient
        .newDeployResourceCommand()
        .addProcessModel(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .userTask(userTaskName)
                .zeebeUserTask()
                .zeebeCandidateGroups(candidateGroup)
                .zeebeCandidateUsers(candidateUser)
                .endEvent()
                .done(),
            resourceName)
        .send()
        .join();
  }

  private static void startProcessInstance(final String processId) {
    camundaClient.newCreateInstanceCommand().bpmnProcessId(processId).latestVersion().send().join();
  }

  private static void waitForTasksBeingExported() {
    Awaitility.await("should receive data from ES")
        .atMost(Duration.ofMinutes(1))
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () -> {
              final var result = camundaClient.newUserTaskQuery().send().join();
              assertThat(result.items().size()).isEqualTo(3);
              userTaskKeyTaskAssigned = result.items().getFirst().getUserTaskKey();
            });

    camundaClient
        .newUserTaskAssignCommand(userTaskKeyTaskAssigned)
        .assignee("demo")
        .action("assignee")
        .send()
        .join();

    camundaClient.newUserTaskCompleteCommand(userTaskKeyTaskAssigned).send().join();

    Awaitility.await("should export Assigned task and Completed to ElasticSearch")
        .atMost(Duration.ofMinutes(1))
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () -> {
              final var result =
                  camundaClient
                      .newUserTaskQuery()
                      .filter(f -> f.userTaskAssignee("demo"))
                      .send()
                      .join();
              assertThat(result.items().size()).isEqualTo(1);
              final var resultComplete =
                  camundaClient
                      .newUserTaskQuery()
                      .filter(f -> f.userTaskState("COMPLETED"))
                      .send()
                      .join();
              assertThat(resultComplete.items().size()).isEqualTo(1);
            });
  }
}
