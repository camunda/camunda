/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.stream.impl.records;

import static io.camunda.zeebe.stream.impl.TypedEventRegistry.EVENT_REGISTRY;

import io.camunda.zeebe.logstreams.log.LogAppendEntry;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.util.ReflectUtil;
import io.camunda.zeebe.util.buffer.BufferWriter;
import org.agrona.concurrent.UnsafeBuffer;

public record RecordBatchEntry(
    RecordMetadata recordMetadata, long key, int sourceIndex, UnifiedRecordValue unifiedRecordValue)
    implements LogAppendEntry {

  @Override
  public UnifiedRecordValue recordValue() {
    return unifiedRecordValue;
  }

  public static RecordBatchEntry createEntry(
      final long key,
      final RecordMetadata metadata,
      final int sourceIndex,
      final BufferWriter valueWriter) {

    // we need to copy the value, to make sure that it will not change later
    final var bytes = new byte[valueWriter.getLength()];
    final var recordValueBuffer = new UnsafeBuffer(bytes);
    valueWriter.write(recordValueBuffer, 0);

    final UnifiedRecordValue unifiedRecordValue =
        ReflectUtil.newInstance(EVENT_REGISTRY.get(metadata.getValueType()));
    unifiedRecordValue.wrap(recordValueBuffer, 0, recordValueBuffer.capacity());

    return new RecordBatchEntry(metadata, key, sourceIndex, unifiedRecordValue);
  }
}
