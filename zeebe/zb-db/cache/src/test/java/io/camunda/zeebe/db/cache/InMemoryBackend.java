/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.cache;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.TransactionOperation;
import io.camunda.zeebe.db.ZeebeDb;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.agrona.concurrent.UnsafeBuffer;
import org.jspecify.annotations.Nullable;

/**
 * A backend that keeps each column family in a sorted map. It supports only what the pass-through
 * column families use: {@code get}, {@code upsert} and {@code deleteIfExists}, and transactions
 * that run their operations directly.
 */
final class InMemoryBackend {

  private final Map<TestColumnFamily, NavigableMap<byte[], byte[]>> data =
      new EnumMap<>(TestColumnFamily.class);
  private final AtomicInteger transactions = new AtomicInteger();
  private final ZeebeDb<TestColumnFamily> db;

  @SuppressWarnings("unchecked")
  InMemoryBackend() {
    db = mock(ZeebeDb.class);
    when(db.createContext()).thenAnswer(invocation -> createContext());
    when(db.createColumnFamily(any(), any(), any(), any()))
        .thenAnswer(
            invocation -> createColumnFamily(invocation.getArgument(0), invocation.getArgument(3)));
  }

  ZeebeDb<TestColumnFamily> db() {
    return db;
  }

  int transactions() {
    return transactions.get();
  }

  byte @Nullable [] get(final TestColumnFamily columnFamily, final byte[] key) {
    return rows(columnFamily).get(key);
  }

  void put(final TestColumnFamily columnFamily, final byte[] key, final byte[] value) {
    rows(columnFamily).put(key.clone(), value.clone());
  }

  private NavigableMap<byte[], byte[]> rows(final TestColumnFamily columnFamily) {
    return data.computeIfAbsent(columnFamily, cf -> new TreeMap<>(Arrays::compareUnsigned));
  }

  private TransactionContext createContext() {
    final var context = mock(TransactionContext.class);
    doAnswer(
            invocation -> {
              transactions.incrementAndGet();
              invocation.<TransactionOperation>getArgument(0).run();
              return null;
            })
        .when(context)
        .runInTransaction(any());
    return context;
  }

  @SuppressWarnings("unchecked")
  private ColumnFamily<RawKey, RawValue> createColumnFamily(
      final TestColumnFamily columnFamily, final RawValue valueInstance) {
    final ColumnFamily<RawKey, RawValue> passThrough = mock(ColumnFamily.class);
    when(passThrough.get(any()))
        .thenAnswer(
            invocation -> {
              final var value = get(columnFamily, invocation.<RawKey>getArgument(0).bytes());
              if (value == null) {
                return null;
              }
              valueInstance.wrap(new UnsafeBuffer(value), 0, value.length);
              return valueInstance;
            });
    doAnswer(
            invocation -> {
              put(
                  columnFamily,
                  invocation.<RawKey>getArgument(0).bytes(),
                  invocation.<RawValue>getArgument(1).bytes());
              return null;
            })
        .when(passThrough)
        .upsert(any(), any());
    doAnswer(
            invocation -> {
              rows(columnFamily).remove(invocation.<RawKey>getArgument(0).bytes());
              return null;
            })
        .when(passThrough)
        .deleteIfExists(any());
    return passThrough;
  }
}
