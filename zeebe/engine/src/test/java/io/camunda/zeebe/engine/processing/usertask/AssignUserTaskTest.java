/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.usertask;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.builder.UserTaskBuilder;
import io.camunda.zeebe.protocol.impl.record.value.usertask.UserTaskRecord;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.protocol.record.value.UserTaskRecordValue;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.apache.commons.lang3.StringUtils;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public final class AssignUserTaskTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();
  private static final String PROCESS_ID = "process";
  private static final String DEFAULT_ACTION = "assign";

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  private static BpmnModelInstance process() {
    return process(b -> {});
  }

  private static BpmnModelInstance process(final Consumer<UserTaskBuilder> consumer) {
    final var builder =
        Bpmn.createExecutableProcess(PROCESS_ID).startEvent().userTask("task").zeebeUserTask();

    consumer.accept(builder);

    return builder.endEvent().done();
  }

  @Test
  public void shouldEmitAssignEventsForUserTask() {
    // given
    ENGINE.deployment().withXmlResource(process()).deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    final long userTaskKey =
        RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst()
            .getKey();

    // when
    final var assigningRecord = ENGINE.userTask().withKey(userTaskKey).withAssignee("foo").assign();

    // then
    Assertions.assertThat(assigningRecord)
        .hasRecordType(RecordType.EVENT)
        .hasIntent(UserTaskIntent.ASSIGNING);

    final var assigningRecordValue = assigningRecord.getValue();
    final var assignedRecordValue =
        RecordingExporter.userTaskRecords(UserTaskIntent.ASSIGNED).getFirst().getValue();

    assertThat(List.of(assigningRecordValue, assignedRecordValue))
        .describedAs(
            "Ensure ASSIGNING and ASSIGNED records have consistent attribute values "
                + "as dependent applications will rely on them to update user task data internally")
        .allSatisfy(
            recordValue ->
                Assertions.assertThat(recordValue)
                    .hasUserTaskKey(userTaskKey)
                    .hasAction(DEFAULT_ACTION)
                    .hasAssignee("foo")
                    .hasOnlyChangedAttributes(UserTaskRecord.ASSIGNEE)
                    .hasTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER));
  }

  @Test
  public void shouldEmitAssignEventsForUserTaskWhenItWasCreatedWithTheDefinedAssigneeProperty() {
    // given
    final var assignee = "merry";
    final var action = StringUtils.EMPTY;
    ENGINE.deployment().withXmlResource(process(t -> t.zeebeAssignee(assignee))).deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    final long userTaskKey =
        RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst()
            .getKey();

    // then
    assertThat(
            RecordingExporter.userTaskRecords()
                .withProcessInstanceKey(processInstanceKey)
                .withRecordKey(userTaskKey)
                .limit(r -> r.getIntent() == UserTaskIntent.ASSIGNED))
        .as(
            "Verify the sequence of intents, `assignee` `action` and `changedAttributes` properties emitted for the user task")
        .extracting(
            Record::getIntent,
            r -> r.getValue().getAssignee(),
            r -> r.getValue().getAction(),
            r -> r.getValue().getChangedAttributes())
        .containsExactly(
            tuple(UserTaskIntent.CREATING, StringUtils.EMPTY, action, List.of()),
            tuple(UserTaskIntent.CREATED, StringUtils.EMPTY, action, List.of()),
            // The `assignee` property isn't yet available during the `CREATED` event
            // as it becomes effective only during the assignment phase.
            tuple(UserTaskIntent.ASSIGNING, assignee, action, List.of(UserTaskRecord.ASSIGNEE)),
            tuple(UserTaskIntent.ASSIGNED, assignee, action, List.of(UserTaskRecord.ASSIGNEE)));
  }

  @Test
  public void shouldNotUpdateChangedAttributesWhenTaskAssignedToSameAssignee() {
    // given
    final var assignee = "frodo";
    ENGINE.deployment().withXmlResource(process(t -> t.zeebeAssignee(assignee))).deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    final long userTaskKey =
        RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst()
            .getKey();

    // when
    final var assigningRecord =
        ENGINE.userTask().withKey(userTaskKey).withAssignee(assignee).assign();

    // then
    Assertions.assertThat(assigningRecord)
        .hasRecordType(RecordType.EVENT)
        .hasIntent(UserTaskIntent.ASSIGNING);

    final var assigningRecordValue = assigningRecord.getValue();
    final var assignedRecordValue =
        RecordingExporter.userTaskRecords(UserTaskIntent.ASSIGNED)
            .filter(r -> r.getPosition() > assigningRecord.getPosition())
            .getFirst()
            .getValue();

    assertThat(List.of(assigningRecordValue, assignedRecordValue))
        .describedAs(
            "Ensure ASSIGNING and ASSIGNED records have consistent attribute values "
                + "as dependent applications will rely on them to update user task data internally")
        .allSatisfy(
            recordValue ->
                Assertions.assertThat(recordValue)
                    .hasUserTaskKey(userTaskKey)
                    .hasAction(DEFAULT_ACTION)
                    .hasAssignee(assignee)
                    .describedAs(
                        "Expect that `changedAttributes` is empty as the task was assigned to the same assignee")
                    .hasNoChangedAttributes()
                    .hasTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER));
  }

  @Test
  public void shouldEmitAssignEventsWithCustomAction() {
    // given
    ENGINE.deployment().withXmlResource(process()).deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    final long userTaskKey =
        RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst()
            .getKey();

    // when
    final var assigningRecord =
        ENGINE
            .userTask()
            .withKey(userTaskKey)
            .withAction("customAction")
            .withAssignee("foo")
            .assign();

    // then
    Assertions.assertThat(assigningRecord)
        .hasRecordType(RecordType.EVENT)
        .hasIntent(UserTaskIntent.ASSIGNING);

    final var assigningRecordValue = assigningRecord.getValue();
    final var assignedRecordValue =
        RecordingExporter.userTaskRecords(UserTaskIntent.ASSIGNED).getFirst().getValue();

    assertThat(List.of(assigningRecordValue, assignedRecordValue))
        .describedAs(
            "Ensure ASSIGNING and ASSIGNED records have consistent attribute values "
                + "as dependent applications will rely on them to update user task data internally")
        .allSatisfy(
            recordValue ->
                Assertions.assertThat(recordValue)
                    .hasUserTaskKey(userTaskKey)
                    .hasAction("customAction")
                    .hasAssignee("foo")
                    .hasOnlyChangedAttributes(UserTaskRecord.ASSIGNEE)
                    .hasTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER));
  }

  @Test
  public void shouldRejectAssignIfUserTaskNotFound() {
    // given
    final int key = 123;

    // when
    final var assigningRecord =
        ENGINE.userTask().withKey(key).withAssignee("foo").expectRejection().assign();

    // then
    Assertions.assertThat(assigningRecord).hasRejectionType(RejectionType.NOT_FOUND);
  }

  @Test
  public void shouldUnassignUserTaskWithNoAssignee() {
    // given
    final var initialAssignee = "pippin";
    final var unassignAction = "unassign";
    ENGINE.deployment().withXmlResource(process(b -> b.zeebeAssignee(initialAssignee))).deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when
    ENGINE.userTask().ofInstance(processInstanceKey).unassign();

    // then
    final Predicate<Record<UserTaskRecordValue>> untilUserTaskUnassignedRecord =
        r ->
            r.getIntent() == UserTaskIntent.ASSIGNED
                && r.getValue().getAction().equals(unassignAction);

    assertThat(
            RecordingExporter.userTaskRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limit(untilUserTaskUnassignedRecord))
        .as(
            "Verify the sequence of intents, `assignee`, `action` and `changedAttributes` properties emitted for the user task")
        .extracting(
            Record::getIntent,
            r -> r.getValue().getAssignee(),
            r -> r.getValue().getAction(),
            r -> r.getValue().getChangedAttributes())
        .containsExactly(
            tuple(UserTaskIntent.CREATING, "", "", List.of()),
            tuple(UserTaskIntent.CREATED, "", "", List.of()),
            // The `assignee` property isn't yet available during the `CREATING/CREATED` events
            // as it becomes effective only during the assignment phase.
            tuple(UserTaskIntent.ASSIGNING, initialAssignee, "", List.of(UserTaskRecord.ASSIGNEE)),
            tuple(UserTaskIntent.ASSIGNED, initialAssignee, "", List.of(UserTaskRecord.ASSIGNEE)),
            // records related to user task unassignment
            tuple(UserTaskIntent.ASSIGNING, "", unassignAction, List.of(UserTaskRecord.ASSIGNEE)),
            tuple(UserTaskIntent.ASSIGNED, "", unassignAction, List.of(UserTaskRecord.ASSIGNEE)));
  }

  @Test
  public void shouldRejectAssignIfUserTaskIsCompleted() {
    // given
    ENGINE.deployment().withXmlResource(process()).deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    ENGINE.userTask().ofInstance(processInstanceKey).complete();

    // when
    final var assigningRecord =
        ENGINE.userTask().ofInstance(processInstanceKey).expectRejection().assign();

    // then
    Assertions.assertThat(assigningRecord).hasRejectionType(RejectionType.NOT_FOUND);
  }

  @Test
  public void shouldAssignUserTaskForCustomTenant() {
    // given
    final String tenantId = Strings.newRandomValidIdentityId();
    final String username = Strings.newRandomValidIdentityId();
    ENGINE.tenant().newTenant().withTenantId(tenantId).create();
    ENGINE.user().newUser(username).create();
    ENGINE
        .tenant()
        .addEntity(tenantId)
        .withEntityId(username)
        .withEntityType(EntityType.USER)
        .add();
    ENGINE.deployment().withXmlResource(process()).withTenantId(tenantId).deploy();
    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).withTenantId(tenantId).create();

    // when
    final var assigningRecord =
        ENGINE.userTask().ofInstance(processInstanceKey).withAssignee("foo").assign(username);

    // then
    Assertions.assertThat(assigningRecord)
        .hasRecordType(RecordType.EVENT)
        .hasIntent(UserTaskIntent.ASSIGNING);

    final var assigningRecordValue = assigningRecord.getValue();
    final var assignedRecordValue =
        RecordingExporter.userTaskRecords(UserTaskIntent.ASSIGNED).getFirst().getValue();

    assertThat(List.of(assigningRecordValue, assignedRecordValue))
        .describedAs(
            "Ensure ASSIGNING and ASSIGNED records have consistent attribute values "
                + "as dependent applications will rely on them to update user task data internally")
        .allSatisfy(
            recordValue ->
                Assertions.assertThat(recordValue)
                    .hasAction(DEFAULT_ACTION)
                    .hasAssignee("foo")
                    .hasOnlyChangedAttributes(UserTaskRecord.ASSIGNEE)
                    .hasTenantId(tenantId));
  }

  @Test
  public void shouldRejectAssignIfTenantIsUnauthorized() {
    // given
    final String tenantId = "acme";
    final String falseTenantId = "foo";
    ENGINE.deployment().withXmlResource(process()).withTenantId(tenantId).deploy();
    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).withTenantId(tenantId).create();

    // when
    final var assigningRecord =
        ENGINE
            .userTask()
            .ofInstance(processInstanceKey)
            .withAuthorizedTenantIds(falseTenantId)
            .expectRejection()
            .assign();

    // then
    Assertions.assertThat(assigningRecord).hasRejectionType(RejectionType.NOT_FOUND);
  }
}
