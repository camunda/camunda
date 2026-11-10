/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.rdbms;

import io.camunda.zeebe.db.impl.jdbc.EntityCache;
import io.camunda.zeebe.db.impl.jdbc.JdbcTransactionContext;
import io.camunda.zeebe.engine.state.rdbms.MyBatisEntityPersister.Loader;
import io.camunda.zeebe.engine.state.rdbms.MyBatisEntityPersister.Writer;
import java.util.Objects;
import java.util.function.Function;

/**
 * What the RDBMS state classes of a partition build on, like the column families of the RocksDB
 * state classes: caches of the tables they own, and reads that bypass them. Every operation ensures
 * an open transaction, so the state classes never wrap their calls.
 *
 * <p>All instances of a transaction context share one {@link EngineStateFlush}, so a commit writes
 * the staged rows of all state classes with a single flush.
 */
final class EngineStateRdbms {

  private final JdbcTransactionContext transactionContext;
  private final int partitionId;
  private final EngineStateFlush flush;

  EngineStateRdbms(final JdbcTransactionContext transactionContext, final int partitionId) {
    this.transactionContext = transactionContext;
    this.partitionId = partitionId;
    flush = transactionContext.participant(EngineStateFlush.class, EngineStateFlush::new);
  }

  /** A cache of a table, whose staged rows are written on commit. */
  <M, K, E> EntityCache<K, E> cache(
      final String entityName,
      final Function<E, K> keyOf,
      final Class<M> mapperType,
      final Loader<M, K, E> loader,
      final Writer<M, E> inserter,
      final Writer<M, E> updater,
      final Writer<M, K> deleter) {
    final var cache =
        new EntityCache<>(
            entityName,
            transactionContext,
            new MyBatisEntityPersister<>(
                transactionContext, partitionId, keyOf, mapperType, loader));
    flush.add(cache, partitionId, mapperType, inserter, updater, deleter);
    return cache;
  }

  /**
   * Runs a query on the committed rows, for reads that bypass the caches: counts, existence checks
   * and the database part of queries that the caller merges with a cache's staged rows.
   */
  <M, T> T read(final Class<M> mapperType, final EngineStateMyBatis.MapperCall<M, T> call) {
    return Objects.requireNonNull(
        transactionContext.call(
            () -> EngineStateMyBatis.read(transactionContext.connection(), mapperType, call)));
  }
}
