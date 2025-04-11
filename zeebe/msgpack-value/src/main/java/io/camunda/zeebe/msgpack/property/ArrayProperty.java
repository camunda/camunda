/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.msgpack.property;

import io.camunda.zeebe.msgpack.MsgpackPropertyException;
import io.camunda.zeebe.msgpack.value.ArrayValue;
import io.camunda.zeebe.msgpack.value.BaseValue;
import io.camunda.zeebe.msgpack.value.ValueArray;
import java.util.Iterator;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public final class ArrayProperty<T extends BaseValue> extends BaseProperty<ArrayValue<T>>
    implements ValueArray<T> {
  public ArrayProperty(final String keyString, final Supplier<T> innerValueFactory) {
    super(keyString, new ArrayValue<>(innerValueFactory));
    isSet = true;
  }

  @Override
  public void reset() {
    super.reset();
    isSet = true;
  }

  @Override
  public Iterator<T> iterator() {
    return resolveValue().iterator();
  }

  @Override
  public T add() {
    try {
      return value.add();
    } catch (final Exception e) {
      throw new MsgpackPropertyException(getKey(), e);
    }
  }

  @Override
  public T add(final int index) {
    try {
      return value.add(index);
    } catch (final Exception e) {
      throw new MsgpackPropertyException(getKey(), e);
    }
  }

  @Override
  public void remove(final Set<T> items) {
    try {
      value.remove(items);
    } catch (final Exception e) {
      throw new MsgpackPropertyException(getKey(), e);
    }
  }

  @Override
  public Stream<T> stream() {
    // ArrayValue is not a thread-safe Iterable
    final var parallel = false;
    return StreamSupport.stream(spliterator(), parallel);
  }

  public boolean isEmpty() {
    return value.isEmpty();
  }
}
