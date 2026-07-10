/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.layered.typed;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.DbKey;
import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.db.KeyValuePairVisitor;
import io.camunda.zeebe.db.KeyVisitor;
import io.camunda.zeebe.db.ZeebeDbInconsistentException;
import io.camunda.zeebe.db.layered.LayeredKeyValueStore;
import io.camunda.zeebe.protocol.ColumnFamilyScope;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.agrona.collections.MutableBoolean;
import org.agrona.collections.MutableLong;

/**
 * A flyweight-typed {@link ColumnFamily} adapter over a {@link LayeredKeyValueStore}. Keys and
 * values are serialized to fresh byte arrays for every store call (the store retains key arrays);
 * reads and iteration wrap the returned bytes into the key/value instances handed to the
 * constructor, so — exactly like the transactional implementation — a returned or visited flyweight
 * aliases the adapter's single instance and must be copied before being stored.
 *
 * <p><b>Threading:</b> owner-thread only, like the underlying store.
 *
 * <p>Divergences from the transactional implementation, forced by the store's scan surface:
 *
 * <ul>
 *   <li>Early termination: {@link LayeredKeyValueStore#prefixScan} has no boolean-visitor variant,
 *       so terminating visitors stop the scan with a cached, stackless sentinel exception thrown
 *       and caught locally (allocation-free).
 *   <li>Reverse iteration: the store scans forward only, so reverse methods buffer the matching
 *       range of a forward scan and replay it backwards — O(range) memory.
 * </ul>
 *
 * <p>Mutating the column family from within a visitor is supported: the store's scans are
 * point-in-time (see {@link LayeredKeyValueStore#prefixScan}), so a visitor deleting or updating
 * keys — including the one being visited — never disturbs the ongoing iteration.
 */
public final class LayeredColumnFamily<KeyType extends DbKey, ValueType extends DbValue>
    implements ColumnFamily<KeyType, ValueType> {

  private static final byte[] EMPTY_PREFIX = {};

  private final LayeredKeyValueStore store;
  private final KeyType keyInstance;
  private final ValueType valueInstance;

  public LayeredColumnFamily(
      final LayeredKeyValueStore store, final KeyType keyInstance, final ValueType valueInstance) {
    this.store = Objects.requireNonNull(store, "store");
    this.keyInstance = Objects.requireNonNull(keyInstance, "keyInstance");
    this.valueInstance = Objects.requireNonNull(valueInstance, "valueInstance");
  }

  @Override
  public void insert(final KeyType key, final ValueType value) {
    final byte[] keyBytes = TypedBytes.serialize(key);
    if (store.exists(keyBytes)) {
      throw new ZeebeDbInconsistentException(
          "Key " + key + " in ColumnFamily " + store.name() + " already exists");
    }
    store.put(keyBytes, TypedBytes.serialize(value));
  }

  @Override
  public void update(final KeyType key, final ValueType value) {
    final byte[] keyBytes = TypedBytes.serialize(key);
    if (!store.exists(keyBytes)) {
      throw new ZeebeDbInconsistentException(
          "Key " + key + " in ColumnFamily " + store.name() + " does not exist");
    }
    store.put(keyBytes, TypedBytes.serialize(value));
  }

  @Override
  public void upsert(final KeyType key, final ValueType value) {
    store.put(TypedBytes.serialize(key), TypedBytes.serialize(value));
  }

  @Override
  public ValueType get(final KeyType key) {
    final byte[] valueBytes = store.get(TypedBytes.serialize(key));
    if (valueBytes == null) {
      return null;
    }
    return TypedBytes.wrapInto(valueInstance, valueBytes);
  }

  @Override
  public ValueType get(final KeyType key, final Supplier<ValueType> valueSupplier) {
    final byte[] valueBytes = store.get(TypedBytes.serialize(key));
    if (valueBytes == null) {
      return null;
    }
    return TypedBytes.wrapInto(valueSupplier.get(), valueBytes);
  }

  @Override
  public void forEach(final Consumer<ValueType> consumer) {
    scan(
        EMPTY_PREFIX,
        null,
        (keyBytes, valueBytes) -> {
          consumer.accept(TypedBytes.wrapInto(valueInstance, valueBytes));
          return true;
        });
  }

  @Override
  public void forEach(final BiConsumer<KeyType, ValueType> consumer) {
    scan(
        EMPTY_PREFIX,
        null,
        (keyBytes, valueBytes) -> {
          consumer.accept(
              TypedBytes.wrapInto(keyInstance, keyBytes),
              TypedBytes.wrapInto(valueInstance, valueBytes));
          return true;
        });
  }

  @Override
  public void whileTrue(
      final KeyType startAtKey, final KeyValuePairVisitor<KeyType, ValueType> visitor) {
    scan(EMPTY_PREFIX, serializeOrNull(startAtKey), keyValueVisitor(visitor));
  }

  @Override
  public void whileTrue(final KeyValuePairVisitor<KeyType, ValueType> visitor) {
    scan(EMPTY_PREFIX, null, keyValueVisitor(visitor));
  }

  @Override
  public void whileEqualPrefix(
      final DbKey keyPrefix, final BiConsumer<KeyType, ValueType> visitor) {
    scan(
        TypedBytes.serialize(keyPrefix),
        null,
        (keyBytes, valueBytes) -> {
          visitor.accept(
              TypedBytes.wrapInto(keyInstance, keyBytes),
              TypedBytes.wrapInto(valueInstance, valueBytes));
          return true;
        });
  }

  @Override
  public void whileEqualPrefix(
      final DbKey keyPrefix, final KeyValuePairVisitor<KeyType, ValueType> visitor) {
    scan(TypedBytes.serialize(keyPrefix), null, keyValueVisitor(visitor));
  }

  @Override
  public void whileEqualPrefix(
      final DbKey keyPrefix,
      final KeyType startAtKey,
      final KeyValuePairVisitor<KeyType, ValueType> visitor) {
    scan(TypedBytes.serialize(keyPrefix), serializeOrNull(startAtKey), keyValueVisitor(visitor));
  }

  @Override
  public void whileTrueReverse(
      final KeyType startAtKey, final KeyValuePairVisitor<KeyType, ValueType> visitor) {
    scanReverse(TypedBytes.serialize(startAtKey), keyValueVisitor(visitor));
  }

  // ---- Key-only iteration methods ----

  @Override
  public void forEachKey(final Consumer<KeyType> consumer) {
    scan(
        EMPTY_PREFIX,
        null,
        (keyBytes, valueBytes) -> {
          consumer.accept(TypedBytes.wrapInto(keyInstance, keyBytes));
          return true;
        });
  }

  @Override
  public void whileTrue(final KeyVisitor<KeyType> visitor) {
    scan(EMPTY_PREFIX, null, keyOnlyVisitor(visitor));
  }

  @Override
  public void whileTrue(final KeyType startAtKey, final KeyVisitor<KeyType> visitor) {
    scan(EMPTY_PREFIX, serializeOrNull(startAtKey), keyOnlyVisitor(visitor));
  }

  @Override
  public void whileEqualPrefix(final DbKey keyPrefix, final Consumer<KeyType> visitor) {
    scan(
        TypedBytes.serialize(keyPrefix),
        null,
        (keyBytes, valueBytes) -> {
          visitor.accept(TypedBytes.wrapInto(keyInstance, keyBytes));
          return true;
        });
  }

  @Override
  public void whileEqualPrefix(final DbKey keyPrefix, final KeyVisitor<KeyType> visitor) {
    scan(TypedBytes.serialize(keyPrefix), null, keyOnlyVisitor(visitor));
  }

  @Override
  public void whileEqualPrefix(
      final DbKey keyPrefix, final KeyType startAtKey, final KeyVisitor<KeyType> visitor) {
    scan(TypedBytes.serialize(keyPrefix), serializeOrNull(startAtKey), keyOnlyVisitor(visitor));
  }

  @Override
  public void whileTrueReverse(final KeyType startAtKey, final KeyVisitor<KeyType> visitor) {
    scanReverse(TypedBytes.serialize(startAtKey), keyOnlyVisitor(visitor));
  }

  @Override
  public void deleteExisting(final KeyType key) {
    final byte[] keyBytes = TypedBytes.serialize(key);
    if (!store.exists(keyBytes)) {
      throw new ZeebeDbInconsistentException(
          "Key " + key + " in ColumnFamily " + store.name() + " does not exist");
    }
    store.delete(keyBytes);
  }

  @Override
  public void deleteIfExists(final KeyType key) {
    store.delete(TypedBytes.serialize(key));
  }

  @Override
  public boolean exists(final KeyType key) {
    return store.exists(TypedBytes.serialize(key));
  }

  @Override
  public boolean isEmpty() {
    final MutableBoolean isEmpty = new MutableBoolean(true);
    scan(
        EMPTY_PREFIX,
        null,
        (keyBytes, valueBytes) -> {
          isEmpty.set(false);
          return false;
        });
    return isEmpty.get();
  }

  @Override
  public long count() {
    return countInPrefix(EMPTY_PREFIX);
  }

  @Override
  public long countEqualPrefix(final DbKey prefix) {
    return countInPrefix(TypedBytes.serialize(prefix));
  }

  @Override
  public ColumnFamilyScope partitionScope() {
    // layered stores buffer per-partition runtime state; nothing routes a scope to this adapter yet
    return ColumnFamilyScope.PARTITION_LOCAL;
  }

  private long countInPrefix(final byte[] prefixBytes) {
    final MutableLong count = new MutableLong(0);
    scan(
        prefixBytes,
        null,
        (keyBytes, valueBytes) -> {
          count.increment();
          return true;
        });
    return count.get();
  }

  private RawVisitor keyValueVisitor(final KeyValuePairVisitor<KeyType, ValueType> visitor) {
    return (keyBytes, valueBytes) ->
        visitor.visit(
            TypedBytes.wrapInto(keyInstance, keyBytes),
            TypedBytes.wrapInto(valueInstance, valueBytes));
  }

  private RawVisitor keyOnlyVisitor(final KeyVisitor<KeyType> visitor) {
    return (keyBytes, valueBytes) -> visitor.visit(TypedBytes.wrapInto(keyInstance, keyBytes));
  }

  private static byte[] serializeOrNull(final DbKey key) {
    return key == null ? null : TypedBytes.serialize(key);
  }

  /**
   * Forward scan over all visible entries prefixed by {@code prefixBytes}, in unsigned-byte key
   * order, skipping entries before {@code startAtBytes} (inclusive start, matching the reference
   * implementation's seek). Early termination uses the cached sentinel exception because the
   * store's scan cannot be stopped from the visitor.
   */
  private void scan(final byte[] prefixBytes, final byte[] startAtBytes, final RawVisitor visitor) {
    try {
      store.prefixScan(
          prefixBytes,
          (keyBytes, valueBytes) -> {
            if (startAtBytes != null && Arrays.compareUnsigned(keyBytes, startAtBytes) < 0) {
              return;
            }
            if (!visitor.visit(keyBytes, valueBytes)) {
              throw StopVisitation.INSTANCE;
            }
          });
    } catch (final StopVisitation stop) {
      // the visitor requested termination; the scan state is local, nothing to unwind
    }
  }

  /**
   * Reverse scan starting at {@code startAtBytes} — at the key itself if present, else at the key
   * just before it. Buffers the matching forward range and replays it backwards, because the store
   * only scans forward.
   */
  private void scanReverse(final byte[] startAtBytes, final RawVisitor visitor) {
    final List<byte[]> keys = new ArrayList<>();
    final List<byte[]> values = new ArrayList<>();
    store.prefixScan(
        EMPTY_PREFIX,
        (keyBytes, valueBytes) -> {
          if (Arrays.compareUnsigned(keyBytes, startAtBytes) <= 0) {
            keys.add(keyBytes);
            values.add(valueBytes);
          }
        });
    for (int i = keys.size() - 1; i >= 0; i--) {
      if (!visitor.visit(keys.get(i), values.get(i))) {
        return;
      }
    }
  }

  @FunctionalInterface
  private interface RawVisitor {
    boolean visit(byte[] keyBytes, byte[] valueBytes);
  }

  /** Cached, stackless control-flow sentinel — never observable outside {@link #scan}. */
  private static final class StopVisitation extends RuntimeException {
    private static final StopVisitation INSTANCE = new StopVisitation();

    private StopVisitation() {
      super(null, null, false, false);
    }
  }
}
