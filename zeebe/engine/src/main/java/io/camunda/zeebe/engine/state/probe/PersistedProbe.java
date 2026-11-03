/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.probe;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.engine.state.immutable.ProbeState.State;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.EnumProperty;
import io.camunda.zeebe.msgpack.property.ObjectProperty;
import io.camunda.zeebe.protocol.impl.record.value.probe.ProbeRecord;

public final class PersistedProbe extends UnpackedObject implements DbValue {

  private final EnumProperty<State> stateProp =
      new EnumProperty<>("state", State.class, State.ACTIVATABLE);
  private final ObjectProperty<ProbeRecord> recordProp =
      new ObjectProperty<>("probeRecord", new ProbeRecord());

  public PersistedProbe() {
    super(2);
    declareProperty(stateProp).declareProperty(recordProp);
  }

  public State getState() {
    return stateProp.getValue();
  }

  public PersistedProbe setState(final State state) {
    stateProp.setValue(state);
    return this;
  }

  public ProbeRecord getRecord() {
    return recordProp.getValue();
  }

  public PersistedProbe setRecord(final ProbeRecord record) {
    recordProp.getValue().copyFrom(record);
    return this;
  }
}
