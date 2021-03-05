/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.processing.streamprocessor;

import io.zeebe.protocol.record.RecordType;
import io.zeebe.protocol.record.ValueType;
import io.zeebe.protocol.record.intent.Intent;
import java.util.Iterator;

@SuppressWarnings({"rawtypes"})
public final class RecordProcessorMap {
  private final TypedRecordProcessor[] elements;

  private final int valueTypeCardinality;
  private final int intentCardinality;

  private final ValueIterator valueIt = new ValueIterator();

  public <R extends Enum<R>, S extends Enum<S>> RecordProcessorMap() {
    final int recordTypeCardinality = RecordType.class.getEnumConstants().length;
    valueTypeCardinality = ValueType.class.getEnumConstants().length;
    intentCardinality = Intent.maxCardinality();

    final int cardinality = recordTypeCardinality * valueTypeCardinality * intentCardinality;
    elements = new TypedRecordProcessor[cardinality];
  }

  public TypedRecordProcessor get(final RecordType key1, final ValueType key2, final int key3) {
    final int index = mapToIndex(key1, key2, key3);

    if (index >= 0) {
      return elements[index];
    } else {
      return null;
    }
  }

  public void put(
      final RecordType key1,
      final ValueType key2,
      final int key3,
      final TypedRecordProcessor value) {
    final int index = mapToIndex(key1, key2, key3);

    if (index < 0) {
      throw new RuntimeException("Invalid intent value " + key3);
    }

    final TypedRecordProcessor oldElement = elements[index];
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

    elements[index] = value;
  }

  private int mapToIndex(final RecordType key1, final ValueType key2, final int key3) {
    if (key3 >= intentCardinality) {
      return -1;
    }

    return (key1.ordinal() * valueTypeCardinality * intentCardinality)
        + (key2.ordinal() * intentCardinality)
        + key3;
  }

  /** BEWARE: does not detect concurrent modifications and behaves incorrectly in this case */
  public Iterator<TypedRecordProcessor> values() {
    valueIt.init();
    return valueIt;
  }

  private class ValueIterator implements Iterator<TypedRecordProcessor> {
    private int next;

    private void scanToNext() {
      do {
        next++;
      } while (next < elements.length && elements[next] == null);
    }

    public void init() {
      next = -1;
      scanToNext();
    }

    @Override
    public boolean hasNext() {
      return next < elements.length;
    }

    @Override
    public TypedRecordProcessor next() {
      final TypedRecordProcessor element = elements[next];
      scanToNext();
      return element;
    }
  }
}
