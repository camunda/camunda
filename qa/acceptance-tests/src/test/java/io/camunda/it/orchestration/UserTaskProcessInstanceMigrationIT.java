/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.orchestration;

import static io.camunda.it.util.TestHelper.startProcessInstance;
import static io.camunda.qa.util.multidb.CamundaMultiDBExtension.TIMEOUT_DATA_AVAILABILITY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.MigrationPlan;
import io.camunda.client.api.search.response.UserTask;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.builder.AbstractUserTaskBuilder;
import io.camunda.zeebe.model.bpmn.builder.UserTaskBuilder;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@MultiDbTest
@DisabledIfSystemProperty(
    named = "test.integration.camunda.database.type",
    matches = "rdbms.*$",
    disabledReason = "Job-based user tasks don't work with RDBMS")
public class UserTaskProcessInstanceMigrationIT {

  private static CamundaClient client;

  private static final String FROM_PROCESS_ID = "migration-user-task_v1";
  private static final String TO_PROCESS_ID = "migration-user-task_v2";
  private static final String TASK_ID = "task1";
  private static final String EXAMPLE_DUE_DATE = "2017-07-21T19:32:28+02:00";
  private static final String EXAMPLE_FOLLOW_UP_DATE = "2017-07-22T19:32:28+02:00";

  @ParameterizedTest
  @MethodSource("migrationTestCases")
  void shouldMigrateProcessInstance(
      final Consumer<UserTaskBuilder> jobWorkerUserTask,
      final Consumer<UserTaskBuilder> camundaUserTask,
      final ExpectedUserTask expectedTask) {

    // given
    final BpmnModelInstance jwProcess =
        Bpmn.createExecutableProcess(FROM_PROCESS_ID)
            .startEvent()
            .userTask(TASK_ID, jobWorkerUserTask)
            .endEvent()
            .done();
    final BpmnModelInstance cutProcess =
        Bpmn.createExecutableProcess(TO_PROCESS_ID)
            .startEvent()
            .userTask(TASK_ID, camundaUserTask)
            .endEvent()
            .done();

    final var deploymentEvent =
        client
            .newDeployResourceCommand()
            .addProcessModel(jwProcess, FROM_PROCESS_ID + ".bpmn")
            .addProcessModel(cutProcess, TO_PROCESS_ID + ".bpmn")
            .send()
            .join();
    final var pd2 =
        deploymentEvent.getProcesses().stream()
            .filter(p -> p.getBpmnProcessId().equals(TO_PROCESS_ID))
            .findFirst()
            .get()
            .getProcessDefinitionKey();
    final var processInstanceKey =
        startProcessInstance(
                client,
                FROM_PROCESS_ID,
                Map.of("varAssignee", "demo", "varPriority", 20)) // TODO variables?
            .getProcessInstanceKey();

    // when
    migrateProcessInstance(
        client,
        processInstanceKey,
        MigrationPlan.newBuilder()
            .withTargetProcessDefinitionKey(pd2)
            .addMappingInstruction(TASK_ID, TASK_ID)
            .build());

    // then
    await()
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .untilAsserted(
            () -> {
              assertThat(
                      client
                          .newUserTaskSearchRequest()
                          .filter(f -> f.processDefinitionKey(pd2).bpmnProcessId(TO_PROCESS_ID))
                          .send()
                          .join()
                          .items())
                  .hasSize(1);
            });
    final UserTask migratedTask =
        client
            .newUserTaskSearchRequest()
            .filter(f -> f.processDefinitionKey(pd2))
            .send()
            .join()
            .singleItem();
    assertThat(migratedTask.getAssignee()).isEqualTo(expectedTask.assignee);
    assertThat(migratedTask.getCandidateGroups()).isEqualTo(List.of("g1", "g2"));
    assertThat(migratedTask.getCandidateUsers()).isEqualTo(List.of("u1", "u2"));
    assertThat(migratedTask.getDueDate()).isEqualTo(EXAMPLE_DUE_DATE);
    assertThat(migratedTask.getFollowUpDate()).isEqualTo(EXAMPLE_FOLLOW_UP_DATE);
    assertThat(migratedTask.getPriority()).isEqualTo(expectedTask.priority);
    assertThat(migratedTask.getFormKey()).isEqualTo(expectedTask.formKey);
    assertThat(migratedTask.getExternalFormReference())
        .isEqualTo(expectedTask.externalFormReference);
  }

  static Stream<Arguments> migrationTestCases() {
    // Test case with assignee and priority expressions
    final Consumer<UserTaskBuilder> from1 =
        t ->
            t.zeebeCandidateGroups("g1,g2")
                .zeebeCandidateUsers("u1,u2")
                .zeebeDueDate(EXAMPLE_DUE_DATE)
                .zeebeFollowUpDate(EXAMPLE_FOLLOW_UP_DATE)
                .zeebeAssignee("original");
    final Consumer<UserTaskBuilder> to1 =
        t ->
            t.zeebeUserTask()
                .zeebeAssigneeExpression("varAssignee")
                .zeebeTaskPriorityExpression("2*varPriority");

    // Test case where assignee and priority are removed
    final Consumer<UserTaskBuilder> fromWithoutAssigneeAndPriority =
        t ->
            t.zeebeCandidateGroups("g1,g2")
                .zeebeCandidateUsers("u1,u2")
                .zeebeDueDate(EXAMPLE_DUE_DATE)
                .zeebeFollowUpDate(EXAMPLE_FOLLOW_UP_DATE)
                .zeebeAssignee("original");
    final Consumer<UserTaskBuilder> toWithoutAssigneeAndPriority =
        AbstractUserTaskBuilder::zeebeUserTask;

    // Test case migrating from embedded form to external form reference
    final Consumer<UserTaskBuilder> fromEmbeddedForm =
        t ->
            t.zeebeCandidateGroups("g1,g2")
                .zeebeUserTaskForm("{}")
                .zeebeCandidateUsers("u1,u2")
                .zeebeDueDate(EXAMPLE_DUE_DATE)
                .zeebeFollowUpDate(EXAMPLE_FOLLOW_UP_DATE)
                .zeebeAssignee("original");
    final Consumer<UserTaskBuilder> toExternalForm =
        t ->
            t.zeebeUserTask()
                .zeebeAssigneeExpression("varAssignee")
                .zeebeExternalFormReference("localhost:8080//example.form");

    return Stream.of(
        Arguments.of(from1, to1, new ExpectedUserTask("demo", 40, null, null)),
        Arguments.of(
            fromWithoutAssigneeAndPriority,
            toWithoutAssigneeAndPriority,
            new ExpectedUserTask(null, 50, null, null)),
        Arguments.of(
            fromEmbeddedForm,
            toExternalForm,
            new ExpectedUserTask("demo", 50, "localhost:8080//example.form", null)));
  }

  private void migrateProcessInstance(
      final CamundaClient client,
      final Long processInstanceKey,
      final MigrationPlan migrationPlan) {
    client
        .newMigrateProcessInstanceCommand(processInstanceKey)
        .migrationPlan(migrationPlan)
        .send()
        .join();
  }

  record ExpectedUserTask(
      String assignee, Integer priority, String externalFormReference, Long formKey) {}
}
