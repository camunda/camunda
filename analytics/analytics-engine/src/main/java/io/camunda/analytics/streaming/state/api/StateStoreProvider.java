/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.streaming.state.api;

import io.camunda.zeebe.db.DbKey;
import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.protocol.EnumValue;
import io.camunda.zeebe.protocol.ScopedColumnFamily;

/**
 * Opens the underlying store once and vends named {@link KeyValueStore}s. A store's identity is a
 * column-family enum constant (ZeebeDb keys column families by a stable enum id); the caller
 * defines that enum, listing every store it needs.
 *
 * @param <CF> the caller's column-family enum
 */
public interface StateStoreProvider<
        CF extends Enum<? extends EnumValue> & EnumValue & ScopedColumnFamily>
    extends AutoCloseable {

  /**
   * Returns the store for {@code columnFamily}. The flyweights are the reusable key/value instances
   * the store reads and writes through; pass fresh instances dedicated to this store. Calling this
   * twice for the same column family returns the same store (the first flyweights win).
   */
  <K extends DbKey, V extends DbValue> KeyValueStore<K, V> keyValueStore(
      CF columnFamily, K keyFlyweight, V valueFlyweight);

  /** Runs {@code operations} atomically across stores opened by this provider. */
  void runInTransaction(Runnable operations);
}
