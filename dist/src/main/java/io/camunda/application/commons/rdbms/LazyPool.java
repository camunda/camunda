/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.rdbms;

import com.zaxxer.hikari.HikariDataSource;
import io.camunda.application.commons.pt.LazyPhysicalTenantResource;
import java.time.Duration;
import java.time.InstantSource;
import java.util.Optional;
import java.util.function.Function;
import org.jspecify.annotations.NullMarked;

/**
 * The connection pool of one physical tenant, built on its first use with the connection settings
 * that are current at that moment. New settings that arrive before the first use replace the ones
 * it will be built with, so a node that never uses the tenant builds no pool, however often its
 * secrets rotate.
 */
@NullMarked
final class LazyPool implements AutoCloseable {

  private static final Duration RETRY_BACKOFF = Duration.ofSeconds(5);

  private final LazyPhysicalTenantResource<HikariDataSource> pool;
  private volatile RdbmsConnection settings;

  LazyPool(
      final String physicalTenantId,
      final RdbmsConnection settings,
      final Function<RdbmsConnection, HikariDataSource> factory) {
    this.settings = settings;
    pool =
        new LazyPhysicalTenantResource<>(
            physicalTenantId,
            "connection pool",
            () -> factory.apply(this.settings),
            RdbmsDataSources::closeQuietly,
            RETRY_BACKOFF,
            InstantSource.system());
  }

  /** The pool, built if this is its first use. */
  HikariDataSource get() {
    return pool.get();
  }

  Optional<HikariDataSource> getIfCreated() {
    return pool.getIfCreated();
  }

  LazyPhysicalTenantResource.State state() {
    return pool.state();
  }

  /**
   * Sets the settings the pool will be built with, if it has not been built yet.
   *
   * @return false if the pool exists, so the new settings must be applied to it instead
   */
  boolean updateIfNotCreated(final RdbmsConnection next) {
    return pool.runIfNotCreated(() -> settings = next);
  }

  @Override
  public void close() {
    pool.close();
  }
}
