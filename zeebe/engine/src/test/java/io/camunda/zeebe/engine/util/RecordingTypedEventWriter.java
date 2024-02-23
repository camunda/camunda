/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.util;

import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedEventWriter;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.intent.Intent;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * An event writer which simply records follow up events in a thread-safe way. Can be passed to a
 * {@link io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter} for easy unit
 * testing of behaviors and processors.
 */
public final class RecordingTypedEventWriter implements TypedEventWriter {

  private final List<RecordedEvent<?>> events = new CopyOnWriteArrayList<>();

  public List<RecordedEvent<?>> getEvents() {
    return events;
  }

  @Override
  public void appendFollowUpEvent(final long key, final Intent intent, final RecordValue value) {
    appendFollowUpEvent(key, intent, value, RecordMetadata.DEFAULT_RECORD_VERSION);
  }

  @Override
  public void appendFollowUpEvent(
      final long key, final Intent intent, final RecordValue value, final int recordVersion) {
    events.add(new RecordedEvent<>(key, intent, value, recordVersion));
  }

  @Override
  public boolean canWriteEventOfLength(final int eventLength) {
    return true;
  }

  public static final class RecordedEvent<T extends RecordValue> {

    public final long key;
    public final Intent intent;
    public final T value;
    private final int recordVersion;

    public RecordedEvent(
        final long key, final Intent intent, final T value, final int recordVersion) {
      this.key = key;
      this.intent = intent;
      this.value = (T) Records.cloneValue(value);
      this.recordVersion = recordVersion;
    }

    @Override
    public String toString() {
      return "RecordedEvent{"
          + "key="
          + key
          + ", intent="
          + intent
          + ", value="
          + value
          + ", recordVersion="
          + recordVersion
          + '}';
    }
  }
}
