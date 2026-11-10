/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.rdbms;

import io.camunda.zeebe.db.impl.jdbc.EntityPersister;
import io.camunda.zeebe.db.impl.jdbc.JdbcTransactionContext;
import java.sql.SQLException;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * Loads the rows of one engine state table of one partition through a mapper statement, on the
 * context's current connection. Writes are sent by {@link EngineStateFlush}.
 */
final class MyBatisEntityPersister<M, K, E> implements EntityPersister<K, E> {

  private final JdbcTransactionContext transactionContext;
  private final int partitionId;
  private final Function<E, K> keyOf;
  private final Class<M> mapperType;
  private final Loader<M, K, E> loader;

  MyBatisEntityPersister(
      final JdbcTransactionContext transactionContext,
      final int partitionId,
      final Function<E, K> keyOf,
      final Class<M> mapperType,
      final Loader<M, K, E> loader) {
    this.transactionContext = transactionContext;
    this.partitionId = partitionId;
    this.keyOf = keyOf;
    this.mapperType = mapperType;
    this.loader = loader;
  }

  @Override
  public K keyOf(final E entity) {
    return keyOf.apply(entity);
  }

  @Override
  public @Nullable E load(final K key) throws SQLException {
    return EngineStateMyBatis.read(
        transactionContext.connection(),
        mapperType,
        mapper -> loader.load(mapper, partitionId, key));
  }

  @FunctionalInterface
  interface Loader<M, K, E> {
    @Nullable E load(M mapper, int partitionId, K key);
  }

  @FunctionalInterface
  interface Writer<M, T> {
    void write(M mapper, int partitionId, T value);
  }
}
