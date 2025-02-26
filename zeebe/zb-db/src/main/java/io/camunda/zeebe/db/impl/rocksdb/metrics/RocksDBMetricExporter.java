/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.impl.rocksdb.metrics;

import io.camunda.zeebe.util.micrometer.StatefulGauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.rocksdb.RocksDB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** This class exports metrics for a RocksDB instance to Micrometer. */
public final class RocksDBMetricExporter {

  private static final Logger LOG = LoggerFactory.getLogger(RocksDBMetricExporter.class.getName());

  private final Map<RocksDbMetricsDoc, StatefulGauge> metrics =
      new EnumMap<>(RocksDbMetricsDoc.class);
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
}
