/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.instance;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.ObjectProperty;
import io.camunda.zeebe.protocol.impl.record.value.usertask.UserTaskRecord;

/**
 * This class holds the necessary user task data (including variables) to finalize commands such as
 * `COMPLETE`, `UPDATE`, `ASSIGN` etc. mainly when task listeners are defined for these operations.
 *
 * <p>The intermediate state includes {@link UserTaskRecord} containing the state of the user task
 * as received with the command.
 *
 * <p>This state is stored immediately after the user task command is invoked and before the task
 * listeners are executed. It is used to finalize the command after all task listeners have been
 * processed. Once the command is applied and the operation is finalized, this state will be
 * removed.
 *
 * <p>This ensures that any data provided with the user task command is persisted and correctly
 * applied after task listener execution when task listeners are defined for the operation.
 */
public class UserTaskIntermediateState extends UnpackedObject implements DbValue {

  private final ObjectProperty<UserTaskRecord> recordProp =
      new ObjectProperty<>("intermediateUserTaskRecord", new UserTaskRecord());

  public UserTaskIntermediateState() {
    super(1);
    declareProperty(recordProp);
  }

  public UserTaskRecord getRecord() {
    return recordProp.getValue();
  }

  public void setRecord(final UserTaskRecord record) {
    recordProp.getValue().wrap(record);
  }
}
