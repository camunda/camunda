/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.usertask;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.builder.UserTaskBuilder;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.protocol.record.value.UserTaskRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.function.Consumer;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class ClaimUserTaskTest {
  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();
  private static final String PROCESS_ID = "process";

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
  public void shouldEmitAssigningEventForClaimedUserTask() {
    // given
    ENGINE.deployment().withXmlResource(process()).deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    final long userTaskKey =
        RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst()
            .getKey();

    // when
    final Record<UserTaskRecordValue> claimedRecord =
        ENGINE.userTask().withKey(userTaskKey).withAssignee("foo").claim();

    // then
    final UserTaskRecordValue recordValue = claimedRecord.getValue();

    Assertions.assertThat(claimedRecord)
        .hasRecordType(RecordType.EVENT)
        .hasIntent(UserTaskIntent.ASSIGNING);

    Assertions.assertThat(recordValue)
        .hasUserTaskKey(userTaskKey)
        .hasAction("claim")
        .hasTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  }

  @Test
  public void shouldTrackCustomActionInAssigningEvent() {
    // given
    ENGINE.deployment().withXmlResource(process()).deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    final long userTaskKey =
        RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst()
            .getKey();

    // when
    final Record<UserTaskRecordValue> claimedRecord =
        ENGINE
            .userTask()
            .withKey(userTaskKey)
            .withAction("customAction")
            .withAssignee("foo")
            .claim();

    // then
    final UserTaskRecordValue recordValue = claimedRecord.getValue();

    Assertions.assertThat(claimedRecord)
        .hasRecordType(RecordType.EVENT)
        .hasIntent(UserTaskIntent.ASSIGNING);

    Assertions.assertThat(recordValue)
        .hasUserTaskKey(userTaskKey)
        .hasAction("customAction")
        .hasTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  }

  @Test
  public void shouldClaimUserTaskForAssignee() {
    // given
    ENGINE.deployment().withXmlResource(process()).deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when
    final Record<UserTaskRecordValue> claimedRecord =
        ENGINE.userTask().ofInstance(processInstanceKey).withAssignee("foo").claim();

    // then
    Assertions.assertThat(claimedRecord)
        .hasRecordType(RecordType.EVENT)
        .hasIntent(UserTaskIntent.ASSIGNING);
    Assertions.assertThat(claimedRecord.getValue()).hasAssignee("foo");
  }

  @Test
  public void shouldRejectClaimUserTaskForEmptyAssignee() {
    // given
    ENGINE.deployment().withXmlResource(process()).deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when
    final Record<UserTaskRecordValue> claimedRecord =
        ENGINE.userTask().ofInstance(processInstanceKey).withAssignee("").expectRejection().claim();

    // then
    Assertions.assertThat(claimedRecord).hasRejectionType(RejectionType.INVALID_STATE);
  }

  @Test
  public void shouldRejectClaimIfUserTaskNotFound() {
    // given
    final int key = 123;

    // when
    final Record<UserTaskRecordValue> claimedRecord =
        ENGINE.userTask().withKey(key).withAssignee("foo").expectRejection().claim();

    // then
    Assertions.assertThat(claimedRecord).hasRejectionType(RejectionType.NOT_FOUND);
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
    final Record<UserTaskRecordValue> claimedRecord =
        ENGINE
            .userTask()
            .withKey(userTaskKey)
            .withAssignee("new assignee")
            .expectRejection()
            .claim();

    // then
    Assertions.assertThat(claimedRecord).hasRejectionType(RejectionType.INVALID_STATE);
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
    final Record<UserTaskRecordValue> claimedRecord =
        ENGINE.userTask().withKey(userTaskKey).withAssignee("").expectRejection().claim();

    // then
    Assertions.assertThat(claimedRecord).hasRejectionType(RejectionType.INVALID_STATE);
  }

  @Test
  public void shouldClaimUserTaskWithAssigneeSelf() {
    // given
    ENGINE.deployment().withXmlResource(process(b -> b.zeebeAssignee("foo"))).deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    final long userTaskKey =
        RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst()
            .getKey();

    // when
    final Record<UserTaskRecordValue> claimedRecord =
        ENGINE.userTask().withKey(userTaskKey).withAssignee("foo").claim();

    // then
    Assertions.assertThat(claimedRecord)
        .hasRecordType(RecordType.EVENT)
        .hasIntent(UserTaskIntent.ASSIGNING);
    Assertions.assertThat(claimedRecord.getValue()).hasAssignee("foo");
  }

  @Test
  public void shouldRejectClaimIfUserTaskIsCompleted() {
    // given
    ENGINE.deployment().withXmlResource(process()).deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    ENGINE.userTask().ofInstance(processInstanceKey).complete();

    // when
    final Record<UserTaskRecordValue> claimedRecord =
        ENGINE.userTask().ofInstance(processInstanceKey).expectRejection().claim();

    // then
    Assertions.assertThat(claimedRecord).hasRejectionType(RejectionType.NOT_FOUND);
  }

  @Test
  public void shouldClaimUserTaskForCustomTenant() {
    // given
    final String tenantId = "acme";
    ENGINE.deployment().withXmlResource(process()).withTenantId(tenantId).deploy();
    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).withTenantId(tenantId).create();

    // when
    final Record<UserTaskRecordValue> claimedRecord =
        ENGINE
            .userTask()
            .ofInstance(processInstanceKey)
            .withAuthorizedTenantIds(tenantId)
            .withAssignee("foo")
            .claim();

    // then
    final UserTaskRecordValue recordValue = claimedRecord.getValue();

    Assertions.assertThat(claimedRecord)
        .hasRecordType(RecordType.EVENT)
        .hasIntent(UserTaskIntent.ASSIGNING);

    Assertions.assertThat(recordValue).hasTenantId(tenantId);
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
    final Record<UserTaskRecordValue> claimedRecord =
        ENGINE
            .userTask()
            .ofInstance(processInstanceKey)
            .withAuthorizedTenantIds(falseTenantId)
            .expectRejection()
            .claim();

    // then
    Assertions.assertThat(claimedRecord).hasRejectionType(RejectionType.NOT_FOUND);
  }
}
