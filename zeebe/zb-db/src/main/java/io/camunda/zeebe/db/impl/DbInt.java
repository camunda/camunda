/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.impl;

import static io.camunda.zeebe.db.impl.ZeebeDbConstants.ZB_DB_BYTE_ORDER;

import io.camunda.zeebe.db.DbKey;
import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.db.KeyStructure;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

public final class DbInt implements DbKey, DbValue {

  private int intValue;
  private boolean nonNegative;

  public void wrapInt(final int value) {
    intValue = value;
  }

  @Override
  public void wrap(final DirectBuffer buffer, final int offset, final int length) {
    intValue = buffer.getInt(offset, ZB_DB_BYTE_ORDER);
  }

  /**
   * Marks this component as known to never hold a negative value. The default is "may be negative",
   * because signedness is a property of how a key is used, not of its class.
   */
  public DbInt nonNegative() {
    nonNegative = true;
    return this;
  }

  @Override
  public void describe(final KeyStructure structure) {
    structure.int32(null, nonNegative);
  }

  @Override
  public int getLength() {
    return Integer.BYTES;
  }

  @Override
  public int write(final MutableDirectBuffer buffer, final int offset) {
    buffer.putInt(offset, intValue, ZB_DB_BYTE_ORDER);
    return getLength();
  }

  public int getValue() {
    return intValue;
  }

  @Override
  public String toString() {
    return "DbInt{" + intValue + '}';
  }
}
