/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.bpmn.activity.listeners.task;

import static io.camunda.zeebe.test.util.record.RecordingExporter.records;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.engine.util.client.UserTaskClient;
import io.camunda.zeebe.model.bpmn.builder.UserTaskBuilder;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeTaskListenerEventType;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.protocol.record.value.ErrorType;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

/** Tests verifying that a task listener may raise resolvable incidents. */
public class TaskListenerIncidentsTest {
  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();
  @Rule public final TestWatcher watcher = new RecordingExporterTestWatcher();
  private final TaskListenerTestHelper helper = new TaskListenerTestHelper(ENGINE);

  private String listenerType;

  @Before
  public void setup() {
    listenerType = "my_listener_" + UUID.randomUUID();
  }

  @Test
  public void
      shouldCreateJobNoRetriesIncidentForAssigningListenerAndContinueAfterResolutionOnTaskAssignAfterCreation() {
    verifyIncidentCreationOnListenerJobWithoutRetriesAndResolution(
        ZeebeTaskListenerEventType.assigning,
        userTask -> userTask.zeebeAssignee("gandalf"),
        userTaskClient -> {},
        UserTaskIntent.ASSIGNED);
  }

  @Test
  public void
      shouldCreateJobNoRetriesIncidentForAssigningListenerAndContinueAfterResolutionOnTaskAssign() {
    verifyIncidentCreationOnListenerJobWithoutRetriesAndResolution(
        ZeebeTaskListenerEventType.assigning,
        UnaryOperator.identity(),
        userTaskClient -> userTaskClient.withAssignee("bilbo").assign(),
        UserTaskIntent.ASSIGNED);
  }

  @Test
  public void
      shouldCreateJobNoRetriesIncidentForAssigningListenerAndContinueAfterResolutionOnTaskClaim() {
    verifyIncidentCreationOnListenerJobWithoutRetriesAndResolution(
        ZeebeTaskListenerEventType.assigning,
        UnaryOperator.identity(),
        userTaskClient -> userTaskClient.withAssignee("bilbo").claim(),
        UserTaskIntent.ASSIGNED);
  }

  @Test
  public void
      shouldCreateJobNoRetriesIncidentForUpdatingListenerAndContinueAfterResolutionOnTaskUpdate() {
    verifyIncidentCreationOnListenerJobWithoutRetriesAndResolution(
        ZeebeTaskListenerEventType.updating,
        UnaryOperator.identity(),
        UserTaskClient::update,
        UserTaskIntent.UPDATED);
  }

  @Test
  public void
      shouldCreateJobNoRetriesIncidentForCompletingListenerAndContinueAfterResolutionOnTaskComplete() {
    verifyIncidentCreationOnListenerJobWithoutRetriesAndResolution(
        ZeebeTaskListenerEventType.completing,
        UnaryOperator.identity(),
        UserTaskClient::complete,
        UserTaskIntent.COMPLETED);
  }

  private void verifyIncidentCreationOnListenerJobWithoutRetriesAndResolution(
      final ZeebeTaskListenerEventType eventType,
      final UnaryOperator<UserTaskBuilder> userTaskBuilder,
      final Consumer<UserTaskClient> userTaskAction,
      final UserTaskIntent terminalActionIntent) {

    // given
    final long processInstanceKey =
        helper.createProcessInstance(
            helper.createProcessWithZeebeUserTask(
                t ->
                    userTaskBuilder
                        .apply(t)
                        .zeebeTaskListener(l -> l.eventType(eventType).type(listenerType))
                        .zeebeTaskListener(l -> l.eventType(eventType).type(listenerType + "_2"))
                        .zeebeTaskListener(l -> l.eventType(eventType).type(listenerType + "_3"))));

    // when: performing the user task action
    userTaskAction.accept(ENGINE.userTask().ofInstance(processInstanceKey));

    // complete first listener job
    helper.completeJobs(processInstanceKey, listenerType);

    // fail the second listener job with no retries
    final var failedJob =
        ENGINE
            .job()
            .ofInstance(processInstanceKey)
            .withType(listenerType + "_2")
            .withRetries(0)
            .fail();

    // then: incident should be created
    final var incident =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();
    Assertions.assertThat(incident.getValue())
        .hasProcessInstanceKey(processInstanceKey)
        .hasErrorType(ErrorType.TASK_LISTENER_NO_RETRIES)
        .hasJobKey(failedJob.getKey())
        .hasErrorMessage("No more retries left.");

    // when: update retries and resolve incident
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(listenerType + "_2")
        .withRetries(1)
        .updateRetries();
    ENGINE.incident().ofInstance(processInstanceKey).withKey(incident.getKey()).resolve();

    // complete failed and remaining listener jobs
    helper.completeJobs(processInstanceKey, listenerType + "_2", listenerType + "_3");

    // then
    assertThat(records().limit(r -> r.getIntent() == terminalActionIntent))
        .extracting(io.camunda.zeebe.protocol.record.Record::getValueType, Record::getIntent)
        .describedAs("Expected listener jobs to complete after incident resolution")
        .containsSubsequence(
            tuple(ValueType.JOB, JobIntent.CREATED),
            tuple(ValueType.JOB, JobIntent.COMPLETE),
            tuple(ValueType.JOB, JobIntent.COMPLETED),
            tuple(ValueType.USER_TASK, UserTaskIntent.COMPLETE_TASK_LISTENER),
            tuple(ValueType.JOB, JobIntent.CREATED),
            tuple(ValueType.JOB, JobIntent.FAILED),
            // the incident was created & resolved
            tuple(ValueType.INCIDENT, IncidentIntent.CREATED),
            tuple(ValueType.JOB, JobIntent.RETRIES_UPDATED),
            tuple(ValueType.INCIDENT, IncidentIntent.RESOLVED),
            // the failed listener job was retried
            tuple(ValueType.JOB, JobIntent.COMPLETE),
            tuple(ValueType.JOB, JobIntent.COMPLETED),
            tuple(ValueType.USER_TASK, UserTaskIntent.COMPLETE_TASK_LISTENER),
            // the remaining listener job was completed
            tuple(ValueType.JOB, JobIntent.CREATED),
            tuple(ValueType.JOB, JobIntent.COMPLETE),
            tuple(ValueType.JOB, JobIntent.COMPLETED),
            tuple(ValueType.USER_TASK, UserTaskIntent.COMPLETE_TASK_LISTENER),
            tuple(ValueType.USER_TASK, terminalActionIntent));
  }
}
