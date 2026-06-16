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
import io.camunda.zeebe.db.impl.rocksdb.metrics.RocksDbIoStallMetricsDoc;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.File;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rocksdb.Options;
import org.rocksdb.RocksDB;

final class RocksDBMetricExporterTest {

  @Test
  void shouldExportIoStallMetricsFromCfStatsMap(@TempDir final File dir) throws Exception {
    // given
    RocksDB.loadLibrary();
    final var registry = new SimpleMeterRegistry();
    final var exporter = new RocksDBMetricExporter(registry);

    try (final var options = new Options().setCreateIfMissing(true);
        final var db = RocksDB.open(options, dir.getAbsolutePath())) {

      // when
      exporter.exportMetrics(db);

      // then -- every documented io-stall counter is present in the cfstats map and was registered
      assertThat(RocksDbIoStallMetricsDoc.values())
          .allSatisfy(
              doc ->
                  assertThat(registry.find(doc.getName()).functionCounter())
                      .as(
                          "counter '%s' (cfstats key '%s') is registered",
                          doc.getName(), doc.propertyName())
                      .isNotNull());
    }
  }
}
