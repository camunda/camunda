/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.util;

import io.zeebe.engine.processing.streamprocessor.writers.TypedEventWriter;
import io.zeebe.protocol.impl.record.RecordMetadata;
import io.zeebe.protocol.record.RecordValue;
import io.zeebe.protocol.record.intent.Intent;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.UnaryOperator;

/**
 * An event writer which simply records follow up events in a thread-safe way. Can be passed to a
 * {@link io.zeebe.engine.processing.streamprocessor.writers.StateWriter} for easy unit testing of
 * behaviors and processors.
 */
public final class RecordingTypedEventWriter implements TypedEventWriter {

  private final List<RecordedEvent<?>> events = new CopyOnWriteArrayList<>();

  public List<RecordedEvent<?>> getEvents() {
    return events;
  }

  @Override
  public void appendFollowUpEvent(final long key, final Intent intent, final RecordValue value) {
    events.add(new RecordedEvent<>(key, intent, value));
  }

  @Override
  public void appendFollowUpEvent(
      final long key,
      final Intent intent,
      final RecordValue value,
      final UnaryOperator<RecordMetadata> modifier) {
    appendFollowUpEvent(key, intent, value);
  }

  public static final class RecordedEvent<T extends RecordValue> {

    public final long key;
    public final Intent intent;
    public final T value;

    public RecordedEvent(final long key, final Intent intent, final T value) {
      this.key = key;
      this.intent = intent;
      this.value = (T) Records.cloneValue(value);
    }
  }
}
