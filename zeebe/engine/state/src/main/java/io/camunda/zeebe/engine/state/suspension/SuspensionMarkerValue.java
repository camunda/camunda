/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.suspension;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.engine.state.immutable.SuspensionState;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.EnumProperty;

public final class SuspensionMarkerValue extends UnpackedObject implements DbValue {

  private final EnumProperty<SuspensionState.State> stateProp =
      new EnumProperty<>("suspensionState", SuspensionState.State.class);

  public SuspensionMarkerValue() {
    super(1);
    declareProperty(stateProp);
  }

  public SuspensionState.State getState() {
    return stateProp.getValue();
  }

  public void setState(final SuspensionState.State state) {
    stateProp.setValue(state);
  }
}
