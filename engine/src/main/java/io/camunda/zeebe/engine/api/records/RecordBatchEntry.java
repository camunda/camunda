/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.api.records;

import static io.camunda.zeebe.engine.processing.streamprocessor.TypedEventRegistry.EVENT_REGISTRY;

import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.util.ReflectUtil;
import io.camunda.zeebe.util.buffer.BufferWriter;
import org.agrona.concurrent.UnsafeBuffer;

public record RecordBatchEntry(
    long key, int sourceIndex, RecordMetadata recordMetadata, UnifiedRecordValue unifiedRecordValue)
    implements ImmutableRecordBatchEntry {

  @Override
  public UnifiedRecordValue recordValue() {
    return unifiedRecordValue;
  }

  @Override
  public int getLength() {
    return Long.BYTES
        + // key
        Integer.BYTES
        + // source Index
        recordMetadata.getLength()
        + unifiedRecordValue.getLength();
  }

  public int getMetadataLength() {
    return recordMetadata.getLength();
  }

  public int getValueLength() {
    return unifiedRecordValue.getLength();
  }

  public static RecordBatchEntry createEntry(
      final long key,
      final int sourceIndex,
      final RecordType recordType,
      final Intent intent,
      final RejectionType rejectionType,
      final String rejectionReason,
      final ValueType valueType,
      final BufferWriter valueWriter) {
    final var recordMetadata =
        new RecordMetadata()
            .recordType(recordType)
            .intent(intent)
            .rejectionType(rejectionType)
            .rejectionReason(rejectionReason)
            .valueType(valueType);

    // we need to copy the value, to make sure that it will not change later
    final var bytes = new byte[valueWriter.getLength()];
    final var recordValueBuffer = new UnsafeBuffer(bytes);
    valueWriter.write(recordValueBuffer, 0);

    final UnifiedRecordValue unifiedRecordValue =
        ReflectUtil.newInstance(EVENT_REGISTRY.get(recordMetadata.getValueType()));
    unifiedRecordValue.wrap(recordValueBuffer, 0, recordValueBuffer.capacity());

    return new RecordBatchEntry(key, sourceIndex, recordMetadata, unifiedRecordValue);
  }
}
