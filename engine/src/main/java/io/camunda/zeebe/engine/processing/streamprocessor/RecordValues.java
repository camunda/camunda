/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.streamprocessor;

import static io.camunda.zeebe.engine.processing.streamprocessor.TypedEventRegistry.EVENT_REGISTRY;

import io.camunda.zeebe.logstreams.log.LoggedEvent;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.util.ReflectUtil;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

public final class RecordValues {

  private final Map<ValueType, UnifiedRecordValue> eventCache;

  public RecordValues() {
    final EnumMap<ValueType, UnifiedRecordValue> cache = new EnumMap<>(ValueType.class);
    EVENT_REGISTRY.forEach((t, c) -> cache.put(t, ReflectUtil.newInstance(c)));

    eventCache = Collections.unmodifiableMap(cache);
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
