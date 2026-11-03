/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.rdbms;

import com.zaxxer.hikari.HikariDataSource;
import io.camunda.configuration.data.secondarystorage.Rdbms;
import io.camunda.db.rdbms.config.VendorDatabaseProperties;
import io.camunda.db.rdbms.config.VendorDatabasePropertiesLoader;
import io.camunda.zeebe.util.VisibleForTesting;
import io.camunda.zeebe.util.micrometer.MicrometerUtil;
import io.camunda.zeebe.util.micrometer.PartitionKeyNames;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.jdbc.DatabaseDriver;

/**
 * Per-physical-tenant registry of {@link HikariDataSource} pools and their detected {@link
 * VendorDatabaseProperties}.
 *
 * <p>Each entry is built from a physical-tenant-scoped {@link Rdbms} configuration (URL,
 * credentials, connection-pool tuning). Vendor properties are resolved once at construction time,
 * through {@link RdbmsVendorIdProvider} — from configuration or the JDBC URL where it can be, so
 * that constructing this registry does not depend on any tenant's database being reachable.
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

  private final Map<String, DataSource> dataSources = new LinkedHashMap<>();
  private final Map<String, ReleasablePool> pools = new LinkedHashMap<>();
  private final Map<SharedPoolKey, ReleasablePool> sharedPools = new LinkedHashMap<>();
  private final Map<String, DataSource> unpooledDataSources = new LinkedHashMap<>();
  private final List<MeterRegistry> tenantMeterRegistries = new ArrayList<>();
  private final Map<String, VendorDatabaseProperties> vendorProperties = new LinkedHashMap<>();

  /**
   * HikariCP keeps the connections a thread used in a thread local of that thread. Loaded by the
   * system class loader, as from the distribution's start scripts, it holds them strongly, so a
   * released pool stays reachable from every thread that ever used it. Weak references let it go.
   */
  private static final String HIKARI_WEAK_REFERENCES = "com.zaxxer.hikari.useWeakReferences";

  static {
    if (System.getProperty(HIKARI_WEAK_REFERENCES) == null) {
      System.setProperty(HIKARI_WEAK_REFERENCES, "true");
    }
  }

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
    final var sharedDataSources = result.buildSharedPools(physicalTenantConfigs, meterRegistry);
    for (final var entry : physicalTenantConfigs.entrySet()) {
      final var currentPhysicalTenantId = entry.getKey();
      final var rdbms = entry.getValue();
      try {
        final var tenantMeterRegistry =
            result.registerTenantMeterRegistry(currentPhysicalTenantId, meterRegistry);
        final var shared = sharedDataSources.get(currentPhysicalTenantId);
        if (shared != null) {
          result.dataSources.put(currentPhysicalTenantId, shared);
        } else {
          final var metrics =
              new StablePoolMetrics(poolName(currentPhysicalTenantId), tenantMeterRegistry);
          final var ds =
              new ReleasablePool(() -> buildDataSource(currentPhysicalTenantId, rdbms, metrics));
          result.dataSources.put(currentPhysicalTenantId, ds);
          result.pools.put(currentPhysicalTenantId, ds);
        }
        final var unpooled = new UnpooledDataSource(rdbms);
        result.unpooledDataSources.put(currentPhysicalTenantId, unpooled);
        final var databaseId =
            vendorIdFor(currentPhysicalTenantId, rdbms, unpooled, physicalTenantConfigs.size());
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

  /**
   * The tenant's vendor id, from configuration or its JDBC URL where either settles it, and only
   * otherwise from its database.
   *
   * <p>Reading it from the database is the one step of building a tenant that needs that database
   * to be up, so it is reported before it is attempted rather than after it succeeds — what costs
   * the isolation is the attempt, not its outcome. It is a warning only where there is isolation to
   * lose: on a single-tenant node the tenant has nobody to take down but itself, so the same line
   * goes to debug rather than firing on every start of a deployment that is doing nothing wrong.
   */
  private static String vendorIdFor(
      final String physicalTenantId,
      final Rdbms rdbms,
      final DataSource dataSource,
      final int physicalTenantCount) {
    final var fromConfiguration = RdbmsVendorIdProvider.resolve(physicalTenantId, rdbms);
    if (fromConfiguration.isPresent()) {
      return fromConfiguration.get();
    }

    final var report =
        "The JDBC URL of physical tenant '{}' does not name a database vendor, so it is read from"
            + " the database itself. Set {} so that it resolves from configuration instead: while"
            + " it is read over a connection, this tenant's database being unreachable fails"
            + " startup for every physical tenant on this node.";
    if (physicalTenantCount > 1) {
      LOGGER.warn(report, physicalTenantId, RdbmsVendorIdProvider.VENDOR_ID_PROPERTY);
    } else {
      LOGGER.debug(report, physicalTenantId, RdbmsVendorIdProvider.VENDOR_ID_PROPERTY);
    }

    final Optional<String> detected;
    try {
      detected = RdbmsVendorIdProvider.fromDatabaseProductName(dataSource);
    } catch (final RuntimeException notReached) {
      throw RdbmsVendorIdProvider.unresolvable(
          physicalTenantId,
          rdbms.getUrl(),
          "and connecting to its database to detect it failed",
          notReached);
    }
    return detected.orElseThrow(
        () ->
            RdbmsVendorIdProvider.unresolvable(
                physicalTenantId,
                rdbms.getUrl(),
                "or from the database product name it reports",
                null));
  }

  /**
   * Builds one pool for each group of tenants that share a pool and the same database user, and
   * returns each such tenant's budgeted view of it.
   */
  private Map<String, DataSource> buildSharedPools(
      final Map<String, Rdbms> physicalTenantConfigs, final MeterRegistry meterRegistry) {
    final var groups = new LinkedHashMap<SharedPoolKey, Map<String, Rdbms>>();
    physicalTenantConfigs.forEach(
        (tenantId, rdbms) -> {
          if (rdbms.getConnectionPool().isShared()) {
            groups
                .computeIfAbsent(SharedPoolKey.of(rdbms), ignored -> new LinkedHashMap<>())
                .put(tenantId, rdbms);
          }
        });
    final var byTenant = new LinkedHashMap<String, DataSource>();
    int index = 0;
    for (final var group : groups.entrySet()) {
      final var members = group.getValue();
      final var limits = new LinkedHashMap<String, SharedConnectionBudget.Limits>();
      members.forEach(
          (tenantId, rdbms) ->
              limits.put(
                  tenantId,
                  new SharedConnectionBudget.Limits(
                      rdbms.getConnectionPool().getGuaranteed(),
                      rdbms.getConnectionPool().getMaximumPoolSize())));
      final int burst =
          members.values().stream()
              .mapToInt(r -> r.getConnectionPool().getSharedBurst())
              .max()
              .orElse(0);
      final var budget = SharedConnectionBudget.of(limits, burst);
      final var first = members.values().iterator().next();
      final var name = "shared-" + index++;
      final var registry =
          MicrometerUtil.wrap(
              meterRegistry, Tags.of(PartitionKeyNames.PHYSICAL_TENANT.asString(), name));
      final var sharedMetrics = new StablePoolMetrics(poolName(name), registry);
      final var pool =
          new ReleasablePool(
              () -> {
                final var ds = buildDataSource(name, first, sharedMetrics);
                ds.setMaximumPoolSize(budget.poolSize());
                return ds;
              });
      sharedPools.put(group.getKey(), pool);
      LOGGER.info(
          "Physical tenants {} share the RDBMS pool '{}' of {} connections ({} guaranteed, {} burst)",
          members.keySet(),
          pool.poolName(),
          budget.poolSize(),
          budget.poolSize() - burst,
          burst);
      members.forEach(
          (tenantId, rdbms) ->
              byTenant.put(
                  tenantId,
                  budget.dataSourceFor(
                      tenantId, pool, rdbms.getConnectionPool().getConnectionTimeout())));
    }
    return byTenant;
  }

  private MeterRegistry registerTenantMeterRegistry(
      final String physicalTenantId, final MeterRegistry meterRegistry) {
    final var tenantMeterRegistry =
        MicrometerUtil.wrap(
            meterRegistry, Tags.of(PartitionKeyNames.PHYSICAL_TENANT.asString(), physicalTenantId));
    tenantMeterRegistries.add(tenantMeterRegistry);
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

  /**
   * A data source of the tenant that opens a connection per use, for the work a node does on the
   * tenant's database whether it serves the tenant or not, such as initializing its schema. It
   * leaves the tenant's pool unstarted, so that the pool only holds connections on a node that uses
   * them.
   */
  public DataSource unpooledDataSourceFor(final String physicalTenantId) {
    final var ds = unpooledDataSources.get(physicalTenantId);
    if (ds == null) {
      throw new IllegalArgumentException(
          "No DataSource configured for physical tenant " + physicalTenantId);
    }
    return ds;
  }

  /**
   * Whether the tenant's own pool has been started, and so holds connections to its database. A
   * tenant on a shared pool has none of its own.
   */
  public boolean isPoolStarted(final String physicalTenantId) {
    final var ds = pools.get(physicalTenantId);
    return ds != null && ds.isRunning();
  }

  /**
   * Closes the tenant's own pool and its connections. The pool starts again on the tenant's next
   * connection. A shared pool stays open for the other tenants on it, and closes its idle
   * connections after its idle timeout.
   */
  public void releasePool(final String physicalTenantId) {
    final var ds = pools.get(physicalTenantId);
    if (ds != null) {
      ds.release();
    }
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
    // close each tenant's pool together with its wrapped registry so neither is left dangling
    pools.values().forEach(ReleasablePool::close);
    sharedPools.values().forEach(ReleasablePool::close);
    tenantMeterRegistries.forEach(MicrometerUtil::close);
  }

  private static HikariDataSource buildDataSource(
      final String physicalTenantId, final Rdbms rdbms, final StablePoolMetrics metrics) {
    final var ds = new HikariDataSource();
    ds.setPoolName(poolName(physicalTenantId));
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
    ds.setMetricsTrackerFactory(metrics);
    ds.setAutoCommit(false);
    return ds;
  }

  private static String poolName(final String physicalTenantId) {
    return "camunda-rdbms-" + physicalTenantId;
  }

  private static void enableVendorBatchStatements(
      final Rdbms rdbms, final DatabaseDriver driver, final HikariDataSource ds) {
    if (rdbms.isRewriteBatchedStatements()) {
      final var vendor = RdbmsVendorIdProvider.unwrapVendorDriver(driver, rdbms.getUrl());
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
   * Checks whether the given JDBC URL already explicitly sets the given query parameter, so that an
   * operator's own choice in the connection URL is never silently overridden by a driver-specific
   * property added here.
   */
  private static boolean urlSpecifiesProperty(final String url, final String propertyName) {
    return Pattern.compile("[?&]" + Pattern.quote(propertyName) + "=", Pattern.CASE_INSENSITIVE)
        .matcher(url)
        .find();
  }

  /** Tenants share a pool only if their connections are interchangeable. */
  private record SharedPoolKey(String url, String username, String password) {
    static SharedPoolKey of(final Rdbms rdbms) {
      return new SharedPoolKey(rdbms.getUrl(), rdbms.getUsername(), rdbms.getPassword());
    }

    @Override
    public String toString() {
      return "SharedPoolKey[url=%s, username=%s]".formatted(url, username);
    }
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
