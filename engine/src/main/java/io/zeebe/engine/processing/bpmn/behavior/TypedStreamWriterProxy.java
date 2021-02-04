/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processing.bpmn.behavior;

import io.zeebe.engine.processing.streamprocessor.TypedRecord;
import io.zeebe.engine.processing.streamprocessor.writers.TypedStreamWriter;
import io.zeebe.msgpack.UnpackedObject;
import io.zeebe.protocol.impl.record.RecordMetadata;
import io.zeebe.protocol.record.RejectionType;
import io.zeebe.protocol.record.intent.Intent;
import java.util.function.UnaryOperator;

public final class TypedStreamWriterProxy implements TypedStreamWriter {

  private TypedStreamWriter writer;

  public void wrap(final TypedStreamWriter writer) {
    this.writer = writer;
  }

  @Override
  public void appendRejection(
      final TypedRecord<? extends UnpackedObject> command,
      final RejectionType type,
      final String reason) {
    writer.appendRejection(command, type, reason);
  }

  @Override
  public void appendRejection(
      final TypedRecord<? extends UnpackedObject> command,
      final RejectionType type,
      final String reason,
      final UnaryOperator<RecordMetadata> modifier) {
    writer.appendRejection(command, type, reason, modifier);
  }

  @Override
  public void configureSourceContext(final long sourceRecordPosition) {
    writer.configureSourceContext(sourceRecordPosition);
  }

  @Override
  public void appendNewEvent(final long key, final Intent intent, final UnpackedObject value) {
    writer.appendNewEvent(key, intent, value);
  }

  @Override
  public void appendFollowUpEvent(final long key, final Intent intent, final UnpackedObject value) {
    writer.appendFollowUpEvent(key, intent, value);
  }

  @Override
  public void appendFollowUpEvent(
      final long key,
      final Intent intent,
      final UnpackedObject value,
      final UnaryOperator<RecordMetadata> modifier) {
    writer.appendFollowUpEvent(key, intent, value, modifier);
  }

  @Override
  public void appendNewCommand(final Intent intent, final UnpackedObject value) {
    writer.appendNewCommand(intent, value);
  }

  @Override
  public void appendFollowUpCommand(
      final long key, final Intent intent, final UnpackedObject value) {
    writer.appendFollowUpCommand(key, intent, value);
  }

  @Override
  public void appendFollowUpCommand(
      final long key,
      final Intent intent,
      final UnpackedObject value,
      final UnaryOperator<RecordMetadata> modifier) {
    writer.appendFollowUpCommand(key, intent, value, modifier);
  }

  @Override
  public void reset() {
    writer.reset();
  }

  @Override
  public long flush() {
    return writer.flush();
  }
}
