/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.service;

import io.camunda.db.rdbms.RdbmsTableNames;
import io.camunda.db.rdbms.sql.TableMetricsMapper;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
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
  private static final Duration DEFAULT_CACHE_DURATION = Duration.ofMinutes(1);
  private static final Set<String> ALLOWED_TABLE_NAMES = Set.copyOf(RdbmsTableNames.TABLE_NAMES);

  private final TableMetricsMapper tableMetricsMapper;
  private final Duration cacheDuration;
  private final Map<String, CachedRowCount> cachedRowCounts = new ConcurrentHashMap<>();

  public RdbmsTableRowCountMetrics(final TableMetricsMapper tableMetricsMapper) {
    this(tableMetricsMapper, DEFAULT_CACHE_DURATION);
  }

  public RdbmsTableRowCountMetrics(
      final TableMetricsMapper tableMetricsMapper, final Duration cacheDuration) {
    this.tableMetricsMapper = tableMetricsMapper;
    this.cacheDuration = cacheDuration;
  }

  @Override
  public void bindTo(final MeterRegistry registry) {
    for (final String tableName : RdbmsTableNames.TABLE_NAMES) {
      final var cachedRowCount = new CachedRowCount(tableName);
      cachedRowCounts.put(tableName, cachedRowCount);

      Gauge.builder(METRIC_NAME, cachedRowCount, CachedRowCount::getValue)
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

    final var cachedRowCount = cachedRowCounts.get(tableName);
    if (cachedRowCount != null) {
      return (long) cachedRowCount.getValue();
    }
    return fetchRowCount(tableName);
  }

  /**
   * Validates that the table name is in the allowed list of known tables.
   *
   * @param tableName the name of the table to validate
   * @return true if the table name is allowed, false otherwise
   */
  private boolean isAllowedTableName(final String tableName) {
    return ALLOWED_TABLE_NAMES.contains(tableName);
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

  /** Holds a cached row count value with expiration tracking. Thread-safe implementation. */
  private final class CachedRowCount {
    private final String tableName;
    private final Object lock = new Object();
    private long cachedValue = -1;
    private long lastUpdateTimeMs = 0;

    private CachedRowCount(final String tableName) {
      this.tableName = tableName;
    }

    public double getValue() {
      final long currentTimeMs = System.currentTimeMillis();

      synchronized (lock) {
        if (currentTimeMs - lastUpdateTimeMs > cacheDuration.toMillis()) {
          final long newValue = fetchRowCount(tableName);
          if (newValue >= 0) {
            cachedValue = newValue;
            lastUpdateTimeMs = currentTimeMs;
          }
        }
        return cachedValue;
      }
    }
  }
}
