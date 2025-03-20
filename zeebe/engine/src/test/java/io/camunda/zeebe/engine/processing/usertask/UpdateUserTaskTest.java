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

public final class UpdateUserTaskTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();
  private static final String PROCESS_ID = "process";
  private static final String DEFAULT_ACTION = "update";
  private static final int DEFAULT_PRIORITY = 50;
  private static final List<String> ALL_UPDATABLE_ATTRIBUTES =
      List.of(
          UserTaskRecord.CANDIDATE_GROUPS,
          UserTaskRecord.CANDIDATE_USERS,
          UserTaskRecord.DUE_DATE,
          UserTaskRecord.FOLLOW_UP_DATE,
          UserTaskRecord.PRIORITY);

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
    final var updatingRecord = ENGINE.userTask().withKey(userTaskKey).update();

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
                    .hasAction(DEFAULT_ACTION)
                    .hasPriority(DEFAULT_PRIORITY)
                    .hasTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER)
                    .hasNoChangedAttributes());
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
        ENGINE.userTask().withKey(userTaskKey).withAction("customAction").update();

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
                    .hasTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER)
                    .hasNoChangedAttributes());
  }

  @Test
  public void shouldRejectUpdateIfUserTaskNotFound() {
    // given
    final int key = 123;

    // when
    final var updatingRecord = ENGINE.userTask().withKey(key).expectRejection().update();

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
                    t.zeebeCandidateGroups("initial_group_A, initial_group_B")
                        .zeebeCandidateUsers("initial_user_A, initial_user_B")
                        .zeebeDueDate("2023-03-02T15:35+02:00")
                        .zeebeFollowUpDate("2023-03-02T16:35+02:00")
                        .zeebeTaskPriority("20")))
        .deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when
    final var updatingRecord =
        ENGINE
            .userTask()
            .ofInstance(processInstanceKey)
            .withCandidateGroups("updated_group_C", "updated_group_D")
            .update();

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
                    .hasCandidateGroupsList("updated_group_C", "updated_group_C")
                    .hasCandidateUsersList("initial_user_A", "initial_user_B")
                    .hasDueDate("2023-03-02T15:35+02:00")
                    .hasFollowUpDate("2023-03-02T16:35+02:00")
                    .hasPriority(20)
                    .hasOnlyChangedAttributes(UserTaskRecord.CANDIDATE_GROUPS));
  }

  @Test
  public void shouldUpdateUserTaskCandidateUsersOnlyWithSingleAttribute() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            process(
                t ->
                    t.zeebeCandidateGroups("initial_group_A, initial_group_B")
                        .zeebeCandidateUsers("initial_user_A, initial_user_B")
                        .zeebeDueDate("2023-03-02T15:35+02:00")
                        .zeebeFollowUpDate("2023-03-02T16:35+02:00")
                        .zeebeTaskPriority("20")))
        .deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when
    final var updatingRecord =
        ENGINE
            .userTask()
            .ofInstance(processInstanceKey)
            .withCandidateUsers("updated_user_C", "updated_user_D")
            .update();

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
                    .hasCandidateGroupsList("initial_group_A", "initial_group_B")
                    .hasCandidateUsersList("updated_user_C", "updated_user_D")
                    .hasDueDate("2023-03-02T15:35+02:00")
                    .hasFollowUpDate("2023-03-02T16:35+02:00")
                    .hasPriority(20)
                    .hasOnlyChangedAttributes(UserTaskRecord.CANDIDATE_USERS));
  }

  @Test
  public void shouldUpdateUserTaskDueDateOnlyWithSingleAttribute() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            process(
                t ->
                    t.zeebeCandidateGroups("initial_group_A, initial_group_B")
                        .zeebeCandidateUsers("initial_user_A, initial_user_B")
                        .zeebeDueDate("2023-03-02T15:35+02:00")
                        .zeebeFollowUpDate("2023-03-02T16:35+02:00")
                        .zeebeTaskPriority("20")))
        .deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when
    final var updatingRecord =
        ENGINE.userTask().ofInstance(processInstanceKey).withDueDate("updated_dueDate").update();

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
                    .hasCandidateGroupsList("initial_group_A", "initial_group_B")
                    .hasCandidateUsersList("initial_user_A", "initial_user_B")
                    .hasDueDate("updated_dueDate")
                    .hasFollowUpDate("2023-03-02T16:35+02:00")
                    .hasPriority(20)
                    .hasOnlyChangedAttributes(UserTaskRecord.DUE_DATE));
  }

  @Test
  public void shouldUpdateUserTaskFollowUpDateOnlyWithSingleAttribute() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            process(
                t ->
                    t.zeebeCandidateGroups("initial_group_A, initial_group_B")
                        .zeebeCandidateUsers("initial_user_A, initial_user_B")
                        .zeebeDueDate("2023-03-02T15:35+02:00")
                        .zeebeFollowUpDate("2023-03-02T16:35+02:00")
                        .zeebeTaskPriority("20")))
        .deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when
    final var updatingRecord =
        ENGINE
            .userTask()
            .ofInstance(processInstanceKey)
            .withFollowUpDate("updated_followUpDate")
            .update();

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
                    .hasCandidateGroupsList("initial_group_A", "initial_group_B")
                    .hasCandidateUsersList("initial_user_A", "initial_user_B")
                    .hasDueDate("2023-03-02T15:35+02:00")
                    .hasFollowUpDate("updated_followUpDate")
                    .hasPriority(20)
                    .hasOnlyChangedAttributes(UserTaskRecord.FOLLOW_UP_DATE));
  }

  @Test
  public void shouldUpdateUserTaskPriorityOnlyWithSingleAttribute() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            process(
                t ->
                    t.zeebeCandidateGroups("initial_group_A, initial_group_B")
                        .zeebeCandidateUsers("initial_user_A, initial_user_B")
                        .zeebeDueDate("2023-03-02T15:35+02:00")
                        .zeebeFollowUpDate("2023-03-02T16:35+02:00")
                        .zeebeTaskPriority("20")))
        .deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    final int newPriority = 42;

    // when
    final var updatingRecord =
        ENGINE.userTask().ofInstance(processInstanceKey).withPriority(newPriority).update();

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
                    .hasCandidateGroupsList("initial_group_A", "initial_group_B")
                    .hasCandidateUsersList("initial_user_A", "initial_user_B")
                    .hasDueDate("2023-03-02T15:35+02:00")
                    .hasFollowUpDate("2023-03-02T16:35+02:00")
                    .hasPriority(newPriority)
                    .hasOnlyChangedAttributes(UserTaskRecord.PRIORITY));
  }

  @Test
  public void shouldUpdateAllEligibleUserTaskAttributesWithRecord() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            process(
                t ->
                    t.zeebeCandidateGroups("initial_group_A, initial_group_B")
                        .zeebeCandidateUsers("initial_user_A, initial_user_B")
                        .zeebeDueDate("2023-03-02T15:35+02:00")
                        .zeebeFollowUpDate("2023-03-02T16:35+02:00")
                        .zeebeTaskPriority("20")))
        .deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when
    final var updatingRecord =
        ENGINE.userTask().ofInstance(processInstanceKey).withAllAttributesChanged().update();

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
                    .hasFollowUpDate("")
                    .hasPriority(DEFAULT_PRIORITY)
                    .hasOnlyChangedAttributes(ALL_UPDATABLE_ATTRIBUTES));
  }

  @Test
  public void shouldNotPopulateChangedAttributesWhenNoValuesAreChanged() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            process(
                t ->
                    t.zeebeCandidateGroups("initial_group_A, initial_group_B")
                        .zeebeCandidateUsers("initial_user_A, initial_user_B")
                        .zeebeDueDate("2023-03-02T15:35+02:00")
                        .zeebeFollowUpDate("2023-03-02T16:35+02:00")
                        .zeebeTaskPriority("84")))
        .deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when: updating the task with the same values as the initial properties
    final var updatingRecord =
        ENGINE
            .userTask()
            .ofInstance(processInstanceKey)
            .withCandidateGroups("initial_group_A", "initial_group_B")
            .withCandidateUsers("initial_user_A", "initial_user_B")
            .withDueDate("2023-03-02T15:35+02:00")
            .withFollowUpDate("2023-03-02T16:35+02:00")
            .withPriority(84)
            .update();

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
                    .describedAs("Expect user task properties to remain unchanged")
                    .hasCandidateGroupsList("initial_group_A", "initial_group_B")
                    .hasCandidateUsersList("initial_user_A", "initial_user_B")
                    .hasDueDate("2023-03-02T15:35+02:00")
                    .hasFollowUpDate("2023-03-02T16:35+02:00")
                    .hasPriority(84)
                    .describedAs(
                        "Expect `changedAttributes` to be empty since no values were changed")
                    .hasNoChangedAttributes());
  }

  @Test
  public void shouldOnlyPopulateChangedAttributesForActuallyChangedValues() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            process(
                t ->
                    t.zeebeCandidateGroups("initial_group_A, initial_group_B")
                        .zeebeCandidateUsers("initial_user_A, initial_user_B")
                        .zeebeDueDate("2023-03-02T15:35+02:00")
                        .zeebeFollowUpDate("2023-03-02T16:35+02:00")
                        .zeebeTaskPriority("10")))
        .deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when: updating the task with only some values changed
    final var updatingRecord =
        ENGINE
            .userTask()
            .ofInstance(processInstanceKey)
            .withCandidateGroups("updated_group_F") // changed
            .withCandidateUsers("initial_user_A", "initial_user_B") // unchanged
            .withDueDate("updated_dueDate") // changed
            .withFollowUpDate("2023-03-02T16:35+02:00") // unchanged
            .withPriority(55) // changed
            .update();

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
                    .hasCandidateGroupsList("updated_group_F")
                    .hasCandidateUsersList("initial_user_A", "initial_user_B")
                    .hasDueDate("updated_dueDate")
                    .hasFollowUpDate("2023-03-02T16:35+02:00")
                    .hasPriority(55)
                    .describedAs(
                        "Expect `changedAttributes` to include only the actually updated attributes")
                    .hasOnlyChangedAttributes(
                        UserTaskRecord.CANDIDATE_GROUPS,
                        UserTaskRecord.DUE_DATE,
                        UserTaskRecord.PRIORITY));
  }

  @Test
  public void shouldIgnoreAndNotTrackUnknownChangedAttributes() {
    // given
    ENGINE.deployment().withXmlResource(process()).deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    final var unknownAttributeName = "unknown_attribute";

    // when: updating the user task with both valid and unknown attributes
    final var updatingRecord =
        ENGINE
            .userTask()
            .ofInstance(processInstanceKey)
            .withCandidateGroups("updated_group")
            .withPriority(90)
            .withChangedAttribute(unknownAttributeName)
            .update();

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
                    .describedAs("Expect that unknown attributes are not tracked")
                    .doesNotHaveChangedAttributes(unknownAttributeName)
                    .hasOnlyChangedAttributes(
                        UserTaskRecord.CANDIDATE_GROUPS, UserTaskRecord.PRIORITY));
  }

  @Test
  public void shouldRejectUpdateIfUserTaskIsCompleted() {
    // given
    ENGINE.deployment().withXmlResource(process()).deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    ENGINE.userTask().ofInstance(processInstanceKey).complete();

    // when
    final var updatingRecord =
        ENGINE.userTask().ofInstance(processInstanceKey).expectRejection().update();

    // then
    Assertions.assertThat(updatingRecord).hasRejectionType(RejectionType.NOT_FOUND);
  }

  @Test
  public void shouldUpdateUserTaskForCustomTenant() {
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
            .update();

    // then
    Assertions.assertThat(updatingRecord).hasRejectionType(RejectionType.NOT_FOUND);
  }
}
