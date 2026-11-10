/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.instance;

import io.camunda.zeebe.db.impl.jdbc.EntityPersister;
import io.camunda.zeebe.db.impl.jdbc.JdbcTransactionContext;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.function.Function;
import org.apache.ibatis.exceptions.PersistenceException;
import org.apache.ibatis.executor.BatchResult;
import org.apache.ibatis.session.ExecutorType;
import org.jspecify.annotations.Nullable;

/**
 * Persists one engine state table through {@link EngineStateMapper} statements of one partition.
 * Loads run on the context's current connection; a flush sends all statements as JDBC batches on
 * the connection it's given.
 */
final class MyBatisEntityPersister<K, E> implements EntityPersister<K, E> {

  private final JdbcTransactionContext transactionContext;
  private final int partitionId;
  private final Function<E, K> keyOf;
  private final Loader<K, E> loader;
  private final Writer<E> inserter;
  private final Writer<E> updater;
  private final Writer<K> deleter;

  MyBatisEntityPersister(
      final JdbcTransactionContext transactionContext,
      final int partitionId,
      final Function<E, K> keyOf,
      final Loader<K, E> loader,
      final Writer<E> inserter,
      final Writer<E> updater,
      final Writer<K> deleter) {
    this.transactionContext = transactionContext;
    this.partitionId = partitionId;
    this.keyOf = keyOf;
    this.loader = loader;
    this.inserter = inserter;
    this.updater = updater;
    this.deleter = deleter;
  }

  @Override
  public K keyOf(final E entity) {
    return keyOf.apply(entity);
  }

  @Override
  public @Nullable E load(final K key) throws SQLException {
    return EngineStateMyBatis.read(
        transactionContext.connection(), mapper -> loader.load(mapper, partitionId, key));
  }

  @Override
  public int[] flush(
      final Connection connection,
      final List<E> inserts,
      final List<E> updates,
      final List<K> deletes)
      throws SQLException {
    try (final var session = EngineStateMyBatis.openSession(connection, ExecutorType.BATCH)) {
      final var mapper = session.getMapper(EngineStateMapper.class);
      for (final K key : deletes) {
        deleter.write(mapper, partitionId, key);
      }
      for (final E entity : inserts) {
        inserter.write(mapper, partitionId, entity);
      }
      session.flushStatements();

      // flushed on their own, so the batch results hold only the update counts
      for (final E entity : updates) {
        updater.write(mapper, partitionId, entity);
      }
      return updateCounts(session.flushStatements(), updates.size());
    } catch (final PersistenceException e) {
      throw EngineStateMyBatis.unwrap(e);
    }
  }

  private static int[] updateCounts(final List<BatchResult> results, final int updates) {
    final int[] counts = new int[updates];
    int index = 0;
    for (final var result : results) {
      for (final int count : result.getUpdateCounts()) {
        counts[index++] = count;
      }
    }
    return counts;
  }

  @FunctionalInterface
  interface Loader<K, E> {
    @Nullable E load(EngineStateMapper mapper, int partitionId, K key);
  }

  @FunctionalInterface
  interface Writer<T> {
    void write(EngineStateMapper mapper, int partitionId, T value);
  }
}
