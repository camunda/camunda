/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.db.impl.rocksdb.transaction;

import io.zeebe.db.DbKey;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

/** This class is used only internally by #isEmpty to search for same column family prefix. */
final class DbNullKey implements DbKey {

  public static final DbNullKey INSTANCE = new DbNullKey();

  DbNullKey() {}

  @Override
  public void wrap(final DirectBuffer buffer, final int offset, final int length) {
    // do nothing
  }

  @Override
  public void write(final MutableDirectBuffer buffer, final int offset) {
    // do nothing
  }

  @Override
  public int getLength() {
    return 0;
  }
}
