/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.instance;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.EnumProperty;
import io.camunda.zeebe.msgpack.property.ObjectProperty;
import io.camunda.zeebe.protocol.impl.record.value.usertask.UserTaskRecord;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;

public class UserTaskStateValue extends UnpackedObject implements DbValue {

  private final ObjectProperty<UserTaskRecord> userTaskProp =
      new ObjectProperty<UserTaskRecord>("userTask", new UserTaskRecord());

  private final EnumProperty<UserTaskIntent> stateProperty =
      new EnumProperty<>("state", UserTaskIntent.class);

  public UserTaskStateValue() {
    declareProperty(userTaskProp);
  }

  public UserTaskIntent getState() {
    return stateProperty.getValue();
  }

  public UserTaskStateValue setState(final UserTaskIntent state) {
    stateProperty.setValue(state);
    return this;
  }

  public UserTaskRecord getUserTask() {
    // TODO: create a copy
    return userTaskProp.getValue();
  }

  public UserTaskStateValue setUserTask(final UserTaskRecord userTask) {
    userTaskProp.getValue().wrap(userTask);
    return this;
  }
}
