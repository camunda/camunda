/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.msgpack.spec;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public final class MsgPackToken {
  public static final MsgPackToken NIL = new MsgPackToken();

  private MsgPackType type = MsgPackType.NIL;

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
    } else {
      valueBuffer.wrap(buffer, offset, length);
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
