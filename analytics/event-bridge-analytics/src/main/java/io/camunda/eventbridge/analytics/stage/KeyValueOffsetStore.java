/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.analytics.stage;

import io.camunda.eventbridge.streaming.OffsetStore;
import io.camunda.eventbridge.streaming.state.api.KeyValueStore;
import io.camunda.zeebe.db.impl.DbInt;
import io.camunda.zeebe.db.impl.DbLong;
import java.util.HashMap;
import java.util.Map;

/**
 * Runtime {@link OffsetStore} backed by a RocksDB {@code (partition -> position)} key-value store.
 * Writes go straight into the runtime's checkpoint transaction, so no separate flush is needed.
 */
final class KeyValueOffsetStore implements OffsetStore {

  private final KeyValueStore<DbInt, DbLong> store;
  private final DbInt key = new DbInt();
  private final DbLong value = new DbLong();

  KeyValueOffsetStore(final KeyValueStore<DbInt, DbLong> store) {
    this.store = store;
  }

  @Override
  public Map<Integer, Long> restore() {
    final Map<Integer, Long> restored = new HashMap<>();
    store.forEach((partition, position) -> restored.put(partition.getValue(), position.getValue()));
    return restored;
  }

  @Override
  public void store(final int partition, final long offset) {
    key.wrapInt(partition);
    value.wrapLong(offset);
    store.put(key, value);
  }
}
