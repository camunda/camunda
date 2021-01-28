/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.db.impl.rocksdb.transaction;

import io.zeebe.db.ColumnFamily;
import io.zeebe.db.DbContext;
import io.zeebe.db.DbKey;
import io.zeebe.db.DbValue;
import io.zeebe.db.KeyValuePairVisitor;
import io.zeebe.db.impl.DbCompositeKey;
import io.zeebe.db.impl.DbLong;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.agrona.DirectBuffer;

class TransactionalColumnFamily<
        ColumnFamilyNames extends Enum<ColumnFamilyNames>,
        KeyType extends DbKey,
        ValueType extends DbValue>
    implements ColumnFamily<KeyType, ValueType> {

  private final ZeebeTransactionDb<ColumnFamilyNames> transactionDb;
  private final long handle;

  private final DbContext context;

  private final DbCompositeKey<DbLong, KeyType> compositeKey;
  private final ValueType valueInstance;
  private final KeyType keyInstance;
  private final DbLong columnFamilyKey;

  TransactionalColumnFamily(
      final ZeebeTransactionDb<ColumnFamilyNames> transactionDb,
      final ColumnFamilyNames columnFamily,
      final DbContext context,
      final KeyType keyInstance,
      final ValueType valueInstance) {
    this.transactionDb = transactionDb;
    handle = this.transactionDb.getColumnFamilyHandle();
    this.context = context;
    this.keyInstance = keyInstance;
    this.valueInstance = valueInstance;
    columnFamilyKey = new DbLong();
    columnFamilyKey.wrapLong(columnFamily.ordinal());
    compositeKey = new DbCompositeKey<>(columnFamilyKey, keyInstance);
  }

  @Override
  public void put(final KeyType key, final ValueType value) {
    put(context, key, value);
  }

  @Override
  public void put(final DbContext context, final KeyType key, final ValueType value) {
    transactionDb.put(handle, context, compositeKey, value);
  }

  @Override
  public ValueType get(final KeyType key) {
    return get(context, key);
  }

  @Override
  public ValueType get(final DbContext context, final KeyType key, final ValueType value) {
    final DirectBuffer valueBuffer = transactionDb.get(handle, context, compositeKey);
    if (valueBuffer != null) {

      value.wrap(valueBuffer, 0, valueBuffer.capacity());
      return value;
    }
    return null;
  }

  @Override
  public void forEach(final Consumer<ValueType> consumer) {
    forEach(context, consumer);
  }

  @Override
  public void forEach(final BiConsumer<KeyType, ValueType> consumer) {
    forEach(context, consumer);
  }

  @Override
  public void whileTrue(final KeyValuePairVisitor<KeyType, ValueType> visitor) {
    whileTrue(context, visitor);
  }

  @Override
  public void whileTrue(
      final DbContext context,
      final KeyValuePairVisitor<KeyType, ValueType> visitor,
      final KeyType key,
      final ValueType value) {
    transactionDb.whileEqualPrefix(columnFamilyKey, handle, context, key, value, visitor);
  }

  @Override
  public void whileEqualPrefix(
      final DbKey keyPrefix, final BiConsumer<KeyType, ValueType> visitor) {
    whileEqualPrefix(context, keyPrefix, visitor);
  }

  @Override
  public void whileEqualPrefix(
      final DbKey keyPrefix, final KeyValuePairVisitor<KeyType, ValueType> visitor) {
    whileEqualPrefix(context, keyPrefix, visitor);
  }

  @Override
  public void delete(final KeyType key) {
    delete(context, key);
  }

  @Override
  public void delete(final DbContext context, final KeyType key) {
    transactionDb.delete(handle, context, compositeKey);
  }

  @Override
  public boolean exists(final KeyType key) {
    return exists(context, key);
  }

  @Override
  public boolean isEmpty() {
    return isEmpty(context);
  }

  @Override
  public boolean isEmpty(final DbContext context) {
    final AtomicBoolean isEmpty = new AtomicBoolean(true);
    transactionDb.whileEqualPrefix(
        columnFamilyKey,
        handle,
        context,
        keyInstance,
        valueInstance,
        (key, value) -> {
          isEmpty.set(false);
          return false;
        });
    return isEmpty.get();
  }

  public ValueType get(final DbContext context, final KeyType key) {
    return get(context, key, valueInstance);
  }

  public void forEach(final DbContext context, final Consumer<ValueType> consumer) {
    transactionDb.whileEqualPrefix(
        columnFamilyKey,
        handle,
        context,
        keyInstance,
        valueInstance,
        (BiConsumer<KeyType, ValueType>) (ignore, value) -> consumer.accept(value));
  }

  public void forEach(final DbContext context, final BiConsumer<KeyType, ValueType> consumer) {
    transactionDb.whileEqualPrefix(
        columnFamilyKey, handle, context, keyInstance, valueInstance, consumer);
  }

  public void whileTrue(
      final DbContext context, final KeyValuePairVisitor<KeyType, ValueType> visitor) {
    transactionDb.whileEqualPrefix(
        columnFamilyKey, handle, context, keyInstance, valueInstance, visitor);
  }

  public void whileEqualPrefix(
      final DbContext context,
      final DbKey keyPrefix,
      final BiConsumer<KeyType, ValueType> visitor) {
    transactionDb.whileEqualPrefix(
        columnFamilyKey, handle, context, keyPrefix, keyInstance, valueInstance, visitor);
  }

  public void whileEqualPrefix(
      final DbContext context,
      final DbKey keyPrefix,
      final KeyValuePairVisitor<KeyType, ValueType> visitor) {
    transactionDb.whileEqualPrefix(
        columnFamilyKey, handle, context, keyPrefix, keyInstance, valueInstance, visitor);
  }

  public boolean exists(final DbContext context, final KeyType key) {
    return transactionDb.exists(handle, context, compositeKey);
  }
}
