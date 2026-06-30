/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.state.memory;

import io.camunda.analytics.state.api.KeyValueStore;
import io.camunda.analytics.state.api.StateStoreProvider;
import io.camunda.zeebe.db.DbKey;
import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.protocol.EnumValue;
import io.camunda.zeebe.protocol.ScopedColumnFamily;
import java.util.HashMap;
import java.util.Map;

/**
 * A heap-backed {@link StateStoreProvider} for tests: vends {@link InMemoryKeyValueStore}s and runs
 * "transactions" by simply running the operations (single-threaded, no isolation needed in tests).
 *
 * @param <CF> the caller's column-family enum
 */
public final class InMemoryStateStoreProvider<
        CF extends Enum<? extends EnumValue> & EnumValue & ScopedColumnFamily>
    implements StateStoreProvider<CF> {

  private final Map<CF, KeyValueStore<?, ?>> stores = new HashMap<>();

  @Override
  @SuppressWarnings("unchecked")
  public <K extends DbKey, V extends DbValue> KeyValueStore<K, V> keyValueStore(
      final CF columnFamily, final K keyFlyweight, final V valueFlyweight) {
    return (KeyValueStore<K, V>)
        stores.computeIfAbsent(
            columnFamily, cf -> new InMemoryKeyValueStore<>(keyFlyweight, valueFlyweight));
  }

  @Override
  public void runInTransaction(final Runnable operations) {
    operations.run();
  }

  @Override
  public void close() {
    stores.clear();
  }
}
