/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.processing.streamprocessor.writers;

import static io.zeebe.engine.processing.streamprocessor.TypedEventRegistry.EVENT_REGISTRY;

import io.zeebe.engine.processing.streamprocessor.TypedRecord;
import io.zeebe.logstreams.log.LogStreamBatchWriter;
import io.zeebe.logstreams.log.LogStreamBatchWriter.LogEntryBuilder;
import io.zeebe.msgpack.UnpackedObject;
import io.zeebe.protocol.impl.record.RecordMetadata;
import io.zeebe.protocol.record.RecordType;
import io.zeebe.protocol.record.RecordValue;
import io.zeebe.protocol.record.RejectionType;
import io.zeebe.protocol.record.ValueType;
import io.zeebe.protocol.record.intent.Intent;
import io.zeebe.util.buffer.BufferWriter;
import java.util.HashMap;
import java.util.Map;

public class TypedStreamWriterImpl implements TypedStreamWriter {

  private final Map<Class<? extends UnpackedObject>, ValueType> typeRegistry;
  private final RecordMetadata metadata = new RecordMetadata();
  private final LogStreamBatchWriter batchWriter;

  private long sourceRecordPosition = -1;

  public TypedStreamWriterImpl(final LogStreamBatchWriter batchWriter) {
    this.batchWriter = batchWriter;
    typeRegistry = new HashMap<>();
    EVENT_REGISTRY.forEach((e, c) -> typeRegistry.put(c, e));
  }

  protected void initMetadata(final RecordType type, final Intent intent, final RecordValue value) {
    metadata.reset();
    final ValueType valueType = typeRegistry.get(value.getClass());
    if (valueType == null) {
      // usually happens when the record is not registered at the TypedStreamEnvironment
      throw new RuntimeException("Missing value type mapping for record: " + value.getClass());
    }

    metadata.recordType(type).valueType(valueType).intent(intent);
  }

  protected void appendRecord(
      final long key, final RecordType type, final Intent intent, final RecordValue value) {
    appendRecord(key, type, intent, RejectionType.NULL_VAL, "", value);
  }

  protected void appendRecord(
      final long key,
      final RecordType type,
      final Intent intent,
      final RejectionType rejectionType,
      final String rejectionReason,
      final RecordValue value) {

    final LogEntryBuilder event = batchWriter.event();

    if (sourceRecordPosition >= 0) {
      batchWriter.sourceRecordPosition(sourceRecordPosition);
    }

    initMetadata(type, intent, value);
    metadata.rejectionType(rejectionType);
    metadata.rejectionReason(rejectionReason);

    if (key >= 0) {
      event.key(key);
    } else {
      event.keyNull();
    }

    if (value instanceof BufferWriter) {
      event.metadataWriter(metadata).valueWriter((BufferWriter) value).done();
    } else {
      throw new RuntimeException(String.format("The record value %s is not a BufferWriter", value));
    }
  }

  @Override
  public void appendNewCommand(final Intent intent, final RecordValue value) {
    appendRecord(-1, RecordType.COMMAND, intent, value);
  }

  @Override
  public void appendFollowUpCommand(final long key, final Intent intent, final RecordValue value) {
    appendRecord(key, RecordType.COMMAND, intent, value);
  }

  @Override
  public void reset() {
    batchWriter.reset();
  }

  @Override
  public long flush() {
    return batchWriter.tryWrite();
  }

  @Override
  public void appendRejection(
      final TypedRecord<? extends RecordValue> command,
      final RejectionType rejectionType,
      final String reason) {
    appendRecord(
        command.getKey(),
        RecordType.COMMAND_REJECTION,
        command.getIntent(),
        rejectionType,
        reason,
        command.getValue());
  }

  @Override
  public void configureSourceContext(final long sourceRecordPosition) {
    this.sourceRecordPosition = sourceRecordPosition;
  }

  @Override
  public void appendFollowUpEvent(final long key, final Intent intent, final RecordValue value) {
    appendRecord(key, RecordType.EVENT, intent, value);
  }
}
