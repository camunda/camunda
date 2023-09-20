/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.db.impl.rocksdb.transaction;

import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.db.impl.ZeebeDbConstants;
import java.util.Objects;
import org.agrona.MutableDirectBuffer;

public class TenantAwareColumnFamilyKey implements ColumnFamilyKey {

  private final long columnFamilyPrefix;
  private final DbString tenantKey;

  public TenantAwareColumnFamilyKey(final long columnFamilyPrefix, final DbString tenantKey) {
    this.columnFamilyPrefix = columnFamilyPrefix + 100_000L;
    this.tenantKey = Objects.requireNonNull(tenantKey);
  }

  @Override
  public int getLength() {
    return Long.BYTES + tenantKey.getLength();
  }

  @Override
  public void write(MutableDirectBuffer buffer, int offset) {
    buffer.putLong(0, columnFamilyPrefix, ZeebeDbConstants.ZB_DB_BYTE_ORDER);
    tenantKey.write(buffer, Long.BYTES);
  }
}
