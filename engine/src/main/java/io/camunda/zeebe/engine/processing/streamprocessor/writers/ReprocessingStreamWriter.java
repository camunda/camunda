/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.processing.streamprocessor.writers;

import io.zeebe.engine.processing.streamprocessor.TypedRecord;
import io.zeebe.protocol.record.RecordType;
import io.zeebe.protocol.record.RecordValue;
import io.zeebe.protocol.record.RejectionType;
import io.zeebe.protocol.record.intent.Intent;
import java.util.ArrayList;
import java.util.List;

public final class ReprocessingStreamWriter implements TypedStreamWriter {

  private final List<ReprocessingRecord> records = new ArrayList<>();

  private long sourceRecordPosition = -1L;

  @Override
  public void appendRejection(
      final TypedRecord<? extends RecordValue> command,
      final RejectionType type,
      final String reason) {

    final var record =
        new ReprocessingRecord(
            command.getKey(),
            sourceRecordPosition,
            command.getIntent(),
            RecordType.COMMAND_REJECTION);
    records.add(record);
  }

  @Override
  public void configureSourceContext(final long sourceRecordPosition) {
    this.sourceRecordPosition = sourceRecordPosition;
  }

  @Override
  public void appendFollowUpEvent(final long key, final Intent intent, final RecordValue value) {

    final var record = new ReprocessingRecord(key, sourceRecordPosition, intent, RecordType.EVENT);
    records.add(record);
  }

  @Override
  public void appendNewCommand(final Intent intent, final RecordValue value) {

    final var record =
        new ReprocessingRecord(-1L, sourceRecordPosition, intent, RecordType.COMMAND);
    records.add(record);
  }

  @Override
  public void appendFollowUpCommand(final long key, final Intent intent, final RecordValue value) {

    final var record =
        new ReprocessingRecord(key, sourceRecordPosition, intent, RecordType.COMMAND);
    records.add(record);
  }

  @Override
  public void reset() {
    sourceRecordPosition = -1;
  }

  @Override
  public long flush() {
    return 0;
  }

  public List<ReprocessingRecord> getRecords() {
    return records;
  }

  public void removeRecord(final long recordKey, final long sourceRecordPosition) {
    records.removeIf(
        record ->
            record.getKey() == recordKey
                && record.getSourceRecordPosition() == sourceRecordPosition);
  }
}
