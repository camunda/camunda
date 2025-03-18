/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.usertask;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.builder.UserTaskBuilder;
import io.camunda.zeebe.protocol.impl.record.value.usertask.UserTaskRecord;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.List;
import java.util.function.Consumer;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class ClaimUserTaskTest {
  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();
  private static final String PROCESS_ID = "process";
  private static final String DEFAULT_ACTION = "claim";

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
  public void shouldEmitClaimAndAssigningEventsUponClaimingUserTask() {
    // given
    ENGINE.deployment().withXmlResource(process()).deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    final long userTaskKey =
        RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst()
            .getKey();

    // when
    final var claimingRecord = ENGINE.userTask().withKey(userTaskKey).withAssignee("foo").claim();

    // then
    Assertions.assertThat(claimingRecord)
        .hasRecordType(RecordType.EVENT)
        .hasIntent(UserTaskIntent.CLAIMING);

    final var claimingRecordValue = claimingRecord.getValue();
    final var assignedRecordValue =
        RecordingExporter.userTaskRecords(UserTaskIntent.ASSIGNED).getFirst().getValue();

    assertThat(List.of(claimingRecordValue, assignedRecordValue))
        .describedAs(
            "Ensure CLAIMING and ASSIGNED records have consistent attribute values "
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
  public void shouldEmitClaimAndAssigningEventsUponClaimingUserTaskWithCustomAction() {
    // given
    ENGINE.deployment().withXmlResource(process()).deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    final long userTaskKey =
        RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst()
            .getKey();

    // when
    final var claimingRecord =
        ENGINE
            .userTask()
            .withKey(userTaskKey)
            .withAction("customAction")
            .withAssignee("foo")
            .claim();

    // then
    Assertions.assertThat(claimingRecord)
        .hasRecordType(RecordType.EVENT)
        .hasIntent(UserTaskIntent.CLAIMING);

    final var claimingRecordValue = claimingRecord.getValue();
    final var assignedRecordValue =
        RecordingExporter.userTaskRecords(UserTaskIntent.ASSIGNED).getFirst().getValue();

    assertThat(List.of(claimingRecordValue, assignedRecordValue))
        .describedAs(
            "Ensure CLAIMING and ASSIGNED records have consistent attribute values "
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
  public void shouldRejectClaimUserTaskForEmptyAssignee() {
    // given
    ENGINE.deployment().withXmlResource(process()).deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when
    final var claimingRecord =
        ENGINE
            .userTask()
            .ofInstance(processInstanceKey)
            .withoutAssignee()
            .expectRejection()
            .claim();

    // then
    Assertions.assertThat(claimingRecord).hasRejectionType(RejectionType.INVALID_STATE);
  }

  @Test
  public void shouldRejectClaimIfUserTaskNotFound() {
    // given
    final int key = 123;

    // when
    final var claimingRecord =
        ENGINE.userTask().withKey(key).withAssignee("foo").expectRejection().claim();

    // then
    Assertions.assertThat(claimingRecord).hasRejectionType(RejectionType.NOT_FOUND);
  }

  @Test
  public void shouldRejectUserTaskWithAssigneeAlreadyAssigned() {
    // given
    ENGINE.deployment().withXmlResource(process(b -> b.zeebeAssignee("foo"))).deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    final long userTaskKey =
        RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst()
            .getKey();

    // when
    final var claimingRecord =
        ENGINE
            .userTask()
            .withKey(userTaskKey)
            .withAssignee("new assignee")
            .expectRejection()
            .claim();

    // then
    Assertions.assertThat(claimingRecord).hasRejectionType(RejectionType.INVALID_STATE);
  }

  @Test
  public void shouldRejectUserTaskWithEmptyAssigneeWhenAlreadyAssigned() {
    // given
    ENGINE.deployment().withXmlResource(process(b -> b.zeebeAssignee("foo"))).deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    final long userTaskKey =
        RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst()
            .getKey();

    // when
    final var claimingRecord =
        ENGINE.userTask().withKey(userTaskKey).withoutAssignee().expectRejection().claim();

    // then
    Assertions.assertThat(claimingRecord).hasRejectionType(RejectionType.INVALID_STATE);
  }

  @Test
  public void shouldClaimUserTaskWithAssigneeSelf() {
    // given
    final var initialAssignee = "samwise";
    ENGINE.deployment().withXmlResource(process(b -> b.zeebeAssignee(initialAssignee))).deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    final long userTaskKey =
        RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst()
            .getKey();

    // when
    final var claimingRecord =
        ENGINE.userTask().withKey(userTaskKey).withAssignee(initialAssignee).claim();

    // then
    Assertions.assertThat(claimingRecord)
        .hasRecordType(RecordType.EVENT)
        .hasIntent(UserTaskIntent.CLAIMING);

    final var claimingRecordValue = claimingRecord.getValue();
    final var assignedRecordValue =
        // querying for the first `ASSIGNED` record right after `CLAIMING` record
        RecordingExporter.userTaskRecords(UserTaskIntent.ASSIGNED)
            .filter(r -> r.getPosition() > claimingRecord.getPosition())
            .getFirst()
            .getValue();

    assertThat(List.of(claimingRecordValue, assignedRecordValue))
        .describedAs(
            "Ensure CLAIMING and ASSIGNED records have consistent attribute values "
                + "as dependent applications will rely on them to update user task data internally")
        .allSatisfy(
            recordValue ->
                Assertions.assertThat(recordValue)
                    .hasUserTaskKey(userTaskKey)
                    .hasAction(DEFAULT_ACTION)
                    .hasTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER)
                    .hasAssignee(initialAssignee)
                    .describedAs(
                        "Expect that the `changedAttributes` field is empty because the task was "
                            + "claimed by the same user and the `assignee` value did not change")
                    .hasNoChangedAttributes());
  }

  @Test
  public void shouldRejectClaimIfUserTaskIsCompleted() {
    // given
    ENGINE.deployment().withXmlResource(process()).deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    ENGINE.userTask().ofInstance(processInstanceKey).complete();

    // when
    final var claimingRecord =
        ENGINE.userTask().ofInstance(processInstanceKey).expectRejection().claim();

    // then
    Assertions.assertThat(claimingRecord).hasRejectionType(RejectionType.NOT_FOUND);
  }

  @Test
  public void shouldClaimUserTaskForCustomTenant() {
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
    final var claimingRecord =
        ENGINE.userTask().ofInstance(processInstanceKey).withAssignee("foo").claim(username);

    // then
    Assertions.assertThat(claimingRecord)
        .hasRecordType(RecordType.EVENT)
        .hasIntent(UserTaskIntent.CLAIMING);

    final var claimingRecordValue = claimingRecord.getValue();
    final var assignedRecordValue =
        RecordingExporter.userTaskRecords(UserTaskIntent.ASSIGNED).getFirst().getValue();

    assertThat(List.of(claimingRecordValue, assignedRecordValue))
        .describedAs(
            "Ensure CLAIMING and ASSIGNED records have consistent attribute values "
                + "as dependent applications will rely on them to update user task data internally")
        .allSatisfy(
            recordValue ->
                Assertions.assertThat(recordValue)
                    .hasAction(DEFAULT_ACTION)
                    .hasAssignee("foo")
                    .hasTenantId(tenantId));
  }

  @Test
  public void shouldRejectClaimIfTenantIsUnauthorized() {
    // given
    final String tenantId = "acme";
    final String falseTenantId = "foo";
    ENGINE.deployment().withXmlResource(process()).withTenantId(tenantId).deploy();
    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).withTenantId(tenantId).create();

    // when
    final var claimingRecord =
        ENGINE
            .userTask()
            .ofInstance(processInstanceKey)
            .withAuthorizedTenantIds(falseTenantId)
            .expectRejection()
            .claim();

    // then
    Assertions.assertThat(claimingRecord).hasRejectionType(RejectionType.NOT_FOUND);
  }
}
