/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.clock;

import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.value.ClockRecordValue;

/**
 * Frozen copy of the hand-written UnpackedObject implementation, kept to verify that the generated
 * codec of the current class keeps the stored format. Do not modify.
 */
public final class ClockRecordV1 extends UnifiedRecordValue implements ClockRecordValue {

  // depending on the intent, the value may be a:
  // - timestamp (epoch milliseconds)
  // - offset (in milliseconds), either the duration to add (ADD) or the total offset (OFFSET)
  // - current time of the clock (epoch milliseconds), for FETCHED
  private final LongProperty timeProperty = new LongProperty("time", 0);

  public ClockRecordV1() {
    super(1);
    declareProperty(timeProperty);
  }

  @Override
  public long getTime() {
    return timeProperty.getValue();
  }

  public ClockRecordV1 pinAt(final long pinnedEpoch) {
    reset();
    timeProperty.setValue(pinnedEpoch);
    return this;
  }

  public ClockRecordV1 offsetBy(final long offsetMillis) {
    reset();
    timeProperty.setValue(offsetMillis);
    return this;
  }

  public ClockRecordV1 setTime(final long epochMillis) {
    timeProperty.setValue(epochMillis);
    return this;
  }
}
