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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@link AggregationStageTask#closeKeepingStores()} (event-bridge-streaming ADR 0009 decision 6 /
 * consumer-groups ADR 0006 decision 1's demotion lifecycle): releases everything this task owns
 * except its {@link RocksDbStateStoreProvider}, so a controller demoting this task to STANDBY can
 * keep tailing the changelog into the same store, and a subsequent promotion's freshly constructed
 * task sees exactly what this one persisted — the promoted fold cannot tell itself apart from a
 * restart.
 */
final class AggregationStageTaskCloseKeepingStoresTest {

  private static final String PROCESS = "order-process";

  @TempDir Path stateDir;

  private TestMetadataStore metadataStore;
  private DatasetRegistry registry;
  private DatasetCatalog catalog;
  private RocksDbStateStoreProvider<AnalyticsColumnFamilies> provider;

  @Test
  void shouldKeepTheProviderOpenAndLetANewTaskSeeThePersistedState() {
    // given a task that merged and durably committed one delta
    final AggregationStageTask first = openTask();
    final CubeHandle handle = resolve();
    first.process(envelope(handle, 1L, 0));
    Cuts.commit(first, 0L);

    // when demoted (closeKeepingStores, not close)
    first.closeKeepingStores();

    // then the provider is still open and usable directly
    assertThat(durableOffset()).isEqualTo(0L);

    // and — a second task built against the SAME provider (as a promoted fold would be) sees the
    // exact durable offset the first task committed, exactly as a restart would restore it
    final AggregationStageTask second = reopenTaskAgainstSameProvider();
    try {
      assertThat(second.restore()).isEqualTo(0L);
    } finally {
      second.close();
    }
  }

  @Test
  void shouldStillCloseTheProviderOnAFullClose() throws Exception {
    // given a task that merged and durably committed one delta
    final AggregationStageTask task = openTask();
    final CubeHandle handle = resolve();
    task.process(envelope(handle, 1L, 0));
    Cuts.commit(task, 0L);
    final File dir = new File(stateDir.toFile(), "stage2");

    // when fully closed
    task.close();

    // then the provider released its RocksDB lock on the directory — a fresh provider can open the
    // same path (an unreleased lock would make this open fail) and sees the durable row from disk
    try (var reopened =
        RocksDbStateStoreProvider.<AnalyticsColumnFamilies>open(dir, new SimpleMeterRegistry())) {
      final KeyValueStore<DbInt, DbLong> offsets =
          reopened.keyValueStore(
              AnalyticsColumnFamilies.CONSUMED_POSITION, new DbInt(), new DbLong());
      final DbInt key = new DbInt();
      key.wrapInt(1);
      assertThat(offsets.get(key).map(DbLong::getValue)).hasValue(0L);
    }
  }

  private AggregationStageTask openTask() {
    metadataStore = new TestMetadataStore();
    registry = new DatasetRegistry();
    provision("cube-a");
    catalog = new DatasetCatalog(metadataStore);
    provider =
        RocksDbStateStoreProvider.open(
            new File(stateDir.toFile(), "stage2"), new SimpleMeterRegistry());
    return buildTask(provider);
  }

  /**
   * Reopens a task against the SAME (still-open) provider the first task left behind — the shape a
   * promoted fold takes over {@link
   * io.camunda.eventbridge.streaming.changelog.PartitionRoleController}.
   */
  private AggregationStageTask reopenTaskAgainstSameProvider() {
    return buildTask(provider);
  }

  private AggregationStageTask buildTask(
      final RocksDbStateStoreProvider<AnalyticsColumnFamilies> p) {
    final JdbcDataSource h2 = new JdbcDataSource();
    h2.setURL(
        "jdbc:h2:mem:stage2-close-keeping-stores-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
    h2.setUser("sa");
    final DataSource dataSource = h2;
    final KeyValueStore<DbBytes, DbBytes> cellStore =
        p.keyValueStore(AnalyticsColumnFamilies.CUBE_CELLS, new DbBytes(), new DbBytes());
    final KeyValueStore<DbInt, DbLong> offsets =
        p.keyValueStore(AnalyticsColumnFamilies.CONSUMED_POSITION, new DbInt(), new DbLong());
    final KeyValueStore<DbBytes, DbBytes> dedupStore =
        p.keyValueStore(
            AnalyticsColumnFamilies.SHUFFLE_DEDUP_WATERMARK, new DbBytes(), new DbBytes());
    final KeyValueStore<DbBytes, DbBytes> parkedStore =
        p.keyValueStore(AnalyticsColumnFamilies.PARKED_DELTAS, new DbBytes(), new DbBytes());
    final RdbmsDatasetStore datasetStore = new RdbmsDatasetStore(dataSource);
    return new AggregationStageTask(
        1,
        () -> 1L,
        datasetStore,
        datasetStore.writer(),
        p,
        cellStore,
        offsets,
        dedupStore,
        parkedStore,
        catalog,
        null,
        Long.MAX_VALUE,
        0L);
  }

  private void provision(final String name) {
    final DatasetDeclaration declaration =
        DatasetDeclaration.builder(name, FactType.PROCESS_INSTANCE)
            .dimension("bpmnProcessId", DimensionType.STRING)
            .meter(Meter.of("count", MeterCatalog.COUNT))
            .window(60_000L)
            .lateness(300_000L)
            .build();
    final long cubeId = registry.admit(declaration, Map.of(), 0L).cubeId();
    metadataStore.datasetSpecStore().create(registry.get(cubeId).orElseThrow());
  }

  private record CubeHandle(CompiledDataset dataset) {
    int streamId() {
      return dataset.streamId();
    }

    DimensionKeyValue keyCodec() {
      return new DimensionKeyValue(dataset.grain());
    }
  }

  private CubeHandle resolve() {
    catalog.refresh();
    final List<ActiveCube> cubes = catalog.cubes();
    assertThat(cubes).hasSize(1);
    return new CubeHandle(cubes.get(0).compiled());
  }

  private ShuffleEnvelope envelope(final CubeHandle handle, final long segment, final int chunk) {
    final byte[] key =
        handle.keyCodec().toBytes(DimensionKey.of(handle.dataset().grain(), PROCESS));
    return new ShuffleEnvelope(
        0L,
        1,
        1,
        segment,
        chunk,
        false,
        ShufflePayloadKind.AGGREGATE_DELTA,
        ShuffleOperation.MERGE,
        List.of(new CellDelta(handle.streamId(), 0L, key, oneFact(handle.dataset(), segment))));
  }

  private byte[] oneFact(final CompiledDataset dataset, final long position) {
    final CompositeAggregateFunction aggregate =
        new CompositeAggregateFunction(dataset.meterBounds());
    final Fact fact =
        Fact.builder(FactType.PROCESS_INSTANCE)
            .field("bpmnProcessId", PROCESS)
            .eventTime(0L)
            .source(1, position)
            .build();
    final Object[] accumulator = aggregate.add(fact, aggregate.createAccumulator());
    return new CompositeAccumulatorValue(dataset.meterBounds()).toBytes(accumulator);
  }

  private Long durableOffset() {
    final KeyValueStore<DbInt, DbLong> offsets =
        provider.keyValueStore(
            AnalyticsColumnFamilies.CONSUMED_POSITION, new DbInt(), new DbLong());
    final DbInt key = new DbInt();
    key.wrapInt(1);
    return offsets.get(key).map(DbLong::getValue).orElse(null);
  }
}
