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
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provides metrics for the number of rows in each RDBMS table. The row counts are cached to avoid
 * performance impact on the database.
 */
public class RdbmsTableRowCountMetrics implements MeterBinder {

  private static final Logger LOG = LoggerFactory.getLogger(RdbmsTableRowCountMetrics.class);
  private static final String NAMESPACE = "zeebe.rdbms";
  private static final String METRIC_NAME = NAMESPACE + ".table.row.count";

  private final TableMetricsMapper tableMetricsMapper;
  private final Cache<String, Long> rowCountCache;

  public RdbmsTableRowCountMetrics(
      final TableMetricsMapper tableMetricsMapper, final Duration cacheDuration) {
    this.tableMetricsMapper = tableMetricsMapper;
    this.rowCountCache = Caffeine.newBuilder().expireAfterWrite(cacheDuration).build();
  }

  @Override
  public void bindTo(final MeterRegistry registry) {
    for (final String tableName : RdbmsTableNames.TABLE_NAMES) {
      Gauge.builder(METRIC_NAME, () -> getRowCount(tableName))
          .description("Number of rows in the RDBMS table")
          .tag("table", tableName)
          .register(registry);
    }
  }

  /**
   * Gets the row count for a specific table, using the cache if available.
   *
   * @param tableName the name of the table
   * @return the number of rows in the table, or -1 if the table is not in the allowed list
   */
  public long getRowCount(final String tableName) {
    if (!isAllowedTableName(tableName)) {
      LOG.warn("Attempted to get row count for unknown table: {}", tableName);
      return -1;
    }

    return rowCountCache.get(tableName, this::fetchRowCount);
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

  private long fetchRowCount(final String tableName) {
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
}
