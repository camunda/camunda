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

  private final ValueType valueInstance;
  private final KeyType keyInstance;

  TransactionalColumnFamily(
      ZeebeTransactionDb<ColumnFamilyNames> transactionDb,
      ColumnFamilyNames columnFamily,
      DbContext context,
      KeyType keyInstance,
      ValueType valueInstance) {
    this.transactionDb = transactionDb;
    handle = this.transactionDb.getColumnFamilyHandle(columnFamily);
    this.context = context;
    this.keyInstance = keyInstance;
    this.valueInstance = valueInstance;
  }

  @Override
  public void put(KeyType key, ValueType value) {
    put(context, key, value);
  }

  @Override
  public void put(DbContext context, KeyType key, ValueType value) {
    transactionDb.put(handle, context, key, value);
  }

  @Override
  public ValueType get(KeyType key) {
    return get(context, key);
  }

  @Override
  public ValueType get(DbContext context, KeyType key, ValueType value) {
    final DirectBuffer valueBuffer = transactionDb.get(handle, context, key);
    if (valueBuffer != null) {

      value.wrap(valueBuffer, 0, valueBuffer.capacity());
      return value;
    }
    return null;
  }

  @Override
  public void forEach(Consumer<ValueType> consumer) {
    forEach(context, consumer);
  }

  @Override
  public void forEach(BiConsumer<KeyType, ValueType> consumer) {
    forEach(context, consumer);
  }

  @Override
  public void whileTrue(KeyValuePairVisitor<KeyType, ValueType> visitor) {
    whileTrue(context, visitor);
  }

  @Override
  public void whileTrue(
      DbContext context,
      KeyValuePairVisitor<KeyType, ValueType> visitor,
      KeyType key,
      ValueType value) {
    transactionDb.whileTrue(handle, context, key, value, visitor);
  }

  @Override
  public void whileEqualPrefix(DbKey keyPrefix, BiConsumer<KeyType, ValueType> visitor) {
    whileEqualPrefix(context, keyPrefix, visitor);
  }

  @Override
  public void whileEqualPrefix(DbKey keyPrefix, KeyValuePairVisitor<KeyType, ValueType> visitor) {
    whileEqualPrefix(context, keyPrefix, visitor);
  }

  @Override
  public void delete(KeyType key) {
    delete(context, key);
  }

  @Override
  public void delete(DbContext context, KeyType key) {
    transactionDb.delete(handle, context, key);
  }

  @Override
  public boolean exists(KeyType key) {
    return exists(context, key);
  }

  @Override
  public boolean existsPrefix(DbKey keyPrefix) {
    return transactionDb.existsPrefix(handle, context, keyPrefix, keyInstance, valueInstance);
  }

  @Override
  public boolean isEmpty() {
    return isEmpty(context);
  }

  @Override
  public boolean isEmpty(DbContext context) {
    return transactionDb.isEmpty(handle, context);
  }

  public ValueType get(DbContext context, KeyType key) {
    return get(context, key, valueInstance);
  }

  public void forEach(DbContext context, Consumer<ValueType> consumer) {
    transactionDb.foreach(handle, context, valueInstance, consumer);
  }

  public void forEach(DbContext context, BiConsumer<KeyType, ValueType> consumer) {
    transactionDb.foreach(handle, context, keyInstance, valueInstance, consumer);
  }

  public void whileTrue(DbContext context, KeyValuePairVisitor<KeyType, ValueType> visitor) {
    whileTrue(context, visitor, keyInstance, valueInstance);
  }

  public void whileEqualPrefix(
      DbContext context, DbKey keyPrefix, BiConsumer<KeyType, ValueType> visitor) {
    transactionDb.whileEqualPrefix(handle, context, keyPrefix, keyInstance, valueInstance, visitor);
  }

  public void whileEqualPrefix(
      DbContext context, DbKey keyPrefix, KeyValuePairVisitor<KeyType, ValueType> visitor) {
    transactionDb.whileEqualPrefix(handle, context, keyPrefix, keyInstance, valueInstance, visitor);
  }

  public boolean exists(DbContext context, KeyType key) {
    return transactionDb.exists(handle, context, key);
  }
}
