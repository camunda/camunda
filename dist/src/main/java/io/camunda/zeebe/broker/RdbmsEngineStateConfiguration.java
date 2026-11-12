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
import io.camunda.zeebe.db.writebehind.WriteBehindConfiguration;
import io.camunda.zeebe.engine.state.rdbms.RdbmsEngineStateResources;
import io.camunda.zeebe.engine.state.rdbms.RdbmsEngineStateSchema;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.Map;
import liquibase.exception.LiquibaseException;
import liquibase.integration.spring.SpringLiquibase;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.jdbc.DatabaseDriver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ResourceLoader;

/**
 * Experimental PoC: the connection pool of the RDBMS primary storage, with its schema migrated
 * before the broker starts. It holds both the engine state and the partitions' logs. The pool is
 * deliberately not a {@link javax.sql.DataSource} bean, so Spring Boot's DataSource-based
 * auto-configurations do not pick it up.
 */
@Configuration(proxyBeanMethods = false)
@Profile("broker")
public class RdbmsEngineStateConfiguration {

  /**
   * Experimental: set to true to write the engine state rows to the database after the commit.
   * Resolved through the Spring environment, so the system property, the environment variable
   * ZEEBE_ENGINE_RDBMS_WRITEBEHIND and the application properties all work.
   */
  private static final String WRITE_BEHIND_PROPERTY = "zeebe.engine.rdbms.writeBehind";

  /**
   * How long the write-behind writer waits after the oldest unflushed commit, for example 30s.
   * State that a later commit deletes in this time never reaches the database. Default: no delay.
   */
  private static final String FLUSH_DELAY_PROPERTY = "zeebe.engine.rdbms.writeBehind.flushDelay";

  @Bean(destroyMethod = "close")
  public RdbmsEngineStatePool rdbmsEngineStatePool(
      final BrokerBasedConfiguration configuration,
      final MeterRegistry meterRegistry,
      final ResourceLoader resourceLoader,
      final Environment environment)
      throws LiquibaseException {
    final var cfg = configuration.config().getData().getRdbmsEngineState();
    if (!cfg.isEnabled()) {
      return new RdbmsEngineStatePool(null);
    }

    final var dataSource = createDataSource(cfg, meterRegistry);
    try {
      migrateSchema(dataSource, resourceLoader);
      final var writeBehind =
          environment.getProperty(WRITE_BEHIND_PROPERTY, Boolean.class, false)
              ? WriteBehindConfiguration.defaults()
                  .withFlushDelay(
                      environment.getProperty(FLUSH_DELAY_PROPERTY, Duration.class, Duration.ZERO))
              : null;
      return new RdbmsEngineStatePool(RdbmsEngineStateResources.create(dataSource, writeBehind));
    } catch (final LiquibaseException | RuntimeException e) {
      dataSource.close();
      throw e;
    }
  }

  private static HikariDataSource createDataSource(
      final RdbmsEngineStateCfg cfg, final MeterRegistry meterRegistry) {
    final var dataSource = new HikariDataSource();
    dataSource.setPoolName("camunda-engine-state");
    dataSource.setJdbcUrl(cfg.getUrl());
    dataSource.setUsername(cfg.getUsername());
    dataSource.setPassword(cfg.getPassword());
    final var driverClassName = DatabaseDriver.fromJdbcUrl(cfg.getUrl()).getDriverClassName();
    if (driverClassName != null) {
      dataSource.setDriverClassName(driverClassName);
    }
    dataSource.setMaximumPoolSize(cfg.getPoolSize());
    dataSource.setAutoCommit(false);
    dataSource.setTransactionIsolation("TRANSACTION_READ_COMMITTED");
    dataSource.setMetricRegistry(meterRegistry);
    return dataSource;
  }

  /**
   * Both changelogs are tracked in the engine state's Liquibase tables; the log's is kept next to
   * the log storage code in the broker, which the engine state module does not depend on.
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
}
