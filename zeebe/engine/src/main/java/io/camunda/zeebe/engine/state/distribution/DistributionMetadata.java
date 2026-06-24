/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.distribution;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.value.StringValue;

public class DistributionMetadata extends UnpackedObject implements DbValue {

  private static final StringValue START_TIME = new StringValue("startTime");

  private final LongProperty startTimeProp = new LongProperty(START_TIME, -1L);

  public DistributionMetadata() {
    super(1);
    declareProperty(startTimeProp);
  }

  public long getStartTime() {
    return startTimeProp.getValue();
  }

  public DistributionMetadata setStartTime(final long startTime) {
    startTimeProp.setValue(startTime);
    return this;
  }
}
