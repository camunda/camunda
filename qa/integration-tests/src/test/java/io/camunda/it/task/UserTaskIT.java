/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.exporter;

import static io.camunda.client.api.search.response.UserTaskState.COMPLETED;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.filter.UserTaskFilter;
import io.camunda.client.api.search.response.UserTask;
import io.camunda.client.protocol.rest.UserTaskVariableFilterRequest;
import io.camunda.it.utils.MultiDbTest;
import io.camunda.zeebe.model.bpmn.builder.AbstractUserTaskBuilder;
import io.camunda.zeebe.model.bpmn.builder.UserTaskBuilder;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

@MultiDbTest
public class UserTaskExporterIT {

  private static CamundaClient client;

  @Test
  void shouldExportUserTask() {
    // given
    final var dateTime = OffsetDateTime.now();

    // when
    final var processDefinitionId =
        ExporterTestUtil.createAndDeployUserTaskProcess(
            client,
            "test-process-id",
            "zeebe-task",
            t ->
                t.zeebeUserTask()
                    .zeebeAssignee("demo")
                    .zeebeFollowUpDate(dateTime.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
                    .zeebeDueDate(dateTime.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
                    .zeebeCandidateUsers("demoUsers")
                    .zeebeCandidateGroups("demoGroup"));

    final var processInstanceId = ExporterTestUtil.startProcessInstance(client, "test-process-id");

    ExporterTestUtil.waitForProcessTasks(client, processInstanceId);

    final var userTasks = fetchUserTasks(client, processInstanceId);
    // then
    assertThat(userTasks).hasSize(1);
    assertThat(userTasks.getFirst().getPriority()).isEqualTo(50);
    assertThat(userTasks.getFirst().getUserTaskKey()).isGreaterThan(0);
    assertThat(userTasks.getFirst().getProcessInstanceKey()).isEqualTo(processInstanceId);
    assertThat(userTasks.getFirst().getProcessDefinitionKey())
        .isEqualTo(Long.valueOf(processDefinitionId));
    assertThat(userTasks.getFirst().getCreationDate()).isNotNull();
    assertThat(userTasks.getFirst().getDueDate()).isNotNull();
    assertThat(userTasks.getFirst().getFollowUpDate()).isNotNull();
    assertThat(userTasks.getFirst().getFormKey()).isNull();
    assertThat(userTasks.getFirst().getCandidateUsers()).containsExactly("demoUsers");
    assertThat(userTasks.getFirst().getCandidateGroups()).containsExactly("demoGroup");
    assertThat(userTasks.getFirst().getAssignee()).isEqualTo("demo");
    assertThat(userTasks.getFirst().getBpmnProcessId()).isEqualTo("test-process-id");
    assertThat(userTasks.getFirst().getElementId()).isEqualTo("zeebe-task");
    assertThat(userTasks.getFirst().getExternalFormReference()).isNull();
    assertThat(userTasks.getFirst().getElementInstanceKey()).isGreaterThan(0);
  }

  @Test
  void shouldCompleteUserTask() {
    // given

    // when
    final var processInstanceId = startZeebeUserTaskProcess(client, null);

    var userTasks = fetchUserTasks(client, processInstanceId);

    client.newUserTaskCompleteCommand(userTasks.getFirst().getUserTaskKey()).send().join();
    // then
    waitForTask(
        client,
        f -> {
          f.processInstanceKey(processInstanceId);
          f.state(COMPLETED);
        });

    userTasks = fetchUserTasks(client, processInstanceId);
    assertThat(userTasks).hasSize(1);
    assertThat(userTasks.getFirst().getState()).isEqualTo(COMPLETED);
    assertThat(userTasks.getFirst().getCompletionDate()).isNotNull();
  }

  @Test
  void shouldUpdateUserTask() {
    // given
    final var dateTime = OffsetDateTime.now();

    // when
    final var processInstanceId = startZeebeUserTaskProcess(client, null);

    var userTasks = fetchUserTasks(client, processInstanceId);

    client
        .newUserTaskUpdateCommand(userTasks.getFirst().getUserTaskKey())
        .priority(99)
        .candidateUsers("demoUsers")
        .candidateGroups("demoGroup")
        .dueDate(dateTime.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
        .followUpDate(dateTime.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
        .send()
        .join();

    // then
    waitForTask(
        client,
        f -> {
          f.processInstanceKey(processInstanceId);
          f.candidateUser("demoUsers");
        });

    userTasks = fetchUserTasks(client, processInstanceId);
    assertThat(userTasks).hasSize(1);
    assertThat(userTasks.getFirst().getPriority()).isEqualTo(99);
    assertThat(userTasks.getFirst().getCandidateUsers()).containsExactly("demoUsers");
    assertThat(userTasks.getFirst().getCandidateGroups()).containsExactly("demoGroup");
    assertThat(userTasks.getFirst().getDueDate()).isNotNull();
    assertThat(userTasks.getFirst().getFollowUpDate()).isNotNull();
    assertThat(userTasks.getFirst().getAssignee()).isNull();
  }

  @Test
  void shouldAssignUserTask() {
    // given

    // when
    final var processInstanceId = startZeebeUserTaskProcess(client, null);

    var userTasks = fetchUserTasks(client, processInstanceId);

    client
        .newUserTaskAssignCommand(userTasks.getFirst().getUserTaskKey())
        .assignee("demo")
        .send()
        .join();

    // then
    waitForTask(
        client,
        f -> {
          f.processInstanceKey(processInstanceId);
          f.assignee("demo");
        });

    userTasks = fetchUserTasks(client, processInstanceId);
    assertThat(userTasks).hasSize(1);
    assertThat(userTasks.getFirst().getAssignee()).isEqualTo("demo");
  }

  @Test
  void shouldUnassignUserTask() {
    // given

    // when
    final var processInstanceId = startZeebeUserTaskProcess(client, t -> t.zeebeAssignee("demo"));

    final var userTasks = fetchUserTasks(client, processInstanceId);

    client.newUserTaskUnassignCommand(userTasks.getFirst().getUserTaskKey()).send().join();

    // then
    Awaitility.await()
        .ignoreExceptions()
        .timeout(Duration.ofSeconds(30))
        .until(
            () -> {
              final var tasks =
                  client
                      .newUserTaskQuery()
                      .filter(f -> f.processInstanceKey(processInstanceId))
                      .send()
                      .join()
                      .items();
              return tasks.getFirst().getAssignee() == null;
            });

    final var unassignedTasks = fetchUserTasks(client, processInstanceId);
    assertThat(unassignedTasks).hasSize(1);
    assertThat(unassignedTasks.getFirst().getAssignee()).isNull();
  }

  @Test
  void shouldExportUserTaskVariable() {
    // when
    ExporterTestUtil.createAndDeployUserTaskProcess(
        client, "test-process-id", "zeebe-task", AbstractUserTaskBuilder::zeebeUserTask);

    final var processInstanceId =
        ExporterTestUtil.startProcessInstance(
            client,
            "test-process-id",
            Map.of(
                "stringVariable",
                "value",
                "intVariable",
                13,
                "boolVariable",
                true,
                "bigVariable",
                "a".repeat(8192)));

    ExporterTestUtil.waitForProcessTasks(client, processInstanceId);

    // then
    var tasks =
        client
            .newUserTaskQuery()
            .filter(
                f ->
                    f.processInstanceVariables(
                        List.of(new UserTaskVariableFilterRequest().name("stringVariable"))))
            .send()
            .join()
            .items();

    assertThat(tasks).hasSize(1);

    tasks =
        client
            .newUserTaskQuery()
            .filter(
                f ->
                    f.processInstanceVariables(
                        List.of(new UserTaskVariableFilterRequest().name("intVariable"))))
            .send()
            .join()
            .items();

    assertThat(tasks).hasSize(1);

    tasks =
        client
            .newUserTaskQuery()
            .filter(
                f ->
                    f.processInstanceVariables(
                        List.of(new UserTaskVariableFilterRequest().name("boolVariable"))))
            .send()
            .join()
            .items();

    assertThat(tasks).hasSize(1);

    tasks =
        client
            .newUserTaskQuery()
            .filter(
                f ->
                    f.processInstanceVariables(
                        List.of(new UserTaskVariableFilterRequest().name("bigVariable"))))
            .send()
            .join()
            .items();

    assertThat(tasks).hasSize(1);

    tasks =
        client
            .newUserTaskQuery()
            .filter(
                f ->
                    f.processInstanceVariables(
                        List.of(
                            new UserTaskVariableFilterRequest()
                                .name("stringVariable")
                                .value("wrong-value"))))
            .send()
            .join()
            .items();

    assertThat(tasks).hasSize(0);
  }

  @Test
  void shouldExportUserTaskWithExternalFormReference() {
    // given

    // when
    ExporterTestUtil.createAndDeployUserTaskProcess(
        client,
        "test-process-id",
        "zeebe-task",
        t -> t.zeebeUserTask().zeebeExternalFormReference("test-form-reference"));

    final var processInstanceId = ExporterTestUtil.startProcessInstance(client, "test-process-id");

    ExporterTestUtil.waitForProcessTasks(client, processInstanceId);

    final var userTasks = fetchUserTasks(client, processInstanceId);

    // then
    assertThat(userTasks).hasSize(1);
    assertThat(userTasks.getFirst().getExternalFormReference()).isEqualTo("test-form-reference");
  }

  @Test
  void shouldExportUserTaskWithFormKeyReference() {
    // given
    final var form =
        client
            .newDeployResourceCommand()
            .addResourceFromClasspath("form/form.form")
            .send()
            .join()
            .getForm()
            .getFirst();

    // when
    ExporterTestUtil.createAndDeployUserTaskProcess(
        client,
        "test-process-id",
        "zeebe-task",
        t -> t.zeebeUserTask().zeebeFormId(form.getFormId()));

    final var processInstanceId = ExporterTestUtil.startProcessInstance(client, "test-process-id");

    ExporterTestUtil.waitForProcessTasks(client, processInstanceId);

    final var userTasks = fetchUserTasks(client, processInstanceId);

    // then
    assertThat(userTasks).hasSize(1);
    assertThat(userTasks.getFirst().getExternalFormReference()).isNull();
    assertThat(userTasks.getFirst().getFormKey()).isEqualTo(form.getFormKey());
  }

  public static List<UserTask> fetchUserTasks(
      final CamundaClient client, final long processInstanceId) {
    return client
        .newUserTaskQuery()
        .filter(f -> f.processInstanceKey(processInstanceId))
        .send()
        .join()
        .items();
  }

  public static Long startZeebeUserTaskProcess(
      final CamundaClient client, final Consumer<UserTaskBuilder> taskParams) {
    if (taskParams != null) {
      ExporterTestUtil.createAndDeployUserTaskProcess(
          client,
          "test-process-id",
          "zeebe-task",
          AbstractUserTaskBuilder::zeebeUserTask,
          taskParams);
    } else {
      ExporterTestUtil.createAndDeployUserTaskProcess(
          client, "test-process-id", "zeebe-task", AbstractUserTaskBuilder::zeebeUserTask);
    }
    final var processInstanceKey = ExporterTestUtil.startProcessInstance(client, "test-process-id");

    ExporterTestUtil.waitForProcessTasks(client, processInstanceKey);

    return processInstanceKey;
  }

  public static void waitForTask(
      final CamundaClient client, final Consumer<UserTaskFilter> filterConsumer) {
    Awaitility.await()
        .ignoreExceptions()
        .timeout(Duration.ofSeconds(30))
        .until(
            () ->
                !client.newUserTaskQuery().filter(filterConsumer).send().join().items().isEmpty());
  }
}
