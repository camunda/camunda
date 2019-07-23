/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.msgpack.value;

import io.zeebe.msgpack.spec.MsgPackReader;
import io.zeebe.msgpack.spec.MsgPackWriter;
import java.util.Objects;

public class IntegerValue extends BaseValue {
  protected int value;

  public IntegerValue() {
    this(0);
  }

  public IntegerValue(int initialValue) {
    this.value = initialValue;
  }

  public int getValue() {
    return value;
  }

  public void setValue(int val) {
    this.value = val;
  }

  @Override
  public void reset() {
    value = 0;
  }

  @Override
  public void writeJSON(StringBuilder builder) {
    builder.append(value);
  }

  @Override
  public void write(MsgPackWriter writer) {
    writer.writeInteger(value);
  }

  @Override
  public void read(MsgPackReader reader) {
    final long longValue = reader.readInteger();

    if (longValue < Integer.MIN_VALUE || longValue > Integer.MAX_VALUE) {
      throw new RuntimeException(
          String.format("Value doesn't fit into an integer: %s.", longValue));
    }

    value = (int) longValue;
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
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (!(o instanceof IntegerValue)) {
      return false;
    }

    final IntegerValue that = (IntegerValue) o;
    return getValue() == that.getValue();
  }
}
