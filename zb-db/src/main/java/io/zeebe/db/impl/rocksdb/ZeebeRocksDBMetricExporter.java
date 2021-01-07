/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.db.impl.rocksdb;

import io.prometheus.client.Gauge;
import io.zeebe.db.ZeebeDb;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class exports metrics for a RocksDB instance to Prometheus.
 *
 * @param <ColumnFamilyType>
 */
public final class ZeebeRocksDBMetricExporter<ColumnFamilyType extends Enum<ColumnFamilyType>> {

  private static final Logger LOG =
      LoggerFactory.getLogger(ZeebeRocksDBMetricExporter.class.getName());

  private static final String PARTITION = "partition";
  private static final String COLUMN_FAMILY_NAME = "columnFamilyName";
  private static final String PROPERTY_NAME = "propertyName";
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

  private final String partition;
  private final ZeebeDb<ColumnFamilyType> database;
  private final Class<ColumnFamilyType> columnFamilyTypeClass;

  public ZeebeRocksDBMetricExporter(
      final String partition,
      final ZeebeDb<ColumnFamilyType> database,
      final Class<ColumnFamilyType> columnFamilyTypeClass) {
    this.partition = Objects.requireNonNull(partition);
    this.database = Objects.requireNonNull(database);
    this.columnFamilyTypeClass = Objects.requireNonNull(columnFamilyTypeClass);
  }

  public void exportMetrics() {
    final long startTime = System.currentTimeMillis();
    for (final ColumnFamilyType columnFamilyName : columnFamilyTypeClass.getEnumConstants()) {

      exportMetrics(columnFamilyName, MEMORY_METRICS);
      exportMetrics(columnFamilyName, LIVE_METRICS);
      exportMetrics(columnFamilyName, SST_METRICS);
      exportMetrics(columnFamilyName, WRITE_METRICS);
    }

    final long elapsedTime = System.currentTimeMillis() - startTime;
    LOG.trace("Exporting RocksDBMetrics took + " + elapsedTime + " ms");
  }

  private void exportMetrics(
      final ColumnFamilyType columnFamilyName, final RocksDBMetric[] metrics) {
    for (final RocksDBMetric metric : metrics) {
      try {
        database
            .getProperty(columnFamilyName, metric.getPropertyName())
            .map(Double::parseDouble)
            .ifPresent(value -> metric.exportValue(partition, columnFamilyName, value));
      } catch (Throwable t) {
        LOG.debug(t.getMessage(), t);
      }
    }
  }

  private static final class RocksDBMetric {

    private final String propertyName;
    private final Gauge gauge;

    private RocksDBMetric(String propertyName, String namePrefix, String help) {
      this.propertyName = Objects.requireNonNull(propertyName);

      gauge =
          Gauge.build()
              .namespace(ZEEBE_NAMESPACE)
              .name(namePrefix + gaugeSuffix())
              .help(help)
              .labelNames(PARTITION, COLUMN_FAMILY_NAME)
              .register();
    }

    private String gaugeSuffix() {
      final String suffix =
          "_" + propertyName.substring(propertyName.indexOf(".") + 1); // cut off "rocksdb." prefix
      return suffix.replaceAll("-", "_");
    }

    public void exportValue(String partitionID, Enum columnFamilyName, Double value) {
      gauge.labels(partitionID, columnFamilyName.name().toLowerCase()).set(value);
    }

    public String getPropertyName() {
      return propertyName;
    }
  }
}
