/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.job.state.instance;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.engine.job.state.immutable.JobState;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.EnumProperty;

public class JobStateValue extends UnpackedObject implements DbValue {

  private final EnumProperty<JobState.State> stateProp =
      new EnumProperty<>("jobState", JobState.State.class);

  public JobStateValue() {
    super(1);
    declareProperty(stateProp);
  }

  public JobState.State getState() {
    return stateProp.getValue();
  }

  public void setState(final JobState.State state) {
    stateProp.setValue(state);
  }
}
