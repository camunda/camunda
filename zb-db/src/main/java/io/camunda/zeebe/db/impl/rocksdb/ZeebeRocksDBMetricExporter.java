/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.db.impl.rocksdb;

import io.camunda.zeebe.db.ZeebeDb;
import io.prometheus.client.Gauge;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import org.rocksdb.TickerType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** This class exports metrics for a RocksDB instance to Prometheus. */
public final class ZeebeRocksDBMetricExporter<ColumnFamilyType extends Enum<ColumnFamilyType>> {

  private static final Logger LOG =
      LoggerFactory.getLogger(ZeebeRocksDBMetricExporter.class.getName());

  private static final String PARTITION = "partition";
  private static final String ZEEBE_NAMESPACE = "zeebe";

  private static final String MEMORY_METRICS_HELP =
      "Everything which might be related to current memory consumption of RocksDB per column family and partition";
  private static final String MEMORY_METRICS_PREFIX = "rocksdb_memory";
  private static final RocksDBMetric[] MEMORY_METRICS = {
    new RocksDBMetric(
        "rocksdb.cur-size-all-mem-tables", MEMORY_METRICS_PREFIX, MEMORY_METRICS_HELP),
    new RocksDBMetric(
        "rocksdb.cur-size-active-mem-table", MEMORY_METRICS_PREFIX, MEMORY_METRICS_HELP),
    new RocksDBMetric("rocksdb.size-all-mem-tables", MEMORY_METRICS_PREFIX, MEMORY_METRICS_HELP),
    new RocksDBMetric("rocksdb.block-cache-usage", MEMORY_METRICS_PREFIX, MEMORY_METRICS_HELP),
    new RocksDBMetric("rocksdb.block-cache-capacity", MEMORY_METRICS_PREFIX, MEMORY_METRICS_HELP),
    new RocksDBMetric(
        "rocksdb.block-cache-pinned-usage", MEMORY_METRICS_PREFIX, MEMORY_METRICS_HELP),
    new RocksDBMetric(
        "rocksdb.estimate-table-readers-mem", MEMORY_METRICS_PREFIX, MEMORY_METRICS_HELP),
  };

  private static final String SST_METRICS_HELP =
      "Everything which is related to SST files in RocksDB per column family and partition";
  private static final String SST_METRICS_PREFIX = "rocksdb_sst";
  private static final RocksDBMetric[] SST_METRICS = {
    new RocksDBMetric("rocksdb.total-sst-files-size", SST_METRICS_PREFIX, SST_METRICS_HELP),
    new RocksDBMetric("rocksdb.live-sst-files-size", SST_METRICS_PREFIX, SST_METRICS_HELP),
  };

  private static final String LIVE_METRICS_HELP =
      "Other estimated properties based on entries in RocksDb per column family and partition";
  private static final String LIVE_METRICS_PREFIX = "rocksdb_live";
  private static final RocksDBMetric[] LIVE_METRICS = {
    new RocksDBMetric("rocksdb.num-entries-imm-mem-tables", LIVE_METRICS_PREFIX, LIVE_METRICS_HELP),
    new RocksDBMetric("rocksdb.estimate-num-keys", LIVE_METRICS_PREFIX, LIVE_METRICS_HELP),
    new RocksDBMetric("rocksdb.estimate-live-data-size", LIVE_METRICS_PREFIX, LIVE_METRICS_HELP),
  };

  private static final String WRITE_METRICS_HELP =
      "Properties related to writes, flushes and compactions for RocksDb per column family and partition";
  private static final String WRITE_METRICS_PREFIX = "rocksdb_writes";

  private static final RocksDBMetric[] WRITE_METRICS = {
    new RocksDBMetric("rocksdb.is-write-stopped", WRITE_METRICS_PREFIX, WRITE_METRICS_HELP),
    new RocksDBMetric(
        "rocksdb.actual-delayed-write-rate", WRITE_METRICS_PREFIX, WRITE_METRICS_HELP),
    new RocksDBMetric("rocksdb.mem-table-flush-pending", WRITE_METRICS_PREFIX, WRITE_METRICS_HELP),
    new RocksDBMetric("rocksdb.num-running-flushes", WRITE_METRICS_PREFIX, WRITE_METRICS_HELP),
    new RocksDBMetric("rocksdb.num-running-compactions", WRITE_METRICS_PREFIX, WRITE_METRICS_HELP),
  };

  private static final Gauge MEM_TABLE_HIT_RATIO =
      Gauge.build()
          .namespace(ZEEBE_NAMESPACE)
          .name("rocksdb_mem_table_hit_ratio")
          .help("MEM_TABLE_HIT_RATIO")
          .labelNames(PARTITION)
          .register();

  private static final Gauge BLOCK_DATA_CACHE_HIT_RATIO =
      Gauge.build()
          .namespace(ZEEBE_NAMESPACE)
          .name("rocksdb_block_data_cache_hit_ratio")
          .help("BLOCK_DATA_CACHE_HIT_RATIO")
          .labelNames(PARTITION)
          .register();

  private static final Gauge BLOCK_CACHE_INDEX_HIT_RATIO =
      Gauge.build()
          .namespace(ZEEBE_NAMESPACE)
          .name("rocksdb_block_cache_index_hit_ratio")
          .help("BLOCK_CACHE_INDEX_HIT_RATIO")
          .labelNames(PARTITION)
          .register();

  private static final Gauge BLOCK_CACHE_FILTER_HIT_RATIO =
      Gauge.build()
          .namespace(ZEEBE_NAMESPACE)
          .name("rocksdb_block_cache_filter_hit_ratio")
          .help("BLOCK_CACHE_FILTER_HIT_RATIO")
          .labelNames(PARTITION)
          .register();

  private static final Gauge L0_HIT_COUNT =
      Gauge.build()
          .namespace(ZEEBE_NAMESPACE)
          .name("rocksdb_l0_hit_count")
          .help("L0 Hit Count")
          .labelNames(PARTITION)
          .register();

  private static final Gauge L1_HIT_COUNT =
      Gauge.build()
          .namespace(ZEEBE_NAMESPACE)
          .name("rocksdb_l1_hit_count")
          .help("L1 Hit Count")
          .labelNames(PARTITION)
          .register();

  private static final Gauge L2_AND_UP_HIT_COUNT =
      Gauge.build()
          .namespace(ZEEBE_NAMESPACE)
          .name("rocksdb_l2_and_up_hit_count")
          .help("L2 and up Hit Count")
          .labelNames(PARTITION)
          .register();

  private final String partition;
  private final Supplier<ZeebeDb<ColumnFamilyType>> databaseSupplier;

  public ZeebeRocksDBMetricExporter(
      final String partition, final Supplier<ZeebeDb<ColumnFamilyType>> databaseSupplier) {
    this.partition = Objects.requireNonNull(partition);
    this.databaseSupplier = databaseSupplier;
  }

  public void exportStatistics() {
    final var database = databaseSupplier.get();

    if (database == null) {
      return;
    }

    final var memtableHits = database.getStatistics(TickerType.MEMTABLE_HIT);
    final var memtableMisses = database.getStatistics(TickerType.MEMTABLE_MISS);
    final var memtableHitRatio = computeHitRatio(memtableHits, memtableMisses);
    MEM_TABLE_HIT_RATIO.labels(partition).set(memtableHitRatio);

    final var blockCacheDataHits = database.getStatistics(TickerType.BLOCK_CACHE_DATA_HIT);
    final var blockCacheDataMisses = database.getStatistics(TickerType.BLOCK_CACHE_DATA_MISS);
    final var blockCacheDataHitRatio = computeHitRatio(blockCacheDataHits, blockCacheDataMisses);
    BLOCK_DATA_CACHE_HIT_RATIO.labels(partition).set(blockCacheDataHitRatio);

    final var blockCacheIndexHits = database.getStatistics(TickerType.BLOCK_CACHE_INDEX_HIT);
    final var blockCacheIndexMisses = database.getStatistics(TickerType.BLOCK_CACHE_INDEX_MISS);
    final var blockCacheIndexHitRatio = computeHitRatio(blockCacheIndexHits, blockCacheIndexMisses);
    BLOCK_CACHE_INDEX_HIT_RATIO.labels(partition).set(blockCacheIndexHitRatio);

    final var blockCacheFilterHits = database.getStatistics(TickerType.BLOCK_CACHE_FILTER_HIT);
    final var blockCacheFilterMisses = database.getStatistics(TickerType.BLOCK_CACHE_FILTER_MISS);
    final var blockCacheFilterHitRatio =
        computeHitRatio(blockCacheFilterHits, blockCacheFilterMisses);
    BLOCK_CACHE_FILTER_HIT_RATIO.labels(partition).set(blockCacheFilterHitRatio);

    database
        .getStatistics(TickerType.GET_HIT_L0)
        .ifPresent((v) -> L0_HIT_COUNT.labels(partition).set(v));
    database
        .getStatistics(TickerType.GET_HIT_L1)
        .ifPresent((v) -> L1_HIT_COUNT.labels(partition).set(v));
    database
        .getStatistics(TickerType.GET_HIT_L2_AND_UP)
        .ifPresent((v) -> L2_AND_UP_HIT_COUNT.labels(partition).set(v));
  }

  private double computeHitRatio(final Optional<Long> hits, final Optional<Long> misses) {
    if (!(hits.isPresent() && misses.isPresent()) || hits.get() == 0) {
      return 0;
    }
    return (double) hits.get() / (hits.get() + misses.get());
  }

  public void exportMetrics() {
    final long startTime = System.currentTimeMillis();
    exportMetrics(MEMORY_METRICS);
    exportMetrics(LIVE_METRICS);
    exportMetrics(SST_METRICS);
    exportMetrics(WRITE_METRICS);

    final long elapsedTime = System.currentTimeMillis() - startTime;
    LOG.trace("Exporting RocksDBMetrics took + {} ms", elapsedTime);
  }

  private void exportMetrics(final RocksDBMetric[] metrics) {
    final var database = databaseSupplier.get();
    if (database == null) {
      return;
    }
    for (final RocksDBMetric metric : metrics) {
      try {
        database
            .getProperty(metric.getPropertyName())
            .map(Double::parseDouble)
            .ifPresent(value -> metric.exportValue(partition, value));
      } catch (final Exception exception) {
        LOG.debug("Error occurred on exporting metric {}", metric.getPropertyName(), exception);
      }
    }
  }

  private static final class RocksDBMetric {

    private final String propertyName;
    private final Gauge gauge;

    private RocksDBMetric(final String propertyName, final String namePrefix, final String help) {
      this.propertyName = Objects.requireNonNull(propertyName);

      gauge =
          Gauge.build()
              .namespace(ZEEBE_NAMESPACE)
              .name(namePrefix + gaugeSuffix())
              .help(help)
              .labelNames(PARTITION)
              .register();
    }

    private String gaugeSuffix() {
      final String suffix =
          "_" + propertyName.substring(propertyName.indexOf(".") + 1); // cut off "rocksdb." prefix
      return suffix.replaceAll("-", "_");
    }

    public void exportValue(final String partitionID, final Double value) {
      gauge.labels(partitionID).set(value);
    }

    public String getPropertyName() {
      return propertyName;
    }
  }
}
