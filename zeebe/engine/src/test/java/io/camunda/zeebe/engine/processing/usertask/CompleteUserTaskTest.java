/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.usertask;

import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.msgpack.spec.MsgPackHelper;
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
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public final class CompleteUserTaskTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();
  private static final String PROCESS_ID = "process";

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  private static BpmnModelInstance process() {
    return Bpmn.createExecutableProcess(PROCESS_ID)
        .startEvent()
        .userTask("task")
        .zeebeUserTask()
        .endEvent()
        .done();
  }

  @Test
  public void shouldEmitCompletingEventForUserTask() {
    // given
    ENGINE.deployment().withXmlResource(process()).deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    final long userTaskKey =
        RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst()
            .getKey();

    // when
    final Record<UserTaskRecordValue> completedRecord =
        ENGINE.userTask().withKey(userTaskKey).complete();

    // then
    final UserTaskRecordValue recordValue = completedRecord.getValue();

    Assertions.assertThat(completedRecord)
        .hasRecordType(RecordType.EVENT)
        .hasIntent(UserTaskIntent.COMPLETING);

    Assertions.assertThat(recordValue)
        .hasUserTaskKey(userTaskKey)
        .hasAction("complete")
        .hasTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  }

  @Test
  public void shouldTrackCustomActionInCompletingEvent() {
    // given
    ENGINE.deployment().withXmlResource(process()).deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    final long userTaskKey =
        RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst()
            .getKey();

    // when
    final Record<UserTaskRecordValue> completedRecord =
        ENGINE.userTask().withKey(userTaskKey).withAction("customAction").complete();

    // then
    final UserTaskRecordValue recordValue = completedRecord.getValue();

    Assertions.assertThat(completedRecord)
        .hasRecordType(RecordType.EVENT)
        .hasIntent(UserTaskIntent.COMPLETING);

    Assertions.assertThat(recordValue)
        .hasUserTaskKey(userTaskKey)
        .hasAction("customAction")
        .hasTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  }

  @Test
  public void shouldRejectCompletionIfUserTaskNotFound() {
    // given
    final int key = 123;

    // when
    final Record<UserTaskRecordValue> completedRecord =
        ENGINE.userTask().withKey(key).expectRejection().complete();

    // then
    Assertions.assertThat(completedRecord).hasRejectionType(RejectionType.NOT_FOUND);
  }

  @Test
  public void shouldCompleteUserTaskWithVariables() {
    // given
    ENGINE.deployment().withXmlResource(process()).deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when
    final Record<UserTaskRecordValue> completedRecord =
        ENGINE.userTask().ofInstance(processInstanceKey).withVariables("{'foo':'bar'}").complete();

    // then
    Assertions.assertThat(completedRecord)
        .hasRecordType(RecordType.EVENT)
        .hasIntent(UserTaskIntent.COMPLETING);
    assertThat(completedRecord.getValue().getVariables()).containsExactly(entry("foo", "bar"));
  }

  @Test
  public void shouldCompleteUserTaskWithNilVariables() {
    // given
    ENGINE.deployment().withXmlResource(process()).deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when
    final Record<UserTaskRecordValue> completedRecord =
        ENGINE
            .userTask()
            .ofInstance(processInstanceKey)
            .withVariables(new UnsafeBuffer(MsgPackHelper.NIL))
            .complete();

    // then
    Assertions.assertThat(completedRecord)
        .hasRecordType(RecordType.EVENT)
        .hasIntent(UserTaskIntent.COMPLETING);
    assertThat(completedRecord.getValue().getVariables()).isEmpty();
  }

  @Test
  public void shouldCompleteUserTaskWithZeroLengthVariables() {
    // given
    ENGINE.deployment().withXmlResource(process()).deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when
    final Record<UserTaskRecordValue> completedRecord =
        ENGINE
            .userTask()
            .ofInstance(processInstanceKey)
            .withVariables(new UnsafeBuffer(new byte[0]))
            .complete();

    // then
    Assertions.assertThat(completedRecord)
        .hasRecordType(RecordType.EVENT)
        .hasIntent(UserTaskIntent.COMPLETING);
    assertThat(completedRecord.getValue().getVariables()).isEmpty();
  }

  @Test
  public void shouldThrowExceptionOnCompletionIfVariablesAreInvalid() {
    // given
    ENGINE.deployment().withXmlResource(process()).deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    final byte[] invalidVariables = new byte[] {1}; // positive fixnum, i.e. no object

    // when
    final Throwable throwable =
        catchThrowable(
            () ->
                ENGINE
                    .userTask()
                    .ofInstance(processInstanceKey)
                    .withVariables(new UnsafeBuffer(invalidVariables))
                    .expectRejection()
                    .complete());

    // then
    assertThat(throwable).isInstanceOf(RuntimeException.class);
    assertThat(throwable.getMessage()).contains("Property 'variables' is invalid");
    assertThat(throwable.getMessage())
        .contains("Expected document to be a root level object, but was 'INTEGER'");
  }

  @Test
  public void shouldRejectCompletionIfUserTaskIsCompleted() {
    // given
    ENGINE.deployment().withXmlResource(process()).deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    ENGINE.userTask().ofInstance(processInstanceKey).complete();

    // when
    final Record<UserTaskRecordValue> completedRecord =
        ENGINE.userTask().ofInstance(processInstanceKey).expectRejection().complete();

    // then
    Assertions.assertThat(completedRecord).hasRejectionType(RejectionType.NOT_FOUND);
  }

  @Test
  public void shouldCompleteUserTaskForCustomTenant() {
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
    final Record<UserTaskRecordValue> completedRecord =
        ENGINE.userTask().ofInstance(processInstanceKey).complete(username);

    // then
    final UserTaskRecordValue recordValue = completedRecord.getValue();

    Assertions.assertThat(completedRecord)
        .hasRecordType(RecordType.EVENT)
        .hasIntent(UserTaskIntent.COMPLETING);

    Assertions.assertThat(recordValue).hasTenantId(tenantId);
  }

  @Test
  public void shouldRejectCompletionIfTenantIsUnauthorized() {
    // given
    final String tenantId = "acme";
    final String falseTenantId = "foo";
    ENGINE.deployment().withXmlResource(process()).withTenantId(tenantId).deploy();
    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).withTenantId(tenantId).create();

    // when
    final Record<UserTaskRecordValue> completedRecord =
        ENGINE
            .userTask()
            .ofInstance(processInstanceKey)
            .withAuthorizedTenantIds(falseTenantId)
            .expectRejection()
            .complete();

    // then
    Assertions.assertThat(completedRecord).hasRejectionType(RejectionType.NOT_FOUND);
  }
}
