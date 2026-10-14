/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.impl;

import io.camunda.zeebe.db.DbKey;
import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.db.KeyStructure;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

public final class DbByte implements DbKey, DbValue {

  private byte value;
  private boolean nonNegative;

  public void wrapByte(final byte value) {
    this.value = value;
  }

  @Override
  public void wrap(final DirectBuffer directBuffer, final int offset, final int length) {
    value = directBuffer.getByte(offset);
  }

  /**
   * Marks this component as known to never hold a negative value. The default is "may be negative",
   * because signedness is a property of how a key is used, not of its class.
   */
  public DbByte nonNegative() {
    nonNegative = true;
    return this;
  }

  @Override
  public void describe(final KeyStructure structure) {
    structure.int8(null, nonNegative);
  }

  @Override
  public int getLength() {
    return Byte.BYTES;
  }

  @Override
  public int write(final MutableDirectBuffer mutableDirectBuffer, final int offset) {
    mutableDirectBuffer.putByte(offset, value);
    return getLength();
  }

  public byte getValue() {
    return value;
  }

  @Override
  public String toString() {
    return "DbByte{" + "value=" + value + '}';
  }
}
