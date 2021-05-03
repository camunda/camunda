/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.msgpack.value;

import io.zeebe.msgpack.spec.MsgPackReader;
import io.zeebe.msgpack.spec.MsgPackWriter;
import java.util.Objects;

public final class LongValue extends BaseValue {
  private long value;

  public LongValue() {
    this(0L);
  }

  public LongValue(final long initialValue) {
    value = initialValue;
  }

  public long getValue() {
    return value;
  }

  public void setValue(final long val) {
    value = val;
  }

  @Override
  public void reset() {
    value = 0;
  }

  @Override
  public void writeJSON(final StringBuilder builder) {
    builder.append(value);
  }

  @Override
  public void write(final MsgPackWriter writer) {
    writer.writeInteger(value);
  }

  @Override
  public void read(final MsgPackReader reader) {
    value = reader.readInteger();
  }

  @Override
  public int getEncodedLength() {
    return MsgPackWriter.getEncodedLongValueLength(value);
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

    if (!(o instanceof LongValue)) {
      return false;
    }

    final LongValue longValue = (LongValue) o;
    return getValue() == longValue.getValue();
  }
}
