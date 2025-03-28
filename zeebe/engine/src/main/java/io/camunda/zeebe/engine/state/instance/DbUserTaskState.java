/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.instance;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbForeignKey;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.engine.processing.identity.AuthorizedTenants;
import io.camunda.zeebe.engine.state.mutable.MutableUserTaskState;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.record.value.usertask.UserTaskRecord;
import java.util.Optional;
import java.util.function.Consumer;
import org.apache.commons.lang3.StringUtils;

public class DbUserTaskState implements MutableUserTaskState {

  // key => user task record value
  // we need two separate wrapper to not interfere with get and put
  // see https://github.com/zeebe-io/zeebe/issues/1914
  private final UserTaskRecordValue userTaskRecordToRead = new UserTaskRecordValue();
  private final UserTaskRecordValue userTaskRecordToWrite = new UserTaskRecordValue();

  private final DbLong userTaskKey;

  private final ColumnFamily<DbLong, UserTaskRecordValue> userTasksColumnFamily;

  // key => job state
  private final DbForeignKey<DbLong> fkUserTask;
  private final UserTaskLifecycleStateValue userTaskState = new UserTaskLifecycleStateValue();
  private final ColumnFamily<DbForeignKey<DbLong>, UserTaskLifecycleStateValue>
      statesUserTaskColumnFamily;

  // key => intermediate user task state
  // we need two separate wrapper to not interfere with get and put
  // see https://github.com/zeebe-io/zeebe/issues/1914
  private final UserTaskIntermediateStateValue userTaskIntermediateStateToRead =
      new UserTaskIntermediateStateValue();
  private final UserTaskIntermediateStateValue userTaskIntermediateStateToWrite =
      new UserTaskIntermediateStateValue();

  private final DbLong userTaskIntermediateStateKey;
  private final ColumnFamily<DbLong, UserTaskIntermediateStateValue>
      userTasksIntermediateStatesColumnFamily;

  private final UserTaskTransitionTriggerRequestMetadata userTaskTransitionTriggerRequestMetadata =
      new UserTaskTransitionTriggerRequestMetadata();
  private final ColumnFamily<DbLong, UserTaskTransitionTriggerRequestMetadata>
      userTasksTransitionTriggerRequestMetadataColumnFamily;

  private final DbString intermediateAssignee = new DbString();
  private final ColumnFamily<DbLong, DbString> userTasksIntermediateAssigneeColumnFamily;

  public DbUserTaskState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {
    userTaskKey = new DbLong();
    fkUserTask = new DbForeignKey<>(userTaskKey, ZbColumnFamilies.USER_TASKS);
    userTaskIntermediateStateKey = new DbLong();

    userTasksColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.USER_TASKS, transactionContext, userTaskKey, userTaskRecordToRead);

    statesUserTaskColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.USER_TASK_STATES, transactionContext, fkUserTask, userTaskState);

    userTasksIntermediateStatesColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.USER_TASK_INTERMEDIATE_STATES,
            transactionContext,
            userTaskIntermediateStateKey,
            userTaskIntermediateStateToRead);

    userTasksTransitionTriggerRequestMetadataColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.USER_TASK_TRANSITION_TRIGGER_REQUEST_METADATA,
            transactionContext,
            userTaskKey,
            userTaskTransitionTriggerRequestMetadata);

    userTasksIntermediateAssigneeColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.USER_TASK_INTERMEDIATE_ASSIGNEE,
            transactionContext,
            userTaskKey,
            intermediateAssignee);
  }

  @Override
  public void create(final UserTaskRecord userTask) {
    userTaskKey.wrapLong(userTask.getUserTaskKey());
    // do not persist variables in user task state
    userTaskRecordToWrite.setRecordWithoutVariables(userTask);
    userTasksColumnFamily.insert(userTaskKey, userTaskRecordToWrite);
    // initialize state
    userTaskState.setLifecycleState(LifecycleState.CREATING);
    statesUserTaskColumnFamily.insert(fkUserTask, userTaskState);
  }

  @Override
  public void update(final UserTaskRecord userTask) {
    userTaskKey.wrapLong(userTask.getUserTaskKey());
    // do not persist variables in user task state
    userTaskRecordToWrite.setRecordWithoutVariables(userTask);
    userTasksColumnFamily.update(userTaskKey, userTaskRecordToWrite);
  }

  @Override
  public void updateUserTaskLifecycleState(final long key, final LifecycleState newLifecycleState) {
    userTaskKey.wrapLong(key);
    userTaskState.setLifecycleState(newLifecycleState);
    statesUserTaskColumnFamily.update(fkUserTask, userTaskState);
  }

  @Override
  public void delete(final long key) {
    userTaskKey.wrapLong(key);
    userTasksColumnFamily.deleteExisting(userTaskKey);
    statesUserTaskColumnFamily.deleteExisting(fkUserTask);
  }

  @Override
  public void storeIntermediateState(final UserTaskRecord record, final LifecycleState lifecycle) {
    userTaskIntermediateStateKey.wrapLong(record.getUserTaskKey());
    userTaskIntermediateStateToWrite.setRecord(record);
    userTaskIntermediateStateToWrite.setLifecycleState(lifecycle);
    userTasksIntermediateStatesColumnFamily.insert(
        userTaskIntermediateStateKey, userTaskIntermediateStateToWrite);
  }

  @Override
  public void updateIntermediateState(
      final long key, final Consumer<UserTaskIntermediateStateValue> updater) {
    userTaskIntermediateStateKey.wrapLong(key);
    final var intermediateState =
        userTasksIntermediateStatesColumnFamily.get(userTaskIntermediateStateKey);

    updater.accept(intermediateState);

    userTaskIntermediateStateToWrite.setRecord(intermediateState.getRecord());
    userTaskIntermediateStateToWrite.setLifecycleState(intermediateState.getLifecycleState());
    userTasksIntermediateStatesColumnFamily.update(
        userTaskIntermediateStateKey, userTaskIntermediateStateToWrite);
  }

  @Override
  public void deleteIntermediateState(final long key) {
    userTaskIntermediateStateKey.wrapLong(key);
    userTasksIntermediateStatesColumnFamily.deleteExisting(userTaskIntermediateStateKey);
  }

  @Override
  public void deleteIntermediateStateIfExists(final long key) {
    userTaskIntermediateStateKey.wrapLong(key);
    userTasksIntermediateStatesColumnFamily.deleteIfExists(userTaskIntermediateStateKey);
  }

  @Override
  public void storeRecordRequestMetadata(
      final long key, final UserTaskTransitionTriggerRequestMetadata recordRequestMetadata) {
    userTaskKey.wrapLong(key);
    userTasksTransitionTriggerRequestMetadataColumnFamily.insert(
        userTaskKey, recordRequestMetadata);
  }

  @Override
  public void deleteRecordRequestMetadata(final long key) {
    userTaskKey.wrapLong(key);
    userTasksTransitionTriggerRequestMetadataColumnFamily.deleteIfExists(userTaskKey);
  }

  @Override
  public void storeIntermediateAssignee(final long key, final String assignee) {
    if (!StringUtils.isEmpty(assignee)) {
      userTaskKey.wrapLong(key);
      intermediateAssignee.wrapString(assignee);
      userTasksIntermediateAssigneeColumnFamily.insert(userTaskKey, intermediateAssignee);
    }
  }

  @Override
  public void deleteIntermediateAssignee(final long key) {
    userTaskKey.wrapLong(key);
    userTasksIntermediateAssigneeColumnFamily.deleteIfExists(userTaskKey);
  }

  @Override
  public LifecycleState getLifecycleState(final long key) {
    userTaskKey.wrapLong(key);
    final UserTaskLifecycleStateValue storedLifecycleState =
        statesUserTaskColumnFamily.get(fkUserTask);
    if (storedLifecycleState == null) {
      return LifecycleState.NOT_FOUND;
    }
    return storedLifecycleState.getLifecycleState();
  }

  @Override
  public UserTaskRecord getUserTask(final long key) {
    userTaskKey.wrapLong(key);
    final UserTaskRecordValue userTask = userTasksColumnFamily.get(userTaskKey);
    return userTask == null ? null : userTask.getRecord();
  }

  @Override
  public UserTaskRecord getUserTask(final long key, final AuthorizedTenants authorizedTenantIds) {
    final UserTaskRecord userTask = getUserTask(key);
    if (userTask != null && authorizedTenantIds.isAuthorizedForTenantId(userTask.getTenantId())) {
      return userTask;
    }
    return null;
  }

  @Override
  public UserTaskIntermediateStateValue getIntermediateState(final long userTaskKey) {
    userTaskIntermediateStateKey.wrapLong(userTaskKey);
    return userTasksIntermediateStatesColumnFamily.get(userTaskIntermediateStateKey);
  }

  @Override
  public Optional<UserTaskTransitionTriggerRequestMetadata> findRecordRequestMetadata(
      final long key) {
    userTaskKey.wrapLong(key);
    return Optional.ofNullable(
        userTasksTransitionTriggerRequestMetadataColumnFamily.get(userTaskKey));
  }

  @Override
  public String getIntermediateAssignee(final long key) {
    userTaskKey.wrapLong(key);
    final var intermediateAssignee = userTasksIntermediateAssigneeColumnFamily.get(userTaskKey);
    return intermediateAssignee == null ? null : intermediateAssignee.toString();
  }
}
