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
  private final LongProperty pinProperty = new LongProperty("pin", 0);
  private final LongProperty offsetProperty = new LongProperty("offset", 0);

  public ClockRecord() {
    super(2);
    declareProperty(pinProperty).declareProperty(offsetProperty);
  }

  @Override
  public boolean hasPinnedEpoch() {
    return pinProperty.isSet();
  }

  @Override
  public long getPinnedAtEpoch() {
    return pinProperty.getValue();
  }

  @Override
  public long getOffsetMillis() {
    return offsetProperty.getValue();
  }

  @Override
  public boolean hasOffsetMillis() {
    return offsetProperty.isSet();
  }

  public ClockRecord pinAt(final long pinnedEpoch) {
    reset();
    pinProperty.setValue(pinnedEpoch);
    return this;
  }

  public ClockRecord offsetBy(final long offsetMillis) {
    reset();
    offsetProperty.setValue(offsetMillis);
    return this;
  }
}
