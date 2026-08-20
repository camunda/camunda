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
import io.camunda.zeebe.msgpack.property.LongProperty;

public final class SuspensionMarkerValue extends UnpackedObject implements DbValue {

  private final EnumProperty<SuspensionState.State> stateProp =
      new EnumProperty<>("suspensionState", SuspensionState.State.class);
  private final LongProperty lastResumedJobKeyProp = new LongProperty("lastResumedJobKey", -1L);

  public SuspensionMarkerValue() {
    super(2);
    declareProperty(stateProp).declareProperty(lastResumedJobKeyProp);
  }

  public SuspensionState.State getState() {
    return stateProp.getValue();
  }

  public void setState(final SuspensionState.State state) {
    stateProp.setValue(state);
  }

  public long getLastResumedJobKey() {
    return lastResumedJobKeyProp.getValue();
  }

  public void setLastResumedJobKey(final long lastResumedJobKey) {
    lastResumedJobKeyProp.setValue(lastResumedJobKey);
  }
}
