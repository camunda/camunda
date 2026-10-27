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

public final class ClockRecord extends UnifiedRecordValue implements ClockRecordValue {

  // depending on the intent, the value may be a:
  // - timestamp (epoch milliseconds)
  // - offset (in milliseconds)
  private final LongProperty timeProperty = new LongProperty("time", 0);

  public ClockRecord() {
    super(1);
    declareProperty(timeProperty);
  }

  @Override
  public long getTime() {
    return timeProperty.getValue();
  }

  public ClockRecord pinAt(final long pinnedEpoch) {
    reset();
    timeProperty.setValue(pinnedEpoch);
    return this;
  }

  public ClockRecord offsetBy(final long offsetMillis) {
    reset();
    timeProperty.setValue(offsetMillis);
    return this;
  }
}
