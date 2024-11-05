/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.db.impl.inmemory;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.DbKey;
import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.db.KeyValuePairVisitor;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDbInconsistentException;
import io.camunda.zeebe.protocol.EnumValue;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.agrona.DirectBuffer;

final class InMemoryDbColumnFamily<
        ColumnFamilyNames extends Enum<? extends EnumValue> & EnumValue,
        KeyType extends DbKey,
        ValueType extends DbValue>
    implements ColumnFamily<KeyType, ValueType> {

  private final ColumnFamilyNames columnFamily;
  private final TransactionContext context;
  private final KeyType keyInstance;
  private final ValueType valueInstance;

  private final InMemoryDbColumnFamilyIterationContext iterationContext;

  InMemoryDbColumnFamily(
      final ColumnFamilyNames columnFamily,
      final TransactionContext context,
      final KeyType keyInstance,
      final ValueType valueInstance) {
    this.columnFamily = columnFamily;
    this.context = context;
    this.keyInstance = keyInstance;
    this.valueInstance = valueInstance;

    iterationContext = new InMemoryDbColumnFamilyIterationContext(columnFamily.ordinal());
  }

  private void ensureInOpenTransaction(
      final TransactionContext context, final inMemoryDbStateOperation operation) {
    context.runInTransaction(
        () -> operation.run((InMemoryDbState) context.getCurrentTransaction()));
  }

  @Override
  public void insert(final KeyType key, final ValueType value) {
    ensureInOpenTransaction(
        context,
        state -> {
          final FullyQualifiedKey fullyQualifiedKey = new FullyQualifiedKey(columnFamily, key);
          if (state.contains(fullyQualifiedKey)) {
            throw new ZeebeDbInconsistentException(
                "Key " + keyInstance + " in ColumnFamily " + columnFamily + " already exists");
          } else {
            state.put(fullyQualifiedKey, value);
          }
        });
  }

  @Override
  public void update(final KeyType key, final ValueType value) {
    ensureInOpenTransaction(
        context,
        state -> {
          final FullyQualifiedKey fullyQualifiedKey = new FullyQualifiedKey(columnFamily, key);
          if (state.contains(fullyQualifiedKey)) {
            state.put(fullyQualifiedKey, value);
          } else {
            throw new ZeebeDbInconsistentException(
                "Key " + keyInstance + " in ColumnFamily " + columnFamily + " does not exist");
          }
        });
  }

  @Override
  public void upsert(final KeyType key, final ValueType value) {
    ensureInOpenTransaction(
        context, state -> state.put(new FullyQualifiedKey(columnFamily, key), value));
  }

  @Override
  public ValueType get(final KeyType key) {
    final AtomicReference<DirectBuffer> valueBufferRef = new AtomicReference<>(null);

    ensureInOpenTransaction(context, state -> valueBufferRef.set(getValue(state, key)));

    final DirectBuffer valueBuffer = valueBufferRef.get();

    if (valueBuffer != null) {
      valueInstance.wrap(valueBuffer, 0, valueBuffer.capacity());
      return valueInstance;
    }

    return null;
  }

  @Override
  public void forEach(final Consumer<ValueType> consumer) {
    forEach(context, (key, value) -> consumer.accept(value));
  }

  @Override
  public void forEach(final BiConsumer<KeyType, ValueType> consumer) {
    forEach(context, consumer);
  }

  @Override
  public void whileTrue(
      final KeyType startAtKey, final KeyValuePairVisitor<KeyType, ValueType> visitor) {
    whileEqualPrefix(context, startAtKey, DbNullKey.INSTANCE, keyInstance, valueInstance, visitor);
  }

  @Override
  public void whileTrue(final KeyValuePairVisitor<KeyType, ValueType> visitor) {
    whileEqualPrefix(context, DbNullKey.INSTANCE, keyInstance, valueInstance, visitor);
  }

  @Override
  public void whileEqualPrefix(
      final DbKey keyPrefix, final BiConsumer<KeyType, ValueType> visitor) {
    whileEqualPrefix(context, keyPrefix, keyInstance, valueInstance, new Visitor<>(visitor));
  }

  @Override
  public void whileEqualPrefix(
      final DbKey keyPrefix, final KeyValuePairVisitor<KeyType, ValueType> visitor) {
    whileEqualPrefix(context, keyPrefix, keyInstance, valueInstance, visitor);
  }

  @Override
  public void whileEqualPrefix(
      final DbKey keyPrefix,
      final KeyType startAtKey,
      final KeyValuePairVisitor<KeyType, ValueType> visitor) {
    whileEqualPrefix(context, startAtKey, keyPrefix, keyInstance, valueInstance, visitor);
  }

  @Override
  public void deleteExisting(final KeyType key) {
    ensureInOpenTransaction(
        context,
        state -> {
          final FullyQualifiedKey fullyQualifiedKey = new FullyQualifiedKey(columnFamily, key);
          if (state.contains(fullyQualifiedKey)) {
            state.delete(fullyQualifiedKey);
          } else {
            throw new ZeebeDbInconsistentException(
                "Key " + keyInstance + " in ColumnFamily " + columnFamily + " does not exist");
          }
        });
  }

  @Override
  public void deleteIfExists(final KeyType key) {
    ensureInOpenTransaction(
        context,
        state -> {
          final FullyQualifiedKey fullyQualifiedKey = new FullyQualifiedKey(columnFamily, key);
          state.delete(fullyQualifiedKey);
        });
  }

  @Override
  public boolean exists(final KeyType key) {
    final AtomicBoolean exists = new AtomicBoolean(true);

    ensureInOpenTransaction(context, state -> exists.set(getValue(state, key) != null));

    return exists.get();
  }

  @Override
  public boolean isEmpty() {
    final AtomicBoolean isEmpty = new AtomicBoolean(true);
    whileEqualPrefix(
        DbNullKey.INSTANCE,
        (key, value) -> {
          isEmpty.set(false);
          return false;
        });

    return isEmpty.get();
  }

  @Override
  public long count() {
    return countEachInPrefix(DbNullKey.INSTANCE);
  }

  @Override
  public long countEqualPrefix(final DbKey prefix) {
    return countEachInPrefix(prefix);
  }

  private DirectBuffer getValue(final InMemoryDbState state, final DbKey key) {
    final FullyQualifiedKey fullyQualifiedKey = new FullyQualifiedKey(columnFamily, key);
    final byte[] value = state.get(fullyQualifiedKey);

    if (value != null) {
      return BufferUtil.wrapArray(value);
    } else {
      return null;
    }
  }

  private void forEach(
      final TransactionContext context, final BiConsumer<KeyType, ValueType> consumer) {
    whileEqualPrefix(context, keyInstance, valueInstance, consumer);
  }

  private void whileEqualPrefix(
      final TransactionContext context,
      final KeyType keyInstance,
      final ValueType valueInstance,
      final BiConsumer<KeyType, ValueType> consumer) {
    whileEqualPrefix(
        context, DbNullKey.INSTANCE, keyInstance, valueInstance, new Visitor<>(consumer));
  }

  private void whileEqualPrefix(
      final TransactionContext context,
      final DbKey prefix,
      final KeyType keyInstance,
      final ValueType valueInstance,
      final KeyValuePairVisitor<KeyType, ValueType> visitor) {
    whileEqualPrefix(context, prefix, prefix, keyInstance, valueInstance, visitor);
  }

  private long countEachInPrefix(final DbKey prefix) {
    final var seekTarget = Objects.requireNonNull(prefix);

    final var count = new AtomicLong(0);

    iterationContext.withPrefixKey(
        prefix,
        prefixKey ->
            ensureInOpenTransaction(
                context,
                state -> {
                  final var seekTargetBuffer = iterationContext.keyWithColumnFamily(seekTarget);
                  final byte[] seekTargetBytes = seekTargetBuffer.array();
                  final Iterator<Map.Entry<Bytes, Bytes>> iterator =
                      state.newIterator().seek(seekTargetBytes, seekTargetBytes.length).iterate();

                  final byte[] prefixKeyBytes = prefixKey.toBytes();
                  while (iterator.hasNext()) {
                    final Map.Entry<Bytes, Bytes> entry = iterator.next();

                    final byte[] keyBytes = entry.getKey().toBytes();
                    if (!BufferUtil.startsWith(
                        prefixKeyBytes, 0, prefixKeyBytes.length, keyBytes, 0, keyBytes.length)) {
                      break;
                    }

                    count.getAndIncrement();
                  }
                }));

    return count.get();
  }

  private void whileEqualPrefix(
      final TransactionContext context,
      final DbKey startAt,
      final DbKey prefix,
      final KeyType keyInstance,
      final ValueType valueInstance,
      final KeyValuePairVisitor<KeyType, ValueType> visitor) {
    final var seekTarget = Objects.requireNonNullElse(startAt, prefix);
    Objects.requireNonNull(prefix);
    Objects.requireNonNull(visitor);

    iterationContext.withPrefixKey(
        prefix,
        prefixKey ->
            ensureInOpenTransaction(
                context,
                state -> {
                  final var seekTargetBuffer = iterationContext.keyWithColumnFamily(seekTarget);
                  final byte[] seekTargetBytes = seekTargetBuffer.array();
                  final Iterator<Map.Entry<Bytes, Bytes>> iterator =
                      state.newIterator().seek(seekTargetBytes, seekTargetBytes.length).iterate();

                  final byte[] prefixKeyBytes = prefixKey.toBytes();
                  while (iterator.hasNext()) {
                    final Map.Entry<Bytes, Bytes> entry = iterator.next();

                    final byte[] keyBytes = entry.getKey().toBytes();
                    if (!BufferUtil.startsWith(
                        prefixKeyBytes, 0, prefixKeyBytes.length, keyBytes, 0, keyBytes.length)) {
                      continue;
                    }

                    final DirectBuffer keyViewBuffer =
                        FullyQualifiedKey.wrapKey(entry.getKey().toBytes());

                    keyInstance.wrap(keyViewBuffer, 0, keyViewBuffer.capacity());

                    final DirectBuffer valueViewBuffer =
                        BufferUtil.wrapArray(entry.getValue().toBytes());
                    valueInstance.wrap(valueViewBuffer, 0, valueViewBuffer.capacity());

                    final boolean shouldVisitNext = visitor.visit(keyInstance, valueInstance);

                    if (!shouldVisitNext) {
                      return;
                    }
                  }
                }));
  }

  private static final class Visitor<KeyType extends DbKey, ValueType extends DbValue>
      implements KeyValuePairVisitor<KeyType, ValueType> {

    private final BiConsumer<KeyType, ValueType> delegate;

    private Visitor(final BiConsumer<KeyType, ValueType> delegate) {
      this.delegate = delegate;
    }

    @Override
    public boolean visit(final KeyType key, final ValueType value) {
      delegate.accept(key, value);
      return true;
    }
  }
}
