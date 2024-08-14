/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.util.client;

import io.camunda.zeebe.protocol.impl.record.value.clock.ClockRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.ClockIntent;
import io.camunda.zeebe.protocol.record.value.ClockRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.time.Instant;
import java.util.function.Function;

public class ClockClient {
  private static final Function<Long, Record<ClockRecordValue>> PIN_SUCCESS_EXPECTATION =
      (position) ->
          RecordingExporter.clockRecords(ClockIntent.PINNED)
              .withSourceRecordPosition(position)
              .getFirst();
  private static final Function<Long, Record<ClockRecordValue>> RESET_SUCCESS_EXPECTATION =
      (position) ->
          RecordingExporter.clockRecords(ClockIntent.RESETTED)
              .withSourceRecordPosition(position)
              .getFirst();

  private final CommandWriter writer;
  private final ClockRecord record = new ClockRecord();

  public ClockClient(final CommandWriter writer) {
    this.writer = writer;
  }

  public Record<ClockRecordValue> pinAt(final Instant now) {
    final long position = writer.writeCommand(ClockIntent.PIN, record.pinAt(now.toEpochMilli()));
    return PIN_SUCCESS_EXPECTATION.apply(position);
  }

  public Record<ClockRecordValue> reset() {
    record.reset();
    final long position = writer.writeCommand(ClockIntent.RESET, record);
    return RESET_SUCCESS_EXPECTATION.apply(position);
  }
}
