/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.layered.zdb;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.DbKey;
import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.db.KeyValuePairVisitor;
import io.camunda.zeebe.db.KeyVisitor;
import io.camunda.zeebe.protocol.ColumnFamilyScope;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.agrona.collections.MutableBoolean;
import org.agrona.collections.MutableLong;
import org.agrona.collections.MutableReference;

/**
 * Wraps a layered column family so every operation runs inside an open transaction on the layered
 * context, exactly like the reference implementation's column family does: an operation invoked
 * outside a transaction opens one and commits it (so standalone writes are immediately promoted,
 * not left in staging where a later rollback would discard them), while an operation invoked inside
 * an open transaction joins it.
 */
final class LayeredTransactionalColumnFamily<KeyType extends DbKey, ValueType extends DbValue>
    implements ColumnFamily<KeyType, ValueType> {

  private final LayeredTransactionContext context;
  private final ColumnFamily<KeyType, ValueType> delegate;

  LayeredTransactionalColumnFamily(
      final LayeredTransactionContext context, final ColumnFamily<KeyType, ValueType> delegate) {
    this.context = Objects.requireNonNull(context, "context");
    this.delegate = Objects.requireNonNull(delegate, "delegate");
  }

  @Override
  public void insert(final KeyType key, final ValueType value) {
    context.runInTransaction(() -> delegate.insert(key, value));
  }

  @Override
  public void update(final KeyType key, final ValueType value) {
    context.runInTransaction(() -> delegate.update(key, value));
  }

  @Override
  public void upsert(final KeyType key, final ValueType value) {
    context.runInTransaction(() -> delegate.upsert(key, value));
  }

  @Override
  public ValueType get(final KeyType key) {
    final MutableReference<ValueType> result = new MutableReference<>();
    context.runInTransaction(() -> result.set(delegate.get(key)));
    return result.get();
  }

  @Override
  public ValueType get(final KeyType key, final Supplier<ValueType> valueSupplier) {
    final MutableReference<ValueType> result = new MutableReference<>();
    context.runInTransaction(() -> result.set(delegate.get(key, valueSupplier)));
    return result.get();
  }

  @Override
  public void forEach(final Consumer<ValueType> consumer) {
    context.runInTransaction(() -> delegate.forEach(consumer));
  }

  @Override
  public void forEach(final BiConsumer<KeyType, ValueType> consumer) {
    context.runInTransaction(() -> delegate.forEach(consumer));
  }

  @Override
  public void whileTrue(
      final KeyType startAtKey, final KeyValuePairVisitor<KeyType, ValueType> visitor) {
    context.runInTransaction(() -> delegate.whileTrue(startAtKey, visitor));
  }

  @Override
  public void whileTrue(final KeyValuePairVisitor<KeyType, ValueType> visitor) {
    context.runInTransaction(() -> delegate.whileTrue(visitor));
  }

  @Override
  public void whileEqualPrefix(
      final DbKey keyPrefix, final BiConsumer<KeyType, ValueType> visitor) {
    context.runInTransaction(() -> delegate.whileEqualPrefix(keyPrefix, visitor));
  }

  @Override
  public void whileEqualPrefix(
      final DbKey keyPrefix, final KeyValuePairVisitor<KeyType, ValueType> visitor) {
    context.runInTransaction(() -> delegate.whileEqualPrefix(keyPrefix, visitor));
  }

  @Override
  public void whileEqualPrefix(
      final DbKey keyPrefix,
      final KeyType startAtKey,
      final KeyValuePairVisitor<KeyType, ValueType> visitor) {
    context.runInTransaction(() -> delegate.whileEqualPrefix(keyPrefix, startAtKey, visitor));
  }

  @Override
  public void whileTrueReverse(
      final KeyType startAtKey, final KeyValuePairVisitor<KeyType, ValueType> visitor) {
    context.runInTransaction(() -> delegate.whileTrueReverse(startAtKey, visitor));
  }

  @Override
  public void forEachKey(final Consumer<KeyType> consumer) {
    context.runInTransaction(() -> delegate.forEachKey(consumer));
  }

  @Override
  public void whileTrue(final KeyVisitor<KeyType> visitor) {
    context.runInTransaction(() -> delegate.whileTrue(visitor));
  }

  @Override
  public void whileTrue(final KeyType startAtKey, final KeyVisitor<KeyType> visitor) {
    context.runInTransaction(() -> delegate.whileTrue(startAtKey, visitor));
  }

  @Override
  public void whileEqualPrefix(final DbKey keyPrefix, final Consumer<KeyType> visitor) {
    context.runInTransaction(() -> delegate.whileEqualPrefix(keyPrefix, visitor));
  }

  @Override
  public void whileEqualPrefix(final DbKey keyPrefix, final KeyVisitor<KeyType> visitor) {
    context.runInTransaction(() -> delegate.whileEqualPrefix(keyPrefix, visitor));
  }

  @Override
  public void whileEqualPrefix(
      final DbKey keyPrefix, final KeyType startAtKey, final KeyVisitor<KeyType> visitor) {
    context.runInTransaction(() -> delegate.whileEqualPrefix(keyPrefix, startAtKey, visitor));
  }

  @Override
  public void whileTrueReverse(final KeyType startAtKey, final KeyVisitor<KeyType> visitor) {
    context.runInTransaction(() -> delegate.whileTrueReverse(startAtKey, visitor));
  }

  @Override
  public void deleteExisting(final KeyType key) {
    context.runInTransaction(() -> delegate.deleteExisting(key));
  }

  @Override
  public void deleteIfExists(final KeyType key) {
    context.runInTransaction(() -> delegate.deleteIfExists(key));
  }

  @Override
  public boolean exists(final KeyType key) {
    final MutableBoolean result = new MutableBoolean();
    context.runInTransaction(() -> result.set(delegate.exists(key)));
    return result.get();
  }

  @Override
  public boolean isEmpty() {
    final MutableBoolean result = new MutableBoolean();
    context.runInTransaction(() -> result.set(delegate.isEmpty()));
    return result.get();
  }

  @Override
  public long count() {
    final MutableLong result = new MutableLong();
    context.runInTransaction(() -> result.set(delegate.count()));
    return result.get();
  }

  @Override
  public long countEqualPrefix(final DbKey prefix) {
    final MutableLong result = new MutableLong();
    context.runInTransaction(() -> result.set(delegate.countEqualPrefix(prefix)));
    return result.get();
  }

  @Override
  public ColumnFamilyScope partitionScope() {
    return delegate.partitionScope();
  }
}
