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
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.List;
import java.util.function.Consumer;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public final class UpdateUserTaskTest {

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
  public void shouldEmitUpdatingEventForUserTask() {
    // given
    ENGINE.deployment().withXmlResource(process()).deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    final long userTaskKey =
        RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst()
            .getKey();

    // when
    final var updatingRecord = ENGINE.userTask().withKey(userTaskKey).update(new UserTaskRecord());

    // then
    Assertions.assertThat(updatingRecord)
        .hasRecordType(RecordType.EVENT)
        .hasIntent(UserTaskIntent.UPDATING);

    final var updatingRecordValue = updatingRecord.getValue();
    final var updatedRecordValue =
        RecordingExporter.userTaskRecords(UserTaskIntent.UPDATED).getFirst().getValue();

    assertThat(List.of(updatingRecordValue, updatedRecordValue))
        .describedAs("Ensure both UPDATING and UPDATED records have consistent attribute values")
        .allSatisfy(
            recordValue ->
                Assertions.assertThat(recordValue)
                    .hasUserTaskKey(userTaskKey)
                    .hasAction("update")
                    .hasTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER));
  }

  @Test
  public void shouldTrackCustomActionInUpdatingEvent() {
    // given
    ENGINE.deployment().withXmlResource(process()).deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    final long userTaskKey =
        RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst()
            .getKey();

    // when
    final var updatingRecord =
        ENGINE
            .userTask()
            .withKey(userTaskKey)
            .withAction("customAction")
            .update(new UserTaskRecord());

    // then
    Assertions.assertThat(updatingRecord)
        .hasRecordType(RecordType.EVENT)
        .hasIntent(UserTaskIntent.UPDATING);

    final var updatingRecordValue = updatingRecord.getValue();
    final var updatedRecordValue =
        RecordingExporter.userTaskRecords(UserTaskIntent.UPDATED).getFirst().getValue();

    assertThat(List.of(updatingRecordValue, updatedRecordValue))
        .describedAs("Ensure both UPDATING and UPDATED records have consistent attribute values")
        .allSatisfy(
            recordValue ->
                Assertions.assertThat(recordValue)
                    .hasUserTaskKey(userTaskKey)
                    .hasAction("customAction")
                    .hasTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER));
  }

  @Test
  public void shouldRejectUpdateIfUserTaskNotFound() {
    // given
    final int key = 123;

    // when
    final var updatingRecord =
        ENGINE.userTask().withKey(key).expectRejection().update(new UserTaskRecord());

    // then
    Assertions.assertThat(updatingRecord).hasRejectionType(RejectionType.NOT_FOUND);
  }

  @Test
  public void shouldUpdateUserTaskCandidateGroupsOnlyWithSingleAttribute() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            process(
                t ->
                    t.zeebeCandidateGroups("foo, bar")
                        .zeebeCandidateUsers("oof, rab")
                        .zeebeDueDate("2023-03-02T15:35+02:00")
                        .zeebeFollowUpDate("2023-03-02T16:35+02:00")))
        .deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when
    final var updatingRecord =
        ENGINE
            .userTask()
            .ofInstance(processInstanceKey)
            .update(List.of("baz", "foobar"), null, null, null);

    // then
    Assertions.assertThat(updatingRecord)
        .hasRecordType(RecordType.EVENT)
        .hasIntent(UserTaskIntent.UPDATING);

    final var updatingRecordValue = updatingRecord.getValue();
    final var updatedRecordValue =
        RecordingExporter.userTaskRecords(UserTaskIntent.UPDATED).getFirst().getValue();

    assertThat(List.of(updatingRecordValue, updatedRecordValue))
        .describedAs("Ensure both UPDATING and UPDATED records have consistent attribute values")
        .allSatisfy(
            recordValue ->
                Assertions.assertThat(recordValue)
                    .hasCandidateGroupsList("baz", "foobar")
                    .hasCandidateUsersList("oof", "rab")
                    .hasDueDate("2023-03-02T15:35+02:00")
                    .hasFollowUpDate("2023-03-02T16:35+02:00"));
  }

  @Test
  public void shouldUpdateUserTaskCandidateUsersOnlyWithSingleAttribute() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            process(
                t ->
                    t.zeebeCandidateGroups("foo, bar")
                        .zeebeCandidateUsers("oof, rab")
                        .zeebeDueDate("2023-03-02T15:35+02:00")
                        .zeebeFollowUpDate("2023-03-02T16:35+02:00")))
        .deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when
    final var updatingRecord =
        ENGINE
            .userTask()
            .ofInstance(processInstanceKey)
            .update(null, List.of("baz", "foobar"), null, null);

    // then
    Assertions.assertThat(updatingRecord)
        .hasRecordType(RecordType.EVENT)
        .hasIntent(UserTaskIntent.UPDATING);

    final var updatingRecordValue = updatingRecord.getValue();
    final var updatedRecordValue =
        RecordingExporter.userTaskRecords(UserTaskIntent.UPDATED).getFirst().getValue();

    assertThat(List.of(updatingRecordValue, updatedRecordValue))
        .describedAs("Ensure both UPDATING and UPDATED records have consistent attribute values")
        .allSatisfy(
            recordValue ->
                Assertions.assertThat(recordValue)
                    .hasCandidateGroupsList("foo", "bar")
                    .hasCandidateUsersList("baz", "foobar")
                    .hasDueDate("2023-03-02T15:35+02:00")
                    .hasFollowUpDate("2023-03-02T16:35+02:00"));
  }

  @Test
  public void shouldUpdateUserTaskDueDateOnlyWithSingleAttribute() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            process(
                t ->
                    t.zeebeCandidateGroups("foo, bar")
                        .zeebeCandidateUsers("oof, rab")
                        .zeebeDueDate("2023-03-02T15:35+02:00")
                        .zeebeFollowUpDate("2023-03-02T16:35+02:00")))
        .deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when
    final var updatingRecord =
        ENGINE.userTask().ofInstance(processInstanceKey).update(null, null, "abc", null);

    // then
    Assertions.assertThat(updatingRecord)
        .hasRecordType(RecordType.EVENT)
        .hasIntent(UserTaskIntent.UPDATING);

    final var updatingRecordValue = updatingRecord.getValue();
    final var updatedRecordValue =
        RecordingExporter.userTaskRecords(UserTaskIntent.UPDATED).getFirst().getValue();

    assertThat(List.of(updatingRecordValue, updatedRecordValue))
        .describedAs("Ensure both UPDATING and UPDATED records have consistent attribute values")
        .allSatisfy(
            recordValue ->
                Assertions.assertThat(recordValue)
                    .hasCandidateGroupsList("foo", "bar")
                    .hasCandidateUsersList("oof", "rab")
                    .hasDueDate("abc")
                    .hasFollowUpDate("2023-03-02T16:35+02:00"));
  }

  @Test
  public void shouldUpdateUserTaskFollowUpDateOnlyWithSingleAttribute() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            process(
                t ->
                    t.zeebeCandidateGroups("foo, bar")
                        .zeebeCandidateUsers("oof, rab")
                        .zeebeDueDate("2023-03-02T15:35+02:00")
                        .zeebeFollowUpDate("2023-03-02T16:35+02:00")))
        .deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when
    final var updatingRecord =
        ENGINE.userTask().ofInstance(processInstanceKey).update(null, null, null, "abc");

    // then
    Assertions.assertThat(updatingRecord)
        .hasRecordType(RecordType.EVENT)
        .hasIntent(UserTaskIntent.UPDATING);

    final var updatingRecordValue = updatingRecord.getValue();
    final var updatedRecordValue =
        RecordingExporter.userTaskRecords(UserTaskIntent.UPDATED).getFirst().getValue();

    assertThat(List.of(updatingRecordValue, updatedRecordValue))
        .describedAs("Ensure both UPDATING and UPDATED records have consistent attribute values")
        .allSatisfy(
            recordValue ->
                Assertions.assertThat(recordValue)
                    .hasCandidateGroupsList("foo", "bar")
                    .hasCandidateUsersList("oof", "rab")
                    .hasDueDate("2023-03-02T15:35+02:00")
                    .hasFollowUpDate("abc"));
  }

  @Test
  public void shouldUpdateAllEligibleUserTaskAttributesWithRecord() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            process(
                t ->
                    t.zeebeCandidateGroups("foo, bar")
                        .zeebeCandidateUsers("oof, rab")
                        .zeebeFollowUpDate("2023-03-02T15:35+02:00")
                        .zeebeDueDate("2023-03-02T16:35+02:00")))
        .deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when
    final var updatingRecord =
        ENGINE.userTask().ofInstance(processInstanceKey).update(new UserTaskRecord());

    // then
    Assertions.assertThat(updatingRecord)
        .hasRecordType(RecordType.EVENT)
        .hasIntent(UserTaskIntent.UPDATING);

    final var updatingRecordValue = updatingRecord.getValue();
    final var updatedRecordValue =
        RecordingExporter.userTaskRecords(UserTaskIntent.UPDATED).getFirst().getValue();

    assertThat(List.of(updatingRecordValue, updatedRecordValue))
        .describedAs("Ensure both UPDATING and UPDATED records have consistent attribute values")
        .allSatisfy(
            recordValue ->
                Assertions.assertThat(recordValue)
                    .hasNoCandidateUsersList()
                    .hasNoCandidateUsersList()
                    .hasDueDate("")
                    .hasFollowUpDate(""));
  }

  @Test
  public void shouldRejectUpdateIfUserTaskIsCompleted() {
    // given
    ENGINE.deployment().withXmlResource(process()).deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    ENGINE.userTask().ofInstance(processInstanceKey).complete();

    // when
    final var updatingRecord =
        ENGINE
            .userTask()
            .ofInstance(processInstanceKey)
            .expectRejection()
            .update(new UserTaskRecord());

    // then
    Assertions.assertThat(updatingRecord).hasRejectionType(RejectionType.NOT_FOUND);
  }

  @Test
  public void shouldUpdateUserTaskForCustomTenant() {
    // given
    final String tenantId = "acme";
    ENGINE.deployment().withXmlResource(process()).withTenantId(tenantId).deploy();
    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).withTenantId(tenantId).create();

    // when
    final var updatingRecord =
        ENGINE
            .userTask()
            .ofInstance(processInstanceKey)
            .withAuthorizedTenantIds(tenantId)
            .update(new UserTaskRecord());

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
  public void shouldRejectUpdateIfTenantIsUnauthorized() {
    // given
    final String tenantId = "acme";
    final String falseTenantId = "foo";
    ENGINE.deployment().withXmlResource(process()).withTenantId(tenantId).deploy();
    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).withTenantId(tenantId).create();

    // when
    final var updatingRecord =
        ENGINE
            .userTask()
            .ofInstance(processInstanceKey)
            .withAuthorizedTenantIds(falseTenantId)
            .expectRejection()
            .update(new UserTaskRecord());

    // then
    Assertions.assertThat(updatingRecord).hasRejectionType(RejectionType.NOT_FOUND);
  }
}
