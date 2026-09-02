/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.impl.rocksdb.metrics;

import io.camunda.zeebe.db.impl.rocksdb.metrics.RocksDbHistogramMetricsDoc.Statistic;
import io.camunda.zeebe.util.micrometer.StatefulGauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.jspecify.annotations.Nullable;
import org.rocksdb.RocksDB;
import org.rocksdb.Statistics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** This class exports metrics for a RocksDB instance to Micrometer. */
public final class RocksDBMetricExporter {

  private static final Logger LOG = LoggerFactory.getLogger(RocksDBMetricExporter.class.getName());
  private static final String CF_STATS_PROPERTY = "rocksdb.cfstats";

  private final Map<RocksDbMetricsDoc, StatefulGauge> metrics =
      new EnumMap<>(RocksDbMetricsDoc.class);
  private final Map<RocksDbIoStallMetricsDoc, StatefulGauge> ioStallMetrics =
      new EnumMap<>(RocksDbIoStallMetricsDoc.class);
  private final Map<RocksDbTickerMetricsDoc, StatefulGauge> tickerMetrics =
      new EnumMap<>(RocksDbTickerMetricsDoc.class);
  private final Map<RocksDbHistogramMetricsDoc, HistogramGauges> histogramMetrics =
      new EnumMap<>(RocksDbHistogramMetricsDoc.class);
  private final MeterRegistry registry;

  /**
   * Only present when RocksDB statistics are enabled; the ticker and histogram meters are simply
   * not registered otherwise.
   */
  private final @Nullable Statistics statistics;

  public RocksDBMetricExporter(final MeterRegistry registry) {
    this(registry, null);
  }

  public RocksDBMetricExporter(
      final MeterRegistry registry, final @Nullable Statistics statistics) {
    this.registry = registry;
    this.statistics = statistics;
  }

  public void exportMetrics(final RocksDB database) {
    final long startTime = System.nanoTime();

    for (final var metric : RocksDbMetricsDoc.values()) {
      final var gauge = metrics.computeIfAbsent(metric, this::registerMetric);
      exportMetric(database, metric.propertyName(), gauge);
    }

    exportIoStallMetrics(database);
    exportStatistics();

    final long elapsedTime = System.nanoTime() - startTime;
    LOG.trace(
        "Exporting RocksDBMetrics took + {} ms",
        TimeUnit.MILLISECONDS.convert(elapsedTime, TimeUnit.NANOSECONDS));
  }

  private StatefulGauge registerMetric(final RocksDbMeterDoc doc) {
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
      final var gauge = ioStallMetrics.computeIfAbsent(metric, this::registerMetric);
      final var value = cfStats.get(metric.propertyName());
      if (value == null) {
        continue;
      }

      try {
        gauge.set(Long.parseLong(value.trim()));
      } catch (final NumberFormatException exception) {
        LOG.debug(
            "Could not parse io-stall metric {} with value {}",
            metric.propertyName(),
            value,
            exception);
      }
    }
  }

  private void exportStatistics() {
    if (statistics == null) {
      return;
    }

    for (final var metric : RocksDbTickerMetricsDoc.values()) {
      final var gauge = tickerMetrics.computeIfAbsent(metric, this::registerMetric);
      try {
        gauge.set(statistics.getTickerCount(metric.ticker()));
      } catch (final Exception exception) {
        LOG.debug("Error occurred on exporting ticker {}", metric.propertyName(), exception);
      }
    }

    for (final var metric : RocksDbHistogramMetricsDoc.values()) {
      final var gauges = histogramMetrics.computeIfAbsent(metric, this::registerHistogram);
      try {
        final var data = statistics.getHistogramData(metric.histogram());
        gauges.count().set(data.getCount());
        gauges.sum().set(data.getSum());
        gauges.p99().set(data.getPercentile99());
      } catch (final Exception exception) {
        LOG.debug("Error occurred on exporting histogram {}", metric.propertyName(), exception);
      }
    }
  }

  private HistogramGauges registerHistogram(final RocksDbHistogramMetricsDoc doc) {
    return new HistogramGauges(
        registerHistogramGauge(doc, Statistic.COUNT),
        registerHistogramGauge(doc, Statistic.SUM),
        registerHistogramGauge(doc, Statistic.P99));
  }

  private StatefulGauge registerHistogramGauge(
      final RocksDbHistogramMetricsDoc doc, final Statistic statistic) {
    return StatefulGauge.builder(doc.nameFor(statistic))
        .description(doc.descriptionFor(statistic))
        .register(registry);
  }

  private record HistogramGauges(StatefulGauge count, StatefulGauge sum, StatefulGauge p99) {}
}
