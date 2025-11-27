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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
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
   * @return the number of rows in the table
   */
  public long getRowCount(final String tableName) {
    final var cachedRowCount = cachedRowCounts.get(tableName);
    if (cachedRowCount != null) {
      return (long) cachedRowCount.getValue();
    }
    return fetchRowCount(tableName);
  }

  private long fetchRowCount(final String tableName) {
    try {
      return tableMetricsMapper.countTableRows(tableName);
    } catch (final Exception e) {
      LOG.warn("Failed to fetch row count for table {}", tableName, e);
      return -1;
    }
  }

  /** Holds a cached row count value with expiration tracking. */
  private final class CachedRowCount {
    private final String tableName;
    private final AtomicLong cachedValue = new AtomicLong(-1);
    private volatile long lastUpdateTimeMs = 0;

    private CachedRowCount(final String tableName) {
      this.tableName = tableName;
    }

    public double getValue() {
      final long currentTimeMs = System.currentTimeMillis();
      if (currentTimeMs - lastUpdateTimeMs > cacheDuration.toMillis()) {
        final long newValue = fetchRowCount(tableName);
        if (newValue >= 0) {
          cachedValue.set(newValue);
          lastUpdateTimeMs = currentTimeMs;
        }
      }
      return cachedValue.get();
    }
  }
}
