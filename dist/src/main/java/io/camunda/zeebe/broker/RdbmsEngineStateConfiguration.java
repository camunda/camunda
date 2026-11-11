/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker;

import com.zaxxer.hikari.HikariDataSource;
import io.camunda.application.commons.configuration.BrokerBasedConfiguration;
import io.camunda.zeebe.broker.logstreams.rdbms.RdbmsLogStorageSchema;
import io.camunda.zeebe.broker.system.configuration.RdbmsEngineStateCfg;
import io.camunda.zeebe.engine.state.rdbms.RdbmsEngineStateResources;
import io.camunda.zeebe.engine.state.rdbms.RdbmsEngineStateSchema;
import io.camunda.zeebe.mailbox.rdbms.RdbmsMailboxSchema;
import io.camunda.zeebe.mailbox.rdbms.RdbmsMailboxStores;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Map;
import liquibase.exception.LiquibaseException;
import liquibase.integration.spring.SpringLiquibase;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.jdbc.DatabaseDriver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ResourceLoader;

/**
 * Experimental PoC: the connection pools of the RDBMS primary storage, with its schema migrated
 * before the broker starts. It holds the engine state, the partitions' logs, and the mailbox and
 * leases through which brokers and gateways of different regions coordinate. The pools are
 * deliberately not {@link javax.sql.DataSource} beans, so Spring Boot's DataSource-based
 * auto-configurations do not pick them up.
 */
@Configuration(proxyBeanMethods = false)
@Profile("broker")
public class RdbmsEngineStateConfiguration {

  /**
   * The connections of the mailbox and the leases. They have a pool of their own, so that engine
   * load cannot delay a lease renewal until the leader gives up a partition it could still lead.
   */
  private static final int MAILBOX_POOL_SIZE = 8;

  @Bean(destroyMethod = "close")
  public RdbmsEngineStatePool rdbmsEngineStatePool(
      final BrokerBasedConfiguration configuration,
      final MeterRegistry meterRegistry,
      final ResourceLoader resourceLoader)
      throws LiquibaseException {
    final var cfg = configuration.config().getData().getRdbmsEngineState();
    if (!cfg.isEnabled()) {
      return new RdbmsEngineStatePool(null);
    }

    final var dataSource = createDataSource(cfg, meterRegistry);
    try {
      migrateSchema(dataSource, resourceLoader);
      return new RdbmsEngineStatePool(RdbmsEngineStateResources.create(dataSource));
    } catch (final LiquibaseException | RuntimeException e) {
      dataSource.close();
      throw e;
    }
  }

  /**
   * The mailbox and lease stores, on a pool of their own. Depends on the engine state pool, which
   * migrates their schema.
   */
  @Bean(destroyMethod = "close")
  public RdbmsMailboxPool rdbmsMailboxPool(
      final BrokerBasedConfiguration configuration,
      final MeterRegistry meterRegistry,
      final RdbmsEngineStatePool engineStatePool) {
    final var cfg = configuration.config().getData().getRdbmsEngineState();
    if (engineStatePool.resources() == null) {
      return new RdbmsMailboxPool(null);
    }

    final var dataSource = new HikariDataSource();
    configure(dataSource, "camunda-mailbox", cfg, MAILBOX_POOL_SIZE, meterRegistry);
    return new RdbmsMailboxPool(RdbmsMailboxStores.create(dataSource));
  }

  private static HikariDataSource createDataSource(
      final RdbmsEngineStateCfg cfg, final MeterRegistry meterRegistry) {
    final var dataSource = new HikariDataSource();
    configure(dataSource, "camunda-engine-state", cfg, cfg.getPoolSize(), meterRegistry);
    dataSource.setAutoCommit(false);
    return dataSource;
  }

  private static void configure(
      final HikariDataSource dataSource,
      final String poolName,
      final RdbmsEngineStateCfg cfg,
      final int poolSize,
      final MeterRegistry meterRegistry) {
    dataSource.setPoolName(poolName);
    dataSource.setJdbcUrl(cfg.getUrl());
    dataSource.setUsername(cfg.getUsername());
    dataSource.setPassword(cfg.getPassword());
    final var driverClassName = DatabaseDriver.fromJdbcUrl(cfg.getUrl()).getDriverClassName();
    if (driverClassName != null) {
      dataSource.setDriverClassName(driverClassName);
    }
    dataSource.setMaximumPoolSize(poolSize);
    dataSource.setMetricRegistry(meterRegistry);
  }

  /**
   * All changelogs are tracked in the engine state's Liquibase tables; the log's is kept next to
   * the log storage code in the broker, which the engine state module does not depend on, and the
   * mailbox's next to the mailbox stores.
   */
  private static void migrateSchema(
      final HikariDataSource dataSource, final ResourceLoader resourceLoader)
      throws LiquibaseException {
    migrate(dataSource, resourceLoader, RdbmsEngineStateSchema.CHANGELOG, Map.of());
    migrate(
        dataSource,
        resourceLoader,
        RdbmsLogStorageSchema.CHANGELOG,
        Map.of("prefix", RdbmsLogStorageSchema.TABLE_PREFIX));
    migrate(dataSource, resourceLoader, RdbmsMailboxSchema.CHANGELOG, Map.of());
  }

  private static void migrate(
      final HikariDataSource dataSource,
      final ResourceLoader resourceLoader,
      final String changelog,
      final Map<String, String> parameters)
      throws LiquibaseException {
    final var liquibase = new SpringLiquibase();
    liquibase.setDataSource(dataSource);
    liquibase.setResourceLoader(resourceLoader);
    liquibase.setChangeLog(changelog);
    liquibase.setChangeLogParameters(parameters);
    liquibase.setDatabaseChangeLogTable(RdbmsEngineStateSchema.DATABASE_CHANGELOG_TABLE);
    liquibase.setDatabaseChangeLogLockTable(RdbmsEngineStateSchema.DATABASE_CHANGELOG_LOCK_TABLE);
    liquibase.afterPropertiesSet();
  }

  /** Holds what is built once per broker, or null if the RDBMS engine state is disabled. */
  public record RdbmsEngineStatePool(@Nullable RdbmsEngineStateResources resources)
      implements AutoCloseable {

    @Override
    public void close() throws Exception {
      if (resources != null) {
        resources.close();
      }
    }
  }

  /** Holds the mailbox and lease stores, or null if the RDBMS primary storage is disabled. */
  public record RdbmsMailboxPool(@Nullable RdbmsMailboxStores stores) implements AutoCloseable {

    @Override
    public void close() throws Exception {
      if (stores != null && stores.dataSource() instanceof final AutoCloseable closeable) {
        closeable.close();
      }
    }
  }
}
