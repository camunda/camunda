/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.state.instance;

import io.zeebe.db.DbValue;
import io.zeebe.engine.state.immutable.JobState;
import io.zeebe.msgpack.UnpackedObject;
import io.zeebe.msgpack.property.EnumProperty;

public class JobStateValue extends UnpackedObject implements DbValue {

  private final EnumProperty<JobState.State> stateProp =
      new EnumProperty<>("jobState", JobState.State.class);

  public JobStateValue() {
    declareProperty(stateProp);
  }

  public JobState.State getState() {
    return stateProp.getValue();
  }

  public void setState(final JobState.State state) {
    stateProp.setValue(state);
  }
}
