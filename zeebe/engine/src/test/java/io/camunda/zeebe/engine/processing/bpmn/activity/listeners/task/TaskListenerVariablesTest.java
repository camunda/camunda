/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.bpmn.activity.listeners.task;

import static io.camunda.zeebe.engine.processing.job.JobCompleteProcessor.TL_JOB_COMPLETION_WITH_VARS_NOT_SUPPORTED_MESSAGE;
import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.record.value.usertask.UserTaskRecord;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.protocol.record.intent.VariableDocumentIntent;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

/** Tests verifying that a task listener can access and provide variables. */
public class TaskListenerVariablesTest {
  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();
  private static final String USER_TASK_ELEMENT_ID = "my_user_task";

  @Rule public final TestWatcher watcher = new RecordingExporterTestWatcher();
  private final TaskListenerTestHelper helper = new TaskListenerTestHelper(ENGINE);

  private String listenerType;

  @Before
  public void setup() {
    listenerType = "my_listener_" + UUID.randomUUID();
  }

  @Test
  @Ignore(
      "Ignored due to task listener job completion rejection when variables payload is provided (issue #24056). Re-enable after implementing issue #23702.")
  public void shouldMakeVariablesFromPreviousTaskListenersAvailableToSubsequentListeners() {
    final long processInstanceKey =
        helper.createProcessInstance(
            helper.createProcessWithCompletingTaskListeners(listenerType, listenerType + "_2"));

    ENGINE.userTask().ofInstance(processInstanceKey).complete();

    // when
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(listenerType)
        .withVariable("listener_1_var", "foo")
        .complete();

    // then: `listener_1_var` variable accessible in subsequent TL
    final var jobActivated = helper.activateJob(processInstanceKey, listenerType + "_2");
    assertThat(jobActivated.getVariables()).contains(entry("listener_1_var", "foo"));
  }

  @Test
  @Ignore(
      "Ignored due to task listener job completion rejection when variables payload is provided (issue #24056). Re-enable after implementing issue #23702.")
  public void shouldNotExposeTaskListenerVariablesOutsideUserTaskScope() {
    // given: deploy a process with a user task having complete TL and service task following it
    final long processInstanceKey =
        helper.createProcessInstance(
            helper.createProcess(
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
    final var subsequentServiceTaskJob =
        helper.activateJob(processInstanceKey, "subsequent_service_task");
    assertThat(subsequentServiceTaskJob.getVariables()).doesNotContainKey("my_listener_var");
    helper.completeJobs(processInstanceKey, "subsequent_service_task");
  }

  @Test
  @Ignore(
      "Ignored due to task listener job completion rejection when variables payload is provided (issue #24056). Re-enable after implementing issue #23702.")
  public void shouldAllowTaskListenerVariablesInUserTaskOutputMappings() {
    // given: deploy a process with a user task having complete TL and service task following it
    final long processInstanceKey =
        helper.createProcessInstance(
            helper.createProcess(
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
    final var subsequentServiceTaskJob =
        helper.activateJob(processInstanceKey, "subsequent_service_task");
    assertThat(subsequentServiceTaskJob.getVariables()).containsEntry("userTaskOutput", "bar_abc");
    helper.completeJobs(processInstanceKey, "subsequent_service_task");
  }

  @Test
  public void shouldProvideVariablesOfTaskCompletionToCompleteTaskListener() {
    // given
    final var processInstanceKey =
        helper.createProcessInstanceWithVariables(
            helper.createProcessWithCompletingTaskListeners(listenerType), Map.of("foo", "bar"));

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
        helper.createProcessInstanceWithVariables(
            helper.createProcessWithCompletingTaskListeners(listenerType), Map.of("foo", "bar"));

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
        helper.createProcessInstanceWithVariables(
            helper.createProcessWithCompletingTaskListeners(listenerType),
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
        helper.createProcessInstance(helper.createProcessWithCompletingTaskListeners(listenerType));

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

  /* VariableDocument update tests */
  @Test
  public void shouldRejectVariableUpdateWithPropagateSemanticForUserTask() {
    // given: a process instance with a Camunda user task
    final long processInstanceKey =
        helper.createProcessInstance(helper.createProcessWithZeebeUserTask(t -> t));
    final var createdUserTaskRecord =
        RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    // when: attempting to update variables with 'PROPAGATE' semantic for a user task instance
    final var variableUpdateRejection =
        ENGINE
            .variables()
            .ofScope(createdUserTaskRecord.getValue().getElementInstanceKey())
            .withDocument(Map.of("approvalStatus", "SUBMITTED"))
            .withPropagateSemantic()
            .expectRejection()
            .update();

    // then
    Assertions.assertThat(variableUpdateRejection)
        .describedAs(
            "Expect rejection when trying to update variables for a user task instance with 'PROPAGATE' semantic")
        .hasRecordType(RecordType.COMMAND_REJECTION)
        .hasValueType(ValueType.VARIABLE_DOCUMENT)
        .hasRejectionReason(
            "Expected to update variables for user task with key '%d', but updates with 'PROPAGATE' semantic are not supported yet."
                .formatted(createdUserTaskRecord.getKey()));
  }

  @Test
  public void shouldRejectVariableUpdateWhenUserTaskIsNotInCreatedState() {
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
  public void
      shouldUpdateVariablesAndPassUserTaskUpdateTransitionWhenUserTaskHasNoUpdatingListeners() {
    // given: a process instance with a camunda user task
    final long processInstanceKey =
        helper.createProcessInstanceWithVariables(
            helper.createProcessWithZeebeUserTask(t -> t), Map.of("approvalStatus", "PENDING"));

    final var createdUserTaskRecord =
        RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    // when: updating task-scoped variables
    final var variableUpdateRecord =
        ENGINE
            .variables()
            .ofScope(createdUserTaskRecord.getValue().getElementInstanceKey())
            .withDocument(Map.of("approvalStatus", "SUBMITTED"))
            .withLocalSemantic()
            .update();

    // then: variable update should be successful and trigger the user task update transition
    Assertions.assertThat(variableUpdateRecord)
        .describedAs("Expect variables to be successfully updated for a user task")
        .hasRecordType(RecordType.EVENT)
        .hasIntent(VariableDocumentIntent.UPDATED)
        .hasValueType(ValueType.VARIABLE_DOCUMENT);

    Assertions.assertThat(
            RecordingExporter.variableRecords(VariableIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .withScopeKey(createdUserTaskRecord.getValue().getElementInstanceKey())
                .getFirst()
                .getValue())
        .describedAs("Expect the variable to be created at the local scope of user task element")
        .hasName("approvalStatus")
        .hasValue("\"SUBMITTED\"");

    assertThat(
            RecordingExporter.userTaskRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limit(r -> r.getIntent() == UserTaskIntent.UPDATED))
        .extracting(Record::getIntent, r -> r.getValue().getChangedAttributes())
        .describedAs(
            "Expect the user task to pass the update transition with variables as a changed attribute")
        .containsSequence(
            tuple(UserTaskIntent.UPDATING, List.of(UserTaskRecord.VARIABLES)),
            tuple(UserTaskIntent.UPDATED, List.of(UserTaskRecord.VARIABLES)));
  }

  @Test
  public void
      shouldActivateUpdatingListenerJobWithCorrectChangedAttributesHeaderAndVariablesOnTaskVariableUpdate() {
    // given: a process instance with a Camunda user task wit `updating` listener
    final long processInstanceKey =
        helper.createProcessInstanceWithVariables(
            helper.createProcessWithZeebeUserTask(
                t -> t.zeebeTaskListener(l -> l.updating().type(listenerType + "_updating"))),
            Map.of("approvalStatus", "PENDING", "employeeId", "E12345"));

    final var userTaskElementInstanceKey = helper.getUserTaskElementInstanceKey(processInstanceKey);

    // when: updating task-scoped variables
    ENGINE
        .variables()
        .ofScope(userTaskElementInstanceKey)
        .withDocument(Map.of("approvalStatus", "APPROVED"))
        .withLocalSemantic()
        .expectPartialUpdate()
        .update();

    // then: expect a job to be activated for the first `updating` listener
    helper.assertActivatedJob(
        processInstanceKey,
        listenerType + "_updating",
        job -> {
          assertThat(job.getCustomHeaders())
              .describedAs("Expect job custom headers to indicate that `variables` were changed")
              .contains(
                  org.assertj.core.api.Assertions.entry(
                      Protocol.USER_TASK_CHANGED_ATTRIBUTES_HEADER_NAME, "[\"variables\"]"));

          assertThat(job.getVariables())
              .describedAs(
                  "Expect job variables to include the updated 'approvalStatus' and retain 'employeeId'")
              .containsExactly(
                  org.assertj.core.api.Assertions.entry("approvalStatus", "APPROVED"),
                  org.assertj.core.api.Assertions.entry("employeeId", "E12345"));
        });
  }
}
