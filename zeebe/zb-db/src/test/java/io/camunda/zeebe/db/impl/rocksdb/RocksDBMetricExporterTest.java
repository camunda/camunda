/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.impl.rocksdb;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.db.impl.rocksdb.metrics.RocksDBMetricExporter;
import io.camunda.zeebe.db.impl.rocksdb.metrics.RocksDbHistogramMetricsDoc;
import io.camunda.zeebe.db.impl.rocksdb.metrics.RocksDbHistogramMetricsDoc.Statistic;
import io.camunda.zeebe.db.impl.rocksdb.metrics.RocksDbIoStallMetricsDoc;
import io.camunda.zeebe.db.impl.rocksdb.metrics.RocksDbTickerMetricsDoc;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.File;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rocksdb.Options;
import org.rocksdb.RocksDB;
import org.rocksdb.Statistics;

final class RocksDBMetricExporterTest {

  private static final String CF_STATS_PROPERTY = "rocksdb.cfstats";

  @Test
  void shouldRegisterAllIoStallGaugesOnExport(@TempDir final File dir) throws Exception {
    // given
    RocksDB.loadLibrary();
    final var registry = new SimpleMeterRegistry();
    final var exporter = new RocksDBMetricExporter(registry);

    try (final var options = new Options().setCreateIfMissing(true);
        final var db = RocksDB.open(options, dir.getAbsolutePath())) {

      // when
      exporter.exportMetrics(db);

      // then
      assertThat(RocksDbIoStallMetricsDoc.values())
          .allSatisfy(
              doc ->
                  assertThat(registry.find(doc.getName()).gauge())
                      .as("gauge '%s' is registered", doc.getName())
                      .isNotNull());
    }
  }

  @Test
  void shouldExposeAllDocumentedIoStallKeysInCfStats(@TempDir final File dir) throws Exception {
    // given
    RocksDB.loadLibrary();
    final var registry = new SimpleMeterRegistry();
    final var exporter = new RocksDBMetricExporter(registry);

    try (final var options = new Options().setCreateIfMissing(true);
        final var db = RocksDB.open(options, dir.getAbsolutePath())) {

      // when
      exporter.exportMetrics(db);
      final var cfStats = db.getMapProperty(CF_STATS_PROPERTY);

      // then
      assertThat(RocksDbIoStallMetricsDoc.values())
          .allSatisfy(
              doc ->
                  assertThat(cfStats)
                      .as(
                          "cfstats map contains key '%s' for gauge '%s'",
                          doc.propertyName(), doc.getName())
                      .containsKey(doc.propertyName()));
    }
  }

  @Test
  void shouldRegisterAllStatisticsGaugesWhenStatisticsAreEnabled(@TempDir final File dir)
      throws Exception {
    // given
    RocksDB.loadLibrary();
    final var registry = new SimpleMeterRegistry();

    try (final var statistics = new Statistics();
        final var options = new Options().setCreateIfMissing(true).setStatistics(statistics);
        final var db = RocksDB.open(options, dir.getAbsolutePath())) {
      final var exporter = new RocksDBMetricExporter(registry, statistics);

      // when
      exporter.exportMetrics(db);

      // then
      assertThat(RocksDbTickerMetricsDoc.values())
          .allSatisfy(
              doc ->
                  assertThat(registry.find(doc.getName()).gauge())
                      .as("gauge '%s' is registered", doc.getName())
                      .isNotNull());
      assertThat(RocksDbHistogramMetricsDoc.values())
          .allSatisfy(
              doc -> {
                for (final var statistic : Statistic.values()) {
                  assertThat(registry.find(doc.nameFor(statistic)).gauge())
                      .as("gauge '%s' is registered", doc.nameFor(statistic))
                      .isNotNull();
                }
              });
    }
  }

  @Test
  void shouldNotRegisterStatisticsGaugesWhenStatisticsAreDisabled(@TempDir final File dir)
      throws Exception {
    // given
    RocksDB.loadLibrary();
    final var registry = new SimpleMeterRegistry();
    final var exporter = new RocksDBMetricExporter(registry);

    try (final var options = new Options().setCreateIfMissing(true);
        final var db = RocksDB.open(options, dir.getAbsolutePath())) {

      // when
      exporter.exportMetrics(db);

      // then
      assertThat(RocksDbTickerMetricsDoc.values())
          .allSatisfy(
              doc ->
                  assertThat(registry.find(doc.getName()).gauge())
                      .as("gauge '%s' is not registered", doc.getName())
                      .isNull());
    }
  }

  @Test
  void shouldReflectRecordedTickerValues(@TempDir final File dir) throws Exception {
    // given
    RocksDB.loadLibrary();
    final var registry = new SimpleMeterRegistry();

    try (final var statistics = new Statistics();
        final var options = new Options().setCreateIfMissing(true).setStatistics(statistics);
        final var db = RocksDB.open(options, dir.getAbsolutePath())) {
      final var exporter = new RocksDBMetricExporter(registry, statistics);
      db.put("key".getBytes(), "value".getBytes());
      db.get("key".getBytes());

      // when
      exporter.exportMetrics(db);

      // then
      final var keysRead =
          registry.find(RocksDbTickerMetricsDoc.NUMBER_KEYS_READ.getName()).gauge();
      assertThat(keysRead).isNotNull();
      assertThat(keysRead.value()).isPositive();
    }
  }
}
