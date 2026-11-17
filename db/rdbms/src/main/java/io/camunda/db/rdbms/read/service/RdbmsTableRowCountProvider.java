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
import io.camunda.db.rdbms.config.VendorDatabaseProperties;
import io.camunda.db.rdbms.sql.TableMetricsMapper;
import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provides cached row counts for the RDBMS tables of a single physical tenant. Each physical tenant
 * has its own database (backed by its own {@link TableMetricsMapper}) and its own cache duration,
 * so one provider is created per physical tenant. All tables are loaded and refreshed together as a
 * single cache entry.
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

  private static final String ALL_TABLES = "*";

  private final TableMetricsMapper tableMetricsMapper;
  private final boolean usesCatalogRowCountStatistics;

  private final Map<String, String> tableNameBySearchName;
  private final List<String> searchNames;
  private final AsyncLoadingCache<String, Map<String, Long>> rowCountCache;

  /**
   * @param tableMetricsMapper the mapper backing the physical tenant's database
   * @param vendorDatabaseProperties the tenant's vendor, used to fold table identifiers and to pick
   *     the row count strategy
   * @param prefix the tenant's configured table prefix
   * @param cacheDuration how long the row counts are served before a refresh is triggered
   * @param executor runs the (potentially slow or blocking) database reload off the caller's thread
   */
  public RdbmsTableRowCountProvider(
      final TableMetricsMapper tableMetricsMapper,
      final VendorDatabaseProperties vendorDatabaseProperties,
      final String prefix,
      final Duration cacheDuration,
      final Executor executor) {
    this.tableMetricsMapper = tableMetricsMapper;
    usesCatalogRowCountStatistics = vendorDatabaseProperties.usesCatalogRowCountStatistics();

    final var trimmedPrefix = StringUtils.trimToEmpty(prefix);
    final var bySearchName = new LinkedHashMap<String, String>();
    for (final String tableName : RdbmsTableNames.TABLE_NAMES) {
      final var searchName =
          vendorDatabaseProperties.foldTableIdentifier(trimmedPrefix + tableName);
      bySearchName.put(searchName, tableName);
    }
    tableNameBySearchName = Map.copyOf(bySearchName);
    searchNames = List.copyOf(bySearchName.keySet());

    rowCountCache =
        Caffeine.newBuilder()
            .refreshAfterWrite(cacheDuration)
            .executor(executor)
            .buildAsync(
                new CacheLoader<>() {
                  @Override
                  public Map<String, Long> load(final String key) throws Exception {
                    // Rethrown so Caffeine drops the failed future instead of caching an empty
                    // result for a whole cacheDuration.
                    try {
                      return fetchAllRowCounts(Map.of());
                    } catch (final Exception e) {
                      LOG.warn("Failed to fetch RDBMS table row counts: {}", e.getMessage());
                      throw e;
                    }
                  }

                  @Override
                  public Map<String, Long> reload(
                      final String key, final Map<String, Long> oldValue) throws Exception {
                    // Not caught: Caffeine keeps oldValue cached when reload() throws.
                    return fetchAllRowCounts(oldValue);
                  }
                });
  }

  /**
   * Gets the row count for a specific table, using the cache if available.
   *
   * @param tableName the name of the table
   * @return the row count; {@link #UNKNOWN_ROW_COUNT} if the table is unknown or the tenant's first
   *     load hasn't completed yet or failed; the previously cached value if a subsequent refresh
   *     failed
   */
  public long getRowCount(final String tableName) {
    if (!isAllowedTableName(tableName)) {
      LOG.warn("Attempted to get row count for unknown table: {}", tableName);
      return UNKNOWN_ROW_COUNT;
    }

    try {
      return rowCountCache
          .get(ALL_TABLES)
          .getNow(Map.of())
          .getOrDefault(tableName, UNKNOWN_ROW_COUNT);
    } catch (final Exception e) {
      // load() already logged this in full.
      LOG.warn("Failed to read cached row counts for table {}: {}", tableName, e.getMessage());
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

  private Map<String, Long> fetchAllRowCounts(final Map<String, Long> previous) {
    return usesCatalogRowCountStatistics
        ? fetchAllRowCountsBatched()
        : fetchAllRowCountsOneByOne(previous);
  }

  private Map<String, Long> fetchAllRowCountsBatched() {
    final var rows = tableMetricsMapper.countTableRows(searchNames);
    final var result = new HashMap<String, Long>();
    for (final var row : rows) {
      final var tableName = tableNameBySearchName.get(row.tableName());
      if (tableName == null) {
        LOG.warn("Row count query returned an unrecognized table identifier: {}", row.tableName());
        continue;
      }
      result.put(tableName, row.rowCount());
    }
    if (result.size() != searchNames.size()) {
      LOG.warn(
          "Row counts missing for {} of {} known tables",
          searchNames.size() - result.size(),
          searchNames.size());
    }
    return Map.copyOf(result);
  }

  /**
   * Fetches each table's row count independently, tolerating a single table's failure instead of
   * failing the whole batch. Starting from {@code previous} keeps a failed table's last reported
   * count rather than dropping it to {@link #UNKNOWN_ROW_COUNT}; {@code previous} is empty on the
   * first load, so a table that never loaded successfully stays absent.
   */
  private Map<String, Long> fetchAllRowCountsOneByOne(final Map<String, Long> previous) {
    final var result = new HashMap<>(previous);
    for (final var searchName : searchNames) {
      final var tableName = tableNameBySearchName.get(searchName);
      try {
        result.put(tableName, tableMetricsMapper.countSingleTableRows(searchName));
      } catch (final Exception e) {
        LOG.warn("Failed to fetch row count for table {}: {}", tableName, e.getMessage());
      }
    }
    return Map.copyOf(result);
  }
}
