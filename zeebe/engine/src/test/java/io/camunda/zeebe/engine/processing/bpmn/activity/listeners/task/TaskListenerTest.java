/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.bpmn.activity.listeners.task;

import static io.camunda.zeebe.engine.processing.job.JobCompleteProcessor.TL_JOB_COMPLETION_WITH_VARS_NOT_SUPPORTED_MESSAGE;
import static io.camunda.zeebe.test.util.record.RecordingExporter.jobRecords;
import static io.camunda.zeebe.test.util.record.RecordingExporter.records;
import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.engine.util.client.UserTaskClient;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.builder.AbstractFlowNodeBuilder;
import io.camunda.zeebe.model.bpmn.builder.StartEventBuilder;
import io.camunda.zeebe.model.bpmn.builder.UserTaskBuilder;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeTaskListenerEventType;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.record.value.job.JobResult;
import io.camunda.zeebe.protocol.impl.record.value.job.JobResultCorrections;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.impl.record.value.usertask.UserTaskRecord;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.DeploymentIntent;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.TimerIntent;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ErrorType;
import io.camunda.zeebe.protocol.record.value.JobKind;
import io.camunda.zeebe.protocol.record.value.JobListenerEventType;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.protocol.record.value.UserTaskRecordValue;
import io.camunda.zeebe.protocol.record.value.deployment.FormMetadataValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import org.apache.commons.lang3.StringUtils;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;

public class TaskListenerTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  private static final String PROCESS_ID = "process";

  private static final String USER_TASK_ELEMENT_ID = "my_user_task";
  private static final List<String> ALL_CORRECTABLE_ATTRIBUTES =
      List.of(
          UserTaskRecord.ASSIGNEE,
          UserTaskRecord.CANDIDATE_GROUPS,
          UserTaskRecord.CANDIDATE_USERS,
          UserTaskRecord.DUE_DATE,
          UserTaskRecord.FOLLOW_UP_DATE,
          UserTaskRecord.PRIORITY);

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  private String listenerType;

  @Before
  public void setup() {
    listenerType = "my_listener_" + UUID.randomUUID();
  }

  @Test
  public void shouldCompleteUserTaskAfterAllCompleteTaskListenersAreExecuted() {
    // given
    final long processInstanceKey =
        createProcessInstance(
            createProcessWithCompletingTaskListeners(
                listenerType, listenerType + "_2", listenerType + "_3"));

    // when
    ENGINE
        .userTask()
        .ofInstance(processInstanceKey)
        .withVariable("foo_var", "bar")
        .withAction("my_custom_action")
        .complete();
    completeJobs(processInstanceKey, listenerType, listenerType + "_2", listenerType + "_3");

    // then
    assertTaskListenerJobsCompletionSequence(
        processInstanceKey,
        JobListenerEventType.COMPLETING,
        listenerType,
        listenerType + "_2",
        listenerType + "_3");

    // ensure that `COMPLETE_TASK_LISTENER` commands were triggered between
    // `COMPLETING` and `COMPLETED` events
    assertUserTaskIntentsSequence(
        UserTaskIntent.COMPLETE,
        UserTaskIntent.COMPLETING,
        UserTaskIntent.COMPLETE_TASK_LISTENER,
        UserTaskIntent.COMPLETE_TASK_LISTENER,
        UserTaskIntent.COMPLETE_TASK_LISTENER,
        UserTaskIntent.COMPLETED);

    assertUserTaskRecordWithIntent(
        processInstanceKey,
        UserTaskIntent.COMPLETED,
        userTask ->
            Assertions.assertThat(userTask)
                .hasAction("my_custom_action")
                .hasVariables(Map.of("foo_var", "bar"))
                .hasNoChangedAttributes());
    assertThatProcessInstanceCompleted(processInstanceKey);
  }

  @Test
  public void shouldAssignUserTaskAfterAllAssignmentTaskListenersAreExecuted() {
    // given
    final long processInstanceKey =
        createProcessInstance(
            createUserTaskWithTaskListeners(
                ZeebeTaskListenerEventType.assigning,
                listenerType,
                listenerType + "_2",
                listenerType + "_3"));

    // when
    ENGINE
        .userTask()
        .ofInstance(processInstanceKey)
        .withAssignee("me")
        .withAction("my_assign_action")
        .assign();
    completeJobs(processInstanceKey, listenerType, listenerType + "_2", listenerType + "_3");

    // then
    assertTaskListenerJobsCompletionSequence(
        processInstanceKey,
        JobListenerEventType.ASSIGNING,
        listenerType,
        listenerType + "_2",
        listenerType + "_3");

    // ensure that `COMPLETE_TASK_LISTENER` commands were triggered between
    // `ASSIGNING` and `ASSIGNED` events
    assertUserTaskIntentsSequence(
        UserTaskIntent.ASSIGN,
        UserTaskIntent.ASSIGNING,
        UserTaskIntent.COMPLETE_TASK_LISTENER,
        UserTaskIntent.COMPLETE_TASK_LISTENER,
        UserTaskIntent.COMPLETE_TASK_LISTENER,
        UserTaskIntent.ASSIGNED);

    assertUserTaskRecordWithIntent(
        processInstanceKey,
        UserTaskIntent.ASSIGNED,
        userTask ->
            Assertions.assertThat(userTask)
                .hasAssignee("me")
                .hasAction("my_assign_action")
                .hasOnlyChangedAttributes(UserTaskRecord.ASSIGNEE));
  }

  @Test
  public void shouldExecuteAllAssigningListenersOnUnassignmentAfterSuccessfulAssignment() {
    // given: a user task with multiple `assigning` task listeners
    final long processInstanceKey =
        createProcessInstance(
            createUserTaskWithTaskListeners(
                ZeebeTaskListenerEventType.assigning,
                listenerType,
                listenerType + "_2",
                listenerType + "_3"));

    // when: assign the user task to "me" and complete all `assigning` listener jobs
    ENGINE.userTask().ofInstance(processInstanceKey).withAssignee("me").assign();
    completeJobs(processInstanceKey, listenerType, listenerType + "_2", listenerType + "_3");

    // and: unassign the user task and complete all `assigning` listener jobs again
    ENGINE.userTask().ofInstance(processInstanceKey).unassign();
    completeRecreatedJobs(
        processInstanceKey, listenerType, listenerType + "_2", listenerType + "_3");

    // then: all `assigning` listeners should be executed for both assign and unassign operations
    assertTaskListenerJobsCompletionSequence(
        processInstanceKey,
        JobListenerEventType.ASSIGNING,
        listenerType,
        listenerType + "_2",
        listenerType + "_3",
        listenerType,
        listenerType + "_2",
        listenerType + "_3");

    // and: user task should be correctly assigned and unassigned
    assertThat(
            RecordingExporter.userTaskRecords(UserTaskIntent.ASSIGNED)
                .withProcessInstanceKey(processInstanceKey)
                .limit(2))
        .describedAs(
            "Expected user task assignment and unassignment actions to be recorded correctly")
        .extracting(r -> r.getValue().getAssignee(), r -> r.getValue().getAction())
        .containsExactly(
            tuple("me", "assign"), // First assignment
            tuple("", "unassign") // Unassignment
            );
  }

  @Test
  public void shouldUpdateUserTaskAfterAllUpdatingTaskListenersAreExecuted() {
    // given
    final long processInstanceKey =
        createProcessInstance(
            createUserTaskWithTaskListeners(
                ZeebeTaskListenerEventType.updating,
                listenerType,
                listenerType + "_2",
                listenerType + "_3"));

    // when
    ENGINE
        .userTask()
        .ofInstance(processInstanceKey)
        .withAction("my_update_action")
        .update(
            new UserTaskRecord()
                .setCandidateUsersList(List.of("samwise", "frodo"))
                .setCandidateUsersChanged()
                .setPriority(88)
                .setPriorityChanged());
    completeJobs(processInstanceKey, listenerType, listenerType + "_2", listenerType + "_3");

    // then
    assertTaskListenerJobsCompletionSequence(
        processInstanceKey,
        JobListenerEventType.UPDATING,
        listenerType,
        listenerType + "_2",
        listenerType + "_3");

    // ensure that `COMPLETE_TASK_LISTENER` commands were triggered between
    // `UPDATING` and `UPDATED` events
    assertUserTaskIntentsSequence(
        UserTaskIntent.UPDATE,
        UserTaskIntent.UPDATING,
        UserTaskIntent.COMPLETE_TASK_LISTENER,
        UserTaskIntent.COMPLETE_TASK_LISTENER,
        UserTaskIntent.COMPLETE_TASK_LISTENER,
        UserTaskIntent.UPDATED);

    assertUserTaskRecordWithIntent(
        processInstanceKey,
        UserTaskIntent.UPDATED,
        userTask ->
            Assertions.assertThat(userTask)
                .hasAssignee("")
                .hasCandidateGroupsList(List.of())
                .hasCandidateUsersList(List.of("samwise", "frodo")) // updated
                .hasDueDate("")
                .hasFollowUpDate("")
                .hasPriority(88) // updated
                .hasAction("my_update_action")
                .hasOnlyChangedAttributes(UserTaskRecord.CANDIDATE_USERS, UserTaskRecord.PRIORITY));
  }

  @Test
  public void shouldExecuteAllUpdatingListenersOnRepeatedUserTaskUpdates() {
    // given: a user task with multiple `updating` task listeners
    final long processInstanceKey =
        createProcessInstance(
            createUserTaskWithTaskListeners(
                ZeebeTaskListenerEventType.updating,
                listenerType,
                listenerType + "_2",
                listenerType + "_3"));

    // when: update the user task with new candidate users
    ENGINE
        .userTask()
        .ofInstance(processInstanceKey)
        .update(
            new UserTaskRecord()
                .setCandidateUsersList(List.of("frodo", "samwise"))
                .setCandidateUsersChanged());

    // complete all `updating` listener jobs
    completeJobs(processInstanceKey, listenerType, listenerType + "_2", listenerType + "_3");

    // and: update the user task again with new candidate users and priority
    ENGINE
        .userTask()
        .ofInstance(processInstanceKey)
        .withAction("escalate")
        .update(
            new UserTaskRecord()
                .setCandidateUsersList(List.of("aragorn", "legolas"))
                .setCandidateUsersChanged()
                .setPriority(99)
                .setPriorityChanged());

    // complete all `updating` listener jobs for the second update
    completeRecreatedJobs(
        processInstanceKey, listenerType, listenerType + "_2", listenerType + "_3");

    // then: all `updating` listeners should execute for both update operations
    assertTaskListenerJobsCompletionSequence(
        processInstanceKey,
        JobListenerEventType.UPDATING,
        listenerType,
        listenerType + "_2",
        listenerType + "_3",
        listenerType,
        listenerType + "_2",
        listenerType + "_3");

    // and: user task should be correctly updated after both update operations
    assertThat(
            RecordingExporter.userTaskRecords(UserTaskIntent.UPDATED)
                .withProcessInstanceKey(processInstanceKey)
                .limit(2))
        .describedAs(
            "Expected user task updates to be recorded correctly after all updating listeners execute")
        .extracting(
            r -> r.getValue().getCandidateUsersList(),
            r -> r.getValue().getPriority(),
            r -> r.getValue().getAction(),
            r -> r.getValue().getChangedAttributes())
        .containsExactly(
            // First update
            tuple(
                List.of("frodo", "samwise"), 50, "update", List.of(UserTaskRecord.CANDIDATE_USERS)),
            // Second update
            tuple(
                List.of("aragorn", "legolas"),
                99,
                "escalate",
                List.of(UserTaskRecord.CANDIDATE_USERS, UserTaskRecord.PRIORITY)));
  }

  @Test
  public void
      shouldCancelUserTaskAfterAllUpdatingTaskListenersAreExecutedAndTerminateUserTaskElement() {
    // given
    final long processInstanceKey =
        createProcessInstance(
            createUserTaskWithTaskListeners(
                ZeebeTaskListenerEventType.canceling,
                listenerType,
                listenerType + "_2",
                listenerType + "_3"));
    final var userTaskInstanceKey =
        RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst()
            .getValue()
            .getElementInstanceKey();

    // when
    ENGINE.processInstance().withInstanceKey(processInstanceKey).expectTerminating().cancel();

    completeJobs(processInstanceKey, listenerType, listenerType + "_2", listenerType + "_3");

    // then
    assertTaskListenerJobsCompletionSequence(
        processInstanceKey,
        JobListenerEventType.CANCELING,
        listenerType,
        listenerType + "_2",
        listenerType + "_3");

    assertUserTaskRecordWithIntent(
        processInstanceKey,
        UserTaskIntent.CANCELED,
        userTask -> assertThat(userTask.getAction()).isEmpty());

    final Predicate<Record<?>> isUserTaskOrProcessInstanceRecordWithUserTaskInstanceKey =
        r -> {
          if (r.getValue() instanceof UserTaskRecord utr) {
            return utr.getElementInstanceKey() == userTaskInstanceKey;
          } else if (r.getValue() instanceof ProcessInstanceRecord) {
            return r.getKey() == userTaskInstanceKey;
          }
          return false;
        };

    assertThat(
            RecordingExporter.records()
                .limitToProcessInstance(processInstanceKey)
                .filter(isUserTaskOrProcessInstanceRecordWithUserTaskInstanceKey))
        .extracting(Record::getValueType, Record::getIntent)
        .containsSequence(
            tuple(ValueType.PROCESS_INSTANCE, ProcessInstanceIntent.TERMINATE_ELEMENT),
            tuple(ValueType.PROCESS_INSTANCE, ProcessInstanceIntent.ELEMENT_TERMINATING),
            tuple(ValueType.USER_TASK, UserTaskIntent.CANCELING),
            tuple(ValueType.USER_TASK, UserTaskIntent.COMPLETE_TASK_LISTENER),
            tuple(ValueType.USER_TASK, UserTaskIntent.COMPLETE_TASK_LISTENER),
            tuple(ValueType.USER_TASK, UserTaskIntent.COMPLETE_TASK_LISTENER),
            tuple(ValueType.USER_TASK, UserTaskIntent.CANCELED),
            tuple(ValueType.PROCESS_INSTANCE, ProcessInstanceIntent.CONTINUE_TERMINATING_ELEMENT),
            tuple(ValueType.PROCESS_INSTANCE, ProcessInstanceIntent.ELEMENT_TERMINATED));

    // RecordingExporter.processInstanceRecords().withProcessInstanceKey()
  }

  @Test
  public void shouldCancelTaskListenerJobWhenTerminatingElementInstance() {
    // given
    final long processInstanceKey =
        createProcessInstance(
            createProcessWithAssigningTaskListeners(listenerType, listenerType + "_2"));

    ENGINE.userTask().ofInstance(processInstanceKey).withAssignee("samwise").assign();

    completeJobs(processInstanceKey, listenerType);

    final var listenerJob =
        jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withType(listenerType + "_2")
            .getFirst();

    // when
    ENGINE.processInstance().withInstanceKey(processInstanceKey).cancel();

    // then
    assertThat(jobRecords(JobIntent.CANCELED).withProcessInstanceKey(processInstanceKey).getFirst())
        .extracting(Record::getKey)
        .isEqualTo(listenerJob.getKey());
  }

  @Test
  public void shouldResolveTaskListenerIncidentWhenTerminatingElementInstance() {
    // given
    final long processInstanceKey =
        createProcessInstance(
            createProcessWithAssigningTaskListeners(listenerType, listenerType + "_2"));

    ENGINE.userTask().ofInstance(processInstanceKey).withAssignee("samwise").assign();

    completeJobs(processInstanceKey, listenerType);
    ENGINE.job().ofInstance(processInstanceKey).withType(listenerType + "_2").withRetries(0).fail();

    final var incident =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    // when
    ENGINE.processInstance().withInstanceKey(processInstanceKey).cancel();

    // then
    assertThat(
            RecordingExporter.incidentRecords()
                .withProcessInstanceKey(processInstanceKey)
                .withIntent(IncidentIntent.RESOLVED)
                .getFirst())
        .extracting(Record::getKey)
        .isEqualTo(incident.getKey());
  }

  @Test
  public void shouldRejectUserTaskAssignmentWhenTaskListenerDeniesTheTransition() {
    // given
    final long processInstanceKey =
        createProcessInstance(createProcessWithAssigningTaskListeners(listenerType));

    ENGINE.userTask().ofInstance(processInstanceKey).assign(ut -> ut.setAssignee("new_assignee"));
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(listenerType)
        .withResult(new JobResult().setDenied(true))
        .complete();

    // then: ensure that `DENY_TASK_LISTENER` and `ASSIGNMENT_DENIED`
    // are written after `ASSIGNING` event
    assertUserTaskIntentsSequence(
        UserTaskIntent.ASSIGNING,
        UserTaskIntent.DENY_TASK_LISTENER,
        UserTaskIntent.ASSIGNMENT_DENIED);

    // validate the assignee
    assertThat(
            RecordingExporter.userTaskRecords().withProcessInstanceKey(processInstanceKey).limit(1))
        .extracting(Record::getValue)
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
        createProcessInstance(
            createProcessWithAssigningTaskListeners(
                listenerType, listenerType + "_2", listenerType + "_3"));
    ENGINE.userTask().ofInstance(processInstanceKey).assign(ut -> ut.setAssignee("new_assignee"));
    // assignment fails
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(listenerType)
        .withResult(new JobResult().setDenied(true))
        .complete();

    ENGINE.userTask().ofInstance(processInstanceKey).assign(ut -> ut.setAssignee("new_assignee"));
    // assignment is successful
    completeRecreatedJobWithType(ENGINE, processInstanceKey, listenerType);
    completeJobs(processInstanceKey, listenerType + "_2", listenerType + "_3");

    // then: ensure that all three `COMPLETE_TASK_LISTENER` events were triggered
    // correct assignee value is present at all stages
    assertThat(
            RecordingExporter.userTaskRecords()
                .limit(r -> r.getIntent() == UserTaskIntent.ASSIGNED))
        .extracting(Record::getIntent, r -> r.getValue().getAssignee())
        .describedAs("Verify that all task listeners were completed with the correct assignee")
        .containsSequence(
            tuple(UserTaskIntent.ASSIGN, "new_assignee"),
            tuple(UserTaskIntent.ASSIGNING, "new_assignee"),
            tuple(UserTaskIntent.DENY_TASK_LISTENER, "new_assignee"),
            tuple(UserTaskIntent.ASSIGNMENT_DENIED, ""),
            tuple(UserTaskIntent.ASSIGN, "new_assignee"),
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
        createProcessInstance(
            createUserTaskWithTaskListeners(ZeebeTaskListenerEventType.updating, listenerType));

    // when: attempting to update the user task priority for the first time
    ENGINE
        .userTask()
        .ofInstance(processInstanceKey)
        .update(new UserTaskRecord().setPriority(80).setPriorityChanged());

    // and: task listener denies the first update attempt
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(listenerType)
        .withResult(new JobResult().setDenied(true))
        .complete();

    // when: retrying the update operation with a new priority value
    ENGINE
        .userTask()
        .ofInstance(processInstanceKey)
        .update(new UserTaskRecord().setPriority(100).setPriorityChanged());

    // and: completing the re-created task listener job
    completeRecreatedJobWithType(ENGINE, processInstanceKey, listenerType);

    // then
    assertThat(
            RecordingExporter.userTaskRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limit(r -> r.getIntent() == UserTaskIntent.UPDATED))
        .extracting(Record::getIntent, r -> r.getValue().getPriority())
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
        createProcessInstance(
            createUserTaskWithTaskListenersAndAssignee(listenerType, "first_assignee"));

    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(listenerType)
        .withResult(new JobResult().setDenied(false))
        .complete();

    ENGINE
        .userTask()
        .ofInstance(processInstanceKey)
        .assign(ut -> ut.setAssignee("second_assignee"));

    completeRecreatedJobWithTypeAndResult(
        ENGINE, processInstanceKey, listenerType, new JobResult().setDenied(true));

    assertThat(
            RecordingExporter.userTaskRecords()
                .limit(r -> r.getIntent() == UserTaskIntent.ASSIGNMENT_DENIED))
        .extracting(Record::getIntent, r -> r.getValue().getAssignee())
        .describedAs(
            "Verify that the assignee changes. The assignment of the second assignee should be rejected.")
        .containsSequence(
            tuple(UserTaskIntent.CREATING, ""),
            tuple(UserTaskIntent.CREATED, ""),
            tuple(UserTaskIntent.ASSIGNING, "first_assignee"),
            tuple(UserTaskIntent.COMPLETE_TASK_LISTENER, "first_assignee"),
            tuple(UserTaskIntent.ASSIGNED, "first_assignee"),
            tuple(UserTaskIntent.ASSIGN, "second_assignee"),
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
  public void shouldClaimUserTaskAfterAllAssignmentTaskListenersAreExecuted() {
    // given
    final long processInstanceKey =
        createProcessInstance(
            createUserTaskWithTaskListeners(
                ZeebeTaskListenerEventType.assigning, listenerType, listenerType + "_2"));

    // when
    ENGINE
        .userTask()
        .ofInstance(processInstanceKey)
        .withAssignee("test_user")
        .withAction("claim_action")
        .claim();
    completeJobs(processInstanceKey, listenerType, listenerType + "_2");

    // then
    assertTaskListenerJobsCompletionSequence(
        processInstanceKey, JobListenerEventType.ASSIGNING, listenerType, listenerType + "_2");

    // ensure that `COMPLETE_TASK_LISTENER` commands were triggered between
    // `CLAIMING` and `ASSIGNED` events
    assertUserTaskIntentsSequence(
        UserTaskIntent.CLAIM,
        UserTaskIntent.CLAIMING,
        UserTaskIntent.COMPLETE_TASK_LISTENER,
        UserTaskIntent.COMPLETE_TASK_LISTENER,
        UserTaskIntent.ASSIGNED);

    assertUserTaskRecordWithIntent(
        processInstanceKey,
        UserTaskIntent.ASSIGNED,
        userTask ->
            Assertions.assertThat(userTask)
                .hasAssignee("test_user")
                .hasAction("claim_action")
                .hasOnlyChangedAttributes(UserTaskRecord.ASSIGNEE));
  }

  @Test
  public void shouldTriggerAssignmentListenersAfterUserTaskCreationWithDefinedAssigneeProperty() {
    // given
    final var assignee = "peregrin";
    final var action = StringUtils.EMPTY;

    // when: process instance is created with a UT having an `assignee` and `assignment` listeners
    final long processInstanceKey =
        createProcessInstance(
            createProcessWithZeebeUserTask(
                task ->
                    task.zeebeAssignee(assignee)
                        .zeebeTaskListener(l -> l.assigning().type(listenerType))
                        .zeebeTaskListener(l -> l.assigning().type(listenerType + "_2"))
                        .zeebeTaskListener(l -> l.assigning().type(listenerType + "_3"))));

    // await user task creation
    RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .await();

    // when
    completeJobs(processInstanceKey, listenerType, listenerType + "_2", listenerType + "_3");

    // then: verify the task listener completion sequence for the assignment event
    assertTaskListenerJobsCompletionSequence(
        processInstanceKey,
        JobListenerEventType.ASSIGNING,
        listenerType,
        listenerType + "_2",
        listenerType + "_3");

    // verify that UT records follows the expected intents sequence from `CREATING` to `ASSIGNED`
    assertThat(
            RecordingExporter.userTaskRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limit(r -> r.getIntent() == UserTaskIntent.ASSIGNED))
        .as(
            "Verify the sequence of intents, `assignee`, `action` and `changedAttributes` properties emitted for the user task")
        .extracting(
            Record::getIntent,
            r -> r.getValue().getAssignee(),
            r -> r.getValue().getAction(),
            r -> r.getValue().getChangedAttributes())
        .containsExactly(
            tuple(UserTaskIntent.CREATING, StringUtils.EMPTY, action, List.of()),
            tuple(UserTaskIntent.CREATED, StringUtils.EMPTY, action, List.of()),
            tuple(UserTaskIntent.ASSIGNING, assignee, action, List.of(UserTaskRecord.ASSIGNEE)),
            tuple(UserTaskIntent.COMPLETE_TASK_LISTENER, assignee, action, List.of()),
            tuple(UserTaskIntent.COMPLETE_TASK_LISTENER, assignee, action, List.of()),
            tuple(UserTaskIntent.COMPLETE_TASK_LISTENER, assignee, action, List.of()),
            tuple(UserTaskIntent.ASSIGNED, assignee, action, List.of(UserTaskRecord.ASSIGNEE)));
  }

  @Test
  public void shouldRetryAssigningListenerWhenListenerJobFailedOnTaskAssignAfterCreation() {
    verifyListenerIsRetriedWhenListenerJobFailed(
        ZeebeTaskListenerEventType.assigning,
        userTask -> userTask.zeebeAssignee("gandalf"),
        userTaskClient -> {},
        UserTaskIntent.ASSIGNED);
  }

  @Test
  public void shouldRetryAssigningListenerWhenListenerJobFailedOnTaskAssign() {
    verifyListenerIsRetriedWhenListenerJobFailed(
        ZeebeTaskListenerEventType.assigning,
        UnaryOperator.identity(),
        userTaskClient -> userTaskClient.withAssignee("bilbo").assign(),
        UserTaskIntent.ASSIGNED);
  }

  @Test
  public void shouldRetryAssigningListenerWhenListenerJobFailedOnTaskClaim() {
    verifyListenerIsRetriedWhenListenerJobFailed(
        ZeebeTaskListenerEventType.assigning,
        UnaryOperator.identity(),
        userTaskClient -> userTaskClient.withAssignee("bilbo").claim(),
        UserTaskIntent.ASSIGNED);
  }

  @Test
  public void shouldRetryUpdatingListenerWhenListenerJobFailedOnTaskUpdate() {
    verifyListenerIsRetriedWhenListenerJobFailed(
        ZeebeTaskListenerEventType.updating,
        UnaryOperator.identity(),
        userTaskClient -> userTaskClient.update(new UserTaskRecord()),
        UserTaskIntent.UPDATED);
  }

  @Test
  public void shouldRetryCompletingListenerWhenListenerJobFailedOnTaskComplete() {
    verifyListenerIsRetriedWhenListenerJobFailed(
        ZeebeTaskListenerEventType.completing,
        UnaryOperator.identity(),
        UserTaskClient::complete,
        UserTaskIntent.COMPLETED);
  }

  @Test
  public void shouldRetryCancelingListenerWhenListenerJobFailedOnTaskCancellation() {
    verifyListenerIsRetriedWhenListenerJobFailed(
        ZeebeTaskListenerEventType.canceling,
        UnaryOperator.identity(),
        ignore -> cancelProcessInstance(),
        UserTaskIntent.CANCELED);
  }

  private void verifyListenerIsRetriedWhenListenerJobFailed(
      final ZeebeTaskListenerEventType eventType,
      final UnaryOperator<UserTaskBuilder> userTaskBuilder,
      final Consumer<UserTaskClient> userTaskAction,
      final UserTaskIntent terminalActionIntent) {
    // given
    final long processInstanceKey =
        createProcessInstance(
            createProcessWithZeebeUserTask(
                t ->
                    userTaskBuilder
                        .apply(t)
                        .zeebeTaskListener(l -> l.eventType(eventType).type(listenerType))
                        .zeebeTaskListener(l -> l.eventType(eventType).type(listenerType + "_2"))));

    // when: performing the user task action
    userTaskAction.accept(ENGINE.userTask().ofInstance(processInstanceKey));

    // when: fail listener job with retries
    ENGINE.job().ofInstance(processInstanceKey).withType(listenerType).withRetries(1).fail();
    // complete failed and remaining listeners job
    completeJobs(processInstanceKey, listenerType, listenerType + "_2");

    // then: assert the listener job was completed after the failure
    assertThat(records().limit(r -> r.getIntent() == terminalActionIntent))
        .extracting(Record::getValueType, Record::getIntent)
        .containsSubsequence(
            tuple(ValueType.JOB, JobIntent.CREATED),
            tuple(ValueType.JOB, JobIntent.FAILED),
            tuple(ValueType.JOB, JobIntent.COMPLETE),
            tuple(ValueType.JOB, JobIntent.COMPLETED),
            tuple(ValueType.USER_TASK, UserTaskIntent.COMPLETE_TASK_LISTENER),
            tuple(ValueType.JOB, JobIntent.CREATED),
            tuple(ValueType.JOB, JobIntent.COMPLETE),
            tuple(ValueType.JOB, JobIntent.COMPLETED),
            tuple(ValueType.USER_TASK, UserTaskIntent.COMPLETE_TASK_LISTENER),
            tuple(ValueType.USER_TASK, terminalActionIntent));
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
        userTaskClient -> userTaskClient.update(new UserTaskRecord()),
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

  @Test
  public void
      shouldCreateJobNoRetriesIncidentForCancelingListenerAndContinueAfterResolutionOnTaskCancellation() {
    verifyIncidentCreationOnListenerJobWithoutRetriesAndResolution(
        ZeebeTaskListenerEventType.canceling,
        UnaryOperator.identity(),
        ignore -> cancelProcessInstance(),
        UserTaskIntent.CANCELED);
  }

  private static void cancelProcessInstance() {
    final long processInstanceKey =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withElementType(BpmnElementType.PROCESS)
            .getFirst()
            .getKey();
    ENGINE.processInstance().withInstanceKey(processInstanceKey).expectTerminating().cancel();
  }

  private void verifyIncidentCreationOnListenerJobWithoutRetriesAndResolution(
      final ZeebeTaskListenerEventType eventType,
      final UnaryOperator<UserTaskBuilder> userTaskBuilder,
      final Consumer<UserTaskClient> userTaskAction,
      final UserTaskIntent terminalActionIntent) {

    // given
    final long processInstanceKey =
        createProcessInstance(
            createProcessWithZeebeUserTask(
                t ->
                    userTaskBuilder
                        .apply(t)
                        .zeebeTaskListener(l -> l.eventType(eventType).type(listenerType))
                        .zeebeTaskListener(l -> l.eventType(eventType).type(listenerType + "_2"))
                        .zeebeTaskListener(l -> l.eventType(eventType).type(listenerType + "_3"))));

    // when: performing the user task action
    userTaskAction.accept(ENGINE.userTask().ofInstance(processInstanceKey));

    // complete first listener job
    completeJobs(processInstanceKey, listenerType);

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
    completeJobs(processInstanceKey, listenerType + "_2", listenerType + "_3");

    // then
    assertThat(records().limit(r -> r.getIntent() == terminalActionIntent))
        .extracting(Record::getValueType, Record::getIntent)
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

  @Test
  public void shouldEvaluateExpressionsForTaskListeners() {
    final long processInstanceKey =
        createProcessInstanceWithVariables(
            createProcessWithZeebeUserTask(
                t ->
                    t.zeebeTaskListener(
                        l ->
                            l.completing()
                                .typeExpression("\"listener_1_\"+my_var")
                                .retriesExpression("5+3"))),
            Map.of("my_var", "abc"));

    // when
    ENGINE.userTask().ofInstance(processInstanceKey).complete();
    completeJobs(processInstanceKey, "listener_1_abc");

    // then
    assertThat(
            jobRecords()
                .withProcessInstanceKey(processInstanceKey)
                .withJobKind(JobKind.TASK_LISTENER)
                .withIntent(JobIntent.COMPLETED)
                .onlyEvents()
                .getFirst()
                .getValue())
        .satisfies(
            jobRecordValue -> {
              assertThat(jobRecordValue.getType()).isEqualTo("listener_1_abc");
              assertThat(jobRecordValue.getRetries()).isEqualTo(8);
            });
    assertThatProcessInstanceCompleted(processInstanceKey);
  }

  @Test
  @Ignore(
      "Ignored due to task listener job completion rejection when variables payload is provided (issue #24056). Re-enable after implementing issue #23702.")
  public void shouldMakeVariablesFromPreviousTaskListenersAvailableToSubsequentListeners() {
    final long processInstanceKey =
        createProcessInstance(
            createProcessWithCompletingTaskListeners(listenerType, listenerType + "_2"));

    ENGINE.userTask().ofInstance(processInstanceKey).complete();

    // when
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(listenerType)
        .withVariable("listener_1_var", "foo")
        .complete();

    // then: `listener_1_var` variable accessible in subsequent TL
    final var jobActivated = activateJob(processInstanceKey, listenerType + "_2");
    assertThat(jobActivated.getVariables()).contains(entry("listener_1_var", "foo"));
  }

  @Test
  @Ignore(
      "Ignored due to task listener job completion rejection when variables payload is provided (issue #24056). Re-enable after implementing issue #23702.")
  public void shouldNotExposeTaskListenerVariablesOutsideUserTaskScope() {
    // given: deploy a process with a user task having complete TL and service task following it
    final long processInstanceKey =
        createProcessInstance(
            createProcess(
                p ->
                    p.userTask(
                            USER_TASK_ELEMENT_ID,
                            t ->
                                t.zeebeUserTask()
                                    .zeebeAssignee("foo")
                                    .zeebeTaskListener(l -> l.completing().type(listenerType)))
                        .serviceTask(
                            "subsequent_service_task",
                            tb -> tb.zeebeJobType("subsequent_service_task"))));

    ENGINE.userTask().ofInstance(processInstanceKey).complete();

    // when: complete TL job with a variable 'my_listener_var'
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(listenerType)
        .withVariable("my_listener_var", "bar")
        .complete();

    // then: assert the variable 'my_listener_var' isn't accessible in the subsequent element
    final var subsequentServiceTaskJob = activateJob(processInstanceKey, "subsequent_service_task");
    assertThat(subsequentServiceTaskJob.getVariables()).doesNotContainKey("my_listener_var");
    completeJobs(processInstanceKey, "subsequent_service_task");
  }

  @Test
  @Ignore(
      "Ignored due to task listener job completion rejection when variables payload is provided (issue #24056). Re-enable after implementing issue #23702.")
  public void shouldAllowTaskListenerVariablesInUserTaskOutputMappings() {
    // given: deploy a process with a user task having complete TL and service task following it
    final long processInstanceKey =
        createProcessInstance(
            createProcess(
                p ->
                    p.userTask(
                            USER_TASK_ELEMENT_ID,
                            t ->
                                t.zeebeUserTask()
                                    .zeebeAssignee("foo")
                                    .zeebeTaskListener(l -> l.completing().type(listenerType))
                                    .zeebeOutput("=my_listener_var+\"_abc\"", "userTaskOutput"))
                        .serviceTask(
                            "subsequent_service_task",
                            tb -> tb.zeebeJobType("subsequent_service_task"))));

    ENGINE.userTask().ofInstance(processInstanceKey).complete();

    // when: complete TL job with a variable 'my_listener_var'
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(listenerType)
        .withVariable("my_listener_var", "bar")
        .complete();

    // then: assert the variable 'userTaskOutput' is accessible in the subsequent element
    final var subsequentServiceTaskJob = activateJob(processInstanceKey, "subsequent_service_task");
    assertThat(subsequentServiceTaskJob.getVariables()).containsEntry("userTaskOutput", "bar_abc");
    completeJobs(processInstanceKey, "subsequent_service_task");
  }

  @Test
  public void shouldIncludeConfiguredUserTaskDataInCompleteTaskListenerJobHeaders() {
    final var form = deployForm("/form/test-form-1.form");
    final long processInstanceKey =
        createProcessInstance(
            createProcessWithZeebeUserTask(
                t ->
                    t.zeebeAssignee("admin")
                        .zeebeCandidateUsers("user_A, user_B")
                        .zeebeCandidateGroups("group_A, group_C, group_F")
                        .zeebeFormId("Form_0w7r08e")
                        .zeebeDueDate("2095-09-18T10:31:10+02:00")
                        .zeebeTaskPriority("88")
                        .zeebeTaskListener(l -> l.completing().type(listenerType))));

    // when
    final var userTaskCommand = ENGINE.userTask().ofInstance(processInstanceKey).complete();

    // then
    final var activatedListenerJob = activateJob(processInstanceKey, listenerType);

    assertThat(activatedListenerJob.getCustomHeaders())
        .containsOnly(
            entry(
                Protocol.USER_TASK_CANDIDATE_GROUPS_HEADER_NAME,
                "[\"group_A\",\"group_C\",\"group_F\"]"),
            entry(Protocol.USER_TASK_CANDIDATE_USERS_HEADER_NAME, "[\"user_A\",\"user_B\"]"),
            entry(Protocol.USER_TASK_ASSIGNEE_HEADER_NAME, "admin"),
            entry(Protocol.USER_TASK_DUE_DATE_HEADER_NAME, "2095-09-18T10:31:10+02:00"),
            entry(Protocol.USER_TASK_FORM_KEY_HEADER_NAME, Objects.toString(form.getFormKey())),
            entry(Protocol.USER_TASK_KEY_HEADER_NAME, String.valueOf(userTaskCommand.getKey())),
            entry(Protocol.USER_TASK_PRIORITY_HEADER_NAME, "88"),
            entry(Protocol.USER_TASK_ACTION_HEADER_NAME, "complete"));
    completeJobs(processInstanceKey, listenerType);
  }

  @Test
  public void shouldUseUpdatedUserTaskDataInCompleteTaskListenerJobHeadersAfterTaskUpdate() {
    final long processInstanceKey =
        createProcessInstance(
            createProcessWithZeebeUserTask(
                t ->
                    t.zeebeAssignee("admin")
                        .zeebeCandidateUsers("user_A, user_B")
                        .zeebeCandidateGroups("group_A, group_C, group_F")
                        .zeebeDueDate("2085-09-21T11:22:33+02:00")
                        .zeebeFollowUpDate("2095-09-21T11:22:33+02:00")
                        .zeebeTaskListener(l -> l.completing().type(listenerType))));

    final var changes =
        new UserTaskRecord()
            .setCandidateGroupsList(List.of("group_J", "group_R"))
            .setCandidateUsersList(List.of("user_T"))
            .setDueDate("2087-09-21T11:22:33+02:00")
            .setFollowUpDate("2097-09-21T11:22:33+02:00")
            .setPriority(42);

    // when
    ENGINE.userTask().ofInstance(processInstanceKey).update(changes);
    final var userTaskCommand = ENGINE.userTask().ofInstance(processInstanceKey).complete();

    // then
    final var activatedListenerJob = activateJob(processInstanceKey, listenerType);
    assertThat(activatedListenerJob.getCustomHeaders())
        .containsOnly(
            entry(Protocol.USER_TASK_CANDIDATE_GROUPS_HEADER_NAME, "[\"group_J\",\"group_R\"]"),
            entry(Protocol.USER_TASK_CANDIDATE_USERS_HEADER_NAME, "[\"user_T\"]"),
            entry(Protocol.USER_TASK_ASSIGNEE_HEADER_NAME, "admin"),
            entry(Protocol.USER_TASK_DUE_DATE_HEADER_NAME, "2087-09-21T11:22:33+02:00"),
            entry(Protocol.USER_TASK_FOLLOW_UP_DATE_HEADER_NAME, "2097-09-21T11:22:33+02:00"),
            entry(Protocol.USER_TASK_KEY_HEADER_NAME, String.valueOf(userTaskCommand.getKey())),
            entry(Protocol.USER_TASK_PRIORITY_HEADER_NAME, "42"),
            entry(Protocol.USER_TASK_ACTION_HEADER_NAME, "complete"));
    completeJobs(processInstanceKey, listenerType);
  }

  @Test
  public void
      shouldNotIncludeEmptyOrClearedUserTaskPropertiesInCompletingListenerHeadersAfterTaskWasUpdated() {
    // given: a process instance with a user task configured with an initial assignee, candidate
    // users/groups, due/follow-up dates, and a `completing` listener
    final long processInstanceKey =
        createProcessInstance(
            createProcessWithZeebeUserTask(
                userTask ->
                    userTask
                        .zeebeAssignee("admin")
                        .zeebeCandidateUsers("user_A, user_B")
                        .zeebeCandidateGroups("group_A, group_C, group_F")
                        .zeebeDueDate("2085-09-21T11:22:33+02:00")
                        .zeebeFollowUpDate("2095-09-21T11:22:33+02:00")
                        .zeebeTaskListener(listener -> listener.completing().type(listenerType))));

    final var changes =
        new UserTaskRecord()
            // Clear candidate groups and users, due date, and follow-up date
            .setCandidateGroupsList(List.of())
            .setCandidateUsersList(List.of())
            .setDueDate("")
            .setFollowUpDate("")
            // Update priority
            .setPriority(1);

    // when: updating the user task with the specified changes
    ENGINE.userTask().ofInstance(processInstanceKey).update(changes);

    // and: completing the user task
    final var userTaskCommand = ENGINE.userTask().ofInstance(processInstanceKey).complete();

    // then: validate the headers of the completing listener job triggered after the task update
    final var activatedListenerJob = activateJob(processInstanceKey, listenerType);

    assertThat(activatedListenerJob.getCustomHeaders())
        .describedAs(
            "Headers should include only the configured, updated or automatically set user task properties")
        .containsOnly(
            // Assignee remains unchanged
            entry(Protocol.USER_TASK_ASSIGNEE_HEADER_NAME, "admin"),
            // Task key is always included
            entry(Protocol.USER_TASK_KEY_HEADER_NAME, String.valueOf(userTaskCommand.getKey())),
            // Default action value for the completing operation
            entry(Protocol.USER_TASK_ACTION_HEADER_NAME, "complete"),
            // Updated priority
            entry(Protocol.USER_TASK_PRIORITY_HEADER_NAME, "1"))
        .describedAs(
            "Headers should not include not configured or cleared properties such as candidate groups, candidate users, due date, and follow-up date")
        .doesNotContainKeys(
            Protocol.USER_TASK_CANDIDATE_GROUPS_HEADER_NAME,
            Protocol.USER_TASK_CANDIDATE_USERS_HEADER_NAME,
            Protocol.USER_TASK_DUE_DATE_HEADER_NAME,
            Protocol.USER_TASK_FOLLOW_UP_DATE_HEADER_NAME);
  }

  @Test
  public void
      shouldNotIncludeEmptyOrClearedUserTaskPropertiesInAssigningListenerHeadersAfterTriggeringTaskUnassignment() {
    // given: a process instance with a user task configured with an initial assignee and an
    // `assigning` listener
    final long processInstanceKey =
        createProcessInstance(
            createProcessWithZeebeUserTask(
                userTask ->
                    userTask
                        .zeebeAssignee("initial_assignee")
                        .zeebeTaskListener(listener -> listener.assigning().type(listenerType))));

    final var createdUserTaskRecord =
        RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    final var assigningListenerJob = activateJob(processInstanceKey, listenerType);

    // assert the headers of the assigning listener job triggered after the user task creation
    final var userTaskKey = String.valueOf(createdUserTaskRecord.getKey());
    assertThat(assigningListenerJob.getCustomHeaders())
        .describedAs("Headers should not contain empty or non-configured properties")
        .doesNotContainKeys(
            Protocol.USER_TASK_ACTION_HEADER_NAME,
            Protocol.USER_TASK_CANDIDATE_GROUPS_HEADER_NAME,
            Protocol.USER_TASK_CANDIDATE_USERS_HEADER_NAME,
            Protocol.USER_TASK_DUE_DATE_HEADER_NAME,
            Protocol.USER_TASK_FOLLOW_UP_DATE_HEADER_NAME)
        .describedAs(
            "Headers should include only configured, default or automatically set user task properties")
        .containsOnly(
            // Task key is always included
            entry(Protocol.USER_TASK_KEY_HEADER_NAME, userTaskKey),
            // Assignee should match the initial value
            entry(Protocol.USER_TASK_ASSIGNEE_HEADER_NAME, "initial_assignee"),
            // Default priority is propagated
            entry(Protocol.USER_TASK_PRIORITY_HEADER_NAME, "50"));

    // Complete the assigning listener job triggered after the user task creation
    completeJobs(processInstanceKey, listenerType);

    // when: unassigning the user task
    ENGINE.userTask().ofInstance(processInstanceKey).unassign();

    // then
    final var unassigningListenerJob = activateJob(processInstanceKey, listenerType);
    assertThat(unassigningListenerJob.getCustomHeaders())
        .describedAs(
            "Headers should not include the 'assignee' property, as it is cleared during unassignment")
        .doesNotContainKeys(Protocol.USER_TASK_ASSIGNEE_HEADER_NAME)
        .describedAs("Headers should not contain other empty or non-configured properties")
        .doesNotContainKeys(
            Protocol.USER_TASK_CANDIDATE_GROUPS_HEADER_NAME,
            Protocol.USER_TASK_CANDIDATE_USERS_HEADER_NAME,
            Protocol.USER_TASK_DUE_DATE_HEADER_NAME,
            Protocol.USER_TASK_FOLLOW_UP_DATE_HEADER_NAME)
        .describedAs(
            "Headers should include only configured, default or automatically set user task properties")
        .containsOnly(
            // Task key is always included
            entry(Protocol.USER_TASK_KEY_HEADER_NAME, userTaskKey),
            // Priority remains unchanged
            entry(Protocol.USER_TASK_PRIORITY_HEADER_NAME, "50"),
            // Action should reflect the unassign operation
            entry(Protocol.USER_TASK_ACTION_HEADER_NAME, "unassign"));
  }

  @Test
  public void shouldProvideVariablesOfTaskCompletionToCompleteTaskListener() {
    // given
    final var processInstanceKey =
        createProcessInstanceWithVariables(
            createProcessWithCompletingTaskListeners(listenerType), Map.of("foo", "bar"));

    // when
    ENGINE.userTask().ofInstance(processInstanceKey).withVariables(Map.of("baz", 123)).complete();

    // then
    assertThat(ENGINE.jobs().withType(listenerType).activate().getValue().getJobs())
        .describedAs(
            "Expect that both the process variables and the completion variables are provided to the job")
        .allSatisfy(
            job ->
                assertThat(job.getVariables())
                    .containsExactly(Map.entry("foo", "bar"), Map.entry("baz", 123)));
  }

  @Test
  public void shouldProvideVariablesOfTaskCompletionShadowingProcessVariables() {
    // given
    final var processInstanceKey =
        createProcessInstanceWithVariables(
            createProcessWithCompletingTaskListeners(listenerType), Map.of("foo", "bar"));

    // when
    ENGINE
        .userTask()
        .ofInstance(processInstanceKey)
        .withVariables(Map.of("foo", "overwritten"))
        .complete();

    // then
    assertThat(ENGINE.jobs().withType(listenerType).activate().getValue().getJobs())
        .describedAs(
            "Expect that both the process variables and the completion variables are provided to the job")
        .allSatisfy(
            job -> assertThat(job.getVariables()).containsExactly(Map.entry("foo", "overwritten")));
  }

  @Test
  public void shouldProvideVariablesOfTaskCompletionFetchingOnlySpecifiedVariables() {
    // given
    final var processInstanceKey =
        createProcessInstanceWithVariables(
            createProcessWithCompletingTaskListeners(listenerType),
            Map.ofEntries(Map.entry("foo", "bar"), Map.entry("bar", 123)));

    // when
    ENGINE
        .userTask()
        .ofInstance(processInstanceKey)
        .withVariables(Map.ofEntries(Map.entry("foo", "overwritten"), Map.entry("bar", 456)))
        .complete();

    // then
    assertThat(
            ENGINE
                .jobs()
                .withType(listenerType)
                .withFetchVariables("foo")
                .activate()
                .getValue()
                .getJobs())
        .describedAs("Expect that only the specified variable foo is provided to the job")
        .allSatisfy(
            job -> assertThat(job.getVariables()).containsExactly(Map.entry("foo", "overwritten")));
  }

  @Test
  public void shouldRejectCompleteTaskListenerJobCompletionWhenVariablesAreSet() {
    // given
    final long processInstanceKey =
        createProcessInstance(createProcessWithCompletingTaskListeners(listenerType));

    ENGINE.userTask().ofInstance(processInstanceKey).complete();

    // when: try to complete TL job with a variable payload
    final var result =
        ENGINE
            .job()
            .ofInstance(processInstanceKey)
            .withType(listenerType)
            .withVariable("my_listener_var", "foo")
            .complete();

    Assertions.assertThat(result)
        .describedAs(
            "Task Listener job completion should be rejected when variable payload provided")
        .hasIntent(JobIntent.COMPLETE)
        .hasRejectionType(RejectionType.INVALID_ARGUMENT)
        .hasRejectionReason(
            TL_JOB_COMPLETION_WITH_VARS_NOT_SUPPORTED_MESSAGE.formatted(
                result.getKey(), listenerType, processInstanceKey));

    // complete the listener job without variables to have a completed process
    // and prevent flakiness in other tests
    ENGINE.job().ofInstance(processInstanceKey).withType(listenerType).complete();
  }

  @Test
  public void shouldCompleteTaskWithTaskListenerWhenJobResultDeniedIsFalse() {
    // given
    final long processInstanceKey =
        createProcessInstance(
            createProcessWithCompletingTaskListeners(
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
        createProcessInstance(
            createUserTaskWithTaskListeners(ZeebeTaskListenerEventType.updating, listenerType));

    ENGINE
        .userTask()
        .ofInstance(processInstanceKey)
        .update(new UserTaskRecord().setPriority(99).setPriorityChanged());

    // when: complete `updating` a listener job with a denied result
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(listenerType)
        .withResult(new JobResult().setDenied(true))
        .complete();

    // then: ensure that `DENY_TASK_LISTENER` and `UPDATE_DENIED`
    // are written right after `UPDATING` event
    assertUserTaskIntentsSequence(
        UserTaskIntent.UPDATING, UserTaskIntent.DENY_TASK_LISTENER, UserTaskIntent.UPDATE_DENIED);
  }

  @Test
  public void shouldRejectUserTaskCompletionWhenCompletingTaskListenerDeniesTheTransition() {
    // given
    final long processInstanceKey =
        createProcessInstance(createProcessWithCompletingTaskListeners(listenerType));

    ENGINE.userTask().ofInstance(processInstanceKey).complete();
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(listenerType)
        .withResult(new JobResult().setDenied(true))
        .complete();

    // then: ensure that `DENY_TASK_LISTENER` and `COMPLETION_DENIED`
    // are written after `COMPLETING` event
    assertUserTaskIntentsSequence(
        UserTaskIntent.COMPLETING,
        UserTaskIntent.DENY_TASK_LISTENER,
        UserTaskIntent.COMPLETION_DENIED);
  }

  @Test
  public void shouldCompleteTaskWhenCompletingTaskListenerAcceptsTransitionAfterDenial() {
    // given
    final long processInstanceKey =
        createProcessInstance(createProcessWithCompletingTaskListeners(listenerType));

    ENGINE.userTask().ofInstance(processInstanceKey).complete();
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(listenerType)
        .withResult(new JobResult().setDenied(true))
        .complete();

    ENGINE.userTask().ofInstance(processInstanceKey).complete();

    completeRecreatedJobWithType(ENGINE, processInstanceKey, listenerType);

    // then: ensure that `COMPLETING` `COMPLETE_TASK_LISTENER` and `COMPLETED events
    // are present after `DENY_TASK_LISTENER` and `COMPLETION_DENIED` events
    assertUserTaskIntentsSequence(
        UserTaskIntent.COMPLETING,
        UserTaskIntent.DENY_TASK_LISTENER,
        UserTaskIntent.COMPLETION_DENIED,
        UserTaskIntent.COMPLETE,
        UserTaskIntent.COMPLETING,
        UserTaskIntent.COMPLETE_TASK_LISTENER,
        UserTaskIntent.COMPLETED);
  }

  @Test
  public void shouldCompleteAllTaskListenersWhenFirstTaskListenerAcceptTransitionAfterDenial() {
    // given
    final long processInstanceKey =
        createProcessInstance(
            createProcessWithCompletingTaskListeners(
                listenerType, listenerType + "_2", listenerType + "_3"));

    ENGINE.userTask().ofInstance(processInstanceKey).complete();
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(listenerType)
        .withResult(new JobResult().setDenied(true))
        .complete();

    ENGINE.userTask().ofInstance(processInstanceKey).complete();
    completeRecreatedJobWithType(ENGINE, processInstanceKey, listenerType);
    completeJobs(processInstanceKey, listenerType + "_2", listenerType + "_3");

    // then: ensure that all three `COMPLETE_TASK_LISTENER` events were triggered after the
    // rejection from the first Task Listener
    assertUserTaskIntentsSequence(
        UserTaskIntent.COMPLETING,
        UserTaskIntent.DENY_TASK_LISTENER,
        UserTaskIntent.COMPLETION_DENIED,
        UserTaskIntent.COMPLETE,
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
        createProcessInstance(createProcessWithCompletingTaskListeners(listenerType));

    ENGINE.userTask().ofInstance(processInstanceKey).complete();
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(listenerType)
        .withResult(new JobResult().setDenied(true))
        .complete();

    ENGINE.userTask().ofInstance(processInstanceKey).withAssignee("Test Assignee").assign();
    ENGINE.userTask().ofInstance(processInstanceKey).complete();

    completeRecreatedJobWithType(ENGINE, processInstanceKey, listenerType);

    // then: ensure that user task could be assigned after completion was rejected from the
    // `COMPLETE` Task Listener. Ensure that user task could be completed after assignment
    // and `COMPLETE_TASK_LISTENER` event was triggered successfully
    assertUserTaskIntentsSequence(
        UserTaskIntent.COMPLETE,
        UserTaskIntent.COMPLETING,
        UserTaskIntent.DENY_TASK_LISTENER,
        UserTaskIntent.COMPLETION_DENIED,
        UserTaskIntent.ASSIGN,
        UserTaskIntent.ASSIGNING,
        UserTaskIntent.ASSIGNED,
        UserTaskIntent.COMPLETE,
        UserTaskIntent.COMPLETING,
        UserTaskIntent.COMPLETE_TASK_LISTENER,
        UserTaskIntent.COMPLETED);
  }

  @Test
  public void
      shouldAppendUserTaskCorrectedWhenAssigningOnCreationTaskListenerCompletesWithCorrections() {
    testAppendUserTaskCorrectedWhenTaskListenerCompletesWithCorrections(
        ZeebeTaskListenerEventType.assigning,
        u -> u.zeebeAssignee("initial_assignee"),
        userTask -> {},
        "");
  }

  @Test
  public void shouldAppendUserTaskCorrectedWhenAssigningTaskListenerCompletesWithCorrections() {
    testAppendUserTaskCorrectedWhenTaskListenerCompletesWithCorrections(
        ZeebeTaskListenerEventType.assigning,
        u -> u,
        userTask -> userTask.withAssignee("initial_assignee").assign(),
        "assign");
  }

  @Test
  public void shouldAppendUserTaskCorrectedWhenClaimingTaskListenerCompletesWithCorrections() {
    testAppendUserTaskCorrectedWhenTaskListenerCompletesWithCorrections(
        ZeebeTaskListenerEventType.assigning,
        u -> u,
        userTask -> userTask.withAssignee("initial_assignee").claim(),
        "claim");
  }

  @Test
  public void shouldAppendUserTaskCorrectedWhenUpdatingTaskListenerCompletesWithCorrections() {
    testAppendUserTaskCorrectedWhenTaskListenerCompletesWithCorrections(
        ZeebeTaskListenerEventType.updating,
        u -> u,
        userTask -> userTask.update(new UserTaskRecord()),
        "update");
  }

  @Test
  public void shouldAppendUserTaskCorrectedWhenCompletingTaskListenerCompletesWithCorrections() {
    testAppendUserTaskCorrectedWhenTaskListenerCompletesWithCorrections(
        ZeebeTaskListenerEventType.completing, u -> u, UserTaskClient::complete, "complete");
  }

  @Test
  public void shouldAppendUserTaskCorrectedWhenCancelingTaskListenerCompletesWithCorrections() {
    testAppendUserTaskCorrectedWhenTaskListenerCompletesWithCorrections(
        ZeebeTaskListenerEventType.canceling, u -> u, ignore -> cancelProcessInstance(), "");
  }

  private void testAppendUserTaskCorrectedWhenTaskListenerCompletesWithCorrections(
      final ZeebeTaskListenerEventType eventType,
      final UnaryOperator<UserTaskBuilder> userTaskBuilder,
      final Consumer<UserTaskClient> userTaskAction,
      final String expectedAction) {
    // given
    final long processInstanceKey =
        createProcessInstance(
            createProcessWithZeebeUserTask(
                t ->
                    userTaskBuilder
                        .apply(t)
                        .zeebeTaskListener(l -> l.eventType(eventType).type(listenerType))
                        .zeebeTaskListener(l -> l.eventType(eventType).type(listenerType + "_2"))));

    final var userTaskRecord =
        RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();
    final var userTask = userTaskRecord.getValue();
    userTaskAction.accept(ENGINE.userTask().withKey(userTaskRecord.getKey()));

    // when
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(listenerType)
        .withResult(
            new JobResult()
                .setCorrections(
                    new JobResultCorrections()
                        .setAssignee("new_assignee")
                        .setCandidateUsersList(List.of("new_candidate_user"))
                        .setCandidateGroupsList(List.of("new_candidate_group"))
                        .setDueDate("new_due_date")
                        .setFollowUpDate("new_follow_up_date")
                        .setPriority(100))
                .setCorrectedAttributes(
                    List.of(
                        "assignee",
                        "candidateUsersList",
                        "candidateGroupsList",
                        "dueDate",
                        "followUpDate",
                        "priority")))
        .complete();

    // then
    Assertions.assertThat(
            RecordingExporter.userTaskRecords(UserTaskIntent.CORRECTED)
                .withProcessInstanceKey(processInstanceKey)
                .getFirst()
                .getValue())
        .hasChangedAttributes(
            "assignee",
            "candidateUsersList",
            "candidateGroupsList",
            "dueDate",
            "followUpDate",
            "priority")
        .hasAssignee("new_assignee")
        .hasCandidateUsersList(List.of("new_candidate_user"))
        .hasCandidateGroupsList(List.of("new_candidate_group"))
        .hasDueDate("new_due_date")
        .hasFollowUpDate("new_follow_up_date")
        .hasPriority(100)
        .describedAs("Expect that the action references the listened to action")
        .hasAction(expectedAction)
        .describedAs("Expect that the other data is also filled but remains unchanged")
        .hasBpmnProcessId(userTask.getBpmnProcessId())
        .hasCreationTimestamp(userTask.getCreationTimestamp())
        .hasElementId(userTask.getElementId())
        .hasElementInstanceKey(userTask.getElementInstanceKey())
        .hasExternalFormReference(userTask.getExternalFormReference())
        .hasFormKey(userTask.getFormKey())
        .hasProcessDefinitionKey(userTask.getProcessDefinitionKey())
        .hasProcessInstanceKey(userTask.getProcessInstanceKey())
        .hasVariables(userTask.getVariables())
        .hasTenantId(userTask.getTenantId())
        .hasUserTaskKey(userTask.getUserTaskKey());

    // when
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(listenerType + "_2")
        .withResult(
            new JobResult()
                .setCorrections(new JobResultCorrections().setPriority(3))
                .setCorrectedAttributes(List.of("priority")))
        .complete();

    // then
    Assertions.assertThat(
            RecordingExporter.userTaskRecords(UserTaskIntent.CORRECTED)
                .withProcessInstanceKey(processInstanceKey)
                .skip(1)
                .getFirst()
                .getValue())
        .describedAs("Expect only the corrected attributes are mentioned")
        .hasChangedAttributes("priority")
        .describedAs("Expect that the corrected attribute is updated")
        .hasPriority(3)
        .describedAs("Expect that the other corrected data remains unchanged")
        .hasAssignee("new_assignee")
        .hasCandidateUsersList(List.of("new_candidate_user"))
        .hasCandidateGroupsList(List.of("new_candidate_group"))
        .hasDueDate("new_due_date")
        .hasFollowUpDate("new_follow_up_date")
        .describedAs("Expect that the action references the listened to action")
        .hasAction(expectedAction)
        .describedAs("Expect that the other data is also filled but remains unchanged")
        .hasBpmnProcessId(userTask.getBpmnProcessId())
        .hasCreationTimestamp(userTask.getCreationTimestamp())
        .hasElementId(userTask.getElementId())
        .hasElementInstanceKey(userTask.getElementInstanceKey())
        .hasExternalFormReference(userTask.getExternalFormReference())
        .hasFormKey(userTask.getFormKey())
        .hasProcessDefinitionKey(userTask.getProcessDefinitionKey())
        .hasProcessInstanceKey(userTask.getProcessInstanceKey())
        .hasVariables(userTask.getVariables())
        .hasTenantId(userTask.getTenantId())
        .hasUserTaskKey(userTask.getUserTaskKey());
  }

  @Test
  public void shouldPropagateCorrectedDataToAssigningListenerJobHeadersOnTaskCreation() {
    verifyUserTaskDataPropagationAcrossListenerJobHeaders(
        ZeebeTaskListenerEventType.assigning,
        true,
        userTask -> {},
        List.of(
            UserTaskIntent.CREATING,
            UserTaskIntent.CREATED,
            UserTaskIntent.ASSIGNING,
            UserTaskIntent.COMPLETE_TASK_LISTENER,
            UserTaskIntent.CORRECTED,
            UserTaskIntent.COMPLETE_TASK_LISTENER,
            UserTaskIntent.CORRECTED,
            UserTaskIntent.COMPLETE_TASK_LISTENER,
            UserTaskIntent.ASSIGNED));
  }

  @Test
  public void shouldPropagateCorrectedDataToAssigningListenerJobHeadersOnTaskAssignment() {
    verifyUserTaskDataPropagationAcrossListenerJobHeaders(
        ZeebeTaskListenerEventType.assigning,
        false,
        userTask -> userTask.withAssignee("initial_assignee").assign(),
        List.of(
            UserTaskIntent.ASSIGN,
            UserTaskIntent.ASSIGNING,
            UserTaskIntent.COMPLETE_TASK_LISTENER,
            UserTaskIntent.CORRECTED,
            UserTaskIntent.COMPLETE_TASK_LISTENER,
            UserTaskIntent.CORRECTED,
            UserTaskIntent.COMPLETE_TASK_LISTENER,
            UserTaskIntent.ASSIGNED));
  }

  @Test
  public void shouldPropagateCorrectedDataToAssigningListenerJobHeadersOnTaskClaiming() {
    verifyUserTaskDataPropagationAcrossListenerJobHeaders(
        ZeebeTaskListenerEventType.assigning,
        false,
        userTask -> userTask.withAssignee("initial_assignee").claim(),
        List.of(
            UserTaskIntent.CLAIM,
            UserTaskIntent.CLAIMING,
            UserTaskIntent.COMPLETE_TASK_LISTENER,
            UserTaskIntent.CORRECTED,
            UserTaskIntent.COMPLETE_TASK_LISTENER,
            UserTaskIntent.CORRECTED,
            UserTaskIntent.COMPLETE_TASK_LISTENER,
            UserTaskIntent.ASSIGNED));
  }

  @Test
  public void shouldPropagateCorrectedDataToUpdatingListenerJobHeaders() {
    verifyUserTaskDataPropagationAcrossListenerJobHeaders(
        ZeebeTaskListenerEventType.updating,
        false,
        userTask ->
            userTask.update(
                new UserTaskRecord()
                    .setCandidateUsersList(List.of("initial_candidate_user"))
                    .setCandidateGroupsList(List.of("initial_candidate_group"))
                    .setDueDate("2085-09-21T11:22:33+02:00")
                    .setFollowUpDate("2095-09-21T11:22:33+02:00")),
        List.of(
            UserTaskIntent.UPDATE,
            UserTaskIntent.UPDATING,
            UserTaskIntent.COMPLETE_TASK_LISTENER,
            UserTaskIntent.CORRECTED,
            UserTaskIntent.COMPLETE_TASK_LISTENER,
            UserTaskIntent.CORRECTED,
            UserTaskIntent.COMPLETE_TASK_LISTENER,
            UserTaskIntent.UPDATED));
  }

  @Test
  public void shouldPropagateCorrectedDataToCompletingListenerJobHeadersOnTaskCompletion() {
    verifyUserTaskDataPropagationAcrossListenerJobHeaders(
        ZeebeTaskListenerEventType.completing,
        false,
        UserTaskClient::complete,
        List.of(
            UserTaskIntent.COMPLETE,
            UserTaskIntent.COMPLETING,
            UserTaskIntent.COMPLETE_TASK_LISTENER,
            UserTaskIntent.CORRECTED,
            UserTaskIntent.COMPLETE_TASK_LISTENER,
            UserTaskIntent.CORRECTED,
            UserTaskIntent.COMPLETE_TASK_LISTENER,
            UserTaskIntent.COMPLETED));
  }

  @Test
  public void shouldPropagateCorrectedDataToCancelingListenerJobHeadersOnTaskCancellation() {
    verifyUserTaskDataPropagationAcrossListenerJobHeaders(
        ZeebeTaskListenerEventType.canceling,
        false,
        ignore -> cancelProcessInstance(),
        List.of(
            UserTaskIntent.CANCELING,
            UserTaskIntent.COMPLETE_TASK_LISTENER,
            UserTaskIntent.CORRECTED,
            UserTaskIntent.COMPLETE_TASK_LISTENER,
            UserTaskIntent.CORRECTED,
            UserTaskIntent.COMPLETE_TASK_LISTENER,
            UserTaskIntent.CANCELED));
  }

  /**
   * Verifies the propagation of user task data across listener job headers during the task
   * lifecycle.
   *
   * <p>The method validates the following scenarios:
   *
   * <ul>
   *   <li>The headers of the first listener reflect the initial state of the user task, including
   *       configured or default properties.
   *   <li>After corrections are applied to the user task during completion of the first listener
   *       job, the headers of the subsequent listener should reflect the corrected properties.
   *   <li>After user task properties are cleared using corrections during completion of the second
   *       listener job, the headers of the third listener no longer include those cleared
   *       properties.
   *   <li>The entire sequence of user task lifecycle intents aligns with the expected order.
   * </ul>
   *
   * @param eventType the event type of the user task listener
   * @param isAssigneeConfiguredOnTaskCreation whether the assignee is configured during task
   *     creation
   * @param userTaskAction the user task action to trigger the listener (e.g., assign, claim,
   *     complete)
   * @param expectedUserTaskIntents the expected sequence of user task intents for the test
   */
  private void verifyUserTaskDataPropagationAcrossListenerJobHeaders(
      final ZeebeTaskListenerEventType eventType,
      final boolean isAssigneeConfiguredOnTaskCreation,
      final Consumer<UserTaskClient> userTaskAction,
      final List<UserTaskIntent> expectedUserTaskIntents) {

    // given: a process instance with a user task configured with listeners and initial properties
    final long processInstanceKey =
        createProcessInstance(
            createProcessWithZeebeUserTask(
                userTask -> {
                  if (isAssigneeConfiguredOnTaskCreation) {
                    userTask.zeebeAssignee("initial_assignee");
                  }
                  return userTask
                      .zeebeCandidateUsers("initial_candidate_user")
                      .zeebeCandidateGroups("initial_candidate_group")
                      .zeebeDueDate("2085-09-21T11:22:33+02:00")
                      .zeebeFollowUpDate("2095-09-21T11:22:33+02:00")
                      .zeebeTaskListener(l -> l.eventType(eventType).type(listenerType))
                      .zeebeTaskListener(l -> l.eventType(eventType).type(listenerType + "_2"))
                      .zeebeTaskListener(l -> l.eventType(eventType).type(listenerType + "_3"));
                }));

    final var createdUserTaskRecord =
        RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();
    final var userTaskKey = String.valueOf(createdUserTaskRecord.getKey());

    // when: performing the user task action
    userTaskAction.accept(ENGINE.userTask().ofInstance(processInstanceKey));

    // Step 1: Validate headers of the first listener (initial user task state)
    final var firstListenerJob = activateJob(processInstanceKey, listenerType);
    final var expectedInitialHeaders =
        new HashMap<String, String>() {
          {
            if (isAssigneeConfiguredOnTaskCreation) {
              put(Protocol.USER_TASK_ASSIGNEE_HEADER_NAME, "initial_assignee");
            }
            put(Protocol.USER_TASK_KEY_HEADER_NAME, userTaskKey);
            put(Protocol.USER_TASK_CANDIDATE_USERS_HEADER_NAME, "[\"initial_candidate_user\"]");
            put(Protocol.USER_TASK_CANDIDATE_GROUPS_HEADER_NAME, "[\"initial_candidate_group\"]");
            put(Protocol.USER_TASK_DUE_DATE_HEADER_NAME, "2085-09-21T11:22:33+02:00");
            put(Protocol.USER_TASK_FOLLOW_UP_DATE_HEADER_NAME, "2095-09-21T11:22:33+02:00");
            put(Protocol.USER_TASK_PRIORITY_HEADER_NAME, "50");
          }
        };
    assertThat(firstListenerJob.getCustomHeaders())
        .describedAs("Headers should reflect the initial user task data in the first listener")
        .containsAllEntriesOf(expectedInitialHeaders);

    // Step 2: Apply corrections to the user task and validate the headers in the second listener
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(listenerType)
        .withResult(
            new JobResult()
                .setCorrections(
                    new JobResultCorrections()
                        .setAssignee("new_assignee")
                        .setCandidateUsersList(List.of("new_candidate_user"))
                        .setCandidateGroupsList(List.of("new_candidate_group"))
                        .setDueDate("new_due_date")
                        .setFollowUpDate("new_follow_up_date")
                        .setPriority(100))
                .setCorrectedAttributes(
                    List.of(
                        "assignee",
                        "candidateUsersList",
                        "candidateGroupsList",
                        "dueDate",
                        "followUpDate",
                        "priority")))
        .complete();

    final var secondListenerJob = activateJob(processInstanceKey, listenerType + "_2");
    assertThat(secondListenerJob.getCustomHeaders())
        .describedAs(
            "Headers should reflect the corrected user task data in the subsequent listener")
        .contains(
            entry(Protocol.USER_TASK_ASSIGNEE_HEADER_NAME, "new_assignee"),
            entry(Protocol.USER_TASK_KEY_HEADER_NAME, userTaskKey),
            entry(Protocol.USER_TASK_CANDIDATE_USERS_HEADER_NAME, "[\"new_candidate_user\"]"),
            entry(Protocol.USER_TASK_CANDIDATE_GROUPS_HEADER_NAME, "[\"new_candidate_group\"]"),
            entry(Protocol.USER_TASK_DUE_DATE_HEADER_NAME, "new_due_date"),
            entry(Protocol.USER_TASK_FOLLOW_UP_DATE_HEADER_NAME, "new_follow_up_date"),
            entry(Protocol.USER_TASK_PRIORITY_HEADER_NAME, "100"));

    // Step 3: Clear user task properties using corrections and validate third listener headers
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(listenerType + "_2")
        .withResult(
            new JobResult()
                .setCorrections(new JobResultCorrections())
                .setCorrectedAttributes(
                    List.of(
                        "assignee",
                        "candidateUsersList",
                        "candidateGroupsList",
                        "dueDate",
                        "followUpDate",
                        "priority")))
        .complete();

    final var thirdListenerJob = activateJob(processInstanceKey, listenerType + "_3");
    assertThat(thirdListenerJob.getCustomHeaders())
        .describedAs("Headers should not include cleared user task properties")
        .doesNotContainKeys(
            Protocol.USER_TASK_ASSIGNEE_HEADER_NAME,
            Protocol.USER_TASK_PRIORITY_HEADER_NAME,
            Protocol.USER_TASK_CANDIDATE_GROUPS_HEADER_NAME,
            Protocol.USER_TASK_CANDIDATE_USERS_HEADER_NAME,
            Protocol.USER_TASK_DUE_DATE_HEADER_NAME,
            Protocol.USER_TASK_FOLLOW_UP_DATE_HEADER_NAME);

    completeJobs(processInstanceKey, listenerType + "_3");

    // Step 4: Validate the complete sequence of user task lifecycle intents
    assertUserTaskIntentsSequence(expectedUserTaskIntents.toArray(UserTaskIntent[]::new));
  }

  @Test
  public void
      shouldTrackChangedAttributesOnlyForActuallyCorrectedValuesOnTaskAssignmentAfterCreation() {
    verifyChangedAttributesAreTrackedOnlyForActuallyCorrectedValues(
        ZeebeTaskListenerEventType.assigning, true, userTask -> {}, UserTaskIntent.ASSIGNED);
  }

  @Test
  public void shouldTrackChangedAttributesOnlyForActuallyCorrectedValuesOnTaskAssignment() {
    verifyChangedAttributesAreTrackedOnlyForActuallyCorrectedValues(
        ZeebeTaskListenerEventType.assigning,
        false,
        userTask -> userTask.withAssignee("initial_assignee").assign(),
        UserTaskIntent.ASSIGNED);
  }

  @Test
  public void shouldTrackChangedAttributesOnlyForActuallyCorrectedValuesOnTaskClaiming() {
    verifyChangedAttributesAreTrackedOnlyForActuallyCorrectedValues(
        ZeebeTaskListenerEventType.assigning,
        false,
        userTask -> userTask.withAssignee("initial_assignee").claim(),
        UserTaskIntent.ASSIGNED);
  }

  @Test
  public void shouldTrackChangedAttributesOnlyForActuallyCorrectedValuesOnTaskUpdate() {
    verifyChangedAttributesAreTrackedOnlyForActuallyCorrectedValues(
        ZeebeTaskListenerEventType.updating,
        false,
        userTask -> userTask.update(new UserTaskRecord()),
        UserTaskIntent.UPDATED);
  }

  @Test
  public void shouldTrackChangedAttributesOnlyForActuallyCorrectedValuesOnTaskCompletion() {
    verifyChangedAttributesAreTrackedOnlyForActuallyCorrectedValues(
        ZeebeTaskListenerEventType.completing,
        false,
        UserTaskClient::complete,
        UserTaskIntent.COMPLETED);
  }

  @Test
  public void shouldTrackChangedAttributesOnlyForActuallyCorrectedValuesOnTaskCancellation() {
    verifyChangedAttributesAreTrackedOnlyForActuallyCorrectedValues(
        ZeebeTaskListenerEventType.canceling,
        false,
        ignore -> cancelProcessInstance(),
        UserTaskIntent.CANCELED);
  }

  /**
   * Verifies that only the attributes actually corrected by user task listeners are tracked during
   * task lifecycle events.
   *
   * <p>This method validates the following scenarios:
   *
   * <ul>
   *   <li>Attributes corrected by the first listener are tracked and reflected in the emitted
   *       `CORRECTED` user task event.
   *   <li>If the next listener applies the same corrections, the `CORRECTED` intent isn't emitted
   *       because there are no changes in attribute values.
   *   <li>Partial corrections made by a subsequent listener include only the attributes that were
   *       actually modified.
   * </ul>
   *
   * @param eventType the event type of the user task listener
   * @param isAssigneeConfiguredOnTaskCreation whether the assignee is configured during task
   *     creation
   * @param userTaskAction the action performed on the user task
   * @param terminalActionIntent the final intent for the performed action on the user task
   */
  private void verifyChangedAttributesAreTrackedOnlyForActuallyCorrectedValues(
      final ZeebeTaskListenerEventType eventType,
      final boolean isAssigneeConfiguredOnTaskCreation,
      final Consumer<UserTaskClient> userTaskAction,
      final UserTaskIntent terminalActionIntent) {

    // given: a process instance with a user task configured with listeners and initial properties
    final long processInstanceKey =
        createProcessInstance(
            createProcessWithZeebeUserTask(
                userTask -> {
                  if (isAssigneeConfiguredOnTaskCreation) {
                    userTask.zeebeAssignee("initial_assignee");
                  }
                  return userTask
                      .zeebeCandidateUsers("initial_candidate_user")
                      .zeebeCandidateGroups("initial_candidate_group")
                      .zeebeDueDate("2085-09-21T11:22:33+02:00")
                      .zeebeFollowUpDate("2095-09-21T11:22:33+02:00")
                      .zeebeTaskListener(l -> l.eventType(eventType).type(listenerType))
                      .zeebeTaskListener(l -> l.eventType(eventType).type(listenerType + "_2"))
                      .zeebeTaskListener(l -> l.eventType(eventType).type(listenerType + "_3"));
                }));

    // Mocking a result that corrects all user task attributes
    final var correctAllAttributesResult =
        new JobResult()
            .setCorrections(
                new JobResultCorrections()
                    .setAssignee("new_assignee")
                    .setCandidateGroupsList(List.of("new_candidate_group"))
                    .setCandidateUsersList(List.of("new_candidate_user"))
                    .setDueDate("new_due_date")
                    .setFollowUpDate("new_follow_up_date")
                    .setPriority(100))
            .setCorrectedAttributes(
                List.of(
                    "assignee",
                    "candidateGroupsList",
                    "candidateUsersList",
                    "dueDate",
                    "followUpDate",
                    "priority"));

    // when: performing the user task action
    userTaskAction.accept(ENGINE.userTask().ofInstance(processInstanceKey));

    // First listener fully corrects the user task attributes
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(listenerType)
        .withResult(correctAllAttributesResult)
        .complete();

    // Second listener applies the same corrections
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(listenerType + "_2")
        .withResult(correctAllAttributesResult)
        .complete();

    // Third listener partially updates the user task
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(listenerType + "_3")
        .withResult(
            new JobResult()
                .setCorrections(
                    new JobResultCorrections()
                        .setCandidateGroupsList(List.of("modified_candidate_group")) // changed
                        .setDueDate("modified_due_date") // changed
                        .setFollowUpDate("new_follow_up_date") // same as the previous correction
                        .setPriority(88)) // changed
                .setCorrectedAttributes(
                    List.of("candidateGroupsList", "dueDate", "followUpDate", "priority")))
        .complete();

    // then: verify the changed attributes for `COMPLETE_TASK_LISTENER` and `CORRECTED` intents
    final Predicate<Record<?>> isRelevantUserTaskIntent =
        record ->
            record.getIntent() == UserTaskIntent.COMPLETE_TASK_LISTENER
                || record.getIntent() == UserTaskIntent.CORRECTED;
    assertThat(
            RecordingExporter.userTaskRecords().limit(r -> r.getIntent() == terminalActionIntent))
        .filteredOn(isRelevantUserTaskIntent)
        .extracting(Record::getIntent, r -> r.getValue().getChangedAttributes())
        .describedAs(
            "Expected corrected attributes to be tracked only for values that were actually modified")
        .containsExactly(
            // Listener 1: all attributes corrected
            tuple(UserTaskIntent.COMPLETE_TASK_LISTENER, ALL_CORRECTABLE_ATTRIBUTES),
            tuple(UserTaskIntent.CORRECTED, ALL_CORRECTABLE_ATTRIBUTES),
            // Listener 2: attributes corrected again to the same values
            tuple(UserTaskIntent.COMPLETE_TASK_LISTENER, ALL_CORRECTABLE_ATTRIBUTES),
            // No `CORRECTED` event was fired because the attributes' values were unchanged
            // Listener 3: partially corrected attributes
            tuple(
                UserTaskIntent.COMPLETE_TASK_LISTENER,
                List.of("candidateGroupsList", "dueDate", "followUpDate", "priority")),
            // `followUpDate` isn't present as it has the same value as the previous correction
            tuple(UserTaskIntent.CORRECTED, List.of("candidateGroupsList", "dueDate", "priority")));
  }

  @Test
  public void shouldTrackOnlyChangedAttributesDuringUserTaskAssignmentWithMultipleListeners() {
    // given
    final int initialPriority = 42;
    final long processInstanceKey =
        createProcessInstance(
            createProcessWithZeebeUserTask(
                task ->
                    task.zeebeTaskPriority(Integer.toString(initialPriority))
                        .zeebeTaskListener(listener -> listener.assigning().type(listenerType))
                        .zeebeTaskListener(
                            listener -> listener.assigning().type(listenerType + "_2"))
                        .zeebeTaskListener(
                            listener -> listener.assigning().type(listenerType + "_3"))));

    // when
    ENGINE.userTask().ofInstance(processInstanceKey).withAssignee("merry").assign();

    // The first listener applies corrections to the `assignee`, `dueDate`, and `priority` fields
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(listenerType)
        .withResult(
            new JobResult()
                .setCorrections(
                    new JobResultCorrections()
                        .setAssignee("pippin")
                        .setDueDate("corrected_due_date")
                        .setPriority(84))
                .setCorrectedAttributes(List.of("assignee", "dueDate", "priority")))
        .complete();

    // The second listener resets the `assignee` and `priority` to their initial user task values
    // before the task assignment
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(listenerType + "_2")
        .withResult(
            new JobResult()
                .setCorrections(
                    new JobResultCorrections()
                        .setAssignee("") // Clears the assignee to mark the task as unassigned
                        .setPriority(initialPriority))
                .setCorrectedAttributes(List.of("assignee", "priority")))
        .complete();

    // The third listener corrects the `candidateGroupsList`
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(listenerType + "_3")
        .withResult(
            new JobResult()
                .setCorrections(
                    new JobResultCorrections().setCandidateGroupsList(List.of("hobbits")))
                .setCorrectedAttributes(List.of("candidateGroupsList")))
        .complete();

    // then
    // Verify the sequence of intents and the `changedAttributes` emitted for the user task
    assertThat(
            RecordingExporter.userTaskRecords()
                .limit(record -> record.getIntent() == UserTaskIntent.ASSIGNED))
        .as("Verify the user task lifecycle and tracking of `changedAttributes`")
        .extracting(Record::getIntent, record -> record.getValue().getChangedAttributes())
        .containsExactly(
            tuple(UserTaskIntent.CREATING, List.of()),
            tuple(UserTaskIntent.CREATED, List.of()),
            tuple(UserTaskIntent.ASSIGN, List.of()),
            tuple(UserTaskIntent.ASSIGNING, List.of("assignee")),
            tuple(
                UserTaskIntent.COMPLETE_TASK_LISTENER, List.of("assignee", "dueDate", "priority")),
            tuple(UserTaskIntent.CORRECTED, List.of("assignee", "dueDate", "priority")),
            tuple(UserTaskIntent.COMPLETE_TASK_LISTENER, List.of("assignee", "priority")),
            tuple(UserTaskIntent.CORRECTED, List.of("assignee", "priority")),
            tuple(UserTaskIntent.COMPLETE_TASK_LISTENER, List.of("candidateGroupsList")),
            tuple(UserTaskIntent.CORRECTED, List.of("candidateGroupsList")),
            // As a result, `changedAttributes` contains only `dueDate` and `candidateGroupsList`
            // because these are the only properties that differ from the original values of the
            // created user task. All other properties were either unchanged or reset to their
            // initial values that were before task assignment using the corrections.
            tuple(UserTaskIntent.ASSIGNED, List.of("candidateGroupsList", "dueDate")));

    final var createdUserTaskValue =
        RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst()
            .getValue();
    final var assignedUserTaskValue =
        RecordingExporter.userTaskRecords(UserTaskIntent.ASSIGNED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst()
            .getValue();

    Assertions.assertThat(assignedUserTaskValue)
        // unchanged properties
        .hasFollowUpDate(createdUserTaskValue.getFollowUpDate())
        .hasCandidateUsersList(createdUserTaskValue.getCandidateUsersList())
        // properties reset to their initial values
        .hasAssignee(createdUserTaskValue.getAssignee())
        .hasPriority(createdUserTaskValue.getPriority())
        // changed properties
        .hasDueDate("corrected_due_date")
        .hasCandidateGroupsList("hobbits")
        .hasOnlyChangedAttributes("candidateGroupsList", "dueDate")
        .hasAction("assign");
  }

  @Test
  public void shouldPersistCorrectedUserTaskDataWhenAssigningOnCreationTaskListenerCompleted() {
    testPersistCorrectedUserTaskDataWhenAllTaskListenersCompleted(
        ZeebeTaskListenerEventType.assigning,
        u -> u.zeebeAssignee("initial_assignee"),
        userTask -> {},
        UserTaskIntent.ASSIGNED);
  }

  @Test
  public void shouldPersistCorrectedUserTaskDataWhenAssigningTaskListenerCompleted() {
    testPersistCorrectedUserTaskDataWhenAllTaskListenersCompleted(
        ZeebeTaskListenerEventType.assigning,
        u -> u,
        userTask -> userTask.withAssignee("initial_assignee").assign(),
        UserTaskIntent.ASSIGNED);
  }

  @Test
  public void shouldPersistCorrectedUserTaskDataWhenClaimingTaskListenerCompleted() {
    testPersistCorrectedUserTaskDataWhenAllTaskListenersCompleted(
        ZeebeTaskListenerEventType.assigning,
        u -> u,
        userTask -> userTask.withAssignee("initial_assignee").claim(),
        UserTaskIntent.ASSIGNED);
  }

  @Test
  public void shouldPersistCorrectedUserTaskDataWhenUpdatingTaskListenerCompletes() {
    testPersistCorrectedUserTaskDataWhenAllTaskListenersCompleted(
        ZeebeTaskListenerEventType.updating,
        u -> u,
        userTask -> userTask.update(new UserTaskRecord()),
        UserTaskIntent.UPDATED);
  }

  @Test
  public void shouldPersistCorrectedUserTaskDataWhenAllTaskListenersCompleted() {
    testPersistCorrectedUserTaskDataWhenAllTaskListenersCompleted(
        ZeebeTaskListenerEventType.completing,
        u -> u,
        UserTaskClient::complete,
        UserTaskIntent.COMPLETED);
  }

  @Test
  public void shouldPersistCorrectedUserTaskDataWhenCancelingTaskListenerCompletes() {
    testPersistCorrectedUserTaskDataWhenAllTaskListenersCompleted(
        ZeebeTaskListenerEventType.canceling,
        u -> u,
        ignore -> cancelProcessInstance(),
        UserTaskIntent.CANCELED);
  }

  private void testPersistCorrectedUserTaskDataWhenAllTaskListenersCompleted(
      final ZeebeTaskListenerEventType eventType,
      final UnaryOperator<UserTaskBuilder> userTaskBuilder,
      final Consumer<UserTaskClient> userTaskAction,
      final UserTaskIntent expectedUserTaskIntent) {
    // given
    final long processInstanceKey =
        createProcessInstance(
            createProcessWithZeebeUserTask(
                t ->
                    userTaskBuilder
                        .apply(t)
                        .zeebeTaskListener(l -> l.eventType(eventType).type(listenerType))
                        .zeebeTaskListener(l -> l.eventType(eventType).type(listenerType + "_2"))));

    userTaskAction.accept(ENGINE.userTask().ofInstance(processInstanceKey));

    // when
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(listenerType)
        .withResult(
            new JobResult()
                .setCorrections(
                    new JobResultCorrections()
                        .setAssignee("new_assignee")
                        .setCandidateUsersList(List.of("new_candidate_user"))
                        .setCandidateGroupsList(List.of("new_candidate_group"))
                        .setDueDate("new_due_date")
                        .setFollowUpDate("new_follow_up_date")
                        .setPriority(100))
                .setCorrectedAttributes(
                    List.of(
                        "assignee",
                        "candidateUsersList",
                        "candidateGroupsList",
                        "dueDate",
                        "followUpDate",
                        "priority")))
        .complete();
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(listenerType + "_2")
        .withResult(
            new JobResult()
                .setCorrections(new JobResultCorrections().setAssignee("twice_corrected_assignee"))
                .setCorrectedAttributes(List.of("assignee")))
        .complete();

    // then
    assertUserTaskRecordWithIntent(
        processInstanceKey,
        expectedUserTaskIntent,
        userTaskRecord ->
            Assertions.assertThat(userTaskRecord)
                .describedAs("Expect that the last user task event contains the corrected data")
                .hasChangedAttributes(
                    "assignee",
                    "candidateUsersList",
                    "candidateGroupsList",
                    "dueDate",
                    "followUpDate",
                    "priority")
                .hasCandidateUsersList("new_candidate_user")
                .hasCandidateGroupsList("new_candidate_group")
                .hasDueDate("new_due_date")
                .hasFollowUpDate("new_follow_up_date")
                .hasPriority(100)
                .describedAs("Expect that the most recent correction takes precedence")
                .hasAssignee("twice_corrected_assignee"));
  }

  @Test
  public void shouldRevertCorrectedUserTaskDataWhenAssigningOnCreationTaskListenerDenies() {
    testRevertCorrectedUserTaskDataWhenTaskListenerDenies(
        ZeebeTaskListenerEventType.assigning,
        u -> u.zeebeAssignee("initial_assignee"),
        userTask -> {},
        UserTaskIntent.ASSIGNMENT_DENIED);
  }

  @Test
  public void shouldRevertCorrectedUserTaskDataWhenAssigningTaskListenerDenies() {
    testRevertCorrectedUserTaskDataWhenTaskListenerDenies(
        ZeebeTaskListenerEventType.assigning,
        u -> u,
        userTask -> userTask.withAssignee("initial_assignee").assign(),
        UserTaskIntent.ASSIGNMENT_DENIED);
  }

  @Test
  public void shouldRevertCorrectedUserTaskDataWhenClaimingTaskListenerDenies() {
    testRevertCorrectedUserTaskDataWhenTaskListenerDenies(
        ZeebeTaskListenerEventType.assigning,
        u -> u,
        userTask -> userTask.withAssignee("initial_assignee").claim(),
        UserTaskIntent.ASSIGNMENT_DENIED);
  }

  @Test
  public void shouldRevertCorrectedUserTaskDataWhenUpdatingTaskListenerDenies() {
    testRevertCorrectedUserTaskDataWhenTaskListenerDenies(
        ZeebeTaskListenerEventType.updating,
        u -> u,
        userTask -> userTask.update(new UserTaskRecord()),
        UserTaskIntent.UPDATE_DENIED);
  }

  @Test
  public void shouldRevertCorrectedUserTaskDataWhenCompletingTaskListenerDenies() {
    testRevertCorrectedUserTaskDataWhenTaskListenerDenies(
        ZeebeTaskListenerEventType.completing,
        u -> u,
        UserTaskClient::complete,
        UserTaskIntent.COMPLETION_DENIED);
  }

  private void testRevertCorrectedUserTaskDataWhenTaskListenerDenies(
      final ZeebeTaskListenerEventType eventType,
      final UnaryOperator<UserTaskBuilder> userTaskBuilder,
      final Consumer<UserTaskClient> userTaskAction,
      final UserTaskIntent expectedUserTaskIntent) {
    // given
    final long processInstanceKey =
        createProcessInstance(
            createProcessWithZeebeUserTask(
                t ->
                    userTaskBuilder
                        .apply(t)
                        .zeebeTaskListener(l -> l.eventType(eventType).type(listenerType))
                        .zeebeTaskListener(l -> l.eventType(eventType).type(listenerType + "_2"))));

    userTaskAction.accept(ENGINE.userTask().ofInstance(processInstanceKey));

    // when
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(listenerType)
        .withResult(
            new JobResult()
                .setCorrections(
                    new JobResultCorrections()
                        .setAssignee("new_assignee")
                        .setCandidateUsersList(List.of("new_candidate_user"))
                        .setCandidateGroupsList(List.of("new_candidate_group"))
                        .setDueDate("new_due_date")
                        .setFollowUpDate("new_follow_up_date")
                        .setPriority(100))
                .setCorrectedAttributes(
                    List.of(
                        "assignee",
                        "candidateUsersList",
                        "candidateGroupsList",
                        "dueDate",
                        "followUpDate",
                        "priority")))
        .complete();
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(listenerType + "_2")
        .withResult(new JobResult().setDenied(true))
        .complete();

    // then
    assertUserTaskRecordWithIntent(
        processInstanceKey,
        expectedUserTaskIntent,
        userTaskRecord ->
            Assertions.assertThat(userTaskRecord)
                .describedAs("Expect that user task data is reverted to before the event")
                .hasNoChangedAttributes()
                .hasNoCandidateUsersList()
                .hasNoCandidateGroupsList()
                .hasDueDate("")
                .hasFollowUpDate("")
                .hasPriority(50)
                .hasAssignee(""));
  }

  @Test
  public void shouldRejectDenyingTaskListenerWithCorrections() {
    // given
    final long processInstanceKey =
        createProcessInstance(
            createProcessWithCompletingTaskListeners(listenerType, listenerType + "_2"));

    ENGINE.userTask().ofInstance(processInstanceKey).complete();

    // when
    final var rejection =
        ENGINE
            .job()
            .ofInstance(processInstanceKey)
            .withType(listenerType)
            .withResult(
                new JobResult()
                    .setDenied(true)
                    .setCorrections(new JobResultCorrections().setAssignee("new_assignee"))
                    .setCorrectedAttributes(List.of("assignee")))
            .expectRejection()
            .complete();

    // then
    Assertions.assertThat(rejection)
        .describedAs("Expect that the job completion is rejected")
        .hasRejectionType(RejectionType.INVALID_ARGUMENT)
        .hasRejectionReason(
            """
            Expected to complete task listener job with corrections, but the job result is denied. \
            The corrections would be reverted by the denial. Either complete the job with \
            corrections without setting denied, or complete the job with a denied result but no \
            corrections.""");
  }

  @Test
  public void shouldRejectTaskListenerCompletionWithUnknownCorrections() {
    // given
    final long processInstanceKey =
        createProcessInstance(
            createProcessWithCompletingTaskListeners(listenerType, listenerType + "_2"));

    ENGINE.userTask().ofInstance(processInstanceKey).complete();

    // when
    final var rejection =
        ENGINE
            .job()
            .ofInstance(processInstanceKey)
            .withType(listenerType)
            .withResult(new JobResult().setCorrectedAttributes(List.of("unknown_property")))
            .expectRejection()
            .complete();

    // then
    Assertions.assertThat(rejection)
        .describedAs("Expect that the job completion is rejected")
        .hasRejectionType(RejectionType.INVALID_ARGUMENT)
        .hasRejectionReason(
            """
            Expected to complete task listener job with a corrections result, \
            but property 'unknown_property' cannot be corrected. \
            Only the following properties can be corrected: \
            [assignee, candidateGroupsList, candidateUsersList, dueDate, followUpDate, priority].""");
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
        userTask -> userTask.update(new UserTaskRecord()),
        UserTaskIntent.UPDATED,
        userTask -> Assertions.assertThat(userTask).hasAction("update"));
  }

  @Test
  @Ignore(
      "Fails on variable UPDATE with the following error: Expected to update variables for element with key '<kep>', but no such element was found")
  public void shouldRetryUserTaskCancelCommandAfterExtractValueErrorIncidentResolution() {
    testUserTaskCommandRetryAfterExtractValueError(
        ZeebeTaskListenerEventType.canceling,
        "canceling_listener_var_name",
        "expression_canceling_listener_2",
        ignore -> cancelProcessInstance(),
        UserTaskIntent.CANCELED,
        userTask -> Assertions.assertThat(userTask).isNotNull());
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
        createProcessInstance(
            createUserTaskWithTaskListeners(
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
    completeRecreatedJobWithType(ENGINE, processInstanceKey, listenerType);
    completeJobs(processInstanceKey, variableValue, listenerType + "_3");

    // then
    assertTaskListenerJobsCompletionSequence(
        processInstanceKey,
        mapToJobListenerEventType(eventType),
        listenerType,
        listenerType, // re-created task listener job
        variableValue,
        listenerType + "_3");

    assertUserTaskRecordWithIntent(processInstanceKey, expectedIntent, assertion);
  }

  @Test
  public void
      shouldTriggerUserTaskAssignCommandAfterExtractValueErrorIncidentResolutionWhenUserTaskWasConfiguredWithAssignee() {
    // given
    final var assignee = "me";

    // when: process instance is created with a UT having an `assignee` and `assignment` listeners
    final long processInstanceKey =
        createProcessInstance(
            createProcessWithZeebeUserTask(
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
    completeRecreatedJobWithType(ENGINE, processInstanceKey, listenerType);
    completeJobs(processInstanceKey, "expression_assigning_listener_2", listenerType + "_3");

    // then
    assertTaskListenerJobsCompletionSequence(
        processInstanceKey,
        JobListenerEventType.ASSIGNING,
        listenerType,
        listenerType, // re-created task listener job
        "expression_assigning_listener_2",
        listenerType + "_3");

    assertUserTaskRecordWithIntent(
        processInstanceKey,
        UserTaskIntent.ASSIGNED,
        userTask -> Assertions.assertThat(userTask).hasAssignee(assignee).hasAction(""));
  }

  private static void completeRecreatedJobWithType(
      final EngineRule engine, final long processInstanceKey, final String jobType) {
    final long jobKey = findRecreatedJobKey(processInstanceKey, jobType);
    engine.job().ofInstance(processInstanceKey).withKey(jobKey).complete();
  }

  private static void completeRecreatedJobWithTypeAndResult(
      final EngineRule engine,
      final long processInstanceKey,
      final String jobType,
      final JobResult jobResult) {
    final long jobKey = findRecreatedJobKey(processInstanceKey, jobType);
    engine.job().ofInstance(processInstanceKey).withKey(jobKey).withResult(jobResult).complete();
  }

  private static long findRecreatedJobKey(final long processInstanceKey, final String jobType) {
    return jobRecords(JobIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .withType(jobType)
        .skip(1)
        .getFirst()
        .getKey();
  }

  private void assertThatProcessInstanceCompleted(final long processInstanceKey) {
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getBpmnElementType(), Record::getIntent)
        .containsSubsequence(
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  private BpmnModelInstance createProcessWithZeebeUserTask(
      final UnaryOperator<UserTaskBuilder> userTaskBuilderFunction) {
    return Bpmn.createExecutableProcess(PROCESS_ID)
        .startEvent()
        .userTask(USER_TASK_ELEMENT_ID, t -> userTaskBuilderFunction.apply(t.zeebeUserTask()))
        .endEvent()
        .done();
  }

  private BpmnModelInstance createProcess(
      final Function<StartEventBuilder, AbstractFlowNodeBuilder<?, ?>> processBuilderFunction) {
    return processBuilderFunction
        .apply(Bpmn.createExecutableProcess(PROCESS_ID).startEvent())
        .endEvent()
        .done();
  }

  private BpmnModelInstance createProcessWithAssigningTaskListeners(final String... listenerTypes) {
    return createUserTaskWithTaskListeners(ZeebeTaskListenerEventType.assigning, listenerTypes);
  }

  private BpmnModelInstance createUserTaskWithTaskListenersAndAssignee(
      final String listenerType, final String assignee) {
    return createProcessWithZeebeUserTask(
        taskBuilder ->
            taskBuilder
                .zeebeAssignee(assignee)
                .zeebeTaskListener(l -> l.assigning().type(listenerType)));
  }

  private BpmnModelInstance createProcessWithCompletingTaskListeners(
      final String... listenerTypes) {
    return createUserTaskWithTaskListeners(ZeebeTaskListenerEventType.completing, listenerTypes);
  }

  private BpmnModelInstance createUserTaskWithTaskListeners(
      final ZeebeTaskListenerEventType listenerType, final String... listenerTypes) {
    return createProcessWithZeebeUserTask(
        taskBuilder -> {
          Stream.of(listenerTypes)
              .forEach(
                  type -> taskBuilder.zeebeTaskListener(l -> l.eventType(listenerType).type(type)));
          return taskBuilder;
        });
  }

  private long createProcessInstance(final BpmnModelInstance modelInstance) {
    return createProcessInstanceWithVariables(modelInstance, Collections.emptyMap());
  }

  private long createProcessInstanceWithVariables(
      final BpmnModelInstance modelInstance, final Map<String, Object> processVariables) {
    ENGINE.deployment().withXmlResource(modelInstance).deploy();
    return ENGINE
        .processInstance()
        .ofBpmnProcessId(PROCESS_ID)
        .withVariables(processVariables)
        .create();
  }

  private void completeJobs(final long processInstanceKey, final String... jobTypes) {
    for (final String jobType : jobTypes) {
      ENGINE.job().ofInstance(processInstanceKey).withType(jobType).complete();
    }
  }

  private void completeRecreatedJobs(final long processInstanceKey, final String... jobTypes) {
    for (final String jobType : jobTypes) {
      completeRecreatedJobWithType(ENGINE, processInstanceKey, jobType);
    }
  }

  private JobRecordValue activateJob(final long processInstanceKey, final String jobType) {
    return ENGINE.jobs().withType(jobType).activate().getValue().getJobs().stream()
        .filter(job -> job.getProcessInstanceKey() == processInstanceKey)
        .findFirst()
        .orElseThrow(() -> new AssertionError("No job found with type " + jobType));
  }

  private FormMetadataValue deployForm(final String formPath) {
    final var deploymentEvent = ENGINE.deployment().withJsonClasspathResource(formPath).deploy();

    Assertions.assertThat(deploymentEvent)
        .hasIntent(DeploymentIntent.CREATED)
        .hasValueType(ValueType.DEPLOYMENT)
        .hasRecordType(RecordType.EVENT);

    final var formMetadata = deploymentEvent.getValue().getFormMetadata();
    assertThat(formMetadata).hasSize(1);
    return formMetadata.getFirst();
  }

  private void assertTaskListenerJobsCompletionSequence(
      final long processInstanceKey,
      final JobListenerEventType eventType,
      final String... listenerTypes) {
    assertThat(
            RecordingExporter.jobRecords()
                .withProcessInstanceKey(processInstanceKey)
                .withJobKind(JobKind.TASK_LISTENER)
                .withJobListenerEventType(eventType)
                .withIntent(JobIntent.COMPLETED)
                .limit(listenerTypes.length))
        .extracting(Record::getValue)
        .extracting(JobRecordValue::getType)
        .describedAs("Verify that all task listeners were completed in the correct sequence")
        .containsExactly(listenerTypes);
  }

  private void assertUserTaskIntentsSequence(final UserTaskIntent... intents) {
    assertThat(intents).describedAs("Expected intents not to be empty").isNotEmpty();
    assertThat(
            RecordingExporter.userTaskRecords()
                .limit(r -> r.getIntent() == intents[intents.length - 1]))
        .extracting(Record::getIntent)
        .describedAs("Verify the expected sequence of User Task intents")
        .containsSequence(intents);
  }

  private static JobListenerEventType mapToJobListenerEventType(
      final ZeebeTaskListenerEventType eventType) {
    return switch (eventType) {
      case ZeebeTaskListenerEventType.assigning -> JobListenerEventType.ASSIGNING;
      case ZeebeTaskListenerEventType.updating -> JobListenerEventType.UPDATING;
      case ZeebeTaskListenerEventType.completing -> JobListenerEventType.COMPLETING;
      case ZeebeTaskListenerEventType.canceling -> JobListenerEventType.CANCELING;
      default ->
          throw new IllegalArgumentException(
              "Unsupported zeebe task listener event type: '%s'".formatted(eventType));
    };
  }

  private static void assertUserTaskRecordWithIntent(
      final long processInstanceKey,
      final UserTaskIntent intent,
      final Consumer<UserTaskRecordValue> consumer) {
    assertThat(
            RecordingExporter.userTaskRecords(intent)
                .withProcessInstanceKey(processInstanceKey)
                .findFirst()
                .map(Record::getValue))
        .describedAs("Expected to have User Task record with '%s' intent", intent)
        .hasValueSatisfying(consumer);
  }

  @Test
  public void
      shouldCancelUserTaskAfterProcessWasCancelledAndAllCancelingTaskListenersAreExecuted() {
    // given
    final long processInstanceKey =
        createProcessInstance(
            createUserTaskWithTaskListeners(
                ZeebeTaskListenerEventType.canceling,
                listenerType,
                listenerType + "_2",
                listenerType + "_3"));
    RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .await();

    // when
    ENGINE.processInstance().withInstanceKey(processInstanceKey).expectTerminating().cancel();
    completeJobs(processInstanceKey, listenerType, listenerType + "_2", listenerType + "_3");

    // then
    assertTaskListenerJobsCompletionSequence(
        processInstanceKey,
        JobListenerEventType.CANCELING,
        listenerType,
        listenerType + "_2",
        listenerType + "_3");

    // ensure that `COMPLETE_TASK_LISTENER` commands were triggered between
    // `CANCELING` and `CANCELED` events
    assertUserTaskIntentsSequence(
        UserTaskIntent.CANCELING,
        UserTaskIntent.COMPLETE_TASK_LISTENER,
        UserTaskIntent.COMPLETE_TASK_LISTENER,
        UserTaskIntent.COMPLETE_TASK_LISTENER,
        UserTaskIntent.CANCELED);
  }

  @Test
  public void
      shouldCancelUserTaskAfterTimerBoundaryEventTriggersCancellationAndAllCancelingTaskListenersAreExecuted() {
    // given
    final long processInstanceKey =
        createProcessInstance(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .userTask(
                    USER_TASK_ELEMENT_ID,
                    t ->
                        t.zeebeUserTask()
                            .zeebeTaskListener(l -> l.canceling().type(listenerType))
                            .zeebeTaskListener(l -> l.canceling().type(listenerType + "_2"))
                            .zeebeTaskListener(l -> l.canceling().type(listenerType + "_3")))
                .boundaryEvent("timer_boundary_event")
                .timerWithDate("=now()")
                .endEvent("boundary_end")
                .moveToActivity(USER_TASK_ELEMENT_ID)
                .endEvent("user_task_end")
                .done());

    // when
    completeJobs(processInstanceKey, listenerType, listenerType + "_2", listenerType + "_3");

    // then
    assertTaskListenerJobsCompletionSequence(
        processInstanceKey,
        JobListenerEventType.CANCELING,
        listenerType,
        listenerType + "_2",
        listenerType + "_3");

    // ensure TIMER was triggered
    assertThat(
            RecordingExporter.timerRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limit(r -> r.getIntent() == TimerIntent.TRIGGERED))
        .extracting(Record::getIntent)
        .containsExactly(TimerIntent.CREATED, TimerIntent.TRIGGER, TimerIntent.TRIGGERED);

    // ensure that `COMPLETE_TASK_LISTENER` commands were triggered between
    // `CANCELING` and `CANCELED` events
    assertUserTaskIntentsSequence(
        UserTaskIntent.CANCELING,
        UserTaskIntent.COMPLETE_TASK_LISTENER,
        UserTaskIntent.COMPLETE_TASK_LISTENER,
        UserTaskIntent.COMPLETE_TASK_LISTENER,
        UserTaskIntent.CANCELED);
  }

  @Test
  public void shouldTriggerCancelingAfterHavingBeenInterrupted() {
    // given
    final long processInstanceKey =
        createProcessInstance(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .userTask(
                    USER_TASK_ELEMENT_ID,
                    t ->
                        t.zeebeUserTask()
                            .zeebeTaskListener(l -> l.canceling().type(listenerType))
                            .zeebeTaskListener(l -> l.canceling().type(listenerType + "_2"))
                            .zeebeTaskListener(l -> l.canceling().type(listenerType + "_3")))
                .boundaryEvent("timer_boundary_event")
                .timerWithDate("=now()")
                .endEvent("boundary_end")
                .moveToActivity(USER_TASK_ELEMENT_ID)
                .endEvent("user_task_end")
                .done());

    // when
    completeJobs(processInstanceKey, listenerType);

    // cancel a process instance
    ENGINE.processInstance().withInstanceKey(processInstanceKey).expectTerminating().cancel();

    completeJobs(processInstanceKey, listenerType + "_2", listenerType + "_3");

    assertTaskListenerJobsCompletionSequence(
        processInstanceKey,
        JobListenerEventType.CANCELING,
        listenerType,
        listenerType + "_2",
        listenerType + "_3");

    // ensure that `COMPLETE_TASK_LISTENER` commands were triggered between
    // `CANCELING` and `CANCELED` events
    assertUserTaskIntentsSequence(
        UserTaskIntent.CANCELING,
        UserTaskIntent.COMPLETE_TASK_LISTENER,
        UserTaskIntent.COMPLETE_TASK_LISTENER,
        UserTaskIntent.COMPLETE_TASK_LISTENER,
        UserTaskIntent.CANCELED);
  }

  @Test
  public void shouldTriggerCancelingAfterHavingBeenInterruptedByNestedBoundaryEvent() {
    // given
    final long processInstanceKey =
        createProcessInstanceWithVariables(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .subProcess(
                    "subProcess",
                    s ->
                        s.embeddedSubProcess()
                            .startEvent()
                            .userTask(
                                USER_TASK_ELEMENT_ID,
                                t ->
                                    t.zeebeUserTask()
                                        .zeebeTaskListener(l -> l.canceling().type(listenerType))
                                        .zeebeTaskListener(
                                            l -> l.canceling().type(listenerType + "_2"))
                                        .zeebeTaskListener(
                                            l -> l.canceling().type(listenerType + "_3")))
                            .boundaryEvent("inner_timer_boundary_event")
                            .timerWithDate("=now()")
                            .endEvent("boundary_end")
                            .moveToActivity(USER_TASK_ELEMENT_ID)
                            .endEvent("user_task_end"))
                .boundaryEvent("message_boundary_event")
                .message(m -> m.name("msg").zeebeCorrelationKeyExpression("correlationKey"))
                .endEvent("message_boundary_event_end")
                .moveToActivity("subProcess")
                .endEvent("end")
                .done(),
            Map.of("correlationKey", "key_123"));

    // when
    completeJobs(processInstanceKey, listenerType);

    ENGINE.message().withName("msg").withCorrelationKey("key_123").publish();

    completeJobs(processInstanceKey, listenerType + "_2", listenerType + "_3");

    assertTaskListenerJobsCompletionSequence(
        processInstanceKey,
        JobListenerEventType.CANCELING,
        listenerType,
        listenerType + "_2",
        listenerType + "_3");

    // ensure that `COMPLETE_TASK_LISTENER` commands were triggered between
    // `CANCELING` and `CANCELED` events
    assertUserTaskIntentsSequence(
        UserTaskIntent.CANCELING,
        UserTaskIntent.COMPLETE_TASK_LISTENER,
        UserTaskIntent.COMPLETE_TASK_LISTENER,
        UserTaskIntent.COMPLETE_TASK_LISTENER,
        UserTaskIntent.CANCELED);
  }

  @Test
  public void shouldRejectDenyingCancelingTaskListener() {
    // given
    final long processInstanceKey =
        createProcessInstance(
            createUserTaskWithTaskListeners(
                ZeebeTaskListenerEventType.canceling, listenerType, listenerType + "_2"));

    ENGINE.processInstance().withInstanceKey(processInstanceKey).expectTerminating().cancel();

    // when
    final var rejection =
        ENGINE
            .job()
            .ofInstance(processInstanceKey)
            .withType(listenerType)
            .withResult(new JobResult().setDenied(true))
            .expectRejection()
            .complete();

    // then
    Assertions.assertThat(rejection)
        .describedAs("Expect that the `CANCELING` job completion with `denied=true` is rejected")
        .hasRejectionType(RejectionType.INVALID_ARGUMENT)
        .hasRejectionReason(
            """
                Expected to complete a 'CANCELING' task listener job, but the job result is set to \
                `denied`. `Canceling` task listeners cannot deny user task cancellation. \
                Please complete the job without setting the `denied=true`.""");
  }
}
