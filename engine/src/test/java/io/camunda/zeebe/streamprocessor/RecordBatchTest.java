/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.streamprocessor;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.Records;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.util.buffer.BufferWriter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Spliterator;
import java.util.function.Consumer;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

public class BufferedStreamWriterTest {


  @Test
  public void shouldWriteRecordToBuffer() {
    // given
    final var recordBatch = new RecordBatch();
    final var processInstanceRecord = Records.processInstance(1);

    // when
    recordBatch.appendRecord(1,
        -1,
        RecordType.COMMAND,
        ProcessInstanceIntent.ACTIVATE_ELEMENT,
        RejectionType.ALREADY_EXISTS,
        "broken somehow",
        ValueType.PROCESS_INSTANCE,
        processInstanceRecord);

    // then
    final var batchSize = recordBatch.getBatchSize();
    assertThat(batchSize).isGreaterThan(processInstanceRecord.getLength());

    assertThat(recordBatch).map(RecordBatchEntry::key).containsOnly(1L);
    assertThat(recordBatch).map(RecordBatchEntry::sourceIndex).containsOnly(-1);
    assertThat(recordBatch).map(RecordBatchEntry::recordMetadata).map(RecordMetadata::getIntent).containsOnly(ProcessInstanceIntent.ACTIVATE_ELEMENT);
    assertThat(recordBatch).map(RecordBatchEntry::recordMetadata).map(RecordMetadata::getRecordType).containsOnly(RecordType.COMMAND);
    assertThat(recordBatch).map(RecordBatchEntry::recordMetadata).map(RecordMetadata::getRejectionType).containsOnly(RejectionType.ALREADY_EXISTS);
    assertThat(recordBatch).map(RecordBatchEntry::recordMetadata).map(RecordMetadata::getValueType).containsOnly(ValueType.PROCESS_INSTANCE);
    assertThat(recordBatch).map(RecordBatchEntry::recordMetadata).map(RecordMetadata::getRejectionReason).containsOnly("broken somehow");
  }

  private static final class RecordBatch implements Iterable<RecordBatchEntry> {
    final List<RecordBatchEntry> recordBatchEntries = new ArrayList<>();
    private int batchSize;

    void appendRecord(
        final long key,
        final int sourceIndex,
        final RecordType type,
        final Intent intent,
        final RejectionType rejectionType,
        final String rejectionReason,
        final ValueType valueType,
        final BufferWriter valueWriter) {
      final var recordBatchEntry = RecordBatchEntry.createRecordBatchEntry(key, sourceIndex, type,
          intent, rejectionType, rejectionReason, valueType, valueWriter);
      recordBatchEntries.add(recordBatchEntry);
      batchSize += recordBatchEntry.getLength();
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
  }

  private record RecordBatchEntry(long key, int sourceIndex,
                                  RecordMetadata recordMetadata,
                                  DirectBuffer recordValueBuffer) {
    public static RecordBatchEntry createRecordBatchEntry(final long key, final int sourceIndex, final RecordType recordType,
        final Intent intent,
        final RejectionType rejectionType, final String rejectionReason, final ValueType valueType,
        final BufferWriter valueWriter) {
      final var recordMetadata =
          new RecordMetadata()
              .recordType(recordType)
              .intent(intent)
              .rejectionType(rejectionType)
              .rejectionReason(rejectionReason)
              .valueType(valueType);

      final var bytes = new byte[valueWriter.getLength()];
      final var recordValueBuffer = new UnsafeBuffer(bytes);
      valueWriter.write(recordValueBuffer, 0);
      return new RecordBatchEntry(key, sourceIndex, recordMetadata, recordValueBuffer);
    }

    public int getLength() {
      return
        Long.BYTES + // key
        Integer.BYTES + // source Index
        recordMetadata.getLength() +
        recordValueBuffer.capacity();
    }
  }
}
