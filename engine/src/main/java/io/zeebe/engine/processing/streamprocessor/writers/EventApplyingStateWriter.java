/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.processing.streamprocessor.writers;

import io.zeebe.engine.state.EventApplier;
import io.zeebe.protocol.record.RecordValue;
import io.zeebe.protocol.record.intent.Intent;

/**
 * A state writer that uses the event applier, to alter the state for each written event.
 *
 * <p>Note that it does not write events to the stream itself, but it delegates this to the {@link
 * TypedStreamWriter}.
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
    eventWriter.appendFollowUpEvent(key, intent, value);
    eventApplier.applyState(key, intent, value);
  }
}
