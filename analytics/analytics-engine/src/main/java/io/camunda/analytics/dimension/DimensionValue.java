/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.dimension;

import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.IntegerProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.util.buffer.BufferUtil;

/**
 * One msgpack array element of {@link DimensionKeyValue}: a single dimension value tagged with a
 * self-describing wire kind so a key round-trips without external type information. A {@code null}
 * dimension value (the "unknown" bucket) is encoded with {@link #KIND_NULL}. Numeric kinds share
 * the {@code n} slot; strings use the {@code s} slot.
 */
final class DimensionValue extends UnpackedObject {

  static final int KIND_NULL = 0;
  static final int KIND_STRING = 1;
  static final int KIND_LONG = 2;
  static final int KIND_INT = 3;
  static final int KIND_BOOLEAN = 4;

  private final IntegerProperty kindProp = new IntegerProperty("k", KIND_NULL);
  private final StringProperty strProp = new StringProperty("s", "");
  private final LongProperty numProp = new LongProperty("n", 0L);

  DimensionValue() {
    super(3);
    declareProperty(kindProp).declareProperty(strProp).declareProperty(numProp);
  }

  DimensionValue set(final Object value) {
    strProp.setValue("");
    numProp.setValue(0L);
    switch (value) {
      case null -> kindProp.setValue(KIND_NULL);
      case final String s -> {
        kindProp.setValue(KIND_STRING);
        strProp.setValue(s);
      }
      case final Long l -> {
        kindProp.setValue(KIND_LONG);
        numProp.setValue(l);
      }
      case final Integer i -> {
        kindProp.setValue(KIND_INT);
        numProp.setValue(i.longValue());
      }
      case final Boolean b -> {
        kindProp.setValue(KIND_BOOLEAN);
        numProp.setValue(b ? 1L : 0L);
      }
      default ->
          throw new IllegalArgumentException(
              "unsupported dimension value type: " + value.getClass().getName());
    }
    return this;
  }

  Object value() {
    return switch (kindProp.getValue()) {
      case KIND_NULL -> null;
      case KIND_STRING -> BufferUtil.bufferAsString(strProp.getValue());
      case KIND_LONG -> numProp.getValue();
      case KIND_INT -> (int) numProp.getValue();
      case KIND_BOOLEAN -> numProp.getValue() != 0L;
      default ->
          throw new IllegalStateException("unknown dimension value kind: " + kindProp.getValue());
    };
  }
}
