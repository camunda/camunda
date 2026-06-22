/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.stream.impl.records;

import io.camunda.zeebe.logstreams.log.LoggedEvent;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.ValueType;
import java.util.Collections;
import java.util.Map;

public final class RecordValues {

  private final Map<ValueType, UnifiedRecordValue> eventCache;

  /** Default cache covering all engine record values ({@link ValueType}). */
  public RecordValues() {
    this(UnifiedRecordValue.allRecordsMap());
  }

  /**
   * Cache over a caller-supplied {@link ValueType} → value mapping, so platform users other than
   * the engine (e.g. the event bridge coordinator stream) can deserialize their own record types
   * without registering them in the shared protocol. Pass it via {@code
   * StreamProcessorBuilder.recordValues(Supplier)}.
   */
  public RecordValues(final Map<ValueType, UnifiedRecordValue> eventCache) {
    this.eventCache = Collections.unmodifiableMap(eventCache);
  }

  public UnifiedRecordValue readRecordValue(final LoggedEvent event, final ValueType valueType) {
    final UnifiedRecordValue value = eventCache.get(valueType);
    if (value != null) {
      value.reset();
      event.readValue(value);
    }
    return value;
  }
}
