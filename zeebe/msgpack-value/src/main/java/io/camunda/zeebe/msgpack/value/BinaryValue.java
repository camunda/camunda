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
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class BinaryValue extends BaseValue {
  private final MutableDirectBuffer data = new UnsafeBuffer(0, 0);
  private int length = 0;

  public BinaryValue() {}

  public BinaryValue(final DirectBuffer initialValue, final int offset, final int length) {
    wrap(initialValue, offset, length);
  }

  @Override
  public void reset() {
    data.wrap(0, 0);
    length = 0;
  }

  public void wrap(final DirectBuffer buff) {
    wrap(buff, 0, buff.capacity());
  }

  public void wrap(final DirectBuffer buff, final int offset, final int length) {
    if (length == 0) {
      data.wrap(0, 0);
    } else {
      data.wrap(buff, offset, length);
    }
    this.length = length;
  }

  public void wrap(final StringValue decodedKey) {
    wrap(decodedKey.getValue());
  }

  public DirectBuffer getValue() {
    return data;
  }

  @Override
  public void writeJSON(final StringBuilder builder) {
    final byte[] bytes = new byte[length];
    data.getBytes(0, bytes);

    builder.append("\"");
    builder.append(new String(Base64.getEncoder().encode(bytes), StandardCharsets.UTF_8));
    builder.append("\"");
  }

  @Override
  public int write(final MsgPackWriter writer) {
    return writer.writeBinary(data);
  }

  @Override
  public void read(final MsgPackReader reader) {
    final DirectBuffer buffer = reader.getBuffer();
    final int stringLength = reader.readBinaryLength();
    final int offset = reader.getOffset();

    reader.skipBytes(stringLength);

    wrap(buffer, offset, stringLength);
  }

  @Override
  public int getEncodedLength() {
    return MsgPackWriter.getEncodedBinaryValueLength(length);
  }

  @Override
  public int hashCode() {
    return Objects.hash(data, length);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }

    if (!(o instanceof BinaryValue)) {
      return false;
    }

    final BinaryValue that = (BinaryValue) o;
    return length == that.length && Objects.equals(data, that.data);
  }
}
