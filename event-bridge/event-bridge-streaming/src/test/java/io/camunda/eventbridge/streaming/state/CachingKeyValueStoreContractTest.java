/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.streaming.state;

import io.camunda.eventbridge.streaming.state.api.KeyValueStore;
import io.camunda.eventbridge.streaming.state.cache.CachingKeyValueStore;
import io.camunda.eventbridge.streaming.state.memory.InMemoryStateStoreProvider;
import io.camunda.zeebe.db.impl.DbCompositeKey;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbString;

/**
 * The caching store must honour the full {@link KeyValueStore} contract even before any checkpoint
 * — reads and scans see the buffered writes. A large budget keeps eviction out of the way here;
 * eviction and read-through are covered in {@code CachingKeyValueStoreTest}.
 */
final class CachingKeyValueStoreContractTest extends AbstractKeyValueStoreContractTest {

  private static final long LARGE_BUDGET = 64L * 1024 * 1024;

  private final InMemoryStateStoreProvider<TestColumnFamilies> provider =
      new InMemoryStateStoreProvider<>();

  @Override
  protected KeyValueStore<DbLong, DbString> longStore() {
    return new CachingKeyValueStore<>(
        provider.keyValueStore(TestColumnFamilies.KV, new DbLong(), new DbString()),
        new DbLong(),
        new DbString(),
        LARGE_BUDGET);
  }

  @Override
  protected KeyValueStore<DbCompositeKey<DbLong, DbString>, DbString> compositeStore() {
    return new CachingKeyValueStore<>(
        provider.keyValueStore(
            TestColumnFamilies.COMPOSITE,
            new DbCompositeKey<>(new DbLong(), new DbString()),
            new DbString()),
        new DbCompositeKey<>(new DbLong(), new DbString()),
        new DbString(),
        LARGE_BUDGET);
  }
}
