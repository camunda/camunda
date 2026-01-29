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
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

public final class DbLong implements DbKey, DbValue {

  private long longValue;

  public void wrapLong(final long value) {
    longValue = value;
  }

  @Override
  public void wrap(final DirectBuffer buffer, final int offset, final int length) {
    longValue = buffer.getLong(offset, ZB_DB_BYTE_ORDER);
  }

  @Override
  public int getLength() {
    return Long.BYTES;
  }

  @Override
  public int write(final MutableDirectBuffer buffer, final int offset) {
    buffer.putLong(offset, longValue, ZB_DB_BYTE_ORDER);
    return getLength();
  }

  public long getValue() {
    return longValue;
  }

  @Override
  public String toString() {
    return "DbLong{" + longValue + '}';
  }
}
