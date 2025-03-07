/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.bpmn.activity.listeners.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeTaskListenerEventType;
import io.camunda.zeebe.protocol.impl.record.value.job.JobResult;
import io.camunda.zeebe.protocol.impl.record.value.usertask.UserTaskRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.protocol.record.value.JobKind;
import io.camunda.zeebe.protocol.record.value.JobListenerEventType;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.protocol.record.value.UserTaskRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.UUID;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

/** Tests verifying that a task listener may deny the event. */
public class TaskListenerDenialsTest {
  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();
  @Rule public final TestWatcher watcher = new RecordingExporterTestWatcher();
  private final TaskListenerTestHelper helper = new TaskListenerTestHelper(ENGINE);

  private String listenerType;

  @Before
  public void setup() {
    listenerType = "my_listener_" + UUID.randomUUID();
  }

  @Test
  public void shouldRejectUserTaskAssignmentWhenTaskListenerDeniesTheTransition() {
    // given
    final long processInstanceKey =
        helper.createProcessInstance(helper.createProcessWithAssigningTaskListeners(listenerType));

    ENGINE.userTask().ofInstance(processInstanceKey).withAssignee("new_assignee").assign();
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(listenerType)
        .withResult(new JobResult().setDenied(true))
        .complete();

    // then: ensure that `DENY_TASK_LISTENER` and `ASSIGNMENT_DENIED`
    // are written after `ASSIGNING` event
    helper.assertUserTaskIntentsSequence(
        processInstanceKey,
        UserTaskIntent.ASSIGNING,
        UserTaskIntent.DENY_TASK_LISTENER,
        UserTaskIntent.ASSIGNMENT_DENIED);

    // validate the assignee
    assertThat(
            RecordingExporter.userTaskRecords().withProcessInstanceKey(processInstanceKey).limit(1))
        .extracting(io.camunda.zeebe.protocol.record.Record::getValue)
        .extracting(UserTaskRecordValue::getAssignee)
        .describedAs(
            "The assignee should remain unchanged as assignment was denied by Task Listener")
        .containsExactly("");
  }

  @Test
  public void
      shouldCompleteAllAssignmentTaskListenersWhenFirstTaskListenerAcceptTransitionAfterDenial() {
    // given
    final long processInstanceKey =
        helper.createProcessInstance(
            helper.createProcessWithAssigningTaskListeners(
                listenerType, listenerType + "_2", listenerType + "_3"));
    ENGINE.userTask().ofInstance(processInstanceKey).withAssignee("new_assignee").assign();
    // assignment fails
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(listenerType)
        .withResult(new JobResult().setDenied(true))
        .complete();

    ENGINE.userTask().ofInstance(processInstanceKey).withAssignee("new_assignee").assign();
    // assignment is successful
    helper.completeRecreatedJobWithType(processInstanceKey, listenerType);
    helper.completeJobs(processInstanceKey, listenerType + "_2", listenerType + "_3");

    // then: ensure that all three `COMPLETE_TASK_LISTENER` events were triggered
    // correct assignee value is present at all stages
    assertThat(
            RecordingExporter.userTaskRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limit(r -> r.getIntent() == UserTaskIntent.ASSIGNED))
        .extracting(
            io.camunda.zeebe.protocol.record.Record::getIntent, r -> r.getValue().getAssignee())
        .describedAs("Verify that all task listeners were completed with the correct assignee")
        .containsSequence(
            tuple(UserTaskIntent.ASSIGNING, "new_assignee"),
            tuple(UserTaskIntent.DENY_TASK_LISTENER, "new_assignee"),
            tuple(UserTaskIntent.ASSIGNMENT_DENIED, ""),
            tuple(UserTaskIntent.ASSIGNING, "new_assignee"),
            tuple(UserTaskIntent.COMPLETE_TASK_LISTENER, "new_assignee"),
            tuple(UserTaskIntent.COMPLETE_TASK_LISTENER, "new_assignee"),
            tuple(UserTaskIntent.COMPLETE_TASK_LISTENER, "new_assignee"),
            tuple(UserTaskIntent.ASSIGNED, "new_assignee"));
  }

  @Test
  public void shouldUpdateTaskWhenUpdatingTaskListenerAcceptsTransitionAfterDenial() {
    // given
    final long processInstanceKey =
        helper.createProcessInstance(
            helper.createUserTaskWithTaskListeners(
                ZeebeTaskListenerEventType.updating, listenerType));

    // when: attempting to update the user task priority for the first time
    ENGINE.userTask().ofInstance(processInstanceKey).withPriority(80).update();

    // and: task listener denies the first update attempt
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(listenerType)
        .withResult(new JobResult().setDenied(true))
        .complete();

    // when: retrying the update operation with a new priority value
    ENGINE.userTask().ofInstance(processInstanceKey).withPriority(100).update();

    // and: completing the re-created task listener job
    helper.completeRecreatedJobWithType(processInstanceKey, listenerType);

    // then
    assertThat(
            RecordingExporter.userTaskRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limit(r -> r.getIntent() == UserTaskIntent.UPDATED))
        .extracting(
            io.camunda.zeebe.protocol.record.Record::getIntent, r -> r.getValue().getPriority())
        .describedAs(
            "Verify intents sequence and state of the `priority` property through the user task transitions")
        .containsSequence(
            // Initial state of the user task
            tuple(UserTaskIntent.CREATED, 50),
            // First update attempt and rejection by the listener
            tuple(UserTaskIntent.UPDATING, 80),
            tuple(UserTaskIntent.DENY_TASK_LISTENER, 80),
            // Priority reverts after rejection
            tuple(UserTaskIntent.UPDATE_DENIED, 50),
            // Second update attempt and successful completion
            tuple(UserTaskIntent.UPDATING, 100),
            tuple(UserTaskIntent.COMPLETE_TASK_LISTENER, 100),
            // Update was performed successfully
            tuple(UserTaskIntent.UPDATED, 100));
  }

  @Test
  public void
      shouldRevertToPreviousAssigneeWhenRejectingAssignmentFromTaskListenerAfterPreviouslySuccessfulAssignment() {
    // given
    final long processInstanceKey =
        helper.createProcessInstance(
            helper.createUserTaskWithTaskListenersAndAssignee(listenerType, "first_assignee"));

    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(listenerType)
        .withResult(new JobResult().setDenied(false))
        .complete();

    ENGINE.userTask().ofInstance(processInstanceKey).withAssignee("second_assignee").assign();

    helper.completeRecreatedJobWithTypeAndResult(
        processInstanceKey, listenerType, new JobResult().setDenied(true));

    assertThat(
            RecordingExporter.userTaskRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limit(r -> r.getIntent() == UserTaskIntent.ASSIGNMENT_DENIED))
        .extracting(
            io.camunda.zeebe.protocol.record.Record::getIntent, r -> r.getValue().getAssignee())
        .describedAs(
            "Verify that the assignee changes. The assignment of the second assignee should be rejected.")
        .containsSequence(
            tuple(UserTaskIntent.CREATING, ""),
            tuple(UserTaskIntent.CREATED, ""),
            tuple(UserTaskIntent.ASSIGNING, "first_assignee"),
            tuple(UserTaskIntent.COMPLETE_TASK_LISTENER, "first_assignee"),
            tuple(UserTaskIntent.ASSIGNED, "first_assignee"),
            tuple(UserTaskIntent.ASSIGNING, "second_assignee"),
            tuple(UserTaskIntent.DENY_TASK_LISTENER, "second_assignee"),
            // second assignee was not persisted
            tuple(UserTaskIntent.ASSIGNMENT_DENIED, "first_assignee"));

    // then: ensure that the assignee value is rolled back to the first successfully assigned
    // assignee
    assertThat(
            RecordingExporter.userTaskRecords()
                .withProcessInstanceKey(processInstanceKey)
                .filter(r -> r.getIntent() == UserTaskIntent.ASSIGNMENT_DENIED)
                .limit(1))
        .extracting(Record::getValue)
        .extracting(UserTaskRecordValue::getAssignee)
        .describedAs(
            "The assignee should remain unchanged as assignment was denied by Task Listener and the original value is provided")
        .containsExactly("first_assignee");
  }

  @Test
  public void shouldCompleteTaskWithTaskListenerWhenJobResultDeniedIsFalse() {
    // given
    final long processInstanceKey =
        helper.createProcessInstance(
            helper.createProcessWithCompletingTaskListeners(
                listenerType, listenerType + "_2", listenerType + "_3"));

    // when
    ENGINE.userTask().ofInstance(processInstanceKey).complete();
    ENGINE.job().ofInstance(processInstanceKey).withType(listenerType).complete();

    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(listenerType + "_2")
        .withResult(new JobResult())
        .complete();

    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(listenerType + "_3")
        .withResult(new JobResult().setDenied(false))
        .complete();

    // then
    assertThat(
            RecordingExporter.jobRecords()
                .withProcessInstanceKey(processInstanceKey)
                .withJobKind(JobKind.TASK_LISTENER)
                .withJobListenerEventType(JobListenerEventType.COMPLETING)
                .withIntent(JobIntent.COMPLETED)
                .limit(3))
        .extracting(Record::getValue)
        .extracting(JobRecordValue::getType, v -> v.getResult().isDenied())
        .describedAs("Verify that all task listeners were completed with `denied=false`")
        .containsExactly(
            tuple(listenerType, false),
            tuple(listenerType + "_2", false),
            tuple(listenerType + "_3", false));
  }

  @Test
  public void shouldRejectUserTaskUpdateWhenUpdatingTaskListenerDeniesTheTransition() {
    // given
    final long processInstanceKey =
        helper.createProcessInstance(
            helper.createUserTaskWithTaskListeners(
                ZeebeTaskListenerEventType.updating, listenerType));

    ENGINE.userTask().ofInstance(processInstanceKey).withPriority(99).update();

    // when: complete `updating` a listener job with a denied result
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(listenerType)
        .withResult(new JobResult().setDenied(true))
        .complete();

    // then: ensure that `DENY_TASK_LISTENER` and `UPDATE_DENIED`
    // are written right after `UPDATING` event
    helper.assertUserTaskIntentsSequence(
        processInstanceKey,
        UserTaskIntent.UPDATING,
        UserTaskIntent.DENY_TASK_LISTENER,
        UserTaskIntent.UPDATE_DENIED);
  }

  @Test
  public void shouldRejectUserTaskCompletionWhenCompletingTaskListenerDeniesTheTransition() {
    // given
    final long processInstanceKey =
        helper.createProcessInstance(helper.createProcessWithCompletingTaskListeners(listenerType));

    ENGINE.userTask().ofInstance(processInstanceKey).complete();
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(listenerType)
        .withResult(new JobResult().setDenied(true))
        .complete();

    // then: ensure that `DENY_TASK_LISTENER` and `COMPLETION_DENIED`
    // are written after `COMPLETING` event
    helper.assertUserTaskIntentsSequence(
        processInstanceKey,
        UserTaskIntent.COMPLETING,
        UserTaskIntent.DENY_TASK_LISTENER,
        UserTaskIntent.COMPLETION_DENIED);
  }

  @Test
  public void shouldAcceptUserTaskCompletionAfterTaskListenerRejectsTheOperationWithDeniedReason() {
    // given
    final long processInstanceKey =
        helper.createProcessInstance(helper.createProcessWithCompletingTaskListeners(listenerType));

    // deny the completion
    ENGINE.userTask().ofInstance(processInstanceKey).complete();
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(listenerType)
        .withResult(
            new JobResult().setDenied(true).setDeniedReason("Reason to deny lifecycle transition"))
        .complete();

    // completion is successful
    ENGINE.userTask().ofInstance(processInstanceKey).complete();
    helper.completeRecreatedJobWithType(processInstanceKey, listenerType);
    helper.completeJobs(processInstanceKey);

    // validate reason to deny lifecycle transition in events
    assertThat(
            RecordingExporter.userTaskRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limit(r -> r.getIntent() == UserTaskIntent.COMPLETED))
        .extracting(Record::getIntent, r -> getDeniedReason(r.getValue()))
        .describedAs(
            "The reason to deny lifecycle transition should be present when task listener denies the work")
        .containsExactly(
            tuple(UserTaskIntent.CREATING, ""),
            tuple(UserTaskIntent.CREATED, ""),
            tuple(UserTaskIntent.COMPLETING, ""),
            tuple(UserTaskIntent.DENY_TASK_LISTENER, "Reason to deny lifecycle transition"),
            tuple(UserTaskIntent.COMPLETION_DENIED, "Reason to deny lifecycle transition"),
            tuple(UserTaskIntent.COMPLETING, ""),
            tuple(UserTaskIntent.COMPLETE_TASK_LISTENER, ""),
            tuple(UserTaskIntent.COMPLETED, ""));
  }

  @Test
  public void shouldAcceptUserTaskAssignmentAfterTaskListenerRejectsTheOperationWithDeniedReason() {
    // given
    final long processInstanceKey =
        helper.createProcessInstance(helper.createProcessWithAssigningTaskListeners(listenerType));

    ENGINE.userTask().ofInstance(processInstanceKey).withAssignee("new_assignee").assign();

    // deny the assignment
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(listenerType)
        .withResult(
            new JobResult().setDenied(true).setDeniedReason("Reason to deny lifecycle transition"))
        .complete();

    // accept the assignment
    ENGINE.userTask().ofInstance(processInstanceKey).withAssignee("new_assignee").assign();
    helper.completeRecreatedJobWithType(processInstanceKey, listenerType);
    helper.completeJobs(processInstanceKey);

    // validate the reason to deny lifecycle transition
    assertThat(
            RecordingExporter.userTaskRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limit(r -> r.getIntent() == UserTaskIntent.ASSIGNED))
        .extracting(Record::getIntent, r -> getDeniedReason(r.getValue()))
        .describedAs(
            "The reason to deny lifecycle transition should be present when task listener denies the work")
        .containsExactly(
            tuple(UserTaskIntent.CREATING, ""),
            tuple(UserTaskIntent.CREATED, ""),
            tuple(UserTaskIntent.ASSIGNING, ""),
            tuple(UserTaskIntent.DENY_TASK_LISTENER, "Reason to deny lifecycle transition"),
            tuple(UserTaskIntent.ASSIGNMENT_DENIED, "Reason to deny lifecycle transition"),
            tuple(UserTaskIntent.ASSIGNING, ""),
            tuple(UserTaskIntent.COMPLETE_TASK_LISTENER, ""),
            tuple(UserTaskIntent.ASSIGNED, ""));
  }

  @Test
  public void shouldAcceptUserTaskUpdateAfterTaskListenerRejectsTheOperationWithDeniedReason() {
    // given
    final long processInstanceKey =
        helper.createProcessInstance(
            helper.createUserTaskWithTaskListeners(
                ZeebeTaskListenerEventType.updating, listenerType));

    ENGINE.userTask().ofInstance(processInstanceKey).withPriority(80).update();

    // deny the update
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(listenerType)
        .withResult(
            new JobResult().setDenied(true).setDeniedReason("Reason to deny lifecycle transition"))
        .complete();

    // accept the update
    ENGINE.userTask().ofInstance(processInstanceKey).withPriority(80).update();
    helper.completeRecreatedJobWithType(processInstanceKey, listenerType);
    helper.completeJobs(processInstanceKey);

    // validate the reason to deny lifecycle transition
    assertThat(
            RecordingExporter.userTaskRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limit(r -> r.getIntent() == UserTaskIntent.UPDATED))
        .extracting(Record::getIntent, r -> getDeniedReason(r.getValue()))
        .describedAs(
            "The reason to deny lifecycle transition should be present when task listener denies the work")
        .containsExactly(
            tuple(UserTaskIntent.CREATING, ""),
            tuple(UserTaskIntent.CREATED, ""),
            tuple(UserTaskIntent.UPDATING, ""),
            tuple(UserTaskIntent.DENY_TASK_LISTENER, "Reason to deny lifecycle transition"),
            tuple(UserTaskIntent.UPDATE_DENIED, "Reason to deny lifecycle transition"),
            tuple(UserTaskIntent.UPDATING, ""),
            tuple(UserTaskIntent.COMPLETE_TASK_LISTENER, ""),
            tuple(UserTaskIntent.UPDATED, ""));
  }

  private String getDeniedReason(final UserTaskRecordValue record) {
    // This is to be removed when denied reason is exposed in the UserTaskRecordValue interface.
    // Currently added in order to separate processing implementation.
    return ((UserTaskRecord) record).getDeniedReason();
  }

  @Test
  public void shouldCompleteTaskWhenCompletingTaskListenerAcceptsTransitionAfterDenial() {
    // given
    final long processInstanceKey =
        helper.createProcessInstance(helper.createProcessWithCompletingTaskListeners(listenerType));

    ENGINE.userTask().ofInstance(processInstanceKey).complete();
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(listenerType)
        .withResult(new JobResult().setDenied(true))
        .complete();

    ENGINE.userTask().ofInstance(processInstanceKey).complete();

    helper.completeRecreatedJobWithType(processInstanceKey, listenerType);

    // then: ensure that `COMPLETING` `COMPLETE_TASK_LISTENER` and `COMPLETED events
    // are present after `DENY_TASK_LISTENER` and `COMPLETION_DENIED` events
    helper.assertUserTaskIntentsSequence(
        processInstanceKey,
        UserTaskIntent.COMPLETING,
        UserTaskIntent.DENY_TASK_LISTENER,
        UserTaskIntent.COMPLETION_DENIED,
        UserTaskIntent.COMPLETING,
        UserTaskIntent.COMPLETE_TASK_LISTENER,
        UserTaskIntent.COMPLETED);
  }

  @Test
  public void shouldCompleteAllTaskListenersWhenFirstTaskListenerAcceptTransitionAfterDenial() {
    // given
    final long processInstanceKey =
        helper.createProcessInstance(
            helper.createProcessWithCompletingTaskListeners(
                listenerType, listenerType + "_2", listenerType + "_3"));

    ENGINE.userTask().ofInstance(processInstanceKey).complete();
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(listenerType)
        .withResult(new JobResult().setDenied(true))
        .complete();

    ENGINE.userTask().ofInstance(processInstanceKey).complete();
    helper.completeRecreatedJobWithType(processInstanceKey, listenerType);
    helper.completeJobs(processInstanceKey, listenerType + "_2", listenerType + "_3");

    // then: ensure that all three `COMPLETE_TASK_LISTENER` events were triggered after the
    // rejection from the first Task Listener
    helper.assertUserTaskIntentsSequence(
        processInstanceKey,
        UserTaskIntent.COMPLETING,
        UserTaskIntent.DENY_TASK_LISTENER,
        UserTaskIntent.COMPLETION_DENIED,
        UserTaskIntent.COMPLETING,
        UserTaskIntent.COMPLETE_TASK_LISTENER,
        UserTaskIntent.COMPLETE_TASK_LISTENER,
        UserTaskIntent.COMPLETE_TASK_LISTENER,
        UserTaskIntent.COMPLETED);
  }

  @Test
  public void shouldAssignAndCompleteTaskAfterTaskListenerDeniesTheCompletion() {
    // given
    final long processInstanceKey =
        helper.createProcessInstance(helper.createProcessWithCompletingTaskListeners(listenerType));

    ENGINE.userTask().ofInstance(processInstanceKey).complete();
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(listenerType)
        .withResult(new JobResult().setDenied(true))
        .complete();

    ENGINE.userTask().ofInstance(processInstanceKey).withAssignee("Test Assignee").assign();
    ENGINE.userTask().ofInstance(processInstanceKey).complete();

    helper.completeRecreatedJobWithType(processInstanceKey, listenerType);

    // then: ensure that user task could be assigned after completion was rejected from the
    // `COMPLETE` Task Listener. Ensure that user task could be completed after assignment
    // and `COMPLETE_TASK_LISTENER` event was triggered successfully
    helper.assertUserTaskIntentsSequence(
        processInstanceKey,
        UserTaskIntent.COMPLETING,
        UserTaskIntent.DENY_TASK_LISTENER,
        UserTaskIntent.COMPLETION_DENIED,
        UserTaskIntent.ASSIGNING,
        UserTaskIntent.ASSIGNED,
        UserTaskIntent.COMPLETING,
        UserTaskIntent.COMPLETE_TASK_LISTENER,
        UserTaskIntent.COMPLETED);
  }
}
