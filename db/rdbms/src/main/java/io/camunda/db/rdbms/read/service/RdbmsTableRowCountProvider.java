/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.service;

import com.github.benmanes.caffeine.cache.AsyncLoadingCache;
import com.github.benmanes.caffeine.cache.CacheLoader;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.camunda.db.rdbms.RdbmsTableNames;
import io.camunda.db.rdbms.sql.TableMetricsMapper;
import java.time.Duration;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provides cached row counts for the RDBMS tables of a single physical tenant. Each physical tenant
 * has its own database (backed by its own {@link TableMetricsMapper}) and its own cache duration,
 * so one provider is created per physical tenant. Row counts are cached to avoid performance impact
 * on the database.
 *
 * <p>{@link #getRowCount(String)} never blocks: it always returns immediately, either the last
 * successfully loaded value or {@link #UNKNOWN_ROW_COUNT} if none has completed loading yet.
 */
public class RdbmsTableRowCountProvider {

  private static final Logger LOG = LoggerFactory.getLogger(RdbmsTableRowCountProvider.class);

  /**
   * Returned for an unknown table name, a failed row count query, or a table whose first load
   * hasn't completed yet.
   */
  private static final long UNKNOWN_ROW_COUNT = -1;

  private final TableMetricsMapper tableMetricsMapper;
  private final AsyncLoadingCache<String, Long> rowCountCache;

  /**
   * @param tableMetricsMapper the mapper backing the physical tenant's database
   * @param cacheDuration how long a row count is served before a refresh is triggered
   * @param executor runs the (potentially slow or blocking) database reload off the caller's thread
   */
  public RdbmsTableRowCountProvider(
      final TableMetricsMapper tableMetricsMapper,
      final Duration cacheDuration,
      final Executor executor) {
    this.tableMetricsMapper = tableMetricsMapper;
    rowCountCache =
        Caffeine.newBuilder()
            .refreshAfterWrite(cacheDuration)
            .executor(executor)
            .buildAsync(
                new CacheLoader<>() {
                  @Override
                  public Long load(final String tableName) {
                    return fetchRowCount(tableName);
                  }

                  @Override
                  public Long reload(final String tableName, final Long oldValue) throws Exception {
                    if (!isAllowedTableName(tableName)) {
                      LOG.warn("Attempted to reload row count for unknown table: {}", tableName);
                      return oldValue;
                    }
                    return tableMetricsMapper.countTableRows(tableName);
                  }
                });
  }

  /**
   * Gets the row count for a specific table, using the cache if available.
   *
   * @param tableName the name of the table
   * @return the row count; {@link #UNKNOWN_ROW_COUNT} if the table is unknown or its first load
   *     hasn't completed yet or failed; the previously cached value if a subsequent refresh failed
   */
  public long getRowCount(final String tableName) {
    if (!isAllowedTableName(tableName)) {
      LOG.warn("Attempted to get row count for unknown table: {}", tableName);
      return UNKNOWN_ROW_COUNT;
    }

    try {
      return rowCountCache.get(tableName).getNow(UNKNOWN_ROW_COUNT);
    } catch (final Exception e) {
      LOG.warn("Failed to read cached row count for table {}", tableName, e);
      return UNKNOWN_ROW_COUNT;
    }
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

  private Long fetchRowCount(final String tableName) {
    // Only fetch row counts for allowed table names to prevent SQL injection
    if (!isAllowedTableName(tableName)) {
      LOG.warn("Attempted to fetch row count for unknown table: {}", tableName);
      return UNKNOWN_ROW_COUNT;
    }

    try {
      return tableMetricsMapper.countTableRows(tableName);
    } catch (final Exception e) {
      LOG.warn("Failed to fetch row count for table {}", tableName, e);
      return UNKNOWN_ROW_COUNT;
    }
  }
}
