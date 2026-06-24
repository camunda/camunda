/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.instance;

import static io.camunda.zeebe.protocol.record.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.db.ZeebeDbInconsistentException;
import io.camunda.zeebe.engine.state.immutable.UserTaskState.LifecycleState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.state.mutable.MutableUserTaskState;
import io.camunda.zeebe.engine.util.ProcessingStateRule;
import io.camunda.zeebe.msgpack.value.DocumentValue;
import io.camunda.zeebe.protocol.impl.record.value.usertask.UserTaskRecord;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.test.util.BufferAssert;
import io.camunda.zeebe.test.util.MsgPackUtil;
import java.time.InstantSource;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import org.agrona.DirectBuffer;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class UserTaskStateTest {

  @Rule public final ProcessingStateRule stateRule = new ProcessingStateRule();

  private MutableUserTaskState userTaskState;

  @Before
  public void setUp() {
    final MutableProcessingState processingState = stateRule.getProcessingState();
    userTaskState = processingState.getUserTaskState();
  }

  @Test
  public void shouldCreateUserTask() {
    // given
    final UserTaskRecord expectedRecord = createUserTask(5_000);

    // when
    userTaskState.create(expectedRecord);

    // then
    final UserTaskRecord storedRecord = userTaskState.getUserTask(5_000);
    assertUserTask(expectedRecord, storedRecord, LifecycleState.CREATING);
  }

  @Test
  public void shouldCreateUserTaskWithCustomTenantId() {
    // given
    final UserTaskRecord expectedRecord = createUserTask(5_000).setTenantId("customTenantId");

    // when
    userTaskState.create(expectedRecord);

    // then
    final UserTaskRecord storedRecord = userTaskState.getUserTask(5_000);
    assertUserTask(expectedRecord, "customTenantId", storedRecord, LifecycleState.CREATING);
  }

  @Test
  public void shouldUpdateUserTask() {
    // given
    final UserTaskRecord expectedRecord = createUserTask(5_000);
    userTaskState.create(expectedRecord);
    expectedRecord.setAssignee("myNewAssignee");

    // when
    userTaskState.update(expectedRecord);

    // then
    final UserTaskRecord storedRecord = userTaskState.getUserTask(5_000);
    assertThat(storedRecord).hasAssignee("myNewAssignee");
  }

  @Test
  public void shouldUpdateUserTaskState() {
    // given
    final UserTaskRecord expectedRecord = createUserTask(5_000);
    userTaskState.create(expectedRecord);

    // when
    userTaskState.updateUserTaskLifecycleState(5_000, LifecycleState.CREATED);

    // then
    assertUserTaskState(5_000, LifecycleState.CREATED);
  }

  @Test
  public void shouldFailOnUpdatingNonExistingUserTask() {
    // given
    final UserTaskRecord expectedRecord = new UserTaskRecord();

    // when
    assertThatThrownBy(() -> userTaskState.update(expectedRecord))
        // then
        .isInstanceOf(ZeebeDbInconsistentException.class)
        .hasMessageContaining("does not exist");
  }

  @Test
  public void shouldDeleteUserTask() {
    // given
    final UserTaskRecord expectedRecord = createUserTask(5_000);
    userTaskState.create(expectedRecord);

    // when
    userTaskState.delete(5_000);

    // then
    assertThat(userTaskState.getUserTask(5_000)).isNull();
  }

  @Test
  public void shouldNeverPersistUserTaskVariables() {
    // given
    final long key = 1L;
    final UserTaskRecord userTask = createUserTask(key);

    final List<Consumer<UserTaskRecord>> stateUpdates =
        Arrays.asList(userTaskState::create, userTaskState::update);

    // when user task state is updated then the variables are not persisted
    for (final Consumer<UserTaskRecord> stateUpdate : stateUpdates) {
      userTask.setVariables(MsgPackUtil.asMsgPack("foo", "bar"));
      stateUpdate.accept(userTask);
      final DirectBuffer variables = userTaskState.getUserTask(key).getVariablesBuffer();
      BufferAssert.assertThatBuffer(variables).isEqualTo(DocumentValue.EMPTY_DOCUMENT);
    }
  }

  @Test
  public void shouldNotOverwritePersistedRecord() {
    // given
    final long key = 1L;
    final UserTaskRecord writtenRecord = createUserTask(key).setAssignee("test");

    // when
    userTaskState.create(writtenRecord);
    writtenRecord.setAssignee("foo");

    // then
    final UserTaskRecord readRecord = userTaskState.getUserTask(key);
    assertThat(readRecord).hasAssignee("test");
    assertThat(writtenRecord).hasAssignee("foo");
  }

  private UserTaskRecord createUserTask(final long userTaskKey) {
    return new UserTaskRecord()
        .setElementInstanceKey(1234)
        .setBpmnProcessId("process")
        .setElementId("process")
        .setProcessInstanceKey(4321)
        .setProcessDefinitionKey(8765)
        .setProcessDefinitionVersion(2)
        .setAssignee("myAssignee")
        .setCandidateGroupsList(List.of("myGroups"))
        .setCandidateUsersList(List.of("myUsers"))
        .setDueDate("2023-11-11T11:11:00+01:00")
        .setFollowUpDate("2023-11-12T11:11:00+01:00")
        .setFormKey(5678)
        .setUserTaskKey(userTaskKey)
        .setCreationTimestamp(InstantSource.system().millis());
  }

  private void assertUserTask(
      final UserTaskRecord expectedRecord,
      final UserTaskRecord storedRecord,
      final LifecycleState expectedLifecycleState) {
    assertUserTask(
        expectedRecord,
        TenantOwned.DEFAULT_TENANT_IDENTIFIER,
        storedRecord,
        expectedLifecycleState);
  }

  private void assertUserTask(
      final UserTaskRecord expectedRecord,
      final String expectedTenantId,
      final UserTaskRecord storedRecord,
      final LifecycleState expectedLifecycleState) {
    assertThat(storedRecord)
        .hasElementInstanceKey(expectedRecord.getElementInstanceKey())
        .hasBpmnProcessId(expectedRecord.getBpmnProcessId())
        .hasElementId(expectedRecord.getElementId())
        .hasProcessInstanceKey(expectedRecord.getProcessInstanceKey())
        .hasProcessDefinitionKey(expectedRecord.getProcessDefinitionKey())
        .hasProcessDefinitionVersion(expectedRecord.getProcessDefinitionVersion())
        .hasAssignee(expectedRecord.getAssignee())
        .hasCandidateGroupsList(expectedRecord.getCandidateGroupsList())
        .hasCandidateUsersList(expectedRecord.getCandidateUsersList())
        .hasDueDate(expectedRecord.getDueDate())
        .hasFollowUpDate(expectedRecord.getFollowUpDate())
        .hasFormKey(expectedRecord.getFormKey())
        .hasUserTaskKey(expectedRecord.getUserTaskKey())
        .hasTenantId(expectedTenantId)
        .hasCreationTimestamp(expectedRecord.getCreationTimestamp());
    assertUserTaskState(expectedRecord.getUserTaskKey(), expectedLifecycleState);
  }

  private void assertUserTaskState(
      final long userTaskKey, final LifecycleState expectedLifecycleState) {
    Assertions.assertThat(userTaskState.getLifecycleState(userTaskKey))
        .isEqualTo(expectedLifecycleState);
  }
}
