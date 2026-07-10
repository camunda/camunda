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
import io.camunda.eventbridge.streaming.state.api.KeyValueStore;
import io.camunda.eventbridge.streaming.state.rocksdb.RocksDbStateStoreProvider;
import io.camunda.zeebe.db.impl.DbBytes;
import io.camunda.zeebe.db.impl.DbInt;
import io.camunda.zeebe.db.impl.DbLong;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.File;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Periodic snapshots (ADR 0010) through the real Stage-2 task: finalized finest windows fold into
 * the durable cumulative accumulator, boundaries are released by later windows or the watermark,
 * emitted rows are absolute values in the {@code _snapshots} table, and the durable sampler state
 * survives a restart so a replay re-derives identical rows.
 */
final class AggregationStageTaskSnapshotTest {

  private static final String PROCESS = "order-process";
  private static final long MINUTE = 60_000L;

  @TempDir Path stateDir;

  private TestMetadataStore metadataStore;
  private DatasetRegistry registry;
  private DatasetCatalog catalog;
  private JdbcDataSource dataSource;
  private RdbmsDatasetStore datasetStore;
  private RocksDbStateStoreProvider<AnalyticsColumnFamilies> provider;
  private AggregationStageTask task;
  private long segment;
  private long cubeId;

  @BeforeEach
  void setUp() {
    metadataStore = new TestMetadataStore();
    registry = new DatasetRegistry();
    dataSource = new JdbcDataSource();
    dataSource.setURL("jdbc:h2:mem:snap-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
    dataSource.setUser("sa");
    // A level-style cube: count meter, 1-minute finest window, 1-minute grace, 1-minute snapshots.
    final DatasetDeclaration declaration =
        DatasetDeclaration.builder("active-instances", FactType.PROCESS_INSTANCE)
            .dimension("bpmnProcessId", DimensionType.STRING)
            .meter(Meter.of("count", MeterCatalog.COUNT))
            .window(MINUTE)
            .lateness(MINUTE)
            .snapshots(MINUTE)
            .build();
    cubeId = registry.admit(declaration, Map.of(), 0L).cubeId();
    metadataStore.datasetSpecStore().create(registry.get(cubeId).orElseThrow());
  }

  @AfterEach
  void tearDown() {
    if (task != null) {
      task.close();
    }
  }

  @Test
  void shouldEmitAbsoluteSnapshotsAsBoundariesBecomeProvablyComplete() {
    // given a snapshot-enabled cube and a task
    openTask();
    final CompiledDataset dataset = compiled();

    // when the first window's delta arrives (window [0, 1m), one fact) and a much later window
    // advances the watermark far past it
    task.process(envelope(dataset, 0L, 1L));
    Cuts.commit(task, 0L);
    task.process(envelope(dataset, 4 * MINUTE, 2L));
    Cuts.commit(task, 1L);
    // watermark = 5m - 1m grace = 4m: window [0,1m) finalized -> fold=1, boundary 1m released by
    // the watermark; window [4m,5m) still open (its end 5m > watermark)
    Cuts.commit(task, 2L);

    // then the snapshot at boundary 1m carries the ABSOLUTE cumulative value
    assertThat(snapshots()).containsExactly(Map.entry(MINUTE, 1L));

    // when an even later window finalizes the second one (watermark reaches 5m)
    task.process(envelope(dataset, 6 * MINUTE, 4L));
    Cuts.commit(task, 3L);
    Cuts.commit(task, 4L);

    // then the second snapshot is cumulative (1 + 2), sparse (no rows for silent boundaries)
    assertThat(snapshots()).containsExactly(Map.entry(MINUTE, 1L), Map.entry(5 * MINUTE, 3L));
  }

  @Test
  void shouldRecoverTheCumulativeFoldAcrossARestart() {
    // given a snapshot emitted and the sampler's durable state committed
    openTask();
    CompiledDataset dataset = compiled();
    task.process(envelope(dataset, 0L, 1L));
    Cuts.commit(task, 0L);
    task.process(envelope(dataset, 4 * MINUTE, 2L));
    Cuts.commit(task, 1L);
    Cuts.commit(task, 2L);
    assertThat(snapshots()).containsExactly(Map.entry(MINUTE, 1L));

    // when the task restarts over the same stores
    task.close();
    task = null;
    provider =
        RocksDbStateStoreProvider.open(
            new File(stateDir.toFile(), "stage2"), new SimpleMeterRegistry());
    openTask();
    dataset = compiled();

    // and the next window finalizes the pre-restart open one
    task.process(envelope(dataset, 6 * MINUTE, 4L));
    Cuts.commit(task, 3L);
    Cuts.commit(task, 4L);

    // then the cumulative continued from the recovered fold — not from zero
    assertThat(snapshots()).containsExactly(Map.entry(MINUTE, 1L), Map.entry(5 * MINUTE, 3L));
  }

  private void openTask() {
    catalog = new DatasetCatalog(metadataStore);
    if (provider == null) {
      provider =
          RocksDbStateStoreProvider.open(
              new File(stateDir.toFile(), "stage2"), new SimpleMeterRegistry());
    }
    final KeyValueStore<DbBytes, DbBytes> cellStore =
        provider.keyValueStore(AnalyticsColumnFamilies.CUBE_CELLS, new DbBytes(), new DbBytes());
    final KeyValueStore<DbInt, DbLong> offsets =
        provider.keyValueStore(
            AnalyticsColumnFamilies.CONSUMED_POSITION, new DbInt(), new DbLong());
    final KeyValueStore<DbBytes, DbBytes> dedupStore =
        provider.keyValueStore(
            AnalyticsColumnFamilies.SHUFFLE_DEDUP_WATERMARK, new DbBytes(), new DbBytes());
    final KeyValueStore<DbBytes, DbBytes> parkedStore =
        provider.keyValueStore(AnalyticsColumnFamilies.PARKED_DELTAS, new DbBytes(), new DbBytes());
    datasetStore = new RdbmsDatasetStore(dataSource);
    task =
        new AggregationStageTask(
            1,
            () -> 1L,
            datasetStore,
            datasetStore.writer(),
            provider,
            cellStore,
            offsets,
            dedupStore,
            parkedStore,
            catalog,
            0L,
            0L);
    provider = null; // owned by the task now; recreated explicitly for a restart
  }

  private CompiledDataset compiled() {
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

  /** The snapshot rows as (sample_time -> absolute count), ordered by time. */
  private Map<Long, Long> snapshots() {
    final Map<Long, Long> rows = new LinkedHashMap<>();
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement =
            connection.prepareStatement(
                "SELECT sample_time, \"count_\" FROM dataset_"
                    + cubeId
                    + "_snapshots ORDER BY sample_time");
        ResultSet resultSet = statement.executeQuery()) {
      while (resultSet.next()) {
        rows.put(resultSet.getLong(1), resultSet.getLong(2));
      }
    } catch (final Exception e) {
      throw new IllegalStateException(e);
    }
    return rows;
  }
}
