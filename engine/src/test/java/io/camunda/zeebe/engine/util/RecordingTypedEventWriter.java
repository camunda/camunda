/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.util;

import io.camunda.zeebe.engine.processing.streamprocessor.writers.EventsBuilder;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateBuilder;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.intent.Intent;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * An event writer which simply records follow up events in a thread-safe way. Can be passed to a
 * {@link StateBuilder} for easy unit testing of behaviors and processors.
 */
public final class RecordingTypedEventWriter implements EventsBuilder {

  private final List<RecordedEvent<?>> events = new CopyOnWriteArrayList<>();

  public List<RecordedEvent<?>> getEvents() {
    return events;
  }

  @Override
  public void appendFollowUpEvent(final long key, final Intent intent, final RecordValue value) {
    events.add(new RecordedEvent<>(key, intent, value));
  }

  @Override
  public int getMaxEventLength() {
    return Integer.MAX_VALUE;
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

    @Override
    public String toString() {
      return "RecordedEvent{" + "key=" + key + ", intent=" + intent + ", value=" + value + '}';
    }
  }
}
