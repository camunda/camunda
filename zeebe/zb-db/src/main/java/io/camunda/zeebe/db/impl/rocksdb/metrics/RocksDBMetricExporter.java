/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.impl.rocksdb.metrics;

import io.camunda.zeebe.util.micrometer.StatefulGauge;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.rocksdb.RocksDB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** This class exports metrics for a RocksDB instance to Micrometer. */
public final class RocksDBMetricExporter {

  private static final Logger LOG = LoggerFactory.getLogger(RocksDBMetricExporter.class.getName());
  private static final String CF_STATS_PROPERTY = "rocksdb.cfstats";

  private final Map<RocksDbMetricsDoc, StatefulGauge> metrics =
      new EnumMap<>(RocksDbMetricsDoc.class);
  private final Map<RocksDbIoStallMetricsDoc, AtomicLong> ioStallMetrics =
      new EnumMap<>(RocksDbIoStallMetricsDoc.class);
  private final MeterRegistry registry;

  public RocksDBMetricExporter(final MeterRegistry registry) {
    this.registry = registry;
  }

  public void exportMetrics(final RocksDB database) {
    final long startTime = System.nanoTime();

    for (final var metric : RocksDbMetricsDoc.values()) {
      final var gauge = metrics.computeIfAbsent(metric, this::registerMetric);
      exportMetric(database, metric.propertyName(), gauge);
    }

    exportIoStallMetrics(database);

    final long elapsedTime = System.nanoTime() - startTime;
    LOG.trace(
        "Exporting RocksDBMetrics took + {} ms",
        TimeUnit.MILLISECONDS.convert(elapsedTime, TimeUnit.NANOSECONDS));
  }

  private StatefulGauge registerMetric(final RocksDbMetricsDoc doc) {
    return StatefulGauge.builder(doc.getName())
        .description(doc.getDescription())
        .register(registry);
  }

  private void exportMetric(
      final RocksDB database, final String propertyName, final StatefulGauge gauge) {
    try {
      final var value = database.getProperty(propertyName);
      if (value != null) {
        gauge.set(Double.parseDouble(value));
      }
    } catch (final Exception exception) {
      LOG.debug("Error occurred on exporting metric {}", propertyName, exception);
    }
  }

  private void exportIoStallMetrics(final RocksDB database) {
    final Map<String, String> cfStats;
    try {
      cfStats = database.getMapProperty(CF_STATS_PROPERTY);
    } catch (final Exception exception) {
      LOG.debug("Error occurred on reading property {}", CF_STATS_PROPERTY, exception);
      return;
    }

    if (cfStats == null) {
      return;
    }

    for (final var metric : RocksDbIoStallMetricsDoc.values()) {
      final var value = cfStats.get(metric.propertyName());
      if (value == null) {
        continue;
      }

      try {
        final long count = Long.parseLong(value.trim());
        ioStallMetrics.computeIfAbsent(metric, this::registerIoStallCounter).set(count);
      } catch (final NumberFormatException exception) {
        LOG.debug(
            "Could not parse io-stall metric {} with value {}",
            metric.propertyName(),
            value,
            exception);
      }
    }
  }

  /**
   * Registers a {@link FunctionCounter} backed by the returned {@link AtomicLong}. RocksDB hands us
   * the absolute cumulative count each cycle (not a delta), so the exporter holds the latest value
   * in the {@code AtomicLong} and the counter simply reports it.
   */
  private AtomicLong registerIoStallCounter(final RocksDbIoStallMetricsDoc doc) {
    final var state = new AtomicLong();
    FunctionCounter.builder(doc.getName(), state, AtomicLong::doubleValue)
        .description(doc.getDescription())
        .register(registry);
    return state;
  }
}
