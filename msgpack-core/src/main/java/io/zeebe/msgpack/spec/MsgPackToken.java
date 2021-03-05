/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.msgpack.spec;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public final class MsgPackToken {
  public static final MsgPackToken NIL = new MsgPackToken();

  private MsgPackType type = MsgPackType.NIL;
  private int totalLength;

  // string
  private final UnsafeBuffer valueBuffer = new UnsafeBuffer(0, 0);

  // boolean
  private boolean booleanValue;

  // map/array
  private int size;

  // int
  private long integerValue;

  // float32/float64
  private double floatValue;

  public int getTotalLength() {
    return totalLength;
  }

  public void setTotalLength(final int totalLength) {
    this.totalLength = totalLength;
  }

  public int getSize() {
    return size;
  }

  public MsgPackType getType() {
    return type;
  }

  public void setType(final MsgPackType type) {
    this.type = type;
  }

  public DirectBuffer getValueBuffer() {
    return valueBuffer;
  }

  public void setValue(final DirectBuffer buffer, final int offset, final int length) {
    if (length == 0) {
      valueBuffer.wrap(0, 0);
    } else if (offset + length <= buffer.capacity()) {
      valueBuffer.wrap(buffer, offset, length);
    } else {
      final int result = offset + length;
      throw new MsgpackReaderException(
          String.format(
              "Reading %d bytes past buffer capacity(%d) in range [%d:%d]",
              result - buffer.capacity(), buffer.capacity(), offset, result));
    }
  }

  public void setValue(final double value) {
    floatValue = value;
  }

  public void setValue(final long value) {
    integerValue = value;
  }

  public void setValue(final boolean value) {
    booleanValue = value;
  }

  public void setMapHeader(final int size) {
    this.size = size;
  }

  public void setArrayHeader(final int size) {
    this.size = size;
  }

  public boolean getBooleanValue() {
    return booleanValue;
  }

  /**
   * when using this method, keep the value's format in mind; values of negative fixnum (signed) and
   * unsigned integer can return the same long value while representing different numbers
   */
  public long getIntegerValue() {
    return integerValue;
  }

  public double getFloatValue() {
    return floatValue;
  }
}
