/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.instance;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.ArrayProperty;
import io.camunda.zeebe.msgpack.value.LongValue;
import java.util.List;
import java.util.stream.StreamSupport;

public final class PersistedEventTriggerKeys extends UnpackedObject implements DbValue {

  private final ArrayProperty<LongValue> eventTriggerKeysProperty =
      new ArrayProperty<>("eventTriggerKeys", LongValue::new);

  public PersistedEventTriggerKeys() {
    super(1);
    declareProperty(eventTriggerKeysProperty);
  }

  public PersistedEventTriggerKeys addEventTriggerKey(final long eventTriggerKey) {
    if (!contains(eventTriggerKey)) {
      eventTriggerKeysProperty.add().setValue(eventTriggerKey);
    }
    return this;
  }

  public boolean contains(final long eventTriggerKey) {
    return StreamSupport.stream(eventTriggerKeysProperty.spliterator(), false)
        .anyMatch(key -> key.getValue() == eventTriggerKey);
  }

  public List<Long> getEventTriggerKeys() {
    return StreamSupport.stream(eventTriggerKeysProperty.spliterator(), false)
        .map(LongValue::getValue)
        .toList();
  }

  public boolean removeEventTriggerKey(final long eventTriggerKey) {
    final var iterator = eventTriggerKeysProperty.iterator();
    while (iterator.hasNext()) {
      if (iterator.next().getValue() == eventTriggerKey) {
        iterator.remove();
        return true;
      }
    }

    return false;
  }

  public boolean isEmpty() {
    return !eventTriggerKeysProperty.iterator().hasNext();
  }
}
