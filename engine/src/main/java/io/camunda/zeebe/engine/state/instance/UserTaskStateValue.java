/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.instance;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.engine.state.immutable.UserTaskState.State;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.EnumProperty;

public class UserTaskStateValue extends UnpackedObject implements DbValue {

  private final EnumProperty<State> stateProp = new EnumProperty<>("userTaskState", State.class);

  public UserTaskStateValue() {
    declareProperty(stateProp);
  }

  public State getState() {
    return stateProp.getValue();
  }

  public void setState(final State state) {
    stateProp.setValue(state);
  }
}
