/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.camunda.db.rdbms.RdbmsTableNames;
import io.camunda.db.rdbms.sql.TableMetricsMapper;
import io.camunda.zeebe.util.VisibleForTesting;
import io.camunda.zeebe.util.micrometer.PartitionKeyNames;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import java.time.Duration;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provides metrics for the number of rows in each RDBMS table, per physical tenant. Each physical
 * tenant has its own database, so a separate gauge is registered per (physical tenant, table) and
 * tagged with the {@code physicalTenant} label, consistent with the other partition-scoped RDBMS
 * metrics. The row counts are cached to avoid performance impact on the database.
 */
public class RdbmsTableRowCountMetrics implements MeterBinder {

  private static final Logger LOG = LoggerFactory.getLogger(RdbmsTableRowCountMetrics.class);
  private static final String NAMESPACE = "zeebe.rdbms";
  private static final String METRIC_NAME = NAMESPACE + ".table.row.count";

  private final Map<String, TableMetricsMapper> tableMetricsMappers;
  private final Cache<CacheKey, Long> rowCountCache;

  /**
   * @param tableMetricsMappers the table metrics mapper for each physical tenant, keyed by physical
   *     tenant id
   * @param cacheDuration how long row counts are cached before being fetched again
   */
  public RdbmsTableRowCountMetrics(
      final Map<String, TableMetricsMapper> tableMetricsMappers, final Duration cacheDuration) {
    this.tableMetricsMappers = Map.copyOf(tableMetricsMappers);
    rowCountCache = Caffeine.newBuilder().expireAfterWrite(cacheDuration).build();
  }

  @Override
  public void bindTo(final MeterRegistry registry) {
    tableMetricsMappers.forEach(
        (physicalTenantId, tableMetricsMapper) -> {
          for (final String tableName : RdbmsTableNames.TABLE_NAMES) {
            Gauge.builder(
                    METRIC_NAME, () -> getRowCount(tableMetricsMapper, physicalTenantId, tableName))
                .description("Number of rows in the RDBMS table")
                .tag(PartitionKeyNames.PHYSICAL_TENANT.asString(), physicalTenantId)
                .tag("table", tableName)
                .register(registry);
          }
        });
  }

  /**
   * Gets the row count for a specific table of a physical tenant, using the cache if available.
   *
   * @param tableMetricsMapper the mapper backing the physical tenant's database
   * @param physicalTenantId the id of the physical tenant that owns the table
   * @param tableName the name of the table
   * @return the number of rows in the table, or -1 if the table is not in the allowed list
   */
  @VisibleForTesting
  long getRowCount(
      final TableMetricsMapper tableMetricsMapper,
      final String physicalTenantId,
      final String tableName) {
    if (!isAllowedTableName(tableName)) {
      LOG.warn("Attempted to get row count for unknown table: {}", tableName);
      return -1;
    }

    return rowCountCache.get(
        new CacheKey(physicalTenantId, tableName),
        key -> fetchRowCount(tableMetricsMapper, key.tableName()));
  }

  /**
   * Validates that the table name is in the allowed list of known tables.
   *
   * @param tableName the name of the table to validate
   * @return true if the table name is allowed, false otherwise
   */
  private boolean isAllowedTableName(final String tableName) {
    return RdbmsTableNames.TABLE_NAMES.contains(tableName);
  }

  private long fetchRowCount(final TableMetricsMapper tableMetricsMapper, final String tableName) {
    // Only fetch row counts for allowed table names to prevent SQL injection
    if (!isAllowedTableName(tableName)) {
      LOG.warn("Attempted to fetch row count for unknown table: {}", tableName);
      return -1;
    }

    try {
      return tableMetricsMapper.countTableRows(tableName);
    } catch (final Exception e) {
      LOG.warn("Failed to fetch row count for table {}", tableName, e);
      return -1;
    }
  }

  private record CacheKey(String physicalTenantId, String tableName) {}
}
