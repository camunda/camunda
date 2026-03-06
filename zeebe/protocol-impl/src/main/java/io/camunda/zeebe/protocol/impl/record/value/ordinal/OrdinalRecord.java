/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.ordinal;

import io.camunda.zeebe.msgpack.property.IntegerProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.value.StringValue;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.value.OrdinalRecordValue;

public final class OrdinalRecord extends UnifiedRecordValue implements OrdinalRecordValue {

  private static final StringValue ORDINAL_KEY = new StringValue("ordinal");
  private static final StringValue DATE_TIME_KEY = new StringValue("dateTime");

  /** Monotonically incrementing short stored as int (msgpack has no native short). */
  private final IntegerProperty ordinalProp = new IntegerProperty(ORDINAL_KEY, 0);

  /** Epoch-millisecond wall-clock time at which this ordinal was assigned. */
  private final LongProperty dateTimeProp = new LongProperty(DATE_TIME_KEY, 0L);

  public OrdinalRecord() {
    super(2);
    declareProperty(ordinalProp).declareProperty(dateTimeProp);
  }

  @Override
  public int getOrdinal() {
    return ordinalProp.getValue();
  }

  public OrdinalRecord setOrdinal(final int ordinal) {
    ordinalProp.setValue(ordinal);
    return this;
  }

  @Override
  public long getDateTime() {
    return dateTimeProp.getValue();
  }

  public OrdinalRecord setDateTime(final long dateTimeMillis) {
    dateTimeProp.setValue(dateTimeMillis);
    return this;
  }
}
