/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.streaming.state;

import io.camunda.eventbridge.streaming.state.api.KeyValueStore;
import io.camunda.eventbridge.streaming.state.rocksdb.RocksDbStateStoreProvider;
import io.camunda.zeebe.db.impl.DbCompositeKey;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbString;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

final class RocksDbKeyValueStoreTest extends AbstractKeyValueStoreContractTest {

  @TempDir private Path dataDir;
  private RocksDbStateStoreProvider<TestColumnFamilies> provider;

  @BeforeEach
  void setUp() {
    provider = RocksDbStateStoreProvider.open(dataDir.toFile(), new SimpleMeterRegistry());
  }

  @AfterEach
  void tearDown() throws Exception {
    provider.close();
  }

  @Override
  protected KeyValueStore<DbLong, DbString> longStore() {
    return provider.keyValueStore(TestColumnFamilies.KV, new DbLong(), new DbString());
  }

  @Override
  protected KeyValueStore<DbCompositeKey<DbLong, DbString>, DbString> compositeStore() {
    return provider.keyValueStore(
        TestColumnFamilies.COMPOSITE,
        new DbCompositeKey<>(new DbLong(), new DbString()),
        new DbString());
  }
}
