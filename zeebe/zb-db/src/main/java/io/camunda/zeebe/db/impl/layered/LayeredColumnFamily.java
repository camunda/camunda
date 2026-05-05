/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.impl.layered;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.DbKey;
import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.db.KeyValuePairVisitor;
import io.camunda.zeebe.db.KeyVisitor;
import io.camunda.zeebe.protocol.ColumnFamilyScope;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

final class LayeredColumnFamily<KeyType extends DbKey, ValueType extends DbValue>
    implements ColumnFamily<KeyType, ValueType> {

  private final LayeredTransactionContext context;
  private final ColumnFamily<KeyType, ValueType> activeColumnFamily;
  private final ColumnFamily<KeyType, ValueType> persistentColumnFamily;
  private final ValueType valueInstance;

  LayeredColumnFamily(
      final LayeredTransactionContext context,
      final ColumnFamily<KeyType, ValueType> activeColumnFamily,
      final ColumnFamily<KeyType, ValueType> persistentColumnFamily,
      final ValueType valueInstance) {
    this.context = context;
    this.activeColumnFamily = activeColumnFamily;
    this.persistentColumnFamily = persistentColumnFamily;
    this.valueInstance = valueInstance;
  }

  @Override
  public void insert(final KeyType key, final ValueType value) {
    context.runInTransaction(
        () -> {
          persistentColumnFamily.insert(key, value);
          activeColumnFamily.insert(key, value);
        });
  }

  @Override
  public void update(final KeyType key, final ValueType value) {
    context.runInTransaction(
        () -> {
          persistentColumnFamily.update(key, value);
          activeColumnFamily.upsert(key, value);
        });
  }

  @Override
  public void upsert(final KeyType key, final ValueType value) {
    context.runInTransaction(
        () -> {
          persistentColumnFamily.upsert(key, value);
          activeColumnFamily.upsert(key, value);
        });
  }

  @Override
  public @Nullable ValueType get(final KeyType key) {
    final var result = new Holder<ValueType>();
    context.runInTransaction(() -> result.value = getOrLoad(key));
    return result.value;
  }

  @Override
  public @Nullable ValueType get(final KeyType key, final Supplier<ValueType> valueSupplier) {
    final var result = new Holder<ValueType>();
    context.runInTransaction(
        () -> {
          final var activeValue = activeColumnFamily.get(key, valueSupplier);
          if (activeValue != null) {
            result.value = activeValue;
            return;
          }

          final var persistentValue = persistentColumnFamily.get(key, this::newValueInstance);
          if (persistentValue == null) {
            result.value = null;
            return;
          }

          activeColumnFamily.upsert(key, persistentValue);
          result.value = activeColumnFamily.get(key, valueSupplier);
        });
    return result.value;
  }

  @Override
  public void forEach(final Consumer<ValueType> consumer) {
    persistentColumnFamily.forEach(consumer);
  }

  @Override
  public void forEach(final BiConsumer<KeyType, ValueType> consumer) {
    persistentColumnFamily.forEach(consumer);
  }

  @Override
  public void whileTrue(
      final KeyType startAtKey, final KeyValuePairVisitor<KeyType, ValueType> visitor) {
    persistentColumnFamily.whileTrue(startAtKey, visitor);
  }

  @Override
  public void whileTrue(final KeyValuePairVisitor<KeyType, ValueType> visitor) {
    persistentColumnFamily.whileTrue(visitor);
  }

  @Override
  public void whileEqualPrefix(
      final DbKey keyPrefix, final BiConsumer<KeyType, ValueType> visitor) {
    persistentColumnFamily.whileEqualPrefix(keyPrefix, visitor);
  }

  @Override
  public void whileEqualPrefix(
      final DbKey keyPrefix, final KeyValuePairVisitor<KeyType, ValueType> visitor) {
    persistentColumnFamily.whileEqualPrefix(keyPrefix, visitor);
  }

  @Override
  public void whileEqualPrefix(
      final DbKey keyPrefix,
      final KeyType startAtKey,
      final KeyValuePairVisitor<KeyType, ValueType> visitor) {
    persistentColumnFamily.whileEqualPrefix(keyPrefix, startAtKey, visitor);
  }

  @Override
  public void whileTrueReverse(
      final KeyType startAtKey, final KeyValuePairVisitor<KeyType, ValueType> visitor) {
    persistentColumnFamily.whileTrueReverse(startAtKey, visitor);
  }

  @Override
  public void forEachKey(final Consumer<KeyType> consumer) {
    persistentColumnFamily.forEachKey(consumer);
  }

  @Override
  public void whileTrue(final KeyVisitor<KeyType> visitor) {
    persistentColumnFamily.whileTrue(visitor);
  }

  @Override
  public void whileTrue(final KeyType startAtKey, final KeyVisitor<KeyType> visitor) {
    persistentColumnFamily.whileTrue(startAtKey, visitor);
  }

  @Override
  public void whileEqualPrefix(final DbKey keyPrefix, final Consumer<KeyType> visitor) {
    persistentColumnFamily.whileEqualPrefix(keyPrefix, visitor);
  }

  @Override
  public void whileEqualPrefix(final DbKey keyPrefix, final KeyVisitor<KeyType> visitor) {
    persistentColumnFamily.whileEqualPrefix(keyPrefix, visitor);
  }

  @Override
  public void whileEqualPrefix(
      final DbKey keyPrefix, final KeyType startAtKey, final KeyVisitor<KeyType> visitor) {
    persistentColumnFamily.whileEqualPrefix(keyPrefix, startAtKey, visitor);
  }

  @Override
  public void whileTrueReverse(final KeyType startAtKey, final KeyVisitor<KeyType> visitor) {
    persistentColumnFamily.whileTrueReverse(startAtKey, visitor);
  }

  @Override
  public void deleteExisting(final KeyType key) {
    context.runInTransaction(
        () -> {
          persistentColumnFamily.deleteExisting(key);
          activeColumnFamily.deleteIfExists(key);
        });
  }

  @Override
  public void deleteIfExists(final KeyType key) {
    context.runInTransaction(
        () -> {
          persistentColumnFamily.deleteIfExists(key);
          activeColumnFamily.deleteIfExists(key);
        });
  }

  @Override
  public boolean exists(final KeyType key) {
    final boolean[] result = {false};
    context.runInTransaction(
        () -> result[0] = activeColumnFamily.exists(key) || persistentColumnFamily.exists(key));
    return result[0];
  }

  @Override
  public boolean isEmpty() {
    final boolean[] result = {true};
    context.runInTransaction(
        () -> result[0] = activeColumnFamily.isEmpty() && persistentColumnFamily.isEmpty());
    return result[0];
  }

  @Override
  public long count() {
    return persistentColumnFamily.count();
  }

  @Override
  public long countEqualPrefix(final DbKey prefix) {
    return persistentColumnFamily.countEqualPrefix(prefix);
  }

  @Override
  public ColumnFamilyScope partitionScope() {
    return persistentColumnFamily.partitionScope();
  }

  private @Nullable ValueType getOrLoad(final KeyType key) {
    final var activeValue = activeColumnFamily.get(key);
    if (activeValue != null) {
      return activeValue;
    }

    final var persistentValue = persistentColumnFamily.get(key, this::newValueInstance);
    if (persistentValue == null) {
      return null;
    }

    activeColumnFamily.upsert(key, persistentValue);
    return activeColumnFamily.get(key);
  }

  @SuppressWarnings("unchecked")
  private ValueType newValueInstance() {
    return (ValueType) valueInstance.newInstance();
  }

  private static final class Holder<T> {
    private T value;
  }
}
