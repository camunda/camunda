/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.rdbms;

import com.zaxxer.hikari.HikariDataSource;
import io.camunda.configuration.Rdbms;
import io.camunda.db.rdbms.config.VendorDatabaseProperties;
import io.camunda.db.rdbms.config.VendorDatabasePropertiesLoader;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.jdbc.DatabaseDriver;

/**
 * Per-physical-tenant registry of {@link HikariDataSource} pools and their detected {@link
 * VendorDatabaseProperties}.
 *
 * <p>Each entry is built from a physical-tenant-scoped {@link Rdbms} configuration (URL,
 * credentials, connection-pool tuning). Vendor properties are detected once at construction time
 * from the physical tenant's connection.
 *
 * <p>While the per-physical-tenant configuration surface (see {@code camunda.physical-tenants.*})
 * is being delivered as a separate prerequisite, this class is wired with a single {@code default}
 * physical tenant built from the cluster-wide {@code camunda.data.secondary-storage.rdbms.*} block.
 */
public final class RdbmsDataSources implements AutoCloseable {

  public static final String DEFAULT_PHYSICAL_TENANT_ID = "default";

  private static final Logger LOGGER = LoggerFactory.getLogger(RdbmsDataSources.class);

  private final Map<String, HikariDataSource> dataSources;
  private final Map<String, VendorDatabaseProperties> vendorProperties;

  private RdbmsDataSources(
      final Map<String, HikariDataSource> dataSources,
      final Map<String, VendorDatabaseProperties> vendorProperties) {
    this.dataSources = dataSources;
    this.vendorProperties = vendorProperties;
  }

  public static RdbmsDataSources of(
      final Map<String, Rdbms> physicalTenantConfigs,
      final RdbmsDatabaseIdProvider databaseIdProvider)
      throws IOException {
    final var dataSources = new LinkedHashMap<String, HikariDataSource>();
    final var vendorProperties = new LinkedHashMap<String, VendorDatabaseProperties>();
    for (final var entry : physicalTenantConfigs.entrySet()) {
      final var currentPhysicalTenantId = entry.getKey();
      final var rdbms = entry.getValue();
      try {
        final var ds = buildDataSource(currentPhysicalTenantId, rdbms);
        dataSources.put(currentPhysicalTenantId, ds);
        final var databaseId = databaseIdProvider.getDatabaseId(ds);
        LOGGER.info(
            "Detected databaseId '{}' for physical tenant '{}'",
            databaseId,
            currentPhysicalTenantId);
        vendorProperties.put(
            currentPhysicalTenantId, VendorDatabasePropertiesLoader.load(databaseId));
      } catch (final IOException | RuntimeException e) {
        LOGGER.error(
            "Failed to initialize RDBMS datasource for physical tenant {}",
            currentPhysicalTenantId,
            e);
        dataSources.values().forEach(RdbmsDataSources::closeQuietly);
        throw e;
      }
    }
    return new RdbmsDataSources(dataSources, vendorProperties);
  }

  public DataSource dataSourceFor(final String physicalTenantId) {
    final var ds = dataSources.get(physicalTenantId);
    if (ds == null) {
      throw new IllegalArgumentException(
          "No DataSource configured for physical tenant " + physicalTenantId);
    }
    return ds;
  }

  public VendorDatabaseProperties vendorPropertiesFor(final String physicalTenantId) {
    final var props = vendorProperties.get(physicalTenantId);
    if (props == null) {
      throw new IllegalArgumentException(
          "No VendorDatabaseProperties configured for physical tenant " + physicalTenantId);
    }
    return props;
  }

  @Override
  public void close() {
    dataSources.values().forEach(RdbmsDataSources::closeQuietly);
  }

  private static HikariDataSource buildDataSource(
      final String physicalTenantId, final Rdbms rdbms) {
    final var ds = new HikariDataSource();
    ds.setPoolName("camunda-rdbms-" + physicalTenantId);
    ds.setJdbcUrl(rdbms.getUrl());
    ds.setUsername(rdbms.getUsername());
    ds.setPassword(rdbms.getPassword());
    final var driverClassName = DatabaseDriver.fromJdbcUrl(rdbms.getUrl()).getDriverClassName();
    if (driverClassName != null) {
      ds.setDriverClassName(driverClassName);
    }
    final var pool = rdbms.getConnectionPool();
    ds.setMaximumPoolSize(pool.getMaximumPoolSize());
    ds.setMinimumIdle(pool.getMinimumIdle());
    ds.setConnectionTimeout(pool.getConnectionTimeout().toMillis());
    ds.setIdleTimeout(pool.getIdleTimeout().toMillis());
    ds.setMaxLifetime(pool.getMaxLifetime().toMillis());
    ds.setLeakDetectionThreshold(pool.getLeakDetectionThreshold().toMillis());
    return ds;
  }

  private static void closeQuietly(final HikariDataSource ds) {
    try {
      ds.close();
    } catch (final Exception e) {
      LOGGER.debug("Failed to close RDBMS datasource '{}'", ds.getPoolName(), e);
    }
  }
}
