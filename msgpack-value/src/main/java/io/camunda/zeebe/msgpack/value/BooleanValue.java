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

public final class BooleanValue extends BaseValue {
  private boolean val = false;

  public BooleanValue() {
    this(false);
  }

  public BooleanValue(final boolean initialValue) {
    val = initialValue;
  }

  @Override
  public void reset() {
    val = false;
  }

  public boolean getValue() {
    return val;
  }

  public void setValue(final boolean value) {
    val = value;
  }

  @Override
  public void writeJSON(final StringBuilder builder) {
    builder.append(val);
  }

  @Override
  public void write(final MsgPackWriter writer) {
    writer.writeBoolean(val);
  }

  @Override
  public void read(final MsgPackReader reader) {
    val = reader.readBoolean();
  }

  @Override
  public int getEncodedLength() {
    return MsgPackWriter.getEncodedBooleanValueLength();
  }

  @Override
  public int hashCode() {
    return Objects.hash(val);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }

    if (!(o instanceof BooleanValue)) {
      return false;
    }

    final BooleanValue that = (BooleanValue) o;
    return val == that.val;
  }
}
