/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.partitioning.startup;

import com.zaxxer.hikari.HikariDataSource;
import io.camunda.zeebe.broker.system.configuration.RdbmsStorageCfg;
import io.camunda.zeebe.db.rdbms.RdbmsStorage;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The relational database that holds the state of one partition group's partitions, when {@code
 * camunda.data.primary-storage.type} is {@code rdbms}. It owns the group's connection pool: {@link
 * #open()} creates the pool and the schema before the partitions start, and {@link #close()} closes
 * the pool once they have stopped.
 */
@NullMarked
public final class RdbmsStateStorage implements AutoCloseable {

  private final String partitionGroup;
  private final RdbmsStorageCfg cfg;
  private final MeterRegistry meterRegistry;
  private volatile @Nullable HikariDataSource dataSource;
  private volatile @Nullable RdbmsStorage storage;

  public RdbmsStateStorage(
      final String partitionGroup, final RdbmsStorageCfg cfg, final MeterRegistry meterRegistry) {
    this.partitionGroup = Objects.requireNonNull(partitionGroup, "must specify a partition group");
    this.cfg = Objects.requireNonNull(cfg, "must specify the database settings");
    this.meterRegistry = Objects.requireNonNull(meterRegistry, "must specify a meter registry");
  }

  /**
   * Creates the connection pool, then creates or updates the schema.
   *
   * @throws RuntimeException if the database can't be reached or its schema can't be updated; the
   *     pool is closed again
   */
  public void open() {
    final var pool = createPool();
    try {
      storage = RdbmsStorage.open(pool, Objects.requireNonNullElse(cfg.getPrefix(), ""));
      dataSource = pool;
    } catch (final RuntimeException e) {
      pool.close();
      throw e;
    }
  }

  /**
   * @throws IllegalStateException if it isn't open
   */
  public RdbmsStorage storage() {
    final var opened = storage;
    if (opened == null) {
      throw new IllegalStateException(
          "Expected the database of partition group '%s' to be open, but it isn't"
              .formatted(partitionGroup));
    }
    return opened;
  }

  @Override
  public void close() {
    storage = null;
    final var pool = dataSource;
    dataSource = null;
    if (pool != null) {
      pool.close();
    }
  }

  private HikariDataSource createPool() {
    final var pool = new HikariDataSource();
    pool.setPoolName("zeebe-state-" + partitionGroup);
    pool.setJdbcUrl(cfg.getUrl());
    pool.setUsername(cfg.getUsername());
    pool.setPassword(cfg.getPassword());

    final var poolCfg = cfg.getConnectionPool();
    pool.setMaximumPoolSize(poolCfg.getMaximumPoolSize());
    pool.setMinimumIdle(poolCfg.getMinimumIdle());
    pool.setConnectionTimeout(poolCfg.getConnectionTimeout().toMillis());
    pool.setIdleTimeout(poolCfg.getIdleTimeout().toMillis());
    pool.setMaxLifetime(poolCfg.getMaxLifetime().toMillis());
    pool.setLeakDetectionThreshold(poolCfg.getLeakDetectionThreshold().toMillis());
    pool.setKeepaliveTime(poolCfg.getKeepaliveTime().toMillis());
    pool.setValidationTimeout(poolCfg.getValidationTimeout().toMillis());

    pool.setMetricRegistry(meterRegistry);
    pool.setAutoCommit(false);
    return pool;
  }
}
