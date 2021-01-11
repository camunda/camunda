/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processing.streamprocessor.writers;

import io.zeebe.engine.processing.streamprocessor.TypedRecord;
import io.zeebe.logstreams.log.LogStreamBatchWriter;
import io.zeebe.msgpack.UnpackedObject;
import io.zeebe.protocol.impl.record.RecordMetadata;
import io.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.zeebe.protocol.record.RecordType;
import io.zeebe.protocol.record.RejectionType;
import io.zeebe.protocol.record.intent.Intent;
import java.util.function.Consumer;

public final class TypedStreamWriterImpl implements TypedStreamWriter {

  private final TypedCommandWriter commandWriter;
  private final TypedEventWriterImpl eventWriter;
  private final TypedRecordWriter recordWriter;

  // todo remove this (it's already in typedrecordwriter
  private final Consumer<RecordMetadata> noop = m -> {};

  public TypedStreamWriterImpl(final LogStreamBatchWriter batchWriter) {
    recordWriter = new TypedRecordWriter(batchWriter);
    commandWriter = new TypedCommandWriterImpl(recordWriter);
    eventWriter = new TypedEventWriterImpl(recordWriter);
  }

  @Override
  public void appendRejection(
      final TypedRecord<? extends UnpackedObject> command,
      final RejectionType rejectionType,
      final String reason) {
    recordWriter.appendRecord(
        command.getKey(),
        RecordType.COMMAND_REJECTION,
        command.getIntent(),
        rejectionType,
        reason,
        command.getValue(),
        noop);
  }

  @Override
  public void appendRejection(
      final TypedRecord<? extends UnpackedObject> command,
      final RejectionType rejectionType,
      final String reason,
      final Consumer<RecordMetadata> metadata) {
    recordWriter.appendRecord(
        command.getKey(),
        RecordType.COMMAND_REJECTION,
        command.getIntent(),
        rejectionType,
        reason,
        command.getValue(),
        metadata);
  }

  @Override
  public void configureSourceContext(final long sourceRecordPosition) {
    recordWriter.configureSourceContext(sourceRecordPosition);
  }

  @Override
  public void appendFollowUpEvent(
      final long key, final Intent intent, final UnifiedRecordValue value) {
    eventWriter.appendFollowUpEvent(key, intent, value);
  }

  @Override
  public void appendFollowUpEvent(
      final long key,
      final Intent intent,
      final UnifiedRecordValue value,
      final Consumer<RecordMetadata> metadata) {
    eventWriter.appendFollowUpEvent(key, intent, value, metadata);
  }

  @Override
  public void appendNewEvent(final long key, final Intent intent, final UnifiedRecordValue value) {
    eventWriter.appendNewEvent(key, intent, value);
  }

  @Override
  public void appendNewCommand(final Intent intent, final UnifiedRecordValue value) {
    commandWriter.appendNewCommand(intent, value);
  }

  @Override
  public void appendFollowUpCommand(
      final long key, final Intent intent, final UnifiedRecordValue value) {
    commandWriter.appendFollowUpCommand(key, intent, value);
  }

  @Override
  public void appendFollowUpCommand(
      final long key,
      final Intent intent,
      final UnifiedRecordValue value,
      final Consumer<RecordMetadata> metadata) {
    commandWriter.appendFollowUpCommand(key, intent, value, metadata);
  }

  @Override
  public void reset() {
    commandWriter.reset();
  }

  @Override
  public long flush() {
    return commandWriter.flush();
  }
}
