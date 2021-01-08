/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processing.streamprocessor.writers;

import io.zeebe.engine.processing.streamprocessor.ProcessingContext;
import io.zeebe.engine.processing.streamprocessor.TypedRecord;
import io.zeebe.logstreams.log.LogStreamBatchWriter;
import io.zeebe.msgpack.UnpackedObject;
import io.zeebe.protocol.impl.record.RecordMetadata;
import io.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.zeebe.protocol.record.RecordType;
import io.zeebe.protocol.record.RejectionType;
import io.zeebe.protocol.record.intent.Intent;
import java.util.function.Consumer;

public final class TypedStreamWriterImpl extends TypedCommandWriterImpl
    implements TypedStreamWriter {

  private final ProcessingContext processingContext;

  public TypedStreamWriterImpl(
      final LogStreamBatchWriter batchWriter, final ProcessingContext processingContext) {
    super(batchWriter);
    this.processingContext = processingContext;
  }

  @Override
  public void appendRejection(
      final TypedRecord<? extends UnpackedObject> command,
      final RejectionType rejectionType,
      final String reason) {
    appendRecord(
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
    appendRecord(
        command.getKey(),
        RecordType.COMMAND_REJECTION,
        command.getIntent(),
        rejectionType,
        reason,
        command.getValue(),
        metadata);
  }

  @Override
  public void appendNewEvent(final long key, final Intent intent, final UnifiedRecordValue value) {
    appendEventRecord(key, intent, value, noop);
  }

  @Override
  public void appendFollowUpEvent(
      final long key, final Intent intent, final UnifiedRecordValue value) {
    appendEventRecord(key, intent, value, noop);
  }

  @Override
  public void appendFollowUpEvent(
      final long key,
      final Intent intent,
      final UnifiedRecordValue value,
      final Consumer<RecordMetadata> metadata) {
    appendEventRecord(key, intent, value, metadata);
  }

  private void appendEventRecord(
      final long key,
      final Intent intent,
      final UnifiedRecordValue value,
      final Consumer<RecordMetadata> metadata) {
    appendRecord(key, RecordType.EVENT, intent, value, metadata);
    processingContext.getEventApplier().applyState(key, intent, value);
  }
}
