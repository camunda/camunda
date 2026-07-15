/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.store.rdbms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

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
import io.camunda.analytics.serving.spi.SnapshotPoint;
import io.camunda.analytics.serving.spi.WriteVersion;
import java.util.List;
import java.util.UUID;
import org.agrona.collections.MutableLong;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The snapshot table round trip (ADR 0010): DDL, fenced idempotent writes of absolute values, the
 * per-key BASELINE read (newest at-or-before a time — the opening balance), and the ordered RANGE
 * read of sparse change points.
 */
final class RdbmsSnapshotStoreTest {

  private static final long MINUTE = 60_000L;

  private final JdbcDataSource dataSource = h2();
  private final RdbmsDatasetStore store = new RdbmsDatasetStore(dataSource);
  private final CompiledDataset dataset =
      new DatasetCompiler(
              MeterCatalog.withDefaults(), new MeterIdRegistry(new InMemoryMeterIdStore()))
          .compile(
              1L,
              DatasetDeclaration.builder("active-instances", FactType.PROCESS_INSTANCE)
                  .dimension("bpmnProcessId", DimensionType.STRING)
                  .meter(Meter.of("active", MeterCatalog.LEVEL, "delta"))
                  .window(MINUTE)
                  .snapshots(MINUTE)
                  .build());

  private static JdbcDataSource h2() {
    final JdbcDataSource ds = new JdbcDataSource();
    ds.setURL("jdbc:h2:mem:snapread-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
    return ds;
  }

  @BeforeEach
  void setUp() {
    store.schemaManager().ensure(dataset);
    // Sparse snapshot history for two keys: "order" changes at 1m and 5m; "claim" only at 2m.
    write("order", MINUTE, 3L);
    write("order", 5 * MINUTE, 7L);
    write("claim", 2 * MINUTE, 1L);
    store.writer().flush();
  }

  @AfterEach
  void tearDown() {
    store.close();
  }

  @Test
  void shouldReadTheNewestSnapshotAtOrBeforeTheBaselineTime() {
    // when the baseline is fetched between order's two change points
    final List<SnapshotPoint> baseline = store.queryClient().snapshotBaseline(dataset, 3 * MINUTE);

    // then each key contributes its newest row at-or-before that time — however far back
    assertThat(baseline)
        .extracting(
            p -> p.keyValues().get(0), SnapshotPoint::sampleTime, p -> p.measures().get("active"))
        .containsExactlyInAnyOrder(tuple("order", MINUTE, 3L), tuple("claim", 2 * MINUTE, 1L));
  }

  @Test
  void shouldReadTheRangeOrderedByKeyThenTime() {
    // when the range after 1m up to 5m is fetched
    final List<SnapshotPoint> range =
        store.queryClient().snapshotRange(dataset, MINUTE, 5 * MINUTE);

    // then only the change points inside the range appear, ordered
    assertThat(range)
        .extracting(
            p -> p.keyValues().get(0), SnapshotPoint::sampleTime, p -> p.measures().get("active"))
        .containsExactly(tuple("claim", 2 * MINUTE, 1L), tuple("order", 5 * MINUTE, 7L));
  }

  @Test
  void shouldFenceAStaleSnapshotWrite() {
    // when a fenced zombie re-writes an older absolute for an existing sample
    final RdbmsDatasetWriter writer = (RdbmsDatasetWriter) store.writer();
    writer.upsertSnapshotRow(
        dataset,
        DimensionKey.of(dataset.grain(), "order"),
        MINUTE,
        level(999L),
        new WriteVersion(0, 5));
    writer.flush();

    // then the row is untouched (setUp wrote it at the seed version, which zombies cannot beat
    // only equal-or-newer versions can)
    final List<SnapshotPoint> baseline = store.queryClient().snapshotBaseline(dataset, MINUTE);
    assertThat(baseline).extracting(p -> p.measures().get("active")).containsExactly(3L);
  }

  private void write(final String process, final long sampleTime, final long level) {
    store
        .writer()
        .upsertSnapshotRow(
            dataset,
            DimensionKey.of(dataset.grain(), process),
            sampleTime,
            level(level),
            new WriteVersion(1, sampleTime));
  }

  private byte[] level(final long value) {
    return new CompositeAccumulatorValue(dataset.meterBounds())
        .toBytes(new Object[] {new MutableLong(value)});
  }
}
