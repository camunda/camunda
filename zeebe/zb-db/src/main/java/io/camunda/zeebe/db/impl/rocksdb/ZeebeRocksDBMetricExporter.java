/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.db.impl.rocksdb;

import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.protocol.EnumValue;
import io.camunda.zeebe.util.micrometer.ExtendedMeterDocumentation;
import io.camunda.zeebe.util.micrometer.MicrometerUtil.PartitionKeyNames;
import io.micrometer.common.docs.KeyName;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter.Type;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Stream;
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
  private static final RocksDBMetric[] ALL_METRICS =
      Stream.of(MEMORY_METRICS, LIVE_METRICS, SST_METRICS, WRITE_METRICS)
          .flatMap(Arrays::stream)
          .toArray(RocksDBMetric[]::new);
  private final ZeebeDb<ColumnFamilyType> database;

  public ZeebeRocksDBMetricExporter(
      final ZeebeDb<ColumnFamilyType> database, final MeterRegistry meterRegistry) {
    this.database = database;
    forAllMetrics(metric -> metric.register(meterRegistry));
  }

  public static ExtendedMeterDocumentation[] allMeterDocumentations() {
    return ALL_METRICS;
  }

  private static void forAllMetrics(final Consumer<RocksDBMetric> consumer) {
    for (final var metric : ALL_METRICS) {
      consumer.accept(metric);
    }
  }

  public void exportMetrics() {
    final long startTime = System.currentTimeMillis();
    forAllMetrics(this::exportMetric);

    final long elapsedTime = System.currentTimeMillis() - startTime;
    LOG.trace("Exporting RocksDBMetrics took + {} ms", elapsedTime);
  }

  private void exportMetric(final RocksDBMetric metric) {
    try {
      database
          .getProperty(metric.getPropertyName())
          .map(Double::parseDouble)
          .ifPresent(metric::exportValue);
    } catch (final Exception exception) {
      LOG.debug("Error occurred on exporting metric {}", metric.getPropertyName(), exception);
    }
  }

  @SuppressWarnings("NullableProblems")
  private static final class RocksDBMetric implements ExtendedMeterDocumentation {

    private final String propertyName;
    private final String namePrefix;
    private final String help;
    private volatile double value;

    private RocksDBMetric(final String propertyName, final String namePrefix, final String help) {
      this.propertyName = Objects.requireNonNull(propertyName);
      this.namePrefix = namePrefix;
      this.help = help;
    }

    public void register(final MeterRegistry registry) {
      Gauge.builder(getName(), () -> value).description(help).register(registry);
    }

    private String gaugeSuffix() {
      final String suffix =
          "." + propertyName.substring(propertyName.indexOf(".") + 1); // cut off "rocksdb." prefix
      return suffix.replaceAll("-", ".");
    }

    public void exportValue(final Double value) {
      this.value = value;
    }

    public String getPropertyName() {
      return propertyName;
    }

    @Override
    public String getDescription() {
      return help;
    }

    @Override
    public String getName() {
      return ZEEBE_NAMESPACE + ". " + namePrefix + gaugeSuffix();
    }

    @Override
    public Type getType() {
      return Type.GAUGE;
    }

    @Override
    public KeyName[] getAdditionalKeyNames() {
      return PartitionKeyNames.values();
    }
  }
}
