/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.impl;

import io.camunda.zeebe.db.DbValue;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

public final class DbNil implements DbValue {

  public static final DbNil INSTANCE = new DbNil();

  private static final byte EXISTENCE_BYTE = (byte) -1;

  private DbNil() {}

  @Override
  public void wrap(final DirectBuffer directBuffer, final int offset, final int length) {
    // nothing to do
  }

  @Override
  public int getLength() {
    return Byte.BYTES;
  }

  @Override
  public int write(final MutableDirectBuffer mutableDirectBuffer, final int offset) {
    mutableDirectBuffer.putByte(offset, EXISTENCE_BYTE);
    return getLength();
  }

  @Override
  public String toString() {
    return "DbNil{}";
  }
}
