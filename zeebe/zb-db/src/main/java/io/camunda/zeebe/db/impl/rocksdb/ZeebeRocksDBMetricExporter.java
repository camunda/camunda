/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.impl.rocksdb;

import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.protocol.EnumValue;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Objects;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** This class exports metrics for a RocksDB instance to Prometheus. */
public final class ZeebeRocksDBMetricExporter<
    ColumnFamilyType extends Enum<? extends EnumValue> & EnumValue> {

  private static final Logger LOG =
      LoggerFactory.getLogger(ZeebeRocksDBMetricExporter.class.getName());

  private static final String ZEEBE_NAMESPACE = "zeebe";

  private static final String MEMORY_METRICS_HELP =
      "Everything which might be related to current memory consumption of RocksDB per column family and partition";
  private static final String MEMORY_METRICS_PREFIX = "rocksdb.memory";
  private static final String SST_METRICS_HELP =
      "Everything which is related to SST files in RocksDB per column family and partition";
  private static final String SST_METRICS_PREFIX = "rocksdb.sst";
  private static final String LIVE_METRICS_HELP =
      "Other estimated properties based on entries in RocksDb per column family and partition";
  private static final String LIVE_METRICS_PREFIX = "rocksdb.live";
  private static final String WRITE_METRICS_HELP =
      "Properties related to writes, flushes and compactions for RocksDb per column family and partition";
  private static final String WRITE_METRICS_PREFIX = "rocksdb.writes";
  private final String partition;
  private final Supplier<ZeebeDb<ColumnFamilyType>> databaseSupplier;
  private final RocksDBMetric[] memoryMetrics;
  private final RocksDBMetric[] liveMetrics;
  private final RocksDBMetric[] writeMetrics;
  private final RocksDBMetric[] sstMetrics;

  public ZeebeRocksDBMetricExporter(
      final String partition,
      final Supplier<ZeebeDb<ColumnFamilyType>> databaseSupplier,
      final MeterRegistry meterRegistry) {
    this.partition = Objects.requireNonNull(partition);
    this.databaseSupplier = databaseSupplier;
    memoryMetrics =
        new RocksDBMetric[] {
          new RocksDBMetric(
              "rocksdb.cur-size-all-mem-tables",
              MEMORY_METRICS_PREFIX,
              MEMORY_METRICS_HELP,
              meterRegistry),
          new RocksDBMetric(
              "rocksdb.cur-size-active-mem-table",
              MEMORY_METRICS_PREFIX,
              MEMORY_METRICS_HELP,
              meterRegistry),
          new RocksDBMetric(
              "rocksdb.size-all-mem-tables",
              MEMORY_METRICS_PREFIX,
              MEMORY_METRICS_HELP,
              meterRegistry),
          new RocksDBMetric(
              "rocksdb.block-cache-usage",
              MEMORY_METRICS_PREFIX,
              MEMORY_METRICS_HELP,
              meterRegistry),
          new RocksDBMetric(
              "rocksdb.block-cache-capacity",
              MEMORY_METRICS_PREFIX,
              MEMORY_METRICS_HELP,
              meterRegistry),
          new RocksDBMetric(
              "rocksdb.block-cache-pinned-usage",
              MEMORY_METRICS_PREFIX,
              MEMORY_METRICS_HELP,
              meterRegistry),
          new RocksDBMetric(
              "rocksdb.estimate-table-readers-mem",
              MEMORY_METRICS_PREFIX,
              MEMORY_METRICS_HELP,
              meterRegistry),
        };

    liveMetrics =
        new RocksDBMetric[] {
          new RocksDBMetric(
              "rocksdb.num-entries-imm-mem-tables",
              LIVE_METRICS_PREFIX,
              LIVE_METRICS_HELP,
              meterRegistry),
          new RocksDBMetric(
              "rocksdb.estimate-num-keys", LIVE_METRICS_PREFIX, LIVE_METRICS_HELP, meterRegistry),
          new RocksDBMetric(
              "rocksdb.estimate-live-data-size",
              LIVE_METRICS_PREFIX,
              LIVE_METRICS_HELP,
              meterRegistry),
        };
    writeMetrics =
        new RocksDBMetric[] {
          new RocksDBMetric(
              "rocksdb.is-write-stopped", WRITE_METRICS_PREFIX, WRITE_METRICS_HELP, meterRegistry),
          new RocksDBMetric(
              "rocksdb.actual-delayed-write-rate",
              WRITE_METRICS_PREFIX,
              WRITE_METRICS_HELP,
              meterRegistry),
          new RocksDBMetric(
              "rocksdb.mem-table-flush-pending",
              WRITE_METRICS_PREFIX,
              WRITE_METRICS_HELP,
              meterRegistry),
          new RocksDBMetric(
              "rocksdb.num-running-flushes",
              WRITE_METRICS_PREFIX,
              WRITE_METRICS_HELP,
              meterRegistry),
          new RocksDBMetric(
              "rocksdb.num-running-compactions",
              WRITE_METRICS_PREFIX,
              WRITE_METRICS_HELP,
              meterRegistry),
        };

    sstMetrics =
        new RocksDBMetric[] {
          new RocksDBMetric(
              "rocksdb.total-sst-files-size", SST_METRICS_PREFIX, SST_METRICS_HELP, meterRegistry),
          new RocksDBMetric(
              "rocksdb.live-sst-files-size", SST_METRICS_PREFIX, SST_METRICS_HELP, meterRegistry),
        };
  }

  public void exportMetrics() {
    final long startTime = System.currentTimeMillis();
    exportMetrics(memoryMetrics);
    exportMetrics(liveMetrics);
    exportMetrics(sstMetrics);
    exportMetrics(writeMetrics);

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
    private volatile double value;

    private RocksDBMetric(
        final String propertyName,
        final String namePrefix,
        final String help,
        final MeterRegistry registry) {
      this.propertyName = Objects.requireNonNull(propertyName);

      Gauge.builder(ZEEBE_NAMESPACE + ". " + namePrefix + gaugeSuffix(), () -> value)
          .description(help)
          .register(registry);
    }

    private String gaugeSuffix() {
      final String suffix =
          "_" + propertyName.substring(propertyName.indexOf(".") + 1); // cut off "rocksdb." prefix
      return suffix.replaceAll("-", "_");
    }

    public void exportValue(final String partitionID, final Double value) {
      this.value = value;
    }

    public String getPropertyName() {
      return propertyName;
    }
  }
}
