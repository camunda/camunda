/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.test.util.record;

import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.value.ClockRecordValue;
import java.time.Instant;
import java.util.stream.Stream;

public class ClockRecordStream extends ExporterRecordStream<ClockRecordValue, ClockRecordStream> {

  public ClockRecordStream(final Stream<Record<ClockRecordValue>> wrappedStream) {
    super(wrappedStream);
  }

  @Override
  protected ClockRecordStream supply(final Stream<Record<ClockRecordValue>> wrappedStream) {
    return new ClockRecordStream(wrappedStream);
  }

  public ClockRecordStream withTimestamp(final Instant instant) {
    return valueFilter(v -> v.getTime() == instant.toEpochMilli());
  }
}
