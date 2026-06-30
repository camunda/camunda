/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.streaming.state;

import io.camunda.analytics.streaming.state.api.KeyValueStore;
import io.camunda.analytics.streaming.state.memory.InMemoryStateStoreProvider;
import io.camunda.zeebe.db.impl.DbCompositeKey;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbString;

final class InMemoryKeyValueStoreTest extends AbstractKeyValueStoreContractTest {

  private final InMemoryStateStoreProvider<TestColumnFamilies> provider =
      new InMemoryStateStoreProvider<>();

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
