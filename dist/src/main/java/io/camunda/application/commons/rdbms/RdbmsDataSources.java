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
import io.camunda.zeebe.util.VisibleForTesting;
import io.camunda.zeebe.util.micrometer.MicrometerUtil;
import io.camunda.zeebe.util.micrometer.PartitionKeyNames;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import javax.sql.DataSource;
import org.apache.ibatis.mapping.DatabaseIdProvider;
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

  private static final Logger LOGGER = LoggerFactory.getLogger(RdbmsDataSources.class);
  private static final String MYSQL_REWRITE_BATCHED_STATEMENTS_PROPERTY =
      "rewriteBatchedStatements";
  private static final String MARIADB_USE_BULK_STMTS_PROPERTY = "useBulkStmts";

  private final Map<String, HikariDataSource> dataSources = new LinkedHashMap<>();
  private final Map<String, MeterRegistry> tenantMeterRegistries = new LinkedHashMap<>();
  private final Map<String, VendorDatabaseProperties> vendorProperties = new LinkedHashMap<>();
  private final Map<String, DatabaseIdProvider> databaseIdProviders = new LinkedHashMap<>();

  private RdbmsDataSources() {}

  /**
   * @param meterRegistry the cluster-wide registry each physical tenant's HikariCP metrics are
   *     forwarded to. Each tenant gets its own {@link MicrometerUtil#wrap wrapped registry} tagging
   *     metrics with {@link PartitionKeyNames#PHYSICAL_TENANT}, consistent with the other
   *     physical-tenant-scoped RDBMS metrics (e.g. {@code
   *     PhysicalTenantsRdbmsTableRowCountMetrics}).
   */
  public static RdbmsDataSources of(
      final Map<String, Rdbms> physicalTenantConfigs, final MeterRegistry meterRegistry)
      throws IOException {
    final var result = new RdbmsDataSources();
    for (final var entry : physicalTenantConfigs.entrySet()) {
      final var currentPhysicalTenantId = entry.getKey();
      final var rdbms = entry.getValue();
      try {
        final var tenantMeterRegistry =
            result.registerTenantMeterRegistry(currentPhysicalTenantId, meterRegistry);
        final var ds = buildDataSource(currentPhysicalTenantId, rdbms, tenantMeterRegistry);
        result.dataSources.put(currentPhysicalTenantId, ds);
        final var databaseIdProvider = new RdbmsDatabaseIdProvider(rdbms.getDatabaseVendorId());
        result.databaseIdProviders.put(currentPhysicalTenantId, databaseIdProvider);
        final var databaseId = databaseIdProvider.getDatabaseId(ds);
        LOGGER.info(
            "Detected databaseId '{}' for physical tenant '{}'",
            databaseId,
            currentPhysicalTenantId);
        result.vendorProperties.put(
            currentPhysicalTenantId, VendorDatabasePropertiesLoader.load(databaseId));
      } catch (final IOException | RuntimeException e) {
        LOGGER.error(
            "Failed to initialize RDBMS datasource for physical tenant {}",
            currentPhysicalTenantId,
            e);
        result.close();
        throw e;
      }
    }
    return result;
  }

  private MeterRegistry registerTenantMeterRegistry(
      final String physicalTenantId, final MeterRegistry meterRegistry) {
    final var tenantMeterRegistry =
        MicrometerUtil.wrap(
            meterRegistry, Tags.of(PartitionKeyNames.PHYSICAL_TENANT.asString(), physicalTenantId));
    tenantMeterRegistries.put(physicalTenantId, tenantMeterRegistry);
    return tenantMeterRegistry;
  }

  public Set<String> physicalTenantIds() {
    return dataSources.keySet();
  }

  public DataSource dataSourceFor(final String physicalTenantId) {
    final var ds = dataSources.get(physicalTenantId);
    if (ds == null) {
      throw new IllegalArgumentException(
          "No DataSource configured for physical tenant " + physicalTenantId);
    }
    return ds;
  }

  public Map<String, DataSource> dataSources() {
    return Map.copyOf(dataSources);
  }

  public VendorDatabaseProperties vendorPropertiesFor(final String physicalTenantId) {
    final var props = vendorProperties.get(physicalTenantId);
    if (props == null) {
      throw new IllegalArgumentException(
          "No VendorDatabaseProperties configured for physical tenant " + physicalTenantId);
    }
    return props;
  }

  public DatabaseIdProvider databaseIdProviderFor(final String physicalTenantId) {
    final var databaseIdProvider = databaseIdProviders.get(physicalTenantId);
    if (databaseIdProvider == null) {
      throw new IllegalArgumentException(
          "No DatabaseIdProvider configured for physical tenant " + physicalTenantId);
    }
    return databaseIdProvider;
  }

  @Override
  public void close() {
    // close each tenant's pool together with its wrapped registry so neither is left dangling
    dataSources.forEach(
        (tenantId, ds) -> {
          closeQuietly(ds);
          MicrometerUtil.close(tenantMeterRegistries.get(tenantId));
        });
  }

  private static HikariDataSource buildDataSource(
      final String physicalTenantId, final Rdbms rdbms, final MeterRegistry meterRegistry) {
    final var ds = new HikariDataSource();
    ds.setPoolName("camunda-rdbms-" + physicalTenantId);
    ds.setJdbcUrl(rdbms.getUrl());
    ds.setUsername(rdbms.getUsername());
    ds.setPassword(rdbms.getPassword());
    final var driver = DatabaseDriver.fromJdbcUrl(rdbms.getUrl());
    final var driverClassName = driver.getDriverClassName();
    if (driverClassName != null) {
      ds.setDriverClassName(driverClassName);
    }
    enableVendorBatchStatements(rdbms, driver, ds);

    final var pool = rdbms.getConnectionPool();
    ds.setMaximumPoolSize(pool.getMaximumPoolSize());
    ds.setMinimumIdle(pool.getMinimumIdle());
    ds.setConnectionTimeout(pool.getConnectionTimeout().toMillis());
    ds.setIdleTimeout(pool.getIdleTimeout().toMillis());
    ds.setMaxLifetime(pool.getMaxLifetime().toMillis());
    ds.setLeakDetectionThreshold(pool.getLeakDetectionThreshold().toMillis());
    ds.setKeepaliveTime(pool.getKeepaliveTime().toMillis());
    ds.setValidationTimeout(pool.getValidationTimeout().toMillis());
    ds.setMetricRegistry(meterRegistry);
    ds.setAutoCommit(false);
    return ds;
  }

  private static void enableVendorBatchStatements(
      final Rdbms rdbms, final DatabaseDriver driver, final HikariDataSource ds) {
    if (rdbms.isRewriteBatchedStatements()) {
      final var vendor = unwrapVendorDriver(driver, rdbms.getUrl());
      if (vendor == DatabaseDriver.MYSQL
          && !urlSpecifiesProperty(rdbms.getUrl(), MYSQL_REWRITE_BATCHED_STATEMENTS_PROPERTY)) {
        ds.addDataSourceProperty(MYSQL_REWRITE_BATCHED_STATEMENTS_PROPERTY, "true");
      } else if (vendor == DatabaseDriver.MARIADB
          && !urlSpecifiesProperty(rdbms.getUrl(), MARIADB_USE_BULK_STMTS_PROPERTY)) {
        ds.addDataSourceProperty(MARIADB_USE_BULK_STMTS_PROPERTY, "true");
      }
    }
  }

  /**
   * Resolves the underlying database vendor for driver-specific connection properties, unwrapping
   * the AWS Advanced JDBC Wrapper's {@code jdbc:aws-wrapper:<vendor>://...} URL scheme. Without
   * this, {@link DatabaseDriver#fromJdbcUrl} resolves such URLs to {@link
   * DatabaseDriver#AWS_WRAPPER} rather than the wrapped vendor, so vendor-specific properties like
   * batch statement rewriting would never be applied for Aurora failover-aware connections.
   */
  private static DatabaseDriver unwrapVendorDriver(final DatabaseDriver driver, final String url) {
    if (driver != DatabaseDriver.AWS_WRAPPER) {
      return driver;
    }
    return DatabaseDriver.fromJdbcUrl(url.replaceFirst("(?i)^jdbc:aws-wrapper:", "jdbc:"));
  }

  /**
   * Checks whether the given JDBC URL already explicitly sets the given query parameter, so that an
   * operator's own choice in the connection URL is never silently overridden by a driver-specific
   * property added here.
   */
  private static boolean urlSpecifiesProperty(final String url, final String propertyName) {
    return Pattern.compile("[?&]" + Pattern.quote(propertyName) + "=", Pattern.CASE_INSENSITIVE)
        .matcher(url)
        .find();
  }

  @VisibleForTesting
  static void closeQuietly(final HikariDataSource ds) {
    try {
      ds.close();
    } catch (final Exception e) {
      LOGGER.debug("Failed to close RDBMS datasource '{}'", ds.getPoolName(), e);
    }
  }
}
