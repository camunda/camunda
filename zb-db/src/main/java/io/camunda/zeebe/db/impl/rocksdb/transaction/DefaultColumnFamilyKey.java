/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.db.impl.rocksdb.transaction;

import io.camunda.zeebe.db.impl.ZeebeDbConstants;
import org.agrona.MutableDirectBuffer;

public class DefaultColumnFamilyKey implements ColumnFamilyKey {

  private final long columnFamilyPrefix;

  public DefaultColumnFamilyKey(final long columnFamilyPrefix) {
    this.columnFamilyPrefix = columnFamilyPrefix;
  }

  @Override
  public int getLength() {
    return Long.BYTES;
  }

  @Override
  public void write(MutableDirectBuffer buffer, int offset) {
    buffer.putLong(offset, columnFamilyPrefix, ZeebeDbConstants.ZB_DB_BYTE_ORDER);
  }
}
