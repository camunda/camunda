/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor;

import io.zeebe.logstreams.log.LogStreamBatchWriter;
import io.zeebe.msgpack.UnpackedObject;
import io.zeebe.protocol.impl.record.RecordMetadata;
import io.zeebe.protocol.record.RecordType;
import io.zeebe.protocol.record.RejectionType;
import io.zeebe.protocol.record.intent.Intent;
import java.util.function.Consumer;

public class TypedStreamWriterImpl extends TypedCommandWriterImpl implements TypedStreamWriter {

  public TypedStreamWriterImpl(LogStreamBatchWriter batchWriter) {
    super(batchWriter);
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
  public void appendNewEvent(final long key, final Intent intent, final UnpackedObject value) {
    appendRecord(key, RecordType.EVENT, intent, value, noop);
  }

  @Override
  public void appendFollowUpEvent(final long key, final Intent intent, final UnpackedObject value) {
    appendRecord(key, RecordType.EVENT, intent, value, noop);
  }

  @Override
  public void appendFollowUpEvent(
      final long key,
      final Intent intent,
      final UnpackedObject value,
      final Consumer<RecordMetadata> metadata) {
    appendRecord(key, RecordType.EVENT, intent, value, metadata);
  }
}
