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
import java.util.Objects;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class PackedValue extends BaseValue {
  private final DirectBuffer buffer = new UnsafeBuffer(0, 0);
  private int length;

  public PackedValue() {}

  public PackedValue(final DirectBuffer defaultValue, final int offset, final int length) {
    wrap(defaultValue, offset, length);
  }

  public void wrap(final DirectBuffer buff, final int offset, final int length) {
    buffer.wrap(buff, offset, length);
    this.length = length;
  }

  public DirectBuffer getValue() {
    return buffer;
  }

  @Override
  public void reset() {
    buffer.wrap(0, 0);
    length = 0;
  }

  @Override
  public void writeJSON(final StringBuilder builder) {
    builder.append("[packed value (length=");
    builder.append(length);
    builder.append(")]");
  }

  @Override
  public int write(final MsgPackWriter writer) {
    return writer.writeRaw(buffer);
  }

  @Override
  public void read(final MsgPackReader reader) {
    final DirectBuffer buffer = reader.getBuffer();
    final int offset = reader.getOffset();
    reader.skipValue();
    final int lenght = reader.getOffset() - offset;

    wrap(buffer, offset, lenght);
  }

  @Override
  public int getEncodedLength() {
    return length;
  }

  @Override
  public int hashCode() {
    return Objects.hash(buffer, length);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof PackedValue)) {
      return false;
    }

    final PackedValue that = (PackedValue) o;
    return length == that.length && Objects.equals(buffer, that.buffer);
  }
}
