/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.msgpack.property;

import io.camunda.zeebe.msgpack.MsgpackPropertyException;
import io.camunda.zeebe.msgpack.value.ArrayValue;
import io.camunda.zeebe.msgpack.value.BaseValue;
import io.camunda.zeebe.msgpack.value.MutableArrayValueIterator;
import io.camunda.zeebe.msgpack.value.ValueArray;
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

  /**
   * Please be aware that doing modifications whiles iterating over an {@link ArrayValue} is not
   * thread-safe. Modification will modify the underlying buffer and will lead to exceptions when
   * done multiple threads are accessing this buffer simultaneously.
   *
   * <p>When modifying during iteration make sure to {@link MutableArrayValueIterator#flush} when
   * done.
   *
   * @return an iterator for this object
   */
  @Override
  public MutableArrayValueIterator<T> iterator() {
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
  public Stream<T> stream() {
    // ArrayValue is not a thread-safe Iterable
    final var parallel = false;
    return StreamSupport.stream(spliterator(), parallel);
  }

  public boolean isEmpty() {
    return value.isEmpty();
  }
}
