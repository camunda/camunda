/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.processing;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.LongProperty;

public class LastProcessedPosition extends UnpackedObject implements DbValue {
  private final LongProperty positionProp = new LongProperty("lastProcessPosition");

  public LastProcessedPosition() {
    declareProperty(positionProp);
  }

  public void set(final long position) {
    positionProp.setValue(position);
  }

  public long get() {
    return positionProp.getValue();
  }
}
