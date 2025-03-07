/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.bpmn.activity.listeners.task;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.engine.util.client.UserTaskClient;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeTaskListenerEventType;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.protocol.record.value.ErrorType;
import io.camunda.zeebe.protocol.record.value.JobListenerEventType;
import io.camunda.zeebe.protocol.record.value.UserTaskRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class TaskListenerTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  private final TaskListenerTestHelper helper = new TaskListenerTestHelper(ENGINE);
  private String listenerType;

  @Before
  public void setup() {
    listenerType = "my_listener_" + UUID.randomUUID();
  }

  @Test
  public void shouldRetryUserTaskCompleteCommandAfterExtractValueErrorIncidentResolution() {
    testUserTaskCommandRetryAfterExtractValueError(
        ZeebeTaskListenerEventType.completing,
        "completing_listener_var_name",
        "expression_completing_listener_2",
        UserTaskClient::complete,
        UserTaskIntent.COMPLETED,
        userTask -> Assertions.assertThat(userTask).hasAction("complete"));
  }

  @Test
  public void shouldRetryUserTaskAssignCommandAfterExtractValueErrorIncidentResolution() {
    testUserTaskCommandRetryAfterExtractValueError(
        ZeebeTaskListenerEventType.assigning,
        "assigning_listener_var_name",
        "expression_assigning_listener_2",
        userTask -> userTask.withAssignee("me").assign(),
        UserTaskIntent.ASSIGNED,
        userTask -> Assertions.assertThat(userTask).hasAssignee("me").hasAction("assign"));
  }

  @Test
  public void shouldRetryUserTaskClaimCommandAfterExtractValueErrorIncidentResolution() {
    testUserTaskCommandRetryAfterExtractValueError(
        ZeebeTaskListenerEventType.assigning,
        "assigning_listener_var_name",
        "expression_assigning_listener_2",
        userTask -> userTask.withAssignee("me").claim(),
        UserTaskIntent.ASSIGNED,
        userTask -> Assertions.assertThat(userTask).hasAssignee("me").hasAction("claim"));
  }

  @Test
  public void shouldRetryUserTaskUpdateCommandAfterExtractValueErrorIncidentResolution() {
    testUserTaskCommandRetryAfterExtractValueError(
        ZeebeTaskListenerEventType.updating,
        "updating_listener_var_name",
        "expression_updating_listener_2",
        UserTaskClient::update,
        UserTaskIntent.UPDATED,
        userTask -> Assertions.assertThat(userTask).hasAction("update"));
  }

  private void testUserTaskCommandRetryAfterExtractValueError(
      final ZeebeTaskListenerEventType eventType,
      final String variableName,
      final String variableValue,
      final Consumer<UserTaskClient> userTaskAction,
      final UserTaskIntent expectedIntent,
      final Consumer<UserTaskRecordValue> assertion) {

    // given
    final long processInstanceKey =
        helper.createProcessInstance(
            helper.createUserTaskWithTaskListeners(
                eventType, listenerType, "=" + variableName, listenerType + "_3"));

    // when: perform the user task action
    userTaskAction.accept(ENGINE.userTask().ofInstance(processInstanceKey));

    // complete the first task listener job
    ENGINE.job().ofInstance(processInstanceKey).withType(listenerType).complete();

    // then: expect an incident due to missing variable
    final var incidentRecord =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    Assertions.assertThat(incidentRecord.getValue())
        .hasProcessInstanceKey(processInstanceKey)
        .hasErrorType(ErrorType.EXTRACT_VALUE_ERROR)
        .hasErrorMessage(
            """
                Expected result of the expression '%s' to be 'STRING', but was 'NULL'. \
                The evaluation reported the following warnings:
                [NO_VARIABLE_FOUND] No variable found with name '%s'"""
                .formatted(variableName, variableName));

    // when: fix the missing variable and resolve the incident
    ENGINE
        .variables()
        .ofScope(processInstanceKey)
        .withDocument(Map.of(variableName, variableValue))
        .update();
    ENGINE.incident().ofInstance(processInstanceKey).withKey(incidentRecord.getKey()).resolve();

    // complete the retried task listener job and remaining task listeners
    helper.completeRecreatedJobWithType(processInstanceKey, listenerType);
    helper.completeJobs(processInstanceKey, variableValue, listenerType + "_3");

    // then
    helper.assertTaskListenerJobsCompletionSequence(
        processInstanceKey,
        helper.mapToJobListenerEventType(eventType),
        listenerType,
        listenerType, // re-created task listener job
        variableValue,
        listenerType + "_3");

    helper.assertUserTaskRecordWithIntent(processInstanceKey, expectedIntent, assertion);
  }

  @Test
  public void
      shouldTriggerUserTaskAssignCommandAfterExtractValueErrorIncidentResolutionWhenUserTaskWasConfiguredWithAssignee() {
    // given
    final var assignee = "me";

    // when: process instance is created with a UT having an `assignee` and `assignment` listeners
    final long processInstanceKey =
        helper.createProcessInstance(
            helper.createProcessWithZeebeUserTask(
                task ->
                    task.zeebeAssignee(assignee)
                        .zeebeTaskListener(l -> l.assigning().type(listenerType))
                        .zeebeTaskListener(
                            l -> l.assigning().typeExpression("assigning_listener_var_name"))
                        .zeebeTaskListener(l -> l.assigning().type(listenerType + "_3"))));

    // complete the first task listener job
    ENGINE.job().ofInstance(processInstanceKey).withType(listenerType).complete();

    // then: expect an incident due to missing `assign_listener_var_name` variable
    final var incident =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    Assertions.assertThat(incident.getValue())
        .hasProcessInstanceKey(processInstanceKey)
        .hasErrorType(ErrorType.EXTRACT_VALUE_ERROR)
        .hasErrorMessage(
            """
                Expected result of the expression 'assigning_listener_var_name' to be 'STRING', but was 'NULL'. \
                The evaluation reported the following warnings:
                [NO_VARIABLE_FOUND] No variable found with name 'assigning_listener_var_name'""");

    // when: fix the missing variable and resolve the incident
    ENGINE
        .variables()
        .ofScope(processInstanceKey)
        .withDocument(Map.of("assigning_listener_var_name", "expression_assigning_listener_2"))
        .update();

    ENGINE.incident().ofInstance(processInstanceKey).withKey(incident.getKey()).resolve();

    // complete the retried task listener job and remaining task listeners
    helper.completeRecreatedJobWithType(processInstanceKey, listenerType);
    helper.completeJobs(processInstanceKey, "expression_assigning_listener_2", listenerType + "_3");

    // then
    helper.assertTaskListenerJobsCompletionSequence(
        processInstanceKey,
        JobListenerEventType.ASSIGNING,
        listenerType,
        listenerType, // re-created task listener job
        "expression_assigning_listener_2",
        listenerType + "_3");

    helper.assertUserTaskRecordWithIntent(
        processInstanceKey,
        UserTaskIntent.ASSIGNED,
        userTask -> Assertions.assertThat(userTask).hasAssignee(assignee).hasAction(""));
  }
}
