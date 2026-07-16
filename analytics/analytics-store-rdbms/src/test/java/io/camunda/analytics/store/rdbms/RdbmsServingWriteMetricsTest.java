/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.store.rdbms;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.analytics.dataset.CompiledDataset;
import io.camunda.analytics.dataset.DatasetCompiler;
import io.camunda.analytics.dataset.DatasetDeclaration;
import io.camunda.analytics.dimension.DimensionKey;
import io.camunda.analytics.dimension.DimensionType;
import io.camunda.analytics.fact.FactType;
import io.camunda.analytics.meter.CompositeAccumulatorValue;
import io.camunda.analytics.meter.InMemoryMeterIdStore;
import io.camunda.analytics.meter.Meter;
import io.camunda.analytics.meter.MeterCatalog;
import io.camunda.analytics.meter.MeterIdRegistry;
import io.camunda.analytics.serving.spi.ServingWriteMetrics;
import io.camunda.analytics.serving.spi.WriteVersion;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.agrona.collections.MutableLong;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * The RDBMS writer's write-path health signals: successful upserts count as rows written (tagged by
 * the owning dataset), fence rejections count as fenced-rejected, and every flush records one
 * duration sample.
 */
final class RdbmsServingWriteMetricsTest {

  private static final long MINUTE = 60_000L;

  private final RecordingMetrics metrics = new RecordingMetrics();
  private final JdbcDataSource dataSource = h2();
  private final RdbmsDatasetStore store = new RdbmsDatasetStore(dataSource, metrics);
  private final CompiledDataset dataset =
      new DatasetCompiler(
              MeterCatalog.withDefaults(), new MeterIdRegistry(new InMemoryMeterIdStore()))
          .compile(
              1L,
              DatasetDeclaration.builder("pi-count", FactType.PROCESS_INSTANCE)
                  .dimension("bpmnProcessId", DimensionType.STRING)
                  .meter(Meter.of("count", MeterCatalog.COUNT))
                  .window(MINUTE)
                  .build());

  private static JdbcDataSource h2() {
    final JdbcDataSource ds = new JdbcDataSource();
    ds.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
    return ds;
  }

  @AfterEach
  void tearDown() {
    store.close();
  }

  @Test
  void shouldCountWrittenRowsFencedRejectionsAndFlushDurations() {
    // given a provisioned cube
    store.schemaManager().ensure(dataset);
    final RdbmsDatasetWriter writer = (RdbmsDatasetWriter) store.writer();

    // when a fresh cell upserts successfully
    writer.upsertCell(dataset, key(), 0L, MINUTE, count(10L), new WriteVersion(5, 100));
    writer.flush();

    // then one row written for this dataset, no rejections, one flush duration sample
    assertThat(metrics.rowsWritten).containsExactly("pi-count");
    assertThat(metrics.fencedRejected).isZero();
    assertThat(metrics.writeDurations).hasSize(1);

    // when a fenced zombie's stale write follows (older epoch)
    writer.upsertCell(dataset, key(), 0L, MINUTE, count(3L), new WriteVersion(4, 999));
    writer.flush();

    // then the rejection is counted as fenced, not as a written row
    assertThat(metrics.rowsWritten).containsExactly("pi-count");
    assertThat(metrics.fencedRejected).isEqualTo(1);
    assertThat(metrics.writeDurations).hasSize(2);
  }

  private DimensionKey key() {
    return DimensionKey.of(dataset.grain(), "orders");
  }

  private byte[] count(final long value) {
    return new CompositeAccumulatorValue(dataset.meterBounds())
        .toBytes(new Object[] {new MutableLong(value)});
  }

  /** A {@link ServingWriteMetrics} fake recording every signal. */
  private static final class RecordingMetrics implements ServingWriteMetrics {

    private final List<String> rowsWritten = new ArrayList<>();
    private final List<Long> writeDurations = new ArrayList<>();
    private int fencedRejected;

    @Override
    public void rowWritten(final String datasetName) {
      rowsWritten.add(datasetName);
    }

    @Override
    public void fencedRejected() {
      fencedRejected++;
    }

    @Override
    public void writeDuration(final long durationNanos) {
      writeDurations.add(durationNanos);
    }
  }
}
