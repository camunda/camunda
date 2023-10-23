/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.instance;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.engine.state.usertask.MutableUserTaskState;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.record.value.usertask.UserTaskRecord;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;

public class DbUserTaskState implements MutableUserTaskState {

  private final ColumnFamily<DbLong, UserTaskStateValue> userTasksColumnFamily;

  public DbUserTaskState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {

    userTasksColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.USER_TASKS,
            transactionContext,
            new DbLong(),
            new UserTaskStateValue());
  }

  @Override
  public void create(final long key, final UserTaskRecord userTask) {
    final DbLong userTaskKey = new DbLong();
    userTaskKey.wrapLong(key);

    final UserTaskStateValue stateValue = new UserTaskStateValue();
    stateValue.setUserTask(userTask);
    stateValue.setState(UserTaskIntent.CREATING);

    userTasksColumnFamily.insert(userTaskKey, stateValue);
  }

  @Override
  public UserTaskRecord get(final long key) {
    final DbLong userTaskKey = new DbLong();
    userTaskKey.wrapLong(key);
    return userTasksColumnFamily.get(userTaskKey).getUserTask();
  }
}
