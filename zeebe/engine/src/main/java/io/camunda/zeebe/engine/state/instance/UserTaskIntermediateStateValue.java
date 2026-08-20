/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.instance;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.engine.state.immutable.UserTaskState.LifecycleState;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.EnumProperty;
import io.camunda.zeebe.msgpack.property.ObjectProperty;
import io.camunda.zeebe.protocol.impl.record.value.usertask.UserTaskRecord;
import java.util.Objects;

/**
 * This class holds the necessary user task data (including variables) to finalize commands such as
 * `COMPLETE`, `UPDATE`, `ASSIGN` etc. mainly when task listeners are defined for these operations.
 *
 * <p>The intermediate state includes a {@link UserTaskRecord} that captures the state of the user
 * task as it was when the command (e.g., COMPLETE, ASSIGN) was invoked. It also tracks the {@link
 * LifecycleState} of the task, representing the current state in its lifecycle (e.g., COMPLETING,
 * ASSIGNING).
 *
 * <p>This state is stored immediately after the user task command is invoked and before the task
 * listeners are executed. It is used to finalize the command after all task listeners have been
 * processed. Once the original command is applied, this state will be removed.
 *
 * <p>This ensures that any data provided with the user task command is persisted and correctly
 * applied after task listener execution when task listeners are defined for the operation.
 *
 * <p>For example, when the user task is being assigned for the first time, the user task must
 * remain unassigned while the task listener blocks the assignment. In the meantime, this
 * intermediate state tracks the new assignee, which can be applied after the task listener
 * completes.
 */
public class UserTaskIntermediateStateValue extends UnpackedObject implements DbValue {

  private final ObjectProperty<UserTaskRecord> recordProp =
      new ObjectProperty<>("userTaskRecord", new UserTaskRecord());

  private final EnumProperty<LifecycleState> lifecycleStateProp =
      new EnumProperty<>("lifecycleState", LifecycleState.class);

  public UserTaskIntermediateStateValue() {
    super(2);
    declareProperty(recordProp).declareProperty(lifecycleStateProp);
  }

  public UserTaskRecord getRecord() {
    return recordProp.getValue();
  }

  public void setRecord(final UserTaskRecord record) {
    recordProp.getValue().wrap(record);
  }

  public LifecycleState getLifecycleState() {
    return Objects.requireNonNullElse(lifecycleStateProp.getValue(), LifecycleState.NOT_FOUND);
  }

  public void setLifecycleState(final LifecycleState lifecycleState) {
    lifecycleStateProp.setValue(lifecycleState);
  }
}
