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
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
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
        camundaClient.newUserTaskQuery().filter(f -> f.assignee("demo")).send().join();
    assertThat(result.items().size()).isEqualTo(1);
    assertThat(result.items().getFirst().getAssignee()).isEqualTo("demo");
    assertThat(result.items().getFirst().getKey()).isEqualTo(userTaskKeyTaskAssigned);
  }

  @Test
  public void shouldRetrieveTaskByState() {
    final var resultCreated =
        camundaClient.newUserTaskQuery().filter(f -> f.state("CREATED")).send().join();
    assertThat(resultCreated.items().size()).isEqualTo(2);
    resultCreated.items().forEach(item -> assertThat(item.getState()).isEqualTo("CREATED"));

    final var resultCompleted =
        camundaClient.newUserTaskQuery().filter(f -> f.state("COMPLETED")).send().join();
    assertThat(resultCompleted.items().size()).isEqualTo(1);
    resultCompleted.items().forEach(item -> assertThat(item.getState()).isEqualTo("COMPLETED"));
  }

  @Test
  public void shouldRetrieveTaskByTaskDefinitionId() {
    final var result =
        camundaClient.newUserTaskQuery().filter(f -> f.elementId("test-2")).send().join();
    assertThat(result.items().size()).isEqualTo(1);
    result.items().forEach(item -> assertThat(item.getElementId()).isEqualTo("test-2"));
  }

  @Test
  public void shouldRetrieveTaskByBpmProcessDefinitionId() {
    final var result =
        camundaClient.newUserTaskQuery().filter(f -> f.bpmProcessId("process")).send().join();
    assertThat(result.items().size()).isEqualTo(2);
    result.items().forEach(item -> assertThat(item.getBpmnProcessId()).isEqualTo("process"));
  }

  @Test
  public void shouldRetrieveTaskByCandidateGroup() {
    final var expectedGroup = List.of("group");
    final var result =
        camundaClient.newUserTaskQuery().filter(f -> f.candidateGroup("group")).send().join();
    assertThat(result.items().size()).isEqualTo(1);

    result.items().forEach(item -> assertThat(item.getCandidateGroup()).isEqualTo(expectedGroup));
  }

  @Test
  public void shouldRetrieveTaskByCandidateUser() {
    final var expectedUser = List.of("user");
    final var result =
        camundaClient.newUserTaskQuery().filter(f -> f.candidateUser("user")).send().join();
    assertThat(result.items().size()).isEqualTo(1);

    result.items().forEach(item -> assertThat(item.getCandidateUser()).isEqualTo(expectedUser));
  }

  @Test
  public void shouldRetrieveTaskByCompletionDate() {
    final DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
    final ZoneId utcZoneId = ZoneId.of("UTC");

    final LocalDateTime now = LocalDateTime.now(utcZoneId);

    final LocalDateTime dayBefore = now.minusDays(1);
    final LocalDateTime dayAfter = now.plusDays(1);

    final DateFilter dateFilter = new DateFilter().from(dayBefore.format(dateFormat)).to(dayAfter.format(dateFormat));

    final var result =
        camundaClient.newUserTaskQuery().filter(f -> f.completionDate(dateFilter)).send().join();

    assertThat(result.items().size()).isEqualTo(1);
    assertThat(result.items().getFirst().getState()).isEqualTo("COMPLETED");

    final LocalDateTime outOfRangeDayBefore = now.minusDays(2);
    final LocalDateTime outOfRangeDayAfter = now.minusDays(1);

    final DateFilter dateFilterOutOfRange = new DateFilter().from(outOfRangeDayBefore.format(dateFormat)).to(outOfRangeDayAfter.format(dateFormat));
    final var resultOutOfRange =
        camundaClient
            .newUserTaskQuery()
            .filter(f -> f.completionDate(dateFilterOutOfRange))
            .send()
            .join();
    assertThat(resultOutOfRange.items().size()).isEqualTo(0);
  }

  @Test
  public void shouldRetrieveTaskByCreationDate() {
    final DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
    final ZoneId utcZoneId = ZoneId.of("UTC");

    final LocalDateTime now = LocalDateTime.now(utcZoneId);

    final LocalDateTime dayBefore = now.minusDays(1);
    final LocalDateTime dayAfter = now.plusDays(1);

    final DateFilter dateFilter = new DateFilter().from(dayBefore.format(dateFormat)).to(dayAfter.format(dateFormat));

    final var result =
        camundaClient.newUserTaskQuery().filter(f -> f.creationDate(dateFilter)).send().join();

    assertThat(result.items().size()).isEqualTo(3);

    final LocalDateTime outOfRangeDayBefore = now.minusDays(2);
    final LocalDateTime outOfRangeDayAfter = now.minusDays(1);

    final DateFilter dateFilterOutOfRange = new DateFilter().from(outOfRangeDayBefore.format(dateFormat)).to(outOfRangeDayAfter.format(dateFormat));
    final var resultOutOfRange =
        camundaClient
            .newUserTaskQuery()
            .filter(f -> f.creationDate(dateFilterOutOfRange))
            .send()
            .join();
    assertThat(resultOutOfRange.items().size()).isEqualTo(0);
  }

  @Test
  public void shouldRetrieveTaskByDueDate() {
    final DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
    final ZoneId utcZoneId = ZoneId.of("UTC");

    final LocalDateTime now = LocalDateTime.now(utcZoneId);

    final LocalDateTime dayBefore = now.minusDays(4);
    final LocalDateTime dayAfter = now.plusDays(3);

    final DateFilter dateFilter = new DateFilter().from(dayBefore.format(dateFormat)).to(dayAfter.format(dateFormat));

    final var result =
        camundaClient.newUserTaskQuery().filter(f -> f.dueDate(dateFilter)).send().join();

    assertThat(result.items().size()).isEqualTo(3);

    final LocalDateTime outOfRangeDayBefore = now.minusDays(7);
    final LocalDateTime outOfRangeDayAfter = now.minusDays(4);

    final DateFilter dateFilterOutOfRange = new DateFilter().from(outOfRangeDayBefore.format(dateFormat)).to(outOfRangeDayAfter.format(dateFormat));
    final var resultOutOfRange =
        camundaClient.newUserTaskQuery().filter(f -> f.dueDate(dateFilterOutOfRange)).send().join();
    assertThat(resultOutOfRange.items().size()).isEqualTo(0);
  }

  @Test
  public void shouldRetrieveTaskByFollowUpDate() {
    final DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
    final ZoneId utcZoneId = ZoneId.of("UTC");

    final LocalDateTime now = LocalDateTime.now(utcZoneId);

    final LocalDateTime dayBefore = now.minusDays(4);
    final LocalDateTime dayAfter = now.plusDays(3);

    final DateFilter dateFilter = new DateFilter().from(dayBefore.format(dateFormat)).to(dayAfter.format(dateFormat));

    final var result =
        camundaClient.newUserTaskQuery().filter(f -> f.followUpDate(dateFilter)).send().join();

    assertThat(result.items().size()).isEqualTo(3);

    final LocalDateTime outOfRangeDayBefore = now.minusDays(7);
    final LocalDateTime outOfRangeDayAfter = now.minusDays(4);

    final DateFilter dateFilterOutOfRange = new DateFilter().from(outOfRangeDayBefore.format(dateFormat)).to(outOfRangeDayAfter.format(dateFormat));
    final var resultOutOfRange =
        camundaClient
            .newUserTaskQuery()
            .filter(f -> f.followUpDate(dateFilterOutOfRange))
            .send()
            .join();
    assertThat(resultOutOfRange.items().size()).isEqualTo(0);
  }

  @Test
  public void shouldValidatePagination() {
    final var result = camundaClient.newUserTaskQuery().page(p -> p.limit(1)).send().join();
    assertThat(result.items().size()).isEqualTo(1);
    final var key = result.items().getFirst().getKey();
    // apply searchAfter
    final var resultAfter =
        camundaClient
            .newUserTaskQuery()
            .page(p -> p.searchAfter(Collections.singletonList(key)))
            .send()
            .join();

    assertThat(resultAfter.items().size()).isEqualTo(2);
    final var keyAfter = resultAfter.items().getFirst().getKey();
    // apply searchBefore
    final var resultBefore =
        camundaClient
            .newUserTaskQuery()
            .page(p -> p.searchBefore(Collections.singletonList(keyAfter)))
            .send()
            .join();
    assertThat(result.items().size()).isEqualTo(1);
    assertThat(resultBefore.items().getFirst().getKey()).isEqualTo(key);
  }

  @Test
  public void shouldSortTasksByCreationDateASC() {
    final var result =
        camundaClient.newUserTaskQuery().sort(s -> s.creationDate().asc()).send().join();

    assertThat(result.items().size()).isEqualTo(3);

    // Assert that the creation date of item 0 is before item 1, and item 1 is before item 2
    assertThat(result.items().get(0).getCreationDate())
        .isLessThan(result.items().get(1).getCreationDate());
    assertThat(result.items().get(1).getCreationDate())
        .isLessThan(result.items().get(2).getCreationDate());
  }

  @Test
  public void shouldSortTasksByStartDateDESC() {
    final var result =
        camundaClient.newUserTaskQuery().sort(s -> s.creationDate().desc()).send().join();

    assertThat(result.items().size()).isEqualTo(3);

    assertThat(result.items().get(0).getCreationDate())
        .isGreaterThan(result.items().get(1).getCreationDate());
    assertThat(result.items().get(1).getCreationDate())
        .isGreaterThan(result.items().get(2).getCreationDate());
  }

  @Test
  public void shouldRetrieveTaskByTenantId() {
    final var resultDefaultTenant =
        camundaClient.newUserTaskQuery().filter(f -> f.tentantId("<default>")).send().join();
    assertThat(resultDefaultTenant.items().size()).isEqualTo(3);
    resultDefaultTenant
        .items()
        .forEach(item -> assertThat(item.getTenantIds()).isEqualTo("<default>"));

    final var resultNonExistent =
        camundaClient.newUserTaskQuery().filter(f -> f.tentantId("<default123>")).send().join();
    assertThat(resultNonExistent.items().size()).isEqualTo(0);
  }

  private static void deployProcess(
      final String processId,
      final String resourceName,
      final String userTaskName,
      final String candidateGroup,
      final String candidateUser) {
    final DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
    final ZoneId utcZoneId = ZoneId.of("UTC");

    final LocalDateTime now = LocalDateTime.now(utcZoneId);
    final LocalDateTime dayBefore = now.minusDays(1);

    camundaClient
        .newDeployResourceCommand()
        .addProcessModel(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .userTask(userTaskName)
                .zeebeUserTask()
                .zeebeDueDate(dayBefore.format(dateFormat))
                .zeebeFollowUpDate(dayBefore.format(dateFormat))
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
              userTaskKeyTaskAssigned = result.items().getFirst().getKey();
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
                  camundaClient.newUserTaskQuery().filter(f -> f.assignee("demo")).send().join();
              assertThat(result.items().size()).isEqualTo(1);
              final var resultComplete =
                  camundaClient.newUserTaskQuery().filter(f -> f.state("COMPLETED")).send().join();
              assertThat(resultComplete.items().size()).isEqualTo(1);
            });
  }
}
