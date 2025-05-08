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
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.builder.UserTaskBuilder;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeTaskListenerEventType;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.record.value.usertask.UserTaskRecord;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.protocol.record.value.ErrorType;
import io.camunda.zeebe.protocol.record.value.JobListenerEventType;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.protocol.record.value.UserTaskRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.Map;
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
  public void shouldCreateJobNoRetriesIncidentForCreatingListenerAndContinueAfterResolution() {
    verifyIncidentCreationOnListenerJobWithoutRetriesAndResolution(
        ZeebeTaskListenerEventType.creating,
        UnaryOperator.identity(),
        ignored -> {},
        UserTaskIntent.CREATED);
  }

  @Test
  public void
      shouldCreateJobNoRetriesIncidentForAssigningListenerAndContinueAfterResolutionOnTaskAssignAfterCreation() {
    verifyIncidentCreationOnListenerJobWithoutRetriesAndResolution(
        ZeebeTaskListenerEventType.assigning,
        userTask -> userTask.zeebeAssignee("gandalf"),
        pik -> {},
        UserTaskIntent.ASSIGNED);
  }

  @Test
  public void
      shouldCreateJobNoRetriesIncidentForAssigningListenerAndContinueAfterResolutionOnTaskAssign() {
    verifyIncidentCreationOnListenerJobWithoutRetriesAndResolution(
        ZeebeTaskListenerEventType.assigning,
        UnaryOperator.identity(),
        pik -> ENGINE.userTask().ofInstance(pik).withAssignee("bilbo").assign(),
        UserTaskIntent.ASSIGNED);
  }

  @Test
  public void
      shouldCreateJobNoRetriesIncidentForAssigningListenerAndContinueAfterResolutionOnTaskClaim() {
    verifyIncidentCreationOnListenerJobWithoutRetriesAndResolution(
        ZeebeTaskListenerEventType.assigning,
        UnaryOperator.identity(),
        pik -> ENGINE.userTask().ofInstance(pik).withAssignee("bilbo").claim(),
        UserTaskIntent.ASSIGNED);
  }

  @Test
  public void
      shouldCreateJobNoRetriesIncidentForUpdatingListenerAndContinueAfterResolutionOnTaskUpdate() {
    verifyIncidentCreationOnListenerJobWithoutRetriesAndResolution(
        ZeebeTaskListenerEventType.updating,
        UnaryOperator.identity(),
        pik -> ENGINE.userTask().ofInstance(pik).update(),
        UserTaskIntent.UPDATED);
  }

  @Test
  public void
      shouldCreateJobNoRetriesIncidentContinueAfterResolutionAndCreateLocalTaskVariableOnTaskVariablesUpdate() {
    final long processInstanceKey =
        verifyIncidentCreationOnListenerJobWithoutRetriesAndResolution(
            ZeebeTaskListenerEventType.updating,
            UnaryOperator.identity(),
            pik ->
                ENGINE
                    .variables()
                    .ofScope(helper.getUserTaskElementInstanceKey(pik))
                    .withDocument(Map.of("status", "APPROVED"))
                    .withLocalSemantic()
                    .expectUpdating()
                    .update(),
            UserTaskIntent.UPDATED);

    Assertions.assertThat(
            RecordingExporter.variableRecords(VariableIntent.CREATED)
                .withScopeKey(helper.getUserTaskElementInstanceKey(processInstanceKey))
                .getFirst()
                .getValue())
        .describedAs(
            "Expect that the variable was created in the user task's local scope with the expected value")
        .hasName("status")
        .hasValue("\"APPROVED\"");
  }

  @Test
  public void
      shouldCreateJobNoRetriesIncidentForCompletingListenerAndContinueAfterResolutionOnTaskComplete() {
    verifyIncidentCreationOnListenerJobWithoutRetriesAndResolution(
        ZeebeTaskListenerEventType.completing,
        UnaryOperator.identity(),
        pik -> ENGINE.userTask().ofInstance(pik).complete(),
        UserTaskIntent.COMPLETED);
  }

  @Test
  public void shouldCreateJobNoRetriesIncidentForCancelingListenerAndContinueAfterResolution() {
    verifyIncidentCreationOnListenerJobWithoutRetriesAndResolution(
        ZeebeTaskListenerEventType.canceling,
        UnaryOperator.identity(),
        pik -> ENGINE.processInstance().withInstanceKey(pik).expectTerminating().cancel(),
        UserTaskIntent.CANCELED);
  }

  private long verifyIncidentCreationOnListenerJobWithoutRetriesAndResolution(
      final ZeebeTaskListenerEventType eventType,
      final UnaryOperator<UserTaskBuilder> userTaskBuilder,
      final Consumer<Long> transitionTrigger,
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

    // when: trigger the user task transition
    transitionTrigger.accept(processInstanceKey);

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
    return processInstanceKey;
  }

  @Test
  public void shouldRetryUserTaskCreateCommandAfterExtractValueErrorIncidentResolution() {
    testUserTaskCommandRetryAfterExtractValueError(
        ZeebeTaskListenerEventType.creating,
        "creating_listener_var_name",
        "expression_creating_listener_2",
        ignored -> {},
        UserTaskIntent.CREATED,
        userTask -> Assertions.assertThat(userTask).hasAction(""));
  }

  @Test
  public void shouldRetryUserTaskCompleteCommandAfterExtractValueErrorIncidentResolution() {
    testUserTaskCommandRetryAfterExtractValueError(
        ZeebeTaskListenerEventType.completing,
        "completing_listener_var_name",
        "expression_completing_listener_2",
        pik -> ENGINE.userTask().ofInstance(pik).complete(),
        UserTaskIntent.COMPLETED,
        userTask -> Assertions.assertThat(userTask).hasAction("complete"));
  }

  @Test
  public void shouldRetryUserTaskAssignCommandAfterExtractValueErrorIncidentResolution() {
    testUserTaskCommandRetryAfterExtractValueError(
        ZeebeTaskListenerEventType.assigning,
        "assigning_listener_var_name",
        "expression_assigning_listener_2",
        pik -> ENGINE.userTask().ofInstance(pik).withAssignee("me").assign(),
        UserTaskIntent.ASSIGNED,
        userTask -> Assertions.assertThat(userTask).hasAssignee("me").hasAction("assign"));
  }

  @Test
  public void shouldRetryUserTaskClaimCommandAfterExtractValueErrorIncidentResolution() {
    testUserTaskCommandRetryAfterExtractValueError(
        ZeebeTaskListenerEventType.assigning,
        "assigning_listener_var_name",
        "expression_assigning_listener_2",
        pik -> ENGINE.userTask().ofInstance(pik).withAssignee("me").claim(),
        UserTaskIntent.ASSIGNED,
        userTask -> Assertions.assertThat(userTask).hasAssignee("me").hasAction("claim"));
  }

  @Test
  public void shouldRetryUserTaskUpdateCommandAfterExtractValueErrorIncidentResolution() {
    testUserTaskCommandRetryAfterExtractValueError(
        ZeebeTaskListenerEventType.updating,
        "updating_listener_var_name",
        "expression_updating_listener_2",
        pik -> ENGINE.userTask().ofInstance(pik).update(),
        UserTaskIntent.UPDATED,
        userTask -> Assertions.assertThat(userTask).hasAction("update"));
  }

  @Test
  public void
      shouldRetryUserTaskUpdateCommandAfterExtractValueErrorIncidentResolutionAndCreateLocalTaskVariableOnTaskVariablesUpdate() {
    final long processInstanceKey =
        testUserTaskCommandRetryAfterExtractValueError(
            ZeebeTaskListenerEventType.updating,
            "updating_listener_var_name",
            "expression_updating_listener_2",
            pik ->
                ENGINE
                    .variables()
                    .ofScope(helper.getUserTaskElementInstanceKey(pik))
                    .withDocument(Map.of("status", "APPROVED"))
                    .withLocalSemantic()
                    .expectUpdating()
                    .update(),
            UserTaskIntent.UPDATED,
            userTask ->
                Assertions.assertThat(userTask)
                    .hasAction("")
                    .hasOnlyChangedAttributes(UserTaskRecord.VARIABLES));

    Assertions.assertThat(
            RecordingExporter.variableRecords(VariableIntent.CREATED)
                .withScopeKey(helper.getUserTaskElementInstanceKey(processInstanceKey))
                .getFirst()
                .getValue())
        .describedAs(
            "Expect that the variable was created in the user task's local scope with the expected value")
        .hasName("status")
        .hasValue("\"APPROVED\"");
  }

  private long testUserTaskCommandRetryAfterExtractValueError(
      final ZeebeTaskListenerEventType eventType,
      final String variableName,
      final String variableValue,
      final Consumer<Long> transitionTrigger,
      final UserTaskIntent expectedIntent,
      final Consumer<UserTaskRecordValue> assertion) {

    // given
    final long processInstanceKey =
        helper.createProcessInstance(
            helper.createUserTaskWithTaskListeners(
                eventType, listenerType, "=" + variableName, listenerType + "_3"));

    // when: trigger the user task transition
    transitionTrigger.accept(processInstanceKey);

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
    return processInstanceKey;
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

  @Test
  public void shouldRetryCancelingListenerJobAfterExtractValueErrorDuringUserTaskInterruption() {
    // given
    final long processInstanceKey =
        helper.createProcessInstance(
            Bpmn.createExecutableProcess("process")
                .startEvent()
                .userTask(
                    "user_task",
                    t ->
                        t.zeebeUserTask()
                            .zeebeTaskListener(l -> l.canceling().type(listenerType))
                            .zeebeTaskListener(
                                l -> l.canceling().typeExpression("canceling_listener_var_name"))
                            .zeebeTaskListener(l -> l.canceling().type(listenerType + "_3")))
                .boundaryEvent(
                    "msg_boundary_event",
                    e -> e.message(m -> m.name("my_message").zeebeCorrelationKey("=\"my_key-1\"")))
                .endEvent("boundary_end")
                .moveToActivity("user_task")
                .endEvent("main_end")
                .done());

    // when: interrupt the user task via message boundary event to trigger canceling transition
    ENGINE.message().withName("my_message").withCorrelationKey("my_key-1").publish();

    // complete the first task listener job
    ENGINE.job().ofInstance(processInstanceKey).withType(listenerType).complete();

    // then: expect incident due to missing variable in canceling listener expression
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
                .formatted("canceling_listener_var_name", "canceling_listener_var_name"));

    // when: fix the missing variable and resolve the incident
    ENGINE
        .variables()
        .ofScope(processInstanceKey)
        .withDocument(Map.of("canceling_listener_var_name", listenerType + "_2"))
        .update();
    ENGINE.incident().ofInstance(processInstanceKey).withKey(incidentRecord.getKey()).resolve();

    // complete the retried task listener job and remaining task listeners
    helper.completeRecreatedJobWithType(processInstanceKey, listenerType);
    helper.completeJobs(processInstanceKey, listenerType + "_2", listenerType + "_3");

    // then
    helper.assertTaskListenerJobsCompletionSequence(
        processInstanceKey,
        helper.mapToJobListenerEventType(ZeebeTaskListenerEventType.canceling),
        listenerType,
        listenerType, // re-created task listener job
        listenerType + "_2",
        listenerType + "_3");

    helper.assertUserTaskRecordWithIntent(
        processInstanceKey,
        UserTaskIntent.CANCELED,
        userTask -> assertThat(userTask.getAction()).isEmpty());
  }

  @Test
  public void retriedAssigningListenerJobShouldHaveCorrectHeadersFromAssignCommand() {
    testRetriedListenerJobHasExpectedHeaders(
        ZeebeTaskListenerEventType.assigning,
        pik ->
            ENGINE
                .userTask()
                .ofInstance(pik)
                .withAssignee("bryan")
                .withAction("custom_assignment")
                .assign(),
        Map.of(
            Protocol.USER_TASK_ASSIGNEE_HEADER_NAME,
            "bryan",
            Protocol.USER_TASK_CHANGED_ATTRIBUTES_HEADER_NAME,
            "[\"assignee\"]",
            Protocol.USER_TASK_ACTION_HEADER_NAME,
            "custom_assignment"));
  }

  @Test
  public void retriedAssigningListenerJobShouldHaveCorrectHeadersFromClaimCommand() {
    testRetriedListenerJobHasExpectedHeaders(
        ZeebeTaskListenerEventType.assigning,
        pik ->
            ENGINE
                .userTask()
                .ofInstance(pik)
                .withAssignee("jesse")
                .withAction("custom_claiming")
                .claim(),
        Map.of(
            Protocol.USER_TASK_ASSIGNEE_HEADER_NAME,
            "jesse",
            Protocol.USER_TASK_CHANGED_ATTRIBUTES_HEADER_NAME,
            "[\"assignee\"]",
            Protocol.USER_TASK_ACTION_HEADER_NAME,
            "custom_claiming"));
  }

  @Test
  public void retriedUpdatingListenerJobShouldHaveCorrectHeadersFromUpdateCommand() {
    testRetriedListenerJobHasExpectedHeaders(
        ZeebeTaskListenerEventType.updating,
        pik ->
            ENGINE
                .userTask()
                .ofInstance(pik)
                .withCandidateUsers("bob", "alice")
                .withDueDate("new_due_date")
                .withPriority(88)
                .withAction("custom_update")
                .update(),
        Map.of(
            Protocol.USER_TASK_CANDIDATE_USERS_HEADER_NAME,
            "[\"bob\",\"alice\"]",
            Protocol.USER_TASK_DUE_DATE_HEADER_NAME,
            "new_due_date",
            Protocol.USER_TASK_PRIORITY_HEADER_NAME,
            "88",
            Protocol.USER_TASK_CHANGED_ATTRIBUTES_HEADER_NAME,
            "[\"candidateUsersList\",\"dueDate\",\"priority\"]",
            Protocol.USER_TASK_ACTION_HEADER_NAME,
            "custom_update"));
  }

  /**
   * Verifies that retried listener job has access to the original command's changed attributes by
   * asserting the presence of expected custom headers on retried, and subsequent listener job.
   */
  private void testRetriedListenerJobHasExpectedHeaders(
      final ZeebeTaskListenerEventType eventType,
      final Consumer<Long> transitionTrigger,
      final Map<String, String> expectedHeaders) {

    // given: process with two listener jobs of the same type (first with a failing expression)
    final long processInstanceKey =
        helper.createProcessInstance(
            helper.createProcessWithZeebeUserTask(
                task ->
                    task.zeebeTaskListener(l -> l.eventType(eventType).type(listenerType))
                        .zeebeTaskListener(
                            l -> l.eventType(eventType).typeExpression("missing_var_name"))));

    // when: trigger transition that causes listener jobs to be created
    transitionTrigger.accept(processInstanceKey);

    // then: assert first listener job uses expected headers and complete it
    final Consumer<JobRecordValue> assertHeaders =
        job ->
            assertThat(job.getCustomHeaders())
                .describedAs(
                    "Listener job should contain headers changed by the original user task command")
                .containsAllEntriesOf(expectedHeaders);

    helper.assertActivatedJob(processInstanceKey, listenerType, assertHeaders);
    ENGINE.job().ofInstance(processInstanceKey).withType(listenerType).complete();

    // then: assert incident due to missing variable in listener property expression
    final var incident =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    Assertions.assertThat(incident.getValue())
        .hasProcessInstanceKey(processInstanceKey)
        .hasErrorType(ErrorType.EXTRACT_VALUE_ERROR)
        .hasErrorMessage(
            """
                Expected result of the expression 'missing_var_name' to be 'STRING', but was 'NULL'. \
                The evaluation reported the following warnings:
                [NO_VARIABLE_FOUND] No variable found with name 'missing_var_name'""");

    // when: fix missing variable and resolve the incident
    ENGINE
        .variables()
        .ofScope(processInstanceKey)
        .withDocument(Map.of("missing_var_name", listenerType + "_2"))
        .update();
    ENGINE.incident().ofInstance(processInstanceKey).withKey(incident.getKey()).resolve();

    // then: retried and remaining listener jobs should receive the same headers
    helper.assertActivatedJob(processInstanceKey, listenerType, assertHeaders);
    helper.completeRecreatedJobWithType(processInstanceKey, listenerType);
    helper.assertActivatedJob(processInstanceKey, listenerType + "_2", assertHeaders);
  }
}
