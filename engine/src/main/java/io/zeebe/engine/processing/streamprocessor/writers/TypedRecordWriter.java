/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processing.streamprocessor.writers;

import static io.zeebe.engine.processing.streamprocessor.TypedEventRegistry.EVENT_REGISTRY;

import io.zeebe.logstreams.log.LogStreamBatchWriter;
import io.zeebe.logstreams.log.LogStreamBatchWriter.LogEntryBuilder;
import io.zeebe.msgpack.UnpackedObject;
import io.zeebe.protocol.impl.record.RecordMetadata;
import io.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.zeebe.protocol.record.RecordType;
import io.zeebe.protocol.record.RejectionType;
import io.zeebe.protocol.record.ValueType;
import io.zeebe.protocol.record.intent.Intent;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class TypedRecordWriter {

  protected final Map<Class<? extends UnpackedObject>, ValueType> typeRegistry;
  protected final RecordMetadata metadata = new RecordMetadata();
  protected final LogStreamBatchWriter batchWriter;
  protected long sourceRecordPosition = -1;
  private final Consumer<RecordMetadata> noop = m -> {};

  public TypedRecordWriter(final LogStreamBatchWriter batchWriter) {
    this.batchWriter = batchWriter;
    typeRegistry = new HashMap<>();
    EVENT_REGISTRY.forEach((e, c) -> typeRegistry.put(c, e));
  }

  public void configureSourceContext(final long sourceRecordPosition) {
    this.sourceRecordPosition = sourceRecordPosition;
  }

  protected void initMetadata(
      final RecordType type, final Intent intent, final UnpackedObject value) {
    metadata.reset();
    final ValueType valueType = typeRegistry.get(value.getClass());
    if (valueType == null) {
      // usually happens when the record is not registered at the TypedStreamEnvironment
      throw new RuntimeException("Missing value type mapping for record: " + value.getClass());
    }

    metadata.recordType(type).valueType(valueType).intent(intent);
  }

  protected void appendRecord(
      final long key, final RecordType type, final Intent intent, final UnifiedRecordValue value) {
    appendRecord(key, type, intent, RejectionType.NULL_VAL, "", value, noop);
  }

  protected void appendRecord(
      final long key,
      final RecordType type,
      final Intent intent,
      final UnifiedRecordValue value,
      final Consumer<RecordMetadata> additionalMetadata) {
    appendRecord(key, type, intent, RejectionType.NULL_VAL, "", value, additionalMetadata);
  }

  protected void appendRecord(
      final long key,
      final RecordType type,
      final Intent intent,
      final RejectionType rejectionType,
      final String rejectionReason,
      final UnifiedRecordValue value,
      final Consumer<RecordMetadata> additionalMetadata) {

    final LogEntryBuilder event = batchWriter.event();

    if (sourceRecordPosition >= 0) {
      batchWriter.sourceRecordPosition(sourceRecordPosition);
    }

    initMetadata(type, intent, value);
    metadata.rejectionType(rejectionType);
    metadata.rejectionReason(rejectionReason);
    additionalMetadata.accept(metadata);

    if (key >= 0) {
      event.key(key);
    } else {
      event.keyNull();
    }

    event.metadataWriter(metadata).valueWriter(value).done();
  }

  public void reset() {
    batchWriter.reset();
  }

  public long flush() {
    return batchWriter.tryWrite();
  }
}
