/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.db.impl.inmemory;

import io.camunda.zeebe.db.*;
import io.camunda.zeebe.db.impl.DbNil;
import io.camunda.zeebe.protocol.EnumValue;
import java.io.File;
import java.util.Optional;
import java.util.TreeMap;

/**
 * In memory implementation of {@code ZeebeDb}
 *
 * <p>This implementation does not support
 *
 * <ul>
 *   <li>concurrent access
 *   <li>locking between transactions
 *   <li>Taking snapshots
 * </ul>
 *
 * <p>This implementation is backed by a tree map.
 *
 * @param <ColumnFamilyType>
 */
final class InMemoryDb<ColumnFamilyType extends Enum<? extends EnumValue> & EnumValue>
    implements ZeebeDb<ColumnFamilyType> {

  private final TreeMap<Bytes, Bytes> database = new TreeMap<>();

  @Override
  public <KeyType extends DbKey, ValueType extends DbValue>
      ColumnFamily<KeyType, ValueType> createColumnFamily(
          final ColumnFamilyType columnFamily,
          final TransactionContext context,
          final KeyType keyInstance,
          final ValueType valueInstance) {
    return new InMemoryDbColumnFamily<>(columnFamily, context, keyInstance, valueInstance);
  }

  @Override
  public void createSnapshot(final File snapshotDir) {
    throw new IllegalStateException("No snapshots supported");
  }

  @Override
  public Optional<String> getProperty(final String propertyName) {
    return Optional.empty();
  }

  @Override
  public TransactionContext createContext() {
    return new InMemoryDbTransactionContext(database);
  }

  @Override
  public boolean isEmpty(final ColumnFamilyType column, final TransactionContext context) {
    return createColumnFamily(column, context, DbNullKey.INSTANCE, DbNil.INSTANCE).isEmpty();
  }

  @Override
  public void close() {
    database.clear();
  }
}
