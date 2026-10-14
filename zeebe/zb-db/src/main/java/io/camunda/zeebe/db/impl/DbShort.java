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

public final class DbShort implements DbKey, DbValue {

  private short shortValue;
  private boolean nonNegative;

  public void wrapShort(final short value) {
    shortValue = value;
  }

  @Override
  public void wrap(final DirectBuffer buffer, final int offset, final int length) {
    shortValue = buffer.getShort(offset, ZB_DB_BYTE_ORDER);
  }

  /**
   * Marks this component as known to never hold a negative value. The default is "may be negative",
   * because signedness is a property of how a key is used, not of its class.
   */
  public DbShort nonNegative() {
    nonNegative = true;
    return this;
  }

  @Override
  public void describe(final KeyStructure structure) {
    structure.int16(null, nonNegative);
  }

  @Override
  public int getLength() {
    return Short.BYTES;
  }

  @Override
  public int write(final MutableDirectBuffer buffer, final int offset) {
    buffer.putShort(offset, shortValue, ZB_DB_BYTE_ORDER);
    return getLength();
  }

  public short getValue() {
    return shortValue;
  }

  @Override
  public String toString() {
    return "DbShort{" + shortValue + '}';
  }
}
