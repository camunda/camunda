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
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceMatcher;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.protocol.record.value.UserTaskRecordValue;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.List;
import java.util.function.Consumer;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class MultiTenantUserTaskOperationsTest {

  @ClassRule
  public static final EngineRule ENGINE =
      EngineRule.singlePartition()
          .withSecurityConfig(
              config -> {
                config.getAuthorizations().setEnabled(true);
                config.getMultiTenancy().setChecksEnabled(true);
              });

  private static final String PROCESS_ID = "process";
  private static String tenantId;
  private static String username;

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

  @BeforeClass
  public static void setUp() {
    tenantId = Strings.newRandomValidIdentityId();
    username = Strings.newRandomValidIdentityId();
    ENGINE.tenant().newTenant().withTenantId(tenantId).create();
    ENGINE.user().newUser(username).create();
    ENGINE
        .tenant()
        .addEntity(tenantId)
        .withEntityId(username)
        .withEntityType(EntityType.USER)
        .add();
    ENGINE
        .authorization()
        .newAuthorization()
        .withPermissions(PermissionType.UPDATE_USER_TASK)
        .withResourceMatcher(AuthorizationResourceMatcher.ID)
        .withResourceId(PROCESS_ID)
        .withResourceType(AuthorizationResourceType.PROCESS_DEFINITION)
        .withOwnerId(username)
        .withOwnerType(AuthorizationOwnerType.USER)
        .create();
    ENGINE.deployment().withXmlResource(process()).withTenantId(tenantId).deploy();
  }

  @Test
  public void shouldAssignUserTaskForCustomTenant() {
    // given
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
                    .hasAction("assign")
                    .hasAssignee("foo")
                    .hasOnlyChangedAttributes(UserTaskRecord.ASSIGNEE)
                    .hasTenantId(tenantId));
  }

  @Test
  public void shouldClaimUserTaskForCustomTenant() {
    // given
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
                    .hasAction("claim")
                    .hasAssignee("foo")
                    .hasTenantId(tenantId));
  }

  @Test
  public void shouldUpdateUserTaskForCustomTenant() {
    // given
    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).withTenantId(tenantId).create();

    // when
    final var updatingRecord = ENGINE.userTask().ofInstance(processInstanceKey).update(username);

    // then
    Assertions.assertThat(updatingRecord)
        .hasRecordType(RecordType.EVENT)
        .hasIntent(UserTaskIntent.UPDATING);

    final var updatingRecordValue = updatingRecord.getValue();
    final var updatedRecordValue =
        RecordingExporter.userTaskRecords(UserTaskIntent.UPDATED).getFirst().getValue();

    assertThat(List.of(updatingRecordValue, updatedRecordValue))
        .describedAs("Ensure both UPDATING and UPDATED records have consistent attribute values")
        .allSatisfy(recordValue -> Assertions.assertThat(recordValue).hasTenantId(tenantId));
  }

  @Test
  public void shouldCompleteUserTaskForCustomTenant() {
    // given
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
}
