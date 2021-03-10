/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.msgpack.property;

import io.zeebe.msgpack.MsgpackPropertyException;
import io.zeebe.msgpack.value.ArrayValue;
import io.zeebe.msgpack.value.BaseValue;
import io.zeebe.msgpack.value.ValueArray;
import java.util.Iterator;

public final class ArrayProperty<T extends BaseValue> extends BaseProperty<ArrayValue<T>>
    implements ValueArray<T> {
  public ArrayProperty(final String keyString, final T innerValue) {
    super(keyString, new ArrayValue<>(innerValue));
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
}
