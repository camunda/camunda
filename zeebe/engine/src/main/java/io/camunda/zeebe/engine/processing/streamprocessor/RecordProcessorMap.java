/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.streamprocessor;

import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.util.collection.MArray;

@SuppressWarnings({"rawtypes"})
public final class RecordProcessorMap {
  private final MArray<TypedRecordProcessor> elements;

  public RecordProcessorMap() {
    elements =
        MArray.of(
            TypedRecordProcessor[]::new,
            RecordType.values().length,
            ValueType.values().length,
            Intent.maxCardinality());
  }

  public TypedRecordProcessor get(final RecordType key1, final ValueType key2, final int key3) {
    return elements.get(key1.ordinal(), key2.ordinal(), key3);
  }

  public void put(
      final RecordType key1,
      final ValueType key2,
      final int key3,
      final TypedRecordProcessor value) {
    final TypedRecordProcessor oldElement = get(key1, key2, key3);
    if (oldElement != null) {
      final String exceptionMsg =
          String.format(
              "Expected to have a single processor per intent,"
                  + " got for intent %s duplicate processor %s have already %s",
              Intent.fromProtocolValue(key2, (short) key3),
              value.getClass().getName(),
              oldElement.getClass().getName());
      throw new IllegalStateException(exceptionMsg);
    }

    elements.put(value, key1.ordinal(), key2.ordinal(), key3);
  }
}
