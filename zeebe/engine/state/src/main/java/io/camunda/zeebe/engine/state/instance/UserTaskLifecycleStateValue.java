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

public class UserTaskLifecycleStateValue extends UnpackedObject implements DbValue {

  private final EnumProperty<LifecycleState> lifecycleStateProp =
      new EnumProperty<>("userTaskLifecycleState", LifecycleState.class);

  public UserTaskLifecycleStateValue() {
    super(1);
    declareProperty(lifecycleStateProp);
  }

  public LifecycleState getLifecycleState() {
    return lifecycleStateProp.getValue();
  }

  public void setLifecycleState(final LifecycleState lifecycleState) {
    lifecycleStateProp.setValue(lifecycleState);
  }
}
