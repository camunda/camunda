/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.rdbms;

import io.camunda.zeebe.db.impl.jdbc.RelationalState;
import io.camunda.zeebe.db.writebehind.WriteBehindConfiguration;
import io.camunda.zeebe.db.writebehind.WriteBehindStore;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.sql.DataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.jspecify.annotations.Nullable;

/**
 * What all partitions of a broker share for the relational engine state: the connection pool and
 * the MyBatis session factory built on it, both created once per broker. The session factory stays
 * internal to this module, so callers never depend on MyBatis.
 */
public final class RdbmsEngineStateResources implements AutoCloseable {

  private static final int MAX_CACHED_GROUPS = 10_000;

  private final DataSource dataSource;
  private final SqlSessionFactory sessionFactory;
  private final @Nullable WriteBehindConfiguration writeBehind;
  private final Map<Integer, PartitionWriteBehind> stores = new ConcurrentHashMap<>();

  private RdbmsEngineStateResources(
      final DataSource dataSource,
      final SqlSessionFactory sessionFactory,
      final @Nullable WriteBehindConfiguration writeBehind) {
    this.dataSource = dataSource;
    this.sessionFactory = sessionFactory;
    this.writeBehind = writeBehind;
  }

  /** Builds the session factory for the given pool. Commits write to the database directly. */
  public static RdbmsEngineStateResources create(final DataSource dataSource) {
    return create(dataSource, null);
  }

  /**
   * Builds the session factory for the given pool.
   *
   * @param writeBehind true to keep the committed rows in a write-behind store with the default
   *     configuration, see {@link #openPartition}. Each partition then needs up to two connections
   *     at the same time besides the one of its transaction: one for the flush and one for a read.
   */
  public static RdbmsEngineStateResources create(
      final DataSource dataSource, final boolean writeBehind) {
    return create(dataSource, writeBehind ? WriteBehindConfiguration.defaults() : null);
  }

  /**
   * Builds the session factory for the given pool.
   *
   * @param writeBehind if not null, the partitions keep committed rows in a write-behind store and
   *     write them to the database later, see {@link #openPartition}
   */
  public static RdbmsEngineStateResources create(
      final DataSource dataSource, final @Nullable WriteBehindConfiguration writeBehind) {
    return new RdbmsEngineStateResources(
        dataSource, EngineStateMyBatis.createSqlSessionFactory(dataSource), writeBehind);
  }

  /**
   * Opens the write-behind store of a partition, to be handed to the partition's {@code
   * MixedZeebeDb}, which closes it. Call it after {@link #readLastProcessedPosition}, as the store
   * is created empty. Returns null if the resources write to the database directly.
   */
  public @Nullable RelationalState openPartition(final int partitionId) {
    if (writeBehind == null) {
      return null;
    }
    // the state is not scanned by key, and the group ids are those of the entity types, not of the
    // column families that the configuration may name
    final var store =
        new WriteBehindStore<>(
            new JdbcBackend(partitionId, sessionFactory),
            writeBehind.withKeyIndexColumnFamilies(Set.of()));
    final var partition = new PartitionWriteBehind(store, new GroupCache(MAX_CACHED_GROUPS));
    stores.put(partitionId, partition);
    return new PartitionState(partitionId, partition);
  }

  /** The partition's open write-behind store and caches, or null if it has none. */
  @Nullable PartitionWriteBehind writeBehind(final int partitionId) {
    return stores.get(partitionId);
  }

  public DataSource dataSource() {
    return dataSource;
  }

  SqlSessionFactory sessionFactory() {
    return sessionFactory;
  }

  /**
   * Reads the position of the last command whose changes the database contains for the partition,
   * or {@code -1} if there is none: replayed events up to this position must not be applied to the
   * database again. Creates the partition's position row if it doesn't exist yet.
   */
  public long readLastProcessedPosition(final int partitionId) {
    return EntitySession.readLastProcessedPosition(sessionFactory, partitionId);
  }

  private final class PartitionState implements RelationalState {

    private final int partitionId;
    private final PartitionWriteBehind partition;

    private PartitionState(final int partitionId, final PartitionWriteBehind partition) {
      this.partitionId = partitionId;
      this.partition = partition;
    }

    @Override
    public void awaitFlushed() {
      partition.store().awaitFlushed();
    }

    @Override
    public void close() {
      stores.remove(partitionId, partition);
      partition.store().close();
    }
  }

  /** Closes the pool. */
  @Override
  public void close() throws Exception {
    if (dataSource instanceof final AutoCloseable closeable) {
      closeable.close();
    }
  }
}
