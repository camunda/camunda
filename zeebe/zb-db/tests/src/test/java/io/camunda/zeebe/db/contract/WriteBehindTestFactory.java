/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.contract;

import io.camunda.zeebe.db.ZeebeDbFactory;
import io.camunda.zeebe.db.writebehind.WriteBehindConfiguration;
import io.camunda.zeebe.db.writebehind.WriteBehindZeebeDbFactory;
import io.camunda.zeebe.protocol.EnumValue;
import io.camunda.zeebe.protocol.ScopedColumnFamily;

/** Write-behind DB over the same RocksDB setup as {@link RocksDbTestFactory}. */
final class WriteBehindTestFactory {

  private WriteBehindTestFactory() {}

  static <ColumnFamilyType extends Enum<? extends EnumValue> & EnumValue & ScopedColumnFamily>
      ZeebeDbFactory<ColumnFamilyType> create() {
    return create(WriteBehindConfiguration.defaults());
  }

  static <ColumnFamilyType extends Enum<? extends EnumValue> & EnumValue & ScopedColumnFamily>
      ZeebeDbFactory<ColumnFamilyType> create(final WriteBehindConfiguration config) {
    return new WriteBehindZeebeDbFactory<>(
        RocksDbTestFactory.create(), config, RocksDbTestFactory.CONSISTENCY_CHECKS);
  }
}
