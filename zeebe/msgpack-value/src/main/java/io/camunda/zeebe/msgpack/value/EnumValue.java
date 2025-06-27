/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.msgpack.value;

import io.camunda.zeebe.msgpack.spec.MsgPackReader;
import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
import io.camunda.zeebe.msgpack.util.CachedBytesEnum;
import java.util.Objects;

public final class EnumValue<E extends Enum<E>> extends BaseValue {
  private final StringValue decodedValue = new StringValue();
  private E value;
  private final CachedBytesEnum<E> cachedValue;

  public EnumValue(final Class<E> e, final E defaultValue) {
    cachedValue = CachedBytesEnum.get(e);
    if (defaultValue != null) {
      setValue(defaultValue);
    }
  }

  public EnumValue(final Class<E> e) {
    this(e, null);
  }

  public E getValue() {
    return value;
  }

  public void setValue(final E val) {
    decodedValue.wrap(cachedValue.byteRepr(val));
    value = val;
  }

  @Override
  public void reset() {
    decodedValue.reset();
    value = null;
  }

  @Override
  public void writeJSON(final StringBuilder builder) {
    decodedValue.writeJSON(builder);
  }

  @Override
  public void write(final MsgPackWriter writer) {
    decodedValue.write(writer);
  }

  @Override
  public void read(final MsgPackReader reader) {
    decodedValue.read(reader);
    value = cachedValue.getValue(decodedValue.getValue());
  }

  @Override
  public int getEncodedLength() {
    return decodedValue.getEncodedLength();
  }

  @Override
  public int hashCode() {
    return Objects.hash(getValue());
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }

    if (o instanceof final EnumValue<?> enumValue) {
      return Objects.equals(getValue(), enumValue.getValue());
    }

    return false;
  }
}
