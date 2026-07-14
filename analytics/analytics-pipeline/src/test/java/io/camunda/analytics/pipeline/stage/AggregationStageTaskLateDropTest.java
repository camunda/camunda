/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.pipeline.stage;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.analytics.dataset.ActiveCube;
import io.camunda.analytics.dataset.CompiledDataset;
import io.camunda.analytics.dataset.DatasetDeclaration;
import io.camunda.analytics.dataset.DatasetRegistry;
import io.camunda.analytics.dimension.DimensionKey;
import io.camunda.analytics.dimension.DimensionKeyValue;
import io.camunda.analytics.dimension.DimensionType;
import io.camunda.analytics.fact.Fact;
import io.camunda.analytics.fact.FactType;
import io.camunda.analytics.meter.CompositeAccumulatorValue;
import io.camunda.analytics.meter.CompositeAggregateFunction;
import io.camunda.analytics.meter.Meter;
import io.camunda.analytics.meter.MeterCatalog;
import io.camunda.analytics.projection.AnalyticsColumnFamilies;
import io.camunda.analytics.serving.catalog.DatasetCatalog;
import io.camunda.analytics.store.rdbms.RdbmsDatasetStore;
import io.camunda.eventbridge.streaming.shuffle.CellDelta;
import io.camunda.eventbridge.streaming.shuffle.ShuffleEnvelope;
import io.camunda.eventbridge.streaming.shuffle.ShuffleOperation;
import io.camunda.eventbridge.streaming.shuffle.ShufflePayloadKind;
import io.camunda.eventbridge.streaming.state.rocksdb.RocksDbStateStoreProvider;
import io.camunda.zeebe.db.impl.DbBytes;
import io.camunda.zeebe.db.impl.DbInt;
import io.camunda.zeebe.db.impl.DbLong;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The late-drop alarm: a delta whose window closed before it arrived is dropped by the merge guard
 * — and that loss is counted, never silent. Both envelopes here carry the same producer partition,
 * so the source lags <em>itself</em>: the min-of-sources clock is that one source's own max and
 * legitimately still closes the window (only a sibling source running ahead no longer does).
 */
final class AggregationStageTaskLateDropTest {

  private static final String PROCESS = "order-process";
  private static final long MINUTE = 60_000L;

  @TempDir Path stateDir;

  private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
  private AggregationStageTask task;
  private long segment;

  @AfterEach
  void tearDown() {
    if (task != null) {
      task.close();
    }
  }

  @Test
  void shouldCountADeltaDroppedByTheClosedWindowGuard() {
    // given a cube with a 1m window and 1m grace, and a task with a meter registry
    final TestMetadataStore metadataStore = new TestMetadataStore();
    final DatasetRegistry datasetRegistry = new DatasetRegistry();
    final DatasetDeclaration declaration =
        DatasetDeclaration.builder("throughput", FactType.PROCESS_INSTANCE)
            .dimension("bpmnProcessId", DimensionType.STRING)
            .meter(Meter.of("count", MeterCatalog.COUNT))
            .window(MINUTE)
            .lateness(MINUTE)
            .build();
    final long cubeId = datasetRegistry.admit(declaration, Map.of(), 0L).cubeId();
    metadataStore.datasetSpecStore().create(datasetRegistry.get(cubeId).orElseThrow());
    final DatasetCatalog catalog = new DatasetCatalog(metadataStore);
    final JdbcDataSource dataSource = new JdbcDataSource();
    dataSource.setURL("jdbc:h2:mem:late-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
    dataSource.setUser("sa");
    final RocksDbStateStoreProvider<AnalyticsColumnFamilies> provider =
        RocksDbStateStoreProvider.open(new File(stateDir.toFile(), "stage2"), registry);
    final RdbmsDatasetStore datasetStore = new RdbmsDatasetStore(dataSource);
    task =
        new AggregationStageTask(
            1,
            () -> 1L,
            datasetStore,
            datasetStore.writer(),
            provider,
            provider.keyValueStore(
                AnalyticsColumnFamilies.CUBE_CELLS, new DbBytes(), new DbBytes()),
            provider.keyValueStore(
                AnalyticsColumnFamilies.CONSUMED_POSITION, new DbInt(), new DbLong()),
            provider.keyValueStore(
                AnalyticsColumnFamilies.SHUFFLE_DEDUP_WATERMARK, new DbBytes(), new DbBytes()),
            provider.keyValueStore(
                AnalyticsColumnFamilies.PARKED_DELTAS, new DbBytes(), new DbBytes()),
            catalog,
            registry,
            0L,
            0L);
    final CompiledDataset dataset = compiled(catalog, cubeId);

    // when a much later window advances the clock far past the first, and the first window's
    // straggler arrives after its close (window end 1m + grace 1m <= clock 5m)
    task.process(envelope(dataset, 4 * MINUTE, 1L));
    Cuts.commit(task, 0L);
    task.process(envelope(dataset, 0L, 1L));
    Cuts.commit(task, 1L);

    // then the loss is counted, tagged with the cube and tier it hit
    assertThat(
            registry
                .get("analytics.aggregation.late.dropped")
                .tag("dataset", "throughput")
                .tag("tier", Long.toString(MINUTE))
                .tag("partition", "1")
                .counter()
                .count())
        .isEqualTo(1.0);
  }

  private static CompiledDataset compiled(final DatasetCatalog catalog, final long cubeId) {
    catalog.refresh();
    for (final ActiveCube cube : catalog.cubes()) {
      if (cube.registered().cubeId() == cubeId) {
        return cube.compiled();
      }
    }
    throw new IllegalStateException("cube not in catalog");
  }

  /** One envelope with one composite COUNT delta of {@code facts} facts for {@code windowStart}. */
  private ShuffleEnvelope envelope(
      final CompiledDataset dataset, final long windowStart, final long facts) {
    final DimensionKey key = DimensionKey.of(dataset.grain(), PROCESS);
    final byte[] keyBytes = new DimensionKeyValue(dataset.grain()).toBytes(key);
    final CompositeAggregateFunction aggregate =
        new CompositeAggregateFunction(dataset.meterBounds());
    Object[] accumulator = aggregate.createAccumulator();
    for (long i = 0; i < facts; i++) {
      accumulator =
          aggregate.add(
              Fact.builder(FactType.PROCESS_INSTANCE)
                  .field("bpmnProcessId", PROCESS)
                  .eventTime(windowStart)
                  .source(1, segment * 10 + i)
                  .build(),
              accumulator);
    }
    final byte[] payload =
        new CompositeAccumulatorValue(dataset.meterBounds()).toBytes(accumulator);
    return new ShuffleEnvelope(
        0L,
        1,
        1,
        ++segment,
        0,
        false,
        ShufflePayloadKind.AGGREGATE_DELTA,
        ShuffleOperation.MERGE,
        List.of(new CellDelta(dataset.streamId(), windowStart, keyBytes, payload)));
  }
}
