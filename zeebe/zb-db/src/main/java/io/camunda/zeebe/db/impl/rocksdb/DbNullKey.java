/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.impl.rocksdb;

import io.camunda.zeebe.db.DbKey;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

/** This class is used only internally by #isEmpty to search for same column family prefix. */
public final class DbNullKey implements DbKey {

  public static final DbNullKey INSTANCE = new DbNullKey();

  public DbNullKey() {}

  @Override
  public void wrap(final DirectBuffer buffer, final int offset, final int length) {
    // do nothing
  }

  @Override
  public int getLength() {
    return 0;
  }

  @Override
  public int write(final MutableDirectBuffer buffer, final int offset) {
    // do nothing
    return 0;
  }
}
