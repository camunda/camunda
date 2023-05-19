/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.stream.impl.records;

import io.camunda.zeebe.logstreams.log.LogAppendEntry;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.stream.api.records.ExceededBatchRecordSizeException;
import io.camunda.zeebe.stream.api.records.ImmutableRecordBatch;
import io.camunda.zeebe.stream.api.records.MutableRecordBatch;
import io.camunda.zeebe.stream.api.records.RecordBatchSizePredicate;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.buffer.BufferWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Spliterator;
import java.util.function.Consumer;

public final class RecordBatch implements MutableRecordBatch {

  final List<RecordBatchEntry> recordBatchEntries = new ArrayList<>();
  private int batchSize;
  private final RecordBatchSizePredicate recordBatchSizePredicate;

  public RecordBatch(final RecordBatchSizePredicate recordBatchSizePredicate) {
    this.recordBatchSizePredicate = recordBatchSizePredicate;
  }

  public static ImmutableRecordBatch empty() {
    return new RecordBatch((c, s) -> false);
  }

  @Override
  public Either<RuntimeException, Void> appendRecord(
      final long key,
      final int sourceIndex,
      final RecordType recordType,
      final Intent intent,
      final RejectionType rejectionType,
      final String rejectionReason,
      final ValueType valueType,
      final BufferWriter valueWriter) {
    final var metadata =
        new RecordMetadata()
            .recordType(recordType)
            .intent(intent)
            .rejectionType(rejectionType)
            .rejectionReason(rejectionReason)
            .valueType(valueType);
    final var recordBatchEntry =
        RecordBatchEntry.createEntry(key, metadata, sourceIndex, valueWriter);
    final var entryLength = recordBatchEntry.getLength();

    if (!recordBatchSizePredicate.test(recordBatchEntries.size() + 1, batchSize + entryLength)) {
      return Either.left(
          new ExceededBatchRecordSizeException(
              recordBatchEntry, entryLength, recordBatchEntries.size(), batchSize));
    }

    recordBatchEntries.add(recordBatchEntry);
    batchSize += entryLength;
    return Either.right(null);
  }

  @Override
  public boolean canAppendRecordOfLength(final int recordLength) {
    return recordBatchSizePredicate.test(recordBatchEntries.size() + 1, batchSize + recordLength);
  }

  public int getBatchSize() {
    return batchSize;
  }

  @Override
  public Iterator<RecordBatchEntry> iterator() {
    return recordBatchEntries.iterator();
  }

  @Override
  public void forEach(final Consumer<? super RecordBatchEntry> action) {
    recordBatchEntries.forEach(action);
  }

  @Override
  public Spliterator<RecordBatchEntry> spliterator() {
    return recordBatchEntries.spliterator();
  }

  @Override
  public List<LogAppendEntry> entries() {
    return Collections.unmodifiableList(recordBatchEntries);
  }
}
