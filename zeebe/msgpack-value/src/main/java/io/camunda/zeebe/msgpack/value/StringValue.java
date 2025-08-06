/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.msgpack.value;

import static io.camunda.zeebe.util.buffer.BufferUtil.wrapString;

import io.camunda.zeebe.msgpack.spec.MsgPackReader;
import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public final class StringValue extends BaseValue {
  public static final String EMPTY_STRING = "";

  private final MutableDirectBuffer bytes = new UnsafeBuffer(0, 0);
  private int length;

  public StringValue() {
    this(EMPTY_STRING);
  }

  public StringValue(final String string) {
    this(wrapString(string));
  }

  public StringValue(final DirectBuffer buffer) {
    this(buffer, 0, buffer.capacity());
  }

  public StringValue(final DirectBuffer buffer, final int offset, final int length) {
    wrap(buffer, offset, length);
  }

  @Override
  public void reset() {
    bytes.wrap(0, 0);
    length = 0;
  }

  public void wrap(final byte[] bytes) {
    this.bytes.wrap(bytes);
    length = bytes.length;
  }

  public void wrap(final DirectBuffer buff) {
    wrap(buff, 0, buff.capacity());
  }

  public void wrap(final DirectBuffer buff, final int offset, final int length) {
    if (length == 0) {
      bytes.wrap(0, 0);
    } else {
      bytes.wrap(buff, offset, length);
    }
    this.length = length;
  }

  public void wrap(final StringValue anotherString) {
    wrap(anotherString.getValue());
  }

  public void wrap(final String anotherString) {
    wrap(anotherString.getBytes(StandardCharsets.UTF_8));
  }

  public int getLength() {
    return length;
  }

  public DirectBuffer getValue() {
    return bytes;
  }

  @Override
  public void writeJSON(final StringBuilder builder) {
    builder.append("\"");
    builder.append(toString());
    builder.append("\"");
  }

  @Override
  public void write(final MsgPackWriter writer) {
    writer.writeString(bytes);
  }

  @Override
  public void read(final MsgPackReader reader) {
    final DirectBuffer buffer = reader.getBuffer();
    final int stringLength = reader.readStringLength();
    final int offset = reader.getOffset();

    reader.skipBytes(stringLength);

    wrap(buffer, offset, stringLength);
  }

  @Override
  public int getEncodedLength() {
    return MsgPackWriter.getEncodedStringLength(length);
  }

  @Override
  public String toString() {
    return bytes.getStringWithoutLengthUtf8(0, length);
  }

  @Override
  public int hashCode() {
    return Objects.hash(bytes, getLength());
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }

    if (!(o instanceof StringValue)) {
      return false;
    }

    final StringValue that = (StringValue) o;
    return getLength() == that.getLength() && Objects.equals(bytes, that.bytes);
  }
}
