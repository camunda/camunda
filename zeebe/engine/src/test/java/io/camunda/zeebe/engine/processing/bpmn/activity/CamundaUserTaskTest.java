/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.bpmn.activity;

import static io.camunda.zeebe.engine.processing.variable.mapping.VariableValue.variable;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.engine.state.immutable.UserTaskState;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.builder.UserTaskBuilder;
import io.camunda.zeebe.protocol.impl.record.value.usertask.UserTaskRecord;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.protocol.record.value.UserTaskRecordValue;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public final class CamundaUserTaskTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  private static final String PROCESS_ID = "process";
  private static final String FOLLOW_UP_DATE = "2023-02-28T08:16:23+02:00";
  private static final String DUE_DATE = "2023-02-28T09:16:23+02:00";

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  private UserTaskState userTaskState;

  private static BpmnModelInstance process() {
    return process(b -> {});
  }

  private static BpmnModelInstance process(final Consumer<UserTaskBuilder> consumer) {
    final var builder =
        Bpmn.createExecutableProcess(PROCESS_ID).startEvent().userTask("task").zeebeUserTask();
    consumer.accept(builder);

    return builder.endEvent().done();
  }

  @Before
  public void setUp() {
    userTaskState = ENGINE.getProcessingState().getUserTaskState();
  }

  @Test
  public void shouldActivateUserTask() {
    // given
    ENGINE.deployment().withXmlResource(process()).deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .withElementType(BpmnElementType.USER_TASK)
                .withTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER)
                .limit(3))
        .extracting(Record::getRecordType, Record::getIntent)
        .containsSequence(
            tuple(RecordType.COMMAND, ProcessInstanceIntent.ACTIVATE_ELEMENT),
            tuple(RecordType.EVENT, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(RecordType.EVENT, ProcessInstanceIntent.ELEMENT_ACTIVATED));

    final Record<ProcessInstanceRecordValue> userTask =
        RecordingExporter.processInstanceRecords()
            .withProcessInstanceKey(processInstanceKey)
            .withTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER)
            .withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withElementType(BpmnElementType.USER_TASK)
            .getFirst();

    Assertions.assertThat(userTask.getValue())
        .hasElementId("task")
        .hasBpmnElementType(BpmnElementType.USER_TASK)
        .hasFlowScopeKey(processInstanceKey)
        .hasBpmnProcessId(PROCESS_ID)
        .hasProcessInstanceKey(processInstanceKey);
  }

  @Test
  public void shouldActivateUserTaskForCustomTenant() {
    // given
    final String tenantId = "foo";
    ENGINE.deployment().withXmlResource(process()).withTenantId(tenantId).deploy();

    // when
    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).withTenantId(tenantId).create();

    // then
    final Record<ProcessInstanceRecordValue> userTask =
        RecordingExporter.processInstanceRecords()
            .withProcessInstanceKey(processInstanceKey)
            .withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withElementType(BpmnElementType.USER_TASK)
            .withTenantId(tenantId)
            .getFirst();

    Assertions.assertThat(userTask.getValue()).hasTenantId(tenantId);
  }

  @Test
  public void shouldCreateUserTask() {
    // given
    ENGINE.deployment().withXmlResource(process()).deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    final Record<ProcessInstanceRecordValue> taskActivated =
        RecordingExporter.processInstanceRecords()
            .withProcessInstanceKey(processInstanceKey)
            .withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withElementType(BpmnElementType.USER_TASK)
            .getFirst();

    final Record<UserTaskRecordValue> userTask =
        RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    Assertions.assertThat(userTask.getValue())
        .hasElementInstanceKey(taskActivated.getKey())
        .hasElementId(taskActivated.getValue().getElementId())
        .hasProcessDefinitionKey(taskActivated.getValue().getProcessDefinitionKey())
        .hasBpmnProcessId(taskActivated.getValue().getBpmnProcessId())
        .hasProcessDefinitionVersion(taskActivated.getValue().getVersion());

    assertThat(userTask.getValue().getUserTaskKey()).isGreaterThan(0L);
  }

  @Test
  public void shouldCreateUserTaskWithCustomHeaders() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(process(t -> t.zeebeTaskHeader("a", "b").zeebeTaskHeader("c", "d")))
        .deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    final Record<UserTaskRecordValue> userTask =
        RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    final Map<String, String> customHeaders = userTask.getValue().getCustomHeaders();
    assertThat(customHeaders).hasSize(2).containsEntry("a", "b").containsEntry("c", "d");
  }

  @Test
  public void shouldPickUpCustomFormForUserTask() {
    // given
    final String externalReference = "http://example.com/my-external-form";

    ENGINE
        .deployment()
        .withXmlResource(process(t -> t.zeebeExternalFormReference(externalReference)))
        .deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    final Record<UserTaskRecordValue> userTask =
        RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    Assertions.assertThat(userTask.getValue())
        .hasFormKey(-1L)
        .hasExternalFormReference(externalReference);
  }

  @Test
  public void shouldPickUpCustomFormExpressionForUserTask() {
    // given
    final String externalReference = "http://example.com/my-external-form";

    ENGINE
        .deployment()
        .withXmlResource(process(t -> t.zeebeExternalFormReferenceExpression("externalReference")))
        .deploy();

    // when
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable("externalReference", externalReference)
            .create();

    // then
    final Record<UserTaskRecordValue> userTask =
        RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    Assertions.assertThat(userTask.getValue())
        .hasFormKey(-1L)
        .hasExternalFormReference(externalReference);
  }

  @Test
  public void shouldNotPickUpEmbeddedFormForUserTask() {
    // given
    final String formKey = Strings.newRandomValidBpmnId();

    ENGINE
        .deployment()
        .withXmlResource(process(t -> t.zeebeFormKey(formKey).zeebeExternalFormReference("foo")))
        .deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    final Record<UserTaskRecordValue> userTask =
        RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    Assertions.assertThat(userTask.getValue()).hasFormKey(-1L);
  }

  @Test
  public void shouldNotPickUpEmbeddedFormWithJsonForUserTask() {
    // given
    final String formKey = Strings.newRandomValidBpmnId();

    ENGINE
        .deployment()
        .withXmlResource(
            process(
                t ->
                    t.zeebeUserTaskForm(formKey, "User Task Form")
                        .zeebeExternalFormReference("foo")))
        .deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    final Record<UserTaskRecordValue> userTask =
        RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    Assertions.assertThat(userTask.getValue()).hasFormKey(-1L);
  }

  @Test
  public void shouldCreateUserTaskWithAssignee() {
    // given
    ENGINE.deployment().withXmlResource(process(t -> t.zeebeAssignee("alice"))).deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    final Record<UserTaskRecordValue> userTask =
        RecordingExporter.userTaskRecords(UserTaskIntent.ASSIGNED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    Assertions.assertThat(userTask.getValue()).hasAssignee("alice");
  }

  @Test
  public void shouldCreateUserTaskWithStaticNumberValueAssignee() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(process(t -> t.zeebeAssignee("1234567891011121314")))
        .deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    final Record<UserTaskRecordValue> userTask =
        RecordingExporter.userTaskRecords(UserTaskIntent.ASSIGNED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    Assertions.assertThat(userTask.getValue()).hasAssignee("1234567891011121314");
  }

  @Test
  public void shouldCreateUserTaskWithEvaluatedAssigneeExpression() {
    // given
    ENGINE.deployment().withXmlResource(process(t -> t.zeebeAssigneeExpression("user"))).deploy();

    // when
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariables(Map.of("user", "alice"))
            .create();

    // then
    final Record<UserTaskRecordValue> userTask =
        RecordingExporter.userTaskRecords(UserTaskIntent.ASSIGNED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    Assertions.assertThat(userTask.getValue()).hasAssignee("alice");
  }

  @Test
  public void shouldCreateUserTaskWithEmptyEvaluatedAssigneeExpression() {
    // given
    ENGINE.deployment().withXmlResource(process(t -> t.zeebeAssigneeExpression("user"))).deploy();

    // when
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariables(Map.of("user", ""))
            .create();

    // then
    final Record<UserTaskRecordValue> userTask =
        RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    Assertions.assertThat(userTask.getValue()).hasAssignee("");
  }

  @Test
  public void shouldCreateUserTaskWithCandidateGroups() {
    // given
    ENGINE.deployment().withXmlResource(process(t -> t.zeebeCandidateGroups("alice,bob"))).deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    final Record<UserTaskRecordValue> userTask =
        RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    Assertions.assertThat(userTask.getValue()).hasCandidateGroupsList("alice", "bob");
  }

  @Test
  public void shouldCreateUserTaskWithEvaluatedCandidateGroupsExpression() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(process(t -> t.zeebeCandidateGroupsExpression("users")))
        .deploy();

    // when
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariables("{ \"users\": [\"alice\", \"bob\"] }")
            .create();

    // then
    final Record<UserTaskRecordValue> userTask =
        RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    Assertions.assertThat(userTask.getValue()).hasCandidateGroupsList("alice", "bob");
  }

  @Test
  public void shouldCreateUserTaskWithCandidateUsers() {
    // given
    ENGINE.deployment().withXmlResource(process(t -> t.zeebeCandidateUsers("jack,rose"))).deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    final Record<UserTaskRecordValue> userTask =
        RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    Assertions.assertThat(userTask.getValue()).hasCandidateUsersList("jack", "rose");
  }

  @Test
  public void shouldCreateUserTaskWithEvaluatedCandidateUsersExpression() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(process(t -> t.zeebeCandidateUsersExpression("users")))
        .deploy();

    // when
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariables("{ \"users\": [\"jack\", \"rose\"] }")
            .create();

    // then
    final Record<UserTaskRecordValue> userTask =
        RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    Assertions.assertThat(userTask.getValue()).hasCandidateUsersList("jack", "rose");
  }

  @Test
  public void shouldCreateUserTaskWithDueDate() {
    // given
    ENGINE.deployment().withXmlResource(process(t -> t.zeebeDueDate(DUE_DATE))).deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    final Record<UserTaskRecordValue> userTask =
        RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    Assertions.assertThat(userTask.getValue()).hasDueDate(DUE_DATE);
  }

  @Test
  public void shouldCreateUserTaskWithEvaluatedDueDateExpression() {
    // given
    ENGINE.deployment().withXmlResource(process(t -> t.zeebeDueDateExpression("dueDate"))).deploy();

    // when
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariables(Map.of("dueDate", DUE_DATE))
            .create();

    // then
    final Record<UserTaskRecordValue> userTask =
        RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    Assertions.assertThat(userTask.getValue()).hasDueDate(DUE_DATE);
  }

  @Test
  public void shouldCreateUserTaskAndIgnoreEmptyDueDate() {
    // given
    ENGINE.deployment().withXmlResource(process(t -> t.zeebeDueDate(""))).deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    final Record<UserTaskRecordValue> userTask =
        RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    Assertions.assertThat(userTask.getValue()).hasDueDate("");
  }

  @Test
  public void shouldCreateUserTaskAndIgnoreEmptyEvaluatedDueDateExpression() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(process(t -> t.zeebeDueDateExpression("=dueDate")))
        .deploy();

    // when
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariables(Map.of("dueDate", ""))
            .create();

    // then
    final Record<UserTaskRecordValue> userTask =
        RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    Assertions.assertThat(userTask.getValue()).hasDueDate("");
  }

  @Test
  public void shouldCreateUserTaskAndIgnoreNullEvaluatedDueDateExpression() {
    // given
    ENGINE.deployment().withXmlResource(process(t -> t.zeebeDueDateExpression("=null"))).deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    final Record<UserTaskRecordValue> userTask =
        RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    Assertions.assertThat(userTask.getValue()).hasDueDate("");
  }

  @Test
  public void shouldCreateUserTaskWithFollowUpDate() {
    // given
    ENGINE.deployment().withXmlResource(process(t -> t.zeebeFollowUpDate(FOLLOW_UP_DATE))).deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    final Record<UserTaskRecordValue> userTask =
        RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    Assertions.assertThat(userTask.getValue()).hasFollowUpDate(FOLLOW_UP_DATE);
  }

  @Test
  public void shouldCreateUserTaskWithEvaluatedFollowUpDateExpression() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(process(t -> t.zeebeFollowUpDateExpression("followUpDate")))
        .deploy();

    // when
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariables(Map.of("followUpDate", FOLLOW_UP_DATE))
            .create();

    // then
    final Record<UserTaskRecordValue> userTask =
        RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    Assertions.assertThat(userTask.getValue()).hasFollowUpDate(FOLLOW_UP_DATE);
  }

  @Test
  public void shouldCreateUserTaskAndIgnoreEmptyFollowUpDate() {
    // given
    ENGINE.deployment().withXmlResource(process(t -> t.zeebeFollowUpDate(""))).deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    final Record<UserTaskRecordValue> userTask =
        RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    Assertions.assertThat(userTask.getValue()).hasFollowUpDate("");
  }

  @Test
  public void shouldCreateUserTaskAndIgnoreEmptyEvaluatedFollowUpDateExpression() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(process(t -> t.zeebeFollowUpDateExpression("=followUpDate")))
        .deploy();

    // when
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariables(Map.of("followUpDate", ""))
            .create();

    // then
    final Record<UserTaskRecordValue> userTask =
        RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    Assertions.assertThat(userTask.getValue()).hasFollowUpDate("");
  }

  @Test
  public void shouldCreateUserTaskAndIgnoreNullEvaluatedFollowUpDateExpression() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(process(t -> t.zeebeFollowUpDateExpression("=null")))
        .deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    final Record<UserTaskRecordValue> userTask =
        RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    Assertions.assertThat(userTask.getValue()).hasFollowUpDate("");
  }

  @Test
  public void shouldCreateUserTaskWithCreationTimestampGreaterThanZero() {
    // given
    ENGINE.deployment().withXmlResource(process()).deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    final Record<UserTaskRecordValue> userTask =
        RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    assertThat(userTask.getValue().getCreationTimestamp()).isGreaterThan(0L);
  }

  @Test
  public void shouldAssignUserTask() {
    // given
    ENGINE.deployment().withXmlResource(process()).deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    ENGINE.userTask().ofInstance(processInstanceKey).withAssignee("foo").assign();

    // then
    final UserTaskRecordValue createdUserTask =
        RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst()
            .getValue();

    assertThat(
            RecordingExporter.userTaskRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limit(r -> r.getIntent() == UserTaskIntent.ASSIGNED))
        .extracting(Record::getValueType, Record::getIntent)
        .containsSubsequence(
            tuple(ValueType.USER_TASK, UserTaskIntent.ASSIGNING),
            tuple(ValueType.USER_TASK, UserTaskIntent.ASSIGNED));

    Assertions.assertThat(
            RecordingExporter.userTaskRecords(UserTaskIntent.ASSIGNED)
                .withProcessInstanceKey(processInstanceKey)
                .getFirst()
                .getValue())
        .hasAssignee("foo");

    Assertions.assertThat(userTaskState.getUserTask(createdUserTask.getUserTaskKey()))
        .hasAssignee("foo");
  }

  @Test
  public void shouldCancelUserTask() {
    // given
    ENGINE.deployment().withXmlResource(process()).deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    ENGINE.processInstance().withInstanceKey(processInstanceKey).cancel();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceTerminated())
        .extracting(r -> tuple(r.getValue().getBpmnElementType(), r.getIntent()))
        .containsSubsequence(
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.TERMINATE_ELEMENT),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_TERMINATING),
            tuple(BpmnElementType.USER_TASK, ProcessInstanceIntent.TERMINATE_ELEMENT),
            tuple(BpmnElementType.USER_TASK, ProcessInstanceIntent.ELEMENT_TERMINATING),
            tuple(BpmnElementType.USER_TASK, ProcessInstanceIntent.ELEMENT_TERMINATED),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_TERMINATED));

    assertThat(
            RecordingExporter.userTaskRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limit(r -> r.getIntent() == UserTaskIntent.CANCELED))
        .extracting(Record::getValueType, Record::getIntent)
        .containsSubsequence(
            tuple(ValueType.USER_TASK, UserTaskIntent.CANCELING),
            tuple(ValueType.USER_TASK, UserTaskIntent.CANCELED));
  }

  @Test
  public void shouldClaimUserTask() {
    // given
    ENGINE.deployment().withXmlResource(process()).deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    ENGINE.userTask().ofInstance(processInstanceKey).withAssignee("foo").claim();

    // then
    final UserTaskRecordValue createdUserTask =
        RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst()
            .getValue();

    assertThat(
            RecordingExporter.userTaskRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limit(r -> r.getIntent() == UserTaskIntent.ASSIGNED))
        .extracting(Record::getValueType, Record::getIntent)
        .containsSubsequence(
            tuple(ValueType.USER_TASK, UserTaskIntent.CLAIMING),
            tuple(ValueType.USER_TASK, UserTaskIntent.ASSIGNED));

    Assertions.assertThat(
            RecordingExporter.userTaskRecords(UserTaskIntent.ASSIGNED)
                .withProcessInstanceKey(processInstanceKey)
                .getFirst()
                .getValue())
        .hasAssignee("foo");

    Assertions.assertThat(userTaskState.getUserTask(createdUserTask.getUserTaskKey()))
        .hasAssignee("foo");
  }

  @Test
  public void shouldUpdateUserTaskAttributes() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            process(
                t ->
                    t.zeebeCandidateGroups("foo, bar")
                        .zeebeCandidateUsers("oof, rab")
                        .zeebeFollowUpDate("2023-03-02T15:35+02:00")
                        .zeebeDueDate("2023-03-02T16:35+02:00")
                        .zeebeTaskPriority("90")))
        .deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when
    ENGINE.userTask().ofInstance(processInstanceKey).withAllAttributesChanged().update();

    // then
    final UserTaskRecordValue createdUserTask =
        RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst()
            .getValue();

    assertThat(
            RecordingExporter.userTaskRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limit(r -> r.getIntent() == UserTaskIntent.UPDATED))
        .extracting(Record::getValueType, Record::getIntent)
        .containsSubsequence(
            tuple(ValueType.USER_TASK, UserTaskIntent.UPDATING),
            tuple(ValueType.USER_TASK, UserTaskIntent.UPDATED));

    Assertions.assertThat(createdUserTask)
        .hasCandidateGroupsList("foo", "bar")
        .hasCandidateUsersList("oof", "rab")
        .hasDueDate("2023-03-02T16:35+02:00")
        .hasFollowUpDate("2023-03-02T15:35+02:00")
        .hasNoChangedAttributes()
        .hasPriority(90);

    Assertions.assertThat(
            RecordingExporter.userTaskRecords(UserTaskIntent.UPDATED)
                .withProcessInstanceKey(processInstanceKey)
                .getFirst()
                .getValue())
        .hasNoCandidateGroupsList()
        .hasNoCandidateUsersList()
        .hasDueDate("")
        .hasFollowUpDate("")
        .hasPriority(50)
        .hasChangedAttributes(
            UserTaskRecord.CANDIDATE_GROUPS,
            UserTaskRecord.CANDIDATE_USERS,
            UserTaskRecord.DUE_DATE,
            UserTaskRecord.FOLLOW_UP_DATE,
            UserTaskRecord.PRIORITY);

    Assertions.assertThat(userTaskState.getUserTask(createdUserTask.getUserTaskKey()))
        .hasNoCandidateGroupsList()
        .hasNoCandidateUsersList()
        .hasDueDate("")
        .hasFollowUpDate("")
        .hasPriority(50)
        .hasNoChangedAttributes();
  }

  @Test
  public void shouldCompleteUserTask() {
    // given
    ENGINE.deployment().withXmlResource(process()).deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    ENGINE.userTask().ofInstance(processInstanceKey).complete();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> tuple(r.getValue().getBpmnElementType(), r.getIntent()))
        .containsSubsequence(
            tuple(BpmnElementType.USER_TASK, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(BpmnElementType.USER_TASK, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.SEQUENCE_FLOW, ProcessInstanceIntent.SEQUENCE_FLOW_TAKEN),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED));

    assertThat(
            RecordingExporter.userTaskRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limit(r -> r.getIntent() == UserTaskIntent.COMPLETED))
        .extracting(Record::getValueType, Record::getIntent)
        .containsSubsequence(
            tuple(ValueType.USER_TASK, UserTaskIntent.COMPLETING),
            tuple(ValueType.USER_TASK, UserTaskIntent.COMPLETED));
  }

  @Test
  public void shouldCompleteUserTaskWithVariables() {
    // given
    ENGINE.deployment().withXmlResource(process()).deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    ENGINE.userTask().ofInstance(processInstanceKey).withVariable("foo", "bar").complete();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> tuple(r.getValue().getBpmnElementType(), r.getIntent()))
        .containsSubsequence(
            tuple(BpmnElementType.USER_TASK, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(BpmnElementType.USER_TASK, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.SEQUENCE_FLOW, ProcessInstanceIntent.SEQUENCE_FLOW_TAKEN),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED));

    assertThat(
            RecordingExporter.userTaskRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limit(r -> r.getIntent() == UserTaskIntent.COMPLETED))
        .extracting(Record::getValueType, Record::getIntent)
        .containsSubsequence(
            tuple(ValueType.USER_TASK, UserTaskIntent.COMPLETING),
            tuple(ValueType.USER_TASK, UserTaskIntent.COMPLETED));

    assertThat(
            RecordingExporter.variableRecords().withProcessInstanceKey(processInstanceKey).limit(1))
        .extracting(Record::getValue)
        .extracting(v -> variable(v.getName(), v.getValue()))
        .containsExactly(variable("foo", "\"bar\""));
  }

  @Test
  public void shouldResolveIncidentsWhenTerminating() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            process(
                t ->
                    t.zeebeInputExpression(
                        "assert(nonexisting_variable, nonexisting_variable != null)", "target")))
        .deploy();
    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).withVariable("foo", 10).create();
    assertThat(
            RecordingExporter.incidentRecords().withProcessInstanceKey(processInstanceKey).limit(1))
        .extracting(Record::getIntent)
        .containsExactly(IncidentIntent.CREATED);

    // when
    ENGINE.processInstance().withInstanceKey(processInstanceKey).cancel();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceTerminated())
        .extracting(r -> tuple(r.getValue().getBpmnElementType(), r.getIntent()))
        .containsSubsequence(
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_TERMINATING),
            tuple(BpmnElementType.USER_TASK, ProcessInstanceIntent.ELEMENT_TERMINATING),
            tuple(BpmnElementType.USER_TASK, ProcessInstanceIntent.ELEMENT_TERMINATED),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_TERMINATED));

    assertThat(
            RecordingExporter.incidentRecords().withProcessInstanceKey(processInstanceKey).limit(2))
        .extracting(Record::getIntent)
        .containsExactly(IncidentIntent.CREATED, IncidentIntent.RESOLVED);
  }

  @Test
  public void shouldCreateUserTaskWithStaticPriority() {
    // given
    ENGINE.deployment().withXmlResource(process(t -> t.zeebeTaskPriorityExpression("20"))).deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    final Record<UserTaskRecordValue> userTask =
        RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    Assertions.assertThat(userTask.getValue()).hasPriority(20);
  }

  @Test
  public void shouldCreateUserTaskWithPriorityExpression() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(process(t -> t.zeebeTaskPriorityExpression("10+task_priority")))
        .deploy();

    // when
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable("task_priority", 13L)
            .create();

    // then
    final Record<UserTaskRecordValue> userTask =
        RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    Assertions.assertThat(userTask.getValue()).hasPriority(23);
  }

  @Test
  public void shouldCreateUserTaskWithDefaultPriority() {
    // given
    ENGINE.deployment().withXmlResource(process()).deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    final Record<UserTaskRecordValue> userTask =
        RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    Assertions.assertThat(userTask.getValue()).hasPriority(50);
  }
}
