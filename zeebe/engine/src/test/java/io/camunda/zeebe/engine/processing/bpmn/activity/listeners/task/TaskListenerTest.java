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
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.builder.AbstractFlowNodeBuilder;
import io.camunda.zeebe.model.bpmn.builder.StartEventBuilder;
import io.camunda.zeebe.model.bpmn.builder.UserTaskBuilder;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeTaskListenerEventType;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.record.value.job.JobResult;
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
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ErrorType;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import io.camunda.zeebe.protocol.record.value.JobKind;
import io.camunda.zeebe.protocol.record.value.JobListenerEventType;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.protocol.record.value.UserTaskRecordValue;
import io.camunda.zeebe.protocol.record.value.deployment.FormMetadataValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;

public class TaskListenerTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  private static final String PROCESS_ID = "process";
  private static final String USER_TASK_KEY_HEADER_NAME =
      Protocol.RESERVED_HEADER_NAME_PREFIX + "userTaskKey";

  private static final String LISTENER_TYPE = "my_listener";
  private static final String USER_TASK_ELEMENT_ID = "my_user_task";

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldCompleteUserTaskAfterAllCompleteTaskListenersAreExecuted() {
    // given
    final long processInstanceKey =
        createProcessInstance(
            createProcessWithCompleteTaskListeners(
                LISTENER_TYPE, LISTENER_TYPE + "_2", LISTENER_TYPE + "_3"));

    // when
    ENGINE
        .userTask()
        .ofInstance(processInstanceKey)
        .withVariable("foo_var", "bar")
        .withAction("my_custom_action")
        .complete();
    completeJobs(processInstanceKey, LISTENER_TYPE, LISTENER_TYPE + "_2", LISTENER_TYPE + "_3");

    // then
    assertTaskListenerJobsCompletionSequence(
        processInstanceKey,
        JobListenerEventType.COMPLETE,
        LISTENER_TYPE,
        LISTENER_TYPE + "_2",
        LISTENER_TYPE + "_3");

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
                .hasVariables(Map.of("foo_var", "bar")));
    assertThatProcessInstanceCompleted(processInstanceKey);
  }

  @Test
  public void shouldAssignUserTaskAfterAllAssignmentTaskListenersAreExecuted() {
    // given
    final long processInstanceKey =
        createProcessInstance(
            createUserTaskWithTaskListeners(
                ZeebeTaskListenerEventType.assignment,
                LISTENER_TYPE,
                LISTENER_TYPE + "_2",
                LISTENER_TYPE + "_3"));

    // when
    ENGINE
        .userTask()
        .ofInstance(processInstanceKey)
        .withVariable("ignored", "variables")
        .withAssignee("me")
        .withAction("my_assign_action")
        .assign();
    completeJobs(processInstanceKey, LISTENER_TYPE, LISTENER_TYPE + "_2", LISTENER_TYPE + "_3");

    // then
    assertTaskListenerJobsCompletionSequence(
        processInstanceKey,
        JobListenerEventType.ASSIGNMENT,
        LISTENER_TYPE,
        LISTENER_TYPE + "_2",
        LISTENER_TYPE + "_3");

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
                .describedAs(
                    "Expected all variables provided during UserTask assignment to be ignored")
                .hasVariables(Collections.emptyMap()));
  }

  @Test
  public void shouldRetryTaskListenerWhenListenerJobFailed() {
    // given
    final long processInstanceKey =
        createProcessInstance(
            createProcessWithCompleteTaskListeners(LISTENER_TYPE, LISTENER_TYPE + "_2"));

    ENGINE.userTask().ofInstance(processInstanceKey).complete();

    // when: fail listener job with retries
    ENGINE.job().ofInstance(processInstanceKey).withType(LISTENER_TYPE).withRetries(1).fail();
    // complete failed and remaining listeners job
    completeJobs(processInstanceKey, LISTENER_TYPE, LISTENER_TYPE + "_2");

    // then: assert the listener job was completed after the failure
    assertThat(records().betweenProcessInstance(processInstanceKey))
        .extracting(Record::getValueType, Record::getIntent)
        .containsSubsequence(
            tuple(ValueType.USER_TASK, UserTaskIntent.COMPLETING),
            tuple(ValueType.JOB, JobIntent.CREATED),
            tuple(ValueType.JOB, JobIntent.FAILED),
            tuple(ValueType.JOB, JobIntent.COMPLETE),
            tuple(ValueType.JOB, JobIntent.COMPLETED),
            tuple(ValueType.USER_TASK, UserTaskIntent.COMPLETE_TASK_LISTENER),
            tuple(ValueType.JOB, JobIntent.CREATED),
            tuple(ValueType.JOB, JobIntent.COMPLETE),
            tuple(ValueType.JOB, JobIntent.COMPLETED),
            tuple(ValueType.USER_TASK, UserTaskIntent.COMPLETE_TASK_LISTENER),
            tuple(ValueType.USER_TASK, UserTaskIntent.COMPLETED));

    assertThatProcessInstanceCompleted(processInstanceKey);
  }

  @Test
  public void shouldCreateIncidentForListenerWhenNoRetriesLeftAndProceedWithRemainingListeners() {
    // given
    final long processInstanceKey =
        createProcessInstance(
            createProcessWithCompleteTaskListeners(
                LISTENER_TYPE, LISTENER_TYPE + "_2", LISTENER_TYPE + "_3"));

    ENGINE.userTask().ofInstance(processInstanceKey).complete();
    completeJobs(processInstanceKey, LISTENER_TYPE);

    // when: fail 2nd listener job with no retries
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(LISTENER_TYPE + "_2")
        .withRetries(0)
        .fail();

    // then: incident created
    final Record<IncidentRecordValue> incident =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();
    Assertions.assertThat(incident.getValue())
        .hasProcessInstanceKey(processInstanceKey)
        .hasErrorType(ErrorType.JOB_NO_RETRIES)
        .hasErrorMessage("No more retries left.");

    // resolve incident & complete failed TL job
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(LISTENER_TYPE + "_2")
        .withRetries(1)
        .updateRetries();
    ENGINE.incident().ofInstance(processInstanceKey).withKey(incident.getKey()).resolve();

    // complete failed and remaining listener job
    completeJobs(processInstanceKey, LISTENER_TYPE + "_2", LISTENER_TYPE + "_3");

    // assert the listener job was completed after the failure
    assertThat(records().betweenProcessInstance(processInstanceKey))
        .extracting(Record::getValueType, Record::getIntent)
        .containsSubsequence(
            tuple(ValueType.USER_TASK, UserTaskIntent.COMPLETING),
            tuple(ValueType.JOB, JobIntent.CREATED),
            tuple(ValueType.JOB, JobIntent.COMPLETE),
            tuple(ValueType.JOB, JobIntent.COMPLETED),
            tuple(ValueType.USER_TASK, UserTaskIntent.COMPLETE_TASK_LISTENER),
            tuple(ValueType.JOB, JobIntent.CREATED),
            tuple(ValueType.JOB, JobIntent.FAILED),
            tuple(ValueType.INCIDENT, IncidentIntent.CREATED),
            tuple(ValueType.JOB, JobIntent.RETRIES_UPDATED),
            tuple(ValueType.INCIDENT, IncidentIntent.RESOLVED),
            tuple(ValueType.JOB, JobIntent.COMPLETE),
            tuple(ValueType.JOB, JobIntent.COMPLETED),
            tuple(ValueType.USER_TASK, UserTaskIntent.COMPLETE_TASK_LISTENER),
            tuple(ValueType.JOB, JobIntent.CREATED),
            tuple(ValueType.JOB, JobIntent.COMPLETE),
            tuple(ValueType.JOB, JobIntent.COMPLETED),
            tuple(ValueType.USER_TASK, UserTaskIntent.COMPLETE_TASK_LISTENER),
            tuple(ValueType.USER_TASK, UserTaskIntent.COMPLETED));

    assertThatProcessInstanceCompleted(processInstanceKey);
  }

  @Test
  public void shouldEvaluateExpressionsForTaskListeners() {
    final long processInstanceKey =
        createProcessInstanceWithVariables(
            createProcessWithZeebeUserTask(
                t ->
                    t.zeebeTaskListener(
                        l ->
                            l.complete()
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
            createProcessWithCompleteTaskListeners(LISTENER_TYPE, LISTENER_TYPE + "_2"));

    ENGINE.userTask().ofInstance(processInstanceKey).complete();

    // when
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(LISTENER_TYPE)
        .withVariable("listener_1_var", "foo")
        .complete();

    // then: `listener_1_var` variable accessible in subsequent TL
    final var jobActivated = activateJob(processInstanceKey, LISTENER_TYPE + "_2");
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
                                    .zeebeTaskListener(l -> l.complete().type(LISTENER_TYPE)))
                        .serviceTask(
                            "subsequent_service_task",
                            tb -> tb.zeebeJobType("subsequent_service_task"))));

    ENGINE.userTask().ofInstance(processInstanceKey).complete();

    // when: complete TL job with a variable 'my_listener_var'
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(LISTENER_TYPE)
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
                                    .zeebeTaskListener(l -> l.complete().type(LISTENER_TYPE))
                                    .zeebeOutput("=my_listener_var+\"_abc\"", "userTaskOutput"))
                        .serviceTask(
                            "subsequent_service_task",
                            tb -> tb.zeebeJobType("subsequent_service_task"))));

    ENGINE.userTask().ofInstance(processInstanceKey).complete();

    // when: complete TL job with a variable 'my_listener_var'
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(LISTENER_TYPE)
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
                        .zeebeTaskListener(l -> l.complete().type(LISTENER_TYPE))));

    // when
    final var userTaskRecordValue = ENGINE.userTask().ofInstance(processInstanceKey).complete();

    // then
    final var activatedListenerJob = activateJob(processInstanceKey, LISTENER_TYPE);

    assertThat(activatedListenerJob.getCustomHeaders())
        .containsOnly(
            entry(
                Protocol.USER_TASK_CANDIDATE_GROUPS_HEADER_NAME,
                "[\"group_A\",\"group_C\",\"group_F\"]"),
            entry(Protocol.USER_TASK_CANDIDATE_USERS_HEADER_NAME, "[\"user_A\",\"user_B\"]"),
            entry(Protocol.USER_TASK_ASSIGNEE_HEADER_NAME, "admin"),
            entry(Protocol.USER_TASK_DUE_DATE_HEADER_NAME, "2095-09-18T10:31:10+02:00"),
            entry(Protocol.USER_TASK_FORM_KEY_HEADER_NAME, Objects.toString(form.getFormKey())),
            entry(USER_TASK_KEY_HEADER_NAME, String.valueOf(userTaskRecordValue.getKey())));
    completeJobs(processInstanceKey, LISTENER_TYPE);
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
                        .zeebeTaskListener(l -> l.complete().type(LISTENER_TYPE))));

    final var changes =
        new UserTaskRecord()
            .setCandidateGroupsList(List.of("group_J", "group_R"))
            .setCandidateUsersList(List.of("user_T"))
            .setDueDate("2087-09-21T11:22:33+02:00")
            .setFollowUpDate("2097-09-21T11:22:33+02:00");

    // when
    ENGINE.userTask().ofInstance(processInstanceKey).update(changes);
    final var userTaskRecordValue = ENGINE.userTask().ofInstance(processInstanceKey).complete();

    // then
    final var activatedListenerJob = activateJob(processInstanceKey, LISTENER_TYPE);
    assertThat(activatedListenerJob.getCustomHeaders())
        .containsOnly(
            entry(Protocol.USER_TASK_CANDIDATE_GROUPS_HEADER_NAME, "[\"group_J\",\"group_R\"]"),
            entry(Protocol.USER_TASK_CANDIDATE_USERS_HEADER_NAME, "[\"user_T\"]"),
            entry(Protocol.USER_TASK_ASSIGNEE_HEADER_NAME, "admin"),
            entry(Protocol.USER_TASK_DUE_DATE_HEADER_NAME, "2087-09-21T11:22:33+02:00"),
            entry(Protocol.USER_TASK_FOLLOW_UP_DATE_HEADER_NAME, "2097-09-21T11:22:33+02:00"),
            entry(USER_TASK_KEY_HEADER_NAME, String.valueOf(userTaskRecordValue.getKey())));
    completeJobs(processInstanceKey, LISTENER_TYPE);
  }

  @Test
  public void shouldProvideVariablesOfTaskCompletionToCompleteTaskListener() {
    // given
    final var processInstanceKey =
        createProcessInstanceWithVariables(
            createProcessWithCompleteTaskListeners(LISTENER_TYPE), Map.of("foo", "bar"));

    // when
    ENGINE.userTask().ofInstance(processInstanceKey).withVariables(Map.of("baz", 123)).complete();

    // then
    assertThat(ENGINE.jobs().withType(LISTENER_TYPE).activate().getValue().getJobs())
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
            createProcessWithCompleteTaskListeners(LISTENER_TYPE), Map.of("foo", "bar"));

    // when
    ENGINE
        .userTask()
        .ofInstance(processInstanceKey)
        .withVariables(Map.of("foo", "overwritten"))
        .complete();

    // then
    assertThat(ENGINE.jobs().withType(LISTENER_TYPE).activate().getValue().getJobs())
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
            createProcessWithCompleteTaskListeners(LISTENER_TYPE),
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
                .withType(LISTENER_TYPE)
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
        createProcessInstance(createProcessWithCompleteTaskListeners(LISTENER_TYPE));

    ENGINE.userTask().ofInstance(processInstanceKey).complete();

    // when: try to complete TL job with a variable payload
    final var result =
        ENGINE
            .job()
            .ofInstance(processInstanceKey)
            .withType(LISTENER_TYPE)
            .withVariable("my_listener_var", "foo")
            .complete();

    Assertions.assertThat(result)
        .describedAs(
            "Task Listener job completion should be rejected when variable payload provided")
        .hasIntent(JobIntent.COMPLETE)
        .hasRejectionType(RejectionType.INVALID_ARGUMENT)
        .hasRejectionReason(
            TL_JOB_COMPLETION_WITH_VARS_NOT_SUPPORTED_MESSAGE.formatted(
                result.getKey(), LISTENER_TYPE, processInstanceKey));

    // complete the listener job without variables to have a completed process
    // and prevent flakiness in other tests
    ENGINE.job().ofInstance(processInstanceKey).withType(LISTENER_TYPE).complete();
  }

  @Test
  public void shouldCompleteTaskWithTaskListenerWhenJobResultDeniedIsFalse() {
    // given
    final long processInstanceKey =
        createProcessInstance(
            createProcessWithCompleteTaskListeners(
                LISTENER_TYPE, LISTENER_TYPE + "_2", LISTENER_TYPE + "_3"));

    // when
    ENGINE.userTask().ofInstance(processInstanceKey).complete();
    ENGINE.job().ofInstance(processInstanceKey).withType(LISTENER_TYPE).complete();

    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(LISTENER_TYPE + "_2")
        .withResult(new JobResult())
        .complete();

    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(LISTENER_TYPE + "_3")
        .withResult(new JobResult().setDenied(false))
        .complete();

    // then
    assertThat(
            RecordingExporter.jobRecords()
                .withProcessInstanceKey(processInstanceKey)
                .withJobKind(JobKind.TASK_LISTENER)
                .withJobListenerEventType(JobListenerEventType.COMPLETE)
                .withIntent(JobIntent.COMPLETED)
                .limit(3))
        .extracting(Record::getValue)
        .extracting(JobRecordValue::getType, v -> v.getResult().isDenied())
        .describedAs("Verify that all task listeners were completed with `denied=false`")
        .containsExactly(
            tuple(LISTENER_TYPE, false),
            tuple(LISTENER_TYPE + "_2", false),
            tuple(LISTENER_TYPE + "_3", false));
  }

  @Test
  public void shouldRejectUserTaskCompletionWhenTaskListenerRejectsTheOperation() {
    // given
    final long processInstanceKey =
        createProcessInstance(createProcessWithCompleteTaskListeners(LISTENER_TYPE));

    ENGINE.userTask().ofInstance(processInstanceKey).complete();
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(LISTENER_TYPE)
        .withResult(new JobResult().setDenied(true))
        .complete();

    // then: ensure that `REJECT_TASK_LISTENER` and `COMPLETION_DENIED`
    // are written after `COMPLETING` event
    assertUserTaskIntentsSequence(
        UserTaskIntent.COMPLETING,
        UserTaskIntent.DENY_TASK_LISTENER,
        UserTaskIntent.COMPLETION_DENIED);
  }

  @Test
  public void shouldCompleteTaskWhenTaskListenerAcceptsOperationAfterRejection() {
    // given
    final long processInstanceKey =
        createProcessInstance(createProcessWithCompleteTaskListeners(LISTENER_TYPE));

    ENGINE.userTask().ofInstance(processInstanceKey).complete();
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(LISTENER_TYPE)
        .withResult(new JobResult().setDenied(true))
        .complete();

    ENGINE.userTask().ofInstance(processInstanceKey).complete();

    completeRecreatedJobWithType(ENGINE, processInstanceKey, LISTENER_TYPE);

    // then: ensure that `COMPLETING` `COMPLETE_TASK_LISTENER` and `COMPLETED events
    // are present after `REJECT_TASK_LISTENER` and `COMPLETION_DENIED` events
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
  public void shouldCompleteAllTaskListenersWhenFirstTaskListenerAcceptOperationAfterRejection() {
    // given
    final long processInstanceKey =
        createProcessInstance(
            createProcessWithCompleteTaskListeners(
                LISTENER_TYPE, LISTENER_TYPE + "_2", LISTENER_TYPE + "_3"));

    ENGINE.userTask().ofInstance(processInstanceKey).complete();
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(LISTENER_TYPE)
        .withResult(new JobResult().setDenied(true))
        .complete();

    ENGINE.userTask().ofInstance(processInstanceKey).complete();
    completeRecreatedJobWithType(ENGINE, processInstanceKey, LISTENER_TYPE);
    completeJobs(processInstanceKey, LISTENER_TYPE + "_2", LISTENER_TYPE + "_3");

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
  public void shouldAssignAndCompleteTaskAfterTaskListenerRejectsTheCompletion() {
    // given
    final long processInstanceKey =
        createProcessInstance(createProcessWithCompleteTaskListeners(LISTENER_TYPE));

    ENGINE.userTask().ofInstance(processInstanceKey).complete();
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(LISTENER_TYPE)
        .withResult(new JobResult().setDenied(true))
        .complete();

    ENGINE.userTask().ofInstance(processInstanceKey).withAssignee("Test Assignee").assign();
    ENGINE.userTask().ofInstance(processInstanceKey).complete();

    completeRecreatedJobWithType(ENGINE, processInstanceKey, LISTENER_TYPE);

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

  private static void completeRecreatedJobWithType(
      final EngineRule engine, final long processInstanceKey, final String jobType) {
    final long jobKey =
        jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withType(jobType)
            .skip(1)
            .getFirst()
            .getKey();
    engine.job().ofInstance(processInstanceKey).withKey(jobKey).complete();
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

  private BpmnModelInstance createProcessWithCompleteTaskListeners(final String... listenerTypes) {
    return createUserTaskWithTaskListeners(ZeebeTaskListenerEventType.complete, listenerTypes);
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
}
