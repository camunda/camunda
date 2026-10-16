/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.logarchive.state;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.ObjectProperty;
import io.camunda.zeebe.msgpack.value.ObjectValue;
import java.util.function.Supplier;

/**
 * Stores one msgpack object (e.g. an {@code IndexChunk}) as a database value. The wrapped object is
 * reused; callers copy it before keeping it.
 */
final class ObjectDbValue<T extends ObjectValue> extends UnpackedObject implements DbValue {

  private final ObjectProperty<T> valueProperty;

  ObjectDbValue(final Supplier<T> factory) {
    super(1);
    valueProperty = new ObjectProperty<>("value", factory.get());
    declareProperty(valueProperty);
  }

  T value() {
    return valueProperty.getValue();
  }
}
