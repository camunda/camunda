/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.db.impl;

import io.zeebe.db.DbKey;
import io.zeebe.db.DbValue;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

public final class DbByte implements DbKey, DbValue {

  private byte value;

  public void wrapByte(final byte value) {
    this.value = value;
  }

  @Override
  public void wrap(final DirectBuffer directBuffer, final int offset, final int length) {
    value = directBuffer.getByte(offset);
  }

  @Override
  public int getLength() {
    return Byte.BYTES;
  }

  @Override
  public void write(final MutableDirectBuffer mutableDirectBuffer, final int offset) {
    mutableDirectBuffer.putByte(offset, value);
  }

  public byte getValue() {
    return value;
  }
}
