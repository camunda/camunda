/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.streaming.state;

import io.camunda.eventbridge.streaming.state.api.KeyValueStore;
import io.camunda.eventbridge.streaming.state.api.StateStoreProvider;
import io.camunda.eventbridge.streaming.state.cache.CachingKeyValueStore;
import io.camunda.zeebe.db.DbKey;
import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.protocol.EnumValue;
import io.camunda.zeebe.protocol.ScopedColumnFamily;
import java.util.function.Supplier;

/**
 * Declares a state store and materializes it from a {@link StateStoreProvider}, so callers state
 * <em>what</em> store they want (column family, key/value types, whether to cache) instead of
 * hand-wiring the column family and cache wrapper at every site.
 *
 * <pre>{@code
 * KeyValueStore<DbLong, PersistedVariables> variables =
 *     StoreBuilder.keyValueStore(CF.VARIABLES, DbLong::new, PersistedVariables::new)
 *         .withCaching(16 * 1024 * 1024)
 *         .build(provider);
 * }</pre>
 *
 * <p>With caching, the store is wrapped in a {@link CachingKeyValueStore} — a bytes-bounded
 * read-through cache whose writes flush on {@link
 * io.camunda.eventbridge.streaming.state.api.Checkpointable#checkpoint()} into the runtime's commit
 * transaction. Recovery is changelog-free by design: the delegate advances atomically with the
 * consumed offsets and a crash replays the source log, so no per-store changelog is materialized.
 *
 * @param <CF> the caller's column-family enum
 * @param <K> the key type (a {@link DbKey} flyweight)
 * @param <V> the value type (a {@link DbValue} flyweight)
 */
public final class StoreBuilder<
    CF extends Enum<? extends EnumValue> & EnumValue & ScopedColumnFamily,
    K extends DbKey,
    V extends DbValue> {

  private final CF columnFamily;
  private final Supplier<K> keyFlyweight;
  private final Supplier<V> valueFlyweight;
  private long cacheMaxBytes;
  private boolean absorbDeletes;

  private StoreBuilder(
      final CF columnFamily, final Supplier<K> keyFlyweight, final Supplier<V> valueFlyweight) {
    this.columnFamily = columnFamily;
    this.keyFlyweight = keyFlyweight;
    this.valueFlyweight = valueFlyweight;
  }

  /**
   * Declares a key-value store on {@code columnFamily}. The suppliers must return fresh flyweights
   * on each call — the delegate and the cache (which keeps separate flyweights for owner-thread
   * reads and for persisting a frozen snapshot) each need their own.
   */
  public static <
          CF extends Enum<? extends EnumValue> & EnumValue & ScopedColumnFamily,
          K extends DbKey,
          V extends DbValue>
      StoreBuilder<CF, K, V> keyValueStore(
          final CF columnFamily, final Supplier<K> keyFlyweight, final Supplier<V> valueFlyweight) {
    return new StoreBuilder<>(columnFamily, keyFlyweight, valueFlyweight);
  }

  /**
   * Wraps the store in a bytes-bounded read-through cache holding up to {@code maxBytes} of hot
   * entries. Omit to materialize the durable store directly (every read and write hits the
   * delegate).
   */
  public StoreBuilder<CF, K, V> withCaching(final long maxBytes) {
    if (maxBytes <= 0) {
      throw new IllegalArgumentException("cache maxBytes must be > 0, was " + maxBytes);
    }
    cacheMaxBytes = maxBytes;
    return this;
  }

  /**
   * Lets the cache annihilate a put-then-delete pair that never reached the durable store — no
   * write, no tombstone. Opt in ONLY when a deleted key is never read again (deletes are garbage
   * collection of dead rows): if the durable store holds an older flushed value for the key,
   * skipping the tombstone leaves it as unreachable dead space, reclaimed by compaction. Requires
   * {@link #withCaching}.
   */
  public StoreBuilder<CF, K, V> withDeleteAbsorption() {
    absorbDeletes = true;
    return this;
  }

  /** Materializes the store from {@code provider}, applying caching if configured. */
  public KeyValueStore<K, V> build(final StateStoreProvider<CF> provider) {
    if (cacheMaxBytes == 0) {
      return provider.keyValueStore(columnFamily, keyFlyweight.get(), valueFlyweight.get());
    }
    return buildCache(provider);
  }

  /**
   * Materializes a caching store, returning the concrete {@link CachingKeyValueStore} so the caller
   * can drive its {@link io.camunda.eventbridge.streaming.state.api.Checkpointable#checkpoint()}
   * and observe {@link CachingKeyValueStore#overCapacity()}. Requires {@link #withCaching} first.
   */
  public CachingKeyValueStore<K, V> buildCache(final StateStoreProvider<CF> provider) {
    if (cacheMaxBytes == 0) {
      throw new IllegalStateException("caching not enabled; call withCaching(maxBytes) first");
    }
    return new CachingKeyValueStore<>(
        provider.keyValueStore(columnFamily, keyFlyweight.get(), valueFlyweight.get()),
        keyFlyweight,
        valueFlyweight,
        cacheMaxBytes,
        absorbDeletes);
  }
}
