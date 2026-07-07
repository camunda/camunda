/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.pipeline.stage;

import io.camunda.eventbridge.streaming.state.api.KeyValueStore;
import io.camunda.zeebe.db.DbKey;
import io.camunda.zeebe.db.impl.DbBytes;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * A {@link KeyValueStore} wrapper that counts prefix scans per 4-byte {@code aggId} group prefix —
 * the observable of an aggregation's {@code recover()} — so a reload test can prove that surviving
 * aggregations are <em>not</em> re-recovered while added (or re-added) ones are.
 */
final class CountingKeyValueStore implements KeyValueStore<DbBytes, DbBytes> {

  private final KeyValueStore<DbBytes, DbBytes> delegate;
  private final Map<Integer, Integer> scansByGroup = new HashMap<>();

  CountingKeyValueStore(final KeyValueStore<DbBytes, DbBytes> delegate) {
    this.delegate = delegate;
  }

  /** How many times the given {@code aggId}'s prefix was scanned (its recover count). */
  int scans(final int group) {
    return scansByGroup.getOrDefault(group, 0);
  }

  @Override
  public Optional<DbBytes> get(final DbBytes key) {
    return delegate.get(key);
  }

  @Override
  public boolean exists(final DbBytes key) {
    return delegate.exists(key);
  }

  @Override
  public void prefixScan(final DbKey prefix, final BiConsumer<DbBytes, DbBytes> visitor) {
    scansByGroup.merge(groupOf(prefix), 1, Integer::sum);
    delegate.prefixScan(prefix, visitor);
  }

  @Override
  public void prefixScanKeys(final DbKey prefix, final Consumer<DbBytes> visitor) {
    scansByGroup.merge(groupOf(prefix), 1, Integer::sum);
    delegate.prefixScanKeys(prefix, visitor);
  }

  @Override
  public void forEach(final BiConsumer<DbBytes, DbBytes> visitor) {
    delegate.forEach(visitor);
  }

  @Override
  public void put(final DbBytes key, final DbBytes value) {
    delegate.put(key, value);
  }

  @Override
  public void delete(final DbBytes key) {
    delegate.delete(key);
  }

  private static int groupOf(final DbKey prefix) {
    final byte[] bytes = new byte[prefix.getLength()];
    prefix.write(new UnsafeBuffer(bytes), 0);
    return ByteBuffer.wrap(bytes).getInt();
  }
}
