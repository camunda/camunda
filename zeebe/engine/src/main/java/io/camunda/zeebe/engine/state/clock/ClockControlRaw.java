/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.clock;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.ObjectProperty;
import io.camunda.zeebe.protocol.impl.record.value.clock.ClockControlRecord;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

/** ClockControlRecord wrapper to store the record in the State. */
public class ClockControlRaw extends UnpackedObject implements DbValue {

  private final ObjectProperty<ClockControlRecord> clockControlRecordProperty =
      new ObjectProperty<>("clockControlRecord", new ClockControlRecord());

  public ClockControlRaw() {
    super(1);
    declareProperty(clockControlRecordProperty);
  }

  public ClockControlRecord getClockControlRecord() {
    return clockControlRecordProperty.getValue();
  }

  public void setClockControlRecord(final ClockControlRecord clockControlRecord) {
    final MutableDirectBuffer valueBuffer = new UnsafeBuffer(0, 0);
    final int encodedLength = clockControlRecord.getLength();
    valueBuffer.wrap(new byte[encodedLength]);

    clockControlRecord.write(valueBuffer, 0);
    clockControlRecordProperty.getValue().wrap(valueBuffer, 0, encodedLength);
  }
}
