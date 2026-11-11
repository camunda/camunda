/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.contract;

import io.camunda.zeebe.db.ZeebeDbFactory;
import io.camunda.zeebe.db.writebehind.KeyIndexPolicy;
import io.camunda.zeebe.db.writebehind.WriteBehindConfiguration;
import io.camunda.zeebe.db.writebehind.WriteBehindZeebeDbFactory;
import io.camunda.zeebe.protocol.EnumValue;
import io.camunda.zeebe.protocol.ScopedColumnFamily;
import java.util.Map;
import java.util.Set;

/** Write-behind DB over the same RocksDB setup as {@link RocksDbTestFactory}. */
final class WriteBehindTestFactory {

  private WriteBehindTestFactory() {}

  static <ColumnFamilyType extends Enum<? extends EnumValue> & EnumValue & ScopedColumnFamily>
      ZeebeDbFactory<ColumnFamilyType> create() {
    return create(WriteBehindConfiguration.defaults());
  }

  /** Indexes the keys of the column families with IDs 0 to 3, which the contract tests use. */
  static <ColumnFamilyType extends Enum<? extends EnumValue> & EnumValue & ScopedColumnFamily>
      ZeebeDbFactory<ColumnFamilyType> createWithKeyIndex() {
    return create(
        WriteBehindConfiguration.defaults().withKeyIndexColumnFamilies(Set.of(0L, 1L, 2L, 3L)));
  }

  /**
   * Like {@link #createWithKeyIndex()}, but the index of every column family keeps a window of at
   * most 4 keys: the lowest ones for the IDs 0 and 1, the highest ones for the IDs 2 and 3.
   */
  static <ColumnFamilyType extends Enum<? extends EnumValue> & EnumValue & ScopedColumnFamily>
      ZeebeDbFactory<ColumnFamilyType> createWithKeyIndexWindow() {
    return create(
        WriteBehindConfiguration.defaults()
            .withKeyIndexColumnFamilies(Set.of(0L, 1L, 2L, 3L))
            .withKeyIndexMaxKeys(4)
            .withKeyIndexPolicies(
                Map.of(
                    0L, KeyIndexPolicy.KEEP_LOWEST,
                    1L, KeyIndexPolicy.KEEP_LOWEST,
                    2L, KeyIndexPolicy.KEEP_HIGHEST,
                    3L, KeyIndexPolicy.KEEP_HIGHEST)));
  }

  static <ColumnFamilyType extends Enum<? extends EnumValue> & EnumValue & ScopedColumnFamily>
      ZeebeDbFactory<ColumnFamilyType> create(final WriteBehindConfiguration config) {
    return new WriteBehindZeebeDbFactory<>(
        RocksDbTestFactory.create(), config, RocksDbTestFactory.CONSISTENCY_CHECKS);
  }
}
