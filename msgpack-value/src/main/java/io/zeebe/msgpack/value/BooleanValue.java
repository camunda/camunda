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

public class BooleanValue extends BaseValue {
  protected boolean val = false;

  public BooleanValue() {
    this(false);
  }

  public BooleanValue(boolean initialValue) {
    this.val = initialValue;
  }

  @Override
  public void reset() {
    val = false;
  }

  public boolean getValue() {
    return val;
  }

  public void setValue(boolean value) {
    this.val = value;
  }

  @Override
  public void writeJSON(StringBuilder builder) {
    builder.append(val);
  }

  @Override
  public void write(MsgPackWriter writer) {
    writer.writeBoolean(val);
  }

  @Override
  public void read(MsgPackReader reader) {
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
  public boolean equals(Object o) {
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
