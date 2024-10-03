/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.camunda.qa.util.cluster.TestStandaloneCamunda;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.command.ProblemException;
import io.camunda.zeebe.client.protocol.rest.UserTaskVariableFilterRequest;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import java.io.InputStream;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@ZeebeIntegration
class UserTaskQueryTest {
  private static Long userTaskKeyTaskAssigned;

  @TestZeebe(initMethod = "initTestStandaloneCamunda")
  private static TestStandaloneCamunda testStandaloneCamunda;

  private static ZeebeClient camundaClient;

  @SuppressWarnings("unused")
  static void initTestStandaloneCamunda() {
    testStandaloneCamunda = new TestStandaloneCamunda();
  }

  @BeforeAll
  static void beforeAll() {
    camundaClient = testStandaloneCamunda.newClientBuilder().build();

    deployProcess("process", "simple.bpmn", "test", "", "");
    deployProcess("process-2", "simple-2.bpmn", "test-2", "group", "user");
    deployProcess("process-3", "simple-3.bpmn", "test-3", "", "", "30");
    delpoyProcessFromResourcePath("/process/bpm_variable_test.bpmn", "bpm_variable_test.bpmn");

    deployForm("form/form.form");
    delpoyProcessFromResourcePath("/process/process_with_form.bpmn", "process_with_form.bpmn");

    startProcessInstance("process");
    startProcessInstance("process-2");
    startProcessInstance("process");
    startProcessInstance("process-3");
    startProcessInstance("bpmProcessVariable");
    startProcessInstance("processWithForm");

    waitForTasksBeingExported();
  }

  @Test
  public void shouldRetrieveTaskByTaskVariable() {
    final UserTaskVariableFilterRequest variableValueFilter =
        new UserTaskVariableFilterRequest().name("task02").value("1");

    final var result =
        camundaClient
            .newUserTaskQuery()
            .filter(f -> f.variables(List.of(variableValueFilter)))
            .send()
            .join();
    assertThat(result.items().size()).isEqualTo(1);
  }

  @Test
  public void shouldRetrieveTaskByProcessVariable() {
    final UserTaskVariableFilterRequest variableValueFilter =
        new UserTaskVariableFilterRequest().name("process01").value("\"pVar\"");

    final var result =
        camundaClient
            .newUserTaskQuery()
            .filter(f -> f.variables(List.of(variableValueFilter)))
            .send()
            .join();
    assertThat(result.items().size()).isEqualTo(1);
  }

  @Test
  public void shouldRetrieveTaskByVariableNameSearch() {
    final UserTaskVariableFilterRequest variableValueFilter =
        new UserTaskVariableFilterRequest().name("process01");

    final var result =
        camundaClient
            .newUserTaskQuery()
            .filter(f -> f.variables(List.of(variableValueFilter)))
            .send()
            .join();
    assertThat(result.items().size()).isEqualTo(2);
  }

  @Test
  public void shouldNoteRetrieveTaskByInvalidVariableValue() {
    final UserTaskVariableFilterRequest variableValueFilter =
        new UserTaskVariableFilterRequest().name("process01").value("\"pVariable\"");

    final var result =
        camundaClient
            .newUserTaskQuery()
            .filter(f -> f.variables(List.of(variableValueFilter)))
            .send()
            .join();
    assertThat(result.items().size()).isEqualTo(0);
  }

  @Test
  public void shouldRetrieveTaskByOrVariableCondition() {
    final UserTaskVariableFilterRequest variableValueFilter1 =
        new UserTaskVariableFilterRequest().name("task02").value("1");

    final UserTaskVariableFilterRequest variableValueFilter2 =
        new UserTaskVariableFilterRequest().name("task01").value("\"test\"");

    final var result =
        camundaClient
            .newUserTaskQuery()
            .filter(f -> f.variables(List.of(variableValueFilter1, variableValueFilter2)))
            .send()
            .join();
    assertThat(result.items().size()).isEqualTo(2);
  }

  @Test
  public void shouldRetrieveTaskByAssignee() {
    final var result =
        camundaClient.newUserTaskQuery().filter(f -> f.assignee("demo")).send().join();
    assertThat(result.items().size()).isEqualTo(1);
    assertThat(result.items().getFirst().getAssignee()).isEqualTo("demo");
    assertThat(result.items().getFirst().getUserTaskKey()).isEqualTo(userTaskKeyTaskAssigned);
  }

  @Test
  public void shouldRetrieveTaskByState() {
    final var resultCreated =
        camundaClient.newUserTaskQuery().filter(f -> f.state("CREATED")).send().join();
    assertThat(resultCreated.items().size()).isEqualTo(6);
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
  public void shouldRetrieveTaskByBpmnDefinitionId() {
    final var result =
        camundaClient.newUserTaskQuery().filter(f -> f.bpmnProcessId("process")).send().join();
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
  public void shouldValidatePagination() {
    final var result = camundaClient.newUserTaskQuery().page(p -> p.limit(1)).send().join();
    assertThat(result.items().size()).isEqualTo(1);
    final var key = result.items().getFirst().getUserTaskKey();
    // apply searchAfter
    final var resultAfter =
        camundaClient
            .newUserTaskQuery()
            .page(p -> p.searchAfter(Collections.singletonList(key)))
            .send()
            .join();

    assertThat(resultAfter.items().size()).isEqualTo(6);
    final var keyAfter = resultAfter.items().getFirst().getUserTaskKey();
    // apply searchBefore
    final var resultBefore =
        camundaClient
            .newUserTaskQuery()
            .page(p -> p.searchBefore(Collections.singletonList(keyAfter)))
            .send()
            .join();
    assertThat(result.items().size()).isEqualTo(1);
    assertThat(resultBefore.items().getFirst().getUserTaskKey()).isEqualTo(key);
  }

  @Test
  public void shouldSortTasksByCreationDateASC() {
    final var result =
        camundaClient.newUserTaskQuery().sort(s -> s.creationDate().asc()).send().join();

    assertThat(result.items().size()).isEqualTo(7);

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

    assertThat(result.items().size()).isEqualTo(7);

    assertThat(result.items().get(0).getCreationDate())
        .isGreaterThanOrEqualTo(result.items().get(1).getCreationDate());
    assertThat(result.items().get(1).getCreationDate())
        .isGreaterThanOrEqualTo(result.items().get(2).getCreationDate());
    assertThat(result.items().get(2).getCreationDate())
        .isGreaterThanOrEqualTo(result.items().get(3).getCreationDate());
  }

  @Test
  public void shouldRetrieveTaskByTenantId() {
    final var resultDefaultTenant =
        camundaClient.newUserTaskQuery().filter(f -> f.tentantId("<default>")).send().join();
    assertThat(resultDefaultTenant.items().size()).isEqualTo(7);
    resultDefaultTenant
        .items()
        .forEach(item -> assertThat(item.getTenantIds()).isEqualTo("<default>"));

    final var resultNonExistent =
        camundaClient.newUserTaskQuery().filter(f -> f.tentantId("<default123>")).send().join();
    assertThat(resultNonExistent.items().size()).isEqualTo(0);
  }

  @Test
  public void retrievedTasksShouldIncludePriority() {
    final var resultDefaultPriority =
        camundaClient.newUserTaskQuery().filter(f -> f.bpmnProcessId("process-2")).send().join();
    assertThat(resultDefaultPriority.items().size()).isEqualTo(1);
    assertThat(resultDefaultPriority.items().getFirst().getPriority()).isEqualTo(50);

    final var resultDefinedPriority =
        camundaClient.newUserTaskQuery().filter(f -> f.bpmnProcessId("process-3")).send().join();
    assertThat(resultDefinedPriority.items().size()).isEqualTo(1);
    assertThat(resultDefinedPriority.items().getFirst().getPriority()).isEqualTo(30);
  }

  @Test
  void shouldGetUserTaskByKey() {
    // when
    final var result = camundaClient.newUserTaskGetRequest(userTaskKeyTaskAssigned).send().join();

    // then
    assertThat(result.getUserTaskKey()).isEqualTo(userTaskKeyTaskAssigned);
  }

  @Test
  void shouldReturn404ForNotFoundUserTaskKey() {
    // when
    final long userTaskKey = new Random().nextLong();
    final var problemException =
        assertThrows(
            ProblemException.class,
            () -> camundaClient.newUserTaskGetRequest(userTaskKey).send().join());
    // then
    assertThat(problemException.code()).isEqualTo(404);
    assertThat(problemException.details().getDetail())
        .isEqualTo("User Task with key %d not found".formatted(userTaskKey));
  }

  @Test
  void shouldReturnFormByUserTaskKey() {
    // when
    final var userTaskList = camundaClient.newUserTaskQuery().send().join();

    // filter userTask form the list when form is not null
    final var userTaskKeyWithForm =
        userTaskList.items().stream().filter(item -> item.getFormKey() != null).findFirst().get();

    final var result =
        camundaClient.newUserTaskGetFormRequest(userTaskKeyWithForm.getUserTaskKey()).send().join();

    // assert that the form key is the same as the form key of the user task
    assertThat(result.getFormKey()).isEqualTo(userTaskKeyWithForm.getFormKey());
  }

  @Test
  void shouldReturnNotFoundByUserTaskKeyWithNoForm() {
    // when
    final var userTaskList = camundaClient.newUserTaskQuery().send().join();

    // filter userTask form the list when form is not null
    final var userTaskKeyWithNoForm =
        userTaskList.items().stream().filter(item -> item.getFormKey() == null).findFirst().get();

    final var result =
        camundaClient
            .newUserTaskGetFormRequest(userTaskKeyWithNoForm.getUserTaskKey())
            .send()
            .join();
    // then
    assertThat(result).isNull();
  }

  private static void deployProcess(
      final String processId,
      final String resourceName,
      final String userTaskName,
      final String candidateGroup,
      final String candidateUser) {
    final DateTimeFormatter dateFormat =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
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

  private static void deployProcess(
      final String processId,
      final String resourceName,
      final String userTaskName,
      final String candidateGroup,
      final String candidateUser,
      final String priority) {
    final DateTimeFormatter dateFormat =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
    final ZoneId utcZoneId = ZoneId.of("UTC");

    final LocalDateTime now = LocalDateTime.now(utcZoneId);
    final LocalDateTime dayBefore = now.minusDays(1);

    camundaClient
        .newDeployResourceCommand()
        .addProcessModel(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .userTask(userTaskName)
                .zeebeTaskPriority(priority)
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

  private static void delpoyProcessFromResourcePath(
      final String resource, final String resourceName) {
    final InputStream process = UserTaskQueryTest.class.getResourceAsStream(resource);

    camundaClient
        .newDeployResourceCommand()
        .addProcessModel(Bpmn.readModelFromStream(process), resourceName)
        .send()
        .join();
  }

  private static void deployForm(final String resource) {
    camundaClient.newDeployResourceCommand().addResourceFromClasspath(resource).send().join();
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
              assertThat(result.items().size()).isEqualTo(7);
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
                  camundaClient.newUserTaskQuery().filter(f -> f.assignee("demo")).send().join();
              assertThat(result.items().size()).isEqualTo(1);
              final var resultComplete =
                  camundaClient.newUserTaskQuery().filter(f -> f.state("COMPLETED")).send().join();
              assertThat(resultComplete.items().size()).isEqualTo(1);
            });
  }
}
