/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.migration;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.EnumProperty;

public final class MigrationState extends UnpackedObject implements DbValue {

  private final EnumProperty<State> stateProp =
      new EnumProperty<>("state", State.class, State.FINISHED);

  public MigrationState() {
    declareProperty(stateProp);
  }

  public State getState() {
    return stateProp.getValue();
  }

  public enum State {
    FINISHED(0);

    byte value;

    State(final int value) {
      this.value = (byte) value;
    }
  }
}
