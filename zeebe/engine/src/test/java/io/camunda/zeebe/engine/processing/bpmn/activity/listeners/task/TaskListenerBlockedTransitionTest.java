/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.bpmn.activity.listeners.task;

import static io.camunda.zeebe.test.util.record.RecordingExporter.jobRecords;
import static io.camunda.zeebe.test.util.record.RecordingExporter.records;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.engine.util.client.UserTaskClient;
import io.camunda.zeebe.model.bpmn.builder.UserTaskBuilder;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeTaskListenerEventType;
import io.camunda.zeebe.protocol.impl.record.value.usertask.UserTaskRecord;
import io.camunda.zeebe.protocol.impl.record.value.variable.VariableDocumentRecord;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.protocol.record.intent.VariableDocumentIntent;
import io.camunda.zeebe.protocol.record.value.JobKind;
import io.camunda.zeebe.protocol.record.value.JobListenerEventType;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import org.apache.commons.lang3.StringUtils;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

/** Tests verifying that a task listener blocks the lifecycle transition of a user task. */
public class TaskListenerBlockedTransitionTest {
  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();
  @Rule public final TestWatcher watcher = new RecordingExporterTestWatcher();
  private final TaskListenerTestHelper helper = new TaskListenerTestHelper(ENGINE);

  private String listenerType;

  @Before
  public void setup() {
    listenerType = "my_listener_" + UUID.randomUUID();
  }

  @Test
  public void shouldEvaluateExpressionsForTaskListeners() {
    final long processInstanceKey =
        helper.createProcessInstanceWithVariables(
            helper.createProcessWithZeebeUserTask(
                t ->
                    t.zeebeTaskListener(
                        l ->
                            l.completing()
                                .typeExpression("\"listener_1_\"+my_var")
                                .retriesExpression("5+3"))),
            Map.of("my_var", "abc"));

    // when
    ENGINE.userTask().ofInstance(processInstanceKey).complete();
    helper.completeJobs(processInstanceKey, "listener_1_abc");

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
    helper.assertThatProcessInstanceCompleted(processInstanceKey);
  }

  @Test
  public void shouldRejectTaskVariableUpdateWhenUserTaskIsNotInCreatedState() {
    // given: a process instance with a user task having an assignee and task listeners
    final long processInstanceKey =
        helper.createProcessInstance(
            helper.createProcessWithZeebeUserTask(
                t ->
                    t.zeebeAssignee("initial_assignee")
                        .zeebeTaskListener(l -> l.assigning().type(listenerType + "_assigning"))
                        .zeebeTaskListener(l -> l.updating().type(listenerType + "_updating"))));

    // since the user task has an initial assignee, the assignment transition is triggered
    final var assigningUserTaskRecord =
        RecordingExporter.userTaskRecords(UserTaskIntent.ASSIGNING)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    // when: attempting to update variables while the user task is in the 'ASSIGNING' state
    final var variableUpdateRejection =
        ENGINE
            .variables()
            .ofScope(assigningUserTaskRecord.getValue().getElementInstanceKey())
            .withDocument(Map.of("employeeId", "E12345"))
            .withLocalSemantic()
            .expectRejection()
            .update();

    // then
    Assertions.assertThat(variableUpdateRejection)
        .describedAs(
            "Expect rejection when trying to update variables for a user task that is currently in the 'ASSIGNING' state")
        .hasRecordType(RecordType.COMMAND_REJECTION)
        .hasValueType(ValueType.VARIABLE_DOCUMENT)
        .hasRejectionReason(
            "Expected to trigger update transition for user task with key '%d', but it is in state 'ASSIGNING'"
                .formatted(assigningUserTaskRecord.getKey()));
  }

  @Test
  public void shouldCompleteUserTaskAfterAllCompleteTaskListenersAreExecuted() {
    // given
    final long processInstanceKey =
        helper.createProcessInstance(
            helper.createProcessWithCompletingTaskListeners(
                listenerType, listenerType + "_2", listenerType + "_3"));

    // when
    ENGINE
        .userTask()
        .ofInstance(processInstanceKey)
        .withVariable("foo_var", "bar")
        .withAction("my_custom_action")
        .complete();
    helper.completeJobs(processInstanceKey, listenerType, listenerType + "_2", listenerType + "_3");

    // then
    helper.assertTaskListenerJobsCompletionSequence(
        processInstanceKey,
        JobListenerEventType.COMPLETING,
        listenerType,
        listenerType + "_2",
        listenerType + "_3");

    // ensure that `COMPLETE_TASK_LISTENER` commands were triggered between
    // `COMPLETING` and `COMPLETED` events
    helper.assertUserTaskIntentsSequence(
        processInstanceKey,
        UserTaskIntent.COMPLETING,
        UserTaskIntent.COMPLETE_TASK_LISTENER,
        UserTaskIntent.COMPLETE_TASK_LISTENER,
        UserTaskIntent.COMPLETE_TASK_LISTENER,
        UserTaskIntent.COMPLETED);

    helper.assertUserTaskRecordWithIntent(
        processInstanceKey,
        UserTaskIntent.COMPLETED,
        userTask ->
            Assertions.assertThat(userTask)
                .hasAction("my_custom_action")
                .hasVariables(Map.of("foo_var", "bar"))
                .hasOnlyChangedAttributes(UserTaskRecord.VARIABLES));
    helper.assertThatProcessInstanceCompleted(processInstanceKey);
  }

  @Test
  public void shouldCreateFirstCreatingTaskListenerJob() {
    // given
    final long processInstanceKey =
        helper.createProcessInstance(
            helper.createProcessWithCreatingTaskListeners(listenerType + "_creating"));

    // then: expect a job to be activated for the first `creating` listener
    helper.assertActivatedJob(
        processInstanceKey,
        listenerType + "_creating",
        job -> {
          assertThat(job.getJobListenerEventType())
              .describedAs("Expect job to have job listener type 'CREATING'")
              .isEqualTo(JobListenerEventType.CREATING);
        });
  }

  @Test
  public void shouldAssignUserTaskAfterAllAssignmentTaskListenersAreExecuted() {
    // given
    final long processInstanceKey =
        helper.createProcessInstance(
            helper.createUserTaskWithTaskListeners(
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
    helper.completeJobs(processInstanceKey, listenerType, listenerType + "_2", listenerType + "_3");

    // then
    helper.assertTaskListenerJobsCompletionSequence(
        processInstanceKey,
        JobListenerEventType.ASSIGNING,
        listenerType,
        listenerType + "_2",
        listenerType + "_3");

    // ensure that `COMPLETE_TASK_LISTENER` commands were triggered between
    // `ASSIGNING` and `ASSIGNED` events
    helper.assertUserTaskIntentsSequence(
        processInstanceKey,
        UserTaskIntent.ASSIGNING,
        UserTaskIntent.COMPLETE_TASK_LISTENER,
        UserTaskIntent.COMPLETE_TASK_LISTENER,
        UserTaskIntent.COMPLETE_TASK_LISTENER,
        UserTaskIntent.ASSIGNED);

    helper.assertUserTaskRecordWithIntent(
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
        helper.createProcessInstance(
            helper.createUserTaskWithTaskListeners(
                ZeebeTaskListenerEventType.assigning,
                listenerType,
                listenerType + "_2",
                listenerType + "_3"));

    // when: assign the user task to "me" and complete all `assigning` listener jobs
    ENGINE.userTask().ofInstance(processInstanceKey).withAssignee("me").assign();
    helper.completeJobs(processInstanceKey, listenerType, listenerType + "_2", listenerType + "_3");

    // and: unassign the user task and complete all `assigning` listener jobs again
    ENGINE.userTask().ofInstance(processInstanceKey).unassign();
    helper.completeRecreatedJobs(
        processInstanceKey, listenerType, listenerType + "_2", listenerType + "_3");

    // then: all `assigning` listeners should be executed for both assign and unassign operations
    helper.assertTaskListenerJobsCompletionSequence(
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
        helper.createProcessInstance(
            helper.createUserTaskWithTaskListeners(
                ZeebeTaskListenerEventType.updating,
                listenerType,
                listenerType + "_2",
                listenerType + "_3"));

    // when
    ENGINE
        .userTask()
        .ofInstance(processInstanceKey)
        .withAction("my_update_action")
        .withCandidateUsers("samwise", "frodo")
        .withPriority(88)
        .update();
    helper.completeJobs(processInstanceKey, listenerType, listenerType + "_2", listenerType + "_3");

    // then
    helper.assertTaskListenerJobsCompletionSequence(
        processInstanceKey,
        JobListenerEventType.UPDATING,
        listenerType,
        listenerType + "_2",
        listenerType + "_3");

    // ensure that `COMPLETE_TASK_LISTENER` commands were triggered between
    // `UPDATING` and `UPDATED` events
    helper.assertUserTaskIntentsSequence(
        processInstanceKey,
        UserTaskIntent.UPDATING,
        UserTaskIntent.COMPLETE_TASK_LISTENER,
        UserTaskIntent.COMPLETE_TASK_LISTENER,
        UserTaskIntent.COMPLETE_TASK_LISTENER,
        UserTaskIntent.UPDATED);

    helper.assertUserTaskRecordWithIntent(
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
  public void
      shouldUpdateUserTaskAfterAllUpdatingTaskListenersAreExecutedTriggeredByVariableUpdate() {
    // given
    final long processInstanceKey =
        helper.createProcessInstance(
            helper.createUserTaskWithTaskListeners(
                ZeebeTaskListenerEventType.updating,
                listenerType,
                listenerType + "_2",
                listenerType + "_3"));
    final var createdUserTask =
        RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst()
            .getValue();
    final var userTaskElementInstanceKey = createdUserTask.getElementInstanceKey();

    // when
    ENGINE
        .variables()
        .ofScope(userTaskElementInstanceKey)
        .withDocument(Map.of("status", "APPROVED"))
        .withLocalSemantic()
        .expectUpdating()
        .update();
    helper.completeJobs(processInstanceKey, listenerType, listenerType + "_2", listenerType + "_3");

    // then
    helper.assertTaskListenerJobsCompletionSequence(
        processInstanceKey,
        JobListenerEventType.UPDATING,
        listenerType,
        listenerType + "_2",
        listenerType + "_3");

    final Predicate<Record<?>> isUserTaskOrVariableDocumentWithElementInstanceKey =
        r -> {
          if (r.getValue() instanceof final UserTaskRecord utr) {
            return utr.getElementInstanceKey() == userTaskElementInstanceKey;
          } else if (r.getValue() instanceof final VariableDocumentRecord vdr) {
            return vdr.getScopeKey() == userTaskElementInstanceKey;
          }
          return false;
        };

    assertThat(
            RecordingExporter.records()
                .filter(isUserTaskOrVariableDocumentWithElementInstanceKey)
                .skipUntil(r -> r.getIntent() == VariableDocumentIntent.UPDATE)
                .limit(r -> r.getIntent() == VariableDocumentIntent.UPDATED))
        .extracting(Record::getValueType, Record::getIntent)
        .describedAs("Verify the expected sequence of UserTask and VariableDocument intents")
        .containsExactly(
            tuple(ValueType.VARIABLE_DOCUMENT, VariableDocumentIntent.UPDATE),
            tuple(ValueType.VARIABLE_DOCUMENT, VariableDocumentIntent.UPDATING),
            tuple(ValueType.USER_TASK, UserTaskIntent.UPDATING),
            tuple(ValueType.USER_TASK, UserTaskIntent.COMPLETE_TASK_LISTENER),
            tuple(ValueType.USER_TASK, UserTaskIntent.COMPLETE_TASK_LISTENER),
            tuple(ValueType.USER_TASK, UserTaskIntent.COMPLETE_TASK_LISTENER),
            tuple(ValueType.USER_TASK, UserTaskIntent.UPDATED),
            tuple(ValueType.VARIABLE_DOCUMENT, VariableDocumentIntent.UPDATED));

    helper.assertUserTaskRecordWithIntent(
        processInstanceKey,
        UserTaskIntent.UPDATED,
        userTask ->
            Assertions.assertThat(userTask)
                .hasAssignee(createdUserTask.getAssignee())
                .hasCandidateGroupsList(createdUserTask.getCandidateGroupsList())
                .hasCandidateUsersList(createdUserTask.getCandidateUsersList())
                .hasDueDate(createdUserTask.getDueDate())
                .hasFollowUpDate(createdUserTask.getFollowUpDate())
                .hasPriority(createdUserTask.getPriority())
                .hasVariables(Map.of("status", "APPROVED"))
                .hasAction("")
                .hasOnlyChangedAttributes(UserTaskRecord.VARIABLES));
  }

  @Test
  public void
      shouldUpdateUserTaskAfterAllUpdatingTaskListenersAreExecutedTriggeredByVariableUpdateWithEmptyDocument() {
    // given
    final long processInstanceKey =
        helper.createProcessInstance(
            helper.createUserTaskWithTaskListeners(
                ZeebeTaskListenerEventType.updating, listenerType, listenerType + "_2"));
    final var userTaskElementInstanceKey = helper.getUserTaskElementInstanceKey(processInstanceKey);

    // when: empty document triggers update transition
    ENGINE
        .variables()
        .ofScope(userTaskElementInstanceKey)
        .withDocument(Map.of()) // empty document
        .withLocalSemantic()
        .expectUpdating()
        .update();

    helper.completeJobs(processInstanceKey, listenerType, listenerType + "_2");

    // then: ensure update transition completed and no variable was tracked as changed
    assertThat(
            RecordingExporter.userTaskRecords()
                .onlyEvents()
                .withProcessInstanceKey(processInstanceKey)
                .skipUntil(r -> r.getIntent() == UserTaskIntent.UPDATING)
                .limit(r -> r.getIntent() == UserTaskIntent.UPDATED))
        .extracting(Record::getValue)
        .allSatisfy(
            userTask -> {
              assertThat(userTask.getVariables())
                  .describedAs("Expect no variable was added to user task record")
                  .isEmpty();
              assertThat(userTask.getChangedAttributes())
                  .describedAs("Expect no attribute was marked as changed")
                  .isEmpty();
            });
  }

  @Test
  public void shouldExecuteAllUpdatingListenersOnRepeatedUserTaskUpdates() {
    // given: a user task with multiple `updating` task listeners
    final long processInstanceKey =
        helper.createProcessInstance(
            helper.createUserTaskWithTaskListeners(
                ZeebeTaskListenerEventType.updating,
                listenerType,
                listenerType + "_2",
                listenerType + "_3"));

    // when: update the user task with new candidate users
    ENGINE
        .userTask()
        .ofInstance(processInstanceKey)
        .withCandidateUsers("frodo", "samwise")
        .update();

    // complete all `updating` listener jobs
    helper.completeJobs(processInstanceKey, listenerType, listenerType + "_2", listenerType + "_3");

    // and: update the user task again with new candidate users and priority
    ENGINE
        .userTask()
        .ofInstance(processInstanceKey)
        .withAction("escalate")
        .withCandidateUsers("aragorn", "legolas")
        .withPriority(99)
        .update();

    // complete all `updating` listener jobs for the second update
    helper.completeRecreatedJobs(
        processInstanceKey, listenerType, listenerType + "_2", listenerType + "_3");

    // then: all `updating` listeners should execute for both update operations
    helper.assertTaskListenerJobsCompletionSequence(
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
  public void shouldCreateFirstCancelingTaskListenerJob() {
    // given
    final long processInstanceKey =
        helper.createProcessInstance(
            helper.createUserTaskWithTaskListeners(
                ZeebeTaskListenerEventType.canceling, listenerType));

    // when
    ENGINE.processInstance().withInstanceKey(processInstanceKey).expectTerminating().cancel();

    // then
    helper.assertActivatedJob(
        processInstanceKey,
        listenerType,
        job ->
            Assertions.assertThat(job)
                .describedAs("Expect activated job to be 'CANCELLING' task listener job")
                .hasJobKind(JobKind.TASK_LISTENER)
                .hasJobListenerEventType(JobListenerEventType.CANCELING));
  }

  @Test
  public void shouldCancelTaskListenerJobWhenTerminatingElementInstance() {
    // given
    final long processInstanceKey =
        helper.createProcessInstance(
            helper.createProcessWithAssigningTaskListeners(listenerType, listenerType + "_2"));

    ENGINE.userTask().ofInstance(processInstanceKey).withAssignee("samwise").assign();

    helper.completeJobs(processInstanceKey, listenerType);

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

    helper.assertUserTaskIntentsSequence(
        processInstanceKey,
        UserTaskIntent.ASSIGNING,
        UserTaskIntent.COMPLETE_TASK_LISTENER,
        UserTaskIntent.CANCELING,
        UserTaskIntent.CANCELED);
  }

  @Test
  public void shouldResolveTaskListenerIncidentWhenTerminatingElementInstance() {
    // given
    final long processInstanceKey =
        helper.createProcessInstance(
            helper.createProcessWithAssigningTaskListeners(listenerType, listenerType + "_2"));

    ENGINE.userTask().ofInstance(processInstanceKey).withAssignee("samwise").assign();

    helper.completeJobs(processInstanceKey, listenerType);
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

    helper.assertUserTaskIntentsSequence(
        processInstanceKey,
        UserTaskIntent.ASSIGNING,
        UserTaskIntent.COMPLETE_TASK_LISTENER,
        UserTaskIntent.CANCELING,
        UserTaskIntent.CANCELED);
  }

  @Test
  public void shouldClaimUserTaskAfterAllAssignmentTaskListenersAreExecuted() {
    // given
    final long processInstanceKey =
        helper.createProcessInstance(
            helper.createUserTaskWithTaskListeners(
                ZeebeTaskListenerEventType.assigning, listenerType, listenerType + "_2"));

    // when
    ENGINE
        .userTask()
        .ofInstance(processInstanceKey)
        .withAssignee("test_user")
        .withAction("claim_action")
        .claim();
    helper.completeJobs(processInstanceKey, listenerType, listenerType + "_2");

    // then
    helper.assertTaskListenerJobsCompletionSequence(
        processInstanceKey, JobListenerEventType.ASSIGNING, listenerType, listenerType + "_2");

    // ensure that `COMPLETE_TASK_LISTENER` commands were triggered between
    // `CLAIMING` and `ASSIGNED` events
    helper.assertUserTaskIntentsSequence(
        processInstanceKey,
        UserTaskIntent.CLAIMING,
        UserTaskIntent.COMPLETE_TASK_LISTENER,
        UserTaskIntent.COMPLETE_TASK_LISTENER,
        UserTaskIntent.ASSIGNED);

    helper.assertUserTaskRecordWithIntent(
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
        helper.createProcessInstance(
            helper.createProcessWithZeebeUserTask(
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
    helper.completeJobs(processInstanceKey, listenerType, listenerType + "_2", listenerType + "_3");

    // then: verify the task listener completion sequence for the assignment event
    helper.assertTaskListenerJobsCompletionSequence(
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
            tuple(UserTaskIntent.CREATING, assignee, action, List.of()),
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
        UserTaskClient::update,
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

  private void verifyListenerIsRetriedWhenListenerJobFailed(
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
                        .zeebeTaskListener(l -> l.eventType(eventType).type(listenerType + "_2"))));

    // when: performing the user task action
    userTaskAction.accept(ENGINE.userTask().ofInstance(processInstanceKey));

    // when: fail listener job with retries
    ENGINE.job().ofInstance(processInstanceKey).withType(listenerType).withRetries(1).fail();
    // complete failed and remaining listeners job
    helper.completeJobs(processInstanceKey, listenerType, listenerType + "_2");

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
}
