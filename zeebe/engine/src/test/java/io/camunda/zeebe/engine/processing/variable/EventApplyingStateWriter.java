/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.variable;

import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedEventWriter;
import io.camunda.zeebe.engine.state.EventApplier;
import io.camunda.zeebe.engine.state.TriggeringRecordMetadata;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.intent.Intent;

/**
 * A state writer that uses the event applier, to alter the state for each written event.
 *
 * <p>Note that it does not write events to the stream itself, but it delegates this to the {@link
 * TypedEventWriter}.
 *
 * <p>Note that it does not change the state itself, but delegates this to the {@link EventApplier}.
 */
public final class EventApplyingStateWriter implements StateWriter {

  private final TypedEventWriter eventWriter;
  private final EventApplier eventApplier;

  public EventApplyingStateWriter(
      final TypedEventWriter eventWriter, final EventApplier eventApplier) {
    this.eventWriter = eventWriter;
    this.eventApplier = eventApplier;
  }

  @Override
  public void appendFollowUpEvent(final long key, final Intent intent, final RecordValue value) {
    appendFollowUpEvent(key, intent, value, TriggeringRecordMetadata.empty());
  }

  @Override
  public void appendFollowUpEvent(
      final long key,
      final Intent intent,
      final RecordValue value,
      final TriggeringRecordMetadata metadata) {
    eventWriter.appendFollowUpEvent(key, intent, value, metadata);
    eventApplier.applyState(key, intent, value, RecordMetadata.DEFAULT_RECORD_VERSION, metadata);
  }

  @Override
  public boolean canWriteEventOfLength(final int eventLength) {
    return eventWriter.canWriteEventOfLength(eventLength);
  }
}
