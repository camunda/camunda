/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processing.streamprocessor;

import io.zeebe.engine.processing.streamprocessor.writers.EventApplier;
import io.zeebe.engine.processing.streamprocessor.writers.TypedStreamWriter;
import io.zeebe.engine.state.ZeebeState;
import io.zeebe.msgpack.UnpackedObject;
import io.zeebe.protocol.impl.record.RecordMetadata;
import io.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.zeebe.protocol.record.RejectionType;
import io.zeebe.protocol.record.intent.Intent;
import java.util.function.Consumer;

/** Appends records to the stream and applies state changes for follow-up events */
public final class StreamAppender implements TypedStreamWriter {

  private final TypedStreamWriter delegate;
  private final EventApplier eventApplier;

  public StreamAppender(final TypedStreamWriter delegate, final ZeebeState zeebeState) {
    this.delegate = delegate;
    eventApplier = new EventApplier(zeebeState);
  }

  @Override
  public void appendNewCommand(final Intent intent, final UnifiedRecordValue value) {
    delegate.appendNewCommand(intent, value);
  }

  @Override
  public void appendFollowUpCommand(
      final long key, final Intent intent, final UnifiedRecordValue value) {
    delegate.appendFollowUpCommand(key, intent, value);
  }

  @Override
  public void appendFollowUpCommand(
      final long key,
      final Intent intent,
      final UnifiedRecordValue value,
      final Consumer<RecordMetadata> metadata) {
    delegate.appendFollowUpCommand(key, intent, value, metadata);
  }

  @Override
  public void reset() {
    delegate.reset();
  }

  @Override
  public long flush() {
    return delegate.flush();
  }

  @Override
  public void appendFollowUpEvent(
      final long key, final Intent intent, final UnifiedRecordValue value) {
    delegate.appendFollowUpEvent(key, intent, value);
    eventApplier.applyState(key, intent, value);
  }

  @Override
  public void appendFollowUpEvent(
      final long key,
      final Intent intent,
      final UnifiedRecordValue value,
      final Consumer<RecordMetadata> metadata) {
    delegate.appendFollowUpEvent(key, intent, value, metadata);
    eventApplier.applyState(key, intent, value);
  }

  @Override
  public void appendNewEvent(final long key, final Intent intent, final UnifiedRecordValue value) {
    delegate.appendNewEvent(key, intent, value);
  }

  @Override
  public void appendRejection(
      final TypedRecord<? extends UnpackedObject> command,
      final RejectionType type,
      final String reason) {
    delegate.appendRejection(command, type, reason);
  }

  @Override
  public void appendRejection(
      final TypedRecord<? extends UnpackedObject> command,
      final RejectionType type,
      final String reason,
      final Consumer<RecordMetadata> metadata) {
    delegate.appendRejection(command, type, reason, metadata);
  }

  @Override
  public void configureSourceContext(final long sourceRecordPosition) {
    delegate.configureSourceContext(sourceRecordPosition);
  }
}
